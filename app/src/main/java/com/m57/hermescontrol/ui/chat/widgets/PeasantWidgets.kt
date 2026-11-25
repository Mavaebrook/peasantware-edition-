package com.m57.hermescontrol.ui.chat.widgets

import android.content.Context
import android.content.SharedPreferences
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.NetworkError
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/** Calls to the server's `peasant-gateway` Hermes plugin, which passes them on to the Gateway. */
object PeasantWidgets {
    /** The most widgets the TV shows at once (the Gateway's cast_to_tv limit). */
    const val TV_MAX_BLOCKS = 12

    suspend fun fetch(id: String): NetworkResult<WidgetBlock> =
        withContext(Dispatchers.IO) {
            when (val result = safeApiCall { ApiClient.hermesApi.getWidget(id) }) {
                is NetworkResult.Success -> result.data.decodeOr { blockFromReply(it) }
                is NetworkResult.Failure -> result
            }
        }

    suspend fun act(
        id: String,
        action: String,
        body: JsonObject,
    ): NetworkResult<WidgetActionResult> =
        withContext(Dispatchers.IO) {
            when (val result = safeApiCall { ApiClient.hermesApi.runWidgetAction(id, action, body) }) {
                is NetworkResult.Success -> {
                    result.data.decodeOr {
                        runCatching { widgetJson.decodeFromJsonElement<WidgetActionResult>(it) }.getOrNull()
                    }
                }

                is NetworkResult.Failure -> {
                    result
                }
            }
        }

    /**
     * Puts one widget on the TV. A library widget is refetched by the Gateway as it goes;
     * a block an agent wrote by hand isn't in the library, so it goes as the block itself.
     */
    suspend fun sendToTv(block: WidgetBlock): NetworkResult<WidgetActionResult> =
        withContext(Dispatchers.IO) {
            val byId = safeApiCall { ApiClient.hermesApi.sendWidgetToTv(block.id, JsonObject(emptyMap())) }
            val notInLibrary = byId is NetworkResult.Failure && (byId.error as? NetworkError.Http)?.code == 404
            val result = if (notInLibrary) safeApiCall { ApiClient.hermesApi.sendToTv(tvBody(listOf(block))) } else byId
            result.toTvResult()
        }

    /** Puts every widget on the dashboard on the TV at once, as the blocks the panel shows. */
    suspend fun sendDashboardToTv(
        blocks: List<WidgetBlock>,
        title: String,
    ): NetworkResult<WidgetActionResult> =
        withContext(Dispatchers.IO) {
            safeApiCall { ApiClient.hermesApi.sendToTv(tvBody(blocks.take(TV_MAX_BLOCKS), title)) }.toTvResult()
        }

    private fun NetworkResult<JsonElement?>.toTvResult(): NetworkResult<WidgetActionResult> =
        when (this) {
            is NetworkResult.Success -> {
                data.decodeOr { runCatching { widgetJson.decodeFromJsonElement<WidgetActionResult>(it) }.getOrNull() }
            }

            is NetworkResult.Failure -> {
                this
            }
        }

    /** Blocks agents reported to the dashboard with their dash tools. */
    suspend fun dashTiles(): NetworkResult<List<WidgetBlock>> =
        withContext(Dispatchers.IO) {
            when (val result = safeApiCall { ApiClient.hermesApi.getDashTiles() }) {
                is NetworkResult.Success -> result.data.decodeOr { listFrom(it, "tiles", WidgetBlock.serializer()) }
                is NetworkResult.Failure -> result
            }
        }

    suspend fun catalog(): NetworkResult<List<WidgetCatalogEntry>> =
        withContext(Dispatchers.IO) {
            when (val result = safeApiCall { ApiClient.hermesApi.getWidgetCatalog() }) {
                is NetworkResult.Success -> {
                    result.data.decodeOr { listFrom(it, "widgets", WidgetCatalogEntry.serializer()) }
                }

                is NetworkResult.Failure -> {
                    result
                }
            }
        }

    private fun <T> JsonElement?.decodeOr(decode: (JsonElement) -> T?): NetworkResult<T> {
        val value = this?.let(decode)
        return if (value != null) {
            NetworkResult.Success(value)
        } else {
            val problem = "The server sent a widget this app can't read"
            NetworkResult.Failure(NetworkError.Unknown(problem, IllegalStateException(problem)))
        }
    }

    /** Reads `{"<key>": [...]}` or a bare array; entries that don't parse are skipped. */
    private fun <T> listFrom(
        element: JsonElement,
        key: String,
        serializer: KSerializer<T>,
    ): List<T>? {
        val array =
            runCatching {
                if (element is JsonArray) element else element.jsonObject[key]?.jsonArray
            }.getOrNull() ?: return null
        return array.mapNotNull { runCatching { widgetJson.decodeFromJsonElement(serializer, it) }.getOrNull() }
    }
}

/** A widget pinned to the dashboard, with the last block seen so it shows before a refresh. */
data class PinnedWidget(
    val id: String,
    val snapshot: WidgetBlock?,
)

/**
 * The user's dashboard widgets, kept on this device. A pin stores the block it was
 * pinned from, so a widget an agent wrote by hand still shows when the server
 * can't refetch it by id.
 */
object DashboardPins {
    private const val PREFS_NAME = "dashboard_widgets"
    private const val KEY_ORDER = "order"
    private const val SNAPSHOT_PREFIX = "snapshot:"
    private const val SEPARATOR = "\n"

    private var prefs: SharedPreferences? = null
    private val _pins = MutableStateFlow<List<PinnedWidget>>(emptyList())
    val pins: StateFlow<List<PinnedWidget>> = _pins.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p
        val ids =
            p
                .getString(KEY_ORDER, null)
                ?.split(SEPARATOR)
                ?.filter { it.isNotBlank() }
                .orEmpty()
        _pins.value =
            ids.distinct().map { id ->
                PinnedWidget(id, p.getString(SNAPSHOT_PREFIX + id, null)?.let(::parseWidgetBlock))
            }
    }

    fun isPinned(id: String): Boolean = _pins.value.any { it.id == id }

    fun pin(block: WidgetBlock) {
        _pins.update { current ->
            if (current.any { it.id == block.id }) {
                current.map { if (it.id == block.id) it.copy(snapshot = block) else it }
            } else {
                current + PinnedWidget(block.id, block)
            }
        }
        persist()
    }

    fun unpin(id: String) {
        _pins.update { current -> current.filterNot { it.id == id } }
        prefs?.edit()?.remove(SNAPSHOT_PREFIX + id)?.apply()
        persist()
    }

    /** Keeps the newest block for a pinned widget after a refresh or an action. */
    fun remember(block: WidgetBlock) {
        if (!isPinned(block.id)) return
        _pins.update { current -> current.map { if (it.id == block.id) it.copy(snapshot = block) else it } }
        persist()
    }

    private fun persist() {
        val p = prefs ?: return
        val editor = p.edit().putString(KEY_ORDER, _pins.value.joinToString(SEPARATOR) { it.id })
        _pins.value.forEach { pin ->
            pin.snapshot?.let { editor.putString(SNAPSHOT_PREFIX + pin.id, it.toJsonString()) }
        }
        editor.apply()
    }
}
