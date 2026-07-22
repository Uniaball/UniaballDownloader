package com.uniaball.downloader.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniaball.downloader.data.model.GitHubAsset
import com.uniaball.downloader.data.model.GitHubRelease
import com.uniaball.downloader.data.repository.UniaballRepository
import com.uniaball.downloader.ui.components.DownloadProgressDialog
import com.uniaball.downloader.ui.screenTransitionSpec
import com.uniaball.downloader.util.DownloadStatus
import com.uniaball.downloader.util.DownloadUtil
import com.uniaball.downloader.util.InAppDownloadManager
import com.uniaball.downloader.util.formatSize
import com.uniaball.downloader.util.formatTime
import dev.jeziellago.compose.markdowntext.MarkdownText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DesktopGluesUiState {
    data object Loading : DesktopGluesUiState
    data class Success(val releases: List<GitHubRelease>) : DesktopGluesUiState
    data class Error(val message: String) : DesktopGluesUiState
    data object Empty : DesktopGluesUiState
}

class DesktopGluesViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<DesktopGluesUiState>(DesktopGluesUiState.Loading)
    val uiState: StateFlow<DesktopGluesUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>()
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    init { load() }

    fun load() {
        var hasContent = _uiState.value is DesktopGluesUiState.Success
        if (!hasContent) {
            // 优先从磁盘缓存读取 releases，若有立即设 Success 态
            val cached = UniaballRepository.loadDesktopGluesReleasesFromDisk()
            if (cached != null && cached.isNotEmpty()) {
                _uiState.value = DesktopGluesUiState.Success(cached)
                hasContent = true
            } else {
                _uiState.value = DesktopGluesUiState.Loading
            }
        }

        // 节流
        if (hasContent && UniaballRepository.isFresh("desktopglues_releases")) {
            viewModelScope.launch {
                _snackbar.emit("请稍候再刷新")
            }
            return
        }

        viewModelScope.launch {
            try {
                val releases = UniaballRepository.listDesktopGluesReleases()
                if (releases.isEmpty()) {
                    if (!hasContent) {
                        _uiState.value = DesktopGluesUiState.Empty
                    } else {
                        _snackbar.emit("未找到新的构建记录")
                    }
                } else {
                    _uiState.value = DesktopGluesUiState.Success(releases)
                }
            } catch (e: com.uniaball.downloader.data.repository.RateLimitedException) {
                if (!hasContent) {
                    _uiState.value = DesktopGluesUiState.Error(e.message ?: "GitHub API 速率限制")
                } else {
                    _snackbar.emit(e.message ?: "GitHub API 速率限制")
                }
            } catch (e: Exception) {
                if (!hasContent) {
                    _uiState.value = DesktopGluesUiState.Error(e.message ?: "加载失败")
                } else {
                    _snackbar.emit(e.message ?: "刷新失败")
                }
            }
        }
    }
}

@Composable
fun DesktopGluesScreen(modifier: Modifier = Modifier) {
    val viewModel: DesktopGluesViewModel = viewModel { DesktopGluesViewModel() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val downloadState by InAppDownloadManager.downloadState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        AnimatedContent(
            targetState = state,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = { screenTransitionSpec() },
            label = "desktopglues-state"
        ) { target ->
            when (target) {
                DesktopGluesUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                is DesktopGluesUiState.Error -> ErrorView(target.message, { viewModel.load() }, Modifier.fillMaxSize())
                DesktopGluesUiState.Empty -> EmptyView(Modifier.fillMaxSize())
                is DesktopGluesUiState.Success -> SuccessView(target.releases, Modifier.fillMaxSize())
            }
        }
    }

    if (downloadState.status != DownloadStatus.IDLE) {
        DownloadProgressDialog(
            state = downloadState,
            onDismiss = { InAppDownloadManager.resetState() }
        )
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("加载失败", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun EmptyView(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("暂无 Release", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SuccessView(releases: List<GitHubRelease>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "DesktopGlues Releases 下载",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(items = releases, key = { it.id }, contentType = { "desktopglues_release" }) { ReleaseCard(release = it, modifier = Modifier.animateItem()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReleaseCard(release: GitHubRelease, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = release.name?.takeIf { it.isNotBlank() } ?: release.tagName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (release.tagName.isNotBlank()) {
                    AssistChip(onClick = {}, label = { Text(release.tagName) })
                }
            }
            val time = release.publishedAt?.takeIf { it.isNotBlank() } ?: release.createdAt
            if (time.isNotBlank()) {
                Text(
                    text = "发布时间：${formatTime(time)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (release.prerelease) {
                SuggestionChip(onClick = {}, label = { Text("Pre-release") })
            }
            release.body?.takeIf { it.isNotBlank() }?.let { body ->
                MarkdownText(
                    markdown = body,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (release.assets.isEmpty()) {
                FilledTonalButton(
                    onClick = { DownloadUtil.openDownload(context, release.htmlUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.OpenInBrowser, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("查看 Release")
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    release.assets.forEach { AssetRow(asset = it) }
                }
            }
        }
    }
}

@Composable
private fun AssetRow(asset: GitHubAsset) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = asset.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatSize(asset.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FilledTonalButton(onClick = {
            DownloadUtil.startInAppDownload(asset.browserDownloadUrl, asset.name)
        }) {
            Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("下载")
        }
    }
}
