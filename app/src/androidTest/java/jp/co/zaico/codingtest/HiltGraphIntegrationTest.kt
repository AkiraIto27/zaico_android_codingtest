package jp.co.zaico.codingtest

import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import io.ktor.client.HttpClient
import jp.co.zaico.codingtest.di.NetworkModule
import jp.co.zaico.codingtest.domain.company.CompanyRepository
import jp.co.zaico.codingtest.ui.inventory.create.InventoryCreateActivity
import jp.co.zaico.codingtest.ui.inventory.detail.InventoryDetailFragment
import jp.co.zaico.codingtest.ui.main.MainActivity
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@UninstallModules(NetworkModule::class)
class HiltGraphIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var firstHttpClient: HttpClient

    @Inject
    lateinit var secondHttpClient: HttpClient

    @Inject
    lateinit var firstCompanyRepository: CompanyRepository

    @Inject
    lateinit var secondCompanyRepository: CompanyRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun HiltApplicationとNavigation経由の全画面_各ViewModelを生成し共有依存を再利用する() {
        assertSame(firstHttpClient, secondHttpClient)
        assertSame(firstCompanyRepository, secondCompanyRepository)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val navController = activity.findNavController(R.id.nav_host_fragment_content_main)
                assertTrue(navController.currentDestination?.id == R.id.inventory_list_fragment)
                navController.navigate(
                    R.id.action_inventory_list_to_inventory_detail,
                    android.os.Bundle().apply { putString("inventoryId", "77") }
                )
            }
            scenario.onActivity { activity ->
                assertTrue(
                    activity.supportFragmentManager.fragments
                        .flatMap { it.childFragmentManager.fragments }
                        .any { it is InventoryDetailFragment }
                )
            }
        }

        ActivityScenario.launch(InventoryCreateActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue(
                    activity.supportFragmentManager.fragments
                        .any { it is jp.co.zaico.codingtest.ui.inventory.create.InventoryCreateFragment }
                )
            }
        }
    }
}
