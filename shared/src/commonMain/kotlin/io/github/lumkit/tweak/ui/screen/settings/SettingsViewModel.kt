package io.github.lumkit.tweak.ui.screen.settings

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.utils.TweakDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel: BaseViewModel() {

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(false)
    val isIgnoringBatteryOptimizations = _isIgnoringBatteryOptimizations.asStateFlow()

    private val _notificationPermission = MutableStateFlow(false)
    val notificationPermission = _notificationPermission.asStateFlow()

    fun updateIsIgnoringBatteryOptimizations(isIgnoring: Boolean) {
        _isIgnoringBatteryOptimizations.value = isIgnoring
    }

    fun updateNotificationPermission(permission: Boolean) {
        _notificationPermission.value = permission
    }

    fun setApkExportDir(path: String) {
        viewModelScope.launch {
            TweakDataStore.setApkExportDir(path)
        }
    }
}