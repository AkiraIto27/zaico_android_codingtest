package jp.co.zaico.codingtest

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
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
    val dataId = parseToJsonElement(rawResponse)
        .jsonObject["data"]
        ?.jsonObject
        ?.get("id")
        ?.jsonPrimitive
        ?.longOrNull
        ?: throw SerializationException("Create response has no valid data.id")
    return CreateInventoryResponse(dataId)
}

sealed interface CreateInventoryResult {
    data class Success(val inventoryId: Long) : CreateInventoryResult
    data class HttpFailure(val statusCode: Int) : CreateInventoryResult
    data object ConfigurationFailure : CreateInventoryResult
    data object EmptyCompany : CreateInventoryResult
    data object DecodeFailure : CreateInventoryResult
    data object NetworkFailure : CreateInventoryResult
}
