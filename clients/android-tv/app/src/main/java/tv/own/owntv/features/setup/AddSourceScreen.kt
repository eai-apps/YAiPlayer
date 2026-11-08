package tv.own.owntv.features.setup

import androidx.compose.foundation.layout.Box
import tv.own.owntv.ui.theme.mpx
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import tv.own.owntv.R
import tv.own.owntv.core.companion.CompanionPayload
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.resolveExistingProfileId
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.HlsSupport
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.parser.HlsProbe
import tv.own.owntv.core.parser.HlsTest
import tv.own.owntv.core.parser.XtreamClient
import tv.own.owntv.core.sync.SyncScopeChoice
import tv.own.owntv.features.settings.PickerDialog
import tv.own.owntv.features.settings.SettingHelp
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.features.settings.SettingValue
import tv.own.owntv.features.settings.StageAction
import tv.own.owntv.features.settings.StageActionColumn
import tv.own.owntv.features.settings.StageFieldRow
import tv.own.owntv.features.settings.StageFullPage
import tv.own.owntv.features.settings.StageSettingRow
import tv.own.owntv.features.settings.StageSettingsHeading
import androidx.compose.ui.focus.focusProperties
import tv.own.owntv.features.settings.SourceTestDialog
import tv.own.owntv.features.settings.SourceTestUi
import tv.own.owntv.core.repository.SourceTester
import tv.own.owntv.core.setup.MAG_USER_AGENTS
import tv.own.owntv.core.setup.displayText
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.ui.components.BrowseMode
import tv.own.owntv.ui.components.DayStepperDialog
import tv.own.owntv.ui.components.StorageBrowser

private enum class SourceKind { XTREAM, M3U, STALKER }

/** UI state of the Xtream "Test HLS support" probe. Local to this screen — the probe is one short
 *  request and saves nothing unless the source already exists. */
private sealed interface HlsTestUi {
    data object Idle : HlsTestUi
    data object Testing : HlsTestUi
    data class Complete(val test: HlsTest) : HlsTestUi
    data class Failed(val rawMessage: String) : HlsTestUi
}


@Composable
fun AddSourceScreen(
    onStartXtream: (
        name: String,
        server: String,
        user: String,
        pass: String,
        userAgent: String,
        referer: String,
        epgUrl: String,
        autoRefresh: PlaylistRefresh,
        live: SyncScopeChoice,
        movies: SyncScopeChoice,
        series: SyncScopeChoice,
        isDefault: Boolean,
        preferHls: Boolean,
    ) -> Unit,
    onStartM3u: (name: String, url: String, userAgent: String, referer: String, epgUrl: String, autoRefresh: PlaylistRefresh, isDefault: Boolean) -> Unit,
    // The last submission from the Remote companion screen, retained as a StateFlow so it survives the
    // Remote → Manual hand-off (this screen mounts after the remote browser posted). When present, the matching
    // type is selected and the fields pre-filled; the user then presses Start Import. Consumed once via
    // [onRemotePayloadConsumed] so it can't re-fill a later, unrelated Manual add.
    remotePayload: kotlinx.coroutines.flow.StateFlow<CompanionPayload?>? = null,
    onRemotePayloadConsumed: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initial: SourceEntity? = null,
    initialAutoRefresh: PlaylistRefresh = PlaylistRefresh.OFF,
    initialIsDefault: Boolean = false,
    showDefaultToggle: Boolean = true,
    // Editing only: opens the same playlist's page in a browser on another device. Its submission comes
    // back on [remotePayload], already merged with what is stored, and fills this form.
    onEditFromPhone: (() -> Unit)? = null,
    // Stalker portal. Null = the Stalker option is hidden (e.g. the setup wizard).
    onStartStalker: ((
        name: String,
        portalUrl: String,
        mac: String,
        serialNumber: String,
        deviceId: String,
        deviceId2: String,
        signature: String,
        userAgent: String,
        referer: String,
        autoRefresh: PlaylistRefresh,
        isDefault: Boolean,
        live: SyncScopeChoice,
        movies: SyncScopeChoice,
        series: SyncScopeChoice,
        login: String,
        password: String,
        loadChannelLogos: Boolean,
        useOnlyPortalEpg: Boolean,
    ) -> Unit)? = null,
    syncStats: tv.own.owntv.core.setup.SyncProgressStats? = null,
    onOpenLive: (() -> Unit)? = null,
) {
    val editing = initial != null
    var kind by remember {
        mutableStateOf(
            when (initial?.type) {
                SourceType.M3U -> SourceKind.M3U
                SourceType.STALKER -> SourceKind.STALKER
                else -> SourceKind.XTREAM
            },
        )
    }
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var server by remember(initial) { mutableStateOf(if (initial != null && initial.type == SourceType.XTREAM) initial.url else "") }
    var username by remember(initial) { mutableStateOf(initial?.username ?: "") }
    var password by remember(initial) { mutableStateOf(initial?.password ?: "") }
    var m3uUrl by remember(initial) { mutableStateOf(if (initial != null && initial.type == SourceType.M3U) initial.url else "") }
    var portalUrl by remember(initial) { mutableStateOf(if (initial != null && initial.type == SourceType.STALKER) initial.url else "") }
    var mac by remember(initial) { mutableStateOf(initial?.mac ?: "") }
    var stalkerSerialNumber by remember(initial) { mutableStateOf(initial?.stalkerSerialNumber ?: "") }
    var stalkerDeviceId by remember(initial) { mutableStateOf(initial?.stalkerDeviceId ?: "") }
    var stalkerDeviceId2 by remember(initial) { mutableStateOf(initial?.stalkerDeviceId2 ?: "") }
    var stalkerSignature by remember(initial) { mutableStateOf(initial?.stalkerSignature ?: "") }
    var showUaPresetPicker by remember { mutableStateOf(false) }
    var epgUrls by remember(initial) {
        mutableStateOf(tv.own.owntv.core.repository.splitGuideUrls(initial?.epgUrl))
    }
    var userAgent by remember(initial) { mutableStateOf(initial?.userAgent ?: "") }
    var referer by remember(initial) { mutableStateOf(initial?.httpReferer ?: "") }
    var autoRefresh by remember(initialAutoRefresh) { mutableStateOf(initialAutoRefresh) }
    var isDefault by remember(initialIsDefault) { mutableStateOf(initialIsDefault) }
    var preferHls by remember(initial) { mutableStateOf(initial?.preferHls == true) }
    var loadChannelLogos by remember { mutableStateOf(true) }
    var useOnlyPortalEpg by remember { mutableStateOf(true) }
    // "Protect adult channels with a PIN": handed to the import through AdultProtectChoice.
    remember { tv.own.owntv.core.setup.AdultProtectChoice.reset() }
    var protectAdult by remember { mutableStateOf(false) }
    var protectPin by remember { mutableStateOf("") }
    LaunchedEffect(protectAdult, protectPin) {
        tv.own.owntv.core.setup.AdultProtectChoice.enabled = protectAdult
        tv.own.owntv.core.setup.AdultProtectChoice.pin = protectPin
    }
    val stalkerConnectFocus = remember { FocusRequester() }
    // Edit: On(=Now)/Off from persisted flags. Add: default all Now for Xtream; Stalker defaults
    // Live Now + Movies/Series Later when the kind switches (see LaunchedEffect below).
    var syncLive by remember(initial) {
        mutableStateOf(if (initial?.syncLive == false) SyncScopeChoice.Off else SyncScopeChoice.Now)
    }
    var syncMovies by remember(initial) {
        mutableStateOf(if (initial?.syncMovies == false) SyncScopeChoice.Off else SyncScopeChoice.Now)
    }
    var syncSeries by remember(initial) {
        mutableStateOf(if (initial?.syncSeries == false) SyncScopeChoice.Off else SyncScopeChoice.Now)
    }
    var hasRemoteStalkerScopes by remember { mutableStateOf(false) }
    var showFileBrowser by remember { mutableStateOf(false) }
    var showAutoRefreshPicker by remember { mutableStateOf(false) }
    var showManualDaysPicker by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val startImportFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    // Profile-wide "hide new categories on sync" default. Written straight to
    // DataStore — no callback signature change — so it lands on the ACTIVE profile at add time. The row
    // is only shown while ADDING (an edit re-syncs nothing new) and only when a profile exists (the
    // setup wizard may still be profile-less; the value defaults to off there).
    val settings: SettingsRepository = koinInject()
    val profileDao: ProfileDao = koinInject()
    val sourceDao: tv.own.owntv.core.database.dao.SourceDao = koinInject()
    val isGuest by settings.isGuestActive.collectAsStateWithLifecycle(initialValue = false)
    val activeProfileId by settings.activeProfileId.collectAsStateWithLifecycle(initialValue = -1L)
    val sources by sourceDao.observeForProfile(activeProfileId).collectAsStateWithLifecycle(initialValue = emptyList())
    var showLimitPopup by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var hideNewCatsProfile by remember { mutableStateOf(-1L) }
    LaunchedEffect(Unit) {
        val preferred = settings.activeProfileId.first()
        hideNewCatsProfile = profileDao.resolveExistingProfileId(preferred) ?: -1L
    }
    val hideNewCats by settings.hideNewCategoriesDefault(hideNewCatsProfile)
        .collectAsStateWithLifecycle(initialValue = false)

    // ---- "Test HLS support" (Xtream) ----
    // A panel's `allowed_output_formats` is a claim, and a common one to get wrong in both directions,
    // so the button also *requests* an `.m3u8` channel and reads the answer. Driven from here rather
    // than a ViewModel so both hosts (the setup wizard and Settings › Sources & guide › Playlists) get it without
    // duplicating the plumbing — it's one short request that stores nothing unless the source exists.
    val xtreamClient: XtreamClient = koinInject()
    val channelDao: ChannelDao = koinInject()
    var hlsTest by remember { mutableStateOf<HlsTestUi>(HlsTestUi.Idle) }
    // A tested verdict outranks whatever the last sync recorded, so the note under the toggle updates
    // right away — including while ADDING, where there is no row to write to yet.
    var testedSupport by remember(initial) { mutableStateOf<HlsSupport?>(null) }
    // A verdict belongs to the credentials it was measured against. Editing any of them drops it —
    // a green "HLS works" sitting next to a server URL it never tested is worse than no answer.
    LaunchedEffect(server, username, password) {
        hlsTest = HlsTestUi.Idle
        testedSupport = null
    }

    // ---- "Test connection" (all three source types) ----
    // Runs against what is TYPED, not what is saved, so an account can be checked while adding it.
    // Read-only: it asks the provider for its account status and stores nothing.
    val sourceTester: SourceTester = koinInject()
    var sourceTest by remember { mutableStateOf<SourceTestUi?>(null) }
    // True while the "this stops playback and takes a while" confirmation is up.
    var confirmMeasure by remember { mutableStateOf(false) }
    // Cancelling this closes the probe's streams — it releases them in a `finally` — so Skip hands
    // the provider's connections straight back instead of leaving them held.
    var measureJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val connectionLimits: tv.own.owntv.core.live.ConnectionLimits = koinInject()
    val player: tv.own.owntv.player.OwnTVPlayer = koinInject()
    val livePreview: tv.own.owntv.player.LivePreviewEngine = koinInject()
    val enginePool: tv.own.owntv.player.LiveEnginePool = koinInject()

    fun formSource(): SourceEntity {
        fun opt(value: String) = value.trim().takeIf { it.isNotBlank() }
        val ua = opt(userAgent)
        val ref = opt(referer)
        return when (kind) {
            SourceKind.XTREAM -> SourceEntity(
                id = initial?.id ?: 0L, name = name, type = SourceType.XTREAM,
                url = server.trim(), username = username.trim(), password = password, userAgent = ua, httpReferer = ref,
            )
            SourceKind.M3U -> SourceEntity(
                id = initial?.id ?: 0L, name = name, type = SourceType.M3U,
                url = m3uUrl.trim(), userAgent = ua, httpReferer = ref,
            )
            SourceKind.STALKER -> SourceEntity(
                id = initial?.id ?: 0L, name = name, type = SourceType.STALKER,
                url = portalUrl.trim(), mac = mac.trim().ifBlank { tv.own.owntv.core.stalker.StalkerDeviceDerivation.randomMac().also { mac = it } },
                stalkerSerialNumber = opt(stalkerSerialNumber),
                stalkerDeviceId = opt(stalkerDeviceId),
                stalkerDeviceId2 = opt(stalkerDeviceId2),
                stalkerSignature = opt(stalkerSignature),
                username = opt(username),
                password = password.takeIf { opt(username) != null && it.isNotEmpty() },
                userAgent = ua,
                httpReferer = ref,
            )
        }
    }

    /**
     * The credentials check: one short request, and the reason this button exists before saving.
     *
     * It does *not* measure the connection limit. There is no playlist row to store an answer
     * against yet, and the first sync measures it anyway — so doing it here would spend two minutes
     * and two connections producing a number that is thrown away.
     */
    fun runSourceTest() {
        val probe = formSource()
        val label = name.ifBlank { probe.url }
        sourceTest = SourceTestUi.Running(label)
        scope.launch {
            val result = sourceTester.test(probe)
            // Dismissed while the request was in flight — don't reopen the dialog behind the user.
            if (sourceTest != null) sourceTest = SourceTestUi.Done(label, result)
        }
    }

    /**
     * Measure the limit from the edit form, for a playlist that already exists.
     *
     * Offered only when editing, because the result is saved against the playlist's row. Playback is
     * stopped first and the user has already agreed to the warning that says so.
     */
    fun runConnectionMeasurement() {
        val source = initial ?: return
        val label = name.ifBlank { source.url }
        measureJob?.cancel()
        measureJob = scope.launch {
            runCatching { player.stop() }
            runCatching { livePreview.stop() }
            runCatching { enginePool.releaseAll() }
            sourceTest = SourceTestUi.Measuring(label, tv.own.owntv.core.live.ProbeProgress(1, 1, tv.own.owntv.core.live.MAX_PROBE_STREAMS))
            val limit = connectionLimits.measureAndStore(source, force = true) { progress ->
                if (sourceTest is SourceTestUi.Measuring) sourceTest = SourceTestUi.Measuring(label, progress)
            }
            val result = sourceTester.test(source)
            if (sourceTest != null) sourceTest = SourceTestUi.Done(label, result, limit)
        }
    }

    fun runHlsTest() {
        hlsTest = HlsTestUi.Testing
        scope.launch {
            val probeSource = SourceEntity(
                id = initial?.id ?: 0L,
                name = name,
                type = SourceType.XTREAM,
                url = server.trim(),
                username = username.trim(),
                password = password,
                userAgent = userAgent.trim().takeIf { it.isNotBlank() },
            )
            // Stream ids belong to the panel that issued them, so a saved channel is only a valid
            // shortcut while the form still points at the same account.
            val sameAccount = initial != null && initial.type == SourceType.XTREAM &&
                initial.url.trim() == probeSource.url &&
                initial.username?.trim() == probeSource.username &&
                initial.password == probeSource.password
            val result = try {
                val known = if (sameAccount && probeSource.id > 0) channelDao.anyRemoteId(probeSource.id) else null
                xtreamClient.testHlsSupport(probeSource, known)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (e: Exception) {
                hlsTest = HlsTestUi.Failed(e.message ?: e.javaClass.simpleName)
                return@launch
            }
            hlsTest = HlsTestUi.Complete(result)
            // Only a decisive probe is worth storing. "Busy" and "couldn't verify" fall back to the
            // panel's claim for the wording, but must not overwrite an earlier proven answer.
            val proven = when (result.probe) {
                HlsProbe.Served -> HlsSupport.SUPPORTED
                is HlsProbe.NotServed -> HlsSupport.UNSUPPORTED
                else -> null
            }
            testedSupport = proven ?: result.declared?.let(HlsSupport::of) ?: HlsSupport.UNKNOWN
            if (proven != null && sameAccount && probeSource.id > 0) {
                sourceDao.updateHlsSupport(probeSource.id, proven)
            }
        }
    }

    // Pre-fill from a Remote (companion) submission handed off by the host. StateFlow replays its
    // current value to this new collector, so the payload posted before this screen mounted still lands.
    LaunchedEffect(remotePayload) {
        remotePayload?.collect { payload ->
            if (payload == null) return@collect
            if (editing) {
                // Only a browser edit of this playlist; a playlist cannot change its type.
                if (onEditFromPhone == null || payload.type != initial.type) return@collect
                name = payload.name
                userAgent = payload.userAgent
                when (payload.type) {
                    SourceType.M3U -> {
                        m3uUrl = payload.server
                        epgUrls = tv.own.owntv.core.repository.splitGuideUrls(payload.epgUrl)
                    }
                    SourceType.STALKER -> {
                        portalUrl = payload.portalUrl
                        mac = payload.mac
                        stalkerSerialNumber = payload.serialNumber
                        stalkerDeviceId = payload.deviceId
                        stalkerDeviceId2 = payload.deviceId2
                        stalkerSignature = payload.signature
                        username = payload.user
                        password = payload.pass
                    }
                    else -> {
                        server = payload.server
                        username = payload.user
                        password = payload.pass
                        epgUrls = tv.own.owntv.core.repository.splitGuideUrls(payload.epgUrl)
                    }
                }
                onRemotePayloadConsumed()
                runCatching { kotlinx.coroutines.delay(150); startImportFocus.requestFocus() }
                return@collect
            }
            name = payload.name
            userAgent = payload.userAgent
            epgUrls = tv.own.owntv.core.repository.splitGuideUrls(payload.epgUrl)
            isDefault = payload.isDefault
            autoRefresh = PlaylistRefresh.parse(payload.autoRefresh)
            when (payload.type) {
                SourceType.M3U -> {
                    hasRemoteStalkerScopes = false
                    m3uUrl = payload.server
                    kind = SourceKind.M3U
                }
                SourceType.STALKER -> {
                    portalUrl = payload.portalUrl
                    mac = payload.mac
                    stalkerSerialNumber = payload.serialNumber
                    stalkerDeviceId = payload.deviceId
                    stalkerDeviceId2 = payload.deviceId2
                    stalkerSignature = payload.signature
                    username = payload.user
                    password = payload.pass
                    syncLive = payload.syncLive
                    syncMovies = payload.syncMovies
                    syncSeries = payload.syncSeries
                    hasRemoteStalkerScopes = true
                    kind = SourceKind.STALKER
                }
                else -> {
                    server = payload.server
                    username = payload.user
                    password = payload.pass
                    syncLive = payload.syncLive
                    syncMovies = payload.syncMovies
                    syncSeries = payload.syncSeries
                    hasRemoteStalkerScopes = false
                    kind = SourceKind.XTREAM
                }
            }
            onRemotePayloadConsumed() // one-shot: don't re-fill a later, unrelated Manual add
            runCatching { kotlinx.coroutines.delay(150); startImportFocus.requestFocus() }
        }
    }

    // Stalker add defaults: Live Now, Movies/Series Later (VOD has no bulk endpoint). Skip when
    // editing, retrying a failed add, or applying explicit choices from a remote Stalker payload.
    LaunchedEffect(kind, initial, hasRemoteStalkerScopes) {
        if (initial != null || kind != SourceKind.STALKER || hasRemoteStalkerScopes) return@LaunchedEffect
        if (syncLive == SyncScopeChoice.Now && syncMovies == SyncScopeChoice.Now && syncSeries == SyncScopeChoice.Now) {
            syncMovies = SyncScopeChoice.Later
            syncSeries = SyncScopeChoice.Later
        }
    }

    val showContentToggles = kind == SourceKind.XTREAM || kind == SourceKind.STALKER
    val hasAnySectionOn = syncLive != SyncScopeChoice.Off || syncMovies != SyncScopeChoice.Off || syncSeries != SyncScopeChoice.Off
    val macValid = tv.own.owntv.core.stalker.StalkerClient.canonicalizeMac(mac) != null
    val hasInvalidEpg = (kind == SourceKind.XTREAM || kind == SourceKind.M3U) && epgUrls.any { it.isNotBlank() && !tv.own.owntv.core.repository.isValidEpgUrl(it) }
    val canStart = !hasInvalidEpg && when (kind) {
        SourceKind.XTREAM -> server.isNotBlank() && username.isNotBlank() && password.isNotBlank() && hasAnySectionOn
        SourceKind.M3U -> m3uUrl.isNotBlank()
        SourceKind.STALKER -> tv.own.owntv.core.stalker.StalkerClient.isValidPortalUrl(portalUrl) && (mac.isBlank() || macValid) && hasAnySectionOn
    }

    val canTest = when (kind) {
        SourceKind.XTREAM -> server.isNotBlank() && username.isNotBlank() && password.isNotBlank()
        SourceKind.M3U -> m3uUrl.isNotBlank() && !m3uUrl.startsWith("/")
        SourceKind.STALKER -> tv.own.owntv.core.stalker.StalkerClient.isValidPortalUrl(portalUrl) && (mac.isBlank() || macValid)
    }

    // Every field is a row (label + value or placeholder, OK = keyboard); Start import sits in the
    // panel, reached with ▶ from any row.
    val submit = submit@{
        // One import at a time: every press cancels and restarts the running one (the portal login and the
        // first catalogue request start over), which looked like "nothing happens" for minutes.
        if (syncStats?.isRunning == true) return@submit
        if (!editing) {
            val targetType = when (kind) {
                SourceKind.XTREAM -> tv.own.owntv.core.model.SourceType.XTREAM
                SourceKind.M3U -> tv.own.owntv.core.model.SourceType.M3U
                SourceKind.STALKER -> tv.own.owntv.core.model.SourceType.STALKER
            }
            if (tv.own.owntv.core.profile.GuestPolicy.isLimitReached(isGuest, sources, targetType)) {
                showLimitPopup = true
                return@submit
            }
        }
        val finalEpgUrl = tv.own.owntv.core.repository.joinGuideUrls(epgUrls)
        when (kind) {
            SourceKind.XTREAM -> onStartXtream(name, server, username, password, userAgent, referer, finalEpgUrl, autoRefresh, syncLive, syncMovies, syncSeries, isDefault, preferHls)
            SourceKind.M3U -> onStartM3u(name, m3uUrl, userAgent, referer, finalEpgUrl, autoRefresh, isDefault)
            SourceKind.STALKER -> onStartStalker?.invoke(
                // A blank MAC is fine for a login-only portal: make one now and keep it, so a retry
                // after a failure presents the same device.
                name, portalUrl, mac.ifBlank { tv.own.owntv.core.stalker.StalkerDeviceDerivation.randomMac().also { mac = it } }, stalkerSerialNumber, stalkerDeviceId, stalkerDeviceId2,
                stalkerSignature, userAgent, referer, autoRefresh, isDefault, syncLive, syncMovies, syncSeries,
                username, password,
                loadChannelLogos, useOnlyPortalEpg,
            )
        }
    }
    val pageTitle = stringResource(if (editing) R.string.setup_edit_source else R.string.settings_add_type_here)
    val parent = stringResource(if (editing) R.string.settings_playlists else R.string.setup_add_source)
    val ok = stringResource(R.string.common_ok)
    val back = stringResource(R.string.common_back)
    val fieldHelp = stringResource(R.string.settings_form_field_help)
    val submitLabel = stringResource(if (editing) R.string.setup_update_source_save else R.string.setup_start_import)
    // The panel's button: only while the form can be sent, so ▶ never lands on a dead control.
    val submitButton: @Composable () -> Unit = {
        val stats = syncStats
        if (stats != null && (stats.isRunning || stats.isSuccess || stats.isFailed)) {
            SyncProgressPanel(stats = stats, onOpenLive = onOpenLive)
        } else if (canStart) {
            StageActionColumn(listOf(StageAction(OwnTVIcon.DOWNLOADS, submitLabel, { submit() })), back = null, first = startImportFocus)
        }
    }
    val toSubmit = Modifier.focusProperties { right = startImportFocus }
    // ▶ reaches the panel's button from any row; said in the keys while the button is there.
    val submitHint = if (canStart) "▶" to submitLabel else null
    fun help(title: String, text: String, keys: List<Pair<String, String>>) =
        SettingHelp(title, text, hints = if (submitHint != null) keys.dropLast(1) + submitHint + keys.last() else keys, extra = submitButton)
    val openKeys = listOf(ok to stringResource(R.string.settings_key_open), back to parent)
    val changeKeys = listOf(ok to stringResource(R.string.settings_key_change), back to parent)
    val switchKeys = listOf(ok to stringResource(R.string.settings_key_switch), back to parent)

    @Composable
    fun field(
        label: String,
        value: String,
        onChange: (String) -> Unit,
        placeholder: String = "",
        password: Boolean = false,
        keyboardType: KeyboardType = KeyboardType.Text,
        about: String? = null,
        modifier: Modifier = Modifier,
        required: Boolean = false,
        error: String? = null,
    ) = StageFieldRow(
        icon = OwnTVIcon.PENCIL,
        label = label,
        badge = if (required) stringResource(R.string.setup_badge_required) else null,
        value = value,
        onValueChange = onChange,
        help = SettingHelp(label, listOfNotNull(if (required) stringResource(R.string.setup_badge_required) else null, about, fieldHelp).joinToString(" "), extra = submitButton),
        placeholder = placeholder,
        password = password,
        keyboardType = keyboardType,
        backLabel = parent,
        submitHint = submitHint,
        modifier = modifier.then(toSubmit),
        error = error,
    )

    Box(modifier.fillMaxSize()) {
    StageFullPage(
        parents = listOf(parent),
        title = pageTitle,
        count = "",
        onBack = onBack,
        rowsFocus = firstFocus,
        settingsRoot = false,
        panelTop = {
            val stats = syncStats
            if (stats != null && (stats.isRunning || stats.isSuccess || stats.isFailed)) {
                SyncProgressPanel(stats = stats, onOpenLive = onOpenLive)
            }
        },
    ) {
        // Type: locked while editing (a source can't change its type).
        val kinds = listOfNotNull(SourceKind.XTREAM, SourceKind.M3U, SourceKind.STALKER.takeIf { onStartStalker != null })
        val kindLabels = kinds.map {
            stringResource(
                when (it) {
                    SourceKind.XTREAM -> R.string.setup_xtream
                    SourceKind.M3U -> R.string.setup_m3u
                    SourceKind.STALKER -> R.string.setup_stalker_mac
                },
            )
        }
        val typeTitle = stringResource(R.string.settings_source_type)
        StageSettingRow(
            icon = OwnTVIcon.LIST,
            title = typeTitle,
            desc = stringResource(R.string.settings_line_source_type),
            value = if (editing) SettingValue.Action(kindLabels[kinds.indexOf(kind)]) else SettingValue.Segmented(kindLabels, kinds.indexOf(kind)),
            onClick = { if (!editing) kind = kinds[(kinds.indexOf(kind) + 1) % kinds.size] },
            onStep = if (editing) null else { step -> kind = kinds[(kinds.indexOf(kind) + step).coerceIn(0, kinds.lastIndex)] },
            help = help(
                typeTitle,
                stringResource(if (editing) R.string.setup_edit_source_description else R.string.setup_byo_source_description),
                if (editing) listOf(back to parent) else listOf("◀ ▶" to stringResource(R.string.settings_key_change), back to parent),
            ),
        )
        if (editing && onEditFromPhone != null) {
            val remoteTitle = stringResource(R.string.setup_edit_from_phone)
            StageSettingRow(
                icon = OwnTVIcon.PHONE,
                title = remoteTitle,
                desc = stringResource(R.string.settings_line_add_from_phone),
                value = SettingValue.Opens(null),
                onClick = onEditFromPhone,
                modifier = toSubmit,
                help = help(remoteTitle, stringResource(R.string.settings_help_edit_from_phone), openKeys),
            )
        }
        field(stringResource(R.string.setup_source_name_optional), name, { name = it }, placeholder = stringResource(R.string.setup_default_iptv))

        when (kind) {
            SourceKind.XTREAM -> {
                field(stringResource(R.string.setup_server_url), server, { server = it }, placeholder = stringResource(R.string.setup_server_example), keyboardType = KeyboardType.Uri)
                field(stringResource(R.string.setup_username), username, { username = it })
                field(stringResource(if (editing) R.string.setup_password_keep else R.string.setup_password), password, { password = it }, password = true)
            }
            SourceKind.M3U -> {
                field(
                    stringResource(R.string.setup_playlist_url_local_file), m3uUrl, { m3uUrl = it },
                    placeholder = stringResource(R.string.setup_playlist_example), keyboardType = KeyboardType.Uri,
                    about = stringResource(R.string.settings_help_playlist_url),
                )
                val picked = remember(m3uUrl) { if (m3uUrl.startsWith("/")) java.io.File(m3uUrl).name else null }
                val fileTitle = stringResource(R.string.settings_choose_local_file)
                StageSettingRow(
                    icon = OwnTVIcon.FOLDER,
                    title = fileTitle,
                    desc = picked?.let { stringResource(R.string.setup_local_file_prefix, it) } ?: stringResource(R.string.settings_line_local_file),
                    value = SettingValue.Opens(null),
                    onClick = { showFileBrowser = true },
                    modifier = toSubmit,
                    help = help(fileTitle, stringResource(R.string.setup_local_file_choose), openKeys),
                )
                StageSettingRow(
                    icon = OwnTVIcon.LOCK,
                    title = stringResource(R.string.setup_checkbox_protect_adult),
                    desc = null,
                    value = SettingValue.Switch(protectAdult),
                    onClick = { protectAdult = !protectAdult },
                    modifier = toSubmit,
                    help = help(stringResource(R.string.setup_checkbox_protect_adult), stringResource(R.string.setup_checkbox_protect_adult_help), switchKeys),
                )
                if (protectAdult) {
                    field(
                        stringResource(R.string.setup_protect_pin), protectPin,
                        { protectPin = it.filter { c -> c.isDigit() }.take(8) },
                        keyboardType = KeyboardType.NumberPassword, password = true,
                    )
                }
            }
            SourceKind.STALKER -> {
                field(
                    stringResource(R.string.setup_portal_url),
                    portalUrl,
                    { portalUrl = it },
                    placeholder = stringResource(R.string.setup_portal_example),
                    keyboardType = KeyboardType.Uri,
                    required = true,
                )
                field(
                    stringResource(R.string.setup_mac_address),
                    mac,
                    { mac = it },
                    placeholder = stringResource(R.string.setup_mac_example),
                    about = if (mac.isNotBlank() && !macValid) stringResource(R.string.setup_mac_invalid) else null,
                )
                val reliableTitle = stringResource(R.string.setup_mac_generate_reliable)
                StageSettingRow(
                    icon = OwnTVIcon.PENCIL,
                    title = reliableTitle,
                    desc = stringResource(R.string.setup_mac_optional_note),
                    value = SettingValue.Custom {
                        tv.own.owntv.ui.stage.StageTag(stringResource(R.string.setup_badge_required).uppercase(), tint = tv.own.owntv.ui.theme.stageAccent.accent)
                    },
                    onClick = { mac = tv.own.owntv.core.stalker.StalkerDeviceDerivation.reliableMac() },
                    modifier = toSubmit,
                    help = help(reliableTitle, stringResource(R.string.setup_mac_optional_note), openKeys),
                )
                val genTitle = stringResource(R.string.setup_mac_generate)
                StageSettingRow(
                    icon = OwnTVIcon.PENCIL,
                    title = genTitle,
                    desc = stringResource(R.string.setup_mac_optional_note),
                    value = SettingValue.Opens(null),
                    onClick = { mac = tv.own.owntv.core.stalker.StalkerDeviceDerivation.randomMac() },
                    modifier = toSubmit,
                    help = help(genTitle, stringResource(R.string.setup_mac_optional_note), openKeys),
                )
                field(
                    stringResource(R.string.setup_username),
                    username,
                    { username = it },
                    required = true,
                )
                field(
                    stringResource(R.string.setup_password),
                    password,
                    { password = it },
                    password = true,
                    required = true,
                )
                StageSettingsHeading(stringResource(R.string.setup_stalker_advanced_identity), 5)
                val fillTitle = stringResource(R.string.setup_stalker_identity_fill)
                StageSettingRow(
                    icon = OwnTVIcon.PHONE,
                    title = fillTitle,
                    desc = null,
                    value = SettingValue.Custom {
                        tv.own.owntv.ui.stage.StageTag(stringResource(R.string.setup_badge_required).uppercase(), tint = tv.own.owntv.ui.theme.stageAccent.accent)
                    },
                    onClick = {
                        val canon = tv.own.owntv.core.stalker.StalkerClient.canonicalizeMac(mac)
                            ?: tv.own.owntv.core.stalker.StalkerDeviceDerivation.randomMac().also { mac = it }
                        val id = tv.own.owntv.core.stalker.StalkerDeviceDerivation.identityFor(canon)
                        stalkerSerialNumber = id.serialNumber.orEmpty()
                        stalkerDeviceId = id.deviceId.orEmpty()
                        stalkerDeviceId2 = id.deviceId2.orEmpty()
                    },
                    modifier = toSubmit,
                    help = help(fillTitle, stringResource(R.string.setup_stalker_identity_fill), openKeys),
                )
                field(stringResource(R.string.setup_stalker_serial_number_optional), stalkerSerialNumber, { stalkerSerialNumber = it })
                field(stringResource(R.string.setup_stalker_device_id_optional), stalkerDeviceId, { stalkerDeviceId = it })
                field(stringResource(R.string.setup_stalker_device_id2_optional), stalkerDeviceId2, { stalkerDeviceId2 = it })
                field(stringResource(R.string.setup_stalker_signature_optional), stalkerSignature, { stalkerSignature = it })
                val presetTitle = stringResource(R.string.setup_device_model_preset)
                StageSettingRow(
                    icon = OwnTVIcon.PHONE,
                    title = presetTitle,
                    desc = MAG_USER_AGENTS.firstOrNull { it.userAgent == userAgent }?.let { stringResource(it.labelRes) },
                    value = SettingValue.Opens(null),
                    onClick = { showUaPresetPicker = true },
                    modifier = toSubmit,
                    help = help(presetTitle, stringResource(R.string.setup_device_model_preset_title), openKeys),
                )

                // Requirement 1: Main button immediately after the last identity field
                androidx.compose.foundation.layout.Spacer(Modifier.height(14.mpx))
                val stalkerRunning = syncStats?.isRunning == true
                val stalkerBtnLabel = stringResource(if (stalkerRunning) R.string.setup_btn_syncing else R.string.setup_stalker_btn_connect)
                tv.own.owntv.ui.stage.StageButton(
                    text = stalkerBtnLabel,
                    onClick = { if (canStart && !stalkerRunning) submit() },
                    tinted = true,
                    icon = OwnTVIcon.PLAY,
                    height = 68.mpx,
                    textSize = 22,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(stalkerConnectFocus)
                        .then(toSubmit),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(8.mpx))

                // Requirement 3: Two checkboxes under the button
                StageSettingRow(
                    icon = OwnTVIcon.IMAGE,
                    title = stringResource(R.string.setup_checkbox_channel_logos),
                    desc = null,
                    value = SettingValue.Switch(loadChannelLogos),
                    onClick = { loadChannelLogos = !loadChannelLogos },
                    modifier = toSubmit,
                    help = help(stringResource(R.string.setup_checkbox_channel_logos), stringResource(R.string.setup_checkbox_channel_logos), switchKeys),
                )
                StageSettingRow(
                    icon = OwnTVIcon.EPG,
                    title = stringResource(R.string.setup_checkbox_only_portal_epg),
                    desc = null,
                    value = SettingValue.Switch(useOnlyPortalEpg),
                    onClick = { useOnlyPortalEpg = !useOnlyPortalEpg },
                    modifier = toSubmit,
                    help = help(stringResource(R.string.setup_checkbox_only_portal_epg), stringResource(R.string.setup_checkbox_only_portal_epg), switchKeys),
                )
                StageSettingRow(
                    icon = OwnTVIcon.LOCK,
                    title = stringResource(R.string.setup_checkbox_protect_adult),
                    desc = null,
                    value = SettingValue.Switch(protectAdult),
                    onClick = { protectAdult = !protectAdult },
                    modifier = toSubmit,
                    help = help(stringResource(R.string.setup_checkbox_protect_adult), stringResource(R.string.setup_checkbox_protect_adult_help), switchKeys),
                )
                if (protectAdult) {
                    field(
                        stringResource(R.string.setup_protect_pin), protectPin,
                        { protectPin = it.filter { c -> c.isDigit() }.take(8) },
                        keyboardType = KeyboardType.NumberPassword, password = true,
                    )
                }
            }
        }

        if (kind == SourceKind.M3U || kind == SourceKind.XTREAM) {
            StageSettingsHeading(stringResource(R.string.setup_epg_section_title), null)
            if (epgUrls.isNotEmpty()) {
                StageSettingRow(
                    icon = OwnTVIcon.EPG,
                    title = stringResource(R.string.setup_epg_source_priority_hint),
                    desc = null,
                    value = SettingValue.None,
                    onClick = {},
                    modifier = toSubmit,
                    help = help(stringResource(R.string.setup_epg_section_title), stringResource(R.string.setup_epg_source_priority_hint), listOf(back to parent)),
                )
            }
            epgUrls.forEachIndexed { i, url ->
                val invalid = url.isNotBlank() && !tv.own.owntv.core.repository.isValidEpgUrl(url)
                val label = stringResource(R.string.setup_epg_source_label, i + 1)
                val errorText = if (invalid) stringResource(R.string.setup_epg_url_invalid) else null
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.mpx),
                    horizontalArrangement = Arrangement.spacedBy(10.mpx),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        field(
                            label = label,
                            value = url,
                            onChange = { newText ->
                                val list = epgUrls.toMutableList()
                                list[i] = newText
                                epgUrls = list
                            },
                            placeholder = stringResource(R.string.setup_epg_url_placeholder),
                            keyboardType = KeyboardType.Uri,
                            about = errorText,
                            error = errorText,
                        )
                    }
                    StageSurface(
                        onClick = {
                            epgUrls = epgUrls.filterIndexed { index, _ -> index != i }
                        },
                        radius = StageRadii.Row,
                        focusStyle = StageFocus.FX,
                        modifier = Modifier.size(84.mpx).then(toSubmit),
                    ) { focused ->
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            OwnTVIcon(OwnTVIcon.TRASH, tint = if (focused) StageColors.Danger else StageColors.Muted, modifier = Modifier.size(24.mpx))
                        }
                    }
                }
            }
            val addEpgTitle = stringResource(R.string.setup_epg_add_source)
            StageSettingRow(
                icon = OwnTVIcon.EPG,
                title = addEpgTitle,
                desc = null,
                value = SettingValue.Opens(null),
                onClick = { epgUrls = epgUrls + "" },
                modifier = toSubmit,
                help = help(addEpgTitle, addEpgTitle, openKeys),
            )
        }

        StageSettingsHeading(stringResource(R.string.settings_form_optional), null)
        field(stringResource(R.string.setup_user_agent_optional), userAgent, { userAgent = it }, placeholder = stringResource(R.string.setup_user_agent_example))
        field(stringResource(R.string.setup_referer_optional), referer, { referer = it }, placeholder = stringResource(R.string.setup_referer_example))
        val refreshTitle = stringResource(R.string.setup_auto_refresh)
        StageSettingRow(
            icon = OwnTVIcon.REFRESH,
            title = refreshTitle,
            desc = stringResource(R.string.setup_auto_refresh_description),
            value = SettingValue.Choice(playlistAutoRefreshLabel(autoRefresh)),
            onClick = { showAutoRefreshPicker = true },
            modifier = toSubmit,
            help = help(refreshTitle, stringResource(R.string.setup_auto_refresh_description), changeKeys),
        )
        if (showDefaultToggle) {
            val defaultTitle = stringResource(R.string.setup_default_playlist)
            val defaultDesc = stringResource(R.string.setup_default_playlist_description)
            StageSettingRow(
                icon = OwnTVIcon.STAR,
                title = defaultTitle,
                desc = defaultDesc,
                value = SettingValue.Switch(isDefault),
                onClick = { isDefault = !isDefault },
                modifier = toSubmit,
                help = help(defaultTitle, defaultDesc, switchKeys),
            )
        }
        val testTitle = stringResource(if (sourceTest is SourceTestUi.Running) R.string.setup_testing else R.string.setup_test_connection)
        StageSettingRow(
            icon = OwnTVIcon.INFO,
            title = testTitle,
            desc = null,
            value = null,
            onClick = { if (sourceTest == null && canTest) runSourceTest() },
            enabled = canTest,
            modifier = toSubmit,
            help = help(testTitle, stringResource(R.string.setup_byo_source_description), listOf(ok to testTitle, back to parent)),
        )
        // Every Xtream source, add and edit: support is only known after a sync, so the choice is never hidden.
        if (kind == SourceKind.XTREAM) {
            val hlsTitle = stringResource(if (hlsTest is HlsTestUi.Testing) R.string.setup_hls_testing else R.string.setup_hls_test_support)
            val hlsLine = when (val t = hlsTest) {
                is HlsTestUi.Complete -> t.test.displayText(LocalContext.current.resources)
                is HlsTestUi.Failed -> stringResource(R.string.setup_hls_test_failed, t.rawMessage)
                else -> null
            }
            StageSettingRow(
                icon = OwnTVIcon.LIVE_TV,
                title = hlsTitle,
                desc = hlsLine,
                value = null,
                // Re-entry blocked here, not by disabling: a disabled row would drop focus mid-probe.
                onClick = { if (hlsTest !is HlsTestUi.Testing && server.isNotBlank() && username.isNotBlank() && password.isNotBlank()) runHlsTest() },
                modifier = toSubmit,
                help = help(hlsTitle, hlsLine ?: stringResource(R.string.setup_prefer_hls_description), listOf(ok to hlsTitle, back to parent)),
            )
            val preferTitle = stringResource(R.string.setup_prefer_hls_live_tv)
            val preferDesc = stringResource(
                when (testedSupport ?: initial?.hlsSupported ?: HlsSupport.UNKNOWN) {
                    HlsSupport.SUPPORTED -> R.string.setup_prefer_hls_description_supported
                    HlsSupport.UNSUPPORTED -> R.string.setup_prefer_hls_description_unsupported
                    HlsSupport.UNKNOWN -> R.string.setup_prefer_hls_description
                },
            )
            StageSettingRow(
                icon = OwnTVIcon.LIVE_TV,
                title = preferTitle,
                desc = preferDesc,
                value = SettingValue.Switch(preferHls),
                onClick = { preferHls = !preferHls },
                modifier = toSubmit,
                help = help(preferTitle, preferDesc, switchKeys),
            )
        }

        if (showContentToggles) {
            StageSettingsHeading(stringResource(R.string.setup_what_to_sync), 3)
            val scopeHelp = stringResource(if (editing) R.string.setup_sync_off_editing else R.string.setup_sync_choices)
            SyncScopeStageRow(OwnTVIcon.LIVE_TV, stringResource(R.string.setup_live_tv), stringResource(R.string.setup_channels_categories), syncLive, editing, help(stringResource(R.string.setup_live_tv), scopeHelp, changeKeys), toSubmit) { syncLive = it }
            SyncScopeStageRow(OwnTVIcon.MOVIES, stringResource(R.string.setup_movies), stringResource(R.string.setup_vod_movie_catalog), syncMovies, editing, help(stringResource(R.string.setup_movies), scopeHelp, changeKeys), toSubmit) { syncMovies = it }
            SyncScopeStageRow(OwnTVIcon.SERIES, stringResource(R.string.setup_series), stringResource(R.string.setup_tv_series_catalog), syncSeries, editing, help(stringResource(R.string.setup_series), scopeHelp, changeKeys), toSubmit) { syncSeries = it }
        }
        if (!editing && hideNewCatsProfile >= 0) {
            val hideTitle = stringResource(R.string.setup_hide_new_categories)
            val hideDesc = stringResource(R.string.setup_hide_new_categories_description)
            StageSettingRow(
                icon = OwnTVIcon.EYE_OFF,
                title = hideTitle,
                desc = hideDesc,
                value = SettingValue.Switch(hideNewCats),
                onClick = { scope.launch { settings.setHideNewCategoriesDefault(hideNewCatsProfile, !hideNewCats) } },
                modifier = toSubmit,
                help = help(hideTitle, hideDesc, switchKeys),
            )
        }
    }
      // In-app, TV-safe file picker (SAF / system file picker is missing on many TVs).
      if (showFileBrowser) {
          StorageBrowser(
              title = stringResource(R.string.setup_pick_playlist_file),
              mode = BrowseMode.FILE,
              fileExtensions = setOf("m3u", "m3u8"),
              onPick = { file ->
                  showFileBrowser = false
                  m3uUrl = file.absolutePath
                  if (name.isBlank()) name = file.nameWithoutExtension
              },
              onDismiss = { showFileBrowser = false },
          )
      }
      if (showUaPresetPicker) {
          PickerDialog(
              title = stringResource(R.string.setup_device_model_preset_title),
              options = MAG_USER_AGENTS.map { it.labelRes.toString() to stringResource(it.labelRes) },
              selected = MAG_USER_AGENTS.firstOrNull { it.userAgent == userAgent }?.labelRes?.toString()
                  ?: MAG_USER_AGENTS.first().labelRes.toString(),
              onSelect = { labelRes ->
                  userAgent = MAG_USER_AGENTS.firstOrNull { it.labelRes.toString() == labelRes }?.userAgent.orEmpty()
                  showUaPresetPicker = false
              },
              onDismiss = { showUaPresetPicker = false },
          )
      }
      if (showAutoRefreshPicker) {
          PickerDialog(
              title = stringResource(R.string.setup_auto_refresh_title),
              options = PlaylistAutoRefresh.entries.map { it.name to refreshModeLabel(it) },
              selected = autoRefresh.mode.name,
              onSelect = { value ->
                  val mode = runCatching { PlaylistAutoRefresh.valueOf(value) }.getOrDefault(PlaylistAutoRefresh.OFF)
                  showAutoRefreshPicker = false
                  // Manual needs a day count, so it hands straight over to the stepper; cancelling
                  // there leaves the previous selection untouched.
                  if (mode == PlaylistAutoRefresh.MANUAL) showManualDaysPicker = true
                  else autoRefresh = PlaylistRefresh(mode, autoRefresh.manualDays)
              },
              onDismiss = { showAutoRefreshPicker = false },
          )
      }
      if (showManualDaysPicker) {
          ManualDaysDialog(
              initialDays = autoRefresh.manualDays,
              onConfirm = { days ->
                  autoRefresh = PlaylistRefresh(PlaylistAutoRefresh.MANUAL, days)
                  showManualDaysPicker = false
              },
              onDismiss = { showManualDaysPicker = false },
          )
      }
      if (confirmMeasure) {
          tv.own.owntv.features.settings.ConfirmDialog(
              title = stringResource(R.string.settings_sources_probe_title),
              message = stringResource(R.string.settings_sources_probe_warning),
              onConfirm = { confirmMeasure = false; runConnectionMeasurement() },
              onDismiss = { confirmMeasure = false },
              confirmLabel = R.string.settings_sources_retest,
          )
      }
      sourceTest?.let { state ->
          SourceTestDialog(
              state = state,
              onDismiss = { sourceTest = null },
              // Editing only: an unsaved playlist has nowhere to keep the answer.
              onRetest = if (initial != null) { { confirmMeasure = true } } else null,
              onSkip = { measureJob?.cancel(); measureJob = null; sourceTest = null },
          )
      }
      if (showLimitPopup) {
          tv.own.owntv.ui.stage.StagePopup(
              onDismiss = { showLimitPopup = false },
              title = stringResource(R.string.guest_mode_title),
              body = stringResource(R.string.guest_limit_message),
              buttons = {
                  tv.own.owntv.ui.stage.StagePopupButton(
                      label = stringResource(R.string.common_ok),
                      onClick = { showLimitPopup = false },
                      primary = true,
                  )
              },
          )
      }
    }
}

/** Picker entry for a mode. Manual reads as an ellipsis label — it opens the day stepper. */
@Composable
private fun refreshModeLabel(mode: PlaylistAutoRefresh): String = stringResource(
    when (mode) {
        PlaylistAutoRefresh.OFF -> R.string.settings_sources_refresh_off
        PlaylistAutoRefresh.STARTUP -> R.string.settings_sources_refresh_startup
        PlaylistAutoRefresh.HOURS_6 -> R.string.settings_sources_refresh_6h
        PlaylistAutoRefresh.HOURS_12 -> R.string.settings_sources_refresh_12h
        PlaylistAutoRefresh.MANUAL -> R.string.settings_sources_refresh_manual
    },
)

/** The chosen selection as shown on a row: Manual spells out the day count it resolved to. */
@Composable
internal fun playlistAutoRefreshLabel(refresh: PlaylistRefresh): String =
    if (refresh.mode == PlaylistAutoRefresh.MANUAL) {
        pluralStringResource(R.plurals.settings_sources_refresh_days, refresh.manualDays, refresh.manualDays)
    } else {
        refreshModeLabel(refresh.mode)
    }

/**
 * Day stepper for the Manual auto-refresh interval — [DayStepperDialog] with the playlist's own
 * wording and bounds.
 */
@Composable
private fun ManualDaysDialog(initialDays: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    DayStepperDialog(
        title = stringResource(R.string.settings_sources_refresh_days_title),
        hint = stringResource(R.string.settings_sources_refresh_days_hint),
        initialDays = initialDays,
        minDays = PlaylistRefresh.MIN_MANUAL_DAYS,
        maxDays = PlaylistRefresh.MAX_MANUAL_DAYS,
        label = { days -> pluralStringResource(R.plurals.settings_sources_refresh_days, days, days) },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** What to sync, one row per section: ◀ ▶ (or OK) step Now / Later / Off — On / Off while editing. */
@Composable
private fun SyncScopeStageRow(
    icon: OwnTVIcon,
    label: String,
    desc: String,
    value: SyncScopeChoice,
    editing: Boolean,
    help: tv.own.owntv.features.settings.SettingHelp,
    modifier: Modifier,
    onChange: (SyncScopeChoice) -> Unit,
) {
    val options = if (editing) listOf(SyncScopeChoice.Now, SyncScopeChoice.Off) else listOf(SyncScopeChoice.Now, SyncScopeChoice.Later, SyncScopeChoice.Off)
    val labels = options.map {
        when (it) {
            SyncScopeChoice.Now -> stringResource(if (editing) R.string.setup_on else R.string.setup_now)
            SyncScopeChoice.Later -> stringResource(R.string.setup_later)
            SyncScopeChoice.Off -> stringResource(R.string.setup_off)
        }
    }
    val at = options.indexOf(value).coerceAtLeast(0)
    StageSettingRow(
        icon = icon,
        title = label,
        desc = desc,
        value = SettingValue.Segmented(labels, at),
        onClick = { onChange(options[(at + 1) % options.size]) },
        onStep = { step -> onChange(options[(at + step).coerceIn(0, options.lastIndex)]) },
        modifier = modifier,
        help = help,
    )
}

@Composable
private fun SyncProgressPanel(
    stats: tv.own.owntv.core.setup.SyncProgressStats,
    onOpenLive: (() -> Unit)?,
) {
    val liveFocus = remember { FocusRequester() }
    LaunchedEffect(stats.isSuccess) {
        if (stats.isSuccess) {
            runCatching { liveFocus.requestFocus() }
        }
    }
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.mpx),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.mpx),
    ) {
        tv.own.owntv.features.settings.SettingPanelHeading(stringResource(R.string.setup_btn_connect))
        if (stats.isRunning) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.mpx),
            ) {
                tv.own.owntv.ui.components.OwnTVSpinner(sizeDp = 28)
                Text(
                    stringResource(R.string.setup_progress_connecting),
                    style = tv.own.owntv.ui.theme.stageText(18, 500),
                    color = tv.own.owntv.ui.theme.StageColors.Muted,
                )
            }
        }
        Text(
            stringResource(R.string.setup_progress_channels, stats.channels),
            style = tv.own.owntv.ui.theme.stageText(20, 600),
            color = tv.own.owntv.ui.theme.StageColors.Text,
        )
        Text(
            stringResource(R.string.setup_progress_movies, stats.movies),
            style = tv.own.owntv.ui.theme.stageText(20, 600),
            color = tv.own.owntv.ui.theme.StageColors.Text,
        )
        Text(
            stringResource(R.string.setup_progress_series, stats.series),
            style = tv.own.owntv.ui.theme.stageText(20, 600),
            color = tv.own.owntv.ui.theme.StageColors.Text,
        )
        val epgStatus = if (stats.isEpgReady) {
            stringResource(R.string.setup_progress_epg_done)
        } else {
            stringResource(R.string.setup_progress_epg_running)
        }
        Text(
            stringResource(R.string.setup_progress_epg, epgStatus),
            style = tv.own.owntv.ui.theme.stageText(20, 600),
            color = if (stats.isEpgReady) tv.own.owntv.ui.theme.stageAccent.accent else tv.own.owntv.ui.theme.StageColors.Dim,
        )
        if (stats.isSuccess && onOpenLive != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(10.mpx))
            tv.own.owntv.ui.stage.StageButton(
                text = stringResource(R.string.setup_btn_open_live),
                onClick = onOpenLive,
                tinted = true,
                icon = OwnTVIcon.LIVE_TV,
                height = 64.mpx,
                textSize = 20,
                modifier = Modifier.fillMaxWidth().focusRequester(liveFocus),
            )
        }
    }
}
