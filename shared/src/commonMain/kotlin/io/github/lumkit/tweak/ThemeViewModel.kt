package io.github.lumkit.tweak

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.base.BaseViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okio.FileSystem
import okio.Path.Companion.toPath
import top.yukonga.miuix.kmp.theme.ColorSchemeMode

class ThemeViewModel : BaseViewModel() {

    val themeMode: StateFlow<ColorSchemeMode> = TweakDataStore.themeModeFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ColorSchemeMode.System
        )

    val backgroundImagePath: StateFlow<String?> = TweakDataStore.backgroundImagePathFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val enabledBackgroundImage: StateFlow<Boolean> = TweakDataStore.backgroundImagePathFlow()
        .map { uri ->
            runCatching {
                uri?.let {
                    FileSystem.SYSTEM.exists(it.toPath())
                } ?: false
            }.getOrDefault(false)
        }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    suspend fun setThemeMode(mode: ColorSchemeMode) {
        TweakDataStore.setThemeMode(mode)
    }

    suspend fun setBackgroundImagePath(path: String) {
        TweakDataStore.setBackgroundImagePath(path)
    }
}