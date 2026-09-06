package jp.co.zaico.codingtest.ui.inventory.create

import androidx.lifecycle.ViewModelStore
import jp.co.zaico.codingtest.data.repository.CreateInventoryResult
import jp.co.zaico.codingtest.data.repository.InventoryCreator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("NonAsciiCharacters", "TestFunctionName")
class InventoryCreateViewModelTest {

    @Test
    fun 有効なタイトルを一度だけ送信した場合_成功IDを保持して送信中を解除する() = runMainDispatcherTest {
        val creator = RecordingInventoryCreator(CreateInventoryResult.Success(42L))
        val viewModel = InventoryCreateViewModel(creator)

        viewModel.updateTitle("Valid inventory")
        viewModel.submit()
        runCurrent()

        assertEquals(listOf("Valid inventory"), creator.titles)
        assertEquals(42L, viewModel.uiState.value.createdInventoryId)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertNull(viewModel.uiState.value.requestError)
    }

    @Test
    fun タイトルが200文字の場合_入力エラーにせず登録する() = runMainDispatcherTest {
        val title = "x".repeat(200)
        val creator = RecordingInventoryCreator(CreateInventoryResult.Success(1L))
        val viewModel = InventoryCreateViewModel(creator)

        viewModel.updateTitle(title)
        viewModel.submit()
        runCurrent()

        assertEquals(listOf(title), creator.titles)
        assertNull(viewModel.uiState.value.titleError)
        assertEquals(1L, viewModel.uiState.value.createdInventoryId)
    }

    @Test
    fun タイトルの前後に空白がある場合_同じ文字列をCreatorへ渡す() = runMainDispatcherTest {
        val title = "  Inventory with spaces  "
        val creator = RecordingInventoryCreator(CreateInventoryResult.Success(2L))
        val viewModel = InventoryCreateViewModel(creator)

        viewModel.updateTitle(title)
        viewModel.submit()
        runCurrent()

        assertEquals(listOf(title), creator.titles)
    }

    @Test
    fun 登録中に再度送信した場合_Creator呼び出しを一回にする() = runMainDispatcherTest {
        val creator = SuspendedInventoryCreator()
        val viewModel = InventoryCreateViewModel(creator)
        viewModel.updateTitle("Waiting inventory")

        viewModel.submit()
        assertTrue(creator.started.isCompleted)
        assertTrue(viewModel.uiState.value.isSubmitting)

        viewModel.submit()

        assertEquals(1, creator.callCount)
        assertTrue(viewModel.uiState.value.isSubmitting)
        creator.result.complete(CreateInventoryResult.Success(7L))
        runCurrent()

        assertEquals(7L, viewModel.uiState.value.createdInventoryId)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun 成功後に再度送信した場合_Creatorを呼ばない() = runMainDispatcherTest {
        val creator = RecordingInventoryCreator(CreateInventoryResult.Success(8L))
        val viewModel = InventoryCreateViewModel(creator)
        viewModel.updateTitle("Completed inventory")

        viewModel.submit()
        runCurrent()
        viewModel.submit()
        runCurrent()

        assertEquals(1, creator.titles.size)
        assertEquals(8L, viewModel.uiState.value.createdInventoryId)
    }

    @Test
    fun 空文字空白のみまたは201文字の場合_Creatorを呼ばず入力エラーにする() = runMainDispatcherTest {
        val creator = RecordingInventoryCreator()
        val viewModel = InventoryCreateViewModel(creator)

        viewModel.updateTitle("")
        viewModel.submit()
        assertEquals(InventoryCreateTitleError.Required, viewModel.uiState.value.titleError)

        viewModel.updateTitle(" \t\n")
        viewModel.submit()
        assertEquals(InventoryCreateTitleError.Required, viewModel.uiState.value.titleError)

        viewModel.updateTitle("x".repeat(201))
        viewModel.submit()
        assertEquals(InventoryCreateTitleError.TooLong, viewModel.uiState.value.titleError)

        assertTrue(creator.titles.isEmpty())
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun 各種失敗結果の場合_対応するUI状態へ変換してタイトルを保持する() = runMainDispatcherTest {
        val failures = listOf(
            CreateInventoryResult.ConfigurationFailure to InventoryCreateRequestError.Configuration,
            CreateInventoryResult.EmptyCompany to InventoryCreateRequestError.EmptyCompany,
            CreateInventoryResult.HttpFailure(406) to InventoryCreateRequestError.Http,
            CreateInventoryResult.DecodeFailure to InventoryCreateRequestError.InvalidResponse,
            CreateInventoryResult.NetworkFailure to InventoryCreateRequestError.Network
        )

        failures.forEach { (result, expectedError) ->
            val creator = RecordingInventoryCreator(result)
            val viewModel = InventoryCreateViewModel(creator)
            viewModel.updateTitle("Keep this title")

            viewModel.submit()
            runCurrent()

            assertEquals(expectedError, viewModel.uiState.value.requestError)
            assertEquals("Keep this title", viewModel.uiState.value.title)
            assertFalse(viewModel.uiState.value.isSubmitting)
            assertNull(viewModel.uiState.value.createdInventoryId)
            assertEquals(1, creator.titles.size)
        }
    }

    @Test
    fun 失敗後に再送した場合_同じタイトルで成功する() = runMainDispatcherTest {
        val creator = SequencedInventoryCreator(
            CreateInventoryResult.NetworkFailure,
            CreateInventoryResult.Success(73L)
        )
        val viewModel = InventoryCreateViewModel(creator)
        viewModel.updateTitle("Retry inventory")

        viewModel.submit()
        runCurrent()
        assertEquals(InventoryCreateRequestError.Network, viewModel.uiState.value.requestError)
        assertFalse(viewModel.uiState.value.isSubmitting)

        viewModel.submit()
        runCurrent()

        assertEquals(listOf("Retry inventory", "Retry inventory"), creator.titles)
        assertEquals(73L, viewModel.uiState.value.createdInventoryId)
        assertNull(viewModel.uiState.value.requestError)
    }

    @Test
    fun CancellationExceptionが発生した場合_通常エラーへ変換せず送信中を解除する() = runMainDispatcherTest {
        val creator = CancellationInventoryCreator()
        val viewModel = InventoryCreateViewModel(creator)
        viewModel.updateTitle("Cancelled inventory")

        viewModel.submit()
        runCurrent()

        assertTrue(creator.completion.await() is CancellationException)
        assertNull(viewModel.uiState.value.requestError)
        assertNull(viewModel.uiState.value.createdInventoryId)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals("Cancelled inventory", viewModel.uiState.value.title)
    }

    @Test
    fun ViewModelを破棄した場合_処理をキャンセルして共有Creatorを別画面で再利用できる() = runMainDispatcherTest {
        val store = ViewModelStore()
        val creator = SuspendedInventoryCreator()
        val viewModel = InventoryCreateViewModel(creator)
        store.put("inventory-create", viewModel)
        viewModel.updateTitle("Destroy inventory")
        viewModel.submit()

        assertTrue(viewModel.uiState.value.isSubmitting)
        store.clear()
        runCurrent()

        assertTrue(creator.cancelled.isCompleted)
        assertFalse(viewModel.uiState.value.isSubmitting)

        val nextViewModel = InventoryCreateViewModel(creator)
        store.put("next-inventory-create", nextViewModel)
        nextViewModel.updateTitle("Next inventory")
        nextViewModel.submit()
        creator.result.complete(CreateInventoryResult.Success(9L))
        runCurrent()

        assertEquals(2, creator.callCount)
        assertEquals(9L, nextViewModel.uiState.value.createdInventoryId)
        store.clear()
    }

    @Test
    fun 成功後にタイトルを変更した場合_成功IDを解除して再送信できる() = runMainDispatcherTest {
        val creator = SequencedInventoryCreator(
            CreateInventoryResult.Success(8L),
            CreateInventoryResult.Success(9L)
        )
        val viewModel = InventoryCreateViewModel(creator)
        viewModel.updateTitle("First inventory")
        viewModel.submit()
        runCurrent()
        assertEquals(8L, viewModel.uiState.value.createdInventoryId)

        viewModel.updateTitle("Second inventory")
        assertNull(viewModel.uiState.value.createdInventoryId)
        viewModel.submit()
        runCurrent()

        assertEquals(listOf("First inventory", "Second inventory"), creator.titles)
        assertEquals(9L, viewModel.uiState.value.createdInventoryId)
    }

    private class RecordingInventoryCreator(
        private val result: CreateInventoryResult = CreateInventoryResult.Success(1L)
    ) : InventoryCreator {
        val titles = mutableListOf<String>()

        override suspend fun createInventory(title: String): CreateInventoryResult {
            titles += title
            return result
        }

    }

    private class SequencedInventoryCreator(
        private vararg val results: CreateInventoryResult
    ) : InventoryCreator {
        val titles = mutableListOf<String>()
        private var index = 0

        override suspend fun createInventory(title: String): CreateInventoryResult {
            titles += title
            return results[index++]
        }

    }

    private class SuspendedInventoryCreator : InventoryCreator {
        val started = CompletableDeferred<Unit>()
        val result = CompletableDeferred<CreateInventoryResult>()
        val cancelled = CompletableDeferred<Unit>()
        var callCount = 0

        override suspend fun createInventory(title: String): CreateInventoryResult {
            callCount += 1
            started.complete(Unit)
            return try {
                result.await()
            } finally {
                cancelled.complete(Unit)
            }
        }

    }

    private class CancellationInventoryCreator : InventoryCreator {
        val completion = CompletableDeferred<Throwable?>()

        override suspend fun createInventory(title: String): CreateInventoryResult {
            coroutineContext[Job]?.invokeOnCompletion(completion::complete)
            throw CancellationException("synthetic cancellation")
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
