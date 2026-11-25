package com.m57.hermescontrol.ui.chat.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplacingTest {
    private fun stat(
        id: String,
        value: String,
    ) = parseWidgetBlock("""{"id":"$id","type":"stat","title":"$id","data":{"value":"$value"}}""")!!

    @Test
    fun replacing_swapsInTheUpdatedBlockAndKeepsOrder() {
        val tiles = listOf(stat("disk", "52 GB"), stat("cpu", "12%"))
        val updated = stat("disk", "48 GB")
        assertEquals(listOf(updated, tiles[1]), tiles.replacing(updated))
    }

    @Test
    fun replacing_leavesTheListAloneWhenNoIdMatches() {
        val tiles = listOf(stat("disk", "52 GB"))
        assertEquals(tiles, tiles.replacing(stat("mem", "3 GB")))
    }
}
