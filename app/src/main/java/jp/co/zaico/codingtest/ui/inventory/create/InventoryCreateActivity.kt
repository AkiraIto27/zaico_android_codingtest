package jp.co.zaico.codingtest.ui.inventory.create

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import jp.co.zaico.codingtest.R

/**
 * Hosts the inventory creation form.
 *
 * 旧クラス名: `AddActivity`
 */
class InventoryCreateActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: Context) = Intent(context, InventoryCreateActivity::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_inventory_create)
    }

}
