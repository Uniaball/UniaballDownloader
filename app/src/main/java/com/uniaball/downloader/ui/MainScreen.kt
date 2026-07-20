package com.uniaball.downloader.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    var current by remember { mutableStateOf(Destination.Home) }
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(current.label) })
        },
        bottomBar = {
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
    ) { padding ->
        when (current) {
            Destination.Home -> HomeScreen(
                modifier = Modifier.fillMaxSize().padding(padding),
                onNavigate = { current = it }
            )
            Destination.DesktopGlues -> DesktopGluesScreen(modifier = Modifier.fillMaxSize().padding(padding))
            Destination.OpenJdk -> OpenJdkScreen(modifier = Modifier.fillMaxSize().padding(padding))
            Destination.MobileGl -> MobileGlScreen(modifier = Modifier.fillMaxSize().padding(padding))
        }
    }
}
