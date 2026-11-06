# YAiPlayer Benutzerhandbuch

Projektlizenz: GNU General Public License v3.0 (GPL-3.0).

---

## 1. Über YAiPlayer

**YAiPlayer** ist ein moderner Mediaplayer für IPTV-Wiedergabe auf Android TV und Android-Mobilgeräten.

> [!IMPORTANT]
> **YAiPlayer enthält weder Sender noch Filme, Serien, Playlists oder Abonnements.**
> Die Anwendung dient ausschließlich als Wiedergabe-Client. Für die Nutzung benötigen Sie eine eigene, legale Quelle: eine M3U-Playlist-URL, Xtream-Codes-Zugangsdaten oder ein Stalker/Ministra-Portal Ihres jeweiligen Anbieters.

---

## 2. Installation & App-Updates

### Installation
1. Rufen Sie die GitHub-Releases-Seite des Projekts auf (`https://github.com/eai-apps/YAiPlayer/releases`).
2. Laden Sie die aktuelle `.apk`-Datei für Ihr Gerät herunter (Android TV oder Mobiltelefon).
3. Erlauben Sie bei Bedarf in den Android-Sicherheitseinstellungen die Installation von Apps aus unbekannten Quellen.
4. Öffnen Sie die heruntergeladene Datei und bestätigen Sie die Installation.

### Updates innerhalb der App prüfen
Die App verfügt über eine integrierte Update-Funktion:
- Öffnen Sie **Einstellungen** (oder das Zahnradsymbol im Playlist-Menü).
- Wählen Sie den Menüpunkt **Über**.
- Klicken Sie auf **Auf Updates prüfen**.
- Steht eine neue Version bereit, bietet die App den Download und die Aktualisierung an.

---

## 3. Quelle hinzufügen

Um Inhalte abzuspielen, fügen Sie mindestens eine Quelle hinzu:

### M3U-Playlist oder Datei
- Wählen Sie **Quelle hinzufügen** und danach **M3U-Playlist oder Datei**.
- Geben Sie die URL Ihrer Playlist ein (z. B. `http://example.com/playlist.m3u8`) oder wählen Sie eine lokale Datei auf dem Gerät aus.
- Optional kann eine separate XMLTV-EPG-Programmführer-Adresse hinterlegt werden.

### Xtream Codes API
- Wählen Sie **Xtream Codes**.
- Füllen Sie die Verbindungsdaten aus:
  - **Serveradresse**: URL des Anbieters (inklusive Portnummer, falls erforderlich).
  - **Benutzername**.
  - **Passwort**.
- Bestätigen Sie mit „Verbinden“ — die App synchronisiert Live-TV-Kategorien, Filme (VOD) und Serien.

### Stalker / Ministra Portal
- Wählen Sie **Stalker (MAC)**.
- Tragen Sie die erforderlichen Angaben ein:
  - **Portaladresse**: URL des Stalker-Portals (häufig mit Endung `/c/`).
  - **MAC-Adresse**: Format `00:1A:79:XX:XX:XX`.
  - **Benutzername und Passwort**: Nur erforderlich, wenn Ihr Anbieter zusätzlich zur MAC-Adresse eine Benutzeranmeldung verlangt.

> [!NOTE]
> **Woher stammt die MAC-Adresse?**
> Die MAC-Adresse für ein Stalker-Portal wird von Ihrem Anbieter bei der Freischaltung zugewiesen oder in dessen Kundenbereich eingetragen. YAiPlayer ermöglicht die Eingabe einer zugewiesenen MAC-Adresse oder die Generierung einer kompatiblen virtuellen Kennung.

---

## 4. Live-TV & Programmführer (EPG)

Im Bereich **Live-TV** stehen zwei Ansichten zur Verfügung:
- **Senderliste**: Mehrspaltige Übersicht mit Kategorien auf der linken Seite und Vorschaufenster für den ausgewählten Sender.
- **Programmführer (EPG)**: Zeitachsen-Gitter mit dem aktuellen und kommenden Sendeprogramm.

### Ansicht wechseln und anpassen
- Nutzen Sie die Schaltfläche **Senderliste / Programmführer** in der oberen Leiste, um zwischen den Darstellungen zu wechseln.
- Unter **Einstellungen → Live-TV** können Sie festlegen, welches Layout beim Start von Live-TV standardmäßig geöffnet werden soll.
- Passen Sie Spaltenbreiten (Kategorien, Senderliste, Vorschau) und Zeitverschiebungen für das EPG an Ihre Zeitzone an.

---

## 5. Startseite & Zeilen anpassen

Die Startseite bündelt wichtige Inhalte für den schnellen Zugriff:
- **Senderkategorien**: Bis zu 8 Kategoriereihen mit jeweils bis zu 20 Sendern direkt auf der Startseite.
- **Alle Sender**: Direkter Zugriff auf die ersten 30 Sender Ihrer aktiven Playlists.
- **Kürzlich hinzugefügte Filme**: Neue VOD-Titel aus Ihren Quellen (bis zu 30 Filme).
- **Zuletzt gesehen & Favoriten**: Sender und Sendungen, die Sie regelmäßig abrufen.
- **Weiterschauen**: Schneller Wiedereinstieg für angefangene Filme und Serienepisoden.

### Startseite konfigurieren
1. Öffnen Sie **Einstellungen → Startseite**.
2. Blenden Sie Zeilen nach Bedarf ein oder aus.
3. Passen Sie die Reihenfolge der Zeilen an, um bevorzugte Kategorien weiter oben zu platzieren.

---

## 6. Fehlerbehebung

### Sender werden nicht geladen oder Synchronisierung schlägt fehl:
1. Prüfen Sie Internetverbindung sowie Richtigkeit von Serveradresse, Benutzername und Passwort.
2. Öffnen Sie **Einstellungen → Playlists**, wählen Sie die betroffene Quelle und starten Sie **Neu synchronisieren**.

### Stalker-Portal verweigert die Verbindung:
1. Vergewissern Sie sich, dass die in der App angegebene MAC-Adresse bei Ihrem Anbieter freigeschaltet ist.
2. Wählen Sie im Menü der Quelle **Stalker-Daten löschen (Sitzung und Senderliste)** und verbinden Sie sich erneut, um abgelaufene Sitzungstoken zu erneuern.
3. Falls Ihr Anbieter bestimmte Geräteprüfungen voraussetzt, überprüfen Sie in den erweiterten Stalker-Einstellungen das User-Agent-Profil (z. B. MAG250).

---

## 7. Datenschutz & Sicherheit

- **Direkte Verbindung**: YAiPlayer kommuniziert ausschließlich direkt mit Ihren konfigurierten IPTV- und EPG-Servern.
- **Keine Telemetrie von Drittanbietern**: Die App erhebt keine personenbezogenen Daten, enthält keine Werbemodule und überträgt keine Diagnosedaten an Analysedienste.
- **Lokale Speicherung**: Zugangsdaten, Token und Favoriten verbleiben verschlüsselt im geschützten Speicherbereich Ihres Geräts.
- **Sicherungen**: Wenn Sie Einstellungen und Playlists sichern, nutzen Sie vor der Weitergabe der Datei die integrierte Passwortverschlüsselung.
- <!-- TODO(Claude): Bestätigen, ob externe TMDB-Metadatenabfragen ohne explizite Aktivierung in den Einstellungen erfolgen -->
