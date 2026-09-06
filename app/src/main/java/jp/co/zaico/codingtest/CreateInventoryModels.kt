package jp.co.zaico.codingtest

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal data class CreateInventoryRequest(
    val title: String
)

internal data class CreateInventoryResponse(
    val dataId: Long
)

internal fun encodeCreateInventoryRequest(request: CreateInventoryRequest): String =
    buildJsonObject {
        put("title", request.title)
    }.toString()

internal fun Json.decodeCreateInventoryResponse(rawResponse: String): CreateInventoryResponse {
    val root = parseToJsonElement(rawResponse) as? JsonObject
    val data = root?.get("data") as? JsonObject
    val id = data?.get("id") as? JsonPrimitive
    val dataId = id?.longOrNull
        ?: throw SerializationException("Create response has no valid data.id")
    return CreateInventoryResponse(dataId)
}

internal fun JsonObject.toInventory(): Inventory {
    val id = getValue("id").jsonPrimitive.int
    val title = this["title"]?.jsonPrimitive?.content.orEmpty()
    val quantity = this["quantity"]?.jsonPrimitive?.contentOrNull.orEmpty()
    return Inventory(id = id, title = title, quantity = quantity)
}

sealed interface CreateInventoryResult {
    data class Success(val inventoryId: Long) : CreateInventoryResult
    data class HttpFailure(val statusCode: Int) : CreateInventoryResult
    data object ConfigurationFailure : CreateInventoryResult
    data object EmptyCompany : CreateInventoryResult
    data object DecodeFailure : CreateInventoryResult
    data object NetworkFailure : CreateInventoryResult
}
