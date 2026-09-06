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
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val companyRepository: CompanyRepository
) : InventoryCreator {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')
    // 本番のbaseUrlはXMLリソースに定義した固定URLを使用する。

    override suspend fun createInventory(title: String): CreateInventoryResult {
        if (token.isBlank()) {
            return CreateInventoryResult.ConfigurationFailure
        }
        val companyId = when (val companyResult = companyRepository.getCompanyId()) {
            is CompanyIdResult.Success -> companyResult.companyId
            CompanyIdResult.Empty -> return CreateInventoryResult.EmptyCompany
            CompanyIdResult.ConfigurationFailure -> return CreateInventoryResult.ConfigurationFailure
            is CompanyIdResult.HttpFailure -> return CreateInventoryResult.HttpFailure(companyResult.statusCode)
            CompanyIdResult.DecodeFailure -> return CreateInventoryResult.DecodeFailure
            CompanyIdResult.NetworkFailure -> return CreateInventoryResult.NetworkFailure
        }
        val response = try {
            client.post("$normalizedBaseUrl/api/v2/orgs/companies/$companyId/inventories.json") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(encodeCreateInventoryRequest(CreateInventoryRequest(title)))
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return CreateInventoryResult.NetworkFailure
        }
        // API V2仕様書では作成成功は201 Createdだが、200 OKと正常なdata.idが返ったため、両方を許可する。
        // TODO: V2作成APIで200と201が返る条件、および仕様書との不一致をZAICOに確認する。
        // 同一タイトルでも再POSTすると別IDの在庫が作成されることを実測済み。
        // 201が重複登録を示すという根拠はなく、作成POSTの自動再送は行わない。
        if (
            response.status != HttpStatusCode.OK &&
            response.status != HttpStatusCode.Created
        ) {
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
            CreateInventoryResult.Success(json.decodeCreateInventoryResponse(responseBody).dataId)
        } catch (_: SerializationException) {
            CreateInventoryResult.DecodeFailure
        } catch (_: IllegalArgumentException) {
            CreateInventoryResult.DecodeFailure
        }
    }

    override fun close() {
        client.close()
    }
}
