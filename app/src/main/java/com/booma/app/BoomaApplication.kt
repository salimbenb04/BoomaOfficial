package com.booma.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.booma.app.data.Downloads
import com.booma.app.data.Http
import com.booma.app.data.Liked
import com.booma.app.data.Lists
import com.booma.app.data.LyricsClient
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.io.StringWriter

/** The error from the last crash, if Booma crashed; offered on the You screen so it can be sent and fixed. */
object CrashReport {
    var text: String? = null
}

class BoomaApplication : Application(), ImageLoaderFactory {

    /**
     * One image loader for the whole app: a quarter of memory for covers on screen, 256 MB on disk, covers kept
     * until space runs out (they never change), and no fade by default. Scrolling back shows them instantly.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(256L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .crossfade(false)
        .okHttpClient { Http.client }   // same connections as the rest of the app
        .build()

    override fun onCreate() {
        super.onCreate()
        LyricsClient.init(this)
        Downloads.init(this)
        Lists.init(this)
        // which songs you like, from the liked list saved on the phone
        Lists.io.launch {
            val liked = Lists.tracks["liked"]
            if (liked != null) withContext(Dispatchers.Main) { Liked.seed(liked) }
        }
        val crashFile = File(filesDir, "last_crash.txt")
        if (crashFile.exists()) {
            CrashReport.text = crashFile.readText().take(8000)
            crashFile.delete()
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = StringWriter()
                error.printStackTrace(PrintWriter(trace))
                crashFile.writeText("Booma crash, thread ${thread.name}\n$trace")
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
