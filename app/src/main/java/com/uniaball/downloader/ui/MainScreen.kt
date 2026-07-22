package com.uniaball.downloader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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

/**
 * 稳定的屏幕槽位类型，用于 [AnimatedContent] 的 targetState。
 *
 * 直接使用 `SubScreen? : Destination` 联合类型（实际为 `Any`）会导致内容 lambda 每次都重组，
 * 因为该类型不稳定。包装为 sealed interface 后，由于 [Destination]（enum）与 [SubScreen]
 * （sealed class，全 data object 实现）均为稳定类型，[Main] 与 [Sub] 也是稳定类型，
 * 从而使 [AnimatedContent] 仅在 target 真正变化时重组。
 */
sealed interface ScreenSlot {
    data class Main(val destination: Destination) : ScreenSlot
    data class Sub(val screen: SubScreen) : ScreenSlot
}

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
        // 缓存派生的 ScreenSlot，避免每次重组都 new 新实例（否则仍不稳定）。
        // 依赖 subScreen 与 current，仅当二者变化时才重新计算。
        val screenSlot = remember(subScreen, current) {
            // subScreen 是 delegated property,无法 smart cast,用局部变量捕获后再判空
            val sub = subScreen
            if (sub != null) ScreenSlot.Sub(sub) else ScreenSlot.Main(current)
        }
        AnimatedContent(
            targetState = screenSlot,
            modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = { screenTransitionSpec() },
            label = "screen-transition"
        ) { target ->
            when (target) {
                is ScreenSlot.Sub -> when (target.screen) {
                    is SubScreen.DesktopGlues -> DesktopGluesScreen(modifier = Modifier.fillMaxSize())
                    is SubScreen.OpenJdk -> OpenJdkScreen(modifier = Modifier.fillMaxSize())
                    is SubScreen.MobileGl -> MobileGlScreen(modifier = Modifier.fillMaxSize())
                }
                is ScreenSlot.Main -> when (target.destination) {
                    Destination.Home -> HomeScreen(
                        modifier = Modifier.fillMaxSize(),
                        onNavigate = { subScreen = it }
                    )
                    Destination.Settings -> SettingsScreen(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
