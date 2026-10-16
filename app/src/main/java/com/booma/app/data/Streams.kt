package com.booma.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.DeliveryMethod

/**
 * Gets the audio URL with NewPipe Extractor, which handles YouTube's signature and throttling decoding.
 * Uses the stream extractor directly: only the stream data is fetched and parsed, nothing else.
 *
 * [quality] "high" takes the best audio, "low" the smallest (saves mobile data).
 * [forCast] prefers M4A (AAC), which every Cast device can play.
 */
class NewPipeResolver(
    private val quality: () -> String = { "high" },
    private val forCast: Boolean = false,
) : StreamResolver {
    override suspend fun resolve(videoId: String): Uri? = withContext(Dispatchers.IO) {
        Streams.initNewPipe()
        val started = System.currentTimeMillis()
        try {
            val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
            extractor.fetchPage()
            val all = extractor.audioStreams
            var direct = all.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            if (forCast) {
                direct.filter { it.format?.mimeType?.contains("mp4") == true }.takeIf { it.isNotEmpty() }?.let { direct = it }
            }
            val best = if (quality() == "low") direct.minByOrNull { it.averageBitrate } else direct.maxByOrNull { it.averageBitrate }
            Log.d(TAG, "$videoId: ${direct.size} direct streams, ${best?.averageBitrate ?: 0} kbps, ${System.currentTimeMillis() - started} ms")
            best?.content?.let { Uri.parse(it) }
        } catch (e: Exception) {
            Log.w(TAG, "$videoId: no stream", e)
            null
        }
    }
}

/**
 * One shared resolver for the app and the playback service:
 * - links are cached for 2 hours (YouTube links last about 6),
 * - the same song is never looked up twice at once (a prefetch and the player share one lookup),
 * - warmUp() loads YouTube's player code at launch so the first song starts faster.
 */
private const val TAG = "Booma"

object Streams {
    private const val CACHE_MS = 2 * 60 * 60 * 1000L
    private const val WARMUP_ID = "jNQXAC9IVRw"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var newPipeReady = false
    private var shared: StreamResolver? = null
    private val cache = HashMap<String, Pair<Long, Uri>>()
    private val inflight = HashMap<String, Deferred<Uri?>>()

    @Synchronized
    fun initNewPipe() {
        if (!newPipeReady) {
            NewPipe.init(NewPipeDownloader())
            newPipeReady = true
        }
    }

    @Synchronized
    fun resolver(context: Context): StreamResolver {
        shared?.let { return it }
        val settings = SettingsStore(context.applicationContext)
        val resolver = NewPipeResolver(quality = { settings.audioQuality() })
        shared = resolver
        return resolver
    }

    private val castResolver = NewPipeResolver(forCast = true)
    private val castCache = HashMap<String, Pair<Long, Uri>>()

    /** An audio link a Cast device (TV, speaker) can play: an M4A link, cached like the others. */
    suspend fun resolveForCast(videoId: String): Uri? {
        synchronized(castCache) {
            castCache[videoId]?.takeIf { System.currentTimeMillis() - it.first < CACHE_MS }?.let { return it.second }
        }
        val uri = castResolver.resolve(videoId) ?: return null
        synchronized(castCache) { castCache[videoId] = System.currentTimeMillis() to uri }
        return uri
    }

    private fun cached(videoId: String): Uri? = synchronized(cache) {
        cache[videoId]?.takeIf { System.currentTimeMillis() - it.first < CACHE_MS }?.second
    }

    suspend fun resolveCached(context: Context, videoId: String): Uri? {
        cached(videoId)?.let { return it }
        val app = context.applicationContext
        val job = synchronized(inflight) {
            inflight[videoId] ?: scope.async {
                try {
                    val uri = resolver(app).resolve(videoId)
                    if (uri != null) synchronized(cache) { cache[videoId] = System.currentTimeMillis() to uri }
                    uri
                } finally {
                    synchronized(inflight) { inflight.remove(videoId) }
                }
            }.also { inflight[videoId] = it }
        }
        return job.await()
    }

    /** Look a song up in the background so it starts instantly when its turn comes. */
    fun prefetch(context: Context, videoId: String) {
        if (videoId.isBlank()) return
        val app = context.applicationContext
        if (videoId != WARMUP_ID) scope.launch { Loudness.of(app, videoId) }
        if (cached(videoId) != null || Downloads.fileFor(videoId) != null) return
        scope.launch { resolveCached(app, videoId) }
    }

    fun warmUp(context: Context) {
        prefetch(context, WARMUP_ID)
    }

    fun forget(videoId: String) {
        synchronized(cache) { cache.remove(videoId) }
    }
}
