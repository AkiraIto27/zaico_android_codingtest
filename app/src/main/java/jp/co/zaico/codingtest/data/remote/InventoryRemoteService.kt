package jp.co.zaico.codingtest.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import jp.co.zaico.codingtest.data.model.Inventory
import jp.co.zaico.codingtest.data.remote.dto.CreateInventoryRequest
import jp.co.zaico.codingtest.data.remote.mapper.decodeCreateInventoryResponse
import jp.co.zaico.codingtest.data.remote.mapper.encodeCreateInventoryRequest
import jp.co.zaico.codingtest.data.remote.mapper.normalizeInventoryListRoot
import jp.co.zaico.codingtest.data.remote.mapper.toInventory
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

internal interface InventoryRemoteService {
    suspend fun getInventories(companyId: Int): List<Inventory>

    suspend fun getInventory(companyId: Int, inventoryId: Int): Inventory
}

internal class KtorInventoryRemoteService(
    private val client: HttpClient,
    baseUrl: String,
    private val token: String,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : InventoryRemoteService, InventoryCreateRemoteService {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    override suspend fun getInventories(companyId: Int): List<Inventory> {
        val response = client.get(
            "$normalizedBaseUrl/api/v2/orgs/companies/$companyId/inventories.json"
        ) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        check(response.status == HttpStatusCode.OK) { "Inventory list request failed" }
        return normalizeInventoryListRoot(json.parseToJsonElement(response.bodyAsText()))
            .map { it.jsonObject.toInventory() }
    }

    override suspend fun getInventory(companyId: Int, inventoryId: Int): Inventory {
        val response = client.get(
            "$normalizedBaseUrl/api/v2/orgs/companies/$companyId/inventories/$inventoryId.json"
        ) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        check(response.status == HttpStatusCode.OK) { "Inventory detail request failed" }
        val data = json.parseToJsonElement(response.bodyAsText())
            .jsonObject.getValue("data").jsonObject
        return data.toInventory()
    }

    override suspend fun createInventory(
        companyId: Int,
        title: String
    ): InventoryCreateRemoteResult {
        val response = try {
            client.post("$normalizedBaseUrl/api/v2/orgs/companies/$companyId/inventories.json") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(encodeCreateInventoryRequest(CreateInventoryRequest(title)))
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return InventoryCreateRemoteResult.NetworkFailure
        }
        // API V2仕様書では作成成功は201 Createdだが、200 OKと正常なdata.idが返ったため、両方を許可する。
        // TODO: V2作成APIで200と201が返る条件、および仕様書との不一致をZAICOに確認する。
        if (response.status != HttpStatusCode.OK && response.status != HttpStatusCode.Created) {
            return InventoryCreateRemoteResult.HttpFailure(response.status.value)
        }
        val responseBody = try {
            response.bodyAsText()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return InventoryCreateRemoteResult.NetworkFailure
        }
        // 同一タイトルでも再POSTすると別IDの在庫が作成されることを実測済み。
        // 201が重複登録を示すという根拠はなく、作成POSTの自動再送は行わない。
        return try {
            InventoryCreateRemoteResult.Success(json.decodeCreateInventoryResponse(responseBody).dataId)
        } catch (_: SerializationException) {
            InventoryCreateRemoteResult.DecodeFailure
        } catch (_: IllegalArgumentException) {
            InventoryCreateRemoteResult.DecodeFailure
        }
    }
}
