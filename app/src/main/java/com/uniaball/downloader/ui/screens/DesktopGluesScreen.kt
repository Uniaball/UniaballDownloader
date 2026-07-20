package com.uniaball.downloader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.uniaball.downloader.util.DownloadUtil
import com.uniaball.downloader.util.formatSize
import com.uniaball.downloader.util.formatTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = DesktopGluesUiState.Loading
            try {
                val releases = UniaballRepository.listDesktopGluesReleases()
                _uiState.value = if (releases.isEmpty()) DesktopGluesUiState.Empty
                else DesktopGluesUiState.Success(releases)
            } catch (e: Exception) {
                _uiState.value = DesktopGluesUiState.Error(e.message ?: "加载失败")
            }
        }
    }
}

@Composable
fun DesktopGluesScreen(modifier: Modifier = Modifier) {
    val viewModel: DesktopGluesViewModel = viewModel { DesktopGluesViewModel() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        DesktopGluesUiState.Loading -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }
        is DesktopGluesUiState.Error -> ErrorView(s.message, { viewModel.load() }, modifier)
        DesktopGluesUiState.Empty -> EmptyView(modifier)
        is DesktopGluesUiState.Success -> SuccessView(s.releases, modifier)
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
                text = "DesktopGlues Releases 下载（使用 gh-proxy.com 镜像）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        items(items = releases, key = { it.id }) { ReleaseCard(release = it) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReleaseCard(release: GitHubRelease) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
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
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
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
    val context = LocalContext.current
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
        FilledTonalButton(onClick = { DownloadUtil.openDownload(context, asset.browserDownloadUrl) }) {
            Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("下载")
        }
    }
}
