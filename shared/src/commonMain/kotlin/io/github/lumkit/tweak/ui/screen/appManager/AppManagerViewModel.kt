package io.github.lumkit.tweak.ui.screen.appManager

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.utils.AppInfo
import io.github.lumkit.tweak.common.utils.AppsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppManagerViewModel: BaseViewModel() {

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps = _allApps.asStateFlow()

    private val _userApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val userApps = _userApps.asStateFlow()

    private val _systemApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val systemApps = _systemApps.asStateFlow()

    init {
        viewModelScope.launch {
            AppsHelper.apps.collect {
                _allApps.value = it
                _userApps.value = it.filter { info -> info.isSystemApp.not() }
                _systemApps.value = it.filter { info -> info.isSystemApp }
            }
        }
    }

}