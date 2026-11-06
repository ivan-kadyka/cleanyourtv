# Trending

A row of popular-film posters on the launcher's Home, between the top bar and the dock, with the focused film's
name, IMDb rating, year, genres and country above it. The data comes from
[kinopoiskapiunofficial.tech](https://kinopoiskapiunofficial.tech) (`TOP_POPULAR_MOVIES`), with your own free key.

The add-on does the network work; the launcher only reads what it publishes (`trending_app` in
`launcher/app/src/main/res/values/config.xml`), as with `game_day`. The list is fetched when Home asks for it and is
more than 6 hours old (30 minutes after a failure); there is no background service and nothing at boot. Up to 20 films;
posters are stored at 360 px tall in the app's private files.

Genres and countries come from Kinopoisk in Russian and are translated with a fixed table (`Translate.java`); the
title is Kinopoisk's English name, else the original title, else the Russian one.

## Set up

```bash
tools/docker-build.sh addons/trending
adb install -r addons/trending/app/build/outputs/apk/release/app-release.apk
adb shell content call --uri content://tv.cleanyour.trending.config --method set_key --arg <your key>
```

Without a key, Home shows five sample films with no posters. The Trending tile opens a status screen (films, last
update, last error) with a Refresh button.

## What OK on a poster opens

By default the app named as the fallback, HD AlfaBox (`com.hd.videoboxalfa`). A link template can be set instead;
`{kp}` is the Kinopoisk id, `{imdb}` the IMDb id, `{title}` the URL-encoded title:

```bash
adb shell content call --uri content://tv.cleanyour.trending.config --method set_link --arg 'app://film?id={kp}' --extra fallback:s:com.hd.videoboxalfa
adb shell content call --uri content://tv.cleanyour.trending.config --method set_link     # back to no link
adb shell content call --uri content://tv.cleanyour.trending.config --method status
```

The config provider requires `WRITE_SECURE_SETTINGS`, which only the ADB shell holds, so no app on the TV can change
the key or the link. Permissions: `INTERNET` only.
