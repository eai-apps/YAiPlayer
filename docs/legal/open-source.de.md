<!-- ENTWURF. Keine Rechtsberatung; vor Veröffentlichung von einem Fachanwalt prüfen lassen. -->

# Open-Source-Hinweise und Lizenzen

**YAiPlayer** basiert auf freier und quelloffener Software (Open Source). Wir bedanken uns bei den Entwicklern und der Open-Source-Community für die Bereitstellung dieser Komponenten.

---

## Lizenz der YAiPlayer-Anwendung

- **Projekt**: YAiPlayer
- **Repository**: [https://github.com/eai-apps/YAiPlayer](https://github.com/eai-apps/YAiPlayer)
- **Lizenz**: [GNU General Public License v3.0 (GPL-3.0)](https://www.gnu.org/licenses/gpl-3.0.html)

Sie haben das Recht, die Anwendung unter den Bedingungen der GPL-3.0 auszuführen, zu studieren, zu verändern und weiterzugeben.

---

## Ursprüngliches Projekt (Upstream)

YAiPlayer basiert auf dem quelloffenen Projekt **OwnTV**:
- **Ursprungsprojekt**: OwnTV (`tv.own.owntv`)
- **Lizenz des Ursprungsprojekts**: GNU General Public License v3.0 (GPL-3.0)
- <!-- TODO(Claude): Offiziellen Upstream-Link zum OwnTV-Repository vor der Veröffentlichung nachtragen -->

---

## Verwendete Open-Source-Bibliotheken

Die nachfolgende Übersicht führt die wesentlichen Drittanbieter-Bibliotheken und deren Lizenzen auf:

| Komponente / Bibliothek | Verwendungszweck | Lizenz |
| :--- | :--- | :--- |
| **AndroidX / Jetpack** (Core, Room, DataStore, WorkManager, Paging, SQLite) | Android-Architekturkomponenten und Datenbank | Apache License 2.0 |
| **Jetpack Compose Runtime** | Zustandsverwaltung der Benutzeroberfläche | Apache License 2.0 |
| **KotlinX Coroutines** (`kotlinx-coroutines`) | Asynchrone Programmierung in Kotlin | Apache License 2.0 |
| **Media3 / ExoPlayer** (`androidx.media3`) | Medienwiedergabe für adaptive Streams (HLS, DASH) | Apache License 2.0 |
| **libmpv** (OwnTV mpv / FFmpeg Build) | Nativer Wiedergabekern für Audio-/Videodekodierung | GPLv3 / LGPLv2.1+ |
| **OkHttp 5** (`com.squareup.okhttp3:okhttp`) | HTTP/HTTPS-Netzwerk-Client | Apache License 2.0 |
| **Koin** (`io.insert-koin:koin-android`) | Dependency Injection | Apache License 2.0 |
| **ZXing Core** (`com.google.zxing:core`) | QR-Code-Erzeugung für den Verbindungsaufbau | Apache License 2.0 |
| **juniversalchardet** | Zeichensatzerkennung lokaler Untertiteldateien | MPL 1.1 / LGPL 2.1 / GPL 2.0 |
| **org.json** | JSON-Verarbeitung in Tests und Hilfsklassen | The JSON License / Apache 2.0 <!-- TODO(Claude): Lizenzkompatibilität der JSON-Lizenz für Binärdistribution prüfen --> |
| **JUnit 4** | Test-Framework für automatisierte Tests | Eclipse Public License 1.0 (EPL-1.0) |

---

## Anfragen zum Quellcode
Die Lizenztexte und Quelltexte der Drittanbieter-Komponenten sind in den Repositorien der jeweiligen Autoren abrufbar. Für Lizenzanfragen kontaktieren Sie bitte: `{{E_MAIL}}`.
