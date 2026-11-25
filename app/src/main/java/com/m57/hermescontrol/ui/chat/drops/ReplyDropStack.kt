package com.m57.hermescontrol.ui.chat.drops

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.NavigationController
import com.m57.hermescontrol.R
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.ui.common.ExpressiveOutlinedButton
import com.m57.hermescontrol.ui.common.ExpressiveTextButton
import com.m57.hermescontrol.ui.common.HermesIconButton
import kotlinx.coroutines.delay
import java.util.Date

private const val SHOW_MILLIS = 6_000L
private const val AFTER_TOUCH_MILLIS = 3_000L

/**
 * Cards that drop down over the top of the chat when an agent finishes a reply in
 * another chat. Open jumps to that chat; the close button, Later, a swipe up or six
 * seconds put a card away, and a finger held on it keeps it there.
 */
@Composable
fun ReplyDropStack(
    currentSessionId: String?,
    titleFor: (String) -> String?,
    modifier: Modifier = Modifier,
) {
    val recorder = remember { ReplyDropRecorder() }
    var drops by remember { mutableStateOf(emptyList<ReplyDrop>()) }
    val current by rememberUpdatedState(currentSessionId)

    LaunchedEffect(recorder) {
        HermesWsClient.events.collect { event ->
            val drop = recorder.onEvent(event) ?: return@collect
            if (drop.sessionId != current) drops = drops.withDrop(drop)
        }
    }
    LaunchedEffect(currentSessionId) {
        drops = drops.filterNot { it.sessionId == currentSessionId }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        drops.forEach { drop ->
            key(drop.key) {
                ReplyDropCard(
                    drop = drop,
                    title = titleFor(drop.sessionId)?.takeIf { it.isNotBlank() },
                    onOpen = { NavigationController.openChatSessionFromNotification(drop.sessionId) },
                    onGone = { drops = drops.filterNot { it.key == drop.key } },
                )
            }
        }
    }
}

@Composable
private fun ReplyDropCard(
    drop: ReplyDrop,
    title: String?,
    onOpen: () -> Unit,
    onGone: () -> Unit,
) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    var held by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    val swipeUpPx = with(LocalDensity.current) { 24.dp.toPx() }
    val context = LocalContext.current
    val time = remember(drop.atMillis) { DateFormat.getTimeFormat(context).format(Date(drop.atMillis)) }
    val name = title ?: stringResource(R.string.reply_drop_untitled)

    fun dismiss() {
        visible.targetState = false
    }

    LaunchedEffect(held, touched) {
        if (!held) {
            delay(if (touched) AFTER_TOUCH_MILLIS else SHOW_MILLIS)
            dismiss()
        }
    }
    LaunchedEffect(visible.currentState, visible.isIdle) {
        if (visible.isIdle && !visible.currentState && !visible.targetState) onGone()
    }

    AnimatedVisibility(
        visibleState = visible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 6.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .pointerInput(drop.key) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            held = true
                            touched = true
                            var dy = 0f
                            do {
                                val event = awaitPointerEvent()
                                event.changes.firstOrNull { it.id == down.id }?.let { dy += it.positionChange().y }
                                if (dy < -swipeUpPx) dismiss()
                            } while (event.changes.any { it.pressed })
                            held = false
                        }
                    },
        ) {
            Row(modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 8.dp, end = 4.dp)) {
                Box(
                    modifier =
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = time,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text =
                            when {
                                drop.failed -> stringResource(R.string.reply_drop_failed)
                                drop.preview.isEmpty() -> stringResource(R.string.reply_drop_new_reply)
                                else -> drop.preview
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (drop.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        ExpressiveOutlinedButton(onClick = {
                            dismiss()
                            onOpen()
                        }) { Text(stringResource(R.string.reply_drop_open)) }
                        ExpressiveTextButton(onClick = ::dismiss) { Text(stringResource(R.string.reply_drop_later)) }
                    }
                }
                HermesIconButton(
                    imageVector = Icons.Filled.Close,
                    label = stringResource(R.string.reply_drop_dismiss),
                    onClick = ::dismiss,
                )
            }
        }
    }
}
