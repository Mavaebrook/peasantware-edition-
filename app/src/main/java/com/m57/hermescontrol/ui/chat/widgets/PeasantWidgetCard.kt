package com.m57.hermescontrol.ui.chat.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import com.m57.hermescontrol.ui.common.ExpressiveButton
import com.m57.hermescontrol.ui.common.ExpressiveCircularProgressIndicator
import com.m57.hermescontrol.ui.common.ExpressiveOutlinedButton
import com.m57.hermescontrol.ui.common.ExpressiveTextButton
import com.m57.hermescontrol.ui.common.HermesIconButton
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Buttons the card draws itself instead of as plain action buttons. */
internal val HANDLED_ACTIONS = setOf("reply", "send", "save", "open")

/** The draft being written in a mail widget. */
private data class MailDraft(
    val to: String,
    val subject: String,
    val body: String,
    val inReplyTo: String?,
)

/** Splits a To field on commas and semicolons into addresses. */
internal fun splitRecipients(raw: String): List<String> =
    raw.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }

/** Body for the `send` action, in the shape the widget's action expects. */
internal fun sendBody(
    to: List<String>,
    subject: String,
    body: String,
    inReplyTo: String?,
): JsonObject =
    buildJsonObject {
        put("to", JsonArray(to.map(::JsonPrimitive)))
        put("subject", subject)
        put("body", body)
        inReplyTo?.let { put("in_reply_to", it) }
    }

/**
 * Draws one widget block natively. Buttons run on the server through the
 * `peasant-gateway` plugin; a write that needs approval comes back pending and
 * shows in the approvals list. In chat the card offers Add to dashboard, and
 * every card offers Send to TV, which shows it full-screen on the TV.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeasantWidgetCard(
    block: WidgetBlock,
    modifier: Modifier = Modifier,
    inDashboard: Boolean = false,
    onRemoveFromDashboard: (() -> Unit)? = null,
    onUpdated: (WidgetBlock) -> Unit = {},
) {
    var current by remember(block) { mutableStateOf(block) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val pins by DashboardPins.pins.collectAsStateWithLifecycle()
    val pinned = pins.any { it.id == current.id }
    val failedText = stringResource(R.string.widget_action_failed)
    val waitingText = stringResource(R.string.widget_waiting_approval)
    val refreshFailedText = stringResource(R.string.widget_refresh_failed)
    val onTvText = stringResource(R.string.widget_on_tv)
    val tvFailedText = stringResource(R.string.widget_tv_failed)
    val actionIds = current.actions.map { it.id }.toSet()

    fun run(
        action: String,
        body: JsonObject,
        onSuccess: () -> Unit = {},
    ) {
        if (busy) return
        busy = true
        notice = null
        scope.launch {
            when (val result = PeasantWidgets.act(current.id, action, body)) {
                is NetworkResult.Success -> {
                    val reply = result.data
                    notice =
                        when {
                            reply.pendingApproval -> reply.message ?: waitingText
                            reply.ok -> reply.message
                            else -> reply.message ?: failedText
                        }
                    reply.block?.let {
                        current = it
                        DashboardPins.remember(it)
                        onUpdated(it)
                    }
                    if (reply.ok || reply.pendingApproval) onSuccess()
                }

                is NetworkResult.Failure -> {
                    notice = failedText
                }
            }
            busy = false
        }
    }

    fun sendToTv() {
        if (busy) return
        busy = true
        notice = null
        scope.launch {
            notice =
                when (val result = PeasantWidgets.sendToTv(current)) {
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
            busy = false
        }
    }

    fun refresh() {
        if (busy) return
        busy = true
        notice = null
        scope.launch {
            when (val result = PeasantWidgets.fetch(current.id)) {
                is NetworkResult.Success -> {
                    current = result.data
                    DashboardPins.remember(result.data)
                    onUpdated(result.data)
                }

                is NetworkResult.Failure -> {
                    notice = refreshFailedText
                }
            }
            busy = false
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetHeader(block = current, busy = busy, onRefresh = ::refresh)
            if (!current.isOk) {
                Text(
                    text = current.message ?: stringResource(R.string.widget_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = toneColor(if (current.status == WidgetBlock.STATUS_NEEDS_APPROVAL) "warn" else "bad"),
                )
            } else {
                when (val content = current.content()) {
                    is WidgetContent.Stat -> {
                        StatBody(content.data)
                    }

                    is WidgetContent.Bar -> {
                        BarBody(content.data)
                    }

                    is WidgetContent.Items -> {
                        ItemsBody(content.data)
                    }

                    is WidgetContent.Rings -> {
                        RingsBody(content.data)
                    }

                    is WidgetContent.Mail -> {
                        MailBody(
                            data = content.data,
                            canReply = "reply" in actionIds && "send" in actionIds,
                            canCompose = "send" in actionIds,
                            busy = busy,
                            onSend = { draft, done ->
                                run(
                                    action = "send",
                                    body =
                                        sendBody(
                                            to = splitRecipients(draft.to),
                                            subject = draft.subject,
                                            body = draft.body,
                                            inReplyTo = draft.inReplyTo,
                                        ),
                                    onSuccess = done,
                                )
                            },
                        )
                    }

                    is WidgetContent.Doc -> {
                        DocBody(
                            data = content.data,
                            canSave = "save" in actionIds,
                            canOpen = "open" in actionIds,
                            busy = busy,
                            onSave = { text, done ->
                                run(
                                    action = "save",
                                    body =
                                        buildJsonObject {
                                            content.data.docId?.let { put("doc_id", it) }
                                            put("text", text)
                                        },
                                    onSuccess = done,
                                )
                            },
                            onOpen = { docId -> run("open", buildJsonObject { put("doc_id", docId) }) },
                        )
                    }

                    is WidgetContent.Folder -> {
                        FolderBody(
                            data = content.data,
                            canOpen = "open" in actionIds,
                            onOpen = { path ->
                                run("open", buildJsonObject { put("path", JsonArray(path.map(::JsonPrimitive))) })
                            },
                        )
                    }

                    is WidgetContent.Browser -> {
                        BrowserBody(content.data)
                    }

                    WidgetContent.Unknown -> {
                        Text(
                            text = stringResource(R.string.widget_unknown_type, current.type),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            notice?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            val extraActions = current.actions.filter { it.id !in HANDLED_ACTIONS }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                extraActions.forEach { action ->
                    ExpressiveTextButton(onClick = { run(action.id, JsonObject(emptyMap())) }, enabled = !busy) {
                        Text(action.label)
                    }
                }
                ExpressiveTextButton(onClick = { sendToTv() }, enabled = !busy) {
                    Text(stringResource(R.string.widget_send_to_tv))
                }
                if (inDashboard) {
                    onRemoveFromDashboard?.let { remove ->
                        ExpressiveTextButton(onClick = remove) {
                            Text(stringResource(R.string.widget_remove_from_dashboard))
                        }
                    }
                } else if (pinned) {
                    ExpressiveTextButton(onClick = { DashboardPins.unpin(current.id) }) {
                        Text(stringResource(R.string.widget_on_dashboard))
                    }
                } else {
                    ExpressiveTextButton(onClick = { DashboardPins.pin(current) }) {
                        Text(stringResource(R.string.widget_add_to_dashboard))
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetHeader(
    block: WidgetBlock,
    busy: Boolean,
    onRefresh: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = block.title ?: block.id,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            block.source?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (busy) {
            ExpressiveCircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            HermesIconButton(
                imageVector = Icons.Filled.Refresh,
                label = stringResource(R.string.widget_refresh),
                onClick = onRefresh,
            )
        }
    }
}

@Composable
internal fun toneColor(tone: String?): Color {
    val status = LocalHermesStatusColors.current
    return when (tone) {
        "info" -> status.info
        "ok" -> status.success
        "warn" -> status.warning
        "bad" -> status.error
        else -> status.neutral
    }
}

@Composable
private fun StatBody(data: StatData) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = data.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        data.detail?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        data.pct?.let { pct -> FillBar(fraction = (pct / 100.0).toFloat(), color = toneColor(data.tone)) }
    }
}

@Composable
private fun FillBar(
    fraction: Float,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(8.dp)
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
private fun BarBody(data: BarData) {
    val filled = data.segments.filter { it.count > 0 }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surface),
        ) {
            filled.forEach { segment ->
                Box(
                    modifier =
                        Modifier
                            .weight(segment.count.toFloat())
                            .fillMaxHeight()
                            .background(toneColor(segment.tone)),
                )
            }
        }
        data.segments.forEach { segment ->
            LegendRow(color = toneColor(segment.tone), label = segment.label, value = formatCount(segment.count))
        }
    }
}

internal fun formatCount(count: Double): String =
    if (count == Math.floor(count)) count.toLong().toString() else "%.1f".format(count)

@Composable
private fun LegendRow(
    color: Color,
    label: String,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ItemsBody(data: ListData) {
    Column {
        data.items.forEachIndexed { index, item ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(modifier = Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.title, style = MaterialTheme.typography.bodyMedium)
                    item.detail?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item.trailing?.let { Text(text = it, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun RingsBody(data: RingsData) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        data.rings.forEach { ring ->
            val fraction = (ring.pct / 100.0).toFloat().coerceIn(0f, 1f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    ExpressiveCircularProgressIndicator(
                        progress = { fraction },
                        color = toneColor(ring.tone),
                        modifier = Modifier.size(56.dp),
                    )
                    Text(
                        text = stringResource(R.string.chat_strip_percent, ring.pct.toInt()),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(text = ring.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun MailBody(
    data: MailListData,
    canReply: Boolean,
    canCompose: Boolean,
    busy: Boolean,
    onSend: (MailDraft, () -> Unit) -> Unit,
) {
    var draft by remember { mutableStateOf<MailDraft?>(null) }
    val replyPrefix = stringResource(R.string.widget_mail_reply_prefix)
    Column {
        data.items.forEachIndexed { index, mail ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canReply) {
                            draft =
                                MailDraft(
                                    to = mail.from,
                                    subject =
                                        if (mail.subject.startsWith(replyPrefix, ignoreCase = true)) {
                                            mail.subject
                                        } else {
                                            "$replyPrefix ${mail.subject}"
                                        },
                                    body = "",
                                    inReplyTo = mail.id.takeIf { it.isNotBlank() },
                                )
                        }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val weight = if (mail.unread) FontWeight.Bold else FontWeight.Normal
                    Text(
                        text = mail.from,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = weight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = mail.subject,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = weight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                mail.time?.let {
                    Text(
                        text = it.take(10),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val open = draft
        if (open != null) {
            MailDraftEditor(
                draft = open,
                busy = busy,
                onChange = { draft = it },
                onCancel = { draft = null },
                onSend = { onSend(open) { draft = null } },
            )
        } else if (canCompose) {
            ExpressiveTextButton(onClick = { draft = MailDraft(to = "", subject = "", body = "", inReplyTo = null) }) {
                Text(stringResource(R.string.widget_mail_compose))
            }
        }
    }
}

@Composable
private fun MailDraftEditor(
    draft: MailDraft,
    busy: Boolean,
    onChange: (MailDraft) -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = draft.to,
            onValueChange = { onChange(draft.copy(to = it)) },
            label = { Text(stringResource(R.string.widget_mail_to)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.subject,
            onValueChange = { onChange(draft.copy(subject = it)) },
            label = { Text(stringResource(R.string.widget_mail_subject)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = draft.body,
            onValueChange = { onChange(draft.copy(body = it)) },
            label = { Text(stringResource(R.string.widget_mail_body)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpressiveOutlinedButton(onClick = onCancel) { Text(stringResource(R.string.widget_cancel)) }
            ExpressiveButton(
                onClick = onSend,
                enabled = !busy && splitRecipients(draft.to).isNotEmpty() && draft.body.isNotBlank(),
            ) { Text(stringResource(R.string.widget_mail_send)) }
        }
    }
}

@Composable
private fun DocBody(
    data: DocData,
    canSave: Boolean,
    canOpen: Boolean,
    busy: Boolean,
    onSave: (String, () -> Unit) -> Unit,
    onOpen: (String) -> Unit,
) {
    var editing by remember(data.docId, data.text) { mutableStateOf(false) }
    var text by remember(data.docId, data.text) { mutableStateOf(data.text.orEmpty()) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        data.title?.let { Text(text = it, style = MaterialTheme.typography.titleSmall) }
        if (editing) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressiveOutlinedButton(onClick = {
                    text = data.text.orEmpty()
                    editing = false
                }) { Text(stringResource(R.string.widget_cancel)) }
                ExpressiveButton(onClick = { onSave(text) { editing = false } }, enabled = !busy) {
                    Text(stringResource(R.string.widget_doc_save))
                }
            }
        } else {
            data.text?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = DOC_PREVIEW_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (data.editable && canSave && data.text != null) {
                ExpressiveTextButton(onClick = { editing = true }) { Text(stringResource(R.string.widget_doc_edit)) }
            }
        }
        if (data.recent.isNotEmpty()) {
            Text(
                text = stringResource(R.string.widget_doc_recent),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            data.recent.forEach { doc ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = canOpen && doc.id.isNotBlank()) { onOpen(doc.id) }
                            .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = doc.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    doc.whenText?.let { Text(text = it.take(10), style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

@Composable
private fun FolderBody(
    data: FolderData,
    canOpen: Boolean,
    onOpen: (List<String>) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.widget_folder_root),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(enabled = canOpen && data.path.isNotEmpty()) { onOpen(emptyList()) },
            )
            data.path.forEachIndexed { index, part ->
                Text(text = " / ", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = part,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier.clickable(enabled = canOpen && index < data.path.lastIndex) {
                            onOpen(data.path.take(index + 1))
                        },
                )
            }
        }
        data.items.forEach { item ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canOpen && item.isDir) { onOpen(data.path + item.name) }
                        .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (item.isDir) Icons.Filled.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val trailing = item.count?.toString() ?: item.sizeBytes?.let(::formatBytes)
                trailing?.let { Text(text = it, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

internal fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else "%.1f %s".format(value, units[unit])
}

@Composable
private fun BrowserBody(data: BrowserData) {
    val status = LocalHermesStatusColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (data.live) status.success else status.neutral),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = data.url ?: stringResource(R.string.widget_browser_no_page),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        data.steps.takeLast(BROWSER_STEPS_SHOWN).forEach { step ->
            Text(
                text = listOfNotNull(step.t, step.text).joinToString("  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        data.vaultMatch?.let { match ->
            Text(
                text = stringResource(R.string.widget_browser_vault_match, match.label ?: match.handle),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private const val BROWSER_STEPS_SHOWN = 5
private const val DOC_PREVIEW_LINES = 12
