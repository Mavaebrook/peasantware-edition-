package com.m57.hermescontrol.ui.chat.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.ui.common.ExpressiveTextButton
import com.m57.hermescontrol.ui.chat.widgets.DashboardPins
import com.m57.hermescontrol.ui.chat.widgets.PeasantWidgetCard
import com.m57.hermescontrol.ui.chat.widgets.PeasantWidgets
import com.m57.hermescontrol.ui.chat.widgets.WidgetBlock
import kotlinx.coroutines.launch

/**
 * The widgets on the dashboard: the ones the user pinned from chat, then the
 * Gateway's own tiles. Each pin shows its saved block at once and is refetched
 * when the panel opens. Send dashboard to TV shows them all full-screen on the TV.
 */
@Composable
internal fun DashboardWidgetsSection() {
    val pins by DashboardPins.pins.collectAsStateWithLifecycle()
    var tiles by remember { mutableStateOf<List<WidgetBlock>>(emptyList()) }

    LaunchedEffect(Unit) {
        (PeasantWidgets.dashTiles() as? NetworkResult.Success)?.let { tiles = it.data }
    }
    LaunchedEffect(Unit) {
        DashboardPins.pins.value.forEach { pin ->
            (PeasantWidgets.fetch(pin.id) as? NetworkResult.Success)?.let { DashboardPins.remember(it.data) }
        }
    }

    val pinnedIds = pins.map { it.id }.toSet()
    val shownTiles = tiles.filter { it.id !in pinnedIds }
    val shown = pins.mapNotNull { it.snapshot } + shownTiles
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val tvTitle = stringResource(R.string.chat_dashboard_tv_title)
    val onTvText = stringResource(R.string.widget_on_tv)
    val tvFailedText = stringResource(R.string.widget_tv_failed)
    val waitingText = stringResource(R.string.widget_waiting_approval)

    fun sendAllToTv() {
        if (sending || shown.isEmpty()) return
        sending = true
        notice = null
        scope.launch {
            notice =
                when (val result = PeasantWidgets.sendDashboardToTv(shown, tvTitle)) {
                    is NetworkResult.Success -> {
                        when {
                            result.data.pendingApproval -> result.data.message ?: waitingText
                            result.data.ok -> onTvText
                            else -> result.data.message ?: tvFailedText
                        }
                    }

                    is NetworkResult.Failure -> {
                        tvFailedText
                    }
                }
            sending = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) { SectionTitle(stringResource(R.string.chat_dashboard_widgets)) }
            if (shown.isNotEmpty()) {
                ExpressiveTextButton(onClick = { sendAllToTv() }, enabled = !sending) {
                    Text(stringResource(R.string.chat_dashboard_send_to_tv))
                }
            }
        }
        notice?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        pins.forEach { pin ->
            pin.snapshot?.let { block ->
                PeasantWidgetCard(
                    block = block,
                    inDashboard = true,
                    onRemoveFromDashboard = { DashboardPins.unpin(pin.id) },
                )
            }
        }
        shownTiles.forEach { block -> PeasantWidgetCard(block = block, inDashboard = true) }
        if (pins.none { it.snapshot != null } && shownTiles.isEmpty()) {
            Text(
                text = stringResource(R.string.chat_dashboard_widgets_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
