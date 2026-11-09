// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.own.owntv.core.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StageIcon
import tv.own.owntv.mobile.ui.stage.StageProgress
import tv.own.owntv.mobile.ui.stage.StageRadii
import tv.own.owntv.mobile.ui.stage.StageSearchField
import tv.own.owntv.mobile.ui.stage.StageSwitch
import tv.own.owntv.mobile.ui.stage.StageTag
import tv.own.owntv.mobile.ui.stage.StageTagTone
import tv.own.owntv.mobile.ui.stage.stageAccent
import tv.own.owntv.mobile.ui.stage.stageBackground
import tv.own.owntv.mobile.ui.stage.stageText

fun resolvePackageString(context: Context, key: String): String {
    val resId = when (key) {
        "pkg_youtube_tv_title" -> R.string.pkg_youtube_tv_title
        "pkg_youtube_tv_desc" -> R.string.pkg_youtube_tv_desc
        "pkg_play_games_title" -> R.string.pkg_play_games_title
        "pkg_play_games_desc" -> R.string.pkg_play_games_desc
        "pkg_patchwall_title" -> R.string.pkg_patchwall_title
        "pkg_patchwall_desc" -> R.string.pkg_patchwall_desc
        "pkg_xiaomi_upgrade_title" -> R.string.pkg_xiaomi_upgrade_title
        "pkg_xiaomi_upgrade_desc" -> R.string.pkg_xiaomi_upgrade_desc
        "pkg_sony_sync_title" -> R.string.pkg_sony_sync_title
        "pkg_sony_sync_desc" -> R.string.pkg_sony_sync_desc
        "pkg_tcl_manager_title" -> R.string.pkg_tcl_manager_title
        "pkg_tcl_manager_desc" -> R.string.pkg_tcl_manager_desc
        "pkg_print_spooler_title" -> R.string.pkg_print_spooler_title
        "pkg_print_spooler_desc" -> R.string.pkg_print_spooler_desc
        "pkg_google_music_title" -> R.string.pkg_google_music_title
        "pkg_google_music_desc" -> R.string.pkg_google_music_desc
        else -> runCatching {
            context.resources.getIdentifier(key, "string", context.packageName)
        }.getOrDefault(0)
    }
    return if (resId != 0) context.getString(resId) else key
}

@Composable
fun rememberTvOptimizerViewModel(
    context: Context = LocalContext.current,
): TvOptimizerViewModel {
    val repository = remember(context) { AssetTvOptimizerRepository(context.applicationContext) }
    val transport = remember { FakeAdbTransport() }
    val store = remember(context) { TvOptimizerStore(context.applicationContext) }

    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return TvOptimizerViewModel(repository, transport, store) as T
            }
        }
    )
}

@Composable
fun TvOptimizerScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    vm: TvOptimizerViewModel = rememberTvOptimizerViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val palette = LocalStagePalette.current
    val accent = stageAccent

    Column(
        modifier = modifier
            .fillMaxSize()
            .stageBackground()
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.currentStep.hasPrevious()) {
                    StageButton(
                        onClick = { vm.previousStep() },
                        icon = MobileIcons.ArrowBack,
                        style = StageButtonStyle.PLAIN,
                        small = true,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.tv_optimizer_title),
                    style = stageText(18f, 700),
                    color = palette.text,
                )
            }
            StageButton(
                onClick = onDismiss,
                icon = MobileIcons.Close,
                style = StageButtonStyle.PLAIN,
                small = true,
            )
        }

        // Body content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            when (state.currentStep) {
                TvOptimizerStep.WELCOME -> WelcomeStepView(
                    savedCount = state.savedDisabledPackages.size,
                    onStart = { vm.nextStep() },
                    onGoToRollback = { vm.goToRollback() },
                )
                TvOptimizerStep.DEBUG_GUIDE -> DebugGuideStepView(
                    onNext = { vm.nextStep() },
                    onBack = { vm.previousStep() },
                )
                TvOptimizerStep.CONNECT -> ConnectStepView(
                    state = state,
                    onHostChange = { vm.setAdbHost(it) },
                    onPortChange = { vm.setAdbPort(it) },
                    onConnect = { vm.connect() },
                    onNext = { vm.nextStep() },
                    onBack = { vm.previousStep() },
                )
                TvOptimizerStep.SELECT_MODEL -> SelectModelStepView(
                    state = state,
                    onSearchChange = { vm.setModelSearchQuery(it) },
                    onModelSelect = { vm.selectModel(it) },
                    onNext = { vm.nextStep() },
                    onBack = { vm.previousStep() },
                )
                TvOptimizerStep.SELECT_PACKAGES -> SelectPackagesStepView(
                    state = state,
                    onTogglePackage = { vm.togglePackageSelection(it) },
                    onSelectRecommended = { vm.selectRecommendedPackages() },
                    onSelectAll = { vm.selectAllPackages() },
                    onClearAll = { vm.clearPackageSelection() },
                    onNext = { vm.nextStep() },
                    onBack = { vm.previousStep() },
                )
                TvOptimizerStep.CONFIRM -> ConfirmStepView(
                    state = state,
                    onUnderstandToggle = { vm.setUnderstandRisk(it) },
                    onApply = { vm.nextStep() },
                    onBack = { vm.previousStep() },
                )
                TvOptimizerStep.APPLYING -> ApplyingStepView(state = state)
                TvOptimizerStep.RESULT -> ResultStepView(
                    state = state,
                    onRollbackAll = { vm.rollbackAll() },
                    onDone = onDismiss,
                )
                TvOptimizerStep.ROLLBACK -> RollbackStepView(
                    state = state,
                    onToggle = { vm.toggleRollbackPackage(it) },
                    onRollbackSelected = { vm.rollbackSelected() },
                    onRollbackAll = { vm.rollbackAll() },
                    onDone = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun WelcomeStepView(
    savedCount: Int,
    onStart: () -> Unit,
    onGoToRollback: () -> Unit,
) {
    val palette = LocalStagePalette.current
    val accent = stageAccent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(accent.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            StageIcon(MobileIcons.AutoAwesome, 40.dp, accent.accent)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.tv_optimizer_welcome_title),
            style = stageText(24f, 800),
            color = palette.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.tv_optimizer_welcome_body),
            style = stageText(14f, 400),
            color = palette.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(36.dp))
        StageButton(
            onClick = onStart,
            text = stringResource(R.string.tv_optimizer_start),
            style = StageButtonStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
        )
        if (savedCount > 0) {
            Spacer(Modifier.height(16.dp))
            StageButton(
                onClick = onGoToRollback,
                text = stringResource(R.string.tv_optimizer_rollback_past),
                style = StageButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DebugGuideStepView(
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalStagePalette.current
    var showHint by remember { mutableStateOf(false) }
    var expandedVendor by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.tv_optimizer_debug_title),
            style = stageText(20f, 700),
            color = palette.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.tv_optimizer_debug_subtitle),
            style = stageText(13f, 400),
            color = palette.muted,
        )
        Spacer(Modifier.height(16.dp))

        // Steps cards
        val steps = listOf(
            MobileIcons.Settings to stringResource(R.string.tv_optimizer_debug_step1),
            MobileIcons.Info to stringResource(R.string.tv_optimizer_debug_step2),
            MobileIcons.ArrowBack to stringResource(R.string.tv_optimizer_debug_step3),
            MobileIcons.Tune to stringResource(R.string.tv_optimizer_debug_step4),
            MobileIcons.Wifi to stringResource(R.string.tv_optimizer_debug_step5),
        )

        steps.forEachIndexed { index, (icon, text) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(StageRadii.Card))
                    .background(palette.card)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(palette.ink.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    StageIcon(icon, 18.dp, palette.accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = text,
                        style = stageText(13.5f, 500),
                        color = palette.text,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Vendor Accordions
        val vendors = listOf(
            "generic" to (stringResource(R.string.tv_optimizer_vendor_generic) to stringResource(R.string.tv_optimizer_vendor_generic_steps)),
            "xiaomi" to (stringResource(R.string.tv_optimizer_vendor_xiaomi) to stringResource(R.string.tv_optimizer_vendor_xiaomi_steps)),
            "sony" to (stringResource(R.string.tv_optimizer_vendor_sony) to stringResource(R.string.tv_optimizer_vendor_sony_steps)),
            "tcl" to (stringResource(R.string.tv_optimizer_vendor_tcl) to stringResource(R.string.tv_optimizer_vendor_tcl_steps)),
            "philips" to (stringResource(R.string.tv_optimizer_vendor_philips) to stringResource(R.string.tv_optimizer_vendor_philips_steps)),
            "google" to (stringResource(R.string.tv_optimizer_vendor_google) to stringResource(R.string.tv_optimizer_vendor_google_steps)),
        )

        vendors.forEach { (key, info) ->
            val isExpanded = expandedVendor == key
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(StageRadii.Card))
                    .background(palette.card)
                    .clickable { expandedVendor = if (isExpanded) null else key }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .animateContentSize(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = info.first,
                        style = stageText(13.5f, 600),
                        color = palette.text,
                    )
                    StageIcon(
                        if (isExpanded) MobileIcons.KeyboardArrowUp else MobileIcons.KeyboardArrowDown,
                        18.dp,
                        palette.muted,
                    )
                }
                if (isExpanded) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = info.second,
                        style = stageText(12f, 400),
                        color = palette.muted,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // "Can't find" button & hint
        StageButton(
            onClick = { showHint = !showHint },
            text = stringResource(R.string.tv_optimizer_cant_find),
            style = StageButtonStyle.PLAIN,
            small = true,
        )
        AnimatedVisibility(visible = showHint) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(StageRadii.Card))
                    .background(palette.ink.copy(alpha = 0.05f))
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.tv_optimizer_cant_find_hint),
                    style = stageText(12f, 400),
                    color = palette.muted,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StageButton(
                onClick = onBack,
                text = stringResource(R.string.tv_optimizer_nav_back),
                style = StageButtonStyle.SECONDARY,
                modifier = Modifier.weight(1f),
            )
            StageButton(
                onClick = onNext,
                text = stringResource(R.string.tv_optimizer_nav_next),
                style = StageButtonStyle.PRIMARY,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ConnectStepView(
    state: TvOptimizerUiState,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onConnect: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalStagePalette.current
    val isConnected = state.connectionStatus == ConnectionStatus.CONNECTED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.tv_optimizer_connect_title),
            style = stageText(20f, 700),
            color = palette.text,
        )
        Spacer(Modifier.height(16.dp))

        StageSearchField(
            value = state.adbHost,
            onValueChange = onHostChange,
            placeholder = stringResource(R.string.tv_optimizer_ip_hint),
            icon = MobileIcons.Wifi,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        StageSearchField(
            value = state.adbPort,
            onValueChange = onPortChange,
            placeholder = stringResource(R.string.tv_optimizer_port_hint),
            icon = null,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        StageButton(
            onClick = onConnect,
            text = stringResource(R.string.tv_optimizer_connect_btn),
            style = StageButtonStyle.SECONDARY,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        // Connection feedback status
        when (state.connectionStatus) {
            ConnectionStatus.CONNECTING -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(StageRadii.Card))
                        .background(palette.card)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = palette.accent,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.tv_optimizer_searching),
                        style = stageText(13f, 500),
                        color = palette.muted,
                    )
                }
            }
            ConnectionStatus.AUTHORIZATION_PENDING -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(StageRadii.Card))
                        .background(palette.warn.copy(alpha = 0.15f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StageIcon(MobileIcons.Warning, 20.dp, palette.warn)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.tv_optimizer_status_prompt),
                        style = stageText(13.5f, 600),
                        color = palette.warn,
                    )
                }
            }
            ConnectionStatus.CONNECTED -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(StageRadii.Card))
                        .background(palette.card)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StageIcon(MobileIcons.CheckCircle, 20.dp, palette.accent)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.tv_optimizer_status_connected),
                        style = stageText(13.5f, 600),
                        color = palette.text,
                    )
                }
            }
            ConnectionStatus.FAILED -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(StageRadii.Card))
                        .background(palette.danger.copy(alpha = 0.15f))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StageIcon(MobileIcons.Warning, 20.dp, palette.danger)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = state.connectionErrorRes?.let { stringResource(it) } ?: stringResource(R.string.tv_optimizer_error_failed),
                        style = stageText(13f, 500),
                        color = palette.danger,
                    )
                }
            }
            ConnectionStatus.DISCONNECTED -> Unit
        }

        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StageButton(
                onClick = onBack,
                text = stringResource(R.string.tv_optimizer_nav_back),
                style = StageButtonStyle.SECONDARY,
                modifier = Modifier.weight(1f),
            )
            StageButton(
                onClick = onNext,
                text = stringResource(R.string.tv_optimizer_nav_next),
                style = StageButtonStyle.PRIMARY,
                enabled = isConnected,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SelectModelStepView(
    state: TvOptimizerUiState,
    onSearchChange: (String) -> Unit,
    onModelSelect: (TvModel) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalStagePalette.current
    val filteredModels = remember(state.availableModels, state.modelSearchQuery) {
        val q = state.modelSearchQuery.trim().lowercase()
        if (q.isEmpty()) state.availableModels
        else state.availableModels.filter { it.name.lowercase().contains(q) || it.id.lowercase().contains(q) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.tv_optimizer_model_title),
            style = stageText(20f, 700),
            color = palette.text,
        )
        Spacer(Modifier.height(12.dp))

        StageSearchField(
            value = state.modelSearchQuery,
            onValueChange = onSearchChange,
            placeholder = stringResource(R.string.tv_optimizer_model_search),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        filteredModels.forEach { model ->
            val isSelected = model.id == state.selectedModel?.id
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(StageRadii.Card))
                    .background(if (isSelected) palette.accent.copy(alpha = 0.12f) else palette.card)
                    .border(
                        width = if (isSelected) 1.5.dp else 0.dp,
                        color = if (isSelected) palette.accent else Color.Transparent,
                        shape = RoundedCornerShape(StageRadii.Card),
                    )
                    .clickable { onModelSelect(model) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = model.name,
                    style = stageText(14f, if (isSelected) 700 else 500),
                    color = if (isSelected) palette.accent else palette.text,
                )
                if (isSelected) {
                    StageIcon(MobileIcons.Check, 18.dp, palette.accent)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StageButton(
                onClick = onBack,
                text = stringResource(R.string.tv_optimizer_nav_back),
                style = StageButtonStyle.SECONDARY,
                modifier = Modifier.weight(1f),
            )
            StageButton(
                onClick = onNext,
                text = stringResource(R.string.tv_optimizer_nav_next),
                style = StageButtonStyle.PRIMARY,
                enabled = state.selectedModel != null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SelectPackagesStepView(
    state: TvOptimizerUiState,
    onTogglePackage: (String) -> Unit,
    onSelectRecommended: () -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalStagePalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.tv_optimizer_packages_title),
            style = stageText(20f, 700),
            color = palette.text,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.tv_optimizer_packages_subtitle),
            style = stageText(13f, 400),
            color = palette.muted,
        )
        Spacer(Modifier.height(14.dp))

        // Actions toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StageButton(
                onClick = onSelectRecommended,
                text = stringResource(R.string.tv_optimizer_select_recommended),
                style = StageButtonStyle.SECONDARY,
                small = true,
            )
            Text(
                text = stringResource(
                    R.string.tv_optimizer_packages_count,
                    state.selectedPackageNames.size,
                    state.availablePackages.size,
                ),
                style = stageText(12f, 500),
                color = palette.muted,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Package Cards
        state.availablePackages.forEach { pkg ->
            val isChecked = state.selectedPackageNames.contains(pkg.packageName)
            val title = resolvePackageString(context, pkg.titleKey)
            val desc = resolvePackageString(context, pkg.descriptionKey)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(StageRadii.Card))
                    .background(palette.card)
                    .clickable { onTogglePackage(pkg.packageName) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = title,
                            style = stageText(14f, 700),
                            color = palette.text,
                        )
                        Spacer(Modifier.width(8.dp))
                        when (pkg.risk) {
                            PackageRisk.SAFE -> StageTag(
                                text = stringResource(R.string.tv_optimizer_risk_safe),
                                tone = StageTagTone.PLAIN,
                            )
                            PackageRisk.MODERATE -> StageTag(
                                text = stringResource(R.string.tv_optimizer_risk_moderate),
                                tone = StageTagTone.WARN,
                            )
                            PackageRisk.HIGH -> StageTag(
                                text = stringResource(R.string.tv_optimizer_risk_high),
                                tone = StageTagTone.WARN,
                            )
                        }
                    }
                    StageSwitch(checked = isChecked)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = desc,
                    style = stageText(12f, 400),
                    color = palette.muted,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = pkg.packageName,
                    style = stageText(10.5f, 400),
                    color = palette.dim,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StageButton(
                onClick = onBack,
                text = stringResource(R.string.tv_optimizer_nav_back),
                style = StageButtonStyle.SECONDARY,
                modifier = Modifier.weight(1f),
            )
            StageButton(
                onClick = onNext,
                text = stringResource(R.string.tv_optimizer_nav_next),
                style = StageButtonStyle.PRIMARY,
                enabled = state.selectedPackageNames.isNotEmpty(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ConfirmStepView(
    state: TvOptimizerUiState,
    onUnderstandToggle: (Boolean) -> Unit,
    onApply: () -> Unit,
    onBack: () -> Unit,
) {
    val palette = LocalStagePalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.tv_optimizer_confirm_title),
            style = stageText(20f, 700),
            color = palette.text,
        )
        Spacer(Modifier.height(16.dp))

        // Calm Warning Box - SHOWN ONLY HERE
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(StageRadii.Card))
                .background(palette.warn.copy(alpha = 0.12f))
                .border(1.dp, palette.warn.copy(alpha = 0.4f), RoundedCornerShape(StageRadii.Card))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StageIcon(MobileIcons.Warning, 20.dp, palette.warn)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.tv_optimizer_confirm_title),
                    style = stageText(14f, 700),
                    color = palette.warn,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.tv_optimizer_confirm_warning),
                style = stageText(12.5f, 400),
                color = palette.text,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Mandatory "I understand" checkbox row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(StageRadii.Card))
                .background(palette.card)
                .clickable { onUnderstandToggle(!state.understandRiskChecked) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.tv_optimizer_understand_checkbox),
                style = stageText(14f, 600),
                color = palette.text,
            )
            StageSwitch(checked = state.understandRiskChecked)
        }

        Spacer(Modifier.height(16.dp))

        // List of packages to be disabled
        Text(
            text = stringResource(R.string.tv_optimizer_result_disabled_count, state.selectedPackageNames.size),
            style = stageText(13f, 600),
            color = palette.muted,
        )
        Spacer(Modifier.height(8.dp))
        state.selectedPackageNames.forEach { pkg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.card.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StageIcon(MobileIcons.Check, 14.dp, palette.accent)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = pkg,
                    style = stageText(12f, 400),
                    color = palette.text,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StageButton(
                onClick = onBack,
                text = stringResource(R.string.tv_optimizer_nav_back),
                style = StageButtonStyle.SECONDARY,
                modifier = Modifier.weight(1f),
            )
            StageButton(
                onClick = onApply,
                text = stringResource(R.string.tv_optimizer_apply_btn),
                style = StageButtonStyle.PRIMARY,
                enabled = state.understandRiskChecked && state.selectedPackageNames.isNotEmpty(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ApplyingStepView(state: TvOptimizerUiState) {
    val palette = LocalStagePalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(56.dp),
            strokeWidth = 3.dp,
            color = palette.accent,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.tv_optimizer_applying),
            style = stageText(18f, 700),
            color = palette.text,
        )
        Spacer(Modifier.height(12.dp))
        if (state.applyTotalCount > 0) {
            val fraction = state.applyProgressIndex.toFloat() / state.applyTotalCount.toFloat()
            StageProgress(fraction = fraction, modifier = Modifier.fillMaxWidth(0.8f))
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.tv_optimizer_progress_format, state.applyProgressIndex, state.applyTotalCount),
                style = stageText(13f, 500),
                color = palette.muted,
            )
        }
    }
}

@Composable
private fun ResultStepView(
    state: TvOptimizerUiState,
    onRollbackAll: () -> Unit,
    onDone: () -> Unit,
) {
    val palette = LocalStagePalette.current
    val count = state.disabledSuccessPackages.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(palette.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            StageIcon(MobileIcons.CheckCircle, 36.dp, palette.accent)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.tv_optimizer_result_title),
            style = stageText(22f, 800),
            color = palette.text,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tv_optimizer_result_disabled_count, count),
            style = stageText(14f, 500),
            color = palette.muted,
        )

        Spacer(Modifier.height(36.dp))
        StageButton(
            onClick = onDone,
            text = stringResource(R.string.tv_optimizer_done),
            style = StageButtonStyle.PRIMARY,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        StageButton(
            onClick = onRollbackAll,
            text = stringResource(R.string.tv_optimizer_rollback_btn),
            style = StageButtonStyle.SECONDARY,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RollbackStepView(
    state: TvOptimizerUiState,
    onToggle: (String) -> Unit,
    onRollbackSelected: () -> Unit,
    onRollbackAll: () -> Unit,
    onDone: () -> Unit,
) {
    val palette = LocalStagePalette.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.tv_optimizer_rollback_title),
            style = stageText(20f, 700),
            color = palette.text,
        )
        Spacer(Modifier.height(16.dp))

        if (state.rollbackDone) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(StageRadii.Card))
                    .background(palette.card)
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StageIcon(MobileIcons.CheckCircle, 32.dp, palette.accent)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.tv_optimizer_result_title),
                        style = stageText(15f, 700),
                        color = palette.text,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            StageButton(
                onClick = onDone,
                text = stringResource(R.string.tv_optimizer_done),
                style = StageButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            )
            return
        }

        if (state.savedDisabledPackages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(StageRadii.Card))
                    .background(palette.card)
                    .padding(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.tv_optimizer_rollback_empty),
                    style = stageText(13f, 400),
                    color = palette.muted,
                )
            }
            Spacer(Modifier.height(24.dp))
            StageButton(
                onClick = onDone,
                text = stringResource(R.string.tv_optimizer_done),
                style = StageButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
            return
        }

        state.savedDisabledPackages.forEach { pkg ->
            val isChecked = state.selectedRollbackPackages.contains(pkg)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(StageRadii.Card))
                    .background(palette.card)
                    .clickable { onToggle(pkg) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = pkg,
                    style = stageText(13f, 500),
                    color = palette.text,
                    modifier = Modifier.weight(1f),
                )
                StageSwitch(checked = isChecked)
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StageButton(
                onClick = onRollbackSelected,
                text = stringResource(R.string.tv_optimizer_rollback_selected),
                style = StageButtonStyle.SECONDARY,
                enabled = state.selectedRollbackPackages.isNotEmpty() && !state.isRollingBack,
                modifier = Modifier.weight(1f),
            )
            StageButton(
                onClick = onRollbackAll,
                text = stringResource(R.string.tv_optimizer_rollback_btn),
                style = StageButtonStyle.PRIMARY,
                enabled = !state.isRollingBack,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
