package jp.co.zaico.codingtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AddTitleError {
    Required,
    TooLong
}

enum class AddRequestError {
    Configuration,
    EmptyCompany,
    Http,
    InvalidResponse,
    Network
}

data class AddUiState(
    val title: String = "",
    val isSubmitting: Boolean = false,
    val titleError: AddTitleError? = null,
    val requestError: AddRequestError? = null,
    val createdInventoryId: Long? = null
)

class AddViewModel(
    private val inventoryCreator: InventoryCreator
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(AddUiState())
    val uiState: StateFlow<AddUiState> = mutableUiState.asStateFlow()

    fun updateTitle(title: String) {
        if (mutableUiState.value.title == title) return
        mutableUiState.update {
            it.copy(
                title = title,
                titleError = null,
                requestError = null,
                createdInventoryId = null
            )
        }
    }

    fun submit() {
        val currentState = mutableUiState.value
        if (currentState.isSubmitting || currentState.createdInventoryId != null) return

        val titleError = when {
            currentState.title.isBlank() -> AddTitleError.Required
            currentState.title.length > MAX_TITLE_LENGTH -> AddTitleError.TooLong
            else -> null
        }
        if (titleError != null) {
            mutableUiState.update {
                it.copy(titleError = titleError, requestError = null, createdInventoryId = null)
            }
            return
        }

        mutableUiState.update {
            it.copy(isSubmitting = true, titleError = null, requestError = null, createdInventoryId = null)
        }

        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            executeSubmission(currentState.title)
        }
    }

    private suspend fun executeSubmission(title: String) {
        try {
            when (val result = inventoryCreator.createInventory(title)) {
                is CreateInventoryResult.Success -> mutableUiState.update {
                    it.copy(createdInventoryId = result.inventoryId, requestError = null)
                }
                is CreateInventoryResult.HttpFailure -> mutableUiState.update {
                    it.copy(requestError = AddRequestError.Http)
                }
                CreateInventoryResult.ConfigurationFailure -> mutableUiState.update {
                    it.copy(requestError = AddRequestError.Configuration)
                }
                CreateInventoryResult.EmptyCompany -> mutableUiState.update {
                    it.copy(requestError = AddRequestError.EmptyCompany)
                }
                CreateInventoryResult.DecodeFailure -> mutableUiState.update {
                    it.copy(requestError = AddRequestError.InvalidResponse)
                }
                CreateInventoryResult.NetworkFailure -> mutableUiState.update {
                    it.copy(requestError = AddRequestError.Network)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            mutableUiState.update { it.copy(requestError = AddRequestError.Network) }
        } finally {
            mutableUiState.update { it.copy(isSubmitting = false) }
        }
    }

    override fun onCleared() {
        inventoryCreator.close()
        super.onCleared()
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 200
    }
}
