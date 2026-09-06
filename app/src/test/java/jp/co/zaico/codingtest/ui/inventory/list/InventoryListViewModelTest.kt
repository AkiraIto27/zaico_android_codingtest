package jp.co.zaico.codingtest.ui.inventory.list

import jp.co.zaico.codingtest.domain.inventory.Inventory
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("NonAsciiCharacters", "TestFunctionName")
class InventoryListViewModelTest {
    @Test
    fun ViewModelを生成した場合_取得を開始しない() = runTest {
        val repository = RecordingRepository()
        InventoryListViewModel(repository)
        runCurrent()
        assertEquals(0, repository.calls)
    }

    @Test
    fun 明示的に二回取得した場合_キャッシュや重複抑制を追加せず二回取得する() = runTest {
        val repository = RecordingRepository()
        val viewModel = InventoryListViewModel(repository)
        repeat(2) { assertEquals(repository.result, viewModel.getInventories()) }
        assertEquals(2, repository.calls)
    }

    @Test
    fun Repositoryが失敗した場合_通知状態へ変換せず同じ例外を呼び出し元へ返す() = runTest {
        val failure = IllegalStateException("synthetic failure")
        val repository = RecordingRepository(failure = failure)
        val viewModel = InventoryListViewModel(repository)
        val actual = runCatching { viewModel.getInventories() }.exceptionOrNull()
        assertSame(failure, actual)
    }

    @Test
    fun 呼び出し元をキャンセルした場合_取得をキャンセルして別のJobへ移さない() = runTest {
        val repository = RecordingRepository(pending = CompletableDeferred())
        val viewModel = InventoryListViewModel(repository)
        val caller = launch { viewModel.getInventories() }
        runCurrent()
        assertEquals(1, repository.calls)
        caller.cancelAndJoin()
        assertTrue(repository.cancelled)
    }

    private class RecordingRepository(
        private val failure: Exception? = null,
        private val pending: CompletableDeferred<List<Inventory>>? = null
    ) : InventoryRepository {
        val result = listOf(Inventory(77, "Shelf", "1"))
        var calls = 0
        var cancelled = false

        override suspend fun getInventories(): List<Inventory> {
            calls += 1
            failure?.let { throw it }
            return try {
                pending?.await() ?: result
            } catch (cancellation: CancellationException) {
                cancelled = true
                throw cancellation
            }
        }

        override suspend fun getInventory(inventoryId: Int): Inventory = error("unused")
    }
}
