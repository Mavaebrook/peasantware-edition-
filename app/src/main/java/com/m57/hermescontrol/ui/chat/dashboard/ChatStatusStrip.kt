package com.m57.hermescontrol.ui.chat.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.LocalHermesStatusColors

/** What the Approvals chip shows: a count, or a question mark when it isn't known. */
internal fun approvalsLabel(
    approvals: StripApprovals,
    chatPending: Int,
): Int? =
    when (approvals) {
        is StripApprovals.Count -> approvals.pending
        StripApprovals.NotAvailable -> chatPending
        StripApprovals.Unknown -> null
    }

/**
 * Glanceable strip under the chat's top bar: approvals waiting, the Tasks board
 * and server load. Each chip is a shortcut; any part the server can't report is left out.
 */
@Composable
fun ChatStatusStrip(
    state: ChatStatusStripState,
    chatPendingApprovals: Int,
    onApprovalsClick: () -> Unit,
    onTasksClick: () -> Unit,
    onServerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ApprovalsChip(
            count = approvalsLabel(state.approvals, chatPendingApprovals),
            onClick = onApprovalsClick,
        )
        state.taskColumns?.let { columns -> TasksChip(columns = columns, onClick = onTasksClick) }
        state.server?.let { server -> ServerChip(server = server, onClick = onServerClick) }
    }
}

@Composable
private fun ApprovalsChip(
    count: Int?,
    onClick: () -> Unit,
) {
    val statusColors = LocalHermesStatusColors.current
    val waiting = count == null || count > 0
    val countText = count?.toString() ?: stringResource(R.string.chat_strip_unknown)
    val description =
        if (count == null) {
            stringResource(R.string.chat_strip_approvals_unknown_desc)
        } else {
            stringResource(R.string.chat_strip_approvals_desc, count)
        }
    StripChip(
        icon = Icons.Filled.VerifiedUser,
        label = stringResource(R.string.chat_strip_approvals),
        description = description,
        onClick = onClick,
    ) {
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (waiting) statusColors.warning else MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 6.dp),
        ) {
            Text(
                text = countText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (waiting) statusColors.onWarning else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TasksChip(
    columns: List<StripTaskColumn>,
    onClick: () -> Unit,
) {
    val total = columns.sumOf { it.count }
    StripChip(
        icon = Icons.Filled.ViewKanban,
        label = stringResource(R.string.chat_strip_tasks),
        description = stringResource(R.string.chat_strip_tasks_desc, total),
        onClick = onClick,
    ) {
        TaskSegmentBar(columns = columns, modifier = Modifier.width(48.dp).height(6.dp))
        Text(
            text = total.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** One coloured segment per board column, sized by how many cards it holds. */
@Composable
internal fun TaskSegmentBar(
    columns: List<StripTaskColumn>,
    modifier: Modifier = Modifier,
) {
    val palette = taskColumnPalette()
    val filled = columns.filter { it.count > 0 }
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface),
    ) {
        filled.forEachIndexed { index, column ->
            Box(
                modifier =
                    Modifier
                        .weight(column.count.toFloat())
                        .fillMaxHeight()
                        .background(palette[index % palette.size]),
            )
        }
    }
}

@Composable
internal fun taskColumnPalette(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    val status = LocalHermesStatusColors.current
    return listOf(status.neutral, status.info, status.warning, status.success, scheme.tertiary, scheme.secondary)
}

@Composable
private fun ServerChip(
    server: StripServerStats,
    onClick: () -> Unit,
) {
    val meters =
        listOfNotNull(
            server.cpu?.let { R.string.chat_strip_cpu to it },
            server.ram?.let { R.string.chat_strip_ram to it },
            server.disk?.let { R.string.chat_strip_disk to it },
        )
    if (meters.isEmpty()) return
    StripChip(
        icon = Icons.Filled.Dns,
        label = stringResource(R.string.chat_strip_server),
        description =
            stringResource(
                R.string.chat_strip_server_desc,
                percent(server.cpu),
                percent(server.ram),
                percent(server.disk),
            ),
        onClick = onClick,
    ) {
        meters.forEach { (labelRes, fraction) ->
            MiniMeter(label = stringResource(labelRes), fraction = fraction)
        }
    }
}

@Composable
private fun percent(fraction: Float?): String =
    fraction?.let { stringResource(R.string.chat_strip_percent, (it * 100).toInt()) }
        ?: stringResource(R.string.chat_strip_unknown)

@Composable
private fun MiniMeter(
    label: String,
    fraction: Float,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
        LoadBar(fraction = fraction, modifier = Modifier.width(22.dp).height(5.dp))
        Text(
            text = "${(fraction * 100).toInt()}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Horizontal fill coloured by load: green under 75%, amber to 90%, red above. */
@Composable
internal fun LoadBar(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    val color = loadColor(fraction)
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .background(color),
        )
    }
}

@Composable
internal fun loadColor(fraction: Float): Color {
    val status = LocalHermesStatusColors.current
    return when (loadLevel(fraction)) {
        LoadLevel.OK -> status.success
        LoadLevel.WARN -> status.warning
        LoadLevel.BAD -> status.error
    }
}

@Composable
private fun StripChip(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            content()
        }
    }
}
