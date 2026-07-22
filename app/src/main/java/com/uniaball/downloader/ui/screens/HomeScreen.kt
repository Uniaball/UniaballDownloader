package com.uniaball.downloader.ui.screens

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
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.uniaball.downloader.ui.SubScreen
import com.uniaball.downloader.ui.entranceAnimation

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onNavigate: (SubScreen) -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "选择下方任一项目开始下载",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        HomeEntryCard(
            icon = Icons.Filled.Build,
            title = "DesktopGlues Releases",
            subtitle = "DesktopGlues 项目构建下载",
            onClick = { onNavigate(SubScreen.DesktopGlues) },
            delayMillis = 0,
            visible = visible
        )

        HomeEntryCard(
            icon = Icons.Filled.Code,
            title = "OpenJDK-Android",
            subtitle = "GitHub Actions 构建下载，OpenJDK 17/21/25/26/27/28",
            onClick = { onNavigate(SubScreen.OpenJdk) },
            delayMillis = 60,
            visible = visible
        )

        HomeEntryCard(
            icon = Icons.Filled.Apps,
            title = "MobileGL Actions",
            subtitle = "MobileGL APK 构建，来自 GitHub Actions",
            onClick = { onNavigate(SubScreen.MobileGl) },
            delayMillis = 120,
            visible = visible
        )
    }
}

@Composable
private fun HomeEntryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    delayMillis: Int,
    visible: Boolean
) {
    Card(
        onClick = onClick,
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
            Column {
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
        }
    }
}
