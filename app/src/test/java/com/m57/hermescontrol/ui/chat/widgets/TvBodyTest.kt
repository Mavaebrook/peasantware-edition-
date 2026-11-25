package com.m57.hermescontrol.ui.chat.widgets

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvBodyTest {
    private val block = parseWidgetBlock("""{"id":"disk","type":"stat","title":"Disk","data":{"value":"52 GB"}}""")!!

    @Test
    fun tvBody_sendsEveryFieldTheGatewayChecks() {
        val body = tvBody(listOf(block), "Dashboard")
        assertEquals("Dashboard", body["title"]!!.jsonPrimitive.content)
        val sent = body["blocks"]!!.jsonArray.single().jsonObject
        // `v` is a default in the model; the Gateway refuses a block without it.
        assertEquals(1, sent["v"]!!.jsonPrimitive.content.toInt())
        assertEquals("stat", sent["type"]!!.jsonPrimitive.content)
        assertTrue(sent.containsKey("data"))
        assertTrue(sent.containsKey("actions"))
        assertFalse(sent.containsKey("message"))
    }

    @Test
    fun tvBody_leavesTheTitleOutWhenThereIsNone() {
        assertFalse(tvBody(listOf(block)).containsKey("title"))
    }
}
