package jp.co.zaico.codingtest

import android.view.View
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.ktor.client.HttpClient
import jp.co.zaico.codingtest.di.MockNetworkControl
import jp.co.zaico.codingtest.data.repository.CompanyRepositoryImpl
import jp.co.zaico.codingtest.data.repository.InventoryCreator
import jp.co.zaico.codingtest.data.repository.KtorInventoryCreator
import jp.co.zaico.codingtest.domain.company.CompanyRepository
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository
import jp.co.zaico.codingtest.ui.inventory.create.InventoryCreateActivity
import jp.co.zaico.codingtest.ui.main.MainActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Suppress("NonAsciiCharacters", "TestFunctionName")
class HiltGraphIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @javax.inject.Inject
    lateinit var firstHttpClient: HttpClient

    @javax.inject.Inject
    lateinit var secondHttpClient: HttpClient

    @javax.inject.Inject
    lateinit var companyRepository: CompanyRepository

    @javax.inject.Inject
    lateinit var companyRepositoryImpl: CompanyRepositoryImpl

    @javax.inject.Inject
    lateinit var inventoryRepository: InventoryRepository

    @javax.inject.Inject
    lateinit var inventoryCreator: InventoryCreator

    @javax.inject.Inject
    lateinit var ktorInventoryCreator: KtorInventoryCreator

    @Before
    fun setUp() {
        MockNetworkControl.reset()
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        firstHttpClient.close()
    }

    @Test
    fun 本番Hiltグラフから全依存を取得する_MockEngineへ到達する() = runBlocking {
        assertSame(firstHttpClient, secondHttpClient)
        assertSame(companyRepository, companyRepositoryImpl)
        assertSame(inventoryCreator, ktorInventoryCreator)

        assertEquals(123, companyRepository.getCompanyId().let { result ->
            check(result is jp.co.zaico.codingtest.domain.company.CompanyIdResult.Success)
            result.companyId
        })
        assertEquals(77, inventoryRepository.getInventories().single().id)
        assertEquals(77, inventoryRepository.getInventory(77).id)
        assertEquals(
            jp.co.zaico.codingtest.data.repository.CreateInventoryResult.Success(99L),
            inventoryCreator.createInventory("Integration inventory")
        )
    }

    @Test
    fun 一つの取得をキャンセルする_共有clientと別経路の通信を継続する() = runBlocking {
        MockNetworkControl.blockInventoryList = true
        val pendingList = async {
            inventoryRepository.getInventories()
        }
        MockNetworkControl.listRequestStarted.await()

        pendingList.cancelAndJoin()

        assertTrue(firstHttpClient.coroutineContext.isActive)
        assertEquals(77, inventoryRepository.getInventory(77).id)
        MockNetworkControl.releaseInventoryList.complete(Unit)
    }

    @Test
    fun HiltApplicationと実Activityを起動する_XMLFragmentとNavigationへ注入する() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById<View>(R.id.nav_host_fragment_content_main))
                val navController = NavHostFragment.findNavController(
                    requireNotNull(activity.supportFragmentManager
                        .findFragmentById(R.id.nav_host_fragment_content_main))
                )
                assertEquals(R.id.inventory_list_fragment, navController.currentDestination?.id)
                navController.navigate(
                    R.id.action_inventory_list_to_inventory_detail,
                    android.os.Bundle().apply { putString("inventoryId", "77") }
                )
                assertEquals(R.id.inventory_detail_fragment, navController.currentDestination?.id)
            }
        }

        ActivityScenario.launch(InventoryCreateActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.findViewById<View>(R.id.inventory_create_fragment_container))
            }
        }
    }
}
