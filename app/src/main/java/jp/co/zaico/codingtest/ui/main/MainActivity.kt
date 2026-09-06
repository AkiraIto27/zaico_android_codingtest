package jp.co.zaico.codingtest.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import dagger.hilt.android.AndroidEntryPoint
import jp.co.zaico.codingtest.R
import jp.co.zaico.codingtest.databinding.ActivityMainBinding
import jp.co.zaico.codingtest.ui.inventory.create.InventoryCreateActivity

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var inventoryCreateEventId = 0L
    private val createInventoryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            findNavController(R.id.nav_host_fragment_content_main)
                .currentBackStackEntry
                ?.savedStateHandle
                ?.set(INVENTORY_CREATE_RESULT_KEY, ++inventoryCreateEventId)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        binding.fab.setOnClickListener {
            createInventoryLauncher.launch(InventoryCreateActivity.createIntent(this))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private companion object {
        const val INVENTORY_CREATE_RESULT_KEY = "inventory_create_result"
    }

}
