package com.booma.app.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateMapOf
import com.booma.app.player.bigArt
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

data class DownloadedSong(val track: Track, val file: String, val bytes: Long, val time: Long)

/**
 * Songs saved on the phone for offline playback, with their cover art and lyrics.
 * Files live in the app's private storage (no storage permission needed); the player plays the local
 * file whenever one exists. Two downloads run at a time; the audio is fetched in 4 MB pieces, which
 * keeps YouTube from slowing the transfer down.
 */
object Downloads {
    private const val CHUNK = 4L * 1024 * 1024
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

    private lateinit var app: Context
    private lateinit var dir: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val slots = Semaphore(2)
    private val main = Handler(Looper.getMainLooper())

    /** Read by the player on its own threads. */
    private val files = ConcurrentHashMap<String, String>()
    private val records = LinkedHashMap<String, DownloadedSong>()

    /** For the screens (main thread only). */
    val byId = mutableStateMapOf<String, DownloadedSong>()
    val progress = mutableStateMapOf<String, Float>()

    fun init(context: Context) {
        if (::dir.isInitialized) return
        app = context.applicationContext
        dir = File(app.filesDir, "downloads").apply { mkdirs() }
        load()
    }

    /** Downloaded songs, newest first. */
    fun tracks(): List<Track> = byId.values.sortedByDescending { it.time }.map { it.track }

    fun totalBytes(): Long = byId.values.sumOf { it.bytes }

    fun fileFor(videoId: String): File? = files[videoId]?.let { File(it) }?.takeIf { it.exists() }

    fun download(tracks: List<Track>) = tracks.forEach { download(it) }

    fun download(track: Track) {
        if (files.containsKey(track.id) || progress.containsKey(track.id)) return
        progress[track.id] = 0f
        scope.launch {
            slots.withPermit {
                val part = File(dir, "${track.id}.part")
                try {
                    val uri = Streams.resolveCached(app, track.id) ?: throw IOException("no audio stream found")
                    // Progress reaches the screen at most 5 times a second; every update redraws the rows
                    // that show it, so posting each 64 KB piece would make scrolling stutter.
                    var lastPost = 0L
                    var lastValue = 0f
                    fetchInPieces(uri.toString(), part) { p ->
                        val now = System.currentTimeMillis()
                        if (now - lastPost >= 200 && p - lastValue >= 0.01f) {
                            lastPost = now
                            lastValue = p
                            main.post { if (progress.containsKey(track.id)) progress[track.id] = p }
                        }
                    }
                    val audio = File(dir, "${track.id}.audio")
                    audio.delete()
                    if (!part.renameTo(audio)) throw IOException("couldn't save the file")
                    val art = track.thumb?.let { saveArt(it, track.id) }
                    // fetch lyrics now so they show offline too
                    runCatching { LyricsClient.fetch(track.id, track.title, track.artist) }
                    val song = DownloadedSong(track.copy(thumb = art ?: track.thumb), audio.path, audio.length(), System.currentTimeMillis())
                    synchronized(records) { records[track.id] = song }
                    files[track.id] = audio.path
                    saveIndex()
                    main.post {
                        byId[track.id] = song
                        progress.remove(track.id)
                    }
                    Log.d("Booma", "downloaded ${track.id} (${audio.length() / 1024} KB)")
                } catch (e: Exception) {
                    part.delete()
                    main.post { progress.remove(track.id) }
                    Log.w("Booma", "download failed: ${track.id}", e)
                }
            }
        }
    }

    fun remove(videoId: String) {
        files.remove(videoId)
        val song = synchronized(records) { records.remove(videoId) }
        byId.remove(videoId)
        scope.launch {
            song?.let { File(it.file).delete() }
            File(dir, "$videoId.jpg").delete()
            saveIndex()
        }
    }

    private fun fetchInPieces(url: String, out: File, onProgress: (Float) -> Unit) {
        var pos = 0L
        var total = -1L
        FileOutputStream(out).use { fos ->
            while (total < 0 || pos < total) {
                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 30_000
                    conn.setRequestProperty("User-Agent", UA)
                    conn.setRequestProperty("Range", "bytes=$pos-${pos + CHUNK - 1}")
                    val code = conn.responseCode
                    if (code != 200 && code != 206) throw IOException("HTTP $code")
                    total = if (code == 206) {
                        conn.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: total
                    } else {
                        conn.contentLengthLong
                    }
                    var got = 0L
                    conn.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            fos.write(buf, 0, n)
                            got += n
                            if (total > 0) onProgress(((pos + got).toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    pos += got
                    if (code == 200 || got == 0L) break
                } finally {
                    conn.disconnect()
                }
            }
        }
        if (total > 0 && pos < total) throw IOException("download stopped early ($pos of $total bytes)")
    }

    private fun saveArt(url: String, id: String): String? = try {
        val file = File(dir, "$id.jpg")
        val conn = URL(bigArt(url)).openConnection() as HttpURLConnection
        try {
            conn.inputStream.use { input -> FileOutputStream(file).use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        Uri.fromFile(file).toString()
    } catch (e: Exception) {
        null
    }

    private fun saveIndex() {
        val arr = JSONArray()
        synchronized(records) {
            for (s in records.values) {
                arr.put(
                    JSONObject()
                        .put("id", s.track.id).put("title", s.track.title).put("artist", s.track.artist)
                        .put("thumb", s.track.thumb ?: "").put("file", s.file).put("bytes", s.bytes).put("time", s.time)
                )
            }
        }
        runCatching { File(dir, "index.json").writeText(arr.toString()) }
    }

    private fun load() {
        val arr = runCatching { JSONArray(File(dir, "index.json").readText()) }.getOrNull() ?: return
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val file = o.optString("file")
            if (!File(file).exists()) continue
            val id = o.optString("id")
            val track = Track(id, o.optString("title"), o.optString("artist"), o.optString("thumb").ifEmpty { null })
            val song = DownloadedSong(track, file, o.optLong("bytes"), o.optLong("time"))
            records[id] = song
            files[id] = file
            byId[id] = song
        }
    }
}
