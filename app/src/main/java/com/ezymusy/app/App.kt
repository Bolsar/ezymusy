package com.ezymusy.app

import android.app.Application
import com.ezymusy.app.core.youtube.YouTube
import okhttp3.OkHttpClient

class App : Application() {
    lateinit var container: AppContainer
        private set


    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}

/** Hand-wired dependency graph. Built once in [App]. */
class AppContainer {
    val httpClient = OkHttpClient()
    val youTube = YouTube(httpClient)
}
