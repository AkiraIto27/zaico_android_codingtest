package jp.co.zaico.codingtest.ui.inventory.create

/**
 * 在庫タイトルの入力エラー。
 *
 * 旧クラス名: `AddTitleError`
 */
enum class InventoryCreateTitleError {
    Required,
    TooLong
}

/**
 * 在庫作成画面に表示するリクエストエラー。
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
 * 在庫作成画面に表示するUI状態。
 *
 * 旧クラス名: `AddUiState`
 */
data class InventoryCreateUiState(
    val title: String = "",
    val isSubmitting: Boolean = false,
    val titleError: InventoryCreateTitleError? = null,
    val requestError: InventoryCreateRequestError? = null,
    val createdInventoryId: Long? = null,
    val requestErrorNotificationId: Long? = null,
    val emptyCompanyToastNotificationId: Long? = null,
    val successNotificationId: Long? = null
)
