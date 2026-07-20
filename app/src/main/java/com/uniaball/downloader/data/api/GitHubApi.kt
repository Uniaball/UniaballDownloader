package com.uniaball.downloader.data.api

import com.uniaball.downloader.data.model.ArtifactPage
import com.uniaball.downloader.data.model.GitHubRelease
import com.uniaball.downloader.data.model.WorkflowRunPage
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubApi {
    @GET("repos/{owner}/{repo}/releases")
    suspend fun listReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): List<GitHubRelease>

    @GET("repos/{owner}/{repo}/actions/workflows/{workflow_id}/runs")
    suspend fun listWorkflowRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: String,
        @Query("branch") branch: String? = null,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): WorkflowRunPage

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun listAllRuns(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 50,
        @Query("sort") sort: String = "created",
        @Query("direction") direction: String = "desc"
    ): WorkflowRunPage

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun listArtifacts(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long,
        @Query("per_page") perPage: Int = 30,
        @Query("page") page: Int = 1
    ): ArtifactPage
}
