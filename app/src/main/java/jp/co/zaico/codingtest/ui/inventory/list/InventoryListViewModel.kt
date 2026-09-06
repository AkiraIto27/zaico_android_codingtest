package jp.co.zaico.codingtest.ui.inventory.list

import androidx.lifecycle.ViewModel
import jp.co.zaico.codingtest.domain.inventory.Inventory
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository

/**
 * 在庫一覧画面に表示する在庫概要を読み込むViewModel。
 *
 * 旧クラス名: `FirstViewModel`
 */
class InventoryListViewModel(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {
    suspend fun getInventories(): List<Inventory> = inventoryRepository.getInventories()
}
