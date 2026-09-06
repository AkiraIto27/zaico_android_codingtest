package jp.co.zaico.codingtest.data.repository

import io.ktor.client.HttpClient
import jp.co.zaico.codingtest.data.remote.CompanyRemoteResult
import jp.co.zaico.codingtest.data.remote.CompanyRemoteService
import jp.co.zaico.codingtest.data.remote.KtorCompanyRemoteService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

sealed interface CompanyIdResult {
    data class Success(val companyId: Int) : CompanyIdResult
    data object Empty : CompanyIdResult
    data object ConfigurationFailure : CompanyIdResult
    data class HttpFailure(val statusCode: Int) : CompanyIdResult
    data object DecodeFailure : CompanyIdResult
    data object NetworkFailure : CompanyIdResult
}

class CompanyRepositoryException(val result: CompanyIdResult) : Exception()

class CompanyRepository internal constructor(
    private val remote: CompanyRemoteService,
    private val token: String
) {
    constructor(
        client: HttpClient,
        baseUrl: String,
        token: String,
        companiesPath: String,
        json: Json = Json { ignoreUnknownKeys = true }
    ) : this(
        remote = KtorCompanyRemoteService(client, baseUrl, token, companiesPath, json),
        token = token
    )

    private val mutex = Mutex()
    private var cachedResult: CompanyIdResult? = null

    suspend fun getCompanyId(): CompanyIdResult = mutex.withLock {
        cachedResult?.let { return@withLock it }
        if (token.isBlank()) {
            return@withLock CompanyIdResult.ConfigurationFailure
        }

        val result = try {
            when (val remoteResult = remote.getCompanyId()) {
                is CompanyRemoteResult.Success -> CompanyIdResult.Success(remoteResult.companyId)
                CompanyRemoteResult.Empty -> CompanyIdResult.Empty
                is CompanyRemoteResult.HttpFailure -> CompanyIdResult.HttpFailure(remoteResult.statusCode)
                CompanyRemoteResult.DecodeFailure -> CompanyIdResult.DecodeFailure
                CompanyRemoteResult.NetworkFailure -> CompanyIdResult.NetworkFailure
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        }
        if (result is CompanyIdResult.Success || result == CompanyIdResult.Empty) {
            cachedResult = result
        }
        return@withLock result
    }

    suspend fun requireCompanyId(): Int = when (val result = getCompanyId()) {
        is CompanyIdResult.Success -> result.companyId
        else -> throw CompanyRepositoryException(result)
    }
}
