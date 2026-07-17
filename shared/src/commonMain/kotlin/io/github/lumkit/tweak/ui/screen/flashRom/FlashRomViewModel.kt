package io.github.lumkit.tweak.ui.screen.flashRom

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.feature.FastbootDevice
import io.github.lumkit.tweak.common.feature.FastbootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

object FlashRomViewModel : BaseViewModel() {

    data class Device(
        val fastbootDevice: FastbootDevice,
        val getVar: Map<String, String>,
        val id: String,
    )

    val fastBootDevices = FastbootManager.devices
        .map { devices ->
            if (devices.isEmpty()) {
                _targetFastbootDevice.value = null
            }
            coroutineScope {
                devices.map { device ->
                    async(Dispatchers.IO) {
                        val deviceName = device.deviceName.substringAfterLast("/")
                        if (device.hasPermission) {
                            val getVar = FastbootManager.getAllVars(device)
                            Device(device, getVar, getVar["serialno"] ?: deviceName)
                        } else {
                            Device(device, emptyMap(), deviceName)
                        }
                    }
                }.awaitAll()
            }
        }.flowOn(Dispatchers.IO)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
        )

    private val _targetFastbootDevice = MutableStateFlow<Device?>(null)
    val targetFastbootDevice = _targetFastbootDevice.asStateFlow()

    private val _romPath = MutableStateFlow("")
    val romPath = _romPath.asStateFlow()

    init {
        FastbootManager.startMonitor()
    }

    fun setTargetFastbootDevice(device: Device) {
        _targetFastbootDevice.value = device
    }

    fun setRomPath(path: String) {
        _romPath.value = path
    }

    @Suppress("EmptySuperCall")
    override fun onCleared() {
        super.onCleared()
        FastbootManager.stopMonitor()
    }
}