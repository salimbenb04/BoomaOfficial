# Booma TV: the screen your TV shows while you cast

`index.html` is Booma's own Cast receiver. Without it, casting uses Google's standard player (cover, title,
progress). With it, the TV shows:

- the cover, title, artist, the dancing owl and a progress bar,
- synced lyrics that follow the song (or lyrics filling the whole screen),
- up next,
- your Booma theme colours, or colours taken from each song's cover,
- a clock.

Everything is set from the phone: **You > TV (Cast)** in Booma.

Google only lets a Cast device open a page you've registered, so this is a one-time setup (about 15 minutes,
and a one-time US$5 Google fee for the Cast developer account).

## 1. Put the page online (free, GitHub Pages)

1. On github.com, create a new **public** repository, for example `booma-tv`.
2. Upload `index.html` from this folder to it (Add file > Upload files > Commit).
3. In the repository: **Settings > Pages**. Under "Build and deployment", pick **Deploy from a branch**,
   branch **main**, folder **/ (root)**, then **Save**.
4. After a minute the page is at `https://YOUR-USERNAME.github.io/booma-tv/`. Opening it in a browser
   shows a blank or dark page; that's normal (it only comes alive on a Cast device).

## 2. Register it with Google Cast

1. Go to <https://cast.google.com/publish> and sign in with your Google account.
2. Accept the terms and pay the one-time US$5 registration fee.
3. **Add new application > Custom Receiver**.
   - Name: `Booma TV`
   - Receiver Application URL: your page address from step 1 (with the `https://` and the final `/`).
4. Save. Copy the **Application ID** shown in the list (8 letters and numbers, like `A1B2C3D4`).

New receivers are "Unpublished" and only work on devices you register:

5. **Add new device**: type the serial number of your Chromecast / Google TV / Android TV
   (Settings > System > About on the TV, or the label on the device), and a description. Save.
6. Wait about 15 minutes, then restart the TV device (unplug it for a few seconds).

If you'd rather not register devices, press **Publish** on the application instead; then it works on any
Cast device.

## 3. Turn it on in Booma

1. In Booma: **You > TV (Cast) > Booma TV app ID**, paste the Application ID and save.
2. Close Booma from recent apps and open it again (Cast reads the ID when Booma starts).
3. Play a song, tap the Cast button in Now Playing and pick your TV.

To go back to Google's standard player, clear the app ID and restart Booma.

## Updating the TV screen

Upload a new `index.html` to the same repository. The TV picks it up the next time you start casting.
