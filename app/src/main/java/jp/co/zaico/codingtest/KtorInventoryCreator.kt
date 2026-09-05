package jp.co.zaico.codingtest

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class KtorInventoryCreator(
    private val client: HttpClient,
    baseUrl: String,
    private val token: String,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : InventoryCreator {

    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    override suspend fun createInventory(title: String): CreateInventoryResult {
        if (token.isBlank() || !normalizedBaseUrl.startsWith("https://")) {
            return CreateInventoryResult.ConfigurationFailure
        }

        val response = try {
            client.post(normalizedBaseUrl + CREATE_INVENTORY_PATH) {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(encodeCreateInventoryRequest(CreateInventoryRequest(title)))
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return CreateInventoryResult.NetworkFailure
        }

        if (response.status != HttpStatusCode.OK) {
            return CreateInventoryResult.HttpFailure(response.status.value)
        }

        val responseBody = try {
            response.bodyAsText()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return CreateInventoryResult.NetworkFailure
        }

        return try {
            val decoded = json.decodeCreateInventoryResponse(responseBody)
            CreateInventoryResult.Success(decoded.dataId)
        } catch (_: SerializationException) {
            CreateInventoryResult.DecodeFailure
        } catch (_: IllegalArgumentException) {
            CreateInventoryResult.DecodeFailure
        }
    }

    override fun close() {
        client.close()
    }

    private companion object {
        // 今回の課題では、v2ではなくZAICO API v1の作成エンドポイントを意図的に使用する。
        const val CREATE_INVENTORY_PATH = "/api/v1/inventories"
    }
}
