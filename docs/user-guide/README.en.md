# YAiPlayer User Guide

Project License: GNU General Public License v3.0 (GPL-3.0).

---

## 1. About YAiPlayer

**YAiPlayer** is a media player engineered for IPTV streaming on Android TV and Android mobile devices.

> [!IMPORTANT]
> **YAiPlayer does not provide, host, or bundle any TV channels, movies, playlists, or streaming content.**
> The application functions solely as a playback client. To stream content, you must supply your own legitimate playlist URL (M3U), Xtream Codes server credentials, or Stalker/Ministra portal access details provided by your IPTV service provider.

---

## 2. Installation & In-App Updates

### Installation
1. Visit the project's official GitHub Releases page (`https://github.com/eai-apps/YAiPlayer/releases`).
2. Download the latest `.apk` package corresponding to your target platform (Android TV or mobile device).
3. If prompted by Android, grant permission to install applications from unknown sources.
4. Launch the downloaded package and confirm installation.

### Checking for Updates
The application includes a built-in update verification system:
- Open **Settings** (or tap the gear icon in the Playlists menu).
- Navigate to **About**.
- Select **Check for updates**.
- When a new version is detected, the app prompts you to download and install the update.

---

## 3. Adding a Content Source

To begin streaming, configure at least one content source:

### M3U Playlist URL or File
- Select **Add source** and choose **M3U playlist or file**.
- Enter your playlist URL (e.g., `http://example.com/playlist.m3u8`) or browse for a local file on your device.
- Optionally specify a secondary XMLTV EPG guide URL.

### Xtream Codes API
- Select **Xtream Codes**.
- Complete the authentication fields:
  - **Server URL**: Provider address (including port number if required).
  - **Username**.
  - **Password**.
- Select "Connect" — the player will synchronize live channel categories, movies (VOD), and series.

### Stalker / Ministra Portal
- Select **Stalker (MAC)**.
- Complete the configuration:
  - **Portal URL**: Web address of the Stalker middleware (commonly ending with `/c/`).
  - **MAC address**: Standard hexadecimal identifier format (`00:1A:79:XX:XX:XX`).
  - **Username and Password**: Optional fields, required only when your provider enforces dual credentials above MAC authentication.

> [!NOTE]
> **Obtaining your MAC Address**
> Your Stalker MAC address is provisioned by your IPTV provider upon subscription activation or configured in your provider's customer billing portal. YAiPlayer allows you to enter an assigned MAC or generate a compatible virtual device identifier.

---

## 4. Live TV & Electronic Program Guide (EPG)

The **Live TV** section provides two distinct viewing layouts:
- **Channel List**: A multi-column view displaying channel categories on the left alongside a live channel preview window.
- **TV Guide (EPG)**: A comprehensive chronological grid mapping program schedules across time slots.

### Switching Views & Preferences
- Tap or select the **Channels / TV Guide** toggle in the top action bar to switch layouts.
- Under **Settings → Live TV**, define default startup behavior, such as launching Live TV directly into the TV Guide grid.
- Adjust panel widths (category list / channel list / preview) and configure EPG time offset to match your local timezone.

---

## 5. Home Screen & Feed Customization

The Home screen highlights active playlists and frequently accessed media:
- **Channel Categories**: Up to 8 category rails with up to 20 channels each directly on Home.
- **All Channels**: Immediate access to the first 30 channels from your active playlists.
- **Recently Added Movies**: New movie catalogue arrivals (up to 30 titles).
- **On Now / Recent & Favorite Channels**: Live feeds you frequently watch.
- **Continue Watching**: Quick resume cards for movies and TV show episodes in progress.

### Reordering and Hiding Rows
1. Open **Settings → Home**.
2. Toggle row visibility to show or hide individual content rails.
3. Reorder rows up or down to position preferred categories at the top of the Home feed.

---

## 6. Troubleshooting

### Playlist synchronization errors or missing channels:
1. Verify network connectivity and ensure the provider server URL, username, and password are correct.
2. Navigate to **Settings → Playlists**, select the source, and choose **Re-sync**.

### Stalker portal access or playback refusal:
1. Ensure the provider has authorized the exact MAC address configured in the app.
2. In the playlist menu, choose **Clear Stalker data (session and channel list)** and reconnect to refresh expired session tokens.
3. If your provider enforces hardware device checks, open advanced Stalker parameters in the source settings and verify the User-Agent / device model preset (e.g., MAG250).

---

## 7. Privacy & Security

- **Direct Connections**: YAiPlayer establishes direct network connections exclusively to your configured IPTV and EPG providers.
- **No Third-Party Telemetry**: The application does not collect personal data, contains no advertising frameworks, and transmits no analytics or diagnostic tracking logs.
- **Local Credential Storage**: Passwords, authentication tokens, and user customization preferences remain stored locally on your device within sandboxed application storage.
- **Configuration Backups**: When exporting settings backups containing credentials, utilize backup encryption options before moving files off the device.
- <!-- TODO(Claude): confirm whether external TMDB metadata requests occur when TMDB is not explicitly enabled in settings -->
