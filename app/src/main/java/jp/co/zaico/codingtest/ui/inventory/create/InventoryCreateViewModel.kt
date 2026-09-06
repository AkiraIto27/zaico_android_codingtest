package jp.co.zaico.codingtest.ui.inventory.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.zaico.codingtest.domain.inventory.InventoryCreator
import jp.co.zaico.codingtest.domain.result.CreateInventoryResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * タイトルの入力検証と在庫作成画面のUI状態を管理するViewModel。
 *
 * 旧クラス名: `AddViewModel`
 */
@HiltViewModel
class InventoryCreateViewModel @Inject constructor(
    private val inventoryCreator: InventoryCreator
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(InventoryCreateUiState())
    val uiState: StateFlow<InventoryCreateUiState> = mutableUiState.asStateFlow()
    private var notificationGeneration = 0L
    private var emptyCompanyToastShown = false

    fun updateTitle(title: String) {
        if (mutableUiState.value.title == title) return
        mutableUiState.update {
            it.copy(
                title = title,
                titleError = null,
                requestError = null,
                requestErrorNotificationId = null
            )
        }
    }

    fun submit() {
        val currentState = mutableUiState.value
        if (currentState.isSubmitting || currentState.createdInventoryId != null) return
        val titleError = when {
            currentState.title.isBlank() -> InventoryCreateTitleError.Required
            currentState.title.length > MAX_TITLE_LENGTH -> InventoryCreateTitleError.TooLong
            else -> null
        }
        if (titleError != null) {
            mutableUiState.update {
                it.copy(titleError = titleError, requestError = null, requestErrorNotificationId = null)
            }
            return
        }
        mutableUiState.update {
            it.copy(
                isSubmitting = true,
                titleError = null,
                requestError = null,
                requestErrorNotificationId = null
            )
        }
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            executeSubmission(currentState.title)
        }
    }

    private suspend fun executeSubmission(title: String) {
        try {
            when (val result = inventoryCreator.createInventory(title)) {
                is CreateInventoryResult.Success -> mutableUiState.update {
                    it.copy(
                        createdInventoryId = result.inventoryId,
                        requestError = null,
                        successNotificationId = ++notificationGeneration
                    )
                }
                is CreateInventoryResult.HttpFailure -> mutableUiState.update {
                    it.copy(
                        requestError = InventoryCreateRequestError.Http,
                        requestErrorNotificationId = ++notificationGeneration
                    )
                }
                CreateInventoryResult.ConfigurationFailure -> mutableUiState.update {
                    it.copy(
                        requestError = InventoryCreateRequestError.Configuration,
                        requestErrorNotificationId = ++notificationGeneration
                    )
                }
                CreateInventoryResult.EmptyCompany -> mutableUiState.update {
                    it.copy(
                        requestError = InventoryCreateRequestError.EmptyCompany,
                        requestErrorNotificationId = ++notificationGeneration,
                        emptyCompanyToastNotificationId = if (emptyCompanyToastShown) {
                            null
                        } else {
                            emptyCompanyToastShown = true
                            ++notificationGeneration
                        }
                    )
                }
                CreateInventoryResult.DecodeFailure -> mutableUiState.update {
                    it.copy(
                        requestError = InventoryCreateRequestError.InvalidResponse,
                        requestErrorNotificationId = ++notificationGeneration
                    )
                }
                CreateInventoryResult.NetworkFailure -> mutableUiState.update {
                    it.copy(
                        requestError = InventoryCreateRequestError.Network,
                        requestErrorNotificationId = ++notificationGeneration
                    )
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            mutableUiState.update {
                it.copy(
                    requestError = InventoryCreateRequestError.Network,
                    requestErrorNotificationId = ++notificationGeneration
                )
            }
        } finally {
            mutableUiState.update { it.copy(isSubmitting = false) }
        }
    }

    fun consumeRequestErrorNotification(notificationId: Long) {
        mutableUiState.update { state ->
            if (state.requestErrorNotificationId == notificationId) {
                state.copy(requestErrorNotificationId = null)
            } else {
                state
            }
        }
    }

    fun consumeEmptyCompanyToast(notificationId: Long) {
        mutableUiState.update { state ->
            if (state.emptyCompanyToastNotificationId == notificationId) {
                state.copy(emptyCompanyToastNotificationId = null)
            } else {
                state
            }
        }
    }

    fun consumeSuccessNotification(notificationId: Long) {
        mutableUiState.update { state ->
            if (state.successNotificationId == notificationId) {
                state.copy(successNotificationId = null)
            } else {
                state
            }
        }
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 200
    }
}
