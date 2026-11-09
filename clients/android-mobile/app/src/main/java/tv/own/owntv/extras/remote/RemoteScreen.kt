// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.remote

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageChoiceRow
import tv.own.owntv.mobile.ui.stage.StageField
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.stageBackground
import tv.own.owntv.mobile.ui.stage.stageClickable
import tv.own.owntv.mobile.ui.stage.stageText

@Composable
fun RemoteScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    entitlement: RemoteEntitlement = DefaultRemoteEntitlement,
) {
    if (!entitlement.canUseRemote()) {
        Box(modifier.fillMaxSize().stageBackground(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.remote_title), style = stageText(18f, 600))
        }
        return
    }

    val context = LocalContext.current
    val palette = LocalStagePalette.current
    val scope = rememberCoroutineScope()
    val client = remember { RemoteClient(context.applicationContext) }

    val discoveredDevices by client.discoveredDevices.collectAsStateWithLifecycle()
    var selectedDevice by remember { mutableStateOf<RemoteDevice?>(null) }
    var deviceToken by remember { mutableStateOf<String?>(null) }
    var tvState by remember { mutableStateOf<RemoteState?>(null) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var searchTextInput by remember { mutableStateOf("") }
    var selectedModeIndex by remember { mutableIntStateOf(0) }

    val prefs = remember { context.getSharedPreferences("tv_phone_remote_tokens", Context.MODE_PRIVATE) }

    DisposableEffect(Unit) {
        client.startDiscovery()
        onDispose {
            client.stopDiscovery()
        }
    }

    LaunchedEffect(discoveredDevices) {
        if (selectedDevice == null && discoveredDevices.isNotEmpty()) {
            val first = discoveredDevices.first()
            selectedDevice = first
            deviceToken = prefs.getString("token_${first.id}", null)
        }
    }

    LaunchedEffect(selectedDevice, deviceToken) {
        val dev = selectedDevice ?: return@LaunchedEffect
        val tok = deviceToken ?: return@LaunchedEffect
        while (true) {
            val state = client.fetchState(dev, tok)
            if (state != null) {
                tvState = state
            }
            delay(2000)
        }
    }

    fun sendCmd(command: RemoteCommand) {
        val dev = selectedDevice ?: return
        val tok = deviceToken ?: return
        scope.launch {
            client.sendCommand(dev, tok, command)
        }
    }

    val modeOptions = listOf(
        stringResource(R.string.remote_mode_buttons),
        stringResource(R.string.remote_mode_touchpad),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .stageBackground()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.stageClickable(onClick = onBack).padding(8.dp),
            ) {
                StageIcon(MobileIcons.ArrowBack, contentDescription = null, tint = palette.text, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.remote_title),
                    style = stageText(20f, 700),
                    color = palette.text,
                )
            }
            StageButton(
                icon = MobileIcons.Refresh,
                onClick = {
                    client.stopDiscovery()
                    client.startDiscovery()
                },
                style = StageButtonStyle.SECONDARY,
                small = true,
            )
        }

        if (discoveredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.card)
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.remote_no_devices_found),
                    style = stageText(14f, 500),
                    color = palette.muted,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(16.dp))
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                discoveredDevices.forEach { dev ->
                    val isSelected = dev.id == selectedDevice?.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) palette.accent else palette.card)
                            .stageClickable {
                                selectedDevice = dev
                                deviceToken = prefs.getString("token_${dev.id}", null)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = dev.name,
                            style = stageText(13f, 600),
                            color = if (isSelected) Color.White else palette.text,
                        )
                    }
                }
            }
        }

        val activeDev = selectedDevice
        val activeToken = deviceToken

        if (activeDev != null && activeToken == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.card)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.remote_enter_pin_title),
                    style = stageText(16f, 700),
                    color = palette.text,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.remote_pairing_pin_desc),
                    style = stageText(13f, 400),
                    color = palette.muted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                StageField(
                    value = pinInput,
                    onValueChange = { pinInput = it.filter { c -> c.isDigit() }.take(6) },
                    placeholder = stringResource(R.string.remote_pin_placeholder),
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
                pinError?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(err, style = stageText(12f, 500), color = palette.danger)
                }
                Spacer(Modifier.height(16.dp))
                StageButton(
                    text = stringResource(R.string.remote_enter_pin_btn),
                    onClick = {
                        scope.launch {
                            val res = client.pair(activeDev, pinInput)
                            when (res) {
                                is PairingResult.Success -> {
                                    prefs.edit().putString("token_${activeDev.id}", res.token).apply()
                                    deviceToken = res.token
                                    pinError = null
                                }
                                PairingResult.InvalidPin -> {
                                    pinError = context.getString(R.string.remote_pair_failed)
                                }
                                PairingResult.Blocked -> {
                                    pinError = context.getString(R.string.remote_pair_blocked)
                                }
                                PairingResult.NetworkError -> {
                                    pinError = context.getString(R.string.remote_device_disconnected)
                                }
                            }
                        }
                    },
                    style = StageButtonStyle.PRIMARY,
                    enabled = pinInput.length == 6,
                )
            }
            Spacer(Modifier.height(20.dp))
        } else if (activeDev != null && activeToken != null) {
            StageChoiceRow(
                options = modeOptions,
                selected = selectedModeIndex,
                onSelect = { selectedModeIndex = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )

            if (selectedModeIndex == 1) {
                var totalDragX by remember { mutableFloatStateOf(0f) }
                var totalDragY by remember { mutableFloatStateOf(0f) }
                val threshold = 40f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(palette.card)
                        .pointerInput(activeDev, activeToken) {
                            detectTapGestures(
                                onTap = { sendCmd(RemoteCommand.Ok) },
                            )
                        }
                        .pointerInput(activeDev, activeToken) {
                            detectDragGestures(
                                onDragEnd = {
                                    totalDragX = 0f
                                    totalDragY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    totalDragX += dragAmount.x
                                    totalDragY += dragAmount.y
                                    if (kotlin.math.abs(totalDragX) > threshold) {
                                        if (totalDragX > 0) sendCmd(RemoteCommand.Right) else sendCmd(RemoteCommand.Left)
                                        totalDragX = 0f
                                    }
                                    if (kotlin.math.abs(totalDragY) > threshold) {
                                        if (totalDragY > 0) sendCmd(RemoteCommand.Down) else sendCmd(RemoteCommand.Up)
                                        totalDragY = 0f
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.remote_touchpad_hint),
                        style = stageText(14f, 500),
                        color = palette.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    StageButton(
                        icon = MobileIcons.KeyboardArrowUp,
                        onClick = { sendCmd(RemoteCommand.Up) },
                        style = StageButtonStyle.SECONDARY,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StageButton(
                            icon = MobileIcons.ArrowBack,
                            onClick = { sendCmd(RemoteCommand.Left) },
                            style = StageButtonStyle.SECONDARY,
                            modifier = Modifier.size(56.dp),
                        )
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(palette.accent)
                                .stageClickable { sendCmd(RemoteCommand.Ok) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(stringResource(R.string.common_ok), style = stageText(15f, 700), color = Color.White)
                        }
                        StageButton(
                            icon = MobileIcons.KeyboardArrowRight,
                            onClick = { sendCmd(RemoteCommand.Right) },
                            style = StageButtonStyle.SECONDARY,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    StageButton(
                        icon = MobileIcons.KeyboardArrowDown,
                        onClick = { sendCmd(RemoteCommand.Down) },
                        style = StageButtonStyle.SECONDARY,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StageButton(
                    icon = MobileIcons.ArrowBack,
                    text = stringResource(R.string.remote_btn_back),
                    onClick = { sendCmd(RemoteCommand.Back) },
                    style = StageButtonStyle.SECONDARY,
                    small = true,
                )
                StageButton(
                    icon = MobileIcons.Home,
                    text = stringResource(R.string.remote_btn_home),
                    onClick = { sendCmd(RemoteCommand.Home) },
                    style = StageButtonStyle.SECONDARY,
                    small = true,
                )
                StageButton(
                    icon = MobileIcons.MoreHoriz,
                    text = stringResource(R.string.remote_btn_menu),
                    onClick = { sendCmd(RemoteCommand.Menu) },
                    style = StageButtonStyle.SECONDARY,
                    small = true,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StageButton(icon = MobileIcons.FastRewind, text = stringResource(R.string.remote_btn_rewind_10), onClick = { sendCmd(RemoteCommand.Rewind10) }, small = true)
                StageButton(
                    icon = if (tvState?.isPlaying == false) MobileIcons.PlayArrow else MobileIcons.Pause,
                    onClick = { sendCmd(RemoteCommand.PlayPause) },
                    style = StageButtonStyle.PRIMARY,
                    small = true,
                )
                StageButton(icon = MobileIcons.FastForward, text = stringResource(R.string.remote_btn_forward_10), onClick = { sendCmd(RemoteCommand.Forward10) }, small = true)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StageButton(icon = MobileIcons.VolumeOff, onClick = { sendCmd(RemoteCommand.VolumeDown) }, small = true)
                StageButton(text = stringResource(R.string.remote_btn_mute), onClick = { sendCmd(RemoteCommand.VolumeMute) }, small = true)
                StageButton(icon = MobileIcons.VolumeUp, onClick = { sendCmd(RemoteCommand.VolumeUp) }, small = true)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StageButton(icon = MobileIcons.SkipPrevious, text = stringResource(R.string.remote_btn_channel_prev), onClick = { sendCmd(RemoteCommand.ChannelPrev) }, small = true)
                StageButton(icon = MobileIcons.SkipNext, text = stringResource(R.string.remote_btn_channel_next), onClick = { sendCmd(RemoteCommand.ChannelNext) }, small = true)
            }

            Spacer(Modifier.height(16.dp))

            // Digits Grid (0-9)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { d ->
                        StageButton(text = d.toString(), onClick = { sendCmd(RemoteCommand.Digit(d)) }, small = true)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (6..9).forEach { d ->
                        StageButton(text = d.toString(), onClick = { sendCmd(RemoteCommand.Digit(d)) }, small = true)
                    }
                    StageButton(text = 0.toString(), onClick = { sendCmd(RemoteCommand.Digit(0)) }, small = true)
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StageField(
                    value = searchTextInput,
                    onValueChange = { searchTextInput = it },
                    placeholder = stringResource(R.string.remote_text_input_hint),
                    modifier = Modifier.weight(1f),
                )
                StageButton(
                    text = stringResource(R.string.remote_send_text_btn),
                    onClick = {
                        if (searchTextInput.isNotBlank()) {
                            sendCmd(RemoteCommand.InsertText(searchTextInput))
                            searchTextInput = ""
                        }
                    },
                    style = StageButtonStyle.PRIMARY,
                    small = true,
                )
            }

            Spacer(Modifier.height(20.dp))

            StageButton(
                text = stringResource(R.string.remote_export_log_btn),
                onClick = {
                    scope.launch {
                        val report = client.exportLog(activeDev, activeToken)
                        if (report != null) {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.remote_report_subject))
                                putExtra(Intent.EXTRA_TEXT, report)
                            }
                            context.startActivity(
                                Intent.createChooser(sendIntent, context.getString(R.string.remote_export_log_btn))
                            )
                        }
                    }
                },
                style = StageButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            StageButton(
                text = stringResource(R.string.remote_unpair),
                onClick = {
                    prefs.edit().remove("token_${activeDev.id}").apply()
                    deviceToken = null
                },
                style = StageButtonStyle.DANGER,
                small = true,
            )
        }
    }
}
