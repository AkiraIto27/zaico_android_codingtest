package jp.co.zaico.codingtest.domain.company

data class CompanyCandidate(val id: Int?)

sealed interface CompanySelectionResult {
    data class Selected(val companyId: Int) : CompanySelectionResult
    data object Empty : CompanySelectionResult
}

object CompanySelectionPolicy {
    fun selectFirst(companies: List<CompanyCandidate>): CompanySelectionResult =
        companies.firstOrNull()?.id?.let { CompanySelectionResult.Selected(it) }
            ?: CompanySelectionResult.Empty
}
