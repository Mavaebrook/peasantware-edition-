package com.m57.hermescontrol.ui.chat.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiRead
import com.m57.hermescontrol.data.session.ProfileSwitchCoordinator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One agent profile as the composer's picker lists it. */
data class PickerProfile(
    val name: String,
    val label: String,
    val model: String?,
    /** True for a profile that answers with a mix of models (Hermes MoA). */
    val isMixture: Boolean,
)

data class ProfilePickerState(
    val profiles: List<PickerProfile> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val switchingTo: String? = null,
    val switchFailed: Boolean = false,
)

/** Loads the server's agent profiles for the composer and switches the active one. */
class ProfilePickerViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfilePickerState())
    val state: StateFlow<ProfilePickerState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(isLoading = true, loadFailed = false, switchFailed = false) }
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { safeApiRead { ApiClient.hermesApi.getProfiles() } }
            val hidden = runCatching { AuthManager.getHiddenProfiles().toSet() }.getOrDefault(emptySet())
            _state.update {
                when (result) {
                    is NetworkResult.Success ->
                        it.copy(
                            isLoading = false,
                            profiles = result.data.profiles.toPickerProfiles(hidden),
                        )
                    is NetworkResult.Failure -> it.copy(isLoading = false, loadFailed = true)
                }
            }
        }
    }

    /** Switches the active profile; the chat then starts fresh in it. Calls [onDone] once it took. */
    fun switchTo(
        name: String,
        onDone: () -> Unit,
    ) {
        if (_state.value.switchingTo != null) return
        _state.update { it.copy(switchingTo = name, switchFailed = false) }
        viewModelScope.launch {
            val result = ProfileSwitchCoordinator.switchProfile(name)
            _state.update { it.copy(switchingTo = null, switchFailed = result is NetworkResult.Failure) }
            if (result is NetworkResult.Success) onDone()
        }
    }
}

internal const val MIXTURE_PROVIDER = "moa"

internal fun List<ProfileInfo>.toPickerProfiles(hidden: Set<String>): List<PickerProfile> =
    filter { it.name !in hidden }
        .map { profile ->
            PickerProfile(
                name = profile.name,
                label = profile.display_name?.takeIf { it.isNotBlank() } ?: profile.name,
                model = profile.model?.takeIf { it.isNotBlank() },
                isMixture = profile.provider.equals(MIXTURE_PROVIDER, ignoreCase = true),
            )
        }

/** The active profile's name, read the same way the rest of the app scopes requests. */
internal fun activeProfileName(activeProfileId: String?): String =
    activeProfileId?.takeIf { it.isNotBlank() } ?: AuthManager.DEFAULT_PROFILE_ID
