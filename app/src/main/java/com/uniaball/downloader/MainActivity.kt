package com.uniaball.downloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.uniaball.downloader.ui.MainScreen
import com.uniaball.downloader.ui.theme.UniaballDownloaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UniaballDownloaderTheme {
                MainScreen()
            }
        }
    }
}
