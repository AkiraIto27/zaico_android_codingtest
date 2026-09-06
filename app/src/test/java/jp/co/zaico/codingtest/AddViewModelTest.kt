package jp.co.zaico.codingtest

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddViewModelTest {

    @Test
    fun `空白または201文字のタイトル_作成処理を呼ばず入力エラーになる`() = runBlocking {
        val creator = FakeInventoryCreator()
        val viewModel = AddViewModel(creator, this)

        viewModel.updateTitle("   ")
        viewModel.submit()
        assertEquals(AddTitleError.Required, viewModel.uiState.value.titleError)

        viewModel.updateTitle("x".repeat(201))
        viewModel.submit()
        assertEquals(AddTitleError.TooLong, viewModel.uiState.value.titleError)
        assertTrue(creator.titles.isEmpty())
    }

    @Test
    fun `200文字のタイトル_タイトルを変更せず受け付ける`() = runBlocking {
        val title = "x".repeat(200)
        val creator = FakeInventoryCreator(CreateInventoryResult.Success(91L))
        val viewModel = AddViewModel(creator, this)
        viewModel.updateTitle(title)

        viewModel.submit()

        assertEquals(listOf(title), creator.titles)
        assertEquals(91L, viewModel.uiState.value.createdInventoryId)
        assertNull(viewModel.uiState.value.titleError)
        assertFalse(viewModel.uiState.value.isSubmitting)
        viewModel.submit()
        assertEquals(1, creator.titles.size)
    }

    @Test
    fun `送信中に再送する_最初のリクエストだけ実行する`() = runBlocking {
        val creator = SuspendedInventoryCreator()
        val viewModel = AddViewModel(creator, this)
        viewModel.updateTitle("Inventory")

        viewModel.submit()
        withTimeout(1_000) { creator.started.await() }
        viewModel.submit()

        assertEquals(1, creator.callCount)
        assertTrue(viewModel.uiState.value.isSubmitting)

        creator.result.complete(CreateInventoryResult.Success(7L))
        withTimeout(1_000) {
            viewModel.uiState.first { it.createdInventoryId == 7L }
        }
        assertEquals(7L, viewModel.uiState.value.createdInventoryId)
    }

    @Test
    fun `各種作成失敗_安全なUI状態に変換しタイトルを保持する`() = runBlocking {
        val creator = FakeInventoryCreator(CreateInventoryResult.ConfigurationFailure)
        val viewModel = AddViewModel(creator, this)
        viewModel.updateTitle("Keep me")

        viewModel.submit()

        assertFailureState(viewModel, AddRequestError.Configuration)

        creator.result = CreateInventoryResult.HttpFailure(406)
        viewModel.submit()
        assertFailureState(viewModel, AddRequestError.Http)

        creator.result = CreateInventoryResult.DecodeFailure
        viewModel.submit()
        assertFailureState(viewModel, AddRequestError.InvalidResponse)

        creator.result = CreateInventoryResult.NetworkFailure
        viewModel.submit()
        assertFailureState(viewModel, AddRequestError.Network)

        creator.result = CreateInventoryResult.Success(55L)
        viewModel.submit()
        assertEquals(55L, viewModel.uiState.value.createdInventoryId)
        assertNull(viewModel.uiState.value.requestError)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(5, creator.titles.size)
    }

    @Test
    fun `キャンセル例外_失敗表示にせず送信中状態を解除する`() = runBlocking {
        val viewModel = AddViewModel(
            ThrowingInventoryCreator(CancellationException("cancelled")),
            this
        )
        viewModel.updateTitle("Inventory")

        viewModel.submit()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertNull(viewModel.uiState.value.requestError)
        assertEquals("Inventory", viewModel.uiState.value.title)
    }

    @Test
    fun `前後に空白があるタイトル_空白を保持して送信する`() = runBlocking {
        val creator = FakeInventoryCreator(CreateInventoryResult.Success(3L))
        val viewModel = AddViewModel(creator, this)
        val title = "  New inventory  "

        viewModel.updateTitle(title)
        viewModel.submit()

        assertEquals(listOf(title), creator.titles)
    }

    private class FakeInventoryCreator(
        var result: CreateInventoryResult = CreateInventoryResult.Success(1L)
    ) : InventoryCreator {
        val titles = mutableListOf<String>()

        override suspend fun createInventory(title: String): CreateInventoryResult {
            titles += title
            return result
        }

        override fun close() = Unit
    }

    private class SuspendedInventoryCreator : InventoryCreator {
        val started = CompletableDeferred<Unit>()
        val result = CompletableDeferred<CreateInventoryResult>()
        var callCount = 0

        override suspend fun createInventory(title: String): CreateInventoryResult {
            callCount += 1
            started.complete(Unit)
            return result.await()
        }

        override fun close() = Unit
    }

    private class ThrowingInventoryCreator(
        private val throwable: Throwable
    ) : InventoryCreator {
        override suspend fun createInventory(title: String): CreateInventoryResult = throw throwable
        override fun close() = Unit
    }

    private fun assertFailureState(viewModel: AddViewModel, error: AddRequestError) {
        assertEquals("Keep me", viewModel.uiState.value.title)
        assertEquals(error, viewModel.uiState.value.requestError)
        assertNull(viewModel.uiState.value.createdInventoryId)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }
}
