@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package jp.co.zaico.codingtest

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AddViewModelTest {

    private val viewModels = mutableListOf<AddViewModel>()
    private lateinit var mainDispatcher: TestDispatcher

    @Before
    fun setUp() {
        mainDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        try {
            viewModels.forEach { it.viewModelScope.cancel() }
            mainDispatcher.scheduler.advanceUntilIdle()
            viewModels.clear()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(inventoryCreator: InventoryCreator): AddViewModel =
        AddViewModel(inventoryCreator).also(viewModels::add)

    @Test
    fun タイトルが空白のみまたは200文字を超える場合_送信せず入力エラーを表示する() = runTest {
        val creator = FakeInventoryCreator()
        val viewModel = createViewModel(creator)

        viewModel.updateTitle("   ")
        viewModel.submit()
        assertEquals(AddTitleError.Required, viewModel.uiState.value.titleError)

        viewModel.updateTitle("x".repeat(201))
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(AddTitleError.TooLong, viewModel.uiState.value.titleError)
        assertTrue(creator.titles.isEmpty())
    }

    @Test
    fun タイトルが200文字の場合_内容を変更せず送信して登録完了を通知する() = runTest {
        val title = "x".repeat(200)
        val creator = FakeInventoryCreator(CreateInventoryResult.Success(91L))
        val viewModel = createViewModel(creator)
        viewModel.updateTitle(title)

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(listOf(title), creator.titles)
        assertEquals(91L, viewModel.uiState.value.createdInventoryId)
        assertNull(viewModel.uiState.value.titleError)
        assertFalse(viewModel.uiState.value.isSubmitting)
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(1, creator.titles.size)
    }

    @Test
    fun 登録処理中に再度送信した場合_リクエストを重複して送らない() = runTest {
        val creator = SuspendedInventoryCreator()
        val viewModel = createViewModel(creator)
        viewModel.updateTitle("Inventory")

        viewModel.submit()
        runCurrent()
        withTimeout(1_000) { creator.started.await() }
        viewModel.submit()
        runCurrent()

        assertEquals(1, creator.callCount)
        assertTrue(viewModel.uiState.value.isSubmitting)

        creator.result.complete(CreateInventoryResult.Success(7L))
        advanceUntilIdle()
        withTimeout(1_000) {
            viewModel.uiState.first { it.createdInventoryId == 7L }
        }
        assertEquals(7L, viewModel.uiState.value.createdInventoryId)
    }

    @Test
    fun 登録に失敗した場合_安全なエラーを表示して入力を保持し再試行できる() = runTest {
        val creator = FakeInventoryCreator(CreateInventoryResult.ConfigurationFailure)
        val viewModel = createViewModel(creator)
        viewModel.updateTitle("Keep me")

        viewModel.submit()
        advanceUntilIdle()

        assertFailureState(viewModel, AddRequestError.Configuration)

        creator.result = CreateInventoryResult.HttpFailure(406)
        viewModel.submit()
        advanceUntilIdle()
        assertFailureState(viewModel, AddRequestError.Http)

        creator.result = CreateInventoryResult.DecodeFailure
        viewModel.submit()
        advanceUntilIdle()
        assertFailureState(viewModel, AddRequestError.InvalidResponse)

        creator.result = CreateInventoryResult.NetworkFailure
        viewModel.submit()
        advanceUntilIdle()
        assertFailureState(viewModel, AddRequestError.Network)

        creator.result = CreateInventoryResult.Success(55L)
        viewModel.submit()
        advanceUntilIdle()
        assertEquals(55L, viewModel.uiState.value.createdInventoryId)
        assertNull(viewModel.uiState.value.requestError)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(5, creator.titles.size)
    }

    @Test
    fun 登録処理がキャンセルされた場合_エラーに変換せず送信中状態を解除する() = runTest {
        val viewModel = createViewModel(
            ThrowingInventoryCreator(CancellationException("cancelled"))
        )
        viewModel.updateTitle("Inventory")

        viewModel.submit()
        runCurrent()

        assertFalse(viewModel.uiState.value.isSubmitting)
        assertNull(viewModel.uiState.value.requestError)
        assertEquals("Inventory", viewModel.uiState.value.title)
    }

    @Test
    fun タイトルの前後に空白がある場合_空白を保持して送信する() = runTest {
        val creator = FakeInventoryCreator(CreateInventoryResult.Success(3L))
        val viewModel = createViewModel(creator)
        val title = "  New inventory  "

        viewModel.updateTitle(title)
        viewModel.submit()
        advanceUntilIdle()

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
