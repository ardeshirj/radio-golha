# Radio Golha: the idea (2026-11-10)

An Android app for the Pixel 7 that plays random songs like a radio. Nothing is built yet.

## Proposed so far (not yet agreed)
- Kotlin, Jetpack Compose for the screens, and Media3 (ExoPlayer and MediaSessionService) for playback. That gives
  playback with the screen off, lock-screen controls, Bluetooth buttons and car controls.
- What makes it radio: songs dealt from a shuffled deck, so none repeats until all have played; one play/stop button
  and an optional "next"; stations as filters (by singer, mode/dastgah, program). Later, maybe a fade between songs
  and a spoken intro.
- Songs from files on the phone first (offline, no rights questions). Streaming from the Golha archive
  (golha.co.uk) only after checking its rights and terms.
- Built on the Mac with tools from Homebrew (see `Brewfile`), installed on the phone with `adb install`.
- Audio files are never committed.

## Open questions
1. Is "Golha" the Golha radio programs archive, or our own collection?
2. Are the audio files already on hand? How many, and are they tagged?
3. Only for us (adb), or the Play Store one day?

Next: a short PLAN.md in numbered steps. Step 1 would be one screen with a play button that plays random songs from
a folder on the Pixel with the screen off.

(2026-11-11: that plan, PLAN.md, was retired once the app was built. It's in the git history (commit c93c538);
what's still open is under "To do" in the README.)
