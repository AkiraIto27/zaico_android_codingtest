package jp.co.zaico.codingtest.ui.inventory.list

import jp.co.zaico.codingtest.domain.inventory.Inventory

enum class InventoryListLoadState {
    Initial,
    Loading,
    Content,
    Empty,
    Refreshing,
    Failure
}

enum class InventoryListError {
    CompanyEmpty,
    LoadFailed
}

data class InventoryListUiState(
    val inventories: List<Inventory> = emptyList(),
    val loadState: InventoryListLoadState = InventoryListLoadState.Initial,
    val error: InventoryListError? = null,
    val errorNotificationId: Long? = null
)
