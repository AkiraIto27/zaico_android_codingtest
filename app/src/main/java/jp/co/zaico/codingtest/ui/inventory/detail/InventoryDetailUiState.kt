package jp.co.zaico.codingtest.ui.inventory.detail

import jp.co.zaico.codingtest.domain.inventory.Inventory

enum class InventoryDetailLoadState {
    Initial,
    Loading,
    Success,
    Failure
}

enum class InventoryDetailError {
    InvalidId,
    CompanyEmpty,
    LoadFailed
}

data class InventoryDetailUiState(
    val inventory: Inventory? = null,
    val loadState: InventoryDetailLoadState = InventoryDetailLoadState.Initial,
    val error: InventoryDetailError? = null,
    val errorNotificationId: Long? = null
)
