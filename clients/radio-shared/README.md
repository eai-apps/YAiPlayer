# radio-shared

Radio code shared by the TV and phone apps, compiled ONLY into the `full` flavor (APK, RuStore):
`app/build.gradle.kts` adds `../../radio-shared/src/main/java` to the `full` source set. The Google Play
flavor (`play`) never sees these files. Modelled on eai-apps/YAiPlay-Radio (genres, Radio Browser search,
curated collections from `https://yaiplay-radio.pages.dev/catalog.json`).
