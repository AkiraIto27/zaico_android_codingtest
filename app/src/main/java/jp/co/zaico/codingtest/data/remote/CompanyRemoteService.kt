package jp.co.zaico.codingtest.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import jp.co.zaico.codingtest.data.remote.dto.CompanyRemoteCompany
import jp.co.zaico.codingtest.data.remote.CompanyRemoteResult.DecodeFailure
import jp.co.zaico.codingtest.data.remote.CompanyRemoteResult.NetworkFailure
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import jp.co.zaico.codingtest.di.ApiBaseUrl
import jp.co.zaico.codingtest.di.ApiToken
import jp.co.zaico.codingtest.di.CompaniesPath
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CompanyRemoteResult {
    data class Success(val companies: List<CompanyRemoteCompany>) : CompanyRemoteResult
    data object Empty : CompanyRemoteResult
    data class HttpFailure(val statusCode: Int) : CompanyRemoteResult
    data object DecodeFailure : CompanyRemoteResult
    data object NetworkFailure : CompanyRemoteResult
}

/**
 * 拠点一覧をAPIから取得する責務を持つリモートサービス。
 */
interface CompanyRemoteService {
    /**
     * APIから拠点一覧を取得する。
     *
     * @return 拠点一覧または取得失敗を表す結果。
     */
    suspend fun getCompanies(): CompanyRemoteResult
}

@Singleton
class KtorCompanyRemoteService @Inject constructor(
    private val client: HttpClient,
    @ApiBaseUrl baseUrl: String,
    @ApiToken private val token: String,
    @CompaniesPath private val companiesPath: String,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : CompanyRemoteService {
    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    override suspend fun getCompanies(): CompanyRemoteResult {
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
            // 先頭要素は従来のEmpty/DecodeFailure分類を維持し、後続要素は
            // 解釈できない値をnullとして扱う。先頭が正常なら、後続要素の不正な形で
            // 既存の成功結果を失わない。
            val firstCompany = companies.firstOrNull()
                ?: return CompanyRemoteResult.Success(emptyList())
            val firstId = firstCompany.jsonObject["id"]?.jsonPrimitive?.intOrNull
            val laterCompanies = companies.drop(1).map { company ->
                CompanyRemoteCompany(
                    id = runCatching {
                        company.jsonObject["id"]?.jsonPrimitive?.intOrNull
                    }.getOrNull()
                )
            }
            CompanyRemoteResult.Success(
                companies = listOf(CompanyRemoteCompany(firstId)) + laterCompanies
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            DecodeFailure
        }
    }

}
