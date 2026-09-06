package jp.co.zaico.codingtest

import java.io.Closeable

interface InventoryCreator : Closeable {
    suspend fun createInventory(title: String): CreateInventoryResult
}
