# Radio Golha: plan (2026-11-10)

Follows from [IDEA.md](IDEA.md). Each step ends with something that runs on the Pixel 7 and a plain check that it works.
Steps 0 to 2 don't depend on the open questions in IDEA.md; step 3 onward does.

## 0. Toolchain (done)
- Installed on the Mac with Homebrew (`Brewfile`): Java 17, Android command-line tools, `adb`. The Android SDK
  (platform 35, build-tools 35.0.0) lives in `~/Library/Android/sdk`. `make setup` installs it all,
  `make uninstall-tools` removes it.
- Gradle project (Kotlin DSL, Gradle wrapper) with one `app` module: Compose + Media3 (`media3-exoplayer`,
  `media3-session`). `minSdk 33`, `targetSdk 35`.
- `make build` builds the debug APK; `make install` / `make run` put it on the phone.
- The phone connects over Wi-Fi, not USB (its USB doesn't work). Paired once on 2026-11-10. Afterwards: turn on
  Developer options → Wireless debugging on the phone, same Wi-Fi as the Mac; adb usually finds it by itself
  (`adb devices`), otherwise `adb mdns services` shows the address for `adb connect`.
- `.gitignore`: build output, `local.properties`, and all audio.
- Docker was tried and dropped: Google's Linux build tools are x86-only, so on Apple Silicon they ran emulated
  (slow) and the build ran out of Docker's memory.

**Done when:** the "Hello" app installs and opens on the Pixel. (Done 2026-11-10, over Wi-Fi.)

## 1. Play random songs with the screen off
- Songs live in one folder on the phone, `Music/Golha/`, copied over with `adb push`.
- Read them through MediaStore (filter on `RELATIVE_PATH`), asking for `READ_MEDIA_AUDIO`.
- A `MediaSessionService` owns the ExoPlayer, so playback, the notification and lock-screen controls come for free.
  Manifest: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`.
- One screen: a play/stop button and the current title. Each play picks a random song; when it ends, another.

**Done when:** press play, lock the phone, and songs keep going one after another; lock-screen and Bluetooth
headphone buttons pause and resume.
(2026-11-10: playback with the screen off and moving on to the next song checked on the Pixel. Still to try by hand:
lock-screen and Bluetooth buttons. The very first press of Play paused itself after half a second, cause unknown;
not seen again.)
Built as `SongLibrary.kt` (finds songs), `PlaybackService.kt` (player, keeps going in the background) and
`MainActivity.kt` (screen). The songs already play in random order with no repeats per round (shuffle + loop),
which covers part of step 2.

## 2. Make it a radio
- Shuffled deck: shuffle all songs, play in order, reshuffle when the deck runs out (don't start the new deck with the
  song that just played). Save the deck and position so a restart carries on instead of starting over.
- A "next" button (also on the lock screen and headphones).

**Done when:** with a small test folder of 5 songs, no song repeats until all 5 have played, including across an app
restart.
(2026-11-10: built. The running order lives in `RadioQueue.kt`, tested by `make test` (6 tests: every song once per
round, no song twice in a row at a round boundary, restore keeps the order and drops deleted songs). The playback
service saves "current song + the rest of the round" and the position in the song when paused, when a song changes
and when the app closes. Songs are recognised by folder + file name, so re-copying them doesn't lose the order.
Checked on the Pixel the same day: Play resumed the saved song at the saved second; after stopping, force-closing
and reopening, Play carried on at the exact spot; Next went through the saved order, each song once, and the new
round didn't start with the last song. Step 2 is done.
Known gap: if Android kills the app while it's playing, it resumes at the start of the song, not the exact second.)

## 3. Stations
- Read tags (artist/singer, album/program, genre or a custom tag for dastgah) into a small local index.
- A station is a saved filter; the deck is dealt from the songs that match. A "All songs" station is the default.
- If files aren't tagged, decide between tagging them on the Mac first or reading the info from folder/file names.

**Done when:** picking a singer's station plays only that singer, still without repeats.
(2026-11-10: built, using tags. Singer = artist, dastgah = genre, program = album; Android's placeholders for
missing tags ("<unknown>" artist, folder name as album) count as missing, and untagged songs only play on
"All songs". `Station.kt` builds the station list from the tags (tested by `make test`, 4 more tests);
the playback service deals rounds only from the station's songs and remembers the station; picking another station
starts a fresh round there. The screen lists stations under the Play button, grouped as Singers / Dastgah / Programs.
`make test-songs` now makes 8 tagged FLAC test songs (3 made-up singers, 2 made-up programs, 5 dastgahs); Android
reads all their tags (checked with `content query`). Checked on the Pixel the same day: "Test Singer A" played only
its 3 songs, each once per round; "Shur" played only its 2 songs (two singers), alternating; after force-closing
and reopening, the app was still on Shur and carried on there. Step 3 is done for the test songs. Once real files are
on hand, check which tags they actually use and adjust the mapping in `SongLibrary.kt`.)

### 3b. Open recordings, tried and removed (2026-11-10 to 11)
- For a day the app also streamed 12 openly licensed recordings from Wikimedia Commons and 4 Columbia 1928 sides
  from the Internet Archive. Removed on 2026-11-11 once the Golha programmes were in (3c), to keep one source.
  Wikimedia had also started answering this home connection with HTTP 429. The list and credits are in git history
  (commit b56dbec).

### 3c. The Golha programmes, from golhaprogram.com (2026-11-11)
- golhaprogram.com is a community archive (open source on GitHub) that offers each programme as an MP3 to play and
  download; its robots.txt allows everything but /admin/. Its programme list (front matter of
  `content/programs/*/*.md`: singers, dastgah) becomes `assets/golhaprogram.json` via
  `scripts/make_golha_streams.py`, with names in Persian exactly as the site gives them (only the Arabic ي/ك are
  changed to the Persian ی/ک).
- 346 Golha-ye Rangarang programmes stream (checked one by one with a header-only request, 0.2 s apart). Left out:
  8 Rangarang and all 4 Golha-ye Tazeh programmes, listed on the site but missing on its audio server. The other
  series have no programmes in the site's list yet; rerun the script when they appear.
- golhaprogram.com is now the only online source; files in `Music/Golha/` on the phone still play alongside.
- Tags are now lists: most programmes have 2 singers, so a programme plays on each singer's station
  (`make test`: 11 tests).
- Checked on the Pixel: the "Banan" station streamed Golha-ye Rangarang 215, then 103 after Next. Still to check:
  the "Golha-ye Rangarang" program station.

### 3d. Screen redesign, all in Persian (2026-11-11)
- One screen, right to left, all text in Persian with Persian digits: a card with what's on air (a red "روی آنتن"
  light, programme, singers, dastgah, and a link to its page on golhaprogram.com), a round play/stop button with
  next beside it, and two dropdowns ("پخش بر اساس": all / singer / dastgah / program, then which one, each with
  its number of programmes). Stations are in Persian alphabetical order (`PersianOrder`; plain sorting puts پ چ ژ گ
  last). Reading files on the phone is optional now, since the programmes stream.
- The app's name is "رادیو گلها". A station saved by an older version under a Latin name falls back to "all".
- Checked on the Pixel: the English version of this screen (dropdown with counts,
  stop/play resuming the same programme) and the Persian one (layout, digits, next mirrored to point left).
- Buttons: shuffle tunes to a random singer or dastgah station (one with at least 5 programmes, not the current
  one); next plays a random programme of the current station not yet heard this round. Both point left. Icons are
  Media3's own (`media3_icon_play/stop/next/shuffle_on`, Material Symbols, Apache 2.0), the same as on the lock
  screen. `make test`: 14 tests. Checked on the Pixel: shuffle tuned to "مرضیه" and played. Still to check: the
  Media3 icons on screen.

## 4. Nice to have
- Fade between songs (two players or Media3 volume ramp).
- Car: Android Auto needs a `MediaLibraryService` with a browsable station list.
- Spoken intro before a song ("Next, Banan in Dastgah-e Mahur"), recorded or text-to-speech.

## 5. Later, behind decisions
- Streaming from the Golha Project's own archive (golha.co.uk): only after its rights and terms are checked (open question 1).
  Checked 2026-11-10: the site's FAQ says "The Golha Project website does not offer a download service", the footer
  says "All rights reserved", and there are no terms allowing reuse. So: no scraping or downloading from the site.
  Legitimate routes: buy the programmes on CD/DVD (7nava.com, SOAS Bookshop) and put our own copies on the phone, or
  ask the project (Jane Lewisohn, SOAS; contact on golha.co.uk/en/about) for permission. Their FAQ says they hoped to
  make an app themselves.
- Play Store: signing key, privacy policy, store listing (open question 3). Until then, debug builds over adb.
