package jp.co.zaico.codingtest.ui.inventory.detail

import androidx.lifecycle.ViewModel
import jp.co.zaico.codingtest.domain.inventory.Inventory
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository

/**
 * 詳細画面に表示する在庫を読み込むViewModel。
 *
 * 旧クラス名: `SecondViewModel`
 */
class InventoryDetailViewModel(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {
    suspend fun getInventory(inventoryId: Int): Inventory =
        inventoryRepository.getInventory(inventoryId)
}
