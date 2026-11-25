package com.m57.hermescontrol.ui.chat.dashboard

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.ChatScreen
import com.m57.hermescontrol.DrawerSection
import com.m57.hermescontrol.R
import com.m57.hermescontrol.ScreenDefinition
import com.m57.hermescontrol.ScreenRegistry
import com.m57.hermescontrol.applock.ModalBottomSheet
import com.m57.hermescontrol.profilelock.ProfileLock

/**
 * Keeps the user's view shortcuts. Each is stored by its screen key's name,
 * which a `data object` key fixes at compile time, so it survives minification.
 */
class ChatTabStore(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs
            .getString(KEY_TABS, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            .orEmpty()

    fun save(ids: List<String>) {
        prefs.edit().putString(KEY_TABS, ids.joinToString(SEPARATOR)).apply()
    }

    private companion object {
        const val PREFS_NAME = "chat_view_tabs"
        const val KEY_TABS = "tabs"
        const val SEPARATOR = ","
    }
}

/** Screens that can be pinned as tabs: every drawer view except Chat, which is always first. */
internal fun tabbableScreens(): List<ScreenDefinition> =
    ScreenRegistry.ALL_SCREENS.filter { it.drawerSection != null && it.key != ChatScreen }

internal fun ScreenDefinition.tabId(): String = key.toString()

internal fun resolveTabs(ids: List<String>): List<ScreenDefinition> {
    val byId = tabbableScreens().associateBy { it.tabId() }
    return ids.distinct().mapNotNull { byId[it] }
}

/** The user's tabs, loaded once and written back on every change. */
class ChatTabsState(
    private val store: ChatTabStore,
) {
    val tabs: SnapshotStateList<ScreenDefinition> =
        mutableStateListOf<ScreenDefinition>().apply { addAll(resolveTabs(store.load())) }

    fun add(screen: ScreenDefinition) {
        if (tabs.none { it.key == screen.key }) {
            tabs.add(screen)
            persist()
        }
    }

    fun remove(screen: ScreenDefinition) {
        if (tabs.removeAll { it.key == screen.key }) persist()
    }

    private fun persist() = store.save(tabs.map { it.tabId() })
}

@Composable
fun rememberChatTabsState(): ChatTabsState {
    val context = LocalContext.current
    return remember { ChatTabsState(ChatTabStore(context)) }
}

/**
 * Browser-style tab row under the status strip. Chat is pinned and selected;
 * the other tabs are shortcuts that open their view, and + adds one.
 */
@Composable
fun ChatViewTabs(
    tabs: List<ScreenDefinition>,
    onOpen: (ScreenDefinition) -> Unit,
    onRemove: (ScreenDefinition) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lockState by ProfileLock.state.collectAsState()
    val visibleTabs = tabs.filter { !lockState.locked || ProfileLock.allows(it.key) }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ViewTab(
            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = stringResource(R.string.screen_chat),
            selected = true,
            onClick = {},
            onClose = null,
        )
        visibleTabs.forEach { screen ->
            ViewTab(
                icon = { Icon(screen.icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                label = stringResource(screen.labelRes),
                selected = false,
                onClick = { onOpen(screen) },
                onClose = { onRemove(screen) },
            )
        }
        IconButton(onClick = onAddClick, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.chat_tabs_add),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun ViewTab(
    icon: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = !selected,
        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
        color = if (selected) scheme.secondaryContainer else scheme.surfaceVariant,
        contentColor = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = if (onClose == null) 10.dp else 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 7.dp),
            )
            if (onClose != null) {
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.chat_tabs_close, label),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/** Sheet listing every view that isn't a tab yet, grouped like the menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTabSheet(
    existing: List<ScreenDefinition>,
    onPick: (ScreenDefinition) -> Unit,
    onDismiss: () -> Unit,
) {
    val lockState by ProfileLock.state.collectAsState()
    val choices =
        tabbableScreens().filter { screen ->
            existing.none { it.key == screen.key } && (!lockState.locked || ProfileLock.allows(screen.key))
        }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_tabs_add_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (choices.isEmpty()) {
                Text(
                    text = stringResource(R.string.chat_tabs_all_added),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            for (section in DrawerSection.entries) {
                val entries = choices.filter { it.drawerSection == section }
                if (entries.isEmpty()) continue
                Text(
                    text = stringResource(section.titleRes).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
                )
                entries.forEach { screen ->
                    Surface(onClick = { onPick(screen) }) {
                        ListItem(
                            headlineContent = { Text(stringResource(screen.labelRes)) },
                            leadingContent = { Icon(screen.icon, contentDescription = null) },
                        )
                    }
                }
            }
        }
    }
}
