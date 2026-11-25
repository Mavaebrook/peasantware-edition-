package com.m57.hermescontrol.ui.chat.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.model.CommandHomeOverview
import com.m57.hermescontrol.data.model.KanbanBoardResponse
import com.m57.hermescontrol.data.model.SystemStatsResponse
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.NetworkError
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** Server load as fractions from 0 to 1; null when the server does not report one. */
data class StripServerStats(
    val cpu: Float?,
    val ram: Float?,
    val disk: Float?,
)

/** One Tasks board column and how many cards it holds. */
data class StripTaskColumn(
    val name: String,
    val count: Int,
)

/** Approvals waiting across every chat, as the Gateway reports them. */
sealed interface StripApprovals {
    /** The server has no command-home plugin, so only this chat's approvals are known. */
    data object NotAvailable : StripApprovals

    /** The Gateway did not answer, so the count is unknown, not zero. */
    data object Unknown : StripApprovals

    data class Count(
        val pending: Int,
    ) : StripApprovals
}

data class ChatStatusStripState(
    val approvals: StripApprovals = StripApprovals.NotAvailable,
    val server: StripServerStats? = null,
    /** Null until the board loads, or for good when the server has no Tasks board. */
    val taskColumns: List<StripTaskColumn>? = null,
)

/**
 * Feeds the chat status strip and the dashboard panel. Polls only while the
 * strip is on screen: [SharingStarted.WhileSubscribed] stops the loop when the
 * chat leaves the foreground.
 */
class ChatStatusStripViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollMillis: Long = POLL_MILLIS,
) : ViewModel() {
    @Volatile
    private var boardMissing = false

    @Volatile
    private var overviewMissing = false

    val state: StateFlow<ChatStatusStripState> =
        flow {
            var current = ChatStatusStripState()
            while (true) {
                current = fetch(current)
                emit(current)
                delay(pollMillis)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ChatStatusStripState())

    private suspend fun fetch(previous: ChatStatusStripState): ChatStatusStripState =
        withContext(ioDispatcher) {
            coroutineScope {
                val statsCall = async { safeApiCall { ApiClient.hermesApi.getSystemStats() } }
                val boardCall =
                    if (boardMissing) {
                        null
                    } else {
                        async { safeApiCall { ApiClient.hermesApi.getKanbanBoard() } }
                    }
                val overviewCall =
                    if (overviewMissing) {
                        null
                    } else {
                        async { safeApiCall { ApiClient.hermesApi.getCommandHomeOverview() } }
                    }
                val stats = statsCall.await()
                val board = boardCall?.await()
                val overview = overviewCall?.await()
                if (board is NetworkResult.Failure && board.error.isNotFound()) boardMissing = true
                if (overview is NetworkResult.Failure && overview.error.isNotFound()) overviewMissing = true
                ChatStatusStripState(
                    approvals =
                        when {
                            overviewMissing -> StripApprovals.NotAvailable
                            overview is NetworkResult.Success -> overview.data.toStripApprovals()
                            overview is NetworkResult.Failure -> StripApprovals.Unknown
                            else -> previous.approvals
                        },
                    server = (stats as? NetworkResult.Success)?.data?.toStripStats() ?: previous.server,
                    taskColumns =
                        when {
                            boardMissing -> null
                            board is NetworkResult.Success -> board.data?.toStripColumns()
                            else -> previous.taskColumns
                        },
                )
            }
        }

    private fun NetworkError.isNotFound(): Boolean = this is NetworkError.Http && code == HTTP_NOT_FOUND

    companion object {
        private const val POLL_MILLIS = 30_000L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val HTTP_NOT_FOUND = 404
    }
}

internal fun SystemStatsResponse.toStripStats(): StripServerStats =
    StripServerStats(
        cpu = cpu_percent?.toFraction(),
        ram = memory?.percent?.toFraction(),
        disk = disk?.percent?.toFraction(),
    )

internal fun CommandHomeOverview?.toStripApprovals(): StripApprovals {
    val approvals = this?.approvals ?: return StripApprovals.Unknown
    return if (approvals.reachable) StripApprovals.Count(approvals.pending.size) else StripApprovals.Unknown
}

internal fun KanbanBoardResponse.toStripColumns(): List<StripTaskColumn> =
    columns.map { StripTaskColumn(name = it.name, count = it.tasks.size) }

private fun Double.toFraction(): Float = (this / 100.0).coerceIn(0.0, 1.0).toFloat()

/** Load level used to colour a meter. */
enum class LoadLevel { OK, WARN, BAD }

internal fun loadLevel(fraction: Float): LoadLevel =
    when {
        fraction >= 0.9f -> LoadLevel.BAD
        fraction >= 0.75f -> LoadLevel.WARN
        else -> LoadLevel.OK
    }
