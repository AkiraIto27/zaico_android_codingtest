package jp.co.zaico.codingtest.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import jp.co.zaico.codingtest.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import jp.co.zaico.codingtest.domain.company.CompanyRepository
import jp.co.zaico.codingtest.databinding.ActivityMainBinding
import jp.co.zaico.codingtest.ui.inventory.create.InventoryCreateActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var companyRepository: CompanyRepository

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch(Dispatchers.IO) {
            companyRepository.getCompanyId()
        }

        setSupportActionBar(binding.toolbar)

        val navController = findNavController()
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener {
            startActivity(InventoryCreateActivity.createIntent(this))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController()
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private fun findNavController(): NavController {
        val navHost = requireNotNull(
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main)
        )
        return NavHostFragment.findNavController(navHost)
    }

}
