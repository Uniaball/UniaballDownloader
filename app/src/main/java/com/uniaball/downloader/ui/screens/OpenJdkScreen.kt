package com.uniaball.downloader.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.uniaball.downloader.data.model.Artifact
import com.uniaball.downloader.data.model.ArtifactPage
import com.uniaball.downloader.data.model.WorkflowRun
import com.uniaball.downloader.data.model.WorkflowRunPage
import com.uniaball.downloader.data.repository.UniaballRepository
import com.uniaball.downloader.ui.screenTransitionSpec
import com.uniaball.downloader.util.DownloadUtil
import com.uniaball.downloader.util.formatSize
import com.uniaball.downloader.util.formatTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ===== UI state =====

sealed interface OpenJdkUiState {
    data object Idle : OpenJdkUiState
    data object Loading : OpenJdkUiState
    data class Success(val items: List<OpenJdkBuildItem>) : OpenJdkUiState
    data class Error(val message: String) : OpenJdkUiState
    data object Empty : OpenJdkUiState
}

data class OpenJdkBuildItem(
    val artifact: Artifact,
    val run: WorkflowRun
)

// ===== ViewModel =====

class OpenJdkViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<OpenJdkUiState>(OpenJdkUiState.Idle)
    val uiState: StateFlow<OpenJdkUiState> = _uiState.asStateFlow()

    private val _snackbar = MutableSharedFlow<String>()
    val snackbar: SharedFlow<String> = _snackbar.asSharedFlow()

    private val _selectedVersion = MutableStateFlow<Int?>(null)
    val selectedVersion: StateFlow<Int?> = _selectedVersion.asStateFlow()

    // 当前进行中的 fetch 协程；切换版本或重复点击"获取构建"时取消旧 Job，保证串行
    private var fetchJob: Job? = null

    // 加载守卫：网络请求期间为 true，UI 据此禁用"获取构建"按钮避免并发触发
    private val _isFetching = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = _isFetching.asStateFlow()

    fun selectVersion(v: Int) {
        // 取消进行中的 fetch Job，避免版本切换后旧协程继续向 _uiState 写入造成竞态
        fetchJob?.cancel()
        _isFetching.value = false
        _selectedVersion.value = v
        _uiState.value = OpenJdkUiState.Idle  // 立即清旧版本数据

        // 异步查缓存：磁盘 JSON 反序列化移出主线程，避免 ANR
        // 复用 fetchJob 字段统一管理，确保任意时刻最多一个后台协程
        fetchJob = viewModelScope.launch {
            val items = lookupCachedItems(v)
            if (items.isNotEmpty()) {
                _uiState.value = OpenJdkUiState.Success(items)
            }
            // 未命中保持 Idle，不主动调用 fetchBuilds
        }
    }

    /**
     * 按"allRuns 内存 → allRuns 磁盘 → 按 version 单独缓存（兼容旧数据）"顺序
     * 查找指定版本的 runs + artifacts，拼装为 List<OpenJdkBuildItem>；
     * 任一层命中且非空即返回，否则返回空列表。
     * 改为 suspend：所有磁盘 JSON 反序列化在协程内执行，避免主线程 ANR。
     */
    private suspend fun lookupCachedItems(version: Int): List<OpenJdkBuildItem> {
        val owner = UniaballRepository.OPENJDK_OWNER
        val repo = UniaballRepository.OPENJDK_REPO

        // 1. allRuns 内存缓存（按版本关键字过滤）
        buildItemsFromPage(
            page = UniaballRepository.getCachedAllRuns(),
            applyVersionFilter = true,
            version = version,
            artifactLookup = { runId -> UniaballRepository.getCachedArtifacts(owner, repo, runId) }
        ).takeIf { it.isNotEmpty() }?.let { return it }

        // 2. allRuns 磁盘缓存（按版本关键字过滤）
        buildItemsFromPage(
            page = UniaballRepository.loadAllRunsFromDisk(),
            applyVersionFilter = true,
            version = version,
            artifactLookup = { runId -> UniaballRepository.loadArtifactsFromDisk(owner, repo, runId) }
        ).takeIf { it.isNotEmpty() }?.let { return it }

        // 3. 兼容回退：按 version 单独的内存缓存（不过滤，写入时已过滤）
        buildItemsFromPage(
            page = UniaballRepository.getCachedOpenJdkRuns(version),
            applyVersionFilter = false,
            version = version,
            artifactLookup = { runId -> UniaballRepository.getCachedArtifacts(owner, repo, runId) }
        ).takeIf { it.isNotEmpty() }?.let { return it }

        // 4. 兼容回退：按 version 单独的磁盘缓存（不过滤，写入时已过滤）
        buildItemsFromPage(
            page = UniaballRepository.loadOpenJdkRunsFromDisk(version),
            applyVersionFilter = false,
            version = version,
            artifactLookup = { runId -> UniaballRepository.loadArtifactsFromDisk(owner, repo, runId) }
        ).takeIf { it.isNotEmpty() }?.let { return it }

        return emptyList()
    }

    /**
     * 单一缓存源的统一处理：可选按版本关键字过滤 → take(5) → 查 artifacts → 拼装 OpenJdkBuildItem。
     * - [applyVersionFilter] = true：按 jdkKeywords(version) 过滤 runs（allRuns 缓存分支用）
     * - [applyVersionFilter] = false：直接取 runs（version 缓存分支用，数据写入时已过滤）
     * - [artifactLookup]：内存缓存用 getCachedArtifacts，磁盘缓存用 loadArtifactsFromDisk
     * 返回空列表表示该缓存源无可用数据，调用方据此尝试下一源。
     */
    private suspend fun buildItemsFromPage(
        page: WorkflowRunPage?,
        applyVersionFilter: Boolean,
        version: Int,
        artifactLookup: suspend (Long) -> ArtifactPage?
    ): List<OpenJdkBuildItem> {
        if (page == null || page.workflowRuns.isEmpty()) return emptyList()
        val runs = if (applyVersionFilter) {
            val keywords = UniaballRepository.jdkKeywords(version)
            page.workflowRuns.filter { run ->
                val name = (run.name ?: "").lowercase()
                keywords.any { name.contains(it) }
            }
        } else {
            page.workflowRuns
        }
        return runs.take(5).mapNotNull { run ->
            val ap = artifactLookup(run.id) ?: return@mapNotNull null
            ap.artifacts.map { OpenJdkBuildItem(it, run) }
        }.flatten()
    }

    fun fetchBuilds() {
        val version = _selectedVersion.value
        if (version == null) {
            viewModelScope.launch {
                _snackbar.emit("请先选择 OpenJDK 版本")
            }
            return
        }

        fetchJob?.cancel()
        _uiState.value = OpenJdkUiState.Loading
        _isFetching.value = true

        fetchJob = viewModelScope.launch {
            try {
                // 协程内查缓存：磁盘 JSON 反序列化移出主线程，避免 ANR
                val cachedItems = lookupCachedItems(version)
                val hasContent = cachedItems.isNotEmpty()
                if (hasContent) {
                    _uiState.value = OpenJdkUiState.Success(cachedItems)
                }
                // 节流检查：使用共享键 openjdk_all_runs，命中且有缓存数据 → 维持 Success
                if (hasContent && UniaballRepository.isFresh("openjdk_all_runs")) {
                    _snackbar.emit("请稍候再刷新")
                    return@launch
                }
                // 网络请求
                val page = UniaballRepository.listOpenJdkRuns(version)
                val runs = page.workflowRuns.take(5)
                if (runs.isEmpty()) {
                    if (!hasContent) {
                        _uiState.value = OpenJdkUiState.Error("未找到 OpenJDK $version 的构建")
                    } else {
                        _snackbar.emit("未找到新的构建记录")
                    }
                    return@launch
                }
                val pairs = runs.map { run ->
                    async {
                        UniaballRepository.listArtifactsForRun(
                            UniaballRepository.OPENJDK_OWNER,
                            UniaballRepository.OPENJDK_REPO,
                            run.id
                        ) to run
                    }
                }.awaitAll()
                val items = pairs.flatMap { (ap, run) ->
                    ap.artifacts.map { OpenJdkBuildItem(it, run) }
                }
                if (items.isEmpty()) {
                    if (!hasContent) {
                        _uiState.value = OpenJdkUiState.Empty
                    } else {
                        _snackbar.emit("未找到可下载的 artifacts")
                    }
                } else {
                    _uiState.value = OpenJdkUiState.Success(items)
                }
            } catch (e: CancellationException) {
                // 协程被取消时正确向上传播，避免被当作业务异常吞掉导致状态泄漏
                throw e
            } catch (e: com.uniaball.downloader.data.repository.RateLimitedException) {
                if (_uiState.value !is OpenJdkUiState.Success) {
                    _uiState.value = OpenJdkUiState.Error(e.message ?: "GitHub API 速率限制")
                } else {
                    _snackbar.emit(e.message ?: "GitHub API 速率限制")
                }
            } catch (e: Exception) {
                if (_uiState.value !is OpenJdkUiState.Success) {
                    _uiState.value = OpenJdkUiState.Error(e.message ?: "未知错误")
                } else {
                    _snackbar.emit(e.message ?: "刷新失败")
                }
            } finally {
                _isFetching.value = false
            }
        }
    }
}

// ===== Screen =====

private val JDK_VERSIONS = listOf(17, 21, 25, 26, 27, 28)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenJdkScreen(
    modifier: Modifier = Modifier,
    viewModel: OpenJdkViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedVersion by viewModel.selectedVersion.collectAsStateWithLifecycle()
    val isFetching by viewModel.isFetching.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedVersion?.let { "OpenJDK $it" } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text("请选择 OpenJDK 版本") },
                    label = { Text("OpenJDK 版本") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    JDK_VERSIONS.forEach { v ->
                        DropdownMenuItem(
                            text = { Text("OpenJDK $v") },
                            onClick = {
                                viewModel.selectVersion(v)
                                menuExpanded = false
                            }
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.fetchBuilds() },
                enabled = selectedVersion != null && !isFetching,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("获取构建")
            }

            Text(
                text = "仅展示最近 5 次构建的 artifacts",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedContent(
                targetState = uiState,
                transitionSpec = { screenTransitionSpec() },
                label = "openjdk-state"
            ) { state ->
                when (state) {
                    OpenJdkUiState.Idle -> IdleContent()
                    OpenJdkUiState.Loading -> LoadingContent()
                    is OpenJdkUiState.Success -> BuildsList(
                        items = state.items,
                        onDownload = { url -> DownloadUtil.openDownload(context, url) }
                    )
                    is OpenJdkUiState.Error -> ErrorContent(
                        message = state.message,
                        onRetry = { viewModel.fetchBuilds() }
                    )
                    OpenJdkUiState.Empty -> EmptyContent()
                }
            }
        }
    }
}

@Composable
private fun IdleContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "请选择版本并获取构建",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "未发现可下载的构建文件",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "加载失败：$message",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(12.dp))
        FilledTonalButton(onClick = onRetry) {
            Text("重试")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildsList(
    items: List<OpenJdkBuildItem>,
    onDownload: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.artifact.id }) { item ->
            BuildCard(item = item, onDownload = onDownload, modifier = Modifier.animateItem())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildCard(
    item: OpenJdkBuildItem,
    onDownload: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val artifact = item.artifact
    val run = item.run
    val downloadUrl = remember(artifact.id, run.id) {
        UniaballRepository.nightlyLinkUrl(
            UniaballRepository.OPENJDK_OWNER,
            UniaballRepository.OPENJDK_REPO,
            run.id,
            artifact.name
        )
    }
    Card(
        onClick = { onDownload(downloadUrl) },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = artifact.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "run #${run.runNumber} • ${formatTime(artifact.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ConclusionChip(conclusion = run.conclusion)
                Text(
                    text = formatSize(artifact.sizeInBytes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (artifact.expired) {
                    Text(
                        text = "已过期",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            FilledTonalButton(
                onClick = { onDownload(downloadUrl) },
                enabled = !artifact.expired
            ) {
                Text("下载")
            }
        }
    }
}

@Composable
private fun ConclusionChip(conclusion: String?) {
    val (text, color) = when (conclusion) {
        "success" -> "成功" to MaterialTheme.colorScheme.primary
        "failure" -> "失败" to MaterialTheme.colorScheme.error
        "cancelled" -> "已取消" to MaterialTheme.colorScheme.outline
        null -> "进行中" to MaterialTheme.colorScheme.tertiary
        else -> conclusion to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .background(
                color = color.copy(alpha = 0.12f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
