package com.booma.app.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val synced: Boolean, val lines: List<LyricLine>)

/**
 * Lyrics from lrclib.net (the source Booma on PC uses). Synced lyrics come with a time for every line.
 * Results are kept on the phone, so a song's lyrics are fetched once and also work offline.
 */
object LyricsClient {
    private var dir: File? = null
    private val memory = HashMap<String, Lyrics?>()

    fun init(context: Context) {
        dir = File(context.applicationContext.filesDir, "lyrics").apply { mkdirs() }
    }

    suspend fun fetch(videoId: String, title: String, artist: String): Lyrics? = withContext(Dispatchers.IO) {
        synchronized(memory) { if (memory.containsKey(videoId)) return@withContext memory[videoId] }
        val file = dir?.let { File(it, "$videoId.json") }
        val saved = file?.takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
        val raw = saved ?: download(title, artist)?.also { obj -> file?.let { runCatching { it.writeText(obj.toString()) } } }
        val lyrics = raw?.let { parse(it.optBoolean("synced"), it.optString("text")) }
        synchronized(memory) { memory[videoId] = lyrics }
        lyrics
    }

    /** Returns {"synced": bool, "text": "..."} or null when lrclib has nothing for this song. */
    private fun download(title: String, artist: String): JSONObject? {
        // "Song (feat. X) [Official Video]" -> "Song"; "Artist, Other" -> "Artist"
        val cleanTitle = title.replace(Regex("""\s*[(\[].*?[)\]]"""), "").trim().ifEmpty { title }
        val mainArtist = artist.split(",", "&", " x ", " feat").first().trim()
        val attempts = listOf(
            "track_name=${enc(cleanTitle)}&artist_name=${enc(mainArtist)}",
            "q=${enc("$cleanTitle $mainArtist")}",
        )
        for (query in attempts) {
            val results = runCatching { search(query) }.getOrNull() ?: continue
            var plain: String? = null
            for (i in 0 until results.length()) {
                val r = results.optJSONObject(i) ?: continue
                val synced = r.optString("syncedLyrics")
                if (synced.isNotBlank() && synced != "null") return JSONObject().put("synced", true).put("text", synced)
                val p = r.optString("plainLyrics")
                if (plain == null && p.isNotBlank() && p != "null") plain = p
            }
            if (plain != null) return JSONObject().put("synced", false).put("text", plain)
        }
        return null
    }

    private fun search(query: String): JSONArray {
        val conn = URL("https://lrclib.net/api/search?$query").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", "Booma/1.0 (Android)")
            return JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private val stamp = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")

    private fun parse(synced: Boolean, text: String): Lyrics? {
        if (text.isBlank()) return null
        if (!synced) return Lyrics(false, text.lines().map { LyricLine(-1, it.trim()) })
        val lines = mutableListOf<LyricLine>()
        for (line in text.lines()) {
            val stamps = stamp.findAll(line).toList()
            if (stamps.isEmpty()) continue
            val words = line.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val ms = (m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toDouble() * 1000).toLong()
                lines += LyricLine(ms, words)
            }
        }
        return if (lines.isEmpty()) null else Lyrics(true, lines.sortedBy { it.timeMs })
    }
}
