package com.uniaball.downloader.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.drawText
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.uniaball.downloader.util.DownloadState
import com.uniaball.downloader.util.DownloadStatus
import com.uniaball.downloader.util.InAppDownloadManager
import com.uniaball.downloader.util.formatSize

@Composable
fun DownloadProgressDialog(
    state: DownloadState,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = {
            if (state.status != DownloadStatus.DOWNLOADING) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.fileName.ifBlank { "下载" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.status != DownloadStatus.DOWNLOADING) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭")
                        }
                    }
                }

                when (state.status) {
                    DownloadStatus.DOWNLOADING -> DownloadingContent(state)
                    DownloadStatus.COMPLETED -> CompletedContent(state, onDismiss)
                    DownloadStatus.FAILED -> FailedContent(state, onDismiss)
                    DownloadStatus.CANCELLED -> CancelledContent(onDismiss)
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun DownloadingContent(state: DownloadState) {

    val infiniteTransition = rememberInfiniteTransition(label = "downloadPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val arrowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowAlpha"
    )

    Box(
        modifier = Modifier
            .size(64.dp)
            .scale(pulseScale)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowDownward,
            contentDescription = "下载中",
            modifier = Modifier
                .size(32.dp)
                .alpha(arrowAlpha),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }

    val animatedProgress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(300),
        label = "progressAnimation"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val percentageTextStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        val percentageTextMeasurer = rememberTextMeasurer()
        val primaryColor = MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            contentAlignment = Alignment.Center
        ) {
            // 延迟读取 animatedProgress 到 draw 阶段，避免每帧重组 Text
            Box(
                modifier = Modifier.drawWithContent {
                    val percent = (animatedProgress * 100).toInt()
                    val text = "$percent%"
                    val layoutResult = percentageTextMeasurer.measure(
                        text = AnnotatedString(text),
                        style = percentageTextStyle
                    )
                    drawText(
                        layoutResult,
                        color = primaryColor,
                        topLeft = Offset(
                            x = (size.width - layoutResult.size.width) / 2f,
                            y = (size.height - layoutResult.size.height) / 2f
                        )
                    )
                }
            )
        }

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${formatSize(state.downloadedBytes)} / ${formatSize(state.totalBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${formatSize(state.speedBytesPerSec)}/s",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Text(
        text = buildString {
            if (state.totalBytes > 0 && state.speedBytesPerSec > 0) {
                val remaining = state.totalBytes - state.downloadedBytes
                val etaSec = remaining / state.speedBytesPerSec
                append("预计剩余 ")
                when {
                    etaSec < 60 -> append("${etaSec} 秒")
                    etaSec < 3600 -> append("${etaSec / 60} 分 ${etaSec % 60} 秒")
                    else -> append("${etaSec / 3600} 时 ${(etaSec % 3600) / 60} 分")
                }
            } else {
                append("正在下载...")
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Button(
        onClick = {
            InAppDownloadManager.cancelDownload()
            InAppDownloadManager.resetState()
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Icon(Icons.Filled.Cancel, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("取消下载")
    }
}

@Composable
private fun CompletedContent(state: DownloadState, onDismiss: () -> Unit) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = "下载完成",
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )
    }

    Text(
        text = "下载完成",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )

    Text(
        text = formatSize(state.downloadedBytes),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = {
                InAppDownloadManager.openDownloadedFile(context)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.FolderOpen, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("查看文件所在位置")
        }
    }
}

@Composable
private fun FailedContent(state: DownloadState, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = "下载失败",
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.error
        )
    }

    Text(
        text = "下载失败",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.error
    )

    state.error?.let { error ->
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Button(
        onClick = {
            InAppDownloadManager.startDownload(state.url, state.fileName)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("重试")
    }

    OutlinedButton(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("关闭")
    }
}

@Composable
private fun CancelledContent(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Cancel,
            contentDescription = "已取消",
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Text(
        text = "下载已取消",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )

    Button(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("确定")
    }
}
