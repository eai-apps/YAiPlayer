// Copyright (C) 2026 YAiPlayer authors. Original code, written for YAiPlayer.
package tv.own.owntv.extras.tvoptimizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tv.own.owntv.core.R

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    AUTHORIZATION_PENDING,
    CONNECTED,
    FAILED,
}

data class TvOptimizerUiState(
    val currentStep: TvOptimizerStep = TvOptimizerStep.WELCOME,
    val adbHost: String = "192.168.1.100",
    val adbPort: String = "5555",
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionErrorRes: Int? = null,
    val availableModels: List<TvModel> = emptyList(),
    val selectedModel: TvModel? = null,
    val modelSearchQuery: String = "",
    val availablePackages: List<TvPackageInfo> = emptyList(),
    val selectedPackageNames: Set<String> = emptySet(),
    val understandRiskChecked: Boolean = false,
    val isApplying: Boolean = false,
    val applyProgressIndex: Int = 0,
    val applyTotalCount: Int = 0,
    val disabledSuccessPackages: List<String> = emptyList(),
    val disabledFailedPackages: List<String> = emptyList(),
    val savedDisabledPackages: Set<String> = emptySet(),
    val selectedRollbackPackages: Set<String> = emptySet(),
    val isRollingBack: Boolean = false,
    val rollbackDone: Boolean = false,
) {
    val canProceed: Boolean
        get() = when (currentStep) {
            TvOptimizerStep.WELCOME -> true
            TvOptimizerStep.DEBUG_GUIDE -> true
            TvOptimizerStep.CONNECT -> connectionStatus == ConnectionStatus.CONNECTED
            TvOptimizerStep.SELECT_MODEL -> selectedModel != null
            TvOptimizerStep.SELECT_PACKAGES -> selectedPackageNames.isNotEmpty()
            TvOptimizerStep.CONFIRM -> understandRiskChecked && selectedPackageNames.isNotEmpty()
            TvOptimizerStep.APPLYING -> false
            TvOptimizerStep.RESULT -> true
            TvOptimizerStep.ROLLBACK -> selectedRollbackPackages.isNotEmpty() && !isRollingBack
        }
}

class TvOptimizerViewModel(
    private val repository: TvOptimizerRepository,
    private val transport: AdbTransport,
    private val store: TvOptimizerStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TvOptimizerUiState())
    val uiState: StateFlow<TvOptimizerUiState> = _uiState.asStateFlow()

    init {
        loadSavedBackups()
        loadModels()
    }

    fun loadSavedBackups() {
        viewModelScope.launch {
            store.disabledPackages.collect { saved ->
                _uiState.update { current ->
                    current.copy(
                        savedDisabledPackages = saved,
                        selectedRollbackPackages = if (current.selectedRollbackPackages.isEmpty()) saved else current.selectedRollbackPackages,
                    )
                }
            }
        }
    }

    fun loadModels() {
        viewModelScope.launch {
            val models = repository.models()
            _uiState.update { current ->
                val autoSelected = current.selectedModel ?: models.firstOrNull()
                current.copy(
                    availableModels = models,
                    selectedModel = autoSelected,
                )
            }
            _uiState.value.selectedModel?.let { loadPackages(it.id) }
        }
    }

    fun setAdbHost(host: String) {
        _uiState.update { it.copy(adbHost = host, connectionErrorRes = null) }
    }

    fun setAdbPort(port: String) {
        _uiState.update { it.copy(adbPort = port, connectionErrorRes = null) }
    }

    fun connect() {
        val state = _uiState.value
        val host = state.adbHost.trim()
        val port = state.adbPort.trim().toIntOrNull() ?: AdbTransport.DEFAULT_ADB_PORT

        if (host.isEmpty()) {
            _uiState.update { it.copy(connectionErrorRes = R.string.tv_optimizer_error_host_required, connectionStatus = ConnectionStatus.FAILED) }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.CONNECTING,
                    connectionErrorRes = null,
                )
            }
            // Transition through auth-pending to simulate ADB dialog prompt
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.AUTHORIZATION_PENDING) }

            val result = transport.connect(host, port)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.CONNECTED,
                        connectionErrorRes = null,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.FAILED,
                        connectionErrorRes = R.string.tv_optimizer_error_failed,
                    )
                }
            }
        }
    }

    fun setModelSearchQuery(query: String) {
        _uiState.update { it.copy(modelSearchQuery = query) }
    }

    fun selectModel(model: TvModel) {
        _uiState.update { it.copy(selectedModel = model) }
        loadPackages(model.id)
    }

    private fun loadPackages(modelId: String) {
        viewModelScope.launch {
            val pkgs = repository.packagesFor(modelId)
            val recommendedSet = pkgs.filter { it.recommended }.map { it.packageName }.toSet()
            _uiState.update {
                it.copy(
                    availablePackages = pkgs,
                    selectedPackageNames = recommendedSet,
                )
            }
        }
    }

    fun togglePackageSelection(packageName: String) {
        _uiState.update { current ->
            val updated = current.selectedPackageNames.toMutableSet()
            if (updated.contains(packageName)) {
                updated.remove(packageName)
            } else {
                updated.add(packageName)
            }
            current.copy(selectedPackageNames = updated)
        }
    }

    fun selectRecommendedPackages() {
        val recommended = _uiState.value.availablePackages.filter { it.recommended }.map { it.packageName }.toSet()
        _uiState.update { it.copy(selectedPackageNames = recommended) }
    }

    fun selectAllPackages() {
        val all = _uiState.value.availablePackages.map { it.packageName }.toSet()
        _uiState.update { it.copy(selectedPackageNames = all) }
    }

    fun clearPackageSelection() {
        _uiState.update { it.copy(selectedPackageNames = emptySet()) }
    }

    fun setUnderstandRisk(checked: Boolean) {
        _uiState.update { it.copy(understandRiskChecked = checked) }
    }

    fun nextStep() {
        val state = _uiState.value
        if (!state.canProceed) return

        val next = when (state.currentStep) {
            TvOptimizerStep.WELCOME -> TvOptimizerStep.DEBUG_GUIDE
            TvOptimizerStep.DEBUG_GUIDE -> TvOptimizerStep.CONNECT
            TvOptimizerStep.CONNECT -> TvOptimizerStep.SELECT_MODEL
            TvOptimizerStep.SELECT_MODEL -> TvOptimizerStep.SELECT_PACKAGES
            TvOptimizerStep.SELECT_PACKAGES -> TvOptimizerStep.CONFIRM
            TvOptimizerStep.CONFIRM -> {
                applyOptimizations()
                TvOptimizerStep.APPLYING
            }
            TvOptimizerStep.APPLYING -> TvOptimizerStep.RESULT
            TvOptimizerStep.RESULT -> TvOptimizerStep.WELCOME
            TvOptimizerStep.ROLLBACK -> TvOptimizerStep.WELCOME
        }
        _uiState.update { it.copy(currentStep = next) }
    }

    fun previousStep() {
        val prev = when (_uiState.value.currentStep) {
            TvOptimizerStep.WELCOME -> TvOptimizerStep.WELCOME
            TvOptimizerStep.DEBUG_GUIDE -> TvOptimizerStep.WELCOME
            TvOptimizerStep.CONNECT -> TvOptimizerStep.DEBUG_GUIDE
            TvOptimizerStep.SELECT_MODEL -> TvOptimizerStep.CONNECT
            TvOptimizerStep.SELECT_PACKAGES -> TvOptimizerStep.SELECT_MODEL
            TvOptimizerStep.CONFIRM -> TvOptimizerStep.SELECT_PACKAGES
            TvOptimizerStep.APPLYING -> TvOptimizerStep.CONFIRM
            TvOptimizerStep.RESULT -> TvOptimizerStep.WELCOME
            TvOptimizerStep.ROLLBACK -> TvOptimizerStep.WELCOME
        }
        _uiState.update { it.copy(currentStep = prev) }
    }

    fun goToRollback() {
        _uiState.update {
            it.copy(
                currentStep = TvOptimizerStep.ROLLBACK,
                selectedRollbackPackages = it.savedDisabledPackages,
                rollbackDone = false,
            )
        }
    }

    fun goToStep(step: TvOptimizerStep) {
        _uiState.update { it.copy(currentStep = step) }
    }

    fun applyOptimizations() {
        val targets = _uiState.value.selectedPackageNames.toList()
        if (targets.isEmpty()) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isApplying = true,
                    applyProgressIndex = 0,
                    applyTotalCount = targets.size,
                    disabledSuccessPackages = emptyList(),
                    disabledFailedPackages = emptyList(),
                )
            }

            // Automatic backup before applying changes
            store.saveDisabledPackages(targets.toSet())

            val successful = mutableListOf<String>()
            val failed = mutableListOf<String>()

            for ((index, pkg) in targets.withIndex()) {
                _uiState.update { it.copy(applyProgressIndex = index + 1) }
                val cmd = AdbCommandHelper.buildDisableCommand(pkg)
                val res = transport.runShell(cmd)
                if (res.isSuccess) {
                    successful.add(pkg)
                } else {
                    failed.add(pkg)
                }
            }

            _uiState.update {
                it.copy(
                    isApplying = false,
                    disabledSuccessPackages = successful,
                    disabledFailedPackages = failed,
                    currentStep = TvOptimizerStep.RESULT,
                )
            }
        }
    }

    fun toggleRollbackPackage(packageName: String) {
        _uiState.update { current ->
            val set = current.selectedRollbackPackages.toMutableSet()
            if (set.contains(packageName)) set.remove(packageName) else set.add(packageName)
            current.copy(selectedRollbackPackages = set)
        }
    }

    fun rollbackSelected() {
        val targets = _uiState.value.selectedRollbackPackages.toList()
        if (targets.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRollingBack = true) }
            for (pkg in targets) {
                val cmd = AdbCommandHelper.buildEnableCommand(pkg)
                transport.runShell(cmd)
            }
            store.removeDisabledPackages(targets.toSet())
            _uiState.update {
                it.copy(
                    isRollingBack = false,
                    rollbackDone = true,
                    selectedRollbackPackages = emptySet(),
                )
            }
        }
    }

    fun rollbackAll() {
        val targets = _uiState.value.savedDisabledPackages.toList()
        if (targets.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRollingBack = true) }
            for (pkg in targets) {
                val cmd = AdbCommandHelper.buildEnableCommand(pkg)
                transport.runShell(cmd)
            }
            store.clearAll()
            _uiState.update {
                it.copy(
                    isRollingBack = false,
                    rollbackDone = true,
                    selectedRollbackPackages = emptySet(),
                )
            }
        }
    }
}
