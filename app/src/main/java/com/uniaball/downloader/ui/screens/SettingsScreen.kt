package com.uniaball.downloader.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uniaball.downloader.BuildConfig
import com.uniaball.downloader.data.repository.UniaballRepository
import com.uniaball.downloader.util.LogUtil
import com.uniaball.downloader.ui.entranceAnimation
import com.uniaball.downloader.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mirrorEnabled by UniaballRepository.isMirrorEnabled.collectAsStateWithLifecycle()
    val apkOnly by UniaballRepository.isMobileGlApkOnly.collectAsStateWithLifecycle()
    val multiThread by UniaballRepository.isMultiThreadDownload.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    var showConfirmDialog by remember { mutableStateOf(false) }
    var cacheSize by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            cacheSize = UniaballRepository.getCacheSize()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "下载设置",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        SettingsToggleCard(
            icon = Icons.Filled.CloudSync,
            title = "使用 gh-proxy.com 镜像下载",
            subtitle = "关闭后直连 GitHub，可能需要科学上网",
            checked = mirrorEnabled,
            onCheckedChange = { UniaballRepository.setMirrorEnabled(it) },
            delayMillis = 0,
            visible = visible
        )

        SettingsToggleCard(
            icon = Icons.Filled.FilterAlt,
            title = "仅显示 APK 产物",
            subtitle = "过滤非 APK 产物及名称含 trace 的 APK",
            checked = apkOnly,
            onCheckedChange = { UniaballRepository.setMobileGlApkOnly(it) },
            delayMillis = 50,
            visible = visible
        )

        SettingsToggleCard(
            icon = Icons.Filled.Speed,
            title = "多线程下载（实验性）",
            subtitle = "通过 HTTP Range 并发分片加速下载，部分服务器可能不支持",
            checked = multiThread,
            onCheckedChange = { UniaballRepository.setMultiThreadDownload(it) },
            delayMillis = 100,
            visible = visible
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ===== 缓存管理 =====
        Text(
            text = "缓存管理",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        SettingsActionCard(
            icon = Icons.Filled.DeleteSweep,
            title = "清除 API 缓存",
            subtitle = if (cacheSize > 0) "当前缓存大小：${formatSize(cacheSize)}" else "当前无缓存数据",
            buttonText = "清除",
            onClick = { showConfirmDialog = true },
            delayMillis = 150,
            visible = visible,
            enabled = cacheSize > 0,
            buttonColors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        )

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("确认清除缓存") },
                text = {
                    Text("将清除所有 API 响应缓存（${formatSize(cacheSize)}）。下次进入下载页面时将重新从 GitHub 拉取数据。")
                },
                confirmButton = {
                    TextButton(onClick = {
                        UniaballRepository.clearCache()
                        cacheSize = 0L
                        showConfirmDialog = false
                        Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("确认清除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("取消")
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ===== 日志与调试 =====
        Text(
            text = "日志与调试",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        SettingsActionCard(
            icon = Icons.Filled.Share,
            title = "导出运行日志",
            subtitle = "导出应用 logcat 日志用于排查问题",
            buttonText = "导出",
            onClick = {
                scope.launch {
                    runCatching {
                        LogUtil.shareLogs(context)
                    }.onFailure {
                        Toast.makeText(context, "导出日志失败: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            delayMillis = 200,
            visible = visible
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "关于",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Card(
            onClick = {
                runCatching {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/Uniaball/UniaballDownloader/blob/main/README.md")
                    )
                    context.startActivity(intent)
                }
            },
            modifier = Modifier.fillMaxWidth().entranceAnimation(visible = visible, delayMillis = 250)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "README 项目说明",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "查看项目 README 文档",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "版本：v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "使用 GitHub API · Actions · 通过 gh-proxy.com / nightly.link 镜像下载",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "本应用为 https://uniaball.github.io 的 Android 客户端复刻，使用 Kotlin + Jetpack Compose + Material Design 3 实现。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    delayMillis: Int,
    visible: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth().entranceAnimation(visible = visible, delayMillis = delayMillis)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun SettingsActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: () -> Unit,
    delayMillis: Int,
    visible: Boolean,
    enabled: Boolean = true,
    buttonColors: ButtonColors = ButtonDefaults.buttonColors()
) {
    Card(
        modifier = Modifier.fillMaxWidth().entranceAnimation(visible = visible, delayMillis = delayMillis)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onClick,
                enabled = enabled,
                colors = buttonColors
            ) {
                Text(buttonText)
            }
        }
    }
}
