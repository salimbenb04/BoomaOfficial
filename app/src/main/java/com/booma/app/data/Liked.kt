package com.booma.app.data

import androidx.compose.runtime.mutableStateMapOf

/**
 * Which songs you like, so the like buttons show the right state and can undo a like.
 * Filled from your liked songs list (saved on the phone, refreshed when Library loads it)
 * and kept up to date when you like or unlike a song in Booma.
 */
object Liked {
    private val state = mutableStateMapOf<String, Boolean>()   // video id -> liked

    fun isLiked(id: String): Boolean = state[id] == true

    /** False only for songs unliked in Booma since the list was last loaded (hidden from the liked list). */
    fun unliked(id: String): Boolean = state[id] == false

    /** Your liked songs list as YouTube Music has it now (songs no longer in it aren't liked any more). */
    fun seed(tracks: List<Track>) {
        val ids = tracks.mapTo(HashSet()) { it.id }
        state.keys.filter { it !in ids && state[it] == true }.forEach { state.remove(it) }
        ids.forEach { if (state[it] != false) state[it] = true }
    }

    fun clear() {
        state.clear()
    }

    /** Like or unlike [track] on YouTube Music. Returns the new state; throws if YouTube refused. */
    suspend fun toggle(repo: YtmRepository, track: Track): Boolean {
        val like = !isLiked(track.id)
        if (like) repo.like(track.id) else repo.unlike(track.id)
        state[track.id] = like
        // keep the saved liked list in step, so it's right the next time it opens
        Lists.tracks["liked"]?.let { list ->
            Lists.tracks["liked"] = if (like) listOf(track) + list.filterNot { it.id == track.id } else list.filterNot { it.id == track.id }
        }
        return like
    }
}
