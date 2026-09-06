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
    // 本番のbaseUrlはXMLリソースに定義した固定URLを使用する。
    private val mutex = Mutex()
    // プロセス内のみで取得結果を保持し、プロセス終了後の起動時に再取得する。
    // アプリ内に拠点追加・切り替え機能がないため、起動中は自動更新しない。
    private var cachedResult: CompanyIdResult? = null

    suspend fun getCompanyId(): CompanyIdResult = mutex.withLock {
        cachedResult?.let { return@withLock it }
        if (token.isBlank()) {
            return@withLock CompanyIdResult.ConfigurationFailure
        }
        val body = try {
            val response = client.get(normalizedBaseUrl + companiesPath) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            if (response.status != HttpStatusCode.OK) {
                return@withLock CompanyIdResult.HttpFailure(response.status.value)
            }
            response.bodyAsText()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return@withLock CompanyIdResult.NetworkFailure
        }

        val result = decodeCompanyId(body)
        if (result is CompanyIdResult.Success || result == CompanyIdResult.Empty) {
            cachedResult = result
        }
        return@withLock result
    }

    private fun decodeCompanyId(body: String): CompanyIdResult {
        return try {
            val companies = json.parseToJsonElement(body)
                .jsonObject["data"]?.jsonArray
                ?: return CompanyIdResult.DecodeFailure
            // サーバー側要件では拠点が最低1件存在するため、通常は空一覧を想定しない。
            // 今回の特別仕様として、拠点一覧APIが返す先頭要素のIDを使用する。
            // TODO: 先頭要素がWebで最後に登録した拠点になるか、APIの返却順を確認する。
            val companyId = companies.firstOrNull()?.jsonObject?.get("id")
                ?.jsonPrimitive?.intOrNull
                ?: return CompanyIdResult.Empty
            CompanyIdResult.Success(companyId)
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
