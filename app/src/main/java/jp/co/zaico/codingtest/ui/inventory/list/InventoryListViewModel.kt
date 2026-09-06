package jp.co.zaico.codingtest.ui.inventory.list

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.zaico.codingtest.domain.inventory.Inventory
import jp.co.zaico.codingtest.domain.inventory.InventoryRepository

/**
 * 在庫一覧画面に表示する在庫概要を読み込むViewModel。
 *
 * 旧クラス名: `FirstViewModel`
 */
@HiltViewModel
class InventoryListViewModel @Inject constructor(
    private val inventoryRepository: InventoryRepository
) : ViewModel() {
    suspend fun getInventories(): List<Inventory> = inventoryRepository.getInventories()
}
