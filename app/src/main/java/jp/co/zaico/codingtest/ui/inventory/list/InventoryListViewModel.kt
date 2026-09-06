package jp.co.zaico.codingtest.ui.inventory.list

import androidx.lifecycle.ViewModel
import jp.co.zaico.codingtest.data.model.Inventory
import jp.co.zaico.codingtest.data.repository.InventoryRepository

/**
 * Loads inventory summaries for the list screen.
 *
 * 旧クラス名: `FirstViewModel`
 */
class InventoryListViewModel(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {
    suspend fun getInventories(): List<Inventory> = inventoryRepository.getInventories()
}
