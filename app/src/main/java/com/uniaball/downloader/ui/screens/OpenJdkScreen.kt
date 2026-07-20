package com.uniaball.downloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.uniaball.downloader.data.model.WorkflowRun
import com.uniaball.downloader.data.repository.UniaballRepository
import com.uniaball.downloader.util.DownloadUtil
import com.uniaball.downloader.util.formatSize
import com.uniaball.downloader.util.formatTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private val _selectedVersion = MutableStateFlow(21)
    val selectedVersion: StateFlow<Int> = _selectedVersion.asStateFlow()

    fun selectVersion(v: Int) {
        _selectedVersion.value = v
    }

    fun fetchBuilds() {
        _uiState.value = OpenJdkUiState.Loading
        viewModelScope.launch {
            try {
                val version = _selectedVersion.value
                val page = UniaballRepository.listOpenJdkRuns(version)
                val runs = page.workflowRuns.take(20)
                if (runs.isEmpty()) {
                    _uiState.value = OpenJdkUiState.Error("未找到 OpenJDK $version 的构建")
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
                _uiState.value = if (items.isEmpty()) {
                    OpenJdkUiState.Empty
                } else {
                    OpenJdkUiState.Success(items)
                }
            } catch (e: Exception) {
                _uiState.value = OpenJdkUiState.Error(e.message ?: "未知错误")
            }
        }
    }
}

// ===== Screen =====

private val JDK_VERSIONS = listOf(17, 21, 25, 26, 27, 28)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenJdkScreen(modifier: Modifier = Modifier) {
    val viewModel: OpenJdkViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedVersion by viewModel.selectedVersion.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = menuExpanded,
            onExpandedChange = { menuExpanded = it }
        ) {
            OutlinedTextField(
                value = "OpenJDK $selectedVersion",
                onValueChange = {},
                readOnly = true,
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("获取构建")
        }

        when (val s = uiState) {
            OpenJdkUiState.Idle -> IdleContent()
            OpenJdkUiState.Loading -> LoadingContent()
            is OpenJdkUiState.Success -> BuildsList(
                items = s.items,
                onDownload = { url -> DownloadUtil.openDownload(context, url) }
            )
            is OpenJdkUiState.Error -> ErrorContent(
                message = s.message,
                onRetry = { viewModel.fetchBuilds() }
            )
            OpenJdkUiState.Empty -> EmptyContent()
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
        items(items) { item ->
            BuildCard(item = item, onDownload = onDownload)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildCard(
    item: OpenJdkBuildItem,
    onDownload: (String) -> Unit
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
        modifier = Modifier.fillMaxWidth()
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
