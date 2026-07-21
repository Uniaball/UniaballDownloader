package com.uniaball.downloader.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    val id: Long,
    val name: String? = null,
    @SerialName("tag_name")
    val tagName: String = "",
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("published_at")
    val publishedAt: String? = null,
    @SerialName("html_url")
    val htmlUrl: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
data class GitHubAsset(
    val id: Long,
    val name: String = "",
    val size: Long = 0,
    @SerialName("download_count")
    val downloadCount: Long = 0,
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = "",
    @SerialName("content_type")
    val contentType: String? = null,
    @SerialName("updated_at")
    val updatedAt: String = ""
)

@Serializable
data class WorkflowRun(
    val id: Long,
    val name: String? = null,
    @SerialName("head_branch")
    val headBranch: String = "",
    @SerialName("head_sha")
    val headSha: String = "",
    val status: String = "",
    val conclusion: String? = null,
    @SerialName("run_number")
    val runNumber: Long = 0,
    @SerialName("run_attempt")
    val runAttempt: Long = 1,
    @SerialName("html_url")
    val htmlUrl: String = "",
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("updated_at")
    val updatedAt: String = "",
    @SerialName("display_title")
    val displayTitle: String = ""
)

@Serializable
data class WorkflowRunPage(
    @SerialName("total_count")
    val totalCount: Long = 0,
    @SerialName("workflow_runs")
    val workflowRuns: List<WorkflowRun> = emptyList()
)

@Serializable
data class Artifact(
    val id: Long,
    val name: String = "",
    @SerialName("size_in_bytes")
    val sizeInBytes: Long = 0,
    val url: String = "",
    @SerialName("archive_download_url")
    val archiveDownloadUrl: String = "",
    val expired: Boolean = false,
    @SerialName("created_at")
    val createdAt: String = "",
    @SerialName("updated_at")
    val updatedAt: String = "",
    @SerialName("workflow_run")
    val workflowRun: ArtifactWorkflowRun? = null
)

@Serializable
data class ArtifactWorkflowRun(
    val id: Long,
    @SerialName("repository_id")
    val repositoryId: Long,
    @SerialName("head_repository_id")
    val headRepositoryId: Long,
    @SerialName("head_repository")
    val headRepository: ArtifactRepository? = null,
    @SerialName("head_branch")
    val headBranch: String = "",
    @SerialName("head_sha")
    val headSha: String = ""
)

@Serializable
data class ArtifactRepository(
    val id: Long,
    val name: String = "",
    @SerialName("full_name")
    val fullName: String = ""
)

@Serializable
data class ArtifactPage(
    @SerialName("total_count")
    val totalCount: Long = 0,
    val artifacts: List<Artifact> = emptyList()
)
