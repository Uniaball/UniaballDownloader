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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniaball.downloader.data.model.Artifact
import com.uniaball.downloader.data.model.WorkflowRun
import com.uniaball.downloader.data.repository.UniaballRepository
import com.uniaball.downloader.ui.components.DownloadProgressDialog
import com.uniaball.downloader.ui.screenTransitionSpec
import com.uniaball.downloader.util.DownloadStatus
import com.uniaball.downloader.util.DownloadUtil
import com.uniaball.downloader.util.InAppDownloadManager
import com.uniaball.downloader.util.formatSize
import com.uniaball.downloader.util.formatTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ===== UI State =====

sealed interface MobileGlUiState {
    data object Loading : MobileGlUiState
    data class Success(val items: List<MobileGlBuildItem>) : MobileGlUiState
    data class Error(val message: String) : MobileGlUiState
    data object Empty : MobileGlUiState
}

data class MobileGlBuildItem(
    val artifact: Artifact,
    val run: WorkflowRun
)

// ===== ViewModel =====

class MobileGlViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<MobileGlUiState>(MobileGlUiState.Loading)
    val uiState: StateFlow<MobileGlUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>()
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private var allItems: List<MobileGlBuildItem> = emptyList()

    init {
        load()
        viewModelScope.launch {
            UniaballRepository.isMobileGlApkOnly.collect {
                if (allItems.isNotEmpty()) {
                    _uiState.value = applyFilter()
                }
            }
        }
    }

    private fun applyFilter(): MobileGlUiState {
        val filteredArtifacts = UniaballRepository.filterApkOnly(allItems.map { it.artifact })
        // 转为 Set 加速 O(1) 查找，避免 O(n*m) 遍历
        val filteredSet = filteredArtifacts.toSet()
        val filtered = allItems.filter { it.artifact in filteredSet }
        return if (filtered.isEmpty()) MobileGlUiState.Empty else MobileGlUiState.Success(filtered)
    }

    fun load() {
        var hasContent = _uiState.value is MobileGlUiState.Success
        if (!hasContent) {
            // 优先从内存/磁盘缓存读取 artifacts，若有立即设 Success 态并标记 hasContent=true
            val cached = UniaballRepository.loadMobileGlRunsFromDisk()
            if (cached != null && cached.workflowRuns.isNotEmpty()) {
                val runs = cached.workflowRuns.take(5)
                val cachedItems = runs.mapNotNull { run ->
                    val ap = UniaballRepository.loadArtifactsFromDisk(
                        UniaballRepository.MOBILEGL_OWNER,
                        UniaballRepository.MOBILEGL_REPO,
                        run.id
                    ) ?: return@mapNotNull null
                    ap.artifacts.map { artifact -> MobileGlBuildItem(artifact, run) }
                }.flatten().sortedByDescending { it.artifact.createdAt }
                if (cachedItems.isNotEmpty()) {
                    allItems = cachedItems
                    _uiState.value = applyFilter()
                    hasContent = true
                } else {
                    _uiState.value = MobileGlUiState.Loading
                }
            } else {
                _uiState.value = MobileGlUiState.Loading
            }
        } else {
            _isRefreshing.value = true
        }

        // 节流：30 秒内不重复请求
        if (hasContent && UniaballRepository.isFresh("mobilegl_runs")) {
            viewModelScope.launch {
                _snackbar.emit("请稍候再刷新")
                _isRefreshing.value = false
            }
            return
        }

        viewModelScope.launch {
            try {
                val runsPage = UniaballRepository.listMobileGlRuns()
                val runs = runsPage.workflowRuns.take(5)
                if (runs.isEmpty()) {
                    if (!hasContent) {
                        _uiState.value = MobileGlUiState.Error("未找到工作流 \"MobileGL APK\" 的构建记录")
                    } else {
                        _snackbar.emit("未找到新的构建记录")
                    }
                    return@launch
                }
                val items = runs.map { run ->
                    async {
                        UniaballRepository.listArtifactsForRun(
                            UniaballRepository.MOBILEGL_OWNER,
                            UniaballRepository.MOBILEGL_REPO,
                            run.id
                        ).artifacts.map { artifact -> MobileGlBuildItem(artifact, run) }
                    }
                }.awaitAll()
                    .flatten()
                    .sortedByDescending { it.artifact.createdAt }
                if (items.isEmpty()) {
                    if (!hasContent) {
                        _uiState.value = MobileGlUiState.Empty
                    } else {
                        _snackbar.emit("未找到可下载的 artifacts")
                    }
                } else {
                    allItems = items
                    _uiState.value = applyFilter()
                }
            } catch (e: com.uniaball.downloader.data.repository.RateLimitedException) {
                if (!hasContent) {
                    _uiState.value = MobileGlUiState.Error(e.message ?: "GitHub API 速率限制")
                } else {
                    _snackbar.emit(e.message ?: "GitHub API 速率限制")
                }
            } catch (e: Exception) {
                if (!hasContent) {
                    _uiState.value = MobileGlUiState.Error(e.message ?: "加载失败")
                } else {
                    _snackbar.emit(e.message ?: "刷新失败")
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

// ===== Screen =====

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileGlScreen(
    modifier: Modifier = Modifier,
    viewModel: MobileGlViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = "MobileGL APK",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "来自 GitHub Actions 的 APK 构建",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                val countText = when (state) {
                    is MobileGlUiState.Success -> "找到 ${(state as MobileGlUiState.Success).items.size} 个构建产物（来自工作流 'MobileGL APK'）"
                    is MobileGlUiState.Empty -> "找到 0 个构建产物（来自工作流 'MobileGL APK'）"
                    is MobileGlUiState.Loading -> "正在加载..."
                    is MobileGlUiState.Error -> "加载失败"
                }
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "仅展示最近 5 次构建的 artifacts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.load() },
                modifier = Modifier.fillMaxSize()
            ) {
                AnimatedContent(
                    targetState = state,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = { screenTransitionSpec() },
                    label = "mobilegl-state"
                ) { target ->
                    when (target) {
                        is MobileGlUiState.Loading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                        is MobileGlUiState.Error -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = target.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(onClick = { viewModel.load() }) {
                                    Text("重试")
                                }
                            }
                        }
                        is MobileGlUiState.Empty -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Inbox,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "暂无 APK 构建文件",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is MobileGlUiState.Success -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(items = target.items, key = { _, it -> it.artifact.id }, contentType = { _, _ -> "mobilegl_build" }) { index, item ->
                                    MobileGlBuildCard(item, modifier = Modifier.animateItem())
                                }
                            }
                        }
                    }
                }
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
private fun MobileGlBuildCard(item: MobileGlBuildItem, modifier: Modifier = Modifier) {
    val artifact = item.artifact
    val run = item.run
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = artifact.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "#${run.runNumber} • ${formatTime(artifact.createdAt)} • ${run.conclusion ?: run.status}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(conclusion = run.conclusion)
                Text(
                    text = formatSize(artifact.sizeInBytes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (artifact.expired) {
                    Text(
                        text = "已过期",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Spacer(Modifier)
                }
                FilledTonalButton(
                    onClick = {
                        DownloadUtil.startInAppDownload(artifact.archiveDownloadUrl, artifact.name)
                    },
                    enabled = !artifact.expired
                ) {
                    Text("下载")
                }
            }
        }
    }
}

@Composable
private fun StatusChip(conclusion: String?) {
    val (text, containerColor, contentColor) = when (conclusion) {
        "success" -> Triple("成功", Color(0xFF4CAF50).copy(alpha = 0.2f), Color(0xFF2E7D32))
        "failure" -> Triple(
            "失败",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        "cancelled" -> Triple(
            "已取消",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        else -> Triple("运行中", Color(0xFFFFC107).copy(alpha = 0.25f), Color(0xFFF57C00))
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
