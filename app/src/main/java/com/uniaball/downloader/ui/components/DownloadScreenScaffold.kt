package com.uniaball.downloader.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniaball.downloader.data.repository.RateLimitedException
import com.uniaball.downloader.util.DownloadStatus
import com.uniaball.downloader.util.InAppDownloadManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

abstract class BaseDownloadViewModel : ViewModel() {
    protected val _snackbar = MutableSharedFlow<String>()
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    /**
     * 统一处理加载/刷新异常：
     * - CancellationException 向上传播（避免被当作业务异常吞掉，导致协程取消信号丢失）
     * - RateLimitedException：无内容 → 通过 [setError] 设置 Error 态；有内容 → snackbar 提示
     * - 其它异常：无内容 → Error（[genericErrorMessage]）；有内容 → snackbar（[refreshFailMessage]）
     */
    protected suspend fun handleLoadException(
        e: Throwable,
        hasContent: Boolean,
        setError: (String) -> Unit,
        rateLimitMessage: String = "GitHub API 速率限制",
        genericErrorMessage: String = "加载失败",
        refreshFailMessage: String = "刷新失败"
    ) {
        if (e is CancellationException) throw e
        when (e) {
            is RateLimitedException -> {
                val msg = e.message ?: rateLimitMessage
                if (!hasContent) setError(msg) else _snackbar.emit(msg)
            }
            else -> {
                if (!hasContent) setError(e.message ?: genericErrorMessage)
                else _snackbar.emit(e.message ?: refreshFailMessage)
            }
        }
    }
}

/**
 * 统一收集 [snackbar] 事件并通过 [snackbarHostState] 展示。
 * 供各下载屏幕复用，替代各自重复的 LaunchedEffect { viewModel.snackbar.collect { ... } } 模式。
 */
@Composable
fun BaseDownloadViewModel.CollectSnackbar(snackbarHostState: SnackbarHostState) {
    LaunchedEffect(Unit) {
        snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
}

@Composable
fun DownloadScreenScaffold(
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit
) {
    val downloadState by InAppDownloadManager.downloadState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        content(padding)
    }
    if (downloadState.status != DownloadStatus.IDLE) {
        DownloadProgressDialog(
            state = downloadState,
            onDismiss = { InAppDownloadManager.resetState() }
        )
    }
}
