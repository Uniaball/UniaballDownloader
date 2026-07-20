package com.uniaball.downloader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.uniaball.downloader.ui.screens.DesktopGluesScreen
import com.uniaball.downloader.ui.screens.HomeScreen
import com.uniaball.downloader.ui.screens.MobileGlScreen
import com.uniaball.downloader.ui.screens.OpenJdkScreen
import com.uniaball.downloader.ui.screens.SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var current by remember { mutableStateOf(Destination.Home) }
    var subScreen by remember { mutableStateOf<SubScreen?>(null) }

    // 子屏幕下拦截返回键：回到主页而不是退出应用
    BackHandler(enabled = subScreen != null) {
        subScreen = null
    }

    val topBarTitle = when {
        subScreen is SubScreen.DesktopGlues -> "DesktopGlues Releases"
        subScreen is SubScreen.OpenJdk -> "OpenJDK-Android"
        subScreen is SubScreen.MobileGl -> "MobileGL Actions"
        current == Destination.Settings -> "设置"
        else -> "Uniaball 下载站"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(topBarTitle) },
                navigationIcon = {
                    if (subScreen != null) {
                        IconButton(onClick = { subScreen = null }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                }
            )
        },
        bottomBar = {
            // 仅在非子屏幕时显示底部导航栏
            if (subScreen == null) {
                NavigationBar {
                    Destination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = current == dest,
                            onClick = { current = dest },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = subScreen ?: current,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) + slideInHorizontally(
                    animationSpec = tween(200),
                    initialOffsetX = { it / 8 }
                ) togetherWith fadeOut(animationSpec = tween(200)) + slideOutHorizontally(
                    animationSpec = tween(200),
                    targetOffsetX = { -it / 8 }
                )
            },
            label = "screen-transition"
        ) { target ->
            when (target) {
                is SubScreen.DesktopGlues -> DesktopGluesScreen(modifier = Modifier.fillMaxSize())
                is SubScreen.OpenJdk -> OpenJdkScreen(modifier = Modifier.fillMaxSize())
                is SubScreen.MobileGl -> MobileGlScreen(modifier = Modifier.fillMaxSize())
                Destination.Home -> HomeScreen(
                    modifier = Modifier.fillMaxSize(),
                    onNavigate = { subScreen = it }
                )
                Destination.Settings -> SettingsScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
