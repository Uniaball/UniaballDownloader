package com.uniaball.downloader.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "主页", Icons.Filled.Home),
    Settings("settings", "设置", Icons.Filled.Settings)
}

sealed class SubScreen {
    data object DesktopGlues : SubScreen()
    data object OpenJdk : SubScreen()
    data object MobileGl : SubScreen()
}
