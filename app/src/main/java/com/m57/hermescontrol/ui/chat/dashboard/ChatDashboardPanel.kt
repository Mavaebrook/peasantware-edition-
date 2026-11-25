package com.m57.hermescontrol.ui.chat.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.m57.hermescontrol.R
import com.m57.hermescontrol.ui.chat.ApprovalInfo
import com.m57.hermescontrol.ui.common.ExpressiveButton
import com.m57.hermescontrol.ui.common.ExpressiveCircularProgressIndicator
import com.m57.hermescontrol.ui.common.ExpressiveOutlinedButton
import com.m57.hermescontrol.ui.common.ExpressiveTextButton
import com.m57.hermescontrol.ui.common.HermesIconButton

/** The newest approval in this chat, with the message id that answers it. */
data class DashboardApproval(
    val messageId: String,
    val info: ApprovalInfo,
)

/** One line that says what an approval is for, from the most specific field the server sent. */
internal fun ApprovalInfo.summary(): String? =
    listOf(description, command, label, path, host).firstOrNull { !it.isNullOrBlank() }

/**
 * Dashboard that slides in from the right over the chat. A swipe left from the
 * right edge opens it; a swipe right, a tap outside or Back closes it.
 */
@Composable
fun ChatDashboardPanel(
    visible: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    state: ChatStatusStripState,
    approval: DashboardApproval?,
    chatPendingApprovals: Int,
    onAllowOnce: (DashboardApproval) -> Unit,
    onDeny: (DashboardApproval) -> Unit,
    onShowInChat: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    val swipeThreshold = with(LocalDensity.current) { SWIPE_DP.dp.toPx() }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (!visible) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .width(EDGE_WIDTH_DP.dp)
                        .fillMaxHeight(EDGE_HEIGHT_FRACTION)
                        .systemGestureExclusion()
                        .pointerInput(Unit) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = { if (total < -swipeThreshold) onOpen() },
                                onHorizontalDrag = { change, amount ->
                                    total += amount
                                    change.consume()
                                },
                            )
                        },
            )
        }
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
            )
        }
        val panelWidth = min(maxWidth * PANEL_WIDTH_FRACTION, PANEL_MAX_WIDTH_DP.dp)
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 2.dp,
                modifier =
                    Modifier
                        .width(panelWidth)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = { if (total > swipeThreshold) onDismiss() },
                                onHorizontalDrag = { _, amount -> total += amount },
                            )
                        },
            ) {
                DashboardContent(
                    state = state,
                    approval = approval,
                    chatPendingApprovals = chatPendingApprovals,
                    onClose = onDismiss,
                    onAllowOnce = onAllowOnce,
                    onDeny = onDeny,
                    onShowInChat = onShowInChat,
                    onOpenTasks = onOpenTasks,
                    onOpenServer = onOpenServer,
                )
            }
        }
    }
}

@Composable
private fun DashboardContent(
    state: ChatStatusStripState,
    approval: DashboardApproval?,
    chatPendingApprovals: Int,
    onClose: () -> Unit,
    onAllowOnce: (DashboardApproval) -> Unit,
    onDeny: (DashboardApproval) -> Unit,
    onShowInChat: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenServer: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.chat_dashboard_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            HermesIconButton(
                imageVector = Icons.Filled.Close,
                label = stringResource(R.string.chat_dashboard_close),
                onClick = onClose,
            )
        }
        ApprovalsSection(
            approvals = state.approvals,
            approval = approval,
            chatPendingApprovals = chatPendingApprovals,
            onAllowOnce = onAllowOnce,
            onDeny = onDeny,
            onShowInChat = onShowInChat,
        )
        state.server?.let { ServerSection(server = it, onOpenServer = onOpenServer) }
        state.taskColumns?.let { TasksSection(columns = it, onOpenTasks = onOpenTasks) }
        DashboardWidgetsSection()
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ApprovalsSection(
    approvals: StripApprovals,
    approval: DashboardApproval?,
    chatPendingApprovals: Int,
    onAllowOnce: (DashboardApproval) -> Unit,
    onDeny: (DashboardApproval) -> Unit,
    onShowInChat: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.chat_strip_approvals))
        if (approval == null) {
            Text(
                text = stringResource(R.string.chat_dashboard_no_approval),
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = approval.info.summary() ?: stringResource(R.string.chat_dashboard_approval_fallback),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExpressiveOutlinedButton(onClick = { onDeny(approval) }) {
                            Text(stringResource(R.string.chat_dashboard_deny))
                        }
                        ExpressiveButton(onClick = { onAllowOnce(approval) }) {
                            Text(stringResource(R.string.chat_dashboard_allow_once))
                        }
                    }
                    ExpressiveTextButton(onClick = onShowInChat) {
                        Text(stringResource(R.string.chat_dashboard_show_in_chat))
                    }
                }
            }
        }
        when (approvals) {
            is StripApprovals.Count -> {
                val elsewhere = approvals.pending - chatPendingApprovals
                if (elsewhere > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.chat_dashboard_approvals_elsewhere, elsewhere, elsewhere),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StripApprovals.Unknown ->
                Text(
                    text = stringResource(R.string.chat_dashboard_approvals_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            StripApprovals.NotAvailable -> Unit
        }
    }
}

@Composable
private fun ServerSection(
    server: StripServerStats,
    onOpenServer: () -> Unit,
) {
    val rings =
        listOfNotNull(
            server.cpu?.let { R.string.chat_strip_cpu to it },
            server.ram?.let { R.string.chat_strip_ram to it },
            server.disk?.let { R.string.chat_strip_disk to it },
        )
    if (rings.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.chat_dashboard_server))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            rings.forEach { (labelRes, fraction) ->
                LoadRing(label = stringResource(labelRes), fraction = fraction)
            }
        }
        ExpressiveTextButton(onClick = onOpenServer) {
            Text(stringResource(R.string.chat_dashboard_open_server))
        }
    }
}

@Composable
private fun LoadRing(
    label: String,
    fraction: Float,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(contentAlignment = Alignment.Center) {
            ExpressiveCircularProgressIndicator(
                progress = { fraction },
                color = loadColor(fraction),
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = stringResource(R.string.chat_strip_percent, (fraction * 100).toInt()),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TasksSection(
    columns: List<StripTaskColumn>,
    onOpenTasks: () -> Unit,
) {
    val palette = taskColumnPalette()
    val filled = columns.filter { it.count > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(stringResource(R.string.chat_strip_tasks))
        TaskSegmentBar(columns = columns, modifier = Modifier.fillMaxWidth().height(10.dp))
        if (filled.isEmpty()) {
            Text(
                text = stringResource(R.string.chat_dashboard_no_tasks),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        filled.forEachIndexed { index, column ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(palette[index % palette.size]),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = column.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = column.count.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        ExpressiveTextButton(onClick = onOpenTasks) {
            Text(stringResource(R.string.chat_dashboard_open_tasks))
        }
    }
}

private const val SWIPE_DP = 48
private const val EDGE_WIDTH_DP = 20
private const val EDGE_HEIGHT_FRACTION = 0.5f
private const val SCRIM_ALPHA = 0.32f
private const val PANEL_WIDTH_FRACTION = 0.86f
private const val PANEL_MAX_WIDTH_DP = 380
