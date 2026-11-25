package com.m57.hermescontrol.ui.chat.drops

import com.m57.hermescontrol.data.ws.WsEvent

/** Most cards stacked at the top of the chat at once; the newest is on top. */
internal const val MAX_REPLY_DROPS = 3

/** A finished agent reply in a chat other than the one on screen. */
data class ReplyDrop(
    val key: String,
    val sessionId: String,
    val preview: String,
    val failed: Boolean,
    val atMillis: Long,
)

/**
 * Turns `message.complete` events into [ReplyDrop]s, keyed by stored session id.
 * Stopped and paused turns make no card, and neither does a turn with no text.
 */
internal class ReplyDropRecorder(
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val storedIdByRuntimeId = mutableMapOf<String, String>()

    fun onEvent(event: WsEvent): ReplyDrop? {
        when (event) {
            is WsEvent.SessionInfo -> {
                val data = event.data ?: return null
                val runtimeId = (data["session_id"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                val storedId = (data["stored_session_id"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
                storedIdByRuntimeId[runtimeId] = storedId
            }

            is WsEvent.SessionReclaimed -> {
                event.sessionId?.trim()?.let(storedIdByRuntimeId::remove)
            }

            is WsEvent.MessageComplete -> {
                return fromComplete(event)
            }

            else -> {}
        }
        return null
    }

    private fun fromComplete(event: WsEvent.MessageComplete): ReplyDrop? {
        if (event.paused) return null
        val status = event.rawPayload?.get("status") as? String
        if (status == "interrupted") return null
        val failed = status == "error"
        if (event.text.isBlank() && !failed) return null
        val storedId =
            event.storedSessionId?.trim()?.takeIf { it.isNotEmpty() }
                ?: event.sessionId?.trim()?.let(storedIdByRuntimeId::get)
                ?: return null
        return ReplyDrop(
            key = event.completionId,
            sessionId = storedId,
            preview = replyPreview(event.text),
            failed = failed,
            atMillis = now(),
        )
    }
}

/** Adds [drop] on top, replacing an older card from the same chat, and keeps at most [max]. */
internal fun List<ReplyDrop>.withDrop(
    drop: ReplyDrop,
    max: Int = MAX_REPLY_DROPS,
): List<ReplyDrop> = (listOf(drop) + filterNot { it.sessionId == drop.sessionId || it.key == drop.key }).take(max)

private val CODE_FENCE = Regex("```[^\\n]*\\n[\\s\\S]*?(?:```|$)")
private val MARKDOWN_LINK = Regex("!?\\[([^\\]]*)]\\([^)]*\\)")
private val LINE_MARKER = Regex("(?m)^\\s*(?:#{1,6}\\s+|>\\s?|[-*+]\\s+(?:\\[[ xX]]\\s+)?|\\d+[.)]\\s+)")
private val EMPHASIS = Regex("\\*{1,3}|`+|~~")
private val WHITESPACE = Regex("\\s+")

/** The reply as plain text for the card: code blocks, links and Markdown marks removed. */
internal fun replyPreview(
    text: String,
    max: Int = 200,
): String {
    val plain =
        text
            .replace(CODE_FENCE, " ")
            .replace(MARKDOWN_LINK, "$1")
            .replace(LINE_MARKER, "")
            .replace(EMPHASIS, "")
            .replace(WHITESPACE, " ")
            .trim()
    return if (plain.length <= max) plain else plain.take(max - 1).trimEnd() + "…"
}
