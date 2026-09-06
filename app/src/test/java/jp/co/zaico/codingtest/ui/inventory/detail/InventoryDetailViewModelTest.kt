package jp.co.zaico.codingtest.ui.inventory.detail

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
class InventoryDetailViewModelTest {
    @Test
    fun ViewModelを生成した場合_取得を開始しない() = runTest {
        val repository = RecordingRepository()
        InventoryDetailViewModel(repository)
        runCurrent()
        assertEquals(0, repository.calls)
    }

    @Test
    fun 明示的に二回取得した場合_キャッシュや重複抑制を追加せず二回取得する() = runTest {
        val repository = RecordingRepository()
        val viewModel = InventoryDetailViewModel(repository)
        repeat(2) { assertEquals(repository.result, viewModel.getInventory(77)) }
        assertEquals(2, repository.calls)
    }

    @Test
    fun Repositoryが失敗した場合_通知状態へ変換せず同じ例外を呼び出し元へ返す() = runTest {
        val failure = IllegalStateException("synthetic failure")
        val repository = RecordingRepository(failure = failure)
        val viewModel = InventoryDetailViewModel(repository)
        val actual = runCatching { viewModel.getInventory(77) }.exceptionOrNull()
        assertSame(failure, actual)
    }

    @Test
    fun 呼び出し元をキャンセルした場合_取得をキャンセルして別のJobへ移さない() = runTest {
        val repository = RecordingRepository(pending = CompletableDeferred())
        val viewModel = InventoryDetailViewModel(repository)
        val caller = launch { viewModel.getInventory(77) }
        runCurrent()
        assertEquals(1, repository.calls)
        caller.cancelAndJoin()
        assertTrue(repository.cancelled)
    }

    private class RecordingRepository(
        private val failure: Exception? = null,
        private val pending: CompletableDeferred<Inventory>? = null
    ) : InventoryRepository {
        val result = Inventory(77, "Shelf", "1")
        var calls = 0
        var cancelled = false

        override suspend fun getInventory(inventoryId: Int): Inventory {
            assertEquals(77, inventoryId)
            calls += 1
            failure?.let { throw it }
            return try {
                pending?.await() ?: result
            } catch (cancellation: CancellationException) {
                cancelled = true
                throw cancellation
            }
        }

        override suspend fun getInventories(): List<Inventory> = error("unused")
    }
}
