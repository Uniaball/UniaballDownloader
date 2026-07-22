package com.uniaball.downloader.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun StatusChip(conclusion: String?, modifier: Modifier = Modifier) {
    val (text, color) = when (conclusion) {
        "success" -> "成功" to MaterialTheme.colorScheme.primary
        "failure" -> "失败" to MaterialTheme.colorScheme.error
        "cancelled" -> "已取消" to MaterialTheme.colorScheme.outline
        null -> "进行中" to MaterialTheme.colorScheme.secondary
        else -> conclusion to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
