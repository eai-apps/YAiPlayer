# radio-shared

Radio code shared by the TV and phone apps. It is part of the normal (only) build: `app/build.gradle.kts`
adds `../../radio-shared/src/main/java` and `src/radio` to the main source set. A Google Play build later is
the same app with `-Powntv.playStore=true`: then `src/noradio` (a stub) is used instead and none of these
files are compiled. Modelled on eai-apps/YAiPlay-Radio (genres, Radio Browser search, curated collections from
`https://yaiplay-radio.pages.dev/catalog.json`, favourites synced through the site's account).
