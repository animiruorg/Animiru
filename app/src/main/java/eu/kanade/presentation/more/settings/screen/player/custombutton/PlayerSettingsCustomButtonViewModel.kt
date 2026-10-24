package eu.kanade.presentation.more.settings.screen.player.custombutton

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import animiru.feature.mpvfiles.MpvConfig
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mihon.core.metro.AppCoroutineScope
import tachiyomi.domain.custombutton.interactor.CreateCustomButton
import tachiyomi.domain.custombutton.interactor.DeleteCustomButton
import tachiyomi.domain.custombutton.interactor.GetCustomButtons
import tachiyomi.domain.custombutton.interactor.ReorderCustomButton
import tachiyomi.domain.custombutton.interactor.ToggleFavoriteCustomButton
import tachiyomi.domain.custombutton.interactor.UpdateCustomButton
import tachiyomi.domain.custombutton.model.CustomButton
import tachiyomi.domain.custombutton.model.CustomButtonUpdate
import tachiyomi.i18n.MR

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
class PlayerSettingsCustomButtonViewModel(
    @AppCoroutineScope private val scope: CoroutineScope,
    private val getCustomButtons: GetCustomButtons,
    private val createCustomButton: CreateCustomButton,
    private val deleteCustomButton: DeleteCustomButton,
    private val updateCustomButton: UpdateCustomButton,
    private val reorderCustomButton: ReorderCustomButton,
    private val toggleFavoriteCustomButton: ToggleFavoriteCustomButton,
    // AM -->
    private val mpvConfig: MpvConfig,
    // <-- AM
) : ViewModel() {

    val state: StateFlow<CustomButtonScreenState>
        field = MutableStateFlow<CustomButtonScreenState>(CustomButtonScreenState.Loading)

    private val _events: Channel<CustomButtonEvent> = Channel()
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            getCustomButtons.subscribeAll()
                .collectLatest { customButtons ->
                    state.update {
                        CustomButtonScreenState.Success(
                            customButtons = customButtons,
                        )
                    }
                }
        }

        // AM -->
        viewModelScope.launch(Dispatchers.IO) {
            getCustomButtons.subscribeAll()
                .distinctUntilChanged()
                .collectLatest { customButtons ->
                    mpvConfig.setupCustomButtons(customButtons)
                }
        }
        // <-- AM
    }

    fun createCustomButton(name: String, content: String, longPressContent: String, onStartup: String) {
        scope.launch {
            when (createCustomButton.await(name, content, longPressContent, onStartup)) {
                is CreateCustomButton.Result.InternalError -> _events.send(
                    CustomButtonEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun togglePrimaryButton(customButton: CustomButton) {
        scope.launch {
            when (toggleFavoriteCustomButton.await(customButton)) {
                is ToggleFavoriteCustomButton.Result.InternalError -> _events.send(
                    CustomButtonEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun editCustomButton(customButtonUpdate: CustomButtonUpdate) {
        scope.launch {
            when (updateCustomButton.await(customButtonUpdate)) {
                is UpdateCustomButton.Result.InternalError -> _events.send(
                    CustomButtonEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun deleteCustomButton(customButton: CustomButton) {
        scope.launch {
            when (deleteCustomButton.await(customButton.id)) {
                is DeleteCustomButton.Result.InternalError -> _events.send(
                    CustomButtonEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun changeOrder(customButton: CustomButton, newIndex: Int) {
        scope.launch {
            when (reorderCustomButton.changeOrder(customButton, newIndex)) {
                is ReorderCustomButton.Result.InternalError -> _events.send(
                    CustomButtonEvent.InternalError,
                )
                else -> {}
            }
        }
    }

    fun showDialog(dialog: CustomButtonDialog) {
        state.update {
            when (it) {
                CustomButtonScreenState.Loading -> it
                is CustomButtonScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        state.update {
            when (it) {
                CustomButtonScreenState.Loading -> it
                is CustomButtonScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed interface CustomButtonDialog {
    data object Create : CustomButtonDialog
    data class Edit(val customButton: CustomButton) : CustomButtonDialog
    data class Delete(val customButton: CustomButton) : CustomButtonDialog
}

sealed interface CustomButtonEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : CustomButtonEvent
    data object InternalError : LocalizedMessage(MR.strings.internal_error)
}

sealed interface CustomButtonScreenState {
    @Immutable
    data object Loading : CustomButtonScreenState

    @Immutable
    data class Success(
        val customButtons: List<CustomButton>,
        val dialog: CustomButtonDialog? = null,
    ) : CustomButtonScreenState {
        val isEmpty: Boolean
            get() = customButtons.isEmpty()
    }
}
