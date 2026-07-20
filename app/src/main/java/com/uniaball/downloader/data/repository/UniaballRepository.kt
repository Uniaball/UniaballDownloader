package com.uniaball.downloader.data.repository

import com.uniaball.downloader.data.api.GitHubApi
import com.uniaball.downloader.data.model.ArtifactPage
import com.uniaball.downloader.data.model.GitHubRelease
import com.uniaball.downloader.data.model.WorkflowRunPage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class RateLimitedException(message: String) : Exception(message)

object UniaballRepository {

    // 与网站一致的仓库与镜像配置
    const val GH_PROXY = "https://gh-proxy.com/"
    const val GITHUB_API_BASE = "https://api.github.com/"
    const val GITHUB_BASE = "https://github.com/"
    const val GITHUB_API_DOWNLOAD_BASE = "https://api.github.com/repos/"

    // 三个目标仓库（owner/repo）
    // DesktopGlues releases
    const val DESKTOPGLUES_OWNER = "Uniaball"
    const val DESKTOPGLUES_REPO = "DesktopGlues"
    // OpenJDK-Android Actions
    const val OPENJDK_OWNER = "Uniaball"
    const val OPENJDK_REPO = "OpenJDK-Android"
    const val OPENJDK_WORKFLOW_ID = "build.yml"
    // MobileGL Actions
    const val MOBILEGL_OWNER = "MobileGL-Dev"
    const val MOBILEGL_REPO = "MobileGL"
    const val MOBILEGL_WORKFLOW_ID = "mobilegl-apk.yml"

    // 网页版用于过滤 run.name 的关键字
    const val RUN_NAME_MOBILEGL = "MobileGL APK"
    const val NIGHTLY_LINK_BASE = "https://nightly.link/"

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    // 403 退避（内存态，不持久化）
    private const val RATE_LIMIT_BACKOFF_MS = 5L * 60 * 1000 // 5 分钟
    @Volatile
    private var rateLimitUntil: Long = 0L

    // 内存缓存
    private val releasesCache = mutableMapOf<String, List<GitHubRelease>>()
    @Volatile
    private var mobileGlRunsCacheValue: WorkflowRunPage? = null
    private val openJdkRunsCache = mutableMapOf<Int, WorkflowRunPage>()
    private val artifactCache = mutableMapOf<String, ArtifactPage>()

    // 节流时间戳
    private val lastFetchTimestamps = mutableMapOf<String, Long>()

    // SharedPreferences 注入
    private var appContext: android.content.Context? = null
    private const val PREF_NAME = "uniaball_cache"
    private const val KEY_DESKTOPGLUES_RELEASES = "cache_desktopglues_releases"
    private const val KEY_MOBILEGL_RUNS = "cache_mobilegl_runs"
    private const val KEY_OPENJDK_RUNS_PREFIX = "cache_openjdk_runs_"
    private const val KEY_ARTIFACTS_PREFIX = "cache_artifacts_"

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    private fun checkRateLimit() {
        val now = System.currentTimeMillis()
        if (now < rateLimitUntil) {
            throw RateLimitedException("GitHub API 速率限制，请更换网络后重试")
        }
    }

    private fun markRateLimited() {
        rateLimitUntil = System.currentTimeMillis() + RATE_LIMIT_BACKOFF_MS
    }

    fun isFresh(key: String, throttleMs: Long = 30_000L): Boolean {
        val now = System.currentTimeMillis()
        val last = lastFetchTimestamps[key] ?: 0L
        return now - last < throttleMs
    }

    private fun markFetched(key: String) {
        lastFetchTimestamps[key] = System.currentTimeMillis()
    }

    private val authInterceptor = Interceptor { chain ->
        val req = chain.request().newBuilder()
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "UniaballDownloader-Android")
            .build()
        chain.proceed(req)
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(GITHUB_API_BASE)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val api: GitHubApi = retrofit.create(GitHubApi::class.java)

    // ===== DesktopGlues Releases =====
    suspend fun listDesktopGluesReleases(): List<GitHubRelease> {
        checkRateLimit()
        return try {
            val result = api.listReleases(DESKTOPGLUES_OWNER, DESKTOPGLUES_REPO)
            releasesCache[KEY_DESKTOPGLUES_RELEASES] = result
            markFetched("desktopglues_releases")
            saveToDisk(KEY_DESKTOPGLUES_RELEASES, result)
            result
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 403) {
                markRateLimited()
                throw RateLimitedException("GitHub API 速率限制，请更换网络后重试")
            }
            throw e
        }
    }

    // ===== OpenJDK-Android workflow runs by branch (branch name 形如 "jdk-17" / "jdk-21" 等) =====
    suspend fun listOpenJdkWorkflowRuns(jdkVersion: Int): WorkflowRunPage {
        val branch = "jdk-$jdkVersion"
        return api.listWorkflowRuns(OPENJDK_OWNER, OPENJDK_REPO, OPENJDK_WORKFLOW_ID, branch = branch)
    }

    suspend fun listArtifactsForRun(owner: String, repo: String, runId: Long): ArtifactPage {
        checkRateLimit()
        val cacheKey = "${owner}_${repo}_$runId"
        return try {
            val result = api.listArtifacts(owner, repo, runId)
            artifactCache[cacheKey] = result
            markFetched("artifacts_$cacheKey")
            saveToDisk("$KEY_ARTIFACTS_PREFIX${cacheKey}", result)
            result
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 403) {
                markRateLimited()
                throw RateLimitedException("GitHub API 速率限制，请更换网络后重试")
            }
            throw e
        }
    }

    // ===== MobileGL workflow runs =====
    suspend fun listMobileGlWorkflowRuns(): WorkflowRunPage {
        return api.listWorkflowRuns(MOBILEGL_OWNER, MOBILEGL_REPO, MOBILEGL_WORKFLOW_ID)
    }

    suspend fun listAllRuns(owner: String, repo: String): WorkflowRunPage =
        api.listAllRuns(owner, repo)

    /**
     * 拉取 OpenJDK-Android 仓库的全部 workflow runs，
     * 按 run.name 模糊匹配（小写后包含 jdk{version} / openjdk{version} / java{version} 任一）。
     * 与网页版 openjdk.html 的逻辑一致。
     */
    suspend fun listOpenJdkRuns(jdkVersion: Int): WorkflowRunPage {
        checkRateLimit()
        return try {
            val page = listAllRuns(OPENJDK_OWNER, OPENJDK_REPO)
            val versionStr = jdkVersion.toString()
            val lowerKeywords = listOf("jdk$versionStr", "openjdk$versionStr", "java$versionStr")
            val filtered = page.workflowRuns.filter { run ->
                val name = (run.name ?: "").lowercase()
                lowerKeywords.any { name.contains(it) }
            }
            val result = page.copy(workflowRuns = filtered)
            openJdkRunsCache[jdkVersion] = result
            markFetched("openjdk_runs_$jdkVersion")
            saveToDisk("$KEY_OPENJDK_RUNS_PREFIX$jdkVersion", result)
            result
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 403) {
                markRateLimited()
                throw RateLimitedException("GitHub API 速率限制，请更换网络后重试")
            }
            throw e
        }
    }

    /**
     * 拉取 MobileGL 仓库的全部 workflow runs，
     * 按 run.name equals "MobileGL APK"（忽略大小写）过滤。
     * 与网页版 mobilegl-actions.html 的逻辑一致。
     */
    suspend fun listMobileGlRuns(): WorkflowRunPage {
        checkRateLimit()
        return try {
            val page = listAllRuns(MOBILEGL_OWNER, MOBILEGL_REPO)
            val filtered = page.workflowRuns.filter { run ->
                (run.name ?: "").equals(RUN_NAME_MOBILEGL, ignoreCase = true)
            }
            val result = page.copy(workflowRuns = filtered)
            mobileGlRunsCacheValue = result
            markFetched("mobilegl_runs")
            saveToDisk(KEY_MOBILEGL_RUNS, result)
            result
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 403) {
                markRateLimited()
                throw RateLimitedException("GitHub API 速率限制，请更换网络后重试")
            }
            throw e
        }
    }

    /**
     * 构造 nightly.link 下载 URL（用于 OpenJDK 屏幕的 artifact 下载）。
     * 例如：https://nightly.link/Uniaball/OpenJDK-Android/actions/runs/12345/asset-name.zip
     */
    fun nightlyLinkUrl(owner: String, repo: String, runId: Long, artifactName: String): String {
        return "${NIGHTLY_LINK_BASE}$owner/$repo/actions/runs/$runId/$artifactName.zip"
    }

    /**
     * 将任意 GitHub URL 拼接为 gh-proxy.com 镜像 URL
     * 输入示例：
     *   https://api.github.com/repos/MobileGL-Dev/MobileGL/actions/artifacts/8453894305/zip
     *   https://github.com/MobileGL-Dev/MobileGL/releases/download/v1.0/xxx.zip
     * 输出：https://gh-proxy.com/https://api.github.com/repos/...
     */
    fun mirror(url: String): String {
        if (url.isBlank()) return url
        if (url.startsWith(GH_PROXY)) return url
        return GH_PROXY + url
    }

    // ===== 内存缓存读取 =====
    fun getCachedDesktopGluesReleases(): List<GitHubRelease>? = releasesCache[KEY_DESKTOPGLUES_RELEASES]
    fun getCachedMobileGlRuns(): WorkflowRunPage? = mobileGlRunsCacheValue
    fun getCachedOpenJdkRuns(jdkVersion: Int): WorkflowRunPage? = openJdkRunsCache[jdkVersion]
    fun getCachedArtifacts(owner: String, repo: String, runId: Long): ArtifactPage? =
        artifactCache["${owner}_${repo}_$runId"]

    // ===== 磁盘缓存读取（启动时调用） =====
    fun loadDesktopGluesReleasesFromDisk(): List<GitHubRelease>? =
        loadFromDisk(KEY_DESKTOPGLUES_RELEASES) ?: releasesCache[KEY_DESKTOPGLUES_RELEASES]
    fun loadMobileGlRunsFromDisk(): WorkflowRunPage? {
        return loadFromDisk(KEY_MOBILEGL_RUNS) ?: mobileGlRunsCacheValue
    }
    fun loadOpenJdkRunsFromDisk(jdkVersion: Int): WorkflowRunPage? {
        return loadFromDisk("$KEY_OPENJDK_RUNS_PREFIX$jdkVersion") ?: openJdkRunsCache[jdkVersion]
    }
    fun loadArtifactsFromDisk(owner: String, repo: String, runId: Long): ArtifactPage? {
        return loadFromDisk("$KEY_ARTIFACTS_PREFIX${owner}_${repo}_$runId") ?: artifactCache["${owner}_${repo}_$runId"]
    }

    // ===== 内部 SharedPreferences 读写 =====
    private fun prefs(): android.content.SharedPreferences? =
        appContext?.getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)

    private inline fun <reified T> loadFromDisk(key: String): T? {
        val p = prefs() ?: return null
        val str = p.getString(key, null) ?: return null
        return try {
            json.decodeFromString(str)
        } catch (e: Exception) {
            null
        }
    }

    private inline fun <reified T> saveToDisk(key: String, value: T) {
        val p = prefs() ?: return
        try {
            val str = json.encodeToString(value)
            p.edit().putString(key, str).apply()
        } catch (e: Exception) {
            // ignore
        }
    }
}
