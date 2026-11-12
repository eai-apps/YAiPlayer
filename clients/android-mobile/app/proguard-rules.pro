# OwnTV release keep rules (R8). Each rule guards a runtime lookup that R8 can't see
# statically — trim only with a full on-device regression (sync, both engines, backup).

# --- Crash reports: keep readable stack traces (mapping.txt still needed for names) ---
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- libmpv JNI: native code resolves MPVLib and its event callbacks by name ---
-keep class dev.jdtech.mpv.** { *; }

# --- WorkManager: workers are re-instantiated by FQCN string (KoinWorkerFactory matches
#     workerClassName against ::class.java.name, and WorkManager persists the name in its DB
#     across app updates) ---
-keep class * extends androidx.work.ListenableWorker

# --- Room: Room.getGeneratedImplementation() derives this exact class name and loads it
#     reflectively. Keep the generated database entry point in optimized release builds. ---
-keep class tv.own.owntv.core.database.OwnTVDatabase_Impl { *; }

# --- Persisted enum names: DataStore prefs, backup JSON ("t" = MediaType.name), and
#     per-setting modes (ZoomMode, ThemeMode, StartupMode, AnimationLevel, ResumeMode,
#     EpgAutoRefresh, PlaylistAutoRefresh, ...) round-trip through Enum.name/valueOf.
#     Renaming a constant would silently reset settings and break old backups. ---
-keep enum tv.own.owntv.** { *; }

# --- Cast: the SDK reads the OptionsProvider's class name out of the manifest and instantiates it
#     reflectively, so neither the name nor the no-argument constructor may be touched. ---
-keep class tv.own.owntv.mobile.cast.CastOptionsProvider { *; }

# --- Android 10 ART VerifyError guard (v3.0.53). R8 optimization merged/propagated argument
#     types inside a huge Compose function (52 params) so that ART 10's verifier rejected it
#     ("register has type ... but expected java.lang.String") and the TV app crashed on start.
#     Shrinking and obfuscation stay on; only the bytecode optimizer is off. ---
-dontoptimize
