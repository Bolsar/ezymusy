package com.ezymusy.app

import android.app.Application
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.ezymusy.app.core.data.AppDb
import com.ezymusy.app.core.data.Repository
import com.ezymusy.app.core.youtube.YouTube
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.io.File

// ponytail: fixed LRU size; a setting if anyone asks.
private const val AUDIO_CACHE_BYTES = 200L * 1024 * 1024

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Hand-wired dependency graph. Built once in [App]. */
@OptIn(UnstableApi::class)
class AppContainer(context: Context) {
    val httpClient = OkHttpClient()
    val youTube = YouTube(httpClient)
    val repository = Repository(AppDb.build(context).library(), youTube)

    /** Audio already played or fetched ahead. One instance per folder per process, so it lives here. */
    val audioCache by lazy {
        SimpleCache(
            File(context.cacheDir, "audio"),
            LeastRecentlyUsedCacheEvictor(AUDIO_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )
    }

    /** True once several tracks in a row failed to extract: YouTube likely changed and the app needs an update. */
    val extractorOutdated = MutableStateFlow(false)
}
