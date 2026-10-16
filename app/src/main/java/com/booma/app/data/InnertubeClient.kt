package com.booma.app.data

import java.security.MessageDigest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Talks to YouTube Music's internal "innertube" API, the same API the website uses.
 *
 * Signed-in requests send the session cookie plus a SAPISIDHASH authorisation header.
 * Parsing walks the JSON for song rows, so small layout changes don't break the whole list.
 */
class InnertubeClient(private val auth: AuthStore, private val settings: SettingsStore) : YtmRepository {

    private val origin = "https://music.youtube.com"
    private val desktopUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    override suspend fun home(): List<Track> = browse("FEmusic_home")

    /**
     * Home as YouTube Music lays it out: "Quick picks", "Listen again", mixes, albums and so on.
     * The first page has a few shelves; [pages] more are fetched with its continuation.
     */
    override suspend fun homeShelves(pages: Int): List<Shelf> = withContext(Dispatchers.IO) {
        val shelves = ArrayList<Shelf>()
        var root = post("browse", webBody().put("browseId", "FEmusic_home"), authed = auth.isSignedIn())
        shelves += parseShelves(root)
        repeat(pages) {
            val token = continuationOf(root) ?: return@repeat
            root = post("browse", webBody(), authed = auth.isSignedIn(), query = "ctoken=$token&continuation=$token&type=next")
            shelves += parseShelves(root)
        }
        shelves.filter { it.songs.isNotEmpty() || it.lists.isNotEmpty() }
    }

    override suspend fun likedSongs(): List<Track> = browse("VLLM")

    override suspend fun playlists(): List<Playlist> = withContext(Dispatchers.IO) {
        parsePlaylists(post("browse", webBody().put("browseId", "FEmusic_liked_playlists"), authed = true))
    }

    /** A playlist or album ("VL…", "MPREb_…"), or a mix ("mix:RD…", which only plays as a queue). */
    override suspend fun playlist(browseId: String): List<Track> {
        if (browseId.startsWith(MIX_PREFIX)) {
            val playlistId = browseId.removePrefix(MIX_PREFIX)
            return withContext(Dispatchers.IO) {
                val body = webBody().put("playlistId", playlistId).put("isAudioOnly", true)
                parseQueue(post("next", body, authed = auth.isSignedIn()))
            }
        }
        return browse(browseId)
    }

    /** Songs like this one: the "Start radio" mix (the seed song comes first). */
    override suspend fun radio(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        val body = webBody()
            .put("videoId", videoId)
            .put("playlistId", "RDAMVM$videoId")
            .put("isAudioOnly", true)
        parseQueue(post("next", body, authed = true))
    }

    override suspend fun like(videoId: String) {
        withContext(Dispatchers.IO) {
            post("like/like", webBody().put("target", JSONObject().put("videoId", videoId)), authed = true)
        }
    }

    override suspend fun unlike(videoId: String) {
        withContext(Dispatchers.IO) {
            post("like/removelike", webBody().put("target", JSONObject().put("videoId", videoId)), authed = true)
        }
    }

    override suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String) {
        withContext(Dispatchers.IO) {
            val action = JSONObject()
                .put("action", "ACTION_REMOVE_VIDEO")
                .put("removedVideoId", videoId)
                .put("setVideoId", setVideoId)
            val body = webBody()
                .put("playlistId", playlistId.removePrefix("VL"))
                .put("actions", JSONArray().put(action))
            post("browse/edit_playlist", body, authed = true)
        }
    }

    override suspend fun renamePlaylist(playlistId: String, title: String) {
        withContext(Dispatchers.IO) {
            val action = JSONObject().put("action", "ACTION_SET_PLAYLIST_NAME").put("playlistName", title)
            val body = webBody()
                .put("playlistId", playlistId.removePrefix("VL"))
                .put("actions", JSONArray().put(action))
            post("browse/edit_playlist", body, authed = true)
        }
    }

    override suspend fun deletePlaylist(playlistId: String) {
        withContext(Dispatchers.IO) {
            post("playlist/delete", webBody().put("playlistId", playlistId.removePrefix("VL")), authed = true)
        }
    }

    override suspend fun addToPlaylist(playlistId: String, videoId: String) {
        withContext(Dispatchers.IO) {
            val action = JSONObject()
                .put("action", "ACTION_ADD_VIDEO")
                .put("addedVideoId", videoId)
                .put("dedupeOption", "DEDUPE_OPTION_SKIP")
            val body = webBody()
                .put("playlistId", playlistId.removePrefix("VL"))
                .put("actions", JSONArray().put(action))
            post("browse/edit_playlist", body, authed = true)
        }
    }

    override suspend fun createPlaylist(title: String, videoIds: List<String>): String? = withContext(Dispatchers.IO) {
        val body = webBody()
            .put("title", title)
            .put("description", "Saved from Booma")
            .put("privacyStatus", "PRIVATE")
            .put("videoIds", JSONArray(videoIds))
        post("playlist/create", body, authed = true).optString("playlistId").takeIf { it.isNotEmpty() }
    }

    override suspend fun search(query: String): List<Track> = searchAll(query).songs

    /** Songs, plus the albums and playlists that match (one request). */
    override suspend fun searchAll(query: String): SearchResults {
        if (query.isBlank()) return SearchResults(emptyList(), emptyList())
        return withContext(Dispatchers.IO) {
            val root = post("search", webBody().put("query", query), authed = auth.isSignedIn())
            SearchResults(parseTracks(root), parsePlaylists(root, onlyCollections = true))
        }
    }

    /** The song's loudness relative to YouTube's reference level, or null if YouTube doesn't say. */
    suspend fun loudnessDb(videoId: String): Double? = withContext(Dispatchers.IO) {
        val root = post("player", webBody().put("videoId", videoId), authed = auth.isSignedIn())
        val audio = root.optJSONObject("playerConfig")?.optJSONObject("audioConfig") ?: return@withContext null
        val db = audio.optDouble("loudnessDb", Double.NaN)
        if (db.isNaN()) null else db
    }

    /** What YouTube Music suggests while you type. */
    override suspend fun suggestions(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val root = post("music/get_search_suggestions", webBody().put("input", input), authed = auth.isSignedIn())
            val found = LinkedHashSet<String>()
            collectKey(root, "searchSuggestionRenderer") { found += runsText(it.optJSONObject("suggestion")) }
            found.filter { it.isNotBlank() }.take(8)
        }
    }

    private suspend fun browse(browseId: String): List<Track> = withContext(Dispatchers.IO) {
        parseTracks(post("browse", webBody().put("browseId", browseId), authed = true))
    }

    private fun webBody(): JSONObject {
        val client = JSONObject()
            .put("clientName", "WEB_REMIX")
            .put("clientVersion", settings.webVersion())
            .put("hl", "en")
            .put("gl", "US")
        return JSONObject().put("context", JSONObject().put("client", client))
    }

    private fun post(
        endpoint: String,
        body: JSONObject,
        authed: Boolean,
        userAgent: String = desktopUa,
        query: String? = null,
    ): JSONObject {
        val url = "$origin/youtubei/v1/$endpoint?" + (query?.let { "$it&" } ?: "") + "prettyPrint=false"
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON))
            .header("User-Agent", userAgent)
            .header("Origin", origin)
            .header("Referer", "$origin/")
            .header("X-Goog-AuthUser", "0")
        if (authed) {
            val cookie = auth.cookie() ?: throw IllegalStateException("Not signed in")
            request.header("Cookie", cookie)
            request.header("Authorization", sapisidHash(cookie))
        }
        // the shared client keeps the connection to YouTube open between requests
        Http.client.newCall(request.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("YouTube Music returned HTTP ${response.code} for $endpoint")
            }
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    /** Authorisation header the web client uses: SHA-1 of "timestamp SAPISID origin". */
    private fun sapisidHash(cookie: String): String {
        val sapisid = cookie.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=") }
            ?.substringAfter("=")
            ?: throw IllegalStateException("Sign-in cookie is missing SAPISID")
        val ts = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$ts $sapisid $origin".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${ts}_$digest"
    }

    private fun parseTracks(root: JSONObject): List<Track> {
        val seen = LinkedHashMap<String, Track>()
        collectRows(root) { row ->
            songFromRow(row)?.let { if (!seen.containsKey(it.id)) seen[it.id] = it }
        }
        // Songs shown as cards (Home shelves such as "Quick picks" or "Listen again") open a watch page.
        collectKey(root, "musicTwoRowItemRenderer") { item ->
            songFromCard(item)?.let { if (!seen.containsKey(it.id)) seen[it.id] = it }
        }
        return seen.values.toList()
    }

    /** A song row (search results, playlists, albums, "Quick picks"), or null if the row isn't a song. */
    private fun songFromRow(row: JSONObject): Track? {
        val columns = row.optJSONArray("flexColumns") ?: return null
        val titleRuns = runsOf(columns, 0)
        val title = titleRuns.joinToString("") { it.optString("text") }
        val artist = artistOf((1 until columns.length()).flatMap { runsOf(columns, it) })
        val id = row.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
            .ifEmpty {
                titleRuns.firstNotNullOfOrNull {
                    it.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
                        ?.optString("videoId")?.takeIf { v -> v.isNotEmpty() }
                }.orEmpty()
            }
        if (id.isEmpty() || title.isBlank()) return null
        val setVideoId = row.optJSONObject("playlistItemData")?.optString("playlistSetVideoId")?.takeIf { it.isNotEmpty() }
        return Track(id = id, title = title, artist = artist, thumb = thumbOf(row.optJSONObject("thumbnail")), setVideoId = setVideoId)
    }

    /** A song shown as a card (it opens a watch page), or null. */
    private fun songFromCard(item: JSONObject): Track? {
        val id = item.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
            ?.optString("videoId").orEmpty()
        val title = runsText(item.optJSONObject("title"))
        if (id.isEmpty() || title.isBlank()) return null
        val sub = item.optJSONObject("subtitle")?.optJSONArray("runs")
        val runs = if (sub == null) emptyList() else (0 until sub.length()).map { sub.getJSONObject(it) }
        return Track(id, title, artistOf(runs), thumbOf(item.optJSONObject("thumbnailRenderer")))
    }

    /** An album, playlist or mix shown as a card, or null (artists, podcasts and songs aren't). */
    private fun listFromCard(item: JSONObject): Playlist? {
        val title = runsText(item.optJSONObject("title"))
        if (title.isBlank()) return null
        val subtitle = runsText(item.optJSONObject("subtitle"))
        val thumb = thumbOf(item.optJSONObject("thumbnailRenderer"))
        val nav = item.optJSONObject("navigationEndpoint")
        val browse = nav?.optJSONObject("browseEndpoint")
        if (browse != null) {
            val id = browse.optString("browseId")
            val type = pageTypeOf(browse)
            val isList = type == "MUSIC_PAGE_TYPE_ALBUM" || type == "MUSIC_PAGE_TYPE_PLAYLIST" ||
                (type.isEmpty() && (id.startsWith("VL") || id.startsWith("MPREb")))
            return if (id.isNotEmpty() && isList) Playlist(id, title, subtitle, thumb) else null
        }
        val mix = nav?.optJSONObject("watchPlaylistEndpoint")?.optString("playlistId").orEmpty()
        return if (mix.isNotEmpty()) Playlist(MIX_PREFIX + mix, title, subtitle, thumb) else null
    }

    private fun pageTypeOf(browse: JSONObject): String =
        browse.optJSONObject("browseEndpointContextSupportedConfigs")
            ?.optJSONObject("browseEndpointContextMusicConfig")
            ?.optString("pageType").orEmpty()

    /** Home shelves in page order: each carousel's title and its songs, albums, playlists and mixes. */
    private fun parseShelves(root: JSONObject): List<Shelf> {
        val shelves = ArrayList<Shelf>()
        collectKey(root, "musicCarouselShelfRenderer") { shelf ->
            val header = shelf.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
            val title = runsText(header?.optJSONObject("title"))
            val contents = shelf.optJSONArray("contents") ?: return@collectKey
            val songs = ArrayList<Track>()
            val lists = ArrayList<Playlist>()
            var rows = false
            for (i in 0 until contents.length()) {
                val entry = contents.optJSONObject(i) ?: continue
                entry.optJSONObject("musicResponsiveListItemRenderer")?.let { row ->
                    rows = true
                    songFromRow(row)?.let { songs += it }
                }
                entry.optJSONObject("musicTwoRowItemRenderer")?.let { card ->
                    songFromCard(card)?.let { songs += it } ?: listFromCard(card)?.let { lists += it }
                }
            }
            if (title.isNotBlank()) shelves += Shelf(title, songs.distinctBy { it.id }, lists.distinctBy { it.id }, rows)
        }
        return shelves
    }

    /** The token for the next page of a browse response (first page or a continuation), if any. */
    private fun continuationOf(root: JSONObject): String? {
        var token: String? = null
        collectKey(root, "nextContinuationData") { if (token == null) token = it.optString("continuation").takeIf { t -> t.isNotEmpty() } }
        return token
    }

    /**
     * The second column looks like "Artist • Album • 3:45", or "Song • Artist • 3:45", or "Artist 12M plays".
     * The artist is the first run that isn't a separator, a media type word, a duration, or a play count.
     */
    private fun artistOf(runs: List<JSONObject>): String {
        val mediaWords = setOf("Song", "Video", "Album", "Single", "EP", "Playlist", "Artist", "Podcast", "Episode")
        for (run in runs) {
            val text = run.optString("text").trim()
            if (text.isEmpty() || text.contains("•")) continue
            if (text in mediaWords) continue
            if (Regex("""^\d{1,2}(:\d{2}){1,2}$""").matches(text)) continue
            if (text.contains("plays", ignoreCase = true) || text.contains("views", ignoreCase = true)) continue
            return text
        }
        return ""
    }

    /** Text runs of flex column [index], e.g. the title (0) or artist and album (1). */
    private fun runsOf(columns: JSONArray, index: Int): List<JSONObject> {
        val column = columns.optJSONObject(index)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?: return emptyList()
        val runs = column.optJSONObject("text")?.optJSONArray("runs") ?: return emptyList()
        return (0 until runs.length()).map { runs.getJSONObject(it) }
    }

    /** Library playlists: grid tiles (two-row items) and list rows that open a "VL..." playlist. */
    private fun parsePlaylists(root: JSONObject, onlyCollections: Boolean = false): List<Playlist> {
        val found = LinkedHashMap<String, Playlist>()
        if (onlyCollections) {
            // search results: album and playlist rows
            collectKey(root, "musicResponsiveListItemRenderer") { row ->
                val browse = row.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint") ?: return@collectKey
                val id = browse.optString("browseId")
                val type = pageTypeOf(browse)
                if (id.isEmpty() || found.containsKey(id)) return@collectKey
                if (type != "MUSIC_PAGE_TYPE_ALBUM" && type != "MUSIC_PAGE_TYPE_PLAYLIST") return@collectKey
                val columns = row.optJSONArray("flexColumns") ?: return@collectKey
                val title = runsOf(columns, 0).joinToString("") { it.optString("text") }
                if (title.isBlank()) return@collectKey
                val subtitle = runsOf(columns, 1).joinToString("") { it.optString("text") }
                found[id] = Playlist(id, title, subtitle, thumbOf(row.optJSONObject("thumbnail")))
            }
            return found.values.toList()
        }
        collectKey(root, "musicTwoRowItemRenderer") { item ->
            val id = item.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                ?.optString("browseId").orEmpty()
            val title = runsText(item.optJSONObject("title"))
            if (id.isNotEmpty() && title.isNotBlank() && !found.containsKey(id)) {
                found[id] = Playlist(id, title, runsText(item.optJSONObject("subtitle")), thumbOf(item.optJSONObject("thumbnailRenderer")))
            }
        }
        collectKey(root, "musicResponsiveListItemRenderer") { row ->
            val id = row.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")
                ?.optString("browseId").orEmpty()
            if (!id.startsWith("VL") || found.containsKey(id)) return@collectKey
            val columns = row.optJSONArray("flexColumns") ?: return@collectKey
            val title = runsOf(columns, 0).joinToString("") { it.optString("text") }
            if (title.isBlank()) return@collectKey
            val subtitle = runsOf(columns, 1).joinToString("") { it.optString("text") }
            found[id] = Playlist(id, title, subtitle, thumbOf(row.optJSONObject("thumbnail")))
        }
        return found.values.toList()
    }

    /** Songs in a play queue response (radio, up next). */
    private fun parseQueue(root: JSONObject): List<Track> {
        val found = LinkedHashMap<String, Track>()
        collectKey(root, "playlistPanelVideoRenderer") { v ->
            val id = v.optString("videoId")
            val title = runsText(v.optJSONObject("title"))
            if (id.isEmpty() || title.isBlank() || found.containsKey(id)) return@collectKey
            val by = v.optJSONObject("longBylineText")?.optJSONArray("runs")
            val runs = if (by == null) emptyList() else (0 until by.length()).map { by.getJSONObject(it) }
            val thumbs = v.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            val thumb = thumbs?.takeIf { it.length() > 0 }?.optJSONObject(thumbs.length() - 1)?.optString("url")
            found[id] = Track(id, title, artistOf(runs), thumb?.takeIf { it.isNotEmpty() })
        }
        return found.values.toList()
    }

    /** Largest thumbnail URL inside a { musicThumbnailRenderer: { thumbnail: { thumbnails: [...] } } } holder. */
    private fun thumbOf(holder: JSONObject?): String? {
        val list = holder?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails") ?: return null
        if (list.length() == 0) return null
        return list.optJSONObject(list.length() - 1)?.optString("url")?.takeIf { it.isNotEmpty() }
    }

    private fun runsText(holder: JSONObject?): String {
        val runs = holder?.optJSONArray("runs") ?: return ""
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() }
    }

    /** Walks the whole response and calls [visit] for every object stored under [key]. */
    private fun collectKey(node: Any?, key: String, visit: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject(key)?.let(visit)
                val keys = node.keys()
                while (keys.hasNext()) collectKey(node.get(keys.next()), key, visit)
            }
            is JSONArray -> for (i in 0 until node.length()) collectKey(node.get(i), key, visit)
        }
    }

    /** Walks the whole response and calls [visit] for every song row it finds. */
    private fun collectRows(node: Any?, visit: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("musicResponsiveListItemRenderer")?.let(visit)
                val keys = node.keys()
                while (keys.hasNext()) collectRows(node.get(keys.next()), visit)
            }
            is JSONArray -> for (i in 0 until node.length()) collectRows(node.get(i), visit)
        }
    }

    companion object {
        /** Mixes ("My Mix", "Discover Mix"…) open as a play queue, not a browse page. */
        const val MIX_PREFIX = "mix:"
        private val JSON = "application/json".toMediaType()
    }
}
