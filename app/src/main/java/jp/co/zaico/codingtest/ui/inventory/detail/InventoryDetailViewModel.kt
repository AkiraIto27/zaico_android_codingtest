package jp.co.zaico.codingtest.ui.inventory.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.zaico.codingtest.domain.company.CompanyIdResult
import jp.co.zaico.codingtest.domain.company.CompanyRepositoryException
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 在庫詳細の取得と画面状態を管理するViewModel。
 *
 * 旧クラス名: `SecondViewModel`
 */
@HiltViewModel
class InventoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(InventoryDetailUiState())
    val uiState: StateFlow<InventoryDetailUiState> = mutableUiState.asStateFlow()

    private var notificationGeneration = 0L

    init {
        loadInventory(savedStateHandle.get<String>(INVENTORY_ID_KEY))
    }

    fun consumeErrorNotification(notificationId: Long) {
        mutableUiState.update { state ->
            if (state.errorNotificationId == notificationId) {
                state.copy(errorNotificationId = null)
            } else {
                state
            }
        }
    }

    private fun loadInventory(rawInventoryId: String?) {
        val inventoryId = rawInventoryId?.toIntOrNull()?.takeIf { it > 0 }
        if (inventoryId == null) {
            mutableUiState.value = InventoryDetailUiState(
                loadState = InventoryDetailLoadState.Failure,
                error = InventoryDetailError.InvalidId,
                errorNotificationId = ++notificationGeneration
            )
            return
        }

        mutableUiState.update {
            it.copy(loadState = InventoryDetailLoadState.Loading, error = null)
        }
        viewModelScope.launch {
            try {
                val inventory = inventoryRepository.getInventory(inventoryId)
                mutableUiState.update {
                    it.copy(
                        inventory = inventory,
                        loadState = InventoryDetailLoadState.Success,
                        error = null,
                        errorNotificationId = null
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (companyException: CompanyRepositoryException) {
                val error = if (companyException.result == CompanyIdResult.Empty) {
                    InventoryDetailError.CompanyEmpty
                } else {
                    InventoryDetailError.LoadFailed
                }
                publishFailure(error)
            } catch (_: Exception) {
                publishFailure(InventoryDetailError.LoadFailed)
            }
        }
    }

    private fun publishFailure(error: InventoryDetailError) {
        mutableUiState.update {
            it.copy(
                loadState = InventoryDetailLoadState.Failure,
                error = error,
                errorNotificationId = ++notificationGeneration
            )
        }
    }

    private companion object {
        const val INVENTORY_ID_KEY = "inventoryId"
    }
}
