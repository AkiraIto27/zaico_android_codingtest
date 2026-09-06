package jp.co.zaico.codingtest.ui.inventory.create

/**
 * Validation errors for the inventory title.
 *
 * 旧クラス名: `AddTitleError`
 */
enum class InventoryCreateTitleError {
    Required,
    TooLong
}

/**
 * Request errors exposed by the inventory creation UI.
 *
 * 旧クラス名: `AddRequestError`
 */
enum class InventoryCreateRequestError {
    Configuration,
    EmptyCompany,
    Http,
    InvalidResponse,
    Network
}

/**
 * State rendered by the inventory creation screen.
 *
 * 旧クラス名: `AddUiState`
 */
data class InventoryCreateUiState(
    val title: String = "",
    val isSubmitting: Boolean = false,
    val titleError: InventoryCreateTitleError? = null,
    val requestError: InventoryCreateRequestError? = null,
    val createdInventoryId: Long? = null
)
