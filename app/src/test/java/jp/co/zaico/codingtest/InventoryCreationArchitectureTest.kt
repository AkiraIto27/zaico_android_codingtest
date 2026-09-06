package jp.co.zaico.codingtest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@Suppress("NonAsciiCharacters", "TestFunctionName")
class InventoryCreationArchitectureTest {

    private val repositoryRoot: File = generateSequence(
        File(requireNotNull(System.getProperty("user.dir")))
    ) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun KDocコメントを確認する_説明文が日本語で記述される() {
        val kdocPattern = Regex("/\\*\\*(.*?)\\*/", RegexOption.DOT_MATCHES_ALL)
        val japaneseTextPattern = Regex("[\\u3040-\\u30ff\\u4e00-\\u9fff]")
        val kdocLines = File(repositoryRoot, "app/src")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                kdocPattern.findAll(file.readText()).flatMap { comment ->
                    comment.groupValues[1].lineSequence()
                        .map { it.trim().removePrefix("*").trim() }
                        .filter { it.isNotEmpty() }
                        .asIterable()
                }
            }
            .toList()

        assertTrue("KDocコメントが見つかりません", kdocLines.isNotEmpty())
        kdocLines.forEach { line ->
            assertTrue("KDocの説明文が日本語ではありません: $line", japaneseTextPattern.containsMatchIn(line))
        }
    }

    @Test
    fun 作成画面の構造を確認する_Activityに入力フォームと登録操作を配置する() {
        val activityLayout = source("app/src/main/res/layout/activity_inventory_create.xml")
        val fragmentLayout = source("app/src/main/res/layout/fragment_inventory_create.xml")
        val mainActivityLayout = source("app/src/main/res/layout/activity_main.xml")
        val fragmentSource = source(
            "app/src/main/java/jp/co/zaico/codingtest/ui/inventory/create/InventoryCreateFragment.kt"
        )
        val activitySource = source(
            "app/src/main/java/jp/co/zaico/codingtest/ui/main/MainActivity.kt"
        )
        val manifest = source("app/src/main/AndroidManifest.xml")
        val navigation = source("app/src/main/res/navigation/nav_graph.xml")
        val listFragmentSource = source(
            "app/src/main/java/jp/co/zaico/codingtest/ui/inventory/list/InventoryListFragment.kt"
        )
        val detailFragmentSource = source(
            "app/src/main/java/jp/co/zaico/codingtest/ui/inventory/detail/InventoryDetailFragment.kt"
        )

        assertTrue(activityLayout.contains("androidx.fragment.app.FragmentContainerView"))
        assertTrue(
            activityLayout.contains(
                "android:name=\"jp.co.zaico.codingtest.ui.inventory.create.InventoryCreateFragment\""
            )
        )
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
        assertTrue(
            fragmentLayout.contains(
                "android:contentDescription=\"@string/inventory_create_submitting\""
            )
        )
        assertTrue(activityLayout.contains("android:fitsSystemWindows=\"true\""))
        assertTrue(
            mainActivityLayout.contains(
                "android:contentDescription=\"@string/inventory_create_add\""
            )
        )
        assertTrue(mainActivityLayout.contains("tools:context=\".ui.main.MainActivity\""))
        assertTrue(fragmentSource.contains("InventoryCreateViewModel"))
        assertTrue(fragmentSource.contains("viewLifecycleOwner.lifecycleScope"))
        assertTrue(fragmentSource.contains("InventoryCreateTitleError.Required"))
        assertTrue(fragmentSource.contains("InventoryCreateTitleError.TooLong"))
        assertTrue(
            Regex("submitButton\\.setOnClickListener\\s*\\{\\s*viewModel\\.submit\\(\\)\\s*}")
                .containsMatchIn(fragmentSource)
        )
        assertTrue(fragmentSource.contains("viewModel.uiState.collect"))
        assertTrue(
            activitySource.contains(
                "startActivity(InventoryCreateActivity.createIntent(this))"
            )
        )
        assertTrue(manifest.contains(".ui.main.MainActivity"))
        assertTrue(manifest.contains(".ui.inventory.create.InventoryCreateActivity"))
        assertTrue(manifest.contains("android.intent.action.MAIN"))
        assertTrue(manifest.contains("android.intent.category.LAUNCHER"))
        assertTrue(navigation.contains("inventory_list_fragment"))
        assertTrue(navigation.contains("inventory_detail_fragment"))
        assertTrue(navigation.contains("action_inventory_list_to_inventory_detail"))
        assertTrue(navigation.contains("action_inventory_detail_to_inventory_list"))
        assertTrue(listFragmentSource.contains("FragmentInventoryListBinding"))
        assertTrue(detailFragmentSource.contains("FragmentInventoryDetailBinding"))
    }

    @Test
    fun MainActivityのtoolsコンテキスト_新しいパッケージを参照する() {
        assertTrue(
            source("app/src/main/res/layout/activity_main.xml")
                .contains("tools:context=\".ui.main.MainActivity\"")
        )
    }

    @Test
    fun 会社選択ポリシーの境界_純KotlinでRemoteやAndroidへ依存しない() {
        val policySource = source(
            "app/src/main/java/jp/co/zaico/codingtest/domain/company/CompanySelectionPolicy.kt"
        )

        assertTrue(policySource.contains("object CompanySelectionPolicy"))
        assertTrue(policySource.contains("fun selectFirst"))
        assertFalse(policySource.contains("io.ktor"))
        assertFalse(policySource.contains("android."))
    }

    @Test
    fun 在庫契約とモデルの配置を確認する_Domain層に定義する() {
        val domainInventory = source(
            "app/src/main/java/jp/co/zaico/codingtest/domain/inventory/Inventory.kt"
        )
        val domainRepository = source(
            "app/src/main/java/jp/co/zaico/codingtest/domain/inventory/InventoryRepository.kt"
        )
        val dataModel = File(repositoryRoot, "app/src/main/java/jp/co/zaico/codingtest/data/model/Inventory.kt")
        val dataRepository = File(
            repositoryRoot,
            "app/src/main/java/jp/co/zaico/codingtest/data/repository/InventoryRepository.kt"
        )
        val dataSourcePaths = listOf(
            "app/src/main/java/jp/co/zaico/codingtest/data/remote/InventoryRemoteService.kt",
            "app/src/main/java/jp/co/zaico/codingtest/data/remote/mapper/InventoryResponseMapper.kt",
            "app/src/main/java/jp/co/zaico/codingtest/data/repository/DefaultInventoryRepository.kt"
        )
        val dataInventoryType = "jp.co.zaico.codingtest.data." + "model.Inventory"

        assertTrue(domainInventory.contains("package jp.co.zaico.codingtest.domain.inventory"))
        assertTrue(domainInventory.contains("data class Inventory"))
        assertTrue(domainRepository.contains("package jp.co.zaico.codingtest.domain.inventory"))
        assertTrue(domainRepository.contains("interface InventoryRepository"))
        assertFalse(dataModel.exists())
        assertFalse(dataRepository.exists())
        dataSourcePaths.forEach { path ->
            assertFalse(source(path).contains(dataInventoryType))
        }
    }

    @Test
    fun 会社Repositoryの契約と実装配置を確認する_Domain契約とData実装に分離する() {
        val domainRepository = source(
            "app/src/main/java/jp/co/zaico/codingtest/domain/company/CompanyRepository.kt"
        )
        val domainResult = source(
            "app/src/main/java/jp/co/zaico/codingtest/domain/company/CompanyIdResult.kt"
        )
        val domainException = source(
            "app/src/main/java/jp/co/zaico/codingtest/domain/company/CompanyRepositoryException.kt"
        )
        val dataImplementation = source(
            "app/src/main/java/jp/co/zaico/codingtest/data/repository/CompanyRepositoryImpl.kt"
        )
        val oldDataRepository = File(
            repositoryRoot,
            "app/src/main/java/jp/co/zaico/codingtest/data/repository/CompanyRepository.kt"
        )

        assertTrue(domainRepository.contains("package jp.co.zaico.codingtest.domain.company"))
        assertTrue(domainRepository.contains("interface CompanyRepository"))
        assertTrue(domainResult.contains("sealed interface CompanyIdResult"))
        assertTrue(domainException.contains("class CompanyRepositoryException"))
        assertTrue(dataImplementation.contains("class CompanyRepositoryImpl"))
        assertTrue(dataImplementation.contains(") : CompanyRepository"))
        assertTrue(
            dataImplementation.contains(
                "import jp.co.zaico.codingtest.domain.company.CompanyRepository"
            )
        )
        assertFalse(oldDataRepository.exists())
        listOf(domainRepository, domainResult, domainException).forEach { source ->
            assertFalse(source.contains("jp.co.zaico.codingtest.data."))
            assertFalse(source.contains("io.ktor"))
            assertFalse(source.contains("android."))
        }
    }

    @Test
    fun `V2作成APIと拠点ID_200と201を成功判定する`() {
        val remoteSource = source(
            "app/src/main/java/jp/co/zaico/codingtest/data/remote/InventoryRemoteService.kt"
        )
        val creatorSource = source(
            "app/src/main/java/jp/co/zaico/codingtest/data/repository/KtorInventoryCreator.kt"
        )

        assertTrue(remoteSource.contains("/api/v2/orgs/companies/"))
        assertTrue(remoteSource.contains("/inventories.json"))
        assertTrue(creatorSource.contains("companyRepository.getCompanyId()"))
        assertTrue(remoteSource.contains("HttpStatusCode.OK"))
        assertTrue(remoteSource.contains("HttpStatusCode.Created"))
        assertTrue(remoteSource.contains("decodeCreateInventoryResponse"))
    }

    @Test
    fun `一覧画面を再開する_メインスレッド外で在庫一覧を再取得する`() {
        val fragmentSource = source(
            "app/src/main/java/jp/co/zaico/codingtest/ui/inventory/list/InventoryListFragment.kt"
        )
        val viewModelSource = source(
            "app/src/main/java/jp/co/zaico/codingtest/ui/inventory/list/InventoryListViewModel.kt"
        )
        val adapterSource = source(
            "app/src/main/java/jp/co/zaico/codingtest/ui/inventory/list/InventoryListAdapter.kt"
        )
        assertTrue(fragmentSource.contains("override fun onResume()"))
        assertTrue(fragmentSource.contains("viewLifecycleOwner.lifecycleScope"))
        assertTrue(fragmentSource.contains("override fun onDestroyView()"))
        assertTrue(fragmentSource.contains("loadJob?.cancel()"))
        assertTrue(fragmentSource.contains("adapter.submitList(inventories)"))
        assertTrue(fragmentSource.contains("_binding?.recyclerView?.adapter = null"))
        assertTrue(viewModelSource.contains("suspend fun getInventories()"))
        assertTrue(viewModelSource.contains("InventoryRepository"))
        assertFalse(viewModelSource.contains("android.content.Context"))
        assertFalse(viewModelSource.contains("HttpClient"))
        assertFalse(viewModelSource.contains("Json"))
        assertTrue(adapterSource.contains("InventoryListDiffCallback"))
        assertTrue(adapterSource.contains("oldItem.id == newItem.id"))
    }

    @Test
    fun `旧UI宣言と旧リソース参照_有効なソースに残さない`() {
        val forbiddenFragments = listOf(
            "Add" + "Activity",
            "Add" + "Fragment",
            "Add" + "ViewModel",
            "First" + "Fragment",
            "First" + "ViewModel",
            "Second" + "Fragment",
            "Second" + "ViewModel",
            "My" + "Adapter",
            "diff" + "_util",
            "activity" + "_add.xml",
            "fragment" + "_add.xml",
            "fragment" + "_first.xml",
            "fragment" + "_second.xml",
            "first" + "_item.xml",
            "R.string.add" + "_"
        )
        val files = File(repositoryRoot, "app/src")
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java", "xml") }

        files.forEach { file ->
            val content = file.readText().replace(
                Regex("/\\*\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL)),
                ""
            )
            forbiddenFragments.forEach { forbidden ->
                assertFalse("旧参照 $forbidden が残っています: ${file.path}", content.contains(forbidden))
            }
        }
    }

    @Test
    fun `全ViewModelの境界_通信設定JSON型をUI層へ持ち込まない`() {
        val viewModelPaths = listOf(
            "app/src/main/java/jp/co/zaico/codingtest/ui/inventory/list/InventoryListViewModel.kt",
            "app/src/main/java/jp/co/zaico/codingtest/ui/inventory/detail/InventoryDetailViewModel.kt",
            "app/src/main/java/jp/co/zaico/codingtest/ui/inventory/create/InventoryCreateViewModel.kt"
        )
        val forbiddenFragments = listOf(
            "android.content.Context",
            "android.content.res.Resources",
            "HttpClient",
            "io.ktor",
            "HttpStatusCode",
            "URL(",
            "/api/v2/",
            "R.string.api_token",
            "Json",
            "DTO",
            "dto.",
            "mapper."
        )

        viewModelPaths.forEach { path ->
            val viewModelSource = source(path)
            forbiddenFragments.forEach { forbidden ->
                assertFalse("UI ViewModelに禁止依存 $forbidden があります: $path", viewModelSource.contains(forbidden))
            }
        }
    }

    @Test
    fun `トークン設定と作成クライアント_ログ出力せず無視対象設定から取得する`() {
        val buildScript = source("app/build.gradle.kts")
        val trackedApiResources = source("app/src/main/res/values/api.xml")
        val creatorSource = source(
            "app/src/main/java/jp/co/zaico/codingtest/data/repository/KtorInventoryCreator.kt"
        )
        val createFragmentSource = source(
            "app/src/main/java/jp/co/zaico/codingtest/ui/inventory/create/InventoryCreateFragment.kt"
        )
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
        assertTrue(buildScript.indexOf("ZAICO_API_TOKEN") < buildScript.indexOf("zaico.api.token"))
        assertFalse(trackedApiResources.contains("name=\"api_token\""))
        assertTrue(gitignore.lineSequence().any { it.trim() in setOf("local.properties", "/local.properties") })
        assertFalse(buildScript.contains("println("))
        assertFalse(buildScript.contains("System.out"))
        assertFalse(buildScript.contains("System.err"))
        assertFalse(buildScript.contains("logger."))
        assertFalse(creatorSource.contains("Logging"))
        assertFalse(creatorSource.contains("println("))
        assertFalse(creatorSource.contains("Log."))
        assertFalse(createFragmentSource.contains("println("))
        assertFalse(createFragmentSource.contains("Log."))
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
