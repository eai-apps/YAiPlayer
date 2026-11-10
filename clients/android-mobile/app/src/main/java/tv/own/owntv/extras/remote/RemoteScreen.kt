// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.own.owntv.core.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StagePill
import tv.own.owntv.mobile.ui.stage.StageSearchField
import tv.own.owntv.mobile.ui.stage.StageSegmented
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageBackground
import tv.own.owntv.mobile.ui.stage.stageText
import kotlin.math.abs

@Composable
fun RemoteScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: RemoteViewModel = viewModel(),
) {
    val context = LocalContext.current
    val palette = LocalStagePalette.current
    val accent = stageAccent
    val haptic = LocalHapticFeedback.current

    val status by vm.status.collectAsStateWithLifecycle()
    val tvState by vm.tvState.collectAsStateWithLifecycle()
    val selectedTv by vm.selectedTv.collectAsStateWithLifecycle()
    val discoveredTvs by vm.discoveredTvs.collectAsStateWithLifecycle()
    val savedTvs by vm.savedTvs.collectAsStateWithLifecycle()
    val tvFavorites by vm.tvFavorites.collectAsStateWithLifecycle()
    val hapticEnabled by vm.hapticEnabled.collectAsStateWithLifecycle()
    val hardwareVolume by vm.hardwareVolumeEnabled.collectAsStateWithLifecycle()
    val pairingTarget by vm.pairingTarget.collectAsStateWithLifecycle()
    val pairingError by vm.pairingError.collectAsStateWithLifecycle()

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Buttons, 1 = Touchpad
    var showNumpad by remember { mutableStateOf(false) }
    var showDevicePicker by remember { mutableStateOf(false) }
    var showManualConnect by remember { mutableStateOf(false) }
    var searchInput by remember { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    fun vibrate() {
        if (hapticEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .stageBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .focusRequester(focusRequester)
            .onKeyEvent { event ->
                if (hardwareVolume && event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.VolumeUp -> {
                            vibrate()
                            vm.sendMedia(RemoteMediaKey.VOLUME_UP)
                            true
                        }
                        Key.VolumeDown -> {
                            vibrate()
                            vm.sendMedia(RemoteMediaKey.VOLUME_DOWN)
                            true
                        }
                        Key.VolumeMute -> {
                            vibrate()
                            vm.sendMedia(RemoteMediaKey.VOLUME_MUTE)
                            true
                        }
                        else -> false
                    }
                } else false
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: Back, Status pill, TV picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StagePill(
                    onClick = onBack,
                    icon = MobileIcons.ArrowBack,
                    contentDescription = stringResource(R.string.remote_nav_back),
                )

                val statusLabel = when (val s = status) {
                    is ConnectionStatus.Connected -> s.tvName
                    is ConnectionStatus.Connecting -> stringResource(R.string.remote_connecting)
                    is ConnectionStatus.Disconnected -> stringResource(R.string.remote_status_disconnected)
                    is ConnectionStatus.Error -> stringResource(R.string.remote_status_disconnected)
                }
                val statusIcon = when (status) {
                    is ConnectionStatus.Connected -> MobileIcons.Check
                    is ConnectionStatus.Connecting -> MobileIcons.Sync
                    else -> MobileIcons.Refresh
                }

                StagePill(
                    onClick = {
                        if (status !is ConnectionStatus.Connected) {
                            vm.retryConnection()
                        } else {
                            showDevicePicker = true
                        }
                    },
                    icon = statusIcon,
                    text = statusLabel,
                )

                StagePill(
                    onClick = { showDevicePicker = true },
                    icon = MobileIcons.Tv,
                    contentDescription = stringResource(R.string.remote_device_select),
                )
            }

            // Mode Selector: Buttons vs Touchpad
            StageSegmented(
                options = listOf(
                    stringResource(R.string.remote_mode_dpad),
                    stringResource(R.string.remote_mode_touchpad),
                ),
                selected = activeTab,
                onSelect = { activeTab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                fill = true,
            )

            if (activeTab == 0) {
                // BUTTONS MODE (D-Pad + Controls)
                Spacer(Modifier.height(8.dp))

                // D-PAD CLUSTER
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        .background(palette.ink.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center,
                ) {
                    // UP
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(palette.controlFill)
                            .clickable {
                                vibrate()
                                vm.sendNav(RemoteNavKey.UP)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        StageIcon(MobileIcons.KeyboardArrowUp, 28.dp, palette.text)
                    }

                    // DOWN
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 10.dp)
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(palette.controlFill)
                            .clickable {
                                vibrate()
                                vm.sendNav(RemoteNavKey.DOWN)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        StageIcon(MobileIcons.KeyboardArrowDown, 28.dp, palette.text)
                    }

                    // LEFT
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 10.dp)
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(palette.controlFill)
                            .clickable {
                                vibrate()
                                vm.sendNav(RemoteNavKey.LEFT)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        StageIcon(MobileIcons.ArrowBack, 24.dp, palette.text)
                    }

                    // RIGHT
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 10.dp)
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(palette.controlFill)
                            .clickable {
                                vibrate()
                                vm.sendNav(RemoteNavKey.RIGHT)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        StageIcon(MobileIcons.KeyboardArrowRight, 24.dp, palette.text)
                    }

                    // CENTER OK
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(accent.accent)
                            .clickable {
                                vibrate()
                                vm.sendNav(RemoteNavKey.OK)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.remote_nav_ok),
                            style = stageText(16f, 800),
                            color = accent.onAccent,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // SYSTEM NAV ROW (Back, Home, Menu, 123)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StageButton(
                        onClick = {
                            vibrate()
                            vm.sendNav(RemoteNavKey.BACK)
                        },
                        icon = MobileIcons.ArrowBack,
                    )
                    StageButton(
                        onClick = {
                            vibrate()
                            vm.sendNav(RemoteNavKey.HOME)
                        },
                        icon = MobileIcons.Home,
                    )
                    StageButton(
                        onClick = {
                            vibrate()
                            vm.sendNav(RemoteNavKey.MENU)
                        },
                        icon = MobileIcons.MoreHoriz,
                    )
                    StageButton(
                        onClick = {
                            vibrate()
                            showNumpad = !showNumpad
                        },
                        text = stringResource(R.string.remote_numpad),
                        style = if (showNumpad) StageButtonStyle.PRIMARY else StageButtonStyle.SECONDARY,
                    )
                }

                // NUMPAD (COLLAPSIBLE)
                AnimatedVisibility(visible = showNumpad) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val rows = listOf(
                            listOf(1, 2, 3),
                            listOf(4, 5, 6),
                            listOf(7, 8, 9),
                            listOf(-1, 0, -2),
                        )
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                            ) {
                                row.forEach { num ->
                                    if (num >= 0) {
                                        StageButton(
                                            onClick = {
                                                vibrate()
                                                vm.sendNumber(num)
                                            },
                                            text = num.toString(),
                                            modifier = Modifier.width(64.dp),
                                        )
                                    } else {
                                        Spacer(Modifier.width(64.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // PLAYBACK CONTROLS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StageButton(
                        onClick = {
                            vibrate()
                            vm.sendMedia(RemoteMediaKey.REWIND_10)
                        },
                        text = stringResource(R.string.remote_rewind_10),
                    )
                    StageButton(
                        onClick = {
                            vibrate()
                            vm.sendMedia(RemoteMediaKey.PLAY_PAUSE)
                        },
                        icon = if (tvState.isPlaying) MobileIcons.Pause else MobileIcons.PlayArrow,
                        style = StageButtonStyle.PRIMARY,
                    )
                    StageButton(
                        onClick = {
                            vibrate()
                            vm.sendMedia(RemoteMediaKey.FORWARD_10)
                        },
                        text = stringResource(R.string.remote_forward_10),
                    )
                }

                Spacer(Modifier.height(10.dp))

                // REWIND 60s / FORWARD 60s / TO LIVE / ARCHIVE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StageButton(
                        onClick = {
                            vibrate()
                            vm.sendMedia(RemoteMediaKey.REWIND_60)
                        },
                        text = stringResource(R.string.remote_rewind_60),
                        small = true,
                    )
                    StageButton(
                        onClick = {
                            vibrate()
                            vm.sendMedia(RemoteMediaKey.TO_LIVE)
                        },
                        text = stringResource(R.string.remote_to_live),
                        small = true,
                    )
                    StageButton(
                        onClick = {
                            vibrate()
                            vm.sendMedia(RemoteMediaKey.ARCHIVE)
                        },
                        text = stringResource(R.string.remote_archive),
                        small = true,
                    )
                    StageButton(
                        onClick = {
                            vibrate()
                            vm.sendMedia(RemoteMediaKey.FORWARD_60)
                        },
                        text = stringResource(R.string.remote_forward_60),
                        small = true,
                    )
                }

                Spacer(Modifier.height(14.dp))

                // VOLUME & CHANNEL CONTROLS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    // Volume Group
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StageButton(
                            onClick = {
                                vibrate()
                                vm.sendMedia(RemoteMediaKey.VOLUME_UP)
                            },
                            icon = MobileIcons.VolumeUp,
                        )
                        StageButton(
                            onClick = {
                                vibrate()
                                vm.sendMedia(RemoteMediaKey.VOLUME_MUTE)
                            },
                            icon = MobileIcons.VolumeOff,
                            small = true,
                        )
                        StageButton(
                            onClick = {
                                vibrate()
                                vm.sendMedia(RemoteMediaKey.VOLUME_DOWN)
                            },
                            icon = MobileIcons.VolumeUp,
                        )
                    }

                    // Channel Group
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        StageButton(
                            onClick = {
                                vibrate()
                                vm.sendMedia(RemoteMediaKey.CHANNEL_UP)
                            },
                            text = stringResource(R.string.settings_remote_button_channel_up),
                        )
                        Spacer(Modifier.height(34.dp))
                        StageButton(
                            onClick = {
                                vibrate()
                                vm.sendMedia(RemoteMediaKey.CHANNEL_DOWN)
                            },
                            text = stringResource(R.string.settings_remote_button_channel_down),
                        )
                    }
                }
            } else {
                // TOUCHPAD MODE
                Spacer(Modifier.height(12.dp))

                var totalDragX by remember { mutableFloatStateOf(0f) }
                var totalDragY by remember { mutableFloatStateOf(0f) }
                val swipeThreshold = 65f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(palette.ink.copy(alpha = 0.06f))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    vibrate()
                                    vm.sendNav(RemoteNavKey.OK)
                                },
                                onLongPress = {
                                    vibrate()
                                    vm.sendNav(RemoteNavKey.MENU)
                                },
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    totalDragX = 0f
                                    totalDragY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDragX += dragAmount.x
                                    totalDragY += dragAmount.y

                                    if (abs(totalDragX) > swipeThreshold || abs(totalDragY) > swipeThreshold) {
                                        if (abs(totalDragX) > abs(totalDragY)) {
                                            if (totalDragX > 0) {
                                                vibrate()
                                                vm.sendNav(RemoteNavKey.RIGHT)
                                            } else {
                                                vibrate()
                                                vm.sendNav(RemoteNavKey.LEFT)
                                            }
                                        } else {
                                            if (totalDragY > 0) {
                                                vibrate()
                                                vm.sendNav(RemoteNavKey.DOWN)
                                            } else {
                                                vibrate()
                                                vm.sendNav(RemoteNavKey.UP)
                                            }
                                        }
                                        totalDragX = 0f
                                        totalDragY = 0f
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StageIcon(MobileIcons.Tune, 36.dp, palette.dim)
                        Text(
                            text = stringResource(R.string.remote_touchpad_hint),
                            style = stageText(13f, 500),
                            color = palette.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StageButton(
                        onClick = {
                            vibrate()
                            vm.sendNav(RemoteNavKey.BACK)
                        },
                        icon = MobileIcons.ArrowBack,
                    )
                    StageButton(
                        onClick = {
                            vibrate()
                            vm.sendNav(RemoteNavKey.HOME)
                        },
                        icon = MobileIcons.Home,
                    )
                    StageButton(
                        onClick = {
                            vibrate()
                            vm.sendMedia(RemoteMediaKey.PLAY_PAUSE)
                        },
                        icon = if (tvState.isPlaying) MobileIcons.Pause else MobileIcons.PlayArrow,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // TEXT ENTRY ROW (Search on TV)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StageSearchField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    placeholder = stringResource(R.string.remote_send_text_hint),
                    modifier = Modifier.weight(1f),
                    onSearch = {
                        if (searchInput.isNotBlank()) {
                            vibrate()
                            vm.sendText(searchInput)
                            searchInput = ""
                        }
                    },
                    imeAction = ImeAction.Send,
                )
                StageButton(
                    onClick = {
                        if (searchInput.isNotBlank()) {
                            vibrate()
                            vm.sendText(searchInput)
                            searchInput = ""
                        }
                    },
                    text = stringResource(R.string.remote_send_text_button),
                    style = StageButtonStyle.PRIMARY,
                    small = true,
                )
            }

            Spacer(Modifier.height(16.dp))

            // NOW PLAYING MINI-PLAYER (if TV is playing)
            val currentTitle = tvState.title ?: tvState.channelName
            if (!currentTitle.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.ink.copy(alpha = 0.07f))
                        .padding(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.remote_now_playing),
                                    style = stageText(11.5f, 500),
                                    color = palette.muted,
                                )
                                Text(
                                    text = currentTitle,
                                    style = stageText(14.5f, 700),
                                    color = palette.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            StageButton(
                                onClick = {
                                    vibrate()
                                    vm.sendMedia(RemoteMediaKey.PLAY_PAUSE)
                                },
                                icon = if (tvState.isPlaying) MobileIcons.Pause else MobileIcons.PlayArrow,
                                small = true,
                            )
                        }

                        if (tvState.durationMs > 0L) {
                            val progress = (tvState.positionMs.toFloat() / tvState.durationMs.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = accent.accent,
                                trackColor = palette.ink.copy(alpha = 0.1f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            var link by remember { mutableStateOf("") }
            OutlinedTextField(
                value = link, onValueChange = { link = it.take(2048) },
                label = { Text(stringResource(R.string.remote_link)) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            StageButton(
                onClick = { if (RemoteSecurity.shareableLink(link)) vm.sendTune(streamUrl = link) },
                text = stringResource(R.string.remote_send_to_tv),
                enabled = RemoteSecurity.shareableLink(link),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    // PAIRING DIALOG
    if (pairingTarget != null) {
        var pinInput by remember { mutableStateOf("") }
        var fingerprint by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.dismissPairingDialog() },
            title = {
                Text(
                    text = stringResource(R.string.remote_pairing_code),
                    style = stageText(18f, 700),
                    color = palette.text,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.remote_enter_pin),
                        style = stageText(13.5f, 500),
                        color = palette.muted,
                    )
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = fingerprint,
                        onValueChange = { fingerprint = it.lowercase().filter { c -> c in "0123456789abcdef" }.take(64) },
                        label = { Text(stringResource(R.string.remote_certificate)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (pairingError != null) {
                        val errorMsg = when (pairingError) {
                            "LOCKED" -> stringResource(R.string.remote_pin_locked)
                            "INVALID" -> stringResource(R.string.remote_pin_incorrect)
                            else -> pairingError.orEmpty()
                        }
                        Text(
                            text = errorMsg,
                            style = stageText(12.5f, 600),
                            color = palette.danger,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pinInput.length == 6) {
                            vm.submitPairing(pinInput, fingerprint)
                        }
                    },
                    enabled = pinInput.length == 6 && fingerprint.length == 64,
                ) {
                    Text(stringResource(R.string.remote_pair_button), color = accent.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissPairingDialog() }) {
                    Text(stringResource(R.string.remote_cancel), color = palette.muted)
                }
            },
        )
    }

    // DEVICE PICKER DIALOG
    if (showDevicePicker) {
        AlertDialog(
            onDismissRequest = { showDevicePicker = false },
            title = {
                Text(
                    text = stringResource(R.string.remote_device_select),
                    style = stageText(18f, 700),
                    color = palette.text,
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Saved TVs
                    if (savedTvs.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.remote_paired_devices),
                            style = stageText(13f, 700),
                            color = palette.text,
                        )
                        savedTvs.forEach { tv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selectedTv?.id == tv.id) accent.accent.copy(alpha = 0.15f) else palette.controlFill)
                                    .clickable {
                                        vm.selectTv(tv)
                                        showDevicePicker = false
                                    }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = tv.name,
                                    style = stageText(14f, 600),
                                    color = palette.text,
                                )
                                StageButton(
                                    onClick = { vm.forgetDevice(tv.id) },
                                    text = stringResource(R.string.remote_forget_device),
                                    small = true,
                                    style = StageButtonStyle.DANGER,
                                )
                            }
                        }
                    }

                    // Discovered TVs on Wi-Fi
                    Text(
                        text = stringResource(R.string.remote_title),
                        style = stageText(13f, 700),
                        color = palette.text,
                    )
                    if (discoveredTvs.isEmpty()) {
                        Text(
                            text = stringResource(R.string.remote_status_disconnected),
                            style = stageText(12.5f, 500),
                            color = palette.muted,
                        )
                    } else {
                        discoveredTvs.forEach { tv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.controlFill)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(text = tv.name, style = stageText(14f, 600), color = palette.text)
                                    Text(text = "${tv.host}:${tv.port}", style = stageText(11.5f, 400), color = palette.dim)
                                }
                                StageButton(
                                    onClick = {
                                        showDevicePicker = false
                                        vm.showPairingDialog(tv)
                                    },
                                    text = stringResource(R.string.remote_pair_button),
                                    small = true,
                                    style = StageButtonStyle.PRIMARY,
                                )
                            }
                        }
                    }

                    // Manual connect button
                    StageButton(
                        onClick = {
                            showDevicePicker = false
                            showManualConnect = true
                        },
                        text = stringResource(R.string.remote_add_manual),
                        icon = MobileIcons.Add,
                        modifier = Modifier.fillMaxWidth(),
                        small = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDevicePicker = false }) {
                    Text(stringResource(R.string.remote_cancel), color = palette.muted)
                }
            },
        )
    }

    // MANUAL CONNECT DIALOG
    if (showManualConnect) {
        var ipInput by remember { mutableStateOf("") }
        var portInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showManualConnect = false },
            title = {
                Text(
                    text = stringResource(R.string.remote_add_manual),
                    style = stageText(18f, 700),
                    color = palette.text,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it.trim() },
                        label = { Text(stringResource(R.string.remote_enter_ip)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it.trim() },
                        label = { Text(stringResource(R.string.remote_enter_port)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val portNum = portInput.toIntOrNull() ?: 8080
                        if (ipInput.isNotBlank()) {
                            showManualConnect = false
                            vm.showPairingDialog(
                                RemoteDiscoveryInfo(
                                    name = "TV ($ipInput)",
                                    host = ipInput,
                                    port = portNum,
                                ),
                            )
                        }
                    },
                    enabled = ipInput.isNotBlank(),
                ) {
                    Text(stringResource(R.string.remote_pair_button), color = accent.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualConnect = false }) {
                    Text(stringResource(R.string.remote_cancel), color = palette.muted)
                }
            },
        )
    }
}
