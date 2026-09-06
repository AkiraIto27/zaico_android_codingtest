package jp.co.zaico.codingtest.domain.inventory

/**
 * 在庫一覧と在庫詳細を取得するDomain層の契約。
 */
interface InventoryRepository {
    /**
     * 在庫一覧を取得する。
     *
     * @return 在庫一覧。
     */
    suspend fun getInventories(): List<Inventory>

    /**
     * 指定したIDの在庫を取得する。
     *
     * @param inventoryId 取得対象の在庫ID。
     * @return 指定したIDの在庫。
     */
    suspend fun getInventory(inventoryId: Int): Inventory
}
