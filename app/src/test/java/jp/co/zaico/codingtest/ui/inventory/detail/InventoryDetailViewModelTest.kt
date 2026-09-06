package jp.co.zaico.codingtest.ui.inventory.detail

import androidx.lifecycle.SavedStateHandle
import jp.co.zaico.codingtest.domain.company.CompanyIdResult
import jp.co.zaico.codingtest.domain.company.CompanyRepositoryException
import jp.co.zaico.codingtest.domain.inventory.Inventory
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryDetailViewModelTest {

    @Test
    fun Navigation引数に有効なIDがある場合_在庫を1回取得する() = runMainDispatcherTest {
        val repository = RecordingInventoryRepository()
        val viewModel = InventoryDetailViewModel(
            SavedStateHandle(mapOf("inventoryId" to "77")),
            repository
        )

        runCurrent()

        assertEquals(listOf(77), repository.requestedIds)
        assertEquals(InventoryDetailLoadState.Success, viewModel.uiState.value.loadState)
        assertEquals(Inventory(77, "Shelf", "1"), viewModel.uiState.value.inventory)
    }

    @Test
    fun Navigation引数が欠落または不正な場合_APIを呼ばず失敗状態にする() = runMainDispatcherTest {
        listOf(null, "", "abc", "0", "-1").forEach { rawId ->
            val repository = RecordingInventoryRepository()
            val viewModel = InventoryDetailViewModel(
                SavedStateHandle(rawId?.let { mapOf("inventoryId" to it) } ?: emptyMap()),
                repository
            )

            assertTrue(repository.requestedIds.isEmpty())
            assertEquals(InventoryDetailLoadState.Failure, viewModel.uiState.value.loadState)
            assertEquals(InventoryDetailError.InvalidId, viewModel.uiState.value.error)
            assertTrue(viewModel.uiState.value.errorNotificationId != null)
        }
    }

    @Test
    fun 拠点取得が空の場合_画面用の失敗状態と通知を保持する() = runMainDispatcherTest {
        val repository = RecordingInventoryRepository(
            failure = CompanyRepositoryException(CompanyIdResult.Empty)
        )
        val viewModel = InventoryDetailViewModel(
            SavedStateHandle(mapOf("inventoryId" to "77")),
            repository
        )

        runCurrent()

        assertEquals(InventoryDetailLoadState.Failure, viewModel.uiState.value.loadState)
        assertEquals(InventoryDetailError.CompanyEmpty, viewModel.uiState.value.error)
        val notificationId = requireNotNull(viewModel.uiState.value.errorNotificationId)
        viewModel.consumeErrorNotification(notificationId)
        assertNull(viewModel.uiState.value.errorNotificationId)
    }

    private class RecordingInventoryRepository(
        private val failure: Throwable? = null
    ) : InventoryRepository {
        val requestedIds = mutableListOf<Int>()

        override suspend fun getInventories(): List<Inventory> = emptyList()

        override suspend fun getInventory(inventoryId: Int): Inventory {
            requestedIds += inventoryId
            failure?.let { throw it }
            return Inventory(inventoryId, "Shelf", "1")
        }
    }

    private fun runMainDispatcherTest(block: suspend TestScope.() -> Unit) {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) { block() }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
