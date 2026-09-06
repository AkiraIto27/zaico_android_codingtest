package jp.co.zaico.codingtest.ui.inventory

import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import jp.co.zaico.codingtest.R
import jp.co.zaico.codingtest.domain.company.CompanyIdResult
import jp.co.zaico.codingtest.domain.company.CompanyRepository
import jp.co.zaico.codingtest.domain.company.CompanyRepositoryException
import jp.co.zaico.codingtest.domain.inventory.Inventory
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository
import jp.co.zaico.codingtest.ui.inventory.create.InventoryCreateActivity
import jp.co.zaico.codingtest.ui.inventory.create.InventoryCreateTestBindings
import jp.co.zaico.codingtest.ui.inventory.list.InventoryListAdapter
import jp.co.zaico.codingtest.ui.main.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
@HiltAndroidTest
@Suppress("NonAsciiCharacters", "TestFunctionName")
class InventoryScreenDiRegressionTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @After
    fun tearDown() {
        InventoryCreateTestBindings.reset()
    }

    @Test
    fun 一覧を再開した場合_前の取得をキャンセルして毎回取得する() {
        val repository = installRepository()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            var previous = repository.nextRequest()
            repeat(2) {
                controller.pause().resume()
                assertTrue(previous.cancelled.await(5, TimeUnit.SECONDS))
                previous = repository.nextRequest()
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun 一覧のViewを破棄した場合_ViewModelが残っても取得をキャンセルする() {
        val repository = installRepository()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val request = repository.nextRequest()
            val navHost = controller.get().supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
            val fragment = requireNotNull(navHost.childFragmentManager.primaryNavigationFragment)
            fragment.parentFragmentManager.beginTransaction().detach(fragment).commitNow()
            assertTrue(request.cancelled.await(5, TimeUnit.SECONDS))
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun Activityを再生成した場合_旧取得をキャンセルして一覧を再取得する() {
        val repository = installRepository()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val first = repository.nextRequest()
            controller.recreate()
            assertTrue(first.cancelled.await(5, TimeUnit.SECONDS))
            assertNotNull(repository.nextRequest())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun MainActivityを起動して追加を押した場合_会社IDを先読みして従来の登録Activityを開く() {
        val companyLookup = CountDownLatch(1)
        InventoryCreateTestBindings.companyRepository = object : CompanyRepository {
            override suspend fun getCompanyId(): CompanyIdResult {
                companyLookup.countDown()
                return CompanyIdResult.Success(123)
            }

            override suspend fun requireCompanyId(): Int = 123
        }
        hiltRule.inject()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertTrue(companyLookup.await(5, TimeUnit.SECONDS))
            activity.findViewById<android.view.View>(R.id.fab).performClick()
            val intent = shadowOf(activity).nextStartedActivity
            assertNotNull(intent)
            assertEquals(InventoryCreateActivity::class.java.name, intent.component?.className)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun 一覧項目を押した場合_表示したIDを詳細へ渡して取得結果を表示する() {
        val repository = installRepository()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val recycler = activity.findViewById<RecyclerView>(R.id.recyclerView)
            repository.nextRequest().response.complete(listOf(Inventory(77, "Shelf", "3")))
            awaitUi { recycler.adapter?.itemCount == 1 }
            val adapter = recycler.adapter as InventoryListAdapter
            val holder = adapter.createViewHolder(recycler, 0)
            adapter.bindViewHolder(holder, 0)
            assertEquals("77", holder.itemView.findViewById<TextView>(R.id.textView_id).text.toString())
            assertEquals("Shelf", holder.itemView.findViewById<TextView>(R.id.textView_title).text.toString())

            holder.itemView.performClick()
            shadowOf(Looper.getMainLooper()).idle()
            val detailRequest = repository.nextDetailRequest()
            assertEquals(77, detailRequest.inventoryId)
            detailRequest.response.complete(Inventory(77, "Updated shelf", "9"))
            awaitUi {
                activity.findViewById<TextView>(R.id.textViewTitle)?.text?.toString() == "Updated shelf"
            }
            assertEquals("77", activity.findViewById<TextView>(R.id.textViewId).text.toString())
            assertEquals("9", activity.findViewById<TextView>(R.id.textViewQuantity).text.toString())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun 一覧取得が失敗した場合_従来の種別別Toast文言と表示時間を維持する() {
        val repository = installRepository()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val cases = listOf(
                Triple(IOException("synthetic failure"), R.string.inventory_load_error, Toast.LENGTH_SHORT),
                Triple(CompanyRepositoryException(CompanyIdResult.Empty), R.string.company_list_empty, Toast.LENGTH_LONG),
                Triple(CompanyRepositoryException(CompanyIdResult.HttpFailure(503)), R.string.inventory_load_error, Toast.LENGTH_LONG)
            )
            cases.forEachIndexed { index, (failure, message, duration) ->
                if (index > 0) controller.pause().resume()
                val before = ShadowToast.shownToastCount()
                repository.nextRequest().response.completeExceptionally(failure)
                awaitUi { ShadowToast.shownToastCount() == before + 1 }
                assertEquals(controller.get().getString(message), ShadowToast.getTextOfLatestToast())
                assertEquals(duration, ShadowToast.getLatestToast().duration)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun 詳細のViewを破棄した場合_取得をキャンセルして破棄済みViewを更新しない() {
        val repository = installRepository()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            val navHost = requireNotNull(activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment_content_main) as? NavHostFragment)
            navHost.navController.navigate(
                R.id.action_inventory_list_to_inventory_detail,
                android.os.Bundle().apply { putString("inventoryId", "77") }
            )
            shadowOf(Looper.getMainLooper()).idle()
            val request = repository.nextDetailRequest()
            val fragment = requireNotNull(navHost.childFragmentManager.primaryNavigationFragment)
            val title = fragment.requireView().findViewById<TextView>(R.id.textViewTitle)
            val previousTitle = title.text.toString()
            val toastCount = ShadowToast.shownToastCount()

            fragment.parentFragmentManager.beginTransaction().detach(fragment).commitNow()
            assertTrue(request.cancelled.await(5, TimeUnit.SECONDS))
            assertNull(fragment.view)
            request.response.complete(Inventory(77, "Late result", "4"))
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(previousTitle, title.text.toString())
            assertEquals(toastCount, ShadowToast.shownToastCount())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private fun awaitUi(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        assertTrue("画面更新が完了しませんでした", condition())
    }

    private fun installRepository(): PendingInventoryRepository {
        val repository = PendingInventoryRepository()
        InventoryCreateTestBindings.inventoryRepository = repository
        hiltRule.inject()
        return repository
    }

    private class PendingRequest {
        val response = CompletableDeferred<List<Inventory>>()
        val cancelled = CountDownLatch(1)
    }

    private class DetailRequest(val inventoryId: Int) {
        val response = CompletableDeferred<Inventory>()
        val cancelled = CountDownLatch(1)
    }

    private class PendingInventoryRepository : InventoryRepository {
        private val requests = LinkedBlockingQueue<PendingRequest>()
        private val detailRequests = LinkedBlockingQueue<DetailRequest>()

        fun nextRequest(): PendingRequest = requireNotNull(requests.poll(5, TimeUnit.SECONDS)) {
            "一覧取得が開始されませんでした"
        }

        fun nextDetailRequest(): DetailRequest = requireNotNull(detailRequests.poll(5, TimeUnit.SECONDS)) {
            "詳細取得が開始されませんでした"
        }

        override suspend fun getInventories(): List<Inventory> {
            val request = PendingRequest()
            requests.put(request)
            return try {
                request.response.await()
            } catch (cancellation: CancellationException) {
                request.cancelled.countDown()
                throw cancellation
            }
        }

        override suspend fun getInventory(inventoryId: Int): Inventory {
            val request = DetailRequest(inventoryId)
            detailRequests.put(request)
            return try {
                request.response.await()
            } catch (cancellation: CancellationException) {
                request.cancelled.countDown()
                throw cancellation
            }
        }
    }
}
