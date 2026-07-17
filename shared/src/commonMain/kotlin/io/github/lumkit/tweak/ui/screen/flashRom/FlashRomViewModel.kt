package io.github.lumkit.tweak.ui.screen.flashRom

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.feature.FastbootDevice
import io.github.lumkit.tweak.common.feature.FastbootManager
import io.github.lumkit.tweak.common.feature.LineFlashBehavior
import io.github.lumkit.tweak.common.feature.LineFlashProgress
import io.github.lumkit.tweak.common.feature.LineFlashRomPackage
import io.github.lumkit.tweak.common.feature.LineFlashScript
import io.github.lumkit.tweak.common.feature.LineFlashScriptType
import io.github.lumkit.tweak.common.feature.LineFlashStep
import io.github.lumkit.tweak.common.utils.logE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object FlashRomViewModel : BaseViewModel() {

    enum class Phase {
        Idle,
        Unpacking,
        Validating,
        Ready,
        Flashing,
        Success,
        Error,
    }

    enum class FlashTaskStatus {
        Pending,
        Running,
        Done,
        Failed,
    }

    enum class FlashTaskKind {
        Flash,
        Erase,
        Check,
        Reboot,
        OemLock,
        SetActive,
        Command,
    }

    data class Device(
        val fastbootDevice: FastbootDevice,
        val getVar: Map<String, String>,
        val id: String,
    )

    data class ProgressInfo(
        val percent: Int = 0,
        val message: String = "",
    )

    data class FlashTaskUi(
        val id: String,
        val stepIndex: Int,
        val kind: FlashTaskKind,
        val label: String,
        val status: FlashTaskStatus,
        val percent: Int = 0,
    )

    data class UiSnapshot(
        val phase: Phase = Phase.Idle,
        val romPath: String = "",
        val workRomDir: String? = null,
        val sessionId: String? = null,
        val progress: ProgressInfo = ProgressInfo(),
        val scripts: List<LineFlashScript> = emptyList(),
        val selectedScriptName: String? = null,
        val romPackage: LineFlashRomPackage? = null,
        val flashTasks: List<FlashTaskUi> = emptyList(),
        val errorMessage: String? = null,
        val romSelectionLocked: Boolean = false,
        val suggestRebootFastboot: Boolean = false,
    ) {
        val busy: Boolean
            get() = phase == Phase.Unpacking ||
                phase == Phase.Validating ||
                phase == Phase.Flashing

        val deviceSelectionEnabled: Boolean
            get() = !busy

        val selectedScript: LineFlashScript?
            get() = scripts.firstOrNull { it.name == selectedScriptName }
                ?: scripts.firstOrNull()
    }

    private val _targetFastbootDevice = MutableStateFlow<Device?>(null)
    val targetFastbootDevice = _targetFastbootDevice.asStateFlow()

    private val _uiState = MutableStateFlow(UiSnapshot())
    val uiState = _uiState.asStateFlow()

    val fastBootDevices = FastbootManager.devices
        .map { devices ->
            coroutineScope {
                devices.map { device ->
                    async(Dispatchers.IO) {
                        resolveDevice(device)
                    }
                }.awaitAll()
            }
        }
        .catch { throwable ->
            logE(throwable.stackTraceToString(), throwable, tag = "FlashRomViewModel")
            emit(emptyList())
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    /** 响应式按钮启用状态：同时观察 ROM / 设备 / 阶段 / 在线设备列表 */
    val startEnabled = combine(_uiState, _targetFastbootDevice, fastBootDevices) { ui, device, devices ->
        computeStartEnabled(ui, device, devices)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    /** 兼容旧 UI 对 romPath 的单独订阅 */
    val romPath = _uiState.map { it.romPath }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        "",
    )

    private var deviceWatchJob: Job? = null
    private var trackedDeviceId: String? = null
    var onRequestCancel: (() -> Unit)? = null

    init {
        FastbootManager.startMonitor()
        deviceWatchJob = viewModelScope.launch {
            combine(fastBootDevices, targetFastbootDevice, _uiState) { devices, target, ui ->
                Triple(devices, target, ui)
            }.distinctUntilChanged { old, new ->
                old.first.map { it.id } == new.first.map { it.id } &&
                    old.second?.id == new.second?.id &&
                    old.third.phase == new.third.phase
            }.collect { (devices, target, ui) ->
                if (target == null) {
                    trackedDeviceId = null
                    return@collect
                }
                val stillPresent = devices.any { it.id == target.id }
                val previousId = trackedDeviceId
                val identityChanged = previousId != null && previousId != target.id
                trackedDeviceId = target.id

                if (!stillPresent) {
                    clearTargetDevice()
                    if (ui.phase.shouldCancelOnDeviceLost()) {
                        onRequestCancel?.invoke()
                    }
                    return@collect
                }

                if (identityChanged && ui.phase.shouldCancelOnDeviceLost()) {
                    onRequestCancel?.invoke()
                }
            }
        }
    }

    private fun Phase.shouldCancelOnDeviceLost(): Boolean = when (this) {
        Phase.Unpacking, Phase.Validating, Phase.Ready, Phase.Flashing -> true
        Phase.Idle, Phase.Success, Phase.Error -> false
    }

    private fun clearTargetDevice() {
        _targetFastbootDevice.value = null
        trackedDeviceId = null
    }

    private fun computeStartEnabled(
        ui: UiSnapshot,
        device: Device?,
        devices: List<Device>,
    ): Boolean {
        if (ui.busy) return false
        if (ui.romPath.isBlank()) return false
        if (device == null) return false
        if (!device.fastbootDevice.hasPermission) return false
        if (devices.none { it.id == device.id }) return false
        return true
    }

    private suspend fun resolveDevice(device: FastbootDevice): Device {
        // id 只用 USB 描述符，避免 getvar 失败时身份抖动触发误取消
        val id = device.serialNumber?.takeIf { it.isNotBlank() }
            ?: device.deviceName.substringAfterLast("/")
        if (!device.hasPermission) {
            return Device(device, emptyMap(), id)
        }
        // 线刷占用 USB 时跳过 getvar，避免与刷写会话抢连接
        if (_uiState.value.busy) {
            return Device(device, emptyMap(), id)
        }
        val getVar = runCatching { FastbootManager.getAllVars(device) }
            .onFailure { logE(it.stackTraceToString(), it, tag = "FlashRomViewModel") }
            .getOrDefault(emptyMap())
        return Device(device, getVar, id)
    }

    fun setTargetFastbootDevice(device: Device) {
        if (!_uiState.value.deviceSelectionEnabled) return
        val previous = _targetFastbootDevice.value
        val phase = _uiState.value.phase
        if (previous != null &&
            previous.id != device.id &&
            phase != Phase.Idle
        ) {
            onRequestCancel?.invoke()
            resetAfterCancel()
        }
        _targetFastbootDevice.value = device
        trackedDeviceId = device.id
    }

    fun setRomPath(path: String) {
        val current = _uiState.value
        if (current.romSelectionLocked) return
        val reset = current.phase == Phase.Ready ||
            current.phase == Phase.Success ||
            current.phase == Phase.Error
        _uiState.value = if (reset) {
            current.copy(
                romPath = path,
                phase = Phase.Idle,
                workRomDir = null,
                sessionId = null,
                progress = ProgressInfo(),
                scripts = emptyList(),
                selectedScriptName = null,
                romPackage = null,
                flashTasks = emptyList(),
                romSelectionLocked = false,
                errorMessage = null,
            )
        } else {
            current.copy(romPath = path)
        }
    }

    fun setSelectedScript(scriptName: String) {
        val current = _uiState.value
        if (current.phase == Phase.Flashing) return
        val script = current.scripts.firstOrNull { it.name == scriptName } ?: return
        _uiState.value = current.copy(
            selectedScriptName = script.name,
            romSelectionLocked = true,
            flashTasks = buildFlashTasks(script),
        )
    }

    fun canStart(): Boolean = computeStartEnabled(
        ui = _uiState.value,
        device = _targetFastbootDevice.value,
        devices = fastBootDevices.value,
    )

    /**
     * 流水线入口立即锁定 UI，避免解压/刷写开始前按钮仍可点。
     * @return false 表示当前不具备启动条件
     */
    fun tryBeginPipeline(): Boolean {
        if (!canStart()) return false
        val current = _uiState.value
        return when (current.phase) {
            Phase.Ready, Phase.Success -> {
                markFlashing()
                true
            }

            Phase.Idle, Phase.Error -> {
                _uiState.value = current.copy(
                    phase = Phase.Validating,
                    errorMessage = null,
                    progress = ProgressInfo(0, ""),
                    flashTasks = emptyList(),
                )
                true
            }

            Phase.Unpacking, Phase.Validating, Phase.Flashing -> false
        }
    }

    fun markUnpacking(sessionId: String) {
        _uiState.value = _uiState.value.copy(
            sessionId = sessionId,
            phase = Phase.Unpacking,
            errorMessage = null,
            progress = ProgressInfo(0, ""),
            flashTasks = emptyList(),
        )
    }

    fun updateProgress(percent: Int, message: String) {
        _uiState.value = _uiState.value.copy(
            progress = ProgressInfo(percent.coerceIn(0, 100), message),
        )
    }

    fun markValidating(workRomDir: String) {
        _uiState.value = _uiState.value.copy(
            workRomDir = workRomDir,
            phase = Phase.Validating,
            errorMessage = null,
            progress = ProgressInfo(0, ""),
        )
    }

    fun markReady(romPackage: LineFlashRomPackage) {
        val scripts = romPackage.scripts
            .filter { it.type == LineFlashScriptType.BAT }
            .ifEmpty { romPackage.scripts }
        val defaultScript = scripts.firstOrNull { it.behavior == LineFlashBehavior.CLEAN_ALL }
            ?: scripts.firstOrNull {
                it.name.contains("flash_all", ignoreCase = true) &&
                    !it.name.contains("lock", ignoreCase = true)
            }
            ?: scripts.firstOrNull()
        _uiState.value = _uiState.value.copy(
            romPackage = romPackage,
            workRomDir = romPackage.rootDir,
            scripts = scripts,
            selectedScriptName = defaultScript?.name,
            romSelectionLocked = defaultScript != null,
            phase = Phase.Ready,
            progress = ProgressInfo(100, ""),
            errorMessage = null,
            flashTasks = defaultScript?.let(::buildFlashTasks).orEmpty(),
        )
    }

    fun markFlashing() {
        val script = _uiState.value.selectedScript
        _uiState.value = _uiState.value.copy(
            phase = Phase.Flashing,
            errorMessage = null,
            romSelectionLocked = true,
            flashTasks = script?.let(::buildFlashTasks).orEmpty(),
        )
    }

    fun onFlashProgress(progress: LineFlashProgress) {
        if (_uiState.value.phase != Phase.Flashing) return
        val index = (progress.currentStepIndex - 1).coerceAtLeast(0)
        val tasks = _uiState.value.flashTasks.mapIndexed { i, task ->
            when {
                i < index -> task.copy(status = FlashTaskStatus.Done, percent = 100)
                i == index -> task.copy(
                    status = FlashTaskStatus.Running,
                    percent = progress.percent.coerceIn(0, 100),
                )
                else -> task.copy(status = FlashTaskStatus.Pending, percent = 0)
            }
        }
        _uiState.value = _uiState.value.copy(
            flashTasks = tasks,
            progress = ProgressInfo(progress.percent, progress.stepDescription),
        )
    }

    fun markSuccess() {
        _uiState.value = _uiState.value.copy(
            flashTasks = _uiState.value.flashTasks.map {
                it.copy(status = FlashTaskStatus.Done, percent = 100)
            },
            phase = Phase.Success,
            progress = ProgressInfo(100, ""),
        )
    }

    fun markError(
        message: String,
        failedStepIndex: Int? = null,
        suggestRebootFastboot: Boolean = false,
    ) {
        val tasks = if (failedStepIndex != null && failedStepIndex > 0) {
            val failed = failedStepIndex - 1
            _uiState.value.flashTasks.mapIndexed { i, task ->
                when {
                    i < failed -> task.copy(status = FlashTaskStatus.Done, percent = 100)
                    i == failed -> task.copy(status = FlashTaskStatus.Failed)
                    else -> task.copy(status = FlashTaskStatus.Pending, percent = 0)
                }
            }
        } else {
            _uiState.value.flashTasks
        }
        val suggest = suggestRebootFastboot || looksLikeUsbSessionError(message)
        _uiState.value = _uiState.value.copy(
            flashTasks = tasks,
            errorMessage = message,
            phase = Phase.Error,
            suggestRebootFastboot = suggest,
        )
    }

    private fun looksLikeUsbSessionError(message: String): Boolean {
        return message.contains("USB 通信失败") ||
            message.contains("Fastboot 会话异常") ||
            message.contains("USB bulk", ignoreCase = true) ||
            message.contains("Failed to receive", ignoreCase = true) ||
            message.contains("Failed to send", ignoreCase = true)
    }

    fun clearForDeviceLost(message: String) {
        _uiState.value = UiSnapshot(
            romPath = _uiState.value.romPath,
            errorMessage = message.ifBlank { null },
        )
        clearTargetDevice()
    }

    fun resetAfterCancel() {
        _uiState.value = UiSnapshot(romPath = _uiState.value.romPath)
    }

    private fun buildFlashTasks(script: LineFlashScript): List<FlashTaskUi> {
        return script.steps.mapIndexed { index, step ->
            FlashTaskUi(
                id = "${script.name}-$index",
                stepIndex = index,
                kind = step.toTaskKind(),
                label = step.toTaskLabel(),
                status = FlashTaskStatus.Pending,
                percent = 0,
            )
        }
    }

    private fun LineFlashStep.toTaskKind(): FlashTaskKind = when (this) {
        is LineFlashStep.Flash, LineFlashStep.FlashChecksumList -> FlashTaskKind.Flash
        is LineFlashStep.Erase -> FlashTaskKind.Erase
        is LineFlashStep.CheckProduct,
        is LineFlashStep.CheckAntiRollback,
        is LineFlashStep.CheckSecurityPatch,
        -> FlashTaskKind.Check
        LineFlashStep.Reboot -> FlashTaskKind.Reboot
        LineFlashStep.OemLock -> FlashTaskKind.OemLock
        is LineFlashStep.SetActive -> FlashTaskKind.SetActive
        is LineFlashStep.Command -> FlashTaskKind.Command
    }

    private fun LineFlashStep.toTaskLabel(): String = when (this) {
        is LineFlashStep.CheckProduct -> "机型 ${products.joinToString("/")}"
        is LineFlashStep.CheckAntiRollback -> "AntiRollback ≤ $packageVersion"
        is LineFlashStep.CheckSecurityPatch -> "安全补丁 ≤ $packageLevel"
        LineFlashStep.FlashChecksumList -> "CRC 校验清单"
        is LineFlashStep.Erase -> partition
        is LineFlashStep.Flash -> fileName.ifBlank { partition }
        is LineFlashStep.SetActive -> slot
        LineFlashStep.Reboot -> "设备"
        LineFlashStep.OemLock -> "Bootloader"
        is LineFlashStep.Command -> command
    }

    @Suppress("EmptySuperCall")
    override fun onCleared() {
        super.onCleared()
        deviceWatchJob?.cancel()
        onRequestCancel = null
        FastbootManager.stopMonitor()
    }
}
