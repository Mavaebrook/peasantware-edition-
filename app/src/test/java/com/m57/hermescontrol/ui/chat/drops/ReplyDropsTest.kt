package com.m57.hermescontrol.ui.chat.drops

import com.m57.hermescontrol.data.ws.WsEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyDropsTest {
    private fun complete(
        text: String = "Restart finished.",
        sessionId: String? = "rt-1",
        storedId: String? = "stored-1",
        status: String? = null,
        paused: Boolean = false,
        id: String = "c1",
    ) = WsEvent.MessageComplete(
        text = text,
        sessionId = sessionId,
        storedSessionId = storedId,
        rawPayload = status?.let { mapOf("status" to it) },
        completionId = id,
        paused = paused,
    )

    @Test
    fun recorder_makesADropFromACompletedReply() {
        val drop = ReplyDropRecorder(now = { 42L }).onEvent(complete())!!
        assertEquals("stored-1", drop.sessionId)
        assertEquals("Restart finished.", drop.preview)
        assertEquals(42L, drop.atMillis)
        assertEquals("c1", drop.key)
    }

    @Test
    fun recorder_resolvesTheStoredIdFromSessionInfo() {
        val recorder = ReplyDropRecorder()
        recorder.onEvent(WsEvent.SessionInfo(mapOf("session_id" to "rt-2", "stored_session_id" to "stored-2")))
        assertEquals("stored-2", recorder.onEvent(complete(sessionId = "rt-2", storedId = null))?.sessionId)
        recorder.onEvent(WsEvent.SessionReclaimed(sessionId = "rt-2", storedSessionId = null, reason = null))
        assertNull(recorder.onEvent(complete(sessionId = "rt-2", storedId = null)))
    }

    @Test
    fun recorder_skipsStoppedPausedAndEmptyTurns() {
        val recorder = ReplyDropRecorder()
        assertNull(recorder.onEvent(complete(status = "interrupted")))
        assertNull(recorder.onEvent(complete(paused = true)))
        assertNull(recorder.onEvent(complete(text = "  ")))
    }

    @Test
    fun recorder_marksAFailedTurnEvenWithoutText() {
        val drop = ReplyDropRecorder().onEvent(complete(text = "", status = "error"))!!
        assertTrue(drop.failed)
    }

    @Test
    fun withDrop_putsTheNewestOnTopReplacesTheSameChatAndKeepsThree() {
        fun drop(
            key: String,
            session: String,
        ) = ReplyDrop(key, session, "x", failed = false, atMillis = 0L)
        var list = emptyList<ReplyDrop>()
        list = list.withDrop(drop("a", "s1"))
        list = list.withDrop(drop("b", "s2"))
        list = list.withDrop(drop("c", "s1"))
        assertEquals(listOf("c", "b"), list.map { it.key })
        list = list.withDrop(drop("d", "s3")).withDrop(drop("e", "s4"))
        assertEquals(listOf("e", "d", "c"), list.map { it.key })
    }

    @Test
    fun replyPreview_dropsMarkdownAndCode() {
        val text =
            "## Done\n\n- **forge** is [healthy](https://x.test)\n" +
                "```bash\nsystemctl status\n```\nUse `peasant_command` now."
        assertEquals("Done forge is healthy Use peasant_command now.", replyPreview(text))
    }

    @Test
    fun replyPreview_cutsLongText() {
        val preview = replyPreview("word ".repeat(100), max = 20)
        assertEquals(20, preview.length)
        assertTrue(preview.endsWith("…"))
    }
}
