package jp.co.zaico.codingtest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InventoryCreationArchitectureTest {

    private val repositoryRoot: File = generateSequence(
        File(requireNotNull(System.getProperty("user.dir")))
    ) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun 追加画面を構成する場合_ActivityにFragmentを配置してタイトル入力と登録操作を用意する() {
        val activityLayout = source("app/src/main/res/layout/activity_add.xml")
        val fragmentLayout = source("app/src/main/res/layout/fragment_add.xml")
        val mainActivityLayout = source("app/src/main/res/layout/activity_main.xml")
        val fragmentSource = source("app/src/main/java/jp/co/zaico/codingtest/AddFragment.kt")
        val activitySource = source("app/src/main/java/jp/co/zaico/codingtest/MainActivity.kt")

        assertTrue(activityLayout.contains("androidx.fragment.app.FragmentContainerView"))
        assertTrue(activityLayout.contains("android:name=\"jp.co.zaico.codingtest.AddFragment\""))
        assertTrue(fragmentLayout.contains("@+id/titleInputLayout"))
        assertTrue(fragmentLayout.contains("@+id/titleEditText"))
        assertTrue(fragmentLayout.contains("@+id/submitButton"))
        assertTrue(fragmentLayout.contains("@+id/progressIndicator"))
        assertTrue(fragmentLayout.contains("@+id/errorText"))
        assertTrue(
            Regex(
                "<TextView(?=[^>]*android:id=\\\"@\\+id/errorText\\\")" +
                    "(?=[^>]*android:accessibilityLiveRegion=\\\"polite\\\")[^>]*>"
            ).containsMatchIn(fragmentLayout)
        )
        assertTrue(fragmentLayout.contains("android:contentDescription=\"@string/add_submitting\""))
        assertTrue(activityLayout.contains("android:fitsSystemWindows=\"true\""))
        assertTrue(mainActivityLayout.contains("android:contentDescription=\"@string/add_inventory\""))
        assertTrue(fragmentSource.contains("AddViewModel"))
        assertTrue(fragmentSource.contains("viewLifecycleOwner.lifecycleScope"))
        assertTrue(fragmentSource.contains("AddTitleError.Required"))
        assertTrue(fragmentSource.contains("AddTitleError.TooLong"))
        assertTrue(
            Regex("submitButton\\.setOnClickListener\\s*\\{\\s*viewModel\\.submit\\(\\)\\s*}")
                .containsMatchIn(fragmentSource)
        )
        assertTrue(fragmentSource.contains("private fun updateView(state: AddUiState)"))
        assertTrue(fragmentSource.contains("viewModel.uiState.collect(::updateView)"))
        val updateViewSource = fragmentSource.substringAfter("private fun updateView(state: AddUiState)")
        assertTrue(updateViewSource.contains("currentBinding.apply"))
        assertTrue(updateViewSource.contains("errorText.isVisible = requestError != null"))
        assertTrue(updateViewSource.contains("requestError?.let(errorText::setText)"))
        assertTrue(updateViewSource.contains("titleEditText.setText(state.title)"))
        assertTrue(updateViewSource.contains("titleInputLayout.error = when (state.titleError)"))
        assertTrue(activitySource.contains("startActivity(AddActivity.createIntent(this))"))
    }

    @Test
    fun 作成エンドポイントを定義する場合_API_v1を使用する選択をコメントに明記する() {
        val source = source("app/src/main/java/jp/co/zaico/codingtest/KtorInventoryCreator.kt")

        assertTrue(source.contains("/api/v1/inventories"))
        assertTrue(
            Regex("v2ではなくZAICO API v1[^\\n]*\\n\\s*const val CREATE_INVENTORY_PATH")
                .containsMatchIn(source)
        )
        assertFalse(source.contains("/api/v2/"))
        assertFalse(source.contains("company_id"))
        assertFalse(source.contains("startsWith(\"https://\")"))
    }

    @Test
    fun 一覧画面へ復帰した場合_メインスレッド外で再取得し登録成功時に追加画面を閉じる() {
        val source = source("app/src/main/java/jp/co/zaico/codingtest/FirstFragment.kt")
        val onResume = source.substringAfter("override fun onResume()")
            .substringBefore("override fun onDestroyView()")
        val addFragment = source("app/src/main/java/jp/co/zaico/codingtest/AddFragment.kt")
        val addViewModel = source("app/src/main/java/jp/co/zaico/codingtest/AddViewModel.kt")

        assertTrue(source.contains("override fun onResume()"))
        assertTrue(source.contains("viewLifecycleOwner.lifecycleScope"))
        assertTrue(source.contains("override fun onDestroyView()"))
        assertTrue(onResume.contains("loadJob?.cancel()"))
        assertTrue(
            Regex("withContext\\(Dispatchers\\.IO\\)\\s*\\{\\s*viewModel\\.getInventories\\(\\)\\s*}")
                .containsMatchIn(onResume)
        )
        assertTrue(onResume.contains("adapter.submitList(inventories)"))
        val onDestroyView = source.substringAfter("override fun onDestroyView()")
            .substringBefore("val diff_util")
        assertTrue(onDestroyView.contains("loadJob?.cancel()"))
        assertTrue(onDestroyView.contains("_binding?.recyclerView?.adapter = null"))
        assertFalse(addFragment.contains("setResult("))
        val successUpdate = addFragment.substringAfter(
            "if (state.createdInventoryId != null && !completionHandled)"
        )
        assertTrue(successUpdate.contains("requireActivity().finish()"))
        assertTrue(addViewModel.contains("viewModelScope.launch"))
        assertFalse(addViewModel.contains("submissionScope"))
        assertFalse(addViewModel.contains("CoroutineStart.UNDISPATCHED"))
        assertTrue(addViewModel.contains("createdInventoryId != null"))
    }

    @Test
    fun APIトークンを設定する場合_Git管理外の設定から読み込みログに出さない() {
        val buildScript = source("app/build.gradle.kts")
        val trackedApiResources = source("app/src/main/res/values/api.xml")
        val creatorSource = source("app/src/main/java/jp/co/zaico/codingtest/KtorInventoryCreator.kt")
        val addFragmentSource = source("app/src/main/java/jp/co/zaico/codingtest/AddFragment.kt")
        val gitignore = source(".gitignore")
        val tokenConsumers = File(repositoryRoot, "app/src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .filter { it.readText().contains("R.string.api_token") }
            .toList()

        assertTrue(buildScript.contains("local.properties"))
        assertTrue(buildScript.contains("ZAICO_API_TOKEN"))
        assertTrue(buildScript.contains("zaico.api.token"))
        assertTrue(buildScript.contains("resValue(\"string\", \"api_token\""))
        assertTrue(
            buildScript.indexOf("ZAICO_API_TOKEN") < buildScript.indexOf("zaico.api.token")
        )
        assertFalse(trackedApiResources.contains("name=\"api_token\""))
        assertTrue(gitignore.lineSequence().any { it.trim() in setOf("local.properties", "/local.properties") })
        assertFalse(buildScript.contains("println("))
        assertFalse(buildScript.contains("System.out"))
        assertFalse(buildScript.contains("System.err"))
        assertFalse(buildScript.contains("logger."))
        assertFalse(creatorSource.contains("Logging"))
        assertFalse(creatorSource.contains("println("))
        assertFalse(creatorSource.contains("Log."))
        assertFalse(addFragmentSource.contains("println("))
        assertFalse(addFragmentSource.contains("Log."))
        assertTrue(tokenConsumers.isNotEmpty())
        tokenConsumers.forEach { file ->
            val tokenConsumerSource = file.readText()
            assertFalse("Token consumer logs with Android Log: ${file.path}", tokenConsumerSource.contains("Log."))
            assertFalse("Token consumer prints to stdout: ${file.path}", tokenConsumerSource.contains("println("))
            assertFalse("Token consumer uses System.out: ${file.path}", tokenConsumerSource.contains("System.out"))
            assertFalse("Token consumer uses System.err: ${file.path}", tokenConsumerSource.contains("System.err"))
        }
        assertTrue(gitExitCode("check-ignore", "-q", "local.properties") == 0)
        assertTrue(gitExitCode("ls-files", "--error-unmatch", "local.properties") != 0)
    }

    private fun source(relativePath: String): String {
        val file = File(repositoryRoot, relativePath)
        assertTrue("Missing required source: $relativePath", file.isFile)
        return file.readText()
    }

    private fun gitExitCode(vararg arguments: String): Int {
        val process = ProcessBuilder(listOf("git", *arguments))
            .directory(repositoryRoot)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { it.readText() }
        return process.waitFor()
    }
}
