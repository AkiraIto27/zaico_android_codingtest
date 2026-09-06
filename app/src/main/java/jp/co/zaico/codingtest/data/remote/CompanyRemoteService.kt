package jp.co.zaico.codingtest.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import jp.co.zaico.codingtest.data.remote.CompanyRemoteResult.DecodeFailure
import jp.co.zaico.codingtest.data.remote.CompanyRemoteResult.Empty
import jp.co.zaico.codingtest.data.remote.CompanyRemoteResult.NetworkFailure
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal sealed interface CompanyRemoteResult {
    data class Success(val companyId: Int) : CompanyRemoteResult
    data object Empty : CompanyRemoteResult
    data class HttpFailure(val statusCode: Int) : CompanyRemoteResult
    data object DecodeFailure : CompanyRemoteResult
    data object NetworkFailure : CompanyRemoteResult
}

internal interface CompanyRemoteService {
    suspend fun getCompanyId(): CompanyRemoteResult
}

internal class KtorCompanyRemoteService(
    private val client: HttpClient,
    baseUrl: String,
    private val token: String,
    private val companiesPath: String,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : CompanyRemoteService {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    override suspend fun getCompanyId(): CompanyRemoteResult {
        val body = try {
            val response = client.get(normalizedBaseUrl + companiesPath) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status != HttpStatusCode.OK) {
                return CompanyRemoteResult.HttpFailure(response.status.value)
            }
            response.bodyAsText()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return NetworkFailure
        }

        return try {
            val companies = json.parseToJsonElement(body)
                .jsonObject["data"]?.jsonArray
                ?: return DecodeFailure
            val companyId = companies.firstOrNull()?.jsonObject?.get("id")
                ?.jsonPrimitive?.intOrNull
                ?: return Empty
            CompanyRemoteResult.Success(companyId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            DecodeFailure
        }
    }
}
