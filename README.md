# Tarang

A **Navidrome client for Android and macOS** written in **Kotlin Multiplatform** with **Compose Multiplatform**.

Tarang (Hindi for "wave") streams from Navidrome or any Subsonic compatible server, and is built around the things paid players usually lock away: hi-res FLAC, bit-perfect USB DAC output, a real parametric EQ that follows your headphones, offline downloads, smart playlists, and synced lyrics with pronunciation and translation for songs in other languages.

No paywall, no account, no tracking. Everything it stores lives on your device or in your own Navidrome account.

---

# Features

## Playback

* Streams the **original file** by default, FLAC and hi-res included, or transcodes on the server (Opus, MP3, AAC) with separate Wi-Fi and mobile data quality
* **Gapless** playback
* **Replay gain** (track, album or auto) with preamp and clipping protection
* Queue with drag to reorder, play next, add to queue, remove, save as playlist
* Shuffle, repeat, sleep timer (minutes or end of track), playback speed (Android)
* **Play queue sync**: the queue and position are saved to Navidrome, so you can pick up on another device
* Internet radio stations from Navidrome
* Signal path view showing exactly what happens to the audio (decoder, EQ, replay gain, mixer, output)

---

## Lyrics

* Synced lyrics from **Navidrome** (OpenSubsonic structured lyrics), falling back to **LRCLIB**
* Auto scrolling, tap a line to seek, adjustable text size
* **Pronunciation in English letters** under each line:
  * Japanese: proper romaji using a morphological analyser (kanji get their real readings, `君の名は` → `kimi no na wa`)
  * Hindi and Punjabi: casual Hinglish with schwa deletion (`दिल धड़कने लगा` → `dil dhadakne laga`)
  * Korean, Chinese, Russian, Arabic and others: ICU transliteration
* **Translation** under each line, turned on automatically when a song isn't in your language
* Hinglish lyrics (Hindi written in English letters) are detected, converted back to Devanagari and then translated
* Lyrics and translations are cached per song

Translation engines:

| Engine | Setup | Notes |
| --- | --- | --- |
| Google Translate | none | default, whole song sent at once for context |
| Gemini | free key from aistudio.google.com | best for lyrics, reads the whole song first |
| DeepL | free key (500k characters a month) | whole song passed as context |
| OpenAI compatible | base URL, model, optional key | Ollama, LM Studio, OpenRouter, Groq, ... |
| LibreTranslate | your server URL | self-hosted, weaker for Japanese and Korean |
| On device (Android) | none | Google ML Kit, works offline once a language is downloaded |

If the chosen engine fails, Tarang falls back to Google and then to on device.

---

## Equalizer

* **10 band graphic** and **fully parametric** modes (peak, low shelf, high shelf, low pass, high pass)
* Preamp, live frequency response curve
* 15 built in presets, save your own
* Import **AutoEq / Equalizer APO / Wavelet** `ParametricEQ.txt`
* Search the **AutoEq** headphone database from inside the app
* **Follows the output device**: each speaker, headphone and DAC remembers its own preset
* **Recognises headphones**: when new Bluetooth or wired headphones connect, Tarang looks them up in AutoEq and applies the correction automatically
* Runs inside the player's audio pipeline, so it works on every output regardless of phone vendor effects

---

## USB DAC (Android 14+)

* **Bit-perfect output**: Tarang configures Android's bit-perfect mixer for the connected DAC at the track's exact sample rate and encoding, so samples reach the DAC untouched (no resampling, no mixing, no system effects)
* Switches the mixer format per track (44.1k, 48k, 96k, 192k, 16 bit or float)
* EQ and replay gain are bypassed while it's active, since they would change the samples
* Pick the output device manually or let Android route

---

## Library

* Home: recently played, recently added, most played, favourites, discover, quick shuffle
* Albums (with sorting), artists, songs, playlists, genres, folders
* Album, artist and playlist pages with play, shuffle, download and favourite
* Artist pages show songs they're credited on even when the album is filed under another artist
* **Custom artist images**: pick from Deezer, TheAudioDB or Navidrome's own (Last.fm/Spotify), or upload your own
* Search across the library (and offline search over the local index)
* Favourites and 5 star ratings
* Instant mix (similar songs) and artist radio
* Playlist editing: create, rename, describe, make public, add and remove songs, delete

---

## Smart Playlists

Rule based playlists evaluated on a local index of the whole library.

* Fields: title, artist, album, album artist, genre, path, format, comment, year, rating, play count, duration, bitrate, sample rate, bit depth, BPM, track, disc, date added, last played, date favourited, favourite, lossless, hi-res
* Match all or any, sort by anything, optional limit
* Can be written to a **normal Navidrome playlist** so every other client sees it
* Comes with Recently added, Most played, Never played, Top rated, Hi-res lossless and Forgotten favourites

---

## Offline

* Download songs, albums, playlists or smart playlists, original or transcoded
* Up to 3 downloads at once, resumes interrupted ones, retry failed
* Streaming cache with a size limit (Android, desktop)
* Offline mode: the app browses and searches your downloads
* Plays made offline are scrobbled once you're back online

---

## Sync Through Navidrome

Navidrome has no API for client settings, so Tarang keeps them as JSON in the description of a private playlist called **Tarang settings**. That makes these follow your account to every device:

* EQ presets and per device assignments
* Smart playlist definitions
* Artist images picked from the web

---

## Scrobbling

* Now playing and plays sent to Navidrome, which forwards them to Last.fm and ListenBrainz when linked there
* Optional direct **ListenBrainz** token
* A play counts after half the song or 4 minutes, like Last.fm

---

## Android

* Media3 playback service with notification, lock screen and Bluetooth controls (with a favourite button)
* **Android Auto** browsing: shuffle, recent, new, smart mixes, playlists, favourites, downloads, voice search
* Home screen widget
* Material You wallpaper colours

---

## macOS

* Native window with a side queue on wide layouts
* Pure Java FLAC (up to 24 bit), MP3, WAV and AIFF decoding, other formats are transcoded by the server
* Same EQ, replay gain and gapless playback as Android
* Output device selection
* Keyboard: `⌘ Space` play/pause, `⌘ ←` / `⌘ →` previous/next, `Esc` back

---

## Look

* **Material 3 Expressive**: morphing play button, wavy progress, floating mini player, scalloped artist images, expressive motion
* Player colours come from the **album art**, the rest of the app from your **wallpaper** (Android 12+) or the album art
* Light, dark, system, pure black

---

## Updates

* Checks **GitHub releases** at startup (or from Settings)
* Android downloads the APK and hands it to the system installer
* macOS downloads the DMG, swaps the app and relaunches
* A **what's new** screen after each update

---

# Settings

| Section | Options |
| --- | --- |
| Server | multiple servers, plain password auth for LDAP, library index sync, ask Navidrome to rescan, offline mode |
| Streaming | Wi-Fi and mobile format and max bitrate, download format and bitrate, cache size |
| Audio | EQ, bit-perfect USB DAC (Android), replay gain mode and preamp, gapless |
| Lyrics | pronunciation, auto translate foreign songs, always translate, LRCLIB fallback, target language, translation engine and keys |
| Scrobbling | on/off, ListenBrainz token, pending plays |
| Sync | play queue sync, EQ / smart playlist / artist image sync, sync now |
| Appearance | theme, wallpaper colours, album art colours in the player or everywhere, pure black |
| Updates | auto check, check now, GitHub token for a private repo |
| Storage | clear lyrics and image cache |

Per song (long press or `⋮`): play next, add to queue, add to playlist, instant mix, favourite, download, go to album/artist, details (format, bitrate, replay gain, file path), **clear cached data** (lyrics, translations, cover art, audio).

---

# Install

Download the latest build from [Releases](https://github.com/West-Tiwan/Tarang/releases).

## Android (arm64)

1. Download `Tarang-<version>.apk` and open it
2. Allow installing from your browser or file manager when asked
3. Sign in with your server URL, username and password

## macOS (Apple Silicon)

1. Open `Tarang-<version>.dmg` and drag **Tarang** into Applications
2. The app isn't notarized, so the first time right click it and choose **Open**, or run `xattr -cr /Applications/Tarang.app`

After that both apps update themselves from Releases.

---

# Architecture

The project has **three modules**.

```
Tarang
│
├─ shared                     Kotlin Multiplatform library (Android + JVM desktop)
│   ├─ commonMain             everything platform free
│   │   - Subsonic API client and models
│   │   - library index, downloads, scrobbling, sync
│   │   - lyrics, transliteration, translation
│   │   - EQ DSP and presets, AutoEq
│   │   - smart playlist engine
│   │   - player contract + playback coordinator
│   │   - all Compose UI
│   ├─ jvmCommonMain          shared by Android and desktop (files, md5, Kuromoji)
│   ├─ androidMain            platform hooks (paths, network state, ICU, wallpaper colours)
│   └─ desktopMain            platform hooks (paths, ICU4J)
│
├─ androidApp
│   - Media3 playback service, Android Auto tree
│   - EQ audio processor, USB DAC bit-perfect routing
│   - ML Kit translation, widget, APK updater
│
└─ desktopApp
    - pure Java decoders + Java Sound output
    - DMG updater
```

Design principle:

```
shared      → all logic and UI, no platform APIs in commonMain
androidApp  → plays audio with Media3, talks to Android
desktopApp  → plays audio with its own engine, talks to macOS
```

The UI only ever talks to a `PlayerController`. Each platform implements it in its own way, and everything around playback (scrobbling, queue sync, EQ following the output, sleep timer) lives once in the shared `PlaybackCoordinator`.

## Object graph

`AppContainer` is created once per process and owns everything:

```
AppContainer
├─ HttpClient (Ktor + OkHttp)
├─ settings, eq, smart playlists, artist art      JsonStore files
├─ SubsonicApi                                    rebuilt when the active server changes
├─ LibrarySync                                    full song index for smart playlists and offline search
├─ DownloadManager
├─ Scrobbler
├─ LyricsService + LyricsProcessor + TranslationCache
├─ EqManager
├─ Updater
└─ PlayerController  ←  AndroidPlayer or DesktopPlayer
      └─ PlaybackCoordinator
```

## Playback on Android

```
Compose UI
   │  PlayerController calls
   ▼
AndroidPlayer ── MediaController ──▶ PlaybackService (MediaLibraryService)
                                        │
                                        ▼
                                     ExoPlayer
                                        │  source: downloaded file, or server stream through the disk cache
                                        ▼
                                     decoder
                                        │
                                        ▼
                                  EqAudioProcessor   (EQ + preamp + replay gain + soft clip)
                                        │
                                        ▼
                                  DefaultAudioSink ──▶ AudioTrack ──▶ Android mixer or bit-perfect mixer ──▶ output
```

`AudioOutputs` watches devices, works out the current route and sets `AudioManager.setPreferredMixerAttributes` with a bit-perfect mixer for USB DACs. Android Auto and other media browsers use the same service through `LibraryTree`.

## Playback on macOS

```
Compose UI ──▶ DesktopPlayer
                  │
                  ▼
             CachedTrack      progressive download into the cache, readable while it downloads
                  │
                  ▼
             PcmDecoder       jflac / JLayer / Java Sound → float samples
                  │
                  ▼
             EqEngine         same DSP as Android
                  │
                  ▼
             SourceDataLine   reused between tracks with the same format, so playback is gapless
```

## Lyrics pipeline

```
song ──▶ cache? ──▶ Navidrome structured lyrics ──▶ Navidrome legacy lyrics ──▶ LRCLIB get ──▶ LRCLIB search
                                      │
                                      ▼
                                 LyricsDoc (lines + timestamps)
                                      │
             ┌────────────────────────┼─────────────────────────┐
             ▼                        ▼                         ▼
       detectScript            pronunciation              translation
                         Kuromoji + Kana (ja)        FallbackTranslator
                         Hinglish (hi, pa)           chosen → Google → device
                         ICU (everything else)        cached per song
```

---

# Code Structure

```
shared/src/commonMain/kotlin/com/westtiwan/tarang/
├─ App.kt                    root composable, theme selection
├─ AppContainer.kt           object graph, settings, server switching, library actions
├─ PlaybackCoordinator.kt    scrobbling, queue sync, saved queue, EQ per device, sleep timer
├─ data/
│   ├─ Models.kt             Song, Album, Artist, Playlist, AppSettings, ...
│   ├─ SubsonicApi.kt        Subsonic + OpenSubsonic client
│   ├─ JsonStore.kt          StateFlow backed JSON file with debounced writes
│   └─ Library.kt            full library index sync
├─ player/Player.kt          PlayerController, PlayerState, AudioInfo
├─ lyrics/
│   ├─ Lyrics.kt             LRC parser, LyricsService, LibreTranslate, LyricsProcessor
│   ├─ Transliterator.kt     script detection, Hinglish, Japanese word grouping
│   ├─ Kana.kt               Hepburn romanization
│   └─ Translators.kt        Google, Gemini, DeepL, OpenAI compatible, fallback, cache
├─ eq/
│   ├─ Eq.kt                 models, presets, biquad filters, EqEngine, AutoEq parser
│   └─ EqManager.kt          presets, per device profiles, AutoEq lookup
├─ smart/SmartPlaylists.kt   rules, evaluation, date parsing
├─ scrobble/Scrobbler.kt     Navidrome + ListenBrainz, offline queue
├─ download/Downloads.kt     download queue and covers
├─ sync/SettingsSync.kt      settings stored in a private Navidrome playlist
├─ art/ArtistArt.kt          custom artist images and online search
├─ update/Updater.kt         GitHub releases check and download
├─ platform/Platform.kt      expect declarations for the few platform hooks
└─ ui/
    ├─ Theme.kt              Material 3 Expressive theme, album art colours
    ├─ Root.kt               navigation shell, mini player, update banner, what's new
    ├─ Nav.kt                tabs, screens, back stack
    ├─ Components.kt         covers, song rows, menus, dialogs
    ├─ CacheTools.kt         per song cache clearing
    ├─ Load.kt               loading state helper with an in memory cache
    ├─ Offline.kt            albums and artists built from downloads
    └─ screens/              Home, Library, Search, Details, NowPlaying, LyricsPanel,
                             QueuePanel, Equalizer, Smart, Downloads, Settings, Login,
                             ArtistImageDialog, Updates

shared/src/jvmCommonMain/    file IO, md5, Kuromoji readings
shared/src/androidMain/      Android paths, network, ICU, dynamic colour
shared/src/desktopMain/      macOS paths, ICU4J

androidApp/src/main/kotlin/com/westtiwan/tarang/
├─ TarangApp.kt              creates AppContainer and the player
├─ MainActivity.kt           Compose host, back handling, image picker
├─ AndroidPlatformUi.kt      bit-perfect settings row
├─ playback/
│   ├─ PlaybackService.kt    ExoPlayer, cache, session, Android Auto callbacks
│   ├─ AndroidPlayer.kt      PlayerController over MediaController
│   ├─ AudioOutputs.kt       routing and bit-perfect mixer
│   ├─ EqAudioProcessor.kt   EQ inside ExoPlayer's sink
│   ├─ LibraryTree.kt        browse tree
│   ├─ MediaItems.kt         Song ↔ MediaItem
│   └─ RoutingDataSource.kt  cache for streams, direct for files and radio
├─ translate/MlKitTranslator.kt
├─ update/ApkInstaller.kt
└─ widget/NowPlayingWidget.kt

desktopApp/src/main/kotlin/com/westtiwan/tarang/desktop/
├─ Main.kt                   window, shortcuts, image picker
├─ DesktopPlayer.kt          playback engine
├─ Decoders.kt               FLAC, MP3, WAV/AIFF decoders
├─ DmgInstaller.kt           in place update
└─ DevLog.kt                 dev only playback log and tag check
```

---

# How It Works

## Subsonic API

* Token auth (`md5(password + salt)`) with a **stable salt per server**, so cover and stream URLs stay identical between calls and image and audio caches actually hit
* Plain password mode for LDAP backed servers
* Detects OpenSubsonic extensions and uses form POST for large requests when the server supports it
* Every response is parsed from the `subsonic-response` envelope with lenient JSON, so newer server fields never break it

## Storage

There is no database. Settings, EQ, smart playlists, downloads, the library index, scrobble queue and saved queue are each a `JsonStore`: a `StateFlow` that the UI collects directly, written to disk atomically and debounced. Simple to read, easy to back up, and plenty fast for a music library.

## Library index

`LibrarySync` pages through `search3` with an empty query to pull every song once (refreshed every 6 hours). Smart playlists, the songs tab, artist credits and offline search all run on this index locally.

## Hinglish

Devanagari and Gurmukhi are split into syllables, then the inherent "a" is dropped the way people actually write Hinglish: at the end of a word, and between two pronounced vowels (scanning right to left). Long vowels shorten at word ends (`तेरा` → `tera`, `नहीं` → `nahi`). Gurmukhi is mapped onto Devanagari first since Unicode lays both blocks out in parallel.

## Japanese romaji

Kuromoji splits a line into morphemes with katakana readings. Morphemes are grouped back into words (auxiliary verbs, suffixes, `て`, and anything after a small `っ`), then each word is converted with Hepburn rules, with particles read as `wa`, `e`, `o`.

## EQ

`EqEngine` runs a chain of RBJ cookbook biquads on float samples, plus preamp, replay gain and a soft clipper above -0.5 dBFS. The same engine runs inside ExoPlayer (`EqAudioProcessor`) and inside the desktop player. `EqManager` remembers a preset per output device key and, for unknown headphones, fuzzy matches the device name against AutoEq's index and downloads the matching `ParametricEQ.txt`.

## Bit-perfect

On Android 14+ `getSupportedMixerAttributes` lists what a USB DAC can do. Tarang picks a bit-perfect mixer whose sample rate matches the track and whose encoding matches what ExoPlayer outputs (16 bit, or float for hi-res), and sets it as the preferred mixer for media on that device. The signal path view only says bit-perfect when that really happened.

## Settings sync

`SettingsSync` serialises EQ presets, device assignments, smart playlists and web artist images into the comment of a private playlist named `Tarang settings`. Each part carries its own timestamp, and the newer side wins when the app starts.

## Updates

`Updater` reads the GitHub releases API, picks the newest release with an asset for the platform (`.apk` or `.dmg`), downloads it and hands it to an `UpdateInstaller`. Android uses a `PackageInstaller` session. macOS writes a small script that waits for the app to quit, mounts the DMG, swaps the bundle and opens the new version.

---

# Building

## Requirements

* JDK 17+ (the Gradle daemon runs on JDK 21 and is downloaded automatically, so newer default JDKs work)
* Android SDK with platform 37
* macOS on Apple Silicon for the DMG

## Clone the repository

```
git clone https://github.com/West-Tiwan/Tarang
cd Tarang
```

## Run

```
./gradlew :desktopApp:run              # macOS app
./gradlew :androidApp:installDebug     # Android, with a phone connected
```

## Run the tests

```
./gradlew :shared:desktopTest
```

## Package a release

The version lives in `gradle.properties` (`tarang.version`) and is used by both apps.

```
./gradlew :androidApp:assembleRelease  # androidApp/build/outputs/apk/release
./gradlew :desktopApp:packageDmg       # desktopApp/build/compose/binaries/main/dmg
```

---

# Tech Stack

Language:

* Kotlin 2.4, Kotlin Multiplatform

UI:

* **Compose Multiplatform** with **Material 3 Expressive**
* **MaterialKolor** for colour schemes from album art
* **Coil 3** for images

Networking and data:

* **Ktor** (OkHttp engine), **kotlinx.serialization**, **kotlinx.coroutines**

Android:

* **Media3** (ExoPlayer, session, OkHttp data source), **ML Kit** translation and language id

Desktop:

* **jflac**, **JLayer**, Java Sound

Text:

* **Kuromoji** (Japanese), **ICU** / **ICU4J** (other scripts)

---

# Current Limitations

* Bit-perfect output needs Android 14+ and a DAC that offers a bit-perfect mixer
* macOS output goes through Core Audio's mixer, so it isn't guaranteed bit-perfect
* macOS has no playback speed control or media keys yet
* Android release builds are arm64 only and not shrunk yet
* No Chromecast or jukebox mode
* The macOS app isn't notarized

---

# Roadmap

* Chromecast and Navidrome jukebox mode
* Media keys and Now Playing integration on macOS
* Lyrics editing and uploading to Navidrome
* Crossfade
* iOS (the shared module already keeps platform code out of `commonMain`)
* Shrunk, smaller Android release builds

---

# Author

**Mohit Singh**
