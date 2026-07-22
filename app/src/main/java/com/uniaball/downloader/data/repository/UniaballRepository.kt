package com.uniaball.downloader.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.uniaball.downloader.data.api.GitHubApi
import com.uniaball.downloader.data.model.Artifact
import com.uniaball.downloader.data.model.ArtifactPage
import com.uniaball.downloader.data.model.GitHubRelease
import com.uniaball.downloader.data.model.WorkflowRunPage
import com.uniaball.downloader.util.LogUtil
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    // MobileGL Actions
    const val MOBILEGL_OWNER = "MobileGL-Dev"
    const val MOBILEGL_REPO = "MobileGL"

    // 网页版用于过滤 run.name 的关键字
    const val RUN_NAME_MOBILEGL = "MobileGL APK"
    const val NIGHTLY_LINK_BASE = "https://nightly.link/"
    const val APK_EXCLUDE_KEYWORD = "trace"

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    // 403 退避（内存态，不持久化）
    private const val RATE_LIMIT_BACKOFF_MS = 5L * 60 * 1000 // 5 分钟
    @Volatile
    private var rateLimitUntil: Long = 0L

    // ===== 缓存清理 =====
    fun clearCache() {
        LogUtil.i("Cache", "清除全部 API 缓存")
        releasesCache.clear()
        mobileGlRunsCacheValue = null
        allRunsCache = null
        openJdkRunsCache.clear()
        artifactCache.clear()
        lastFetchTimestamps.clear()
        rateLimitUntil = 0L

        val p = prefs()?.edit() ?: return
        val allKeys = prefs()?.all?.keys ?: emptySet()
        for (key in allKeys) {
            if (key.startsWith("cache_")) {
                p.remove(key)
            }
        }
        p.apply()
    }

    fun getCacheSize(): Long {
        val p = prefs() ?: return 0L
        var total = 0L
        for ((key, value) in p.all) {
            if (key.startsWith("cache_") && value is String) {
                total += value.length.toLong()
            }
        }
        return total
    }

    // 内存缓存
    private val releasesCache = ConcurrentHashMap<String, List<GitHubRelease>>()
    @Volatile
    private var mobileGlRunsCacheValue: WorkflowRunPage? = null
    @Volatile
    private var allRunsCache: WorkflowRunPage? = null
    private val openJdkRunsCache = ConcurrentHashMap<Int, WorkflowRunPage>()
    private val artifactCache = ConcurrentHashMap<String, ArtifactPage>()

    // 节流时间戳
    private val lastFetchTimestamps = ConcurrentHashMap<String, Long>()

    // SharedPreferences 注入
    private var appContext: Context? = null
    private const val PREF_NAME = "uniaball_cache"
    private const val KEY_DESKTOPGLUES_RELEASES = "cache_desktopglues_releases"
    private const val KEY_MOBILEGL_RUNS = "cache_mobilegl_runs"
    private const val KEY_OPENJDK_RUNS_PREFIX = "cache_openjdk_runs_"
    private const val KEY_OPENJDK_ALL_RUNS = "cache_openjdk_all_runs"
    private const val KEY_ARTIFACTS_PREFIX = "cache_artifacts_"
    private const val KEY_MIRROR_ENABLED = "pref_mirror_enabled"
    private const val KEY_MOBILEGL_APK_ONLY = "pref_mobilegl_apk_only"
    private const val KEY_MULTI_THREAD_DOWNLOAD = "pref_multi_thread_download"

    // gh-proxy 镜像下载开关（默认开启，与原网站一致）
    private val _isMirrorEnabled = MutableStateFlow(true)
    val isMirrorEnabled: StateFlow<Boolean> = _isMirrorEnabled.asStateFlow()

    // MobileGL 仅显示 APK 产物开关（默认开启，过滤掉 trace 等非 APK 产物）
    private val _isMobileGlApkOnly = MutableStateFlow(true)
    val isMobileGlApkOnly: StateFlow<Boolean> = _isMobileGlApkOnly.asStateFlow()

    // 多线程下载实验性开关（默认关闭）
    private val _isMultiThreadDownload = MutableStateFlow(false)
    val isMultiThreadDownload: StateFlow<Boolean> = _isMultiThreadDownload.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        // 从磁盘读取镜像开关初始值（默认 true）
        val prefs = appContext?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _isMirrorEnabled.value = prefs?.getBoolean(KEY_MIRROR_ENABLED, true) ?: true
        _isMobileGlApkOnly.value = prefs?.getBoolean(KEY_MOBILEGL_APK_ONLY, true) ?: true
        _isMultiThreadDownload.value = prefs?.getBoolean(KEY_MULTI_THREAD_DOWNLOAD, false) ?: false
    }

    fun setMirrorEnabled(enabled: Boolean) {
        LogUtil.i("Settings", "镜像下载: ${if (enabled) "开启" else "关闭"}")
        _isMirrorEnabled.value = enabled
        val prefs = prefs() ?: return
        prefs.edit().putBoolean(KEY_MIRROR_ENABLED, enabled).apply()
    }

    fun setMobileGlApkOnly(enabled: Boolean) {
        LogUtil.i("Settings", "APK 过滤: ${if (enabled) "开启" else "关闭"}")
        _isMobileGlApkOnly.value = enabled
        val prefs = prefs() ?: return
        prefs.edit().putBoolean(KEY_MOBILEGL_APK_ONLY, enabled).apply()
    }

    fun setMultiThreadDownload(enabled: Boolean) {
        LogUtil.i("Settings", "多线程下载: ${if (enabled) "开启" else "关闭"}")
        _isMultiThreadDownload.value = enabled
        val prefs = prefs() ?: return
        prefs.edit().putBoolean(KEY_MULTI_THREAD_DOWNLOAD, enabled).apply()
    }

    /**
     * 根据 [isMobileGlApkOnly] 开关过滤 artifacts：
     *   - 开关开启（默认）：仅保留 name 以 ".apk" 结尾（大小写不敏感）的 artifact，
     *     并排除 name 中包含 "trace"（大小写不敏感，即 [APK_EXCLUDE_KEYWORD]）的 artifact
     *   - 开关关闭：返回原始列表
     */
    fun filterApkOnly(artifacts: List<Artifact>): List<Artifact> {
        if (!_isMobileGlApkOnly.value) return artifacts
        return artifacts.filter { artifact ->
            artifact.name.endsWith(".apk", ignoreCase = true) &&
                !artifact.name.contains(APK_EXCLUDE_KEYWORD, ignoreCase = true)
        }
    }

    private fun checkRateLimit() {
        val now = System.currentTimeMillis()
        if (now < rateLimitUntil) {
            throw RateLimitedException("GitHub API 速率限制，请更换网络后重试")
        }
    }

    private fun markRateLimited() {
        LogUtil.w("API", "触发 GitHub API 速率限制退避 (${RATE_LIMIT_BACKOFF_MS / 1000}s)")
        rateLimitUntil = System.currentTimeMillis() + RATE_LIMIT_BACKOFF_MS
    }

    private suspend fun <T> withRateLimit(block: suspend () -> T): T {
        checkRateLimit()
        return try {
            block()
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 403) {
                markRateLimited()
                throw RateLimitedException("GitHub API 速率限制，请更换网络后重试")
            }
            throw e
        }
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
    suspend fun listDesktopGluesReleases(): List<GitHubRelease> = withRateLimit {
        val result = api.listReleases(DESKTOPGLUES_OWNER, DESKTOPGLUES_REPO)
        releasesCache[KEY_DESKTOPGLUES_RELEASES] = result
        markFetched("desktopglues_releases")
        saveToDisk(KEY_DESKTOPGLUES_RELEASES, result)
        result
    }

    suspend fun listArtifactsForRun(owner: String, repo: String, runId: Long): ArtifactPage {
        val cacheKey = "${owner}_${repo}_$runId"
        return withRateLimit {
            val result = api.listArtifacts(owner, repo, runId)
            artifactCache[cacheKey] = result
            markFetched("artifacts_$cacheKey")
            saveToDisk("$KEY_ARTIFACTS_PREFIX${cacheKey}", result)
            result
        }
    }

    suspend fun listAllRuns(owner: String, repo: String): WorkflowRunPage =
        api.listAllRuns(owner, repo)

    /**
     * 带统一缓存的 listAllRuns 封装：所有版本共享同一份 allRuns 数据。
     * - 内存缓存命中且节流期内（30s）→ 直接返回，不发请求
     * - 否则发请求并更新内存/磁盘缓存 + 节流时间戳
     * - HTTP 403 → 标记退避 + 抛 RateLimitedException
     */
    suspend fun listAllRunsCached(owner: String, repo: String): WorkflowRunPage {
        // 内存缓存命中且节流期内：直接返回，不发请求
        if (allRunsCache != null && isFresh("openjdk_all_runs")) {
            return allRunsCache!!
        }
        return withRateLimit {
            val result = api.listAllRuns(owner, repo)
            allRunsCache = result
            markFetched("openjdk_all_runs")
            saveToDisk(KEY_OPENJDK_ALL_RUNS, result)
            result
        }
    }

    /**
     * 拉取 OpenJDK-Android 仓库的全部 workflow runs，
     * 按 run.name 模糊匹配（小写后包含 jdk{version} / openjdk{version} / java{version} 任一）。
     * 与网页版 openjdk.html 的逻辑一致。
     */
    suspend fun listOpenJdkRuns(jdkVersion: Int): WorkflowRunPage = withRateLimit {
        val page = listAllRunsCached(OPENJDK_OWNER, OPENJDK_REPO)
        val lowerKeywords = jdkKeywords(jdkVersion)
        val filtered = page.workflowRuns.filter { run ->
            val name = (run.name ?: "").lowercase()
            lowerKeywords.any { name.contains(it) }
        }
        val result = page.copy(workflowRuns = filtered)
        openJdkRunsCache[jdkVersion] = result
        result
    }

    /**
     * 拉取 MobileGL 仓库的全部 workflow runs，
     * 按 run.name equals "MobileGL APK"（忽略大小写）过滤。
     * 与网页版 mobilegl-actions.html 的逻辑一致。
     */
    suspend fun listMobileGlRuns(): WorkflowRunPage = withRateLimit {
        val page = listAllRuns(MOBILEGL_OWNER, MOBILEGL_REPO)
        val filtered = page.workflowRuns.filter { run ->
            (run.name ?: "").equals(RUN_NAME_MOBILEGL, ignoreCase = true)
        }
        val result = page.copy(workflowRuns = filtered)
        mobileGlRunsCacheValue = result
        markFetched("mobilegl_runs")
        saveToDisk(KEY_MOBILEGL_RUNS, result)
        result
    }

    internal fun jdkKeywords(version: Int): List<String> {
        val v = version.toString()
        return listOf("jdk$v", "openjdk$v", "java$v")
    }

    /**
     * 构造 nightly.link 下载 URL（用于 OpenJDK 屏幕的 artifact 下载）。
     * 例如：https://nightly.link/Uniaball/OpenJDK-Android/actions/runs/12345/asset-name.zip
     */
    fun nightlyLinkUrl(owner: String, repo: String, runId: Long, artifactName: String): String {
        return "${NIGHTLY_LINK_BASE}$owner/$repo/actions/runs/$runId/$artifactName.zip"
    }

    /**
     * 将任意 GitHub URL 拼接为 gh-proxy.com 镜像 URL。
     * 根据 [isMirrorEnabled] 开关决定是否加镜像前缀：
     *   - 开关开启（默认）：返回 https://gh-proxy.com/{url}
     *   - 开关关闭：返回原 URL（直连 GitHub）
     * 输入示例：
     *   https://api.github.com/repos/MobileGL-Dev/MobileGL/actions/artifacts/8453894305/zip
     *   https://github.com/MobileGL-Dev/MobileGL/releases/download/v1.0/xxx.zip
     */
    fun mirror(url: String): String {
        if (url.isBlank()) return url
        if (!_isMirrorEnabled.value) return url
        if (url.startsWith(GH_PROXY)) return url
        return GH_PROXY + url
    }

    // ===== 内存缓存读取 =====
    fun getCachedDesktopGluesReleases(): List<GitHubRelease>? = releasesCache[KEY_DESKTOPGLUES_RELEASES]
    fun getCachedMobileGlRuns(): WorkflowRunPage? = mobileGlRunsCacheValue
    fun getCachedAllRuns(): WorkflowRunPage? = allRunsCache
    fun getCachedOpenJdkRuns(jdkVersion: Int): WorkflowRunPage? = openJdkRunsCache[jdkVersion]
    fun getCachedArtifacts(owner: String, repo: String, runId: Long): ArtifactPage? =
        artifactCache["${owner}_${repo}_$runId"]

    // ===== 磁盘缓存读取（启动时调用） =====
    fun loadDesktopGluesReleasesFromDisk(): List<GitHubRelease>? {
        val fromDisk: List<GitHubRelease>? = loadFromDisk(KEY_DESKTOPGLUES_RELEASES)
        if (fromDisk != null) {
            releasesCache[KEY_DESKTOPGLUES_RELEASES] = fromDisk
            return fromDisk
        }
        return releasesCache[KEY_DESKTOPGLUES_RELEASES]
    }
    fun loadMobileGlRunsFromDisk(): WorkflowRunPage? {
        val fromDisk: WorkflowRunPage? = loadFromDisk(KEY_MOBILEGL_RUNS)
        if (fromDisk != null) {
            mobileGlRunsCacheValue = fromDisk
            return fromDisk
        }
        return mobileGlRunsCacheValue
    }
    fun loadAllRunsFromDisk(): WorkflowRunPage? {
        val fromDisk: WorkflowRunPage? = loadFromDisk(KEY_OPENJDK_ALL_RUNS)
        if (fromDisk != null) {
            allRunsCache = fromDisk
            return fromDisk
        }
        return allRunsCache
    }
    fun loadOpenJdkRunsFromDisk(jdkVersion: Int): WorkflowRunPage? {
        val fromDisk: WorkflowRunPage? = loadFromDisk("$KEY_OPENJDK_RUNS_PREFIX$jdkVersion")
        if (fromDisk != null) {
            openJdkRunsCache[jdkVersion] = fromDisk
            return fromDisk
        }
        return openJdkRunsCache[jdkVersion]
    }
    fun loadArtifactsFromDisk(owner: String, repo: String, runId: Long): ArtifactPage? {
        val cacheKey = "${owner}_${repo}_$runId"
        val fromDisk: ArtifactPage? = loadFromDisk("$KEY_ARTIFACTS_PREFIX$cacheKey")
        if (fromDisk != null) {
            artifactCache[cacheKey] = fromDisk
            return fromDisk
        }
        return artifactCache[cacheKey]
    }

    // ===== 内部 SharedPreferences 读写 =====
    private fun prefs(): SharedPreferences? =
        appContext?.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

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
