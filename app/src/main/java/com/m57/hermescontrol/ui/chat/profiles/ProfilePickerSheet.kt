package com.m57.hermescontrol.ui.chat.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m57.hermescontrol.R
import com.m57.hermescontrol.applock.ModalBottomSheet
import com.m57.hermescontrol.profilelock.ProfileLock
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import com.m57.hermescontrol.ui.common.ExpressiveCircularProgressIndicator
import com.m57.hermescontrol.ui.common.ExpressiveTextButton

/**
 * Composer's profile picker. Lists the server's agent profiles with the model
 * each one uses; picking one switches to it, which starts a fresh chat there.
 * The last row keeps the old pill's job: changing this chat's model.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePickerSheet(
    viewModel: ProfilePickerViewModel,
    activeProfile: String,
    onChangeModel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listColors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    val lockState by ProfileLock.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
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
                text = stringResource(R.string.chat_profile_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.chat_profile_picker_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            when {
                state.isLoading && state.profiles.isEmpty() -> {
                    ExpressiveCircularProgressIndicator(modifier = Modifier.padding(24.dp).size(32.dp))
                }

                state.loadFailed && state.profiles.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.chat_profile_picker_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                    ExpressiveTextButton(
                        onClick = viewModel::load,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) { Text(stringResource(R.string.action_retry)) }
                }
            }
            if (state.switchFailed) {
                Text(
                    text = stringResource(R.string.chat_profile_picker_switch_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalHermesStatusColors.current.error,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            state.profiles.forEach { profile ->
                val active = profile.name == activeProfile
                val allowed = !lockState.locked || ProfileLock.allowsActiveProfile(profile.name)
                Surface(
                    onClick = {
                        if (active) onDismiss() else viewModel.switchTo(profile.name, onDone = onDismiss)
                    },
                    enabled = allowed && state.switchingTo == null,
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = profile.label,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                        supportingContent = {
                            val detail =
                                if (profile.isMixture) {
                                    stringResource(R.string.chat_profile_picker_mixture)
                                } else {
                                    profile.model
                                }
                            detail?.let { Text(it) }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = if (profile.isMixture) Icons.Filled.Hub else Icons.Filled.AccountCircle,
                                contentDescription = null,
                            )
                        },
                        trailingContent = {
                            when {
                                state.switchingTo == profile.name -> {
                                    ExpressiveCircularProgressIndicator(modifier = Modifier.size(20.dp))
                                }

                                active -> {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = stringResource(R.string.chat_profile_picker_active),
                                    )
                                }
                            }
                        },
                        colors = listColors,
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Surface(onClick = onChangeModel) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.chat_profile_picker_change_model)) },
                    supportingContent = { Text(stringResource(R.string.chat_profile_picker_change_model_hint)) },
                    leadingContent = { Icon(Icons.Filled.Tune, contentDescription = null) },
                    colors = listColors,
                )
            }
        }
    }
}
