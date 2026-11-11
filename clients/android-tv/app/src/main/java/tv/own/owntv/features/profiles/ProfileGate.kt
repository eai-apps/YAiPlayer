package tv.own.owntv.features.profiles

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.ui.components.OwnTVAvatar
import tv.own.owntv.ui.components.OwnTVIcon
import tv.own.owntv.ui.stage.StageConfirm
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.stage.StagePopupOption
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx

/**
 * The "Who's watching?" launch gate. Shown when more than one profile exists. Picking a
 * profile makes it active (and prompts for a PIN if it's locked); [onEnter] records the selected
 * profile id and proceeds into the shell.
 */
@Composable
fun ProfileGate(onEnter: (Long) -> Unit, onAddProfile: () -> Unit, modifier: Modifier = Modifier) {
    val vm: ProfilesViewModel = koinViewModel()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val hiddenProfileIds by vm.hiddenProfileIds.collectAsStateWithLifecycle()

    val guestId by vm.guestProfileId.collectAsStateWithLifecycle()
    val guestName = stringResource(R.string.profiles_guest_name)

    val visibleProfiles = remember(profiles, hiddenProfileIds, guestId) {
        profiles.filter { it.id == guestId || it.id !in hiddenProfileIds }
    }

    var menuProfile by remember { mutableStateOf<ProfileEntity?>(null) }
    var deleteConfirmProfile by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinForEnter by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinForDelete by remember { mutableStateOf<ProfileEntity?>(null) }
    var pinError by remember { mutableStateOf(false) }
    // Radio (APK / RuStore build only): a tile next to Guest; Back returns here.
    var showRadio by remember { mutableStateOf(false) }
    if (showRadio) {
        tv.own.owntv.features.radio.RadioFeature.Screen(onBack = { showRadio = false })
        return
    }

    val profileFocus = remember { mutableMapOf<Long, FocusRequester>() }
    val addFocus = remember { FocusRequester() }
    var lastFocusedId by remember { mutableStateOf<Long?>(null) }
    var returnFocusTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(visibleProfiles.isNotEmpty()) {
        if (visibleProfiles.isNotEmpty()) {
            val target = lastFocusedId?.let { profileFocus[it] } ?: profileFocus.getOrPut(visibleProfiles.first().id) { FocusRequester() }
            runCatching { target.requestFocus() }
        }
    }

    LaunchedEffect(returnFocusTrigger) {
        if (returnFocusTrigger > 0) {
            val target = lastFocusedId?.takeIf { id -> visibleProfiles.any { it.id == id } }?.let { profileFocus[it] }
                ?: visibleProfiles.firstOrNull()?.let { profileFocus[it.id] }
                ?: addFocus
            runCatching { target.requestFocus() }
        }
    }

    fun choose(p: ProfileEntity) {
        lastFocusedId = p.id
        if (p.id == guestId) {
            if (p.pinHash != null) {
                pinError = false
                pinForEnter = p
            } else {
                vm.switchTo(p) { onEnter(p.id) }
            }
        } else {
            menuProfile = p
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(tv.own.owntv.ui.theme.LocalStagePalette.current.page),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            tv.own.owntv.ui.components.BrandLockup(markSize = 48)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.profiles_gate_title), style = tv.own.owntv.ui.theme.stageText(54, 700), color = StageColors.Text)
            Spacer(Modifier.height(36.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.focusGroup().padding(horizontal = 48.dp),
            ) {
                items(visibleProfiles, key = { it.id }) { p ->
                    val requester = profileFocus.getOrPut(p.id) { FocusRequester() }
                    ProfileTile(
                        profile = p,
                        isGuest = p.id == guestId,
                        modifier = Modifier.focusRequester(requester),
                        onClick = { choose(p) },
                    )
                }
                item { AddTile(onClick = onAddProfile, modifier = Modifier.focusRequester(addFocus)) }
                // "No registration, no profile": shown until the guest profile exists (then it is a tile above).
                if (visibleProfiles.none { it.id == guestId }) {
                    item { AddTile(onClick = { vm.enterGuest(guestName, onEnter) }, label = guestName, icon = OwnTVIcon.PERSON) }
                }
                if (tv.own.owntv.features.radio.RadioFeature.AVAILABLE) {
                    item { AddTile(onClick = { showRadio = true }, label = tv.own.owntv.features.radio.RadioFeature.label(), icon = OwnTVIcon.EQ) }
                }
            }
        }
    }

    menuProfile?.let { p ->
        StagePopup(
            onDismiss = {
                menuProfile = null
                returnFocusTrigger++
            },
            title = p.name,
            eyebrow = stringResource(R.string.profiles_gate_title),
            width = 560.mpx,
        ) {
            StagePopupOption(
                title = stringResource(R.string.profiles_menu_enter),
                leading = { OwnTVIcon(OwnTVIcon.PLAY, tint = StageColors.Text, modifier = Modifier.size(20.mpx)) },
                onClick = {
                    menuProfile = null
                    if (p.pinHash != null) {
                        pinError = false
                        pinForEnter = p
                    } else {
                        vm.switchTo(p) { onEnter(p.id) }
                    }
                },
            )
            StagePopupOption(
                title = stringResource(R.string.profiles_menu_hide),
                leading = { OwnTVIcon(OwnTVIcon.EYE_OFF, tint = StageColors.Text, modifier = Modifier.size(20.mpx)) },
                onClick = {
                    menuProfile = null
                    vm.hideProfile(p.id)
                    returnFocusTrigger++
                },
            )
            StagePopupOption(
                title = stringResource(R.string.common_delete),
                danger = true,
                leading = { OwnTVIcon(OwnTVIcon.TRASH, tint = StageColors.Danger, modifier = Modifier.size(20.mpx)) },
                onClick = {
                    menuProfile = null
                    deleteConfirmProfile = p
                },
            )
        }
    }

    deleteConfirmProfile?.let { p ->
        StageConfirm(
            title = stringResource(R.string.profiles_delete_confirm_title, p.name),
            body = stringResource(R.string.profiles_delete_confirm_message),
            confirm = stringResource(R.string.common_delete),
            cancel = stringResource(R.string.common_cancel),
            focusCancel = true,
            onConfirm = {
                deleteConfirmProfile = null
                if (p.pinHash != null) {
                    pinError = false
                    pinForDelete = p
                } else {
                    vm.delete(p)
                    returnFocusTrigger++
                }
            },
            onCancel = {
                deleteConfirmProfile = null
                returnFocusTrigger++
            },
        )
    }

    pinForEnter?.let { p ->
        PinDialog(
            title = if (pinError) stringResource(R.string.profiles_wrong_pin) else stringResource(R.string.profiles_enter_pin, p.name),
            onSubmit = { pin ->
                if (vm.verifyPin(p, pin)) {
                    pinForEnter = null
                    vm.switchTo(p) { onEnter(p.id) }
                } else pinError = true
            },
            onDismiss = {
                pinForEnter = null
                returnFocusTrigger++
            },
        )
    }

    pinForDelete?.let { p ->
        PinDialog(
            title = if (pinError) stringResource(R.string.profiles_wrong_pin) else stringResource(R.string.profiles_enter_pin, p.name),
            onSubmit = { pin ->
                if (vm.verifyPin(p, pin)) {
                    pinForDelete = null
                    vm.delete(p)
                    returnFocusTrigger++
                } else pinError = true
            },
            onDismiss = {
                pinForDelete = null
                returnFocusTrigger++
            },
        )
    }
}

@Composable
private fun ProfileTile(profile: ProfileEntity, isGuest: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val amber = androidx.compose.ui.graphics.Color(0xFFF59E0B)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        tv.own.owntv.ui.stage.StageSurface(
            onClick = onClick,
            radius = 24.dp,
            modifier = modifier.size(132.dp),
            focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
            contentAlignment = Alignment.Center,
        ) { _ ->
            Box(contentAlignment = Alignment.TopEnd) {
                val avatarModifier = if (isGuest) {
                    Modifier.size(104.dp).clip(CircleShape).border(2.5.dp, amber, CircleShape)
                } else {
                    Modifier.size(104.dp)
                }
                OwnTVAvatar(avatarId = profile.avatarId, imagePath = profile.avatarPath.orEmpty(), modifier = avatarModifier)
                if (profile.pinHash != null) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(tv.own.owntv.ui.theme.StageColors.Ink.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        OwnTVIcon(OwnTVIcon.SETTINGS, tint = tv.own.owntv.ui.theme.StageColors.Text, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(profile.name, style = tv.own.owntv.ui.theme.stageText(32, 600), color = tv.own.owntv.ui.theme.StageColors.Text, textAlign = TextAlign.Center)
        if (isGuest) {
            Text(stringResource(R.string.profiles_guest_name), style = tv.own.owntv.ui.theme.stageText(20, 600), color = amber)
        } else if (profile.isKids) {
            Text(stringResource(R.string.profiles_kids_badge), style = tv.own.owntv.ui.theme.stageText(20, 500), color = tv.own.owntv.ui.theme.stageAccent.accent)
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit, label: String = stringResource(R.string.profiles_add), icon: OwnTVIcon = OwnTVIcon.ADD, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        tv.own.owntv.ui.stage.StageSurface(
            onClick = onClick,
            radius = 24.dp,
            modifier = modifier.size(132.dp),
            focusStyle = tv.own.owntv.ui.stage.StageFocus.FX,
            contentAlignment = Alignment.Center,
        ) { _ ->
            Box(
                modifier = Modifier.size(104.dp).clip(CircleShape).background(tv.own.owntv.ui.theme.StageColors.Ink.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                OwnTVIcon(icon, tint = tv.own.owntv.ui.theme.StageColors.Muted, modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = tv.own.owntv.ui.theme.stageText(32, 600), color = tv.own.owntv.ui.theme.StageColors.Muted)
    }
}
