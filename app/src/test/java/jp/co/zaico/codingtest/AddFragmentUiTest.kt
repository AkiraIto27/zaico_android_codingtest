package jp.co.zaico.codingtest

import android.app.Activity
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.Shadows
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AddFragmentUiTest {

    @After
    fun tearDown() {
        (RuntimeEnvironment.getApplication() as ZaicoApplication).inventoryCreatorFactory = null
    }

    @Test
    fun タイトル入力後に登録した場合_FakeCreatorへ値を渡す() = runMainDispatcherTest {
        val creator = ControlledInventoryCreator(CreateInventoryResult.Success(10L))

        withActivity(creator) { activity, _ ->
            titleInput(activity).setText("UI inventory")
            submitButton(activity).performClick()
            runCurrent()

            assertEquals(listOf("UI inventory"), creator.titles)
        }
    }

    @Test
    fun 登録待機中の場合_入力と登録を無効にしてLoadingを表示する() = runMainDispatcherTest {
        val creator = ControlledInventoryCreator()

        withActivity(creator) { activity, _ ->
            titleInput(activity).setText("Loading inventory")
            submitButton(activity).performClick()
            runCurrent()

            assertTrue(creator.started.isCompleted)
            assertFalse(titleInput(activity).isEnabled)
            assertFalse(submitButton(activity).isEnabled)
            assertTrue(progressIndicator(activity).visibility == View.VISIBLE)
        }
    }

    @Test
    fun 失敗した場合_画面を終了せず入力を保持してエラーを表示する() = runMainDispatcherTest {
        val creator = ControlledInventoryCreator(CreateInventoryResult.NetworkFailure)

        withActivity(creator) { activity, _ ->
            titleInput(activity).setText("Keep after failure")
            submitButton(activity).performClick()
            runCurrent()

            assertFalse(activity.isFinishing)
            assertEquals("Keep after failure", titleInput(activity).text.toString())
            assertTrue(errorText(activity).visibility == View.VISIBLE)
            assertEquals(
                activity.getString(R.string.add_network_error),
                errorText(activity).text.toString()
            )
            assertTrue(submitButton(activity).isEnabled)
        }
    }

    @Test
    fun エラー種別が変わった場合_新しいエラー文言を表示する() = runMainDispatcherTest {
        val creator = SequencedInventoryCreator(
            CreateInventoryResult.NetworkFailure,
            CreateInventoryResult.ConfigurationFailure
        )

        withActivity(creator) { activity, _ ->
            titleInput(activity).setText("First request")
            submitButton(activity).performClick()
            runCurrent()
            assertEquals(
                activity.getString(R.string.add_network_error),
                errorText(activity).text.toString()
            )

            titleInput(activity).setText("Second request")
            runCurrent()
            assertFalse(errorText(activity).isShown)

            submitButton(activity).performClick()
            runCurrent()
            assertTrue(errorText(activity).isShown)
            assertEquals(
                activity.getString(R.string.add_configuration_error),
                errorText(activity).text.toString()
            )
        }
    }

    @Test
    fun 空拠点エラーのToast表示後にエラーが変化した場合_文言を更新して解除する() = runMainDispatcherTest {
        val creator = SequencedInventoryCreator(
            CreateInventoryResult.EmptyCompany,
            CreateInventoryResult.ConfigurationFailure
        )

        withActivity(creator) { activity, _ ->
            val toastCountBefore = ShadowToast.shownToastCount()
            titleInput(activity).setText("Empty company")
            submitButton(activity).performClick()
            runCurrent()
            val toastCountAfterEmptyCompany = ShadowToast.shownToastCount()
            assertEquals(toastCountBefore + 1, toastCountAfterEmptyCompany)
            assertEquals(
                activity.getString(R.string.company_list_empty),
                errorText(activity).text.toString()
            )

            titleInput(activity).setText("Configuration failure")
            runCurrent()
            assertFalse(errorText(activity).isShown)
            assertEquals("", errorText(activity).text.toString())
            assertEquals(toastCountAfterEmptyCompany, ShadowToast.shownToastCount())

            submitButton(activity).performClick()
            runCurrent()
            assertEquals(
                activity.getString(R.string.add_configuration_error),
                errorText(activity).text.toString()
            )
        }
    }

    @Test
    fun 空拠点エラーを再度受け取った場合_Toastを重複表示せずエラー文言を維持する() = runMainDispatcherTest {
        val creator = SequencedInventoryCreator(
            CreateInventoryResult.EmptyCompany,
            CreateInventoryResult.EmptyCompany
        )

        withActivity(creator) { activity, _ ->
            titleInput(activity).setText("Empty company")
            submitButton(activity).performClick()
            runCurrent()
            val toastCountAfterFirstFailure = ShadowToast.shownToastCount()

            submitButton(activity).performClick()
            runCurrent()

            assertEquals(toastCountAfterFirstFailure, ShadowToast.shownToastCount())
            assertTrue(errorText(activity).isShown)
            assertEquals(
                activity.getString(R.string.company_list_empty),
                errorText(activity).text.toString()
            )
        }
    }

    @Test
    fun 空のタイトルで登録した場合_Creatorを呼ばず入力エラーを表示する() = runMainDispatcherTest {
        val creator = ControlledInventoryCreator()

        withActivity(creator) { activity, _ ->
            submitButton(activity).performClick()
            runCurrent()

            assertTrue(titleInputLayout(activity).isErrorEnabled)
            assertEquals(
                activity.getString(R.string.add_title_required),
                titleInputLayout(activity).error.toString()
            )
            assertTrue(creator.titles.isEmpty())
        }
    }

    @Test
    fun 成功した場合_RESULT_OKを設定してActivityを終了する() = runMainDispatcherTest {
        val creator = ControlledInventoryCreator(CreateInventoryResult.Success(11L))

        withActivity(creator) { activity, _ ->
            titleInput(activity).setText("Successful inventory")
            submitButton(activity).performClick()
            runCurrent()

            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).resultCode)
        }
    }

    @Test
    fun 成功状態を再収集した場合_RESULT_OKと画面終了を重複させない() = runMainDispatcherTest {
        val creator = ControlledInventoryCreator(CreateInventoryResult.Success(13L))

        withActivity(creator) { activity, fragment ->
            titleInput(activity).setText("Repeated success")
            submitButton(activity).performClick()
            runCurrent()
            val toastCountAfterSuccess = ShadowToast.shownToastCount()

            fragment.parentFragmentManager.beginTransaction()
                .detach(fragment)
                .commitNow()
            fragment.parentFragmentManager.beginTransaction()
                .attach(fragment)
                .commitNow()
            runCurrent()

            assertTrue(activity.isFinishing)
            assertEquals(Activity.RESULT_OK, Shadows.shadowOf(activity).resultCode)
            assertEquals(toastCountAfterSuccess, ShadowToast.shownToastCount())
        }
    }

    @Test
    fun 登録中にActivityを再生成した場合_入力と送信状態を保持してPOSTを重複させない() = runMainDispatcherTest {
        val creator = ControlledInventoryCreator()
        val controller = launchActivity(creator)

        try {
            val activity = controller.get()
            titleInput(activity).setText("Retained inventory")
            submitButton(activity).performClick()
            runCurrent()
            assertEquals(1, creator.titles.size)

            controller.recreate()
            val recreated = controller.get()
            runCurrent()

            assertEquals("Retained inventory", titleInput(recreated).text.toString())
            assertTrue(recreated.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            assertFalse(titleInput(recreated).isEnabled)
            assertTrue(progressIndicator(recreated).visibility == View.VISIBLE)
            assertEquals(1, creator.titles.size)
        } finally {
            destroyController(controller)
        }
    }

    @Test
    fun View破棄後に処理が完了した場合_破棄済みViewを更新しない() = runMainDispatcherTest {
        val creator = ControlledInventoryCreator()

        withActivity(creator) { activity, fragment ->
            titleInput(activity).setText("Destroyed view")
            submitButton(activity).performClick()
            runCurrent()
            assertTrue(creator.started.isCompleted)
            val oldErrorText = errorText(activity)
            val toastCountBeforeDetach = ShadowToast.shownToastCount()

            fragment.parentFragmentManager.beginTransaction()
                .detach(fragment)
                .commitNow()
            creator.response.complete(CreateInventoryResult.EmptyCompany)
            runCurrent()

            assertFalse(activity.isFinishing)
            assertEquals(toastCountBeforeDetach, ShadowToast.shownToastCount())
            assertFalse(oldErrorText.isShown)
            assertEquals("", oldErrorText.text.toString())
            assertEquals(1, creator.titles.size)

            fragment.parentFragmentManager.beginTransaction()
                .attach(fragment)
                .commitNow()
            runCurrent()

            assertTrue(errorText(activity).isShown)
            assertEquals(
                activity.getString(R.string.company_list_empty),
                errorText(activity).text.toString()
            )
        }
    }

    private class ControlledInventoryCreator(
        private val immediateResult: CreateInventoryResult? = null
    ) : InventoryCreator {
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<CreateInventoryResult>()
        val titles = mutableListOf<String>()

        override suspend fun createInventory(title: String): CreateInventoryResult {
            titles += title
            started.complete(Unit)
            return immediateResult ?: response.await()
        }

        override fun close() = Unit
    }

    private class SequencedInventoryCreator(
        vararg private val results: CreateInventoryResult
    ) : InventoryCreator {
        val titles = mutableListOf<String>()
        private var resultIndex = 0

        override suspend fun createInventory(title: String): CreateInventoryResult {
            titles += title
            return results[resultIndex++]
        }

        override fun close() = Unit
    }

    private suspend fun withActivity(
        creator: InventoryCreator,
        block: suspend (AddActivity, AddFragment) -> Unit
    ) {
        val controller = launchActivity(creator)
        try {
            val activity = controller.get()
            val fragment = activity.supportFragmentManager
                .findFragmentById(R.id.addFragmentContainer) as AddFragment
            block(activity, fragment)
        } finally {
            destroyController(controller)
        }
    }

    private fun launchActivity(creator: InventoryCreator): ActivityController<AddActivity> {
        val application = RuntimeEnvironment.getApplication() as ZaicoApplication
        application.inventoryCreatorFactory = { creator }
        return Robolectric.buildActivity(AddActivity::class.java).setup()
    }

    private fun destroyController(controller: ActivityController<AddActivity>) {
        val activity = controller.get()
        if (!activity.isDestroyed) controller.destroy()
        (RuntimeEnvironment.getApplication() as ZaicoApplication).inventoryCreatorFactory = null
    }

    private fun titleInput(activity: AddActivity) =
        activity.findViewById<TextInputEditText>(R.id.titleEditText)

    private fun submitButton(activity: AddActivity) =
        activity.findViewById<MaterialButton>(R.id.submitButton)

    private fun progressIndicator(activity: AddActivity) =
        activity.findViewById<CircularProgressIndicator>(R.id.progressIndicator)

    private fun errorText(activity: AddActivity) =
        activity.findViewById<TextView>(R.id.errorText)

    private fun titleInputLayout(activity: AddActivity) =
        activity.findViewById<TextInputLayout>(R.id.titleInputLayout)

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
