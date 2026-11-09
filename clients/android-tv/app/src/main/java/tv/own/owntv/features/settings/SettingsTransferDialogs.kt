package tv.own.owntv.features.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import tv.own.owntv.R
import tv.own.owntv.core.backup.SettingsBackupManager
import tv.own.owntv.core.backup.SettingsTransferServer
import tv.own.owntv.ui.stage.StageButton
import tv.own.owntv.ui.stage.StagePopup
import tv.own.owntv.ui.theme.StageColors
import tv.own.owntv.ui.theme.mpx
import tv.own.owntv.ui.theme.stageAccent
import tv.own.owntv.ui.theme.stageText

@Composable
fun SettingsExportDialog(
    vm: BackupViewModel,
    onDismiss: () -> Unit,
) {
    val serverState by (vm.settingsTransferState ?: return).collectAsStateWithLifecycle(SettingsTransferServer.State.Idle)
    val actionFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        vm.startSettingsExport()
        kotlinx.coroutines.delay(100)
        runCatching { actionFocus.requestFocus() }
    }

    DisposableEffect(Unit) {
        onDispose { vm.stopSettingsTransfer() }
    }

    val runningState = serverState as? SettingsTransferServer.State.Running

    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.backup_save_copy),
        width = 820.mpx,
        buttons = {
            StageButton(
                stringResource(R.string.common_done),
                onClick = onDismiss,
                height = 56.mpx,
                textSize = 19,
                modifier = Modifier.focusRequester(actionFocus),
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.mpx),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.mpx),
        ) {
            Text(
                stringResource(R.string.backup_lan_transfer_desc),
                style = stageText(18, 500),
                color = StageColors.Muted,
                modifier = Modifier.fillMaxWidth(),
            )

            val qr = runningState?.qrBitmap
            if (qr != null) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(220.mpx)
                        .background(Color.White, RoundedCornerShape(14.mpx))
                        .padding(8.mpx),
                )
            }

            if (runningState != null) {
                Text(
                    runningState.url,
                    style = stageText(21, 700),
                    color = stageAccent.accent,
                )
            }

            if (runningState?.transferred == true) {
                Text(
                    stringResource(R.string.backup_applied_success),
                    style = stageText(18, 600),
                    color = StageColors.TagText,
                )
            }
        }
    }
}

@Composable
fun SettingsRestoreDialog(
    vm: BackupViewModel,
    onDismiss: () -> Unit,
) {
    val serverState by (vm.settingsTransferState ?: return).collectAsStateWithLifecycle(SettingsTransferServer.State.Idle)
    val actionFocus = remember { FocusRequester() }
    var receivedJson by remember { mutableStateOf<String?>(null) }
    var appliedSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.startSettingsRestore { json ->
            receivedJson = json
        }
        kotlinx.coroutines.delay(100)
        runCatching { actionFocus.requestFocus() }
    }

    DisposableEffect(Unit) {
        onDispose { vm.stopSettingsTransfer() }
    }

    val runningState = serverState as? SettingsTransferServer.State.Running

    val json = receivedJson
    if (json != null) {
        val validation = remember(json) { vm.settingsBackupManager?.validateJson(json) }
        when {
            appliedSuccess -> {
                StagePopup(
                    onDismiss = onDismiss,
                    title = stringResource(R.string.backup_restore_copy),
                    width = 720.mpx,
                    buttons = {
                        StageButton(
                            stringResource(R.string.common_done),
                            onClick = onDismiss,
                            height = 56.mpx,
                            textSize = 19,
                            modifier = Modifier.focusRequester(actionFocus),
                        )
                    },
                ) {
                    Text(
                        stringResource(R.string.backup_applied_success),
                        style = stageText(20, 600),
                        color = StageColors.TagText,
                        modifier = Modifier.padding(vertical = 16.mpx),
                    )
                }
            }
            errorMessage != null -> {
                StagePopup(
                    onDismiss = onDismiss,
                    title = stringResource(R.string.backup_restore_copy),
                    width = 720.mpx,
                    buttons = {
                        StageButton(
                            stringResource(R.string.common_back),
                            onClick = onDismiss,
                            height = 56.mpx,
                            textSize = 19,
                            modifier = Modifier.focusRequester(actionFocus),
                        )
                    },
                ) {
                    Text(
                        errorMessage.orEmpty(),
                        style = stageText(18, 500),
                        color = StageColors.Warn,
                        modifier = Modifier.padding(vertical = 16.mpx),
                    )
                }
            }
            validation is SettingsBackupManager.ValidationResult.NewerVersion -> {
                StagePopup(
                    onDismiss = onDismiss,
                    title = stringResource(R.string.backup_restore_copy),
                    width = 720.mpx,
                    buttons = {
                        StageButton(
                            stringResource(R.string.common_back),
                            onClick = onDismiss,
                            height = 56.mpx,
                            textSize = 19,
                            modifier = Modifier.focusRequester(actionFocus),
                        )
                    },
                ) {
                    Text(
                        stringResource(R.string.backup_error_newer_version),
                        style = stageText(18, 500),
                        color = StageColors.Warn,
                        modifier = Modifier.padding(vertical = 16.mpx),
                    )
                }
            }
            validation is SettingsBackupManager.ValidationResult.Success -> {
                val summary = validation.summary
                val applyFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(100)
                    runCatching { applyFocus.requestFocus() }
                }

                StagePopup(
                    onDismiss = onDismiss,
                    title = stringResource(R.string.backup_preview_title),
                    width = 780.mpx,
                    buttons = {
                        StageButton(
                            stringResource(R.string.backup_confirm_apply),
                            onClick = {
                                vm.applySettingsBackup(json) { res ->
                                    if (res.isSuccess) {
                                        appliedSuccess = true
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.message
                                    }
                                }
                            },
                            height = 56.mpx,
                            textSize = 19,
                            modifier = Modifier.focusRequester(applyFocus),
                        )
                        StageButton(
                            stringResource(R.string.common_cancel),
                            onClick = onDismiss,
                            height = 56.mpx,
                            textSize = 19,
                        )
                    },
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.mpx),
                        verticalArrangement = Arrangement.spacedBy(14.mpx),
                    ) {
                        Text(
                            stringResource(
                                R.string.backup_preview_summary,
                                summary.profileCount,
                                summary.favoriteCount,
                                summary.categoryCount,
                                summary.epgSourceCount,
                            ),
                            style = stageText(19, 500),
                            color = StageColors.Text,
                        )
                    }
                }
            }
            else -> {
                StagePopup(
                    onDismiss = onDismiss,
                    title = stringResource(R.string.backup_restore_copy),
                    width = 720.mpx,
                    buttons = {
                        StageButton(
                            stringResource(R.string.common_back),
                            onClick = onDismiss,
                            height = 56.mpx,
                            textSize = 19,
                            modifier = Modifier.focusRequester(actionFocus),
                        )
                    },
                ) {
                    Text(
                        stringResource(R.string.backup_error_invalid_format),
                        style = stageText(18, 500),
                        color = StageColors.Warn,
                        modifier = Modifier.padding(vertical = 16.mpx),
                    )
                }
            }
        }
        return
    }

    // Default listening state
    StagePopup(
        onDismiss = onDismiss,
        title = stringResource(R.string.backup_restore_copy),
        width = 820.mpx,
        buttons = {
            StageButton(
                stringResource(R.string.common_back),
                onClick = onDismiss,
                height = 56.mpx,
                textSize = 19,
                modifier = Modifier.focusRequester(actionFocus),
            )
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.mpx),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.mpx),
        ) {
            Text(
                stringResource(R.string.backup_lan_transfer_desc),
                style = stageText(18, 500),
                color = StageColors.Muted,
                modifier = Modifier.fillMaxWidth(),
            )

            val qr = runningState?.qrBitmap
            if (qr != null) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(220.mpx)
                        .background(Color.White, RoundedCornerShape(14.mpx))
                        .padding(8.mpx),
                )
            }

            if (runningState != null) {
                Text(
                    runningState.url,
                    style = stageText(21, 700),
                    color = stageAccent.accent,
                )
            }
        }
    }
}
