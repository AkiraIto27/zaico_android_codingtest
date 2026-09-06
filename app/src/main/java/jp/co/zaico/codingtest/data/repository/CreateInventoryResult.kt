package jp.co.zaico.codingtest.data.repository

sealed interface CreateInventoryResult {
    data class Success(val inventoryId: Long) : CreateInventoryResult
    data class HttpFailure(val statusCode: Int) : CreateInventoryResult
    data object ConfigurationFailure : CreateInventoryResult
    data object EmptyCompany : CreateInventoryResult
    data object DecodeFailure : CreateInventoryResult
    data object NetworkFailure : CreateInventoryResult
}
