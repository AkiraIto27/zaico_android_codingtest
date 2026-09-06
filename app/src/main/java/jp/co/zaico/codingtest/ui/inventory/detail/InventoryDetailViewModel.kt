package jp.co.zaico.codingtest.ui.inventory.detail

import androidx.lifecycle.ViewModel
import jp.co.zaico.codingtest.data.model.Inventory
import jp.co.zaico.codingtest.data.repository.InventoryRepository

class InventoryDetailViewModel(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {
    suspend fun getInventory(inventoryId: Int): Inventory =
        inventoryRepository.getInventory(inventoryId)
}
