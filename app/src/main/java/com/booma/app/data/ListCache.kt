package com.booma.app.data

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lists kept in memory and on disk, so Booma opens with your music already on screen and refreshes it
 * quietly in the background (no loading wait on a cold start or when switching tabs).
 */
class ListCache<T>(private val folder: String, private val encode: (T) -> String, private val decode: (String) -> T?) {
    private val memory = ConcurrentHashMap<String, T>()

    private fun file(key: String): File? = Lists.root?.let { File(it, "$folder/${key.hashCode()}.json") }

    operator fun get(key: String): T? = memory[key] ?: file(key)?.takeIf { it.exists() }
        ?.let { runCatching { decode(it.readText()) }.getOrNull() }
        ?.also { memory[key] = it }

    operator fun set(key: String, value: T) {
        memory[key] = value
        Lists.io.launch {
            file(key)?.let { f ->
                f.parentFile?.mkdirs()
                runCatching { f.writeText(encode(value)) }
            }
        }
    }

    fun remove(key: String) {
        memory.remove(key)
        Lists.io.launch { file(key)?.delete() }
    }

    fun clear() {
        memory.clear()
        Lists.io.launch { Lists.root?.let { File(it, folder).listFiles()?.forEach { f -> f.delete() } } }
    }
}

object Lists {
    internal var root: File? = null
    internal val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        root = File(context.applicationContext.filesDir, "lists")
    }

    val tracks = ListCache<List<Track>>(
        "tracks",
        encode = { list ->
            JSONArray().apply {
                list.forEach { put(trackJson(it)) }
            }.toString()
        },
        decode = { text ->
            val arr = JSONArray(text)
            (0 until arr.length()).map { trackOf(arr.getJSONObject(it)) }
        },
    )

    val playlists = ListCache<List<Playlist>>(
        "playlists",
        encode = { list ->
            JSONArray().apply {
                list.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("subtitle", it.subtitle).put("thumb", it.thumb ?: "")) }
            }.toString()
        },
        decode = { text ->
            val arr = JSONArray(text)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Playlist(o.getString("id"), o.optString("title"), o.optString("subtitle"), o.optString("thumb").ifEmpty { null })
            }
        },
    )

    val shelves = ListCache<List<Shelf>>(
        "shelves",
        encode = { list ->
            JSONArray().apply {
                list.forEach { shelf ->
                    put(
                        JSONObject()
                            .put("title", shelf.title)
                            .put("rows", shelf.rows)
                            .put("songs", JSONArray().apply { shelf.songs.forEach { put(trackJson(it)) } })
                            .put("lists", JSONArray().apply { shelf.lists.forEach { put(playlistJson(it)) } })
                    )
                }
            }.toString()
        },
        decode = { text ->
            val arr = JSONArray(text)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val songs = o.optJSONArray("songs") ?: JSONArray()
                val lists = o.optJSONArray("lists") ?: JSONArray()
                Shelf(
                    title = o.optString("title"),
                    songs = (0 until songs.length()).map { trackOf(songs.getJSONObject(it)) },
                    lists = (0 until lists.length()).map { playlistOf(lists.getJSONObject(it)) },
                    rows = o.optBoolean("rows"),
                )
            }
        },
    )

    /** Search results (songs plus albums and playlists), kept like the other lists. */
    val search = ListCache<SearchResults>(
        "search",
        encode = { r ->
            JSONObject()
                .put("songs", JSONArray().apply { r.songs.forEach { put(trackJson(it)) } })
                .put("lists", JSONArray().apply { r.lists.forEach { put(playlistJson(it)) } })
                .toString()
        },
        decode = { text ->
            val o = JSONObject(text)
            val songs = o.optJSONArray("songs") ?: JSONArray()
            val lists = o.optJSONArray("lists") ?: JSONArray()
            SearchResults(
                (0 until songs.length()).map { trackOf(songs.getJSONObject(it)) },
                (0 until lists.length()).map { playlistOf(lists.getJSONObject(it)) },
            )
        },
    )

    private fun trackJson(t: Track) = JSONObject().put("id", t.id).put("title", t.title).put("artist", t.artist)
        .put("thumb", t.thumb ?: "").put("setVideoId", t.setVideoId ?: "")

    private fun trackOf(o: JSONObject) = Track(
        o.getString("id"), o.optString("title"), o.optString("artist"),
        o.optString("thumb").ifEmpty { null }, o.optString("setVideoId").ifEmpty { null },
    )

    private fun playlistJson(p: Playlist) = JSONObject().put("id", p.id).put("title", p.title).put("subtitle", p.subtitle).put("thumb", p.thumb ?: "")

    private fun playlistOf(o: JSONObject) = Playlist(o.getString("id"), o.optString("title"), o.optString("subtitle"), o.optString("thumb").ifEmpty { null })

    /** Signing out forgets everything. */
    fun clear() {
        tracks.clear()
        playlists.clear()
        shelves.clear()
        search.clear()
    }
}
