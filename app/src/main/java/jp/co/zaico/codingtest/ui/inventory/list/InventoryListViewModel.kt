package jp.co.zaico.codingtest.ui.inventory.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.zaico.codingtest.domain.company.CompanyIdResult
import jp.co.zaico.codingtest.domain.company.CompanyRepositoryException
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 在庫一覧の取得契機と画面状態を管理するViewModel。
 *
 * 旧クラス名: `FirstViewModel`
 */
@HiltViewModel
class InventoryListViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {

    private val mutableUiState = MutableStateFlow(InventoryListUiState())
    val uiState: StateFlow<InventoryListUiState> = mutableUiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadGeneration = 0L
    private var notificationGeneration = 0L
    private var hasResumed = false
    private var refreshOnResume = false
    private var refreshRequestedSincePause = false
    private var lastCreateEventId: Long? = null

    init {
        startLoad(initial = true)
    }

    fun onScreenResumed() {
        if (!hasResumed) {
            hasResumed = true
            return
        }
        if (refreshOnResume && !refreshRequestedSincePause) {
            startLoad(initial = false)
        }
        refreshOnResume = false
        refreshRequestedSincePause = false
    }

    fun onScreenPaused(isChangingConfigurations: Boolean) {
        if (!isChangingConfigurations) {
            refreshOnResume = true
            refreshRequestedSincePause = false
        }
    }

    fun onInventoryCreated(eventId: Long) {
        if (lastCreateEventId == eventId) return
        lastCreateEventId = eventId
        startLoad(initial = false)
        refreshRequestedSincePause = true
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

    private fun startLoad(initial: Boolean) {
        loadJob?.cancel()
        val generation = ++loadGeneration
        mutableUiState.update { state ->
            state.copy(
                loadState = if (initial) {
                    InventoryListLoadState.Loading
                } else {
                    InventoryListLoadState.Refreshing
                },
                error = null,
                errorNotificationId = null
            )
        }
        loadJob = viewModelScope.launch {
            try {
                val inventories = inventoryRepository.getInventories()
                if (generation != loadGeneration) return@launch
                mutableUiState.update { state ->
                    state.copy(
                        inventories = inventories,
                        loadState = if (inventories.isEmpty()) {
                            InventoryListLoadState.Empty
                        } else {
                            InventoryListLoadState.Content
                        },
                        error = null,
                        errorNotificationId = null
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (companyException: CompanyRepositoryException) {
                if (generation != loadGeneration) return@launch
                val error = if (companyException.result == CompanyIdResult.Empty) {
                    InventoryListError.CompanyEmpty
                } else {
                    InventoryListError.LoadFailed
                }
                publishFailure(generation, error)
            } catch (_: Exception) {
                if (generation != loadGeneration) return@launch
                publishFailure(generation, InventoryListError.LoadFailed)
            }
        }
    }

    private fun publishFailure(generation: Long, error: InventoryListError) {
        if (generation != loadGeneration) return
        mutableUiState.update { state ->
            state.copy(
                loadState = InventoryListLoadState.Failure,
                error = error,
                errorNotificationId = ++notificationGeneration
            )
        }
    }
}
