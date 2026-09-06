package jp.co.zaico.codingtest.data.remote.mapper

import jp.co.zaico.codingtest.domain.inventory.Inventory
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

internal fun normalizeInventoryListRoot(root: JsonElement): JsonArray = when (root) {
    is JsonArray -> root
    is JsonObject -> root["data"]?.let { data ->
        data as? JsonArray ?: throw IllegalArgumentException("Inventory list data is not an array")
    } ?: JsonArray(listOf(root))
    else -> throw IllegalArgumentException("Inventory list response has an unsupported root")
}

internal fun JsonObject.toInventory(): Inventory {
    val id = getValue("id").jsonPrimitive.int
    val title = this["title"]?.jsonPrimitive?.content.orEmpty()
    val quantity = this["quantity"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return Inventory(id = id, title = title, quantity = quantity)
}
