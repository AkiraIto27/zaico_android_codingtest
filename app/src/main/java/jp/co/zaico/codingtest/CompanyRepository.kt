package jp.co.zaico.codingtest

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface CompanyIdResult {
    data class Success(val companyId: Int) : CompanyIdResult
    data object Empty : CompanyIdResult
    data object ConfigurationFailure : CompanyIdResult
    data class HttpFailure(val statusCode: Int) : CompanyIdResult
    data object DecodeFailure : CompanyIdResult
    data object NetworkFailure : CompanyIdResult
}

class CompanyRepositoryException(val result: CompanyIdResult) : Exception()

class CompanyRepository(
    private val client: HttpClient,
    baseUrl: String,
    private val token: String,
    private val companiesPath: String,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')
    private val mutex = Mutex()
    private var cachedResult: CompanyIdResult? = null

    suspend fun getCompanyId(): CompanyIdResult = mutex.withLock {
        cachedResult?.let { return@withLock it }
        if (token.isBlank() || !normalizedBaseUrl.startsWith("https://")) {
            return@withLock CompanyIdResult.ConfigurationFailure
        }
        val response = try {
            client.get(normalizedBaseUrl + companiesPath) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return@withLock CompanyIdResult.NetworkFailure
        }
        if (response.status != HttpStatusCode.OK) {
            return@withLock CompanyIdResult.HttpFailure(response.status.value)
        }
        val body = try {
            response.bodyAsText()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return@withLock CompanyIdResult.NetworkFailure
        }
        return@withLock try {
            val companies = json.parseToJsonElement(body)
                .jsonObject["data"]?.jsonArray
                ?: return@withLock CompanyIdResult.DecodeFailure
            val companyId = companies.firstOrNull()?.jsonObject?.get("id")
                ?.jsonPrimitive?.intOrNull
                ?: run {
                    cachedResult = CompanyIdResult.Empty
                    return@withLock CompanyIdResult.Empty
                }
            CompanyIdResult.Success(companyId).also { cachedResult = it }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            CompanyIdResult.DecodeFailure
        }
    }

    suspend fun requireCompanyId(): Int = when (val result = getCompanyId()) {
        is CompanyIdResult.Success -> result.companyId
        else -> throw CompanyRepositoryException(result)
    }
}
