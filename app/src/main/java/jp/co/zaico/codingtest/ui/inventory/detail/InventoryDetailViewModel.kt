package jp.co.zaico.codingtest.ui.inventory.detail

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.zaico.codingtest.domain.inventory.Inventory
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository

/**
 * 詳細画面に表示する在庫を読み込むViewModel。
 *
 * 旧クラス名: `SecondViewModel`
 */
@HiltViewModel
class InventoryDetailViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {
    suspend fun getInventory(inventoryId: Int): Inventory =
        inventoryRepository.getInventory(inventoryId)
}
