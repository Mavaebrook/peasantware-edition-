package com.m57.hermescontrol.ui.chat.widgets

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelsTest {
    private val statBlock =
        """
        {"v":1,"id":"vps.cpu","type":"stat","title":"CPU","source":"vps",
         "data":{"value":"42%","pct":42,"tone":"ok"},"actions":[{"id":"refresh","label":"Refresh"}],
         "refresh_s":30,"status":"ok","future_field":true}
        """.trimIndent()

    @Test
    fun parseWidgetBlock_readsABareBlockAndIgnoresUnknownKeys() {
        val block = parseWidgetBlock(statBlock)
        assertNotNull(block)
        assertEquals("vps.cpu", block!!.id)
        assertEquals(30, block.refreshSeconds)
        assertTrue(block.isOk)
        val content = block.content() as WidgetContent.Stat
        assertEquals("42%", content.data.value)
        assertEquals(42.0, content.data.pct!!, 0.0)
    }

    @Test
    fun parseWidgetBlock_acceptsTheToolReplyWrapper() {
        val block = parseWidgetBlock("""{"ok":true,"block":$statBlock}""")
        assertEquals("vps.cpu", block?.id)
    }

    @Test
    fun parseWidgetBlock_returnsNullForTextThatIsNotABlock() {
        assertNull(parseWidgetBlock("not json"))
        assertNull(parseWidgetBlock("""{"title":"no id or type"}"""))
    }

    @Test
    fun blockFromReply_readsBothShapes() {
        val bare = widgetJson.parseToJsonElement(statBlock)
        val wrapped = widgetJson.parseToJsonElement("""{"ok":true,"block":$statBlock}""")
        assertEquals("vps.cpu", blockFromReply(bare)?.id)
        assertEquals("vps.cpu", blockFromReply(wrapped)?.id)
    }

    @Test
    fun content_isUnknownForANewTypeOrBadData() {
        val newType = parseWidgetBlock("""{"id":"x","type":"hologram","data":{}}""")!!
        assertEquals(WidgetContent.Unknown, newType.content())
        val badData = parseWidgetBlock("""{"id":"x","type":"bar","data":{"segments":"oops"}}""")!!
        assertEquals(WidgetContent.Unknown, badData.content())
    }

    @Test
    fun content_readsFolderAndDocFields() {
        val folder =
            parseWidgetBlock(
                """{"id":"f","type":"folder","data":{"path":["home"],
                "items":[{"name":"notes","kind":"dir","count":3},{"name":"a.txt","kind":"file","size_bytes":2048}]}}""",
            )!!.content() as WidgetContent.Folder
        assertTrue(folder.data.items[0].isDir)
        assertEquals(2048L, folder.data.items[1].sizeBytes)
        val doc =
            parseWidgetBlock(
                """{"id":"d","type":"doc","data":{"doc_id":"7","text":"hi","editable":true,
                "recent":[{"id":"8","title":"Plan","when":"2026-11-20"}]}}""",
            )!!.content() as WidgetContent.Doc
        assertEquals("7", doc.data.docId)
        assertEquals(
            "2026-11-20",
            doc.data.recent
                .single()
                .whenText,
        )
    }

    @Test
    fun isOk_isFalseWhenTheFeedNeedsApproval() {
        val block =
            parseWidgetBlock("""{"id":"m","type":"mail_list","status":"needs_approval","message":"Approve"}""")!!
        assertFalse(block.isOk)
    }

    @Test
    fun toJsonString_roundTrips() {
        val block = parseWidgetBlock(statBlock)!!
        assertEquals(block, parseWidgetBlock(block.toJsonString()))
    }

    @Test
    fun splitRecipients_splitsOnCommasAndSemicolons() {
        assertEquals(listOf("a@x.com", "b@y.com", "c@z.com"), splitRecipients(" a@x.com, b@y.com;c@z.com ,"))
        assertTrue(splitRecipients("  ").isEmpty())
    }

    @Test
    fun sendBody_carriesReplyIdOnlyWhenThereIsOne() {
        val reply = sendBody(listOf("a@x.com"), "Re: hi", "Thanks", "m1")
        assertEquals(JsonArray(listOf(JsonPrimitive("a@x.com"))), reply["to"])
        assertEquals("m1", reply["in_reply_to"]?.jsonPrimitive?.content)
        val fresh = sendBody(listOf("a@x.com"), "hi", "Hello", null)
        assertNull(fresh["in_reply_to"])
    }

    @Test
    fun formatBytes_picksAUnit() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2.0 KB", formatBytes(2048))
        assertEquals("1.5 MB", formatBytes(1_572_864))
    }

    @Test
    fun formatCount_dropsTheDecimalForWholeNumbers() {
        assertEquals("4", formatCount(4.0))
        assertEquals("2.5", formatCount(2.5))
    }
}
