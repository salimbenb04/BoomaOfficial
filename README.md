# Booma for Android

Native Kotlin + Jetpack Compose music player for YouTube Music, with the Booma owl and the PC app's themes.
Library, likes and playlists sync with Booma on PC through the same YouTube Music account.

## Features (v1.3)

- Sign in to YouTube Music (session stored encrypted on the phone).
- Home, search, liked songs, library playlists (open any playlist).
- Album art on every row; tap a song to play the whole list from there; Play and Shuffle buttons.
- Background playback with lock-screen / notification controls, headphone-unplug pause, audio focus.
- Now Playing screen: big artwork, the Booma snake seek bar (head can be turned off in Account), previous / play / next, shuffle, repeat (all / one), dancing owl.
- Themes: the 24 PC palettes plus Default, with a gradient or flat background (Account tab).
- Faster starts: YouTube's player code loads at launch, the next song is looked up while the current one plays,
  and playback starts after 1 s of buffered audio.
- Smoother screens: lists are kept in memory and refreshed quietly, placeholder rows while loading,
  fading images and tab changes, floating mini player (swipe sideways to skip), swipe down to close Now Playing.

### New in 1.3.1
- Fixed black song titles and icons: text and icons without their own colour were black (the system default)
  wherever no themed surface was underneath, such as Now Playing and the lists. They're white on every theme now,
  and Now Playing darkens bright covers a little more behind the text.
- Now Playing: swipe the cover or the song title left for the next song, right for the previous one.

### New in 1.3
- Casting is basic again: Google's standard TV player (cover, title, progress); the Booma TV lyrics screen is gone.
- Casting can't crash Booma any more: every step that talks to the TV is guarded (a crash when the TV's queue
  emptied is fixed).
- Songs that wouldn't play on the TV: the TV is now told each song's real audio format (M4A or WebM, read from
  the link) instead of always "M4A". A song the TV still can't play is skipped with a message, and if the
  playing song can't be prepared for the TV, the next one is used.
- If casting can't start, a message says why.

### New in 1.2
- Sign out works: it also clears the sign-in page's Google session (before, that page signed you straight back
  in). Sign out is at the top of You.
- Readable on every theme: text and accents are checked for contrast against the theme or cover colour and
  adjusted when needed; the status bar icons stay light.
- Unlike: the thumb in Now Playing is filled when you like a song; tap it again (or the song menu's
  "Remove like", or the car's like button) to undo. Unliked songs leave Library > Liked songs.
- Playlists: New playlist (Library > Playlists, or "New playlist" when adding a song); in your own playlists,
  the ⋮ menu renames or deletes it and each song's menu has "Remove from this playlist".

### New in 1.1
- Now Playing while casting: the TV's name, a volume slider for the TV, and Stop.
- Android Auto, the notification and the lock screen get Like, Shuffle up next, Repeat and Start radio buttons.
- You > Car > Lyrics in the car (off by default): the current lyric line shows where the artist name goes, on
  Android Auto, car screens over Bluetooth and the lock screen.

### New in 1.0
- Smoother, built like Metrolist: the same build tools and a much newer Jetpack Compose (faster list
  scrolling), one shared HTTP/2 connection to YouTube for lists, song lookups and the player (no new
  handshake per request, so songs start sooner), image loading on the same connections, Booma's own code
  compiled ahead of time on install, and the background gradient drawn from a cache.
- Home like YouTube Music: Quick picks, Listen again, mixes, albums and the other shelves, scrolling sideways.
  Albums, playlists and mixes open as lists. Home also works signed out.
- Search: suggestions while you type, recent searches (tap to repeat, remove, clear), and albums and
  playlists above the songs.
- Sound (You > Sound): Even out volume (on by default, turns loud songs down to YouTube's reference level),
  Crossfade (off, 3, 6 or 10 s), Audio quality (High or Data saver; downloads follow it too).
- Android Auto: Booma appears as a music app in the car with Home, Liked songs, Playlists and Downloaded,
  search, and "play … on Booma" by voice.
- Cast: the Cast button in Now Playing plays on a Chromecast, Google TV / Android TV or Nest speaker.
  Play, pause, seek and skip from Booma or the Cast notification; stopping hands the song back to the phone.

### New in 0.9
- Smoother: download progress no longer redraws every song row (throttled, per-row); song changes no longer
  rebuild the whole app; no full-screen blur behind Now Playing; one shared image cache (memory + 256 MB disk)
  without fades in lists; lists are saved on disk, so Booma opens with your music already showing.
- The owl appears only on the playing song in lists and in Up next.
- Widget: tapping a playlist opens it in Booma.

### New in 0.8
- Smoother: theme changes fade only the background (drawn, not rebuilt); the owl, snake bar, mini player and
  Now Playing repaint without rebuilding screens; lists recycle rows by type; images are requested once.
- Optimised release build (R8 + startup profiles). Build with `gradlew assembleRelease` for the smooth version.
- Widget: play / pause / next / previous work (also when Booma is closed: play resumes the last queue);
  at 4x2 and bigger it shows your recent playlists, tap one to open it in Booma.
- Removed: Diagnostics screen and response logging, backup YouTube player requests, unused libraries.
  If Booma crashes, You shows "Booma closed unexpectedly" with a Copy button for the report.

### New in 0.7
- Booma logo as the app icon (adaptive, round, and themed-icon layer for Android 13+), owl in the status bar.
- Tapping the media notification, lock-screen controls or dynamic island opens Booma's player.
- Home-screen widget: cover, song, artist, previous / play-pause / next; tap to open the player.
  Play resumes the last queue even after Booma was closed.
- Themes now colour everything: text, accents, the owl and the snake follow the theme's hue (PC formula).
- "Match the song's cover" (You): colours come from the playing song's artwork and fade between songs.

### New in 0.6
- Up next button on the mini player opens the queue directly.
- Drag songs in the queue by their handle to reorder them.
- Add to queue puts the song right after the one playing (several additions keep their order), like Spotify
  and Booma on PC. "Play next" is gone.
- Shuffle reorders the real queue, so Up next shows the true play order; turning it off restores the list order.
- Swiping Booma away from recent apps stops the music (You > Keep playing after closing Booma to change that).

### New in 0.5
- Downloads for offline playback: from any song's menu, or the download button on a playlist or your liked songs.
  Audio, cover art and lyrics are saved in the app's private storage; downloaded songs play from the phone
  even with no connection. Library > Downloaded lists them with the space they use.
- Lyrics (lrclib, like the PC app): synced lines highlight and follow the song; tap a line to jump there.
  Lyrics are saved after the first fetch, so they work offline.
- Up next: see the queue, jump to any song, drag to reorder, remove songs, save the queue as a YouTube Music playlist.
- Song menu on every song: add to queue, start radio, like, add to playlist, download / remove download.
- Sleep timer: 15 / 30 / 60 minutes or the end of the current song.

## How playback works

Queue items point at `booma://stream/<videoId>`. When a song starts loading, the player asks
NewPipe Extractor for the audio URL (it handles YouTube's signature and throttling decoding), falling back
to the YouTube Music player request. Links are cached for 2 hours; if one fails, the player fetches a
fresh link once and retries.

## Build

Needs JDK 21 and the Android SDK (platform 37), the same setup as Metrolist. The Gradle wrapper is included:

```
gradlew.bat assembleRelease
```

The APK is `app/build/outputs/apk/release/app-release.apk` (optimised; signed with your debug key so it
installs over earlier builds). `assembleDebug` still works but runs noticeably slower. The first build downloads
Gradle 9.7.1 (already on your PC if you built the Metrolist fork) and NewPipe Extractor from JitPack.

### Android Auto with a sideloaded APK
Android Auto only lists apps from the Play Store unless you allow others once: on the phone open Android Auto
settings, tap "Version" ten times to unlock developer settings, then in the ⋮ menu > Developer settings turn on
"Unknown sources". Booma then shows up in the car's app launcher.

## Licences

NewPipe Extractor is GPL-3.0. Fine for personal use; if you publish Booma for Android, publish it under GPL-3.0 too.
This is an unofficial client: distribute the APK directly, not through the Play Store.

## Known limits

- Long playlists show the first page (about 100 songs); paging comes next.
- Artists on Home and in search don't open yet (no artist pages).
- Android Auto doesn't let apps draw their own screens, so lyrics in the car are one line at a time in place of
  the artist (songs with synced lyrics only).
- Casting needs the phone and the TV on the same Wi-Fi. Downloaded songs cast from YouTube, so the TV needs
  internet. Booma's own extras (crossfade, even-out volume) apply on the phone, not on the TV.
- Even out volume uses the loudness YouTube reports; songs it has no figure for play as they are.
- Downloads run while the app is open or playing; closing the app from recents stops unfinished ones.

## Crash reports

If the app crashes, the full error is saved and shown in the Debug tab ("Crash from last run") the next time it opens.
