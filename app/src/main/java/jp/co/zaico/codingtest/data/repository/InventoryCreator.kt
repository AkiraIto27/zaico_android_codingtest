package jp.co.zaico.codingtest.data.repository

import java.io.Closeable

interface InventoryCreator : Closeable {
    suspend fun createInventory(title: String): CreateInventoryResult
}
