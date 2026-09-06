package jp.co.zaico.codingtest.data.remote.mapper

import jp.co.zaico.codingtest.data.remote.dto.CreateInventoryRequest
import jp.co.zaico.codingtest.data.remote.dto.CreateInventoryResponse
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

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
