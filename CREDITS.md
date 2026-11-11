# Credits

## Programmes

346 programmes of Golha-ye Rangarang, made by Iranian National Radio (Davud Pirnia's Golha series, 1956–1979),
streamed from [golhaprogram.com](https://golhaprogram.com), a community archive that offers each programme to play
and download on its own pages. The list is `app/src/main/assets/golhaprogram.json`, built from the site's open
source ([github.com/golhaprogram/golhaprogram-website](https://github.com/golhaprogram/golhaprogram-website)) by
`scripts/make_golha_streams.py`; each entry links to its page on the site, where singers, players, poets and
announcers are credited in full.

The site's own text and data are CC BY-SA 4.0. That licence is the site's; it doesn't say who holds the rights
to the recordings themselves, so the app only streams them for personal listening, the same way the site's own
player does, and saves nothing.

## Fonts and icon

The app's text is set in [Vazirmatn](https://github.com/rastikerdar/vazirmatn) by Saber Rastikerdar, SIL Open Font
License 1.1 (`licenses/Vazirmatn-OFL.txt`); the font files are in `app/src/main/res/font/`.

The app icon, a rose with radio-wave rings, is drawn by `scripts/make_icon.py`. The play, stop, next and shuffle icons
are Media3's (Apache 2.0).

## Test songs

Test songs made by `scripts/make_test_songs.py` are generated, with made-up singers and programs.
