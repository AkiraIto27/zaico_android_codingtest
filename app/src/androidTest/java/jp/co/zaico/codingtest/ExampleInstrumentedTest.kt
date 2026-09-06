package jp.co.zaico.codingtest

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * 計装テストをAndroid端末上で実行する。
 *
 * 詳細は[テストドキュメント](http://d.android.com/tools/testing)を参照する。
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun `アプリコンテキストを取得する_パッケージ名が一致する`() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("jp.co.zaico.codingtest", appContext.packageName)
    }
}
