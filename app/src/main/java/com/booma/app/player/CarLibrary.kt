package com.booma.app.player

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.booma.app.data.AuthStore
import com.booma.app.data.Downloads
import com.booma.app.data.Lists
import com.booma.app.data.Track
import com.booma.app.data.Ytm
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future

/**
 * What Android Auto (and other media browsers, like Wear OS or car head units) see of Booma:
 *
 *   Booma
 *   ├─ Home        your Home shelves (Quick picks, mixes, albums…)
 *   ├─ Liked songs
 *   ├─ Playlists   your library playlists
 *   └─ Downloaded  songs saved on the phone
 *
 * Picking a song plays the list it is in, starting from that song. Voice search ("play … on Booma")
 * plays the matching songs. It also gives every queued song its booma:// address, as before.
 */
@OptIn(UnstableApi::class)
class CarLibrary(
    private val context: Context,
    private val scope: CoroutineScope,
) : MediaLibrarySession.Callback {

    // ---------------------------------------------------------------- buttons

    /** Let controllers (Android Auto, the notification, Booma itself) use the like / shuffle / repeat / radio buttons. */
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val base = super.onConnect(session, controller)
        val commands = base.availableSessionCommands.buildUpon().apply { CarButtons.commands.forEach { add(it) } }.build()
        return MediaSession.ConnectionResult.accept(commands, base.availablePlayerCommands)
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        val handled = CarButtons.handle(context, session.player, customCommand.customAction, scope)
        return Futures.immediateFuture(
            SessionResult(if (handled) SessionResult.RESULT_SUCCESS else SessionError.ERROR_NOT_SUPPORTED)
        )
    }

    // ---------------------------------------------------------------- playing

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
    ): ListenableFuture<MutableList<MediaItem>> =
        Futures.immediateFuture(mediaItems.map { it.playable() }.toMutableList())

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaItemsWithStartPosition> {
        val first = mediaItems.firstOrNull()
        val voice = first?.requestMetadata?.searchQuery
        val picked = first?.mediaId.orEmpty()
        // A song picked in the car's browser, or a voice request: play its whole list from there.
        if (mediaItems.size == 1 && (!voice.isNullOrBlank() || picked.contains(SEP))) {
            return scope.future(Dispatchers.IO) {
                val (songs, start) = if (!voice.isNullOrBlank()) {
                    runCatching { Ytm.client(context).search(voice) }.getOrDefault(emptyList()) to 0
                } else {
                    val list = songsOf(picked.substringBeforeLast(SEP))
                    list to list.indexOfFirst { it.id == picked.substringAfterLast(SEP) }.coerceAtLeast(0)
                }
                if (songs.isEmpty()) {
                    MediaItemsWithStartPosition(listOf(mediaItems[0].playable()), 0, 0L)
                } else {
                    MediaItemsWithStartPosition(songs.map { it.toMediaItem() }, start, 0L)
                }
            }
        }
        return Futures.immediateFuture(
            MediaItemsWithStartPosition(mediaItems.map { it.playable() }, startIndex, startPositionMs)
        )
    }

    // Play pressed on the widget, headphones or the car while Booma was closed: continue the last queue.
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): ListenableFuture<MediaItemsWithStartPosition> {
        val saved = ResumeStore.load(context)
            ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
        return Futures.immediateFuture(saved)
    }

    // ---------------------------------------------------------------- browsing

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT, "Booma"), params))

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> =
        Futures.immediateFuture(LibraryResult.ofItem(folder(mediaId, ""), null))

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        scope.future(Dispatchers.IO) {
            val all = runCatching { children(parentId) }.getOrDefault(emptyList())
            val pageItems = if (pageSize > 0) all.drop(page * pageSize).take(pageSize) else all
            LibraryResult.ofItemList(pageItems, params)
        }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> {
        session.notifySearchResultChanged(browser, query, 20, params)
        return Futures.immediateFuture(LibraryResult.ofVoid(params))
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
        scope.future(Dispatchers.IO) {
            val parent = SEARCH + query
            val songs = runCatching { songsOf(parent) }.getOrDefault(emptyList())
            LibraryResult.ofItemList(songs.map { song(parent, it) }, params)
        }

    private suspend fun children(parentId: String): List<MediaItem> {
        val signedIn = AuthStore(context).isSignedIn()
        return when {
            parentId == ROOT -> listOfNotNull(
                folder(HOME, "Home").takeIf { signedIn },
                folder(LIKED, "Liked songs").takeIf { signedIn },
                folder(PLAYLISTS, "Playlists").takeIf { signedIn },
                folder(DOWNLOADS, "Downloaded"),
            )
            parentId == HOME -> shelves().mapIndexed { i, shelf -> folder("$SHELF$i", shelf.title) }
            parentId.startsWith(SHELF) -> {
                val shelf = shelves().getOrNull(parentId.removePrefix(SHELF).toIntOrNull() ?: -1) ?: return emptyList()
                shelf.songs.map { song(parentId, it) } +
                    shelf.lists.map { folder(LIST + it.id, it.title, it.subtitle, it.thumb) }
            }
            parentId == PLAYLISTS -> {
                val lists = Lists.playlists["library"] ?: Ytm.client(context).playlists().also { Lists.playlists["library"] = it }
                lists.map { folder(LIST + it.id, it.title, it.subtitle, it.thumb) }
            }
            else -> songsOf(parentId).map { song(parentId, it) }
        }
    }

    /** The songs in a browsable list (the same lists the app shows, from its cache when possible). */
    private suspend fun songsOf(parent: String): List<Track> {
        val ytm = Ytm.client(context)
        return when {
            parent == LIKED -> Lists.tracks["liked"] ?: ytm.likedSongs().also { Lists.tracks["liked"] = it }
            parent == DOWNLOADS -> Downloads.tracks()
            parent.startsWith(LIST) -> {
                val id = parent.removePrefix(LIST)
                Lists.tracks["playlist:$id"] ?: ytm.playlist(id).also { Lists.tracks["playlist:$id"] = it }
            }
            parent.startsWith(SHELF) -> shelves().getOrNull(parent.removePrefix(SHELF).toIntOrNull() ?: -1)?.songs.orEmpty()
            parent.startsWith(SEARCH) -> ytm.search(parent.removePrefix(SEARCH))
            else -> emptyList()
        }
    }

    private suspend fun shelves() =
        Lists.shelves["home"] ?: Ytm.client(context).homeShelves(1).also { Lists.shelves["home"] = it }

    private fun folder(id: String, title: String, subtitle: String? = null, art: String? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(art?.let { Uri.parse(bigArt(it)) })
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

    private fun song(parent: String, track: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId("$parent$SEP${track.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setSubtitle(track.artist)
                    .setArtworkUri(track.thumb?.let { Uri.parse(bigArt(it)) })
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()

    /** The item with its plain video id and booma:// address, ready for the player. */
    private fun MediaItem.playable(): MediaItem {
        val id = mediaId.substringAfterLast(SEP)
        return buildUpon().setMediaId(id).setUri(PlaybackService.streamUri(id)).build()
    }

    companion object {
        const val ROOT = "root"
        const val HOME = "home"
        const val LIKED = "liked"
        const val PLAYLISTS = "playlists"
        const val DOWNLOADS = "downloads"
        const val SHELF = "shelf/"
        const val LIST = "list/"
        const val SEARCH = "search/"
        const val SEP = "|"
    }
}
