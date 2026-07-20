package com.uniaball.downloader.data.repository

import com.uniaball.downloader.data.api.GitHubApi
import com.uniaball.downloader.data.model.ArtifactPage
import com.uniaball.downloader.data.model.GitHubRelease
import com.uniaball.downloader.data.model.WorkflowRunPage
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

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

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

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
        return api.listReleases(DESKTOPGLUES_OWNER, DESKTOPGLUES_REPO)
    }

    // ===== OpenJDK-Android workflow runs by branch (branch name 形如 "jdk-17" / "jdk-21" 等) =====
    suspend fun listOpenJdkWorkflowRuns(jdkVersion: Int): WorkflowRunPage {
        val branch = "jdk-$jdkVersion"
        return api.listWorkflowRuns(OPENJDK_OWNER, OPENJDK_REPO, OPENJDK_WORKFLOW_ID, branch = branch)
    }

    suspend fun listArtifactsForRun(owner: String, repo: String, runId: Long): ArtifactPage {
        return api.listArtifacts(owner, repo, runId)
    }

    // ===== MobileGL workflow runs =====
    suspend fun listMobileGlWorkflowRuns(): WorkflowRunPage {
        return api.listWorkflowRuns(MOBILEGL_OWNER, MOBILEGL_REPO, MOBILEGL_WORKFLOW_ID)
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
}
