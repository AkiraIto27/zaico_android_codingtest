package jp.co.zaico.codingtest.ui.inventory.list

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InventoryListViewModelTest {

    @Test
    fun 初期取得後に同じ画面を再開した場合_GETを1回だけ実行する() = runMainDispatcherTest {
        val repository = RecordingInventoryRepository(
            { listOf(Inventory(1, "Desk", "3")) }
        )
        val viewModel = InventoryListViewModel(repository)

        runCurrent()
        viewModel.onScreenResumed()
        runCurrent()

        assertEquals(1, repository.listCallCount)
        assertEquals(InventoryListLoadState.Content, viewModel.uiState.value.loadState)
    }

    @Test
    fun 実際の背景復帰が発生した場合_一覧を1回更新する() = runMainDispatcherTest {
        val repository = RecordingInventoryRepository(
            { listOf(Inventory(1, "First", "1")) },
            { listOf(Inventory(2, "Second", "2")) }
        )
        val viewModel = InventoryListViewModel(repository)

        runCurrent()
        viewModel.onScreenResumed()
        viewModel.onScreenPaused(isChangingConfigurations = false)
        viewModel.onScreenResumed()
        runCurrent()

        assertEquals(2, repository.listCallCount)
        assertEquals(listOf(Inventory(2, "Second", "2")), viewModel.uiState.value.inventories)
    }

    @Test
    fun 作成結果と復帰通知が同じ更新を示す場合_GETを重複させない() = runMainDispatcherTest {
        val repository = RecordingInventoryRepository(
            { listOf(Inventory(1, "Before", "1")) },
            { listOf(Inventory(2, "After", "2")) }
        )
        val viewModel = InventoryListViewModel(repository)

        runCurrent()
        viewModel.onScreenResumed()
        viewModel.onScreenPaused(isChangingConfigurations = false)
        viewModel.onInventoryCreated(eventId = 100L)
        viewModel.onScreenResumed()
        runCurrent()

        assertEquals(2, repository.listCallCount)
        assertEquals(listOf(Inventory(2, "After", "2")), viewModel.uiState.value.inventories)
    }

    @Test
    fun 新しい更新要求が取得中に発生した場合_古い結果で状態を上書きしない() = runMainDispatcherTest {
        val firstResponse = kotlinx.coroutines.CompletableDeferred<List<Inventory>>()
        val repository = RecordingInventoryRepository(
            { firstResponse.await() },
            { listOf(Inventory(2, "Latest", "2")) }
        )
        val viewModel = InventoryListViewModel(repository)

        runCurrent()
        viewModel.onScreenResumed()
        viewModel.onScreenPaused(isChangingConfigurations = false)
        viewModel.onInventoryCreated(eventId = 101L)
        runCurrent()
        firstResponse.complete(listOf(Inventory(1, "Stale", "1")))
        runCurrent()

        assertEquals(2, repository.listCallCount)
        assertEquals(listOf(Inventory(2, "Latest", "2")), viewModel.uiState.value.inventories)
    }

    @Test
    fun 更新が失敗した場合_既存一覧を保持し通知を再収集で重複させない() = runMainDispatcherTest {
        val repository = RecordingInventoryRepository(
            { listOf(Inventory(1, "Keep", "1")) },
            { throw CompanyRepositoryException(CompanyIdResult.Empty) }
        )
        val viewModel = InventoryListViewModel(repository)

        runCurrent()
        viewModel.onScreenResumed()
        viewModel.onScreenPaused(isChangingConfigurations = false)
        viewModel.onScreenResumed()
        runCurrent()

        val notificationId = viewModel.uiState.value.errorNotificationId
        assertTrue(notificationId != null)
        assertEquals(listOf(Inventory(1, "Keep", "1")), viewModel.uiState.value.inventories)
        assertEquals(InventoryListError.CompanyEmpty, viewModel.uiState.value.error)
        viewModel.consumeErrorNotification(requireNotNull(notificationId))
        assertNull(viewModel.uiState.value.errorNotificationId)
        assertFalse(viewModel.uiState.value.inventories.isEmpty())
    }

    private class RecordingInventoryRepository(
        private vararg val responses: suspend () -> List<Inventory>
    ) : InventoryRepository {
        var listCallCount = 0
            private set

        override suspend fun getInventories(): List<Inventory> =
            responses[listCallCount++].invoke()

        override suspend fun getInventory(inventoryId: Int): Inventory =
            error("Detail is not used by this test")
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
