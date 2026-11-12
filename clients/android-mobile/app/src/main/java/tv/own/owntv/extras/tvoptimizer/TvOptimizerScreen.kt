// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import tv.own.owntv.core.R
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.stage.LocalStagePalette
import tv.own.owntv.mobile.ui.stage.StageButton
import tv.own.owntv.mobile.ui.stage.StageButtonStyle
import tv.own.owntv.mobile.ui.stage.StagePill
import tv.own.owntv.mobile.ui.stage.StageSearchField
import tv.own.owntv.mobile.ui.stage.StageTag
import tv.own.owntv.mobile.ui.stage.StageTagTone
import tv.own.owntv.mobile.ui.stage.stageBackground
import tv.own.owntv.mobile.ui.stage.stageText

/** Android key codes for the ADB remote (android/keycodes.h). */
private object Keys {
    const val HOME = 3
    const val BACK = 4
    const val UP = 19
    const val DOWN = 20
    const val LEFT = 21
    const val RIGHT = 22
    const val OK = 23
    const val VOL_UP = 24
    const val VOL_DOWN = 25
    const val POWER = 26
    const val MENU = 82
    const val PLAY_PAUSE = 85
}

/**
 * «Ускорь телевизор»: warning → debugging steps → network ADB → which list → plan → apply → result, plus restore,
 * the check after a firmware update, the tracking audit, the new Home screen (launcher) and an ADB remote.
 */
@Composable
fun TvOptimizerScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    vm: TvOptimizerViewModel = viewModel(),
) {
    val s by vm.ui.collectAsStateWithLifecycle()
    val palette = LocalStagePalette.current
    Column(modifier.fillMaxSize().stageBackground().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StagePill(onClick = { back(s, vm, onDismiss) }, icon = MobileIcons.ArrowBack, contentDescription = stringResource(R.string.tv_optimizer_nav_back))
            Text(stringResource(R.string.tv_optimizer_title), style = stageText(16f, 700), color = palette.text)
            StagePill(onClick = onDismiss, icon = MobileIcons.Close, contentDescription = stringResource(R.string.tv_optimizer_done))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            when (s.step) {
                OptimizerStep.WELCOME -> Welcome(s, vm)
                OptimizerStep.DEBUG_GUIDE -> DebugGuide(vm)
                OptimizerStep.CONNECT -> Connect(s, vm)
                OptimizerStep.CHOOSE_LIST -> ChooseList(s, vm)
                OptimizerStep.PLAN -> PlanView(s, vm)
                OptimizerStep.APPLYING -> Applying(s)
                OptimizerStep.RESULT -> Result(s, vm, onDismiss)
                OptimizerStep.MANAGE -> Manage(s, vm)
                OptimizerStep.LAUNCHER -> Launcher(s, vm)
                OptimizerStep.PRIVACY -> Privacy(s, vm)
            }
        }
    }
}

private fun back(s: OptimizerUiState, vm: TvOptimizerViewModel, onDismiss: () -> Unit) {
    when (s.step) {
        OptimizerStep.WELCOME -> onDismiss()
        OptimizerStep.DEBUG_GUIDE -> vm.go(OptimizerStep.WELCOME)
        OptimizerStep.CONNECT -> vm.go(OptimizerStep.DEBUG_GUIDE)
        OptimizerStep.CHOOSE_LIST -> vm.go(OptimizerStep.CONNECT)
        OptimizerStep.PLAN -> vm.go(OptimizerStep.CHOOSE_LIST)
        OptimizerStep.APPLYING -> Unit
        OptimizerStep.RESULT, OptimizerStep.MANAGE, OptimizerStep.LAUNCHER, OptimizerStep.PRIVACY ->
            vm.go(if (s.tv != null) OptimizerStep.CHOOSE_LIST else OptimizerStep.WELCOME)
    }
}

@Composable
private fun Title(text: String) {
    Text(text, style = stageText(20f, 800), color = LocalStagePalette.current.text, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun Body(text: String, warn: Boolean = false) {
    val p = LocalStagePalette.current
    Text(text, style = stageText(13.5f, 500), color = if (warn) p.danger else p.muted, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Welcome(s: OptimizerUiState, vm: TvOptimizerViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Title(stringResource(R.string.tv_optimizer_welcome_title)) }
        item { Body(stringResource(R.string.tv_optimizer_welcome_body)) }
        item { Title(stringResource(R.string.opt_risk_title)) }
        item { Body(stringResource(R.string.opt_risk_body), warn = true) }
        item {
            Row(Modifier.fillMaxWidth().clickable { vm.setRisk(!s.riskAccepted) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = s.riskAccepted, onCheckedChange = vm::setRisk)
                Text(stringResource(R.string.opt_risk_check), style = stageText(14f, 600), color = LocalStagePalette.current.text)
            }
        }
        item {
            StageButton(
                onClick = { vm.go(OptimizerStep.DEBUG_GUIDE) },
                text = stringResource(R.string.tv_optimizer_start),
                style = StageButtonStyle.PRIMARY,
                enabled = s.riskAccepted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DebugGuide(vm: TvOptimizerViewModel) {
    var showVendors by rememberSaveable { mutableStateOf(false) }
    val vendors = listOf(
        R.string.tv_optimizer_vendor_generic to R.string.tv_optimizer_vendor_generic_steps,
        R.string.tv_optimizer_vendor_xiaomi to R.string.tv_optimizer_vendor_xiaomi_steps,
        R.string.tv_optimizer_vendor_sony to R.string.tv_optimizer_vendor_sony_steps,
        R.string.tv_optimizer_vendor_tcl to R.string.tv_optimizer_vendor_tcl_steps,
        R.string.tv_optimizer_vendor_philips to R.string.tv_optimizer_vendor_philips_steps,
        R.string.tv_optimizer_vendor_google to R.string.tv_optimizer_vendor_google_steps,
    )
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Title(stringResource(R.string.tv_optimizer_debug_title)) }
        item { Body(stringResource(R.string.tv_optimizer_debug_subtitle)) }
        val steps = listOf(R.string.tv_optimizer_debug_step1, R.string.tv_optimizer_debug_step2, R.string.tv_optimizer_debug_step3,
            R.string.tv_optimizer_debug_step4, R.string.tv_optimizer_debug_step5)
        items(steps.withIndex().toList()) { (i, res) ->
            Text("${i + 1}. " + stringResource(res), style = stageText(14f, 500), color = LocalStagePalette.current.text)
        }
        item { Body(stringResource(R.string.opt_debug_allow_hint)) }
        item { StageButton(onClick = { showVendors = !showVendors }, text = stringResource(R.string.tv_optimizer_cant_find), small = true) }
        if (showVendors) {
            item { Body(stringResource(R.string.tv_optimizer_cant_find_hint)) }
            items(vendors) { (name, steps) ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(stringResource(name), style = stageText(13.5f, 700), color = LocalStagePalette.current.text)
                    Body(stringResource(steps))
                }
            }
        }
        item {
            StageButton(onClick = { vm.go(OptimizerStep.CONNECT) }, text = stringResource(R.string.tv_optimizer_nav_next),
                style = StageButtonStyle.PRIMARY, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Connect(s: OptimizerUiState, vm: TvOptimizerViewModel) {
    // Finds the TV box by itself on entering this step (owner 12.11): the fields below are only a fallback.
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.autoConnect() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Title(stringResource(R.string.tv_optimizer_connect_title))
        if (s.connecting && s.host.isBlank()) Body(stringResource(R.string.atv_screen_searching))
        OutlinedTextField(
            value = s.host, onValueChange = vm::setHost, singleLine = true,
            label = { Text(stringResource(R.string.tv_optimizer_ip_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = s.port, onValueChange = vm::setPort, singleLine = true,
            label = { Text(stringResource(R.string.tv_optimizer_port_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        if (s.connecting) {
            Body(stringResource(R.string.tv_optimizer_status_prompt))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        s.connectProblem?.let {
            Body(
                stringResource(
                    when (it) {
                        ConnectProblem.NOT_LOCAL -> R.string.opt_connect_not_local
                        ConnectProblem.UNREACHABLE -> R.string.opt_connect_unreachable
                        ConnectProblem.NOT_ALLOWED -> R.string.opt_connect_not_allowed
                        ConnectProblem.OTHER -> R.string.opt_connect_other
                        ConnectProblem.NOT_FOUND -> R.string.atv_screen_not_found
                    },
                ),
                warn = true,
            )
        }
        StageButton(
            onClick = { if (s.host.isBlank()) vm.autoConnect(force = true) else vm.connect() }, text = stringResource(R.string.tv_optimizer_connect_btn), style = StageButtonStyle.PRIMARY,
            enabled = !s.connecting, modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChooseList(s: OptimizerUiState, vm: TvOptimizerViewModel) {
    var pickModel by rememberSaveable { mutableStateOf(false) }
    val tv = s.tv
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tv != null) item { Body(stringResource(R.string.opt_tv_found, tv.label, tv.android)) }
        item { Title(stringResource(R.string.opt_choose_title)) }
        if (!s.catalogReady) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        s.guessed?.let { m ->
            item {
                StageButton(onClick = { vm.choose(PlanSource.Model(m)) }, text = stringResource(R.string.opt_choose_guess, m.name),
                    style = StageButtonStyle.PRIMARY, modifier = Modifier.fillMaxWidth())
            }
        }
        vm.brandSource()?.let { b ->
            item {
                StageButton(onClick = { vm.choose(b) }, text = stringResource(R.string.opt_choose_brand, (b as PlanSource.Brand).brand, b.lists),
                    modifier = Modifier.fillMaxWidth())
            }
            item { Body(stringResource(R.string.opt_choose_brand_hint)) }
        }
        item { StageButton(onClick = { vm.choose(PlanSource.Common) }, text = stringResource(R.string.opt_choose_common), modifier = Modifier.fillMaxWidth()) }
        item { Body(stringResource(R.string.opt_choose_common_hint)) }
        item { StageButton(onClick = { pickModel = !pickModel }, text = stringResource(R.string.opt_choose_model), modifier = Modifier.fillMaxWidth()) }
        if (pickModel) {
            item {
                StageSearchField(value = s.modelQuery, onValueChange = vm::setQuery, placeholder = stringResource(R.string.opt_search_hint))
            }
            val models = vm.models()
            item { Body(stringResource(R.string.opt_models_count, models.size)) }
            items(models.take(80), key = { it.id }) { m ->
                Column(Modifier.fillMaxWidth().clickable { vm.choose(PlanSource.Model(m)) }.padding(vertical = 6.dp)) {
                    Text(m.name, style = stageText(13.5f, 600), color = LocalStagePalette.current.text)
                    Text(listOf(m.brand, m.os).filter { it.isNotBlank() }.joinToString(" · "), style = stageText(11.5f, 500), color = LocalStagePalette.current.muted)
                }
            }
        }
        item { Title(stringResource(R.string.opt_more_title)) }
        item { ToolsRow(s, vm) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolsRow(s: OptimizerUiState, vm: TvOptimizerViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StageButton(onClick = { vm.go(OptimizerStep.MANAGE) }, text = stringResource(R.string.opt_manage), small = true)
        StageButton(onClick = vm::runPrivacyAudit, text = stringResource(R.string.opt_privacy), small = true)
        StageButton(onClick = { vm.go(OptimizerStep.LAUNCHER) }, text = stringResource(R.string.opt_launcher_title), small = true)
        StageButton(onClick = vm::disableDebugging, text = stringResource(R.string.opt_debug_off), small = true, enabled = !s.debuggingOff)
    }
    if (s.debuggingOff) Body(stringResource(R.string.opt_debug_off_done))
}

private fun groupName(g: AppGroup) = when (g) {
    AppGroup.ADS -> R.string.opt_group_ads
    AppGroup.TRACKING -> R.string.opt_group_tracking
    AppGroup.PRELOAD -> R.string.opt_group_preload
    AppGroup.BRAND -> R.string.opt_group_brand
    AppGroup.LAUNCHER -> R.string.opt_group_launcher
    AppGroup.OTHER -> R.string.opt_group_other
}

private fun optionalName(a: OptionalArea) = when (a) {
    OptionalArea.ACCESSIBILITY -> R.string.opt_optional_accessibility
    OptionalArea.CASTING -> R.string.opt_optional_casting
    OptionalArea.VOICE -> R.string.opt_optional_voice
    OptionalArea.UPDATES -> R.string.opt_optional_updates
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanView(s: OptimizerUiState, vm: TvOptimizerViewModel) {
    val plan = s.plan ?: return
    val context = LocalContext.current
    val palette = LocalStagePalette.current
    var showProtected by rememberSaveable { mutableStateOf(false) }
    var why by remember { mutableStateOf<String?>(null) }
    val copied = stringResource(R.string.opt_copied)
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Title(stringResource(R.string.opt_plan_title)) }
        item { Body(stringResource(R.string.opt_plan_summary, s.selected.size, plan.items.size)) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StageButton(onClick = vm::selectRecommended, text = stringResource(R.string.opt_select_recommended), small = true)
                StageButton(onClick = vm::selectAll, text = stringResource(R.string.opt_select_all), small = true)
                StageButton(onClick = vm::selectNone, text = stringResource(R.string.opt_select_none), small = true)
                StageButton(onClick = { copy(context, vm.commandsText()); Toast.makeText(context, copied, Toast.LENGTH_SHORT).show() },
                    text = stringResource(R.string.opt_copy_commands), small = true, enabled = s.selected.isNotEmpty())
            }
        }
        if (plan.alreadyOff.isNotEmpty()) item { Body(stringResource(R.string.opt_already_off, plan.alreadyOff.size)) }
        plan.items.groupBy { it.group }.forEach { (group, list) ->
            item(key = "g$group") { Text(stringResource(groupName(group)), style = stageText(14f, 800), color = palette.text, modifier = Modifier.padding(top = 8.dp)) }
            items(list, key = { it.packageName }) { item ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().clickable { vm.toggle(item.packageName) }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = item.packageName in s.selected, onCheckedChange = { vm.toggle(item.packageName) })
                        Column(Modifier.weight(1f)) {
                            Text(item.packageName, style = stageText(13f, 600), color = palette.text)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item.optional?.let { StageTag(stringResource(optionalName(it)), tone = StageTagTone.WARN) }
                                if (item.group == AppGroup.LAUNCHER) StageTag(stringResource(R.string.opt_tag_launcher), tone = StageTagTone.WARN)
                            }
                        }
                        StageButton(onClick = { why = if (why == item.packageName) null else item.packageName }, text = stringResource(R.string.opt_why), small = true)
                    }
                    if (why == item.packageName) {
                        Body(stringResource(R.string.opt_why_votes, item.disableVotes, item.keepVotes))
                        item.note?.let { Body(stringResource(R.string.opt_note_from_list, it)) }
                    }
                }
            }
        }
        item {
            StageButton(onClick = { showProtected = !showProtected }, text = stringResource(R.string.opt_protected_title, plan.protectedFound.size),
                small = true, modifier = Modifier.padding(top = 10.dp))
        }
        if (showProtected) {
            item { Body(stringResource(R.string.opt_protected_hint)) }
            items(plan.protectedFound) { Text(it, style = stageText(12f, 500), color = palette.muted) }
        }
        item {
            Spacer(Modifier.height(8.dp))
            StageButton(onClick = vm::apply, text = stringResource(R.string.opt_apply, s.selected.size), style = StageButtonStyle.DANGER,
                enabled = s.selected.isNotEmpty() && s.riskAccepted, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Applying(s: OptimizerUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Title(stringResource(R.string.tv_optimizer_applying))
        Body(stringResource(R.string.tv_optimizer_progress_format, s.progressDone, s.progressTotal))
        LinearProgressIndicator(progress = { if (s.progressTotal == 0) 0f else s.progressDone / s.progressTotal.toFloat() }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Result(s: OptimizerUiState, vm: TvOptimizerViewModel, onDismiss: () -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Title(stringResource(R.string.tv_optimizer_result_title)) }
        item { Body(stringResource(R.string.opt_result_ok, s.succeeded.size)) }
        if (s.failed.isNotEmpty()) {
            item { Body(stringResource(R.string.opt_result_failed, s.failed.size), warn = true) }
            items(s.failed) { Text(it, style = stageText(12f, 500), color = LocalStagePalette.current.muted) }
        }
        item { Body(stringResource(R.string.opt_result_hint)) }
        item { StageButton(onClick = { vm.go(OptimizerStep.MANAGE) }, text = stringResource(R.string.opt_manage), modifier = Modifier.fillMaxWidth()) }
        item { ToolsRow(s, vm) }
        item { StageButton(onClick = onDismiss, text = stringResource(R.string.tv_optimizer_done), style = StageButtonStyle.PRIMARY, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun Manage(s: OptimizerUiState, vm: TvOptimizerViewModel) {
    val context = LocalContext.current
    val palette = LocalStagePalette.current
    var text by rememberSaveable { mutableStateOf("") }
    val point = s.restorePoint
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Title(stringResource(R.string.opt_manage)) }
        if (s.tv == null) {
            item { Body(stringResource(R.string.opt_need_connection)) }
            item { StageButton(onClick = { vm.go(OptimizerStep.CONNECT) }, text = stringResource(R.string.tv_optimizer_connect_btn), modifier = Modifier.fillMaxWidth()) }
            return@LazyColumn
        }
        if (point == null || point.packages.isEmpty()) {
            item { Body(stringResource(R.string.opt_restore_none)) }
        } else {
            item { Body(stringResource(R.string.opt_restore_point, point.label, point.packages.size)) }
            item {
                StageButton(onClick = vm::restoreAll, text = stringResource(R.string.opt_restore_all), style = StageButtonStyle.PRIMARY,
                    enabled = !s.busy, modifier = Modifier.fillMaxWidth())
            }
            item {
                StageButton(onClick = { vm.restoreScript()?.let { share(context, it) } }, text = stringResource(R.string.opt_save_copy), modifier = Modifier.fillMaxWidth())
            }
            item {
                StageButton(onClick = vm::checkLeftovers, text = stringResource(R.string.opt_leftovers_check), enabled = !s.busy, modifier = Modifier.fillMaxWidth())
            }
            if (s.leftoversChecked) {
                item {
                    if (s.leftovers.isEmpty()) Body(stringResource(R.string.opt_leftovers_none))
                    else Column {
                        Body(stringResource(R.string.opt_leftovers_found, s.leftovers.size), warn = true)
                        s.leftovers.forEach { Text(it, style = stageText(12f, 500), color = palette.muted) }
                        StageButton(onClick = vm::disableLeftovers, text = stringResource(R.string.opt_leftovers_fix), small = true, enabled = !s.busy)
                    }
                }
            }
            if (s.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            items(point.packages.sorted(), key = { it }) { pkg ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(pkg, style = stageText(12.5f, 500), color = palette.text, modifier = Modifier.weight(1f))
                    StageButton(onClick = { vm.restore(listOf(pkg)) }, text = stringResource(R.string.opt_restore_one), small = true, enabled = !s.busy)
                }
            }
        }
        item { Title(stringResource(R.string.opt_remote_title)) }
        item { Body(stringResource(R.string.opt_remote_hint)) }
        item { AdbRemote(vm) }
        item {
            StageSearchField(value = text, onValueChange = { v -> text = v.filter { it.code in 32..126 }.take(200) },
                placeholder = stringResource(R.string.opt_text_hint), onSearch = { if (text.isNotEmpty()) { vm.text(text); text = "" } })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdbRemote(vm: TvOptimizerViewModel) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        StageButton(onClick = { vm.key(Keys.UP) }, icon = MobileIcons.KeyboardArrowUp, small = true)
        StageButton(onClick = { vm.key(Keys.DOWN) }, icon = MobileIcons.KeyboardArrowDown, small = true)
        StageButton(onClick = { vm.key(Keys.LEFT) }, text = stringResource(R.string.remote_nav_left), small = true)
        StageButton(onClick = { vm.key(Keys.RIGHT) }, icon = MobileIcons.KeyboardArrowRight, small = true)
        StageButton(onClick = { vm.key(Keys.OK) }, text = stringResource(R.string.remote_nav_ok), style = StageButtonStyle.PRIMARY, small = true)
        StageButton(onClick = { vm.key(Keys.BACK) }, icon = MobileIcons.ArrowBack, small = true)
        StageButton(onClick = { vm.key(Keys.HOME) }, icon = MobileIcons.Home, small = true)
        StageButton(onClick = { vm.key(Keys.MENU) }, icon = MobileIcons.MoreHoriz, small = true)
        StageButton(onClick = { vm.key(Keys.VOL_DOWN) }, text = stringResource(R.string.opt_key_volume_down), small = true)
        StageButton(onClick = { vm.key(Keys.VOL_UP) }, icon = MobileIcons.VolumeUp, small = true)
        StageButton(onClick = { vm.key(Keys.PLAY_PAUSE) }, icon = MobileIcons.PlayArrow, small = true)
        StageButton(onClick = { vm.key(Keys.POWER) }, text = stringResource(R.string.opt_key_power), style = StageButtonStyle.DANGER, small = true)
    }
}

@Composable
private fun Launcher(s: OptimizerUiState, vm: TvOptimizerViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Title(stringResource(R.string.opt_launcher_title)) }
        item { Body(stringResource(R.string.opt_launcher_body)) }
        if (s.tv == null) {
            item { Body(stringResource(R.string.opt_need_connection)) }
            return@LazyColumn
        }
        if (!s.launcherBundled) {
            item { Body(stringResource(R.string.opt_launcher_missing), warn = true) }
            return@LazyColumn
        }
        when (s.launcherPhase) {
            LauncherPhase.IDLE, LauncherPhase.FAILED, LauncherPhase.REVERTED -> {
                if (s.launcherPhase == LauncherPhase.FAILED) item { Body(stringResource(R.string.opt_launcher_failed), warn = true) }
                if (s.launcherPhase == LauncherPhase.REVERTED) item { Body(stringResource(R.string.opt_launcher_reverted)) }
                item {
                    StageButton(onClick = vm::installLauncher, text = stringResource(R.string.opt_launcher_install), style = StageButtonStyle.PRIMARY,
                        modifier = Modifier.fillMaxWidth())
                }
            }
            LauncherPhase.INSTALLING -> {
                item { Body(stringResource(R.string.opt_launcher_installing)) }
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            LauncherPhase.WAITING_CONFIRM -> {
                item { Title(stringResource(R.string.opt_launcher_confirm_q)) }
                item { Body(stringResource(R.string.opt_launcher_left, s.watchdogLeft), warn = true) }
                item { StageButton(onClick = vm::confirmLauncher, text = stringResource(R.string.opt_launcher_yes), style = StageButtonStyle.PRIMARY, modifier = Modifier.fillMaxWidth()) }
                item { StageButton(onClick = { vm.revertLauncher(uninstall = false) }, text = stringResource(R.string.opt_launcher_no), modifier = Modifier.fillMaxWidth()) }
            }
            LauncherPhase.ACTIVE -> {
                item { Body(stringResource(R.string.opt_launcher_active)) }
                item { StageButton(onClick = { vm.go(OptimizerStep.CHOOSE_LIST) }, text = stringResource(R.string.opt_choose_title), modifier = Modifier.fillMaxWidth()) }
                item { StageButton(onClick = { vm.revertLauncher(uninstall = true) }, text = stringResource(R.string.opt_launcher_remove), modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun Privacy(s: OptimizerUiState, vm: TvOptimizerViewModel) {
    val palette = LocalStagePalette.current
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Title(stringResource(R.string.opt_privacy)) }
        item { Body(stringResource(R.string.opt_privacy_hint)) }
        if (s.privacy.isEmpty()) item { Body(stringResource(R.string.opt_privacy_none)) }
        s.privacy.groupBy { it.kind }.forEach { (kind, list) ->
            item(key = "k$kind") {
                Text(stringResource(when (kind) {
                    PrivacyKind.ACR -> R.string.opt_privacy_acr
                    PrivacyKind.VOICE -> R.string.opt_privacy_voice
                    PrivacyKind.TELEMETRY -> R.string.opt_privacy_telemetry
                }), style = stageText(14f, 800), color = palette.text, modifier = Modifier.padding(top = 8.dp))
            }
            items(list, key = { it.packageName }) { f ->
                Column {
                    Text(f.packageName, style = stageText(13f, 600), color = palette.text)
                    if (f.listedAsTracking > 0) Text(stringResource(R.string.opt_privacy_votes, f.listedAsTracking), style = stageText(11.5f, 500), color = palette.muted)
                }
            }
        }
        item {
            StageButton(onClick = { vm.go(OptimizerStep.CHOOSE_LIST) }, text = stringResource(R.string.opt_choose_title), modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        }
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("adb", text))
}

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}
