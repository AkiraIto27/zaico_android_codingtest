package jp.co.zaico.codingtest.data.repository

interface InventoryCreator {
    suspend fun createInventory(title: String): CreateInventoryResult
}
