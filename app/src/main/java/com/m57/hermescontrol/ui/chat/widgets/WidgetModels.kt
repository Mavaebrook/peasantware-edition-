package com.m57.hermescontrol.ui.chat.widgets

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** Fence tag an agent puts on a chat code block that holds one widget block. */
const val WIDGET_FENCE_TAG = "peasant-widget"

/** Widget block v1, as the Gateway's widget library and agents write it (docs/WIDGETS.md). */
@Serializable
data class WidgetBlock(
    val v: Int = 1,
    val id: String,
    val type: String,
    val title: String? = null,
    val source: String? = null,
    val data: JsonObject = JsonObject(emptyMap()),
    val actions: List<WidgetActionDef> = emptyList(),
    @SerialName("refresh_s") val refreshSeconds: Int? = null,
    val status: String? = null,
    val message: String? = null,
) {
    /** False when the feed needs approval or failed; the card then shows [message] instead of data. */
    val isOk: Boolean get() = status == null || status == STATUS_OK

    companion object {
        const val STATUS_OK = "ok"
        const val STATUS_NEEDS_APPROVAL = "needs_approval"
    }
}

@Serializable
data class WidgetActionDef(
    val id: String,
    val label: String,
)

/** Reply to a button press; a pending reply means the owner must approve it first. */
@Serializable
data class WidgetActionResult(
    val ok: Boolean = false,
    val message: String? = null,
    val block: WidgetBlock? = null,
    @SerialName("pending_approval") val pendingApproval: Boolean = false,
    @SerialName("request_id") val requestId: String? = null,
)

@Serializable
data class WidgetCatalogEntry(
    val id: String,
    val title: String? = null,
    val type: String? = null,
    val source: String? = null,
    val description: String? = null,
)

// ── Typed data, one shape per block type ─────────────────────────────

@Serializable
data class StatData(
    val value: String = "",
    val detail: String? = null,
    val pct: Double? = null,
    val tone: String? = null,
)

@Serializable
data class BarSegment(
    val label: String = "",
    val count: Double = 0.0,
    val tone: String? = null,
)

@Serializable
data class BarData(
    val segments: List<BarSegment> = emptyList(),
)

@Serializable
data class ListItemData(
    val title: String = "",
    val detail: String? = null,
    val trailing: String? = null,
)

@Serializable
data class ListData(
    val items: List<ListItemData> = emptyList(),
)

@Serializable
data class RingData(
    val label: String = "",
    val pct: Double = 0.0,
    val tone: String? = null,
)

@Serializable
data class RingsData(
    val rings: List<RingData> = emptyList(),
)

@Serializable
data class MailItem(
    val id: String = "",
    val from: String = "",
    val subject: String = "",
    val time: String? = null,
    val unread: Boolean = false,
)

@Serializable
data class MailListData(
    val items: List<MailItem> = emptyList(),
)

@Serializable
data class DocRecent(
    val id: String = "",
    val title: String = "",
    @SerialName("when") val whenText: String? = null,
)

@Serializable
data class DocData(
    @SerialName("doc_id") val docId: String? = null,
    val title: String? = null,
    val text: String? = null,
    val editable: Boolean = false,
    val recent: List<DocRecent> = emptyList(),
)

@Serializable
data class FolderItem(
    val name: String = "",
    val kind: String = "file",
    @SerialName("size_bytes") val sizeBytes: Long? = null,
    val count: Int? = null,
) {
    val isDir: Boolean get() = kind == "dir"
}

@Serializable
data class FolderData(
    val path: List<String> = emptyList(),
    val items: List<FolderItem> = emptyList(),
)

@Serializable
data class BrowserStep(
    val t: String? = null,
    val text: String = "",
)

@Serializable
data class VaultMatch(
    val handle: String,
    val label: String? = null,
)

@Serializable
data class BrowserData(
    @SerialName("session_id") val sessionId: String = "",
    val url: String? = null,
    val live: Boolean = false,
    val steps: List<BrowserStep> = emptyList(),
    @SerialName("vault_match") val vaultMatch: VaultMatch? = null,
)

/** Typed view of a block's data; [Unknown] for a type this app version doesn't draw. */
sealed interface WidgetContent {
    data class Stat(
        val data: StatData,
    ) : WidgetContent

    data class Bar(
        val data: BarData,
    ) : WidgetContent

    data class Items(
        val data: ListData,
    ) : WidgetContent

    data class Rings(
        val data: RingsData,
    ) : WidgetContent

    data class Mail(
        val data: MailListData,
    ) : WidgetContent

    data class Doc(
        val data: DocData,
    ) : WidgetContent

    data class Folder(
        val data: FolderData,
    ) : WidgetContent

    data class Browser(
        val data: BrowserData,
    ) : WidgetContent

    data object Unknown : WidgetContent
}

/** Lenient: numbers may arrive as strings and unknown keys are ignored, so a newer server still draws. */
internal val widgetJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

/** Parses the text of a `peasant-widget` fence, or returns null so the chat shows it as code. */
fun parseWidgetBlock(text: String): WidgetBlock? =
    runCatching { widgetJson.parseToJsonElement(text.trim()) }.getOrNull()?.let(::blockFromReply)

/** Accepts a bare block or a `{"ok": true, "block": {...}}` reply. */
internal fun blockFromReply(element: JsonElement): WidgetBlock? =
    runCatching {
        val obj = element.jsonObject
        val inner = obj["block"] ?: element
        widgetJson.decodeFromJsonElement<WidgetBlock>(inner)
    }.getOrNull()

fun WidgetBlock.content(): WidgetContent =
    runCatching {
        when (type) {
            "stat" -> WidgetContent.Stat(widgetJson.decodeFromJsonElement(data))
            "bar" -> WidgetContent.Bar(widgetJson.decodeFromJsonElement(data))
            "list" -> WidgetContent.Items(widgetJson.decodeFromJsonElement(data))
            "rings" -> WidgetContent.Rings(widgetJson.decodeFromJsonElement(data))
            "mail_list" -> WidgetContent.Mail(widgetJson.decodeFromJsonElement(data))
            "doc" -> WidgetContent.Doc(widgetJson.decodeFromJsonElement(data))
            "folder" -> WidgetContent.Folder(widgetJson.decodeFromJsonElement(data))
            "browser" -> WidgetContent.Browser(widgetJson.decodeFromJsonElement(data))
            else -> WidgetContent.Unknown
        }
    }.getOrDefault(WidgetContent.Unknown)

/** Encodes a block for the local dashboard snapshot. */
internal fun WidgetBlock.toJsonString(): String = widgetJson.encodeToString(WidgetBlock.serializer(), this)

/** Blocks sent to the TV carry every field the Gateway checks, `v` included. */
private val tvJson =
    Json {
        encodeDefaults = true
        explicitNulls = false
    }

internal fun WidgetBlock.toTvJson(): JsonElement = tvJson.encodeToJsonElement(WidgetBlock.serializer(), this)

/** The body of a cast to the TV: `{"blocks": [...], "title"?}`. */
internal fun tvBody(
    blocks: List<WidgetBlock>,
    title: String? = null,
): JsonObject =
    buildJsonObject {
        title?.let { put("title", it) }
        put("blocks", JsonArray(blocks.map { it.toTvJson() }))
    }

/** This list with [updated] in place of the block with the same id; other blocks stay as they are. */
internal fun List<WidgetBlock>.replacing(updated: WidgetBlock): List<WidgetBlock> =
    map { if (it.id == updated.id) updated else it }
