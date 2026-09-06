package jp.co.zaico.codingtest.data.remote

internal sealed interface InventoryCreateRemoteResult {
    data class Success(val inventoryId: Long) : InventoryCreateRemoteResult
    data class HttpFailure(val statusCode: Int) : InventoryCreateRemoteResult
    data object DecodeFailure : InventoryCreateRemoteResult
    data object NetworkFailure : InventoryCreateRemoteResult
}

internal interface InventoryCreateRemoteService {
    suspend fun createInventory(companyId: Int, title: String): InventoryCreateRemoteResult
}
