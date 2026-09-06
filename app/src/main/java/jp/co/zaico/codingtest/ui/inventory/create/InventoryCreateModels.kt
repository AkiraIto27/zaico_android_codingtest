package jp.co.zaico.codingtest.ui.inventory.create

enum class InventoryCreateTitleError {
    Required,
    TooLong
}

enum class InventoryCreateRequestError {
    Configuration,
    EmptyCompany,
    Http,
    InvalidResponse,
    Network
}

data class InventoryCreateUiState(
    val title: String = "",
    val isSubmitting: Boolean = false,
    val titleError: InventoryCreateTitleError? = null,
    val requestError: InventoryCreateRequestError? = null,
    val createdInventoryId: Long? = null
)
