package com.m57.hermescontrol.ui.chat.dashboard

import com.m57.hermescontrol.data.model.CommandHomeApprovals
import com.m57.hermescontrol.data.model.CommandHomeOverview
import com.m57.hermescontrol.data.model.DiskStats
import com.m57.hermescontrol.data.model.KanbanBoardResponse
import com.m57.hermescontrol.data.model.KanbanColumn
import com.m57.hermescontrol.data.model.MemoryStats
import com.m57.hermescontrol.data.model.SystemStatsResponse
import com.m57.hermescontrol.ui.chat.ApprovalInfo
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatStatusStripTest {
    @Test
    fun loadLevel_turnsAmberAt75AndRedAt90() {
        assertEquals(LoadLevel.OK, loadLevel(0.74f))
        assertEquals(LoadLevel.WARN, loadLevel(0.75f))
        assertEquals(LoadLevel.WARN, loadLevel(0.89f))
        assertEquals(LoadLevel.BAD, loadLevel(0.9f))
    }

    @Test
    fun systemStats_becomeFractionsAndKeepMissingValuesNull() {
        val stats =
            SystemStatsResponse(
                cpu_percent = 42.0,
                memory = MemoryStats(percent = 150.0),
                disk = DiskStats(percent = null),
            ).toStripStats()

        assertEquals(0.42f, stats.cpu!!, 0.0001f)
        assertEquals(1f, stats.ram!!, 0.0001f)
        assertNull(stats.disk)
    }

    @Test
    fun overview_countsPendingAndTreatsUnreachableAsUnknown() {
        val pending = List(3) { JsonPrimitive(it) }
        assertEquals(
            StripApprovals.Count(3),
            CommandHomeOverview(approvals = CommandHomeApprovals(pending = pending)).toStripApprovals(),
        )
        assertEquals(
            StripApprovals.Unknown,
            CommandHomeOverview(approvals = CommandHomeApprovals(reachable = false)).toStripApprovals(),
        )
        assertEquals(StripApprovals.Unknown, CommandHomeOverview().toStripApprovals())
    }

    @Test
    fun approvalsLabel_fallsBackToThisChatWhenTheGatewayHasNoOverview() {
        assertEquals(5, approvalsLabel(StripApprovals.Count(5), chatPending = 1))
        assertEquals(1, approvalsLabel(StripApprovals.NotAvailable, chatPending = 1))
        assertNull(approvalsLabel(StripApprovals.Unknown, chatPending = 1))
    }

    @Test
    fun board_becomesOneCountPerColumn() {
        val board = KanbanBoardResponse(columns = listOf(KanbanColumn("todo"), KanbanColumn("done")))
        assertEquals(listOf(StripTaskColumn("todo", 0), StripTaskColumn("done", 0)), board.toStripColumns())
    }

    @Test
    fun approvalSummary_prefersDescriptionThenCommand() {
        val info = ApprovalInfo(command = "rm -rf build", description = " ", patternKeys = null)
        assertEquals("rm -rf build", info.summary())
    }
}
