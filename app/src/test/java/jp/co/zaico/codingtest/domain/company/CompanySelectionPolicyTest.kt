package jp.co.zaico.codingtest.domain.company

import jp.co.zaico.codingtest.domain.company.CompanySelectionResult.Empty
import jp.co.zaico.codingtest.domain.company.CompanySelectionResult.Selected
import org.junit.Assert.assertEquals
import org.junit.Test

class CompanySelectionPolicyTest {

    @Test
    fun 返却順の先頭にIDがある場合_先頭IDを選択する() {
        assertEquals(
            Selected(123),
            CompanySelectionPolicy.selectFirst(
                listOf(CompanyCandidate(123), CompanyCandidate(999))
            )
        )
    }

    @Test
    fun 空一覧または先頭IDがない場合_Emptyを返す() {
        assertEquals(Empty, CompanySelectionPolicy.selectFirst(emptyList()))
        assertEquals(Empty, CompanySelectionPolicy.selectFirst(listOf(CompanyCandidate(null))))
    }

    @Test
    fun 先頭IDが正常で後続要素が不正でも_先頭IDを維持する() {
        assertEquals(
            Selected(123),
            CompanySelectionPolicy.selectFirst(
                listOf(CompanyCandidate(123), CompanyCandidate(null))
            )
        )
    }
}
