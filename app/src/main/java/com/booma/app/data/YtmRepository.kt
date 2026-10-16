package com.booma.app.data

import android.net.Uri

/** Library, search and home content from YouTube Music. */
interface YtmRepository {
    suspend fun home(): List<Track>
    suspend fun homeShelves(pages: Int = 2): List<Shelf>
    suspend fun search(query: String): List<Track>
    suspend fun searchAll(query: String): SearchResults
    suspend fun suggestions(input: String): List<String>
    suspend fun likedSongs(): List<Track>
    suspend fun playlists(): List<Playlist>
    suspend fun playlist(browseId: String): List<Track>
    suspend fun radio(videoId: String): List<Track>
    suspend fun like(videoId: String)
    suspend fun unlike(videoId: String)
    suspend fun removeFromPlaylist(playlistId: String, videoId: String, setVideoId: String)
    suspend fun renamePlaylist(playlistId: String, title: String)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun addToPlaylist(playlistId: String, videoId: String)
    suspend fun createPlaylist(title: String, videoIds: List<String>): String?
}

/** Turns a video id into a playable audio URL. */
interface StreamResolver {
    suspend fun resolve(videoId: String): Uri?
}
