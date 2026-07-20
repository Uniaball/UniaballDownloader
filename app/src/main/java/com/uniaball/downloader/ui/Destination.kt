package com.uniaball.downloader.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "主页", Icons.Filled.Home),
    DesktopGlues("desktop_glues", "DesktopGlues", Icons.Filled.Build),
    OpenJdk("open_jdk", "OpenJDK", Icons.Filled.Code),
    MobileGl("mobile_gl", "MobileGL", Icons.Filled.Apps)
}
