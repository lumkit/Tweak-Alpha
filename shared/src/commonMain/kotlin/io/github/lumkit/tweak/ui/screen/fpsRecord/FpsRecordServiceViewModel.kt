package io.github.lumkit.tweak.ui.screen.fpsRecord

import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.database.fps.repos.FpsRecordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object FpsRecordServiceViewModel: BaseViewModel() {

    private val repository: FpsRecordRepository = FpsRecordRepository()

    private val _isRecordingState = MutableStateFlow(false)
    val isRecordingState = _isRecordingState.asStateFlow()

    fun startRecord() = suspendLaunch(
        id = "startRecord"
    ) {
        loading()


        _isRecordingState.value = true
        success()
    }

    fun stopRecord() = suspendLaunch(
        id = "stopRecord"
    ) {
        loading()

        _isRecordingState.value = false
        success()
    }
}