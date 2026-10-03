package com.ezymusy.app

import android.app.Application
import android.content.Context
import com.ezymusy.app.core.data.AppDb
import com.ezymusy.app.core.data.Repository
import com.ezymusy.app.core.youtube.YouTube
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-wired dependency graph. Built once in [App]. */
class AppContainer(context: Context) {
    val httpClient = OkHttpClient()
    val youTube = YouTube(httpClient)
    val repository = Repository(AppDb.build(context).library(), youTube)

    /** True once several tracks in a row failed to extract: YouTube likely changed and the app needs an update. */
    val extractorOutdated = MutableStateFlow(false)
}
