package com.uniaball.downloader

import android.app.Application
import com.uniaball.downloader.data.repository.UniaballRepository
import com.uniaball.downloader.util.InAppDownloadManager

class UniaballApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UniaballRepository.init(this)
        InAppDownloadManager.init(this)
    }
}
