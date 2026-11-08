package tv.own.owntv.mobile.ui.setup

import androidx.compose.foundation.layout.height
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import tv.own.owntv.mobile.ui.stage.StageRadii
import tv.own.owntv.mobile.ui.stage.stageClickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import tv.own.owntv.mobile.ui.stage.stageAccent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.setup.MAG_USER_AGENTS
import tv.own.owntv.core.stalker.StalkerClient
import tv.own.owntv.core.stalker.StalkerDeviceDerivation
import tv.own.owntv.core.setup.SyncProgressStats
import tv.own.owntv.core.sync.SyncScopeChoice
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.stage.StageTag
import tv.own.owntv.mobile.ui.stage.StageTagTone
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.unit.dp
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.screens.settings.PageNote
import tv.own.owntv.mobile.ui.screens.settings.SettingsChoice
import tv.own.owntv.mobile.ui.screens.settings.StageChoiceSheet
import tv.own.owntv.mobile.ui.stage.StageTextRow
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageSectionHead
import tv.own.owntv.mobile.ui.stage.StageSegmented
import tv.own.owntv.mobile.ui.stage.StageSettingRow
import tv.own.owntv.mobile.ui.stage.stageText
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.StageRowValue
import tv.own.owntv.mobile.ui.stage.StageChevron
import androidx.compose.ui.semantics.Role

/** Which kind of provider the form is asking about. */
enum class SourceKind { XTREAM, M3U, STALKER }

/**
 * Everything the form holds, so editing an existing playlist can fill it and take it back whole.
 * Only the edit path uses it; adding still calls the three start callbacks, one per kind.
 */
data class SourceFormValues(
    val kind: SourceKind,
    val name: String = "",
    val urlOrServer: String = "",
    val username: String = "",
    val password: String = "",
    val mac: String = "",
    val serialNumber: String = "",
    val deviceId: String = "",
    val deviceId2: String = "",
    val signature: String = "",
    val userAgent: String = "",
    val httpReferer: String = "",
    val autoRefresh: PlaylistRefresh = PlaylistRefresh(),
    val live: SyncScopeChoice = SyncScopeChoice.Now,
    val movies: SyncScopeChoice = SyncScopeChoice.Now,
    val series: SyncScopeChoice = SyncScopeChoice.Now,
    val preferHls: Boolean = false,
    val epgUrl: String = "",
    val epgUrls: List<String> = emptyList(),
)

/**
 * One playlist's details, as one scrolling page.
 *
 * The television asks the same questions across several popups because a remote control cannot
 * scroll a long form comfortably. A phone is the opposite: a popup would put the keyboard over the
 * field being typed into, so everything — including the optional half — is on this page, and the
 * page scrolls under the keyboard.
 *
 * Passing [initial] and [onSave] turns the same page into the edit form for a playlist that already
 * exists: the kind chips go away, because a playlist cannot change what kind of provider it is, and
 * a blank password means "keep the stored one" rather than "no password".
 */
@Composable
fun AddSourceForm(
    onStartXtream: (
        name: String, server: String, username: String, password: String, userAgent: String,
        httpReferer: String, autoRefresh: PlaylistRefresh, live: SyncScopeChoice, movies: SyncScopeChoice,
        series: SyncScopeChoice, preferHls: Boolean, epgUrl: String,
    ) -> Unit,
    onStartM3u: (name: String, url: String, userAgent: String, httpReferer: String, autoRefresh: PlaylistRefresh, epgUrl: String) -> Unit,
    onStartStalker: (
        name: String, portalUrl: String, mac: String, serialNumber: String, deviceId: String,
        deviceId2: String, signature: String, userAgent: String, httpReferer: String, autoRefresh: PlaylistRefresh,
        live: SyncScopeChoice, movies: SyncScopeChoice, series: SyncScopeChoice, login: String, password: String,
        loadChannelLogos: Boolean, useOnlyPortalEpg: Boolean,
    ) -> Unit,
    modifier: Modifier = Modifier,
    initial: SourceFormValues? = null,
    onSave: ((SourceFormValues) -> Unit)? = null,
    // Called with everything typed when the user starts an import, so a failed import can reopen the form filled.
    onSubmitted: ((SourceFormValues) -> Unit)? = null,
    syncStats: SyncProgressStats? = null,
    onOpenLive: (() -> Unit)? = null,
) {
    val editing = onSave != null
    val settings: tv.own.owntv.core.settings.SettingsRepository = org.koin.compose.koinInject()
    val sourceDao: tv.own.owntv.core.database.dao.SourceDao = org.koin.compose.koinInject()
    val isGuest by settings.isGuestActive.collectAsStateWithLifecycle(initialValue = false)
    val activeProfileId by settings.activeProfileId.collectAsStateWithLifecycle(initialValue = -1L)
    val sources by sourceDao.observeForProfile(activeProfileId).collectAsStateWithLifecycle(initialValue = emptyList())
    var showLimitSheet by remember { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: SourceKind.XTREAM) }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var server by rememberSaveable {
        mutableStateOf(if (initial?.kind == SourceKind.XTREAM) initial.urlOrServer else "")
    }
    var username by rememberSaveable { mutableStateOf(initial?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(initial?.password.orEmpty()) }
    var m3uUrl by rememberSaveable {
        mutableStateOf(if (initial?.kind == SourceKind.M3U) initial.urlOrServer else "")
    }
    var portalUrl by rememberSaveable {
        mutableStateOf(if (initial?.kind == SourceKind.STALKER) initial.urlOrServer else "")
    }
    var mac by rememberSaveable { mutableStateOf(initial?.mac.orEmpty()) }
    var serialNumber by rememberSaveable { mutableStateOf(initial?.serialNumber.orEmpty()) }
    var deviceId by rememberSaveable { mutableStateOf(initial?.deviceId.orEmpty()) }
    var deviceId2 by rememberSaveable { mutableStateOf(initial?.deviceId2.orEmpty()) }
    var signature by rememberSaveable { mutableStateOf(initial?.signature.orEmpty()) }
    var userAgent by rememberSaveable { mutableStateOf(initial?.userAgent.orEmpty()) }
    var httpReferer by rememberSaveable { mutableStateOf(initial?.httpReferer.orEmpty()) }
    var preferHls by rememberSaveable { mutableStateOf(initial?.preferHls ?: false) }
    var epgUrls by rememberSaveable {
        mutableStateOf(
            initial?.epgUrls?.takeIf { it.isNotEmpty() }
                ?: tv.own.owntv.core.repository.splitGuideUrls(initial?.epgUrl)
        )
    }
    var refreshMode by rememberSaveable { mutableStateOf(initial?.autoRefresh?.mode ?: PlaylistAutoRefresh.OFF) }
    var manualDays by rememberSaveable {
        mutableStateOf((initial?.autoRefresh ?: PlaylistRefresh()).manualDays.toString())
    }
    // Stalker has no bulk catalogue endpoint, so its films and series are fetched later by default.
    var syncLive by rememberSaveable { mutableStateOf(initial?.live ?: SyncScopeChoice.Now) }
    var syncMovies by rememberSaveable { mutableStateOf(initial?.movies ?: SyncScopeChoice.Now) }
    var syncSeries by rememberSaveable { mutableStateOf(initial?.series ?: SyncScopeChoice.Now) }
    var showRefreshSheet by remember { mutableStateOf(false) }
    var showDeviceSheet by remember { mutableStateOf(false) }
    var loadChannelLogos by rememberSaveable { mutableStateOf(true) }
    var useOnlyPortalEpg by rememberSaveable { mutableStateOf(true) }
    // "Protect adult channels with a PIN": handed to the import through AdultProtectChoice.
    remember { tv.own.owntv.core.setup.AdultProtectChoice.reset() }
    var protectAdult by rememberSaveable { mutableStateOf(false) }
    var protectPin by rememberSaveable { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(protectAdult, protectPin) {
        tv.own.owntv.core.setup.AdultProtectChoice.enabled = protectAdult
        tv.own.owntv.core.setup.AdultProtectChoice.pin = protectPin
    }

    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val pickPlaylist = rememberPlaylistFilePicker { uri ->
        scope.launch {
            copyPickedFile(context, uri, java.io.File(context.filesDir, PLAYLIST_DIR))?.let { file ->
                m3uUrl = file.absolutePath
                if (name.isBlank()) name = file.nameWithoutExtension
            }
        }
    }

    val macValid = StalkerClient.canonicalizeMac(mac) != null
    val autoRefresh = PlaylistRefresh(refreshMode, manualDays.toIntOrNull() ?: PlaylistRefresh().manualDays)
    val hasAnySection = syncLive != SyncScopeChoice.Off ||
        syncMovies != SyncScopeChoice.Off ||
        syncSeries != SyncScopeChoice.Off
    val hasInvalidEpg = (kind == SourceKind.XTREAM || kind == SourceKind.M3U) &&
        epgUrls.any { it.isNotBlank() && !tv.own.owntv.core.repository.isValidEpgUrl(it.trim()) }
    val canStart = !hasInvalidEpg && when (kind) {
        SourceKind.XTREAM ->
            // On an edit the password field starts empty and left empty keeps the stored one.
            server.isNotBlank() && username.isNotBlank() && (password.isNotBlank() || editing) && hasAnySection
        SourceKind.M3U -> m3uUrl.isNotBlank()
        SourceKind.STALKER -> StalkerClient.isValidPortalUrl(portalUrl) && (mac.isBlank() || macValid) && hasAnySection
    }
    fun values() = SourceFormValues(
        kind = kind,
        name = name,
        urlOrServer = when (kind) {
            SourceKind.XTREAM -> server
            SourceKind.M3U -> m3uUrl
            SourceKind.STALKER -> portalUrl
        },
        username = username,
        password = password,
        mac = mac,
        serialNumber = serialNumber,
        deviceId = deviceId,
        deviceId2 = deviceId2,
        signature = signature,
        userAgent = userAgent,
        httpReferer = httpReferer,
        autoRefresh = autoRefresh,
        live = syncLive,
        movies = syncMovies,
        series = syncSeries,
        preferHls = preferHls,
        epgUrl = tv.own.owntv.core.repository.joinGuideUrls(epgUrls),
        epgUrls = epgUrls.filter { it.isNotBlank() },
    )

    // The type as a segmented control, the fields as rows (a tap opens the field), OPTIONAL, Start import.
    val palette = LocalStagePalette.current
    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 100.dp),
    ) {
        if (!editing) {
            item(key = "kind") {
                // Stalker portals answer one item at a time, so a full film catalogue on the spot is an hour of
                // requests; the television makes the same choice for the same reason.
                StageSegmented(
                    options = listOf(stringResource(R.string.setup_xtream), stringResource(R.string.setup_m3u), stringResource(R.string.setup_stalker_mac)),
                    selected = kind.ordinal,
                    onSelect = { index ->
                        kind = SourceKind.entries[index]
                        if (kind == SourceKind.STALKER) {
                            syncMovies = SyncScopeChoice.Later
                            syncSeries = SyncScopeChoice.Later
                        }
                    },
                    fill = true,
                    modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 10.dp),
                )
            }
        }
        item(key = "name") {
            StageTextRow(MobileIcons.Edit, stringResource(R.string.setup_source_name_optional), name, { name = it }, placeholder = stringResource(R.string.setup_default_iptv))
        }
        when (kind) {
            SourceKind.XTREAM -> {
                item(key = "server") {
                    StageTextRow(
                        MobileIcons.Translate, stringResource(R.string.setup_server_url), server, { server = it },
                        placeholder = stringResource(R.string.setup_server_example), keyboardType = KeyboardType.Uri,
                    )
                }
                item(key = "user") { StageTextRow(MobileIcons.Person, stringResource(R.string.setup_username), username, { username = it }) }
                item(key = "pass") {
                    StageTextRow(
                        MobileIcons.Lock, stringResource(if (editing) R.string.setup_password_keep else R.string.setup_password),
                        password, { password = it }, password = true,
                    )
                }
                item(key = "hls") {
                    StageSettingRow(
                        icon = MobileIcons.LiveTv,
                        title = stringResource(R.string.setup_prefer_hls_live_tv),
                        subtitle = stringResource(R.string.setup_prefer_hls_description),
                        role = Role.Switch,
                        onClick = { preferHls = !preferHls },
                    ) { StageSwitch(preferHls) }
                }
            }
            SourceKind.M3U -> {
                item(key = "url") {
                    StageTextRow(
                        MobileIcons.Translate, stringResource(R.string.setup_playlist_url_local_file), m3uUrl, { m3uUrl = it },
                        placeholder = stringResource(R.string.setup_playlist_example), keyboardType = KeyboardType.Uri,
                    )
                }
                item(key = "file") {
                    StageSettingRow(
                        title = stringResource(R.string.setup_local_file_choose),
                        icon = MobileIcons.Folder,
                        subtitle = stringResource(R.string.setup_local_file_hint_mobile),
                        subtitleColor = palette.dim,
                        onClick = { pickPlaylist() },
                    ) { StageIcon(MobileIcons.KeyboardArrowRight, 14.dp, palette.muted) }
                }
                item(key = "m3u-protect-adult") {
                    StageSettingRow(
                        icon = MobileIcons.Lock,
                        title = stringResource(R.string.setup_checkbox_protect_adult),
                        role = Role.Switch,
                        onClick = { protectAdult = !protectAdult },
                    ) { StageSwitch(protectAdult) }
                }
                if (protectAdult) {
                    item(key = "m3u-protect-pin") {
                        StageTextRow(
                            MobileIcons.Lock, stringResource(R.string.setup_protect_pin), protectPin,
                            { protectPin = it.filter { c -> c.isDigit() }.take(8) },
                            password = true, keyboardType = KeyboardType.NumberPassword,
                        )
                    }
                }
            }
            SourceKind.STALKER -> {
                item(key = "portal") {
                    StageTextRow(
                        MobileIcons.Translate, stringResource(R.string.setup_portal_url), portalUrl, { portalUrl = it },
                        placeholder = stringResource(R.string.setup_portal_example), keyboardType = KeyboardType.Uri,
                        trailing = {
                            StageTag(stringResource(R.string.setup_badge_required).uppercase(), tone = StageTagTone.ACCENT)
                        },
                    )
                }
                item(key = "mac") {
                    StageTextRow(MobileIcons.Edit, stringResource(R.string.setup_mac_address), mac, { mac = it }, placeholder = stringResource(R.string.setup_mac_example))
                }
                item(key = "mac-generate") {
                    StageSettingRow(
                        icon = MobileIcons.Edit,
                        title = stringResource(R.string.setup_mac_generate),
                        onClick = { mac = StalkerDeviceDerivation.randomMac() },
                    ) { }
                }
                item(key = "mac-generate-reliable") {
                    StageSettingRow(
                        icon = MobileIcons.Edit,
                        title = stringResource(R.string.setup_mac_generate_reliable),
                        onClick = { mac = StalkerDeviceDerivation.reliableMac() },
                        trailing = {
                            StageTag(stringResource(R.string.setup_badge_required).uppercase(), tone = StageTagTone.ACCENT)
                        },
                    )
                }
                if (mac.isNotBlank()) {
                    item(key = "mac-copy") {
                        StageSettingRow(
                            icon = MobileIcons.Edit,
                            title = stringResource(R.string.setup_mac_copy),
                            onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(mac)) },
                        ) { }
                    }
                }
                item(key = "mac-note") { PageNote(stringResource(R.string.setup_mac_optional_note)) }
                if (mac.isNotBlank() && !macValid) {
                    item(key = "mac-bad") {
                        Text(stringResource(R.string.setup_mac_invalid), style = stageText(11.5f, 600), color = palette.danger, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
                // Username & Password
                item(key = "portal-user") {
                    StageTextRow(
                        MobileIcons.Person, stringResource(R.string.setup_username), username, { username = it },
                        trailing = {
                            StageTag(stringResource(R.string.setup_badge_required).uppercase(), tone = StageTagTone.ACCENT)
                        },
                    )
                }
                item(key = "portal-pass") {
                    StageTextRow(
                        MobileIcons.Lock, stringResource(if (editing) R.string.setup_password_keep else R.string.setup_password),
                        password, { password = it }, password = true,
                        trailing = {
                            StageTag(stringResource(R.string.setup_badge_required).uppercase(), tone = StageTagTone.ACCENT)
                        },
                    )
                }
                item(key = "identity") { StageSectionHead(stringResource(R.string.setup_stalker_advanced_identity)) }
                item(key = "fill-identity") {
                    StageSettingRow(
                        icon = MobileIcons.Phone,
                        title = stringResource(R.string.setup_stalker_identity_fill),
                        onClick = {
                            val canon = StalkerClient.canonicalizeMac(mac)
                                ?: StalkerDeviceDerivation.randomMac().also { mac = it }
                            val id = StalkerDeviceDerivation.identityFor(canon)
                            serialNumber = id.serialNumber.orEmpty()
                            deviceId = id.deviceId.orEmpty()
                            deviceId2 = id.deviceId2.orEmpty()
                        },
                        trailing = {
                            StageTag(stringResource(R.string.setup_badge_required).uppercase(), tone = StageTagTone.ACCENT)
                        },
                    )
                }
                item(key = "serial") { StageTextRow(MobileIcons.Edit, stringResource(R.string.setup_stalker_serial_number_optional), serialNumber, { serialNumber = it }) }
                item(key = "dev1") { StageTextRow(MobileIcons.Edit, stringResource(R.string.setup_stalker_device_id_optional), deviceId, { deviceId = it }) }
                item(key = "dev2") { StageTextRow(MobileIcons.Edit, stringResource(R.string.setup_stalker_device_id2_optional), deviceId2, { deviceId2 = it }) }
                item(key = "sig") { StageTextRow(MobileIcons.Edit, stringResource(R.string.setup_stalker_signature_optional), signature, { signature = it }) }
                item(key = "model") {
                    StageSettingRow(
                        icon = MobileIcons.Phone,
                        title = stringResource(R.string.setup_device_model_preset),
                        onClick = { showDeviceSheet = true },
                    ) { StageRowValue(stringResource(MAG_USER_AGENTS.firstOrNull { it.userAgent == userAgent }?.labelRes ?: MAG_USER_AGENTS.first().labelRes), StageChevron.DOWN) }
                }

                // Requirement 1: Primary connect button immediately after last identity parameter
                item(key = "stalker-connect-btn") {
                    StageButton(
                        onClick = {
                            if (tv.own.owntv.core.profile.GuestPolicy.isLimitReached(isGuest, sources, tv.own.owntv.core.model.SourceType.STALKER)) {
                                showLimitSheet = true
                                return@StageButton
                            }
                            if (mac.isBlank()) mac = StalkerDeviceDerivation.randomMac()
                            onSubmitted?.invoke(values())
                            onStartStalker(
                                name, portalUrl, mac, serialNumber, deviceId, deviceId2, signature,
                                userAgent, httpReferer, autoRefresh, syncLive, syncMovies, syncSeries, username, password,
                                loadChannelLogos, useOnlyPortalEpg,
                            )
                        },
                        text = stringResource(if (syncStats?.isRunning == true) R.string.setup_btn_syncing else R.string.setup_stalker_btn_connect),
                        icon = MobileIcons.PlayArrow,
                        style = StageButtonStyle.PRIMARY,
                        // One import at a time: a second press restarts the running one from the login.
                        enabled = canStart && syncStats?.isRunning != true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
                // Requirement 3: Two checkboxes under the button
                item(key = "stalker-checkbox-logos") {
                    StageSettingRow(
                        icon = MobileIcons.Image,
                        title = stringResource(R.string.setup_checkbox_channel_logos),
                        role = Role.Switch,
                        onClick = { loadChannelLogos = !loadChannelLogos },
                    ) { StageSwitch(loadChannelLogos) }
                }
                item(key = "stalker-checkbox-epg") {
                    StageSettingRow(
                        icon = MobileIcons.Guide,
                        title = stringResource(R.string.setup_checkbox_only_portal_epg),
                        role = Role.Switch,
                        onClick = { useOnlyPortalEpg = !useOnlyPortalEpg },
                    ) { StageSwitch(useOnlyPortalEpg) }
                }
                item(key = "stalker-protect-adult") {
                    StageSettingRow(
                        icon = MobileIcons.Lock,
                        title = stringResource(R.string.setup_checkbox_protect_adult),
                        role = Role.Switch,
                        onClick = { protectAdult = !protectAdult },
                    ) { StageSwitch(protectAdult) }
                }
                if (protectAdult) {
                    item(key = "stalker-protect-pin") {
                        StageTextRow(
                            MobileIcons.Lock, stringResource(R.string.setup_protect_pin), protectPin,
                            { protectPin = it.filter { c -> c.isDigit() }.take(8) },
                            password = true, keyboardType = KeyboardType.NumberPassword,
                        )
                    }
                }
                // Requirement 4: In-place sync progress card
                if (syncStats != null && (syncStats.isRunning || syncStats.isSuccess || syncStats.isFailed)) {
                    item(key = "stalker-sync-progress") {
                        SyncProgressCard(
                            stats = syncStats,
                            onOpenLive = onOpenLive,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        if (kind == SourceKind.XTREAM || kind == SourceKind.M3U) {
            item(key = "epg-head") {
                StageSectionHead(stringResource(R.string.setup_epg_section_title))
            }
            if (epgUrls.isNotEmpty()) {
                item(key = "epg-hint") {
                    PageNote(stringResource(R.string.setup_epg_source_priority_hint))
                }
            }
            epgUrls.forEachIndexed { index, url ->
                val urlTrimmed = url.trim()
                val isInvalid = urlTrimmed.isNotBlank() && !tv.own.owntv.core.repository.isValidEpgUrl(urlTrimmed)
                item(key = "epg-url-$index") {
                    StageTextRow(
                        icon = MobileIcons.Guide,
                        label = stringResource(R.string.setup_epg_source_label, index + 1),
                        value = url,
                        onSave = { updatedText ->
                            val list = epgUrls.toMutableList()
                            list[index] = updatedText
                            epgUrls = list
                        },
                        placeholder = stringResource(R.string.setup_epg_url_placeholder),
                        keyboardType = KeyboardType.Uri,
                        error = if (isInvalid) stringResource(R.string.setup_epg_url_invalid) else null,
                        trailing = {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(StageRadii.MenuItem))
                                    .background(palette.ink.copy(alpha = 0.05f))
                                    .stageClickable(role = Role.Button) {
                                        epgUrls = epgUrls.filterIndexed { i, _ -> i != index }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                StageIcon(
                                    icon = MobileIcons.Delete,
                                    size = 18.dp,
                                    tint = palette.danger,
                                )
                            }
                        },
                    )
                }
            }
            item(key = "epg-add") {
                StageSettingRow(
                    title = stringResource(R.string.setup_epg_add_source),
                    icon = MobileIcons.Add,
                    onClick = {
                        epgUrls = epgUrls + ""
                    },
                ) {
                    StageIcon(MobileIcons.KeyboardArrowRight, 14.dp, palette.muted)
                }
            }
        }
        item(key = "optional") { StageSectionHead(stringResource(R.string.settings_form_optional)) }
        item(key = "ua") {
            StageTextRow(MobileIcons.Edit, stringResource(R.string.setup_user_agent_optional), userAgent, { userAgent = it }, placeholder = stringResource(R.string.setup_user_agent_example))
        }
        item(key = "referer") {
            StageTextRow(MobileIcons.Edit, stringResource(R.string.setup_referer_optional), httpReferer, { httpReferer = it }, placeholder = stringResource(R.string.setup_referer_example))
        }
        item(key = "refresh") {
            StageSettingRow(
                icon = MobileIcons.Schedule,
                title = stringResource(R.string.setup_auto_refresh),
                onClick = { showRefreshSheet = true },
            ) { StageRowValue(stringResource(refreshMode.labelRes()), StageChevron.DOWN) }
        }
        if (refreshMode == PlaylistAutoRefresh.MANUAL) {
            item(key = "days") {
                StageTextRow(
                    MobileIcons.CalendarMonth, stringResource(R.string.settings_sources_refresh_days_title), manualDays,
                    { manualDays = it.filter(Char::isDigit).take(3) },
                    placeholder = stringResource(R.string.settings_sources_refresh_days_hint), keyboardType = KeyboardType.Number,
                )
            }
        }
        if (kind != SourceKind.M3U) {
            item(key = "sync-head") { StageSectionHead(stringResource(R.string.setup_what_to_sync)) }
            item(key = "sync-note") { PageNote(stringResource(R.string.setup_sync_choices)) }
            item(key = "sync-live") { ScopeRow(MobileIcons.LiveTv, stringResource(R.string.setup_live_tv), stringResource(R.string.setup_channels_categories), syncLive) { syncLive = it } }
            item(key = "sync-movies") { ScopeRow(MobileIcons.Movie, stringResource(R.string.setup_movies), stringResource(R.string.setup_vod_movie_catalog), syncMovies) { syncMovies = it } }
            item(key = "sync-series") { ScopeRow(MobileIcons.Series, stringResource(R.string.setup_series), stringResource(R.string.setup_tv_series_catalog), syncSeries) { syncSeries = it } }
        }
        if (kind != SourceKind.STALKER || editing) {
            item(key = "start") {
                StageButton(
                    onClick = {
                        onSave?.let { save -> save(values()); return@StageButton }
                        val targetType = when (kind) {
                            SourceKind.XTREAM -> tv.own.owntv.core.model.SourceType.XTREAM
                            SourceKind.M3U -> tv.own.owntv.core.model.SourceType.M3U
                            SourceKind.STALKER -> tv.own.owntv.core.model.SourceType.STALKER
                        }
                        if (tv.own.owntv.core.profile.GuestPolicy.isLimitReached(isGuest, sources, targetType)) {
                            showLimitSheet = true
                            return@StageButton
                        }
                        // A blank MAC is fine for a login-only portal: make one now and keep it, so a retry
                        // after a failure presents the same device (some portals bind the login to the first MAC).
                        if (kind == SourceKind.STALKER && mac.isBlank()) mac = StalkerDeviceDerivation.randomMac()
                        onSubmitted?.invoke(values())
                        val finalEpgUrl = tv.own.owntv.core.repository.joinGuideUrls(epgUrls)
                        when (kind) {
                            SourceKind.XTREAM -> onStartXtream(
                                name, server, username, password, userAgent, httpReferer, autoRefresh,
                                syncLive, syncMovies, syncSeries, preferHls, finalEpgUrl,
                            )
                            SourceKind.M3U -> onStartM3u(name, m3uUrl, userAgent, httpReferer, autoRefresh, finalEpgUrl)
                            SourceKind.STALKER -> onStartStalker(
                                name, portalUrl, mac, serialNumber, deviceId, deviceId2, signature,
                                userAgent, httpReferer, autoRefresh, syncLive, syncMovies, syncSeries, username, password,
                                loadChannelLogos, useOnlyPortalEpg,
                            )
                        }
                    },
                    text = stringResource(if (editing) R.string.common_save else R.string.setup_start_import),
                    icon = if (editing) MobileIcons.Check else MobileIcons.Download,
                    style = StageButtonStyle.PRIMARY,
                    enabled = canStart,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
                )
            }
        }
    }

    if (showRefreshSheet) {
        StageChoiceSheet(
            eyebrow = null,
            title = stringResource(R.string.setup_auto_refresh_title),
            text = stringResource(R.string.setup_auto_refresh_description),
            choices = PlaylistAutoRefresh.entries.map { SettingsChoice(it, stringResource(it.labelRes())) },
            selected = refreshMode,
            onSelect = { refreshMode = it },
            onDismiss = { showRefreshSheet = false },
        )
    }
    if (showDeviceSheet) {
        StageChoiceSheet(
            eyebrow = null,
            title = stringResource(R.string.setup_device_model_preset_title),
            text = null,
            choices = MAG_USER_AGENTS.map { SettingsChoice(it.userAgent, stringResource(it.labelRes)) },
            selected = userAgent,
            onSelect = { userAgent = it },
            onDismiss = { showDeviceSheet = false },
        )
    }
    if (showLimitSheet) {
        tv.own.owntv.mobile.ui.components.MobileBottomSheet(
            onDismissRequest = { showLimitSheet = false },
            title = stringResource(R.string.guest_mode_title),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.guest_limit_message),
                    style = stageText(15f, 500, lineHeight = 1.4f),
                    color = LocalStagePalette.current.text,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
                StageButton(
                    onClick = { showLimitSheet = false },
                    text = stringResource(R.string.common_ok),
                    style = StageButtonStyle.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Now, Later or not at all — one row per catalogue, cycled by tapping it. */
@Composable
private fun ScopeRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, description: String, value: SyncScopeChoice, onChange: (SyncScopeChoice) -> Unit) {
    val options = listOf(SyncScopeChoice.Now, SyncScopeChoice.Later, SyncScopeChoice.Off)
    StageSettingRow(
        icon = icon,
        title = label,
        subtitle = description,
        onClick = { onChange(options[(options.indexOf(value) + 1) % options.size]) },
    ) { StageRowValue(stringResource(
            when (value) {
                SyncScopeChoice.Now -> R.string.setup_now
                SyncScopeChoice.Later -> R.string.setup_later
                SyncScopeChoice.Off -> R.string.setup_off
            },
        ), StageChevron.DOWN) }
}

internal fun PlaylistAutoRefresh.labelRes(): Int = when (this) {
    PlaylistAutoRefresh.OFF -> R.string.settings_sources_refresh_off
    PlaylistAutoRefresh.STARTUP -> R.string.settings_sources_refresh_startup
    PlaylistAutoRefresh.HOURS_6 -> R.string.settings_sources_refresh_6h
    PlaylistAutoRefresh.HOURS_12 -> R.string.settings_sources_refresh_12h
    PlaylistAutoRefresh.MANUAL -> R.string.settings_sources_refresh_manual
}

/** Where a playlist picked from the phone's storage is kept. */
private const val PLAYLIST_DIR = "playlists"

@Composable
private fun SyncProgressCard(
    stats: SyncProgressStats,
    onOpenLive: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalStagePalette.current
    val accent = stageAccent.accent
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(RoundedCornerShape(StageRadii.Row))
            .background(palette.ink.copy(alpha = 0.08f))
            .padding(16.dp),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_btn_connect),
                style = stageText(16f, 700),
                color = palette.text,
            )
            if (stats.isRunning) {
                Text(
                    text = stringResource(R.string.setup_progress_connecting),
                    style = stageText(13f, 500),
                    color = palette.text.copy(alpha = 0.7f),
                )
            }
            Text(
                text = stringResource(R.string.setup_progress_channels, stats.channels),
                style = stageText(14f, 600),
                color = palette.text,
            )
            Text(
                text = stringResource(R.string.setup_progress_movies, stats.movies),
                style = stageText(14f, 600),
                color = palette.text,
            )
            Text(
                text = stringResource(R.string.setup_progress_series, stats.series),
                style = stageText(14f, 600),
                color = palette.text,
            )
            val epgStatus = if (stats.isEpgReady) {
                stringResource(R.string.setup_progress_epg_done)
            } else {
                stringResource(R.string.setup_progress_epg_running)
            }
            Text(
                text = stringResource(R.string.setup_progress_epg, epgStatus),
                style = stageText(14f, 600),
                color = if (stats.isEpgReady) accent else palette.dim,
            )
            if (stats.isSuccess && onOpenLive != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                StageButton(
                    text = stringResource(R.string.setup_btn_open_live),
                    onClick = onOpenLive,
                    icon = MobileIcons.LiveTv,
                    style = StageButtonStyle.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
