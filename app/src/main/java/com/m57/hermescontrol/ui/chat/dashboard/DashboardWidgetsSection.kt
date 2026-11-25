package com.m57.hermescontrol.ui.chat.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.ui.chat.widgets.DashboardPins
import com.m57.hermescontrol.ui.chat.widgets.PeasantWidgetCard
import com.m57.hermescontrol.ui.chat.widgets.PeasantWidgets
import com.m57.hermescontrol.ui.chat.widgets.WidgetBlock

/**
 * The widgets on the dashboard: the ones the user pinned from chat, then the
 * Gateway's own tiles. Each pin shows its saved block at once and is refetched
 * when the panel opens.
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(stringResource(R.string.chat_dashboard_widgets))
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
