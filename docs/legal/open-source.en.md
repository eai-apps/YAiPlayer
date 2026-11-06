<!-- DRAFT / CHERNOVIK. Not legal advice; review with legal counsel before publication. -->

# Open Source Notices & Licenses

**YAiPlayer** is built with and supported by free and open-source software (FOSS). We acknowledge and thank the open-source community for their contributions.

---

## YAiPlayer Application License

- **Project**: YAiPlayer
- **Repository**: [https://github.com/eai-apps/YAiPlayer](https://github.com/eai-apps/YAiPlayer)
- **License**: [GNU General Public License v3.0 (GPL-3.0)](https://www.gnu.org/licenses/gpl-3.0.html)

You are free to run, study, modify, and redistribute the source code pursuant to the terms of the GNU General Public License version 3.0.

---

## Upstream Project Attribution

YAiPlayer is derived from and builds upon the open-source **OwnTV** codebase:
- **Upstream Project**: OwnTV (`tv.own.owntv`)
- **Upstream License**: GNU General Public License v3.0 (GPL-3.0)
- <!-- TODO(Claude): specify authoritative upstream repository URL for OwnTV prior to publishing -->

---

## Third-Party Libraries and Dependencies

The following list identifies the core external open-source libraries integrated into the YAiPlayer codebase along with their respective software licenses:

| Component / Library | Purpose | License |
| :--- | :--- | :--- |
| **AndroidX / Jetpack** (Core, Room, DataStore, WorkManager, Paging, SQLite) | Android architecture and persistent storage | Apache License 2.0 |
| **Jetpack Compose Runtime** | UI state management and runtime composition | Apache License 2.0 |
| **KotlinX Coroutines** (`kotlinx-coroutines`) | Asynchronous coroutines concurrency framework | Apache License 2.0 |
| **Media3 / ExoPlayer** (`androidx.media3`) | Playback engine for adaptive HLS/DASH streams | Apache License 2.0 |
| **libmpv** (OwnTV mpv / FFmpeg build) | Native media engine for hardware/software decoding | GPLv3 / LGPLv2.1+ |
| **OkHttp 5** (`com.squareup.okhttp3:okhttp`) | HTTP/HTTPS networking client | Apache License 2.0 |
| **Koin** (`io.insert-koin:koin-android`) | Dependency injection container | Apache License 2.0 |
| **ZXing Core** (`com.google.zxing:core`) | QR code generation for companion setup | Apache License 2.0 |
| **juniversalchardet** | Subtitle character encoding detection | MPL 1.1 / LGPL 2.1 / GPL 2.0 |
| **org.json** | JSON object parsing in JVM tests and configs | The JSON License / Apache 2.0 <!-- TODO(Claude): verify JSON License compatibility for binary distribution --> |
| **JUnit 4** | Automated testing framework | Eclipse Public License 1.0 (EPL-1.0) |

---

## Source Code Requests
License texts and corresponding upstream source code are available in their respective developer repositories. For license inquiries or source bundle inquiries, contact `{{EMAIL}}`.
