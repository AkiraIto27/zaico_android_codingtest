package jp.co.zaico.codingtest

import android.content.Context
import androidx.lifecycle.ViewModel
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal sealed interface InventoryListResult {
    data class Success(val inventories: List<Inventory>) : InventoryListResult
    data object ConfigurationFailure : InventoryListResult
    data class HttpFailure(val statusCode: Int) : InventoryListResult
    data object DecodeFailure : InventoryListResult
    data object NetworkFailure : InventoryListResult
}

private class InventoryListLoadException : Exception()

internal fun decodeInventoryListResponse(
    statusCode: Int,
    rawBody: String
): InventoryListResult {
    if (statusCode != HttpStatusCode.OK.value) {
        return InventoryListResult.HttpFailure(statusCode)
    }

    return try {
        val root = Json.parseToJsonElement(rawBody)
        // V1の200例は単一オブジェクトだが、0件時は空配列と仕様に記載されている。
        val inventoryObjects: List<JsonObject> = when (root) {
            is JsonObject -> listOf(root)
            is JsonArray -> root.map { it.jsonObject }
            else -> return InventoryListResult.DecodeFailure
        }
        val inventories = inventoryObjects.map { inventory ->
            Inventory(
                id = requireNotNull(inventory["id"]).jsonPrimitive.content.toInt(),
                title = requireNotNull(inventory["title"]).jsonPrimitive.content,
                quantity = requireNotNull(inventory["quantity"]).jsonPrimitive.content
            )
        }

        InventoryListResult.Success(inventories)
    } catch (_: SerializationException) {
        InventoryListResult.DecodeFailure
    } catch (_: IllegalArgumentException) {
        InventoryListResult.DecodeFailure
    }
}

internal class KtorInventoryListLoader(
    private val client: HttpClient,
    baseUrl: String,
    private val token: String
) : AutoCloseable {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    suspend fun load(): InventoryListResult {
        if (token.isBlank() || !normalizedBaseUrl.startsWith("https://")) {
            return InventoryListResult.ConfigurationFailure
        }

        return try {
            val response: HttpResponse = client.get("$normalizedBaseUrl/api/v1/inventories") {
                header("Authorization", "Bearer $token")
            }

            if (response.status == HttpStatusCode.OK) {
                decodeInventoryListResponse(
                    statusCode = response.status.value,
                    rawBody = response.bodyAsText()
                )
            } else {
                InventoryListResult.HttpFailure(response.status.value)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            InventoryListResult.NetworkFailure
        }
    }

    override fun close() {
        client.close()
    }
}

class FirstViewModel(
    private val context: Context
): ViewModel() {

    // データ取得
    suspend fun getInventories(): List<Inventory> {
        val loader = KtorInventoryListLoader(
            client = HttpClient(Android) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            },
            baseUrl = context.getString(R.string.api_endpoint),
            token = context.getString(R.string.api_token)
        )

        return try {
            when (val result = loader.load()) {
                is InventoryListResult.Success -> result.inventories
                InventoryListResult.ConfigurationFailure,
                is InventoryListResult.HttpFailure,
                InventoryListResult.DecodeFailure,
                InventoryListResult.NetworkFailure -> throw InventoryListLoadException()
            }
        } finally {
            loader.close()
        }
    }

}
