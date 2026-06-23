package io.github.lumkit.tweak.model

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.utils.TweakDataStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

object GlobalViewModel: BaseViewModel() {

    val runtimeModeState = TweakDataStore.runtimeModeFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val infoUpdateTimeSpanState = TweakDataStore.infoUpdateTimeSpanFlow()
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TweakDataStore.DEFAULT_INFO_UPDATE_TIME_SP_LEVEL
        )

    val infoUpdateTimeSpanMillisecondsState = TweakDataStore.infoUpdateTimeSpanFlow()
        .distinctUntilChanged()
        .map {
            (it * TweakDataStore.DEFAULT_INFO_UPDATE_TIME_SP_RANGE).toLong()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = (TweakDataStore.DEFAULT_INFO_UPDATE_TIME_SP_LEVEL * TweakDataStore.DEFAULT_INFO_UPDATE_TIME_SP_RANGE).toLong()
        )
}