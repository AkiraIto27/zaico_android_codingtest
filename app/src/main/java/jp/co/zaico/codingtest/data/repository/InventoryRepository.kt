package jp.co.zaico.codingtest.data.repository

import jp.co.zaico.codingtest.data.model.Inventory

interface InventoryRepository {
    suspend fun getInventories(): List<Inventory>

    suspend fun getInventory(inventoryId: Int): Inventory
}
