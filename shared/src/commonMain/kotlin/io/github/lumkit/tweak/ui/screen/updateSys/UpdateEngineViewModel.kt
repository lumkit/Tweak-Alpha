package io.github.lumkit.tweak.ui.screen.updateSys

import android.net.Uri
import androidx.compose.runtime.Immutable
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.feature.UpdateEngineEvent
import io.github.lumkit.tweak.common.feature.UpdateStatus
import io.github.lumkit.tweak.common.feature.commitCancelUpdate
import io.github.lumkit.tweak.common.feature.commitInstallRom
import io.github.lumkit.tweak.common.feature.commitMergeUpdate
import io.github.lumkit.tweak.common.feature.commitResetUpdate
import io.github.lumkit.tweak.common.feature.commitResumeUpdate
import io.github.lumkit.tweak.common.feature.commitSuspendUpdate
import io.github.lumkit.tweak.common.utils.logD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.CoroutineContext

object UpdateEngineViewModel: BaseViewModel() {

    private val _updateEvent = MutableStateFlow<UpdateEngineEvent?>(null)
    val updateEvent = _updateEvent.asStateFlow()
    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Unknown(-1, 0f))
    val updateStatus = _updateStatus.asStateFlow()
    private val _commandMillis = MutableStateFlow(-1L)
    val commandMillis = _commandMillis.asStateFlow()
    private val _updateError = MutableStateFlow<UpdateEngineEvent.PayloadComplete?>(null)
    val updateError = _updateError.asStateFlow()

    private val _unzipping = MutableStateFlow(false)
    val unzipping = _unzipping.asStateFlow()

    @Immutable
    data class Rom(
        val name: String,
        val uri: Uri,
        val size: Long,
    )

    private val _selectedRom = MutableStateFlow<Rom?>(null)
    val selectedRom = _selectedRom.asStateFlow()

    fun setUpdateEvent(event: UpdateEngineEvent?) {
        _updateEvent.value = event
        when (event) {
            is UpdateEngineEvent.CommandTook -> {
                _commandMillis.value = event.millis
            }
            is UpdateEngineEvent.PayloadComplete -> {
                _updateError.value = event
            }
            is UpdateEngineEvent.StatusUpdate -> {
                _updateStatus.value = event.status
                if (event.status is UpdateStatus.Idle) {
                    _updateError.value = null
                }
            }
            null -> Unit
        }

        logD("error code=${_updateError.value?.errorCode}, status=${_updateStatus.value}")
    }

    fun setUnzipping(unzipping: Boolean) {
        _unzipping.value = unzipping
    }

    fun installRom(path: String) {
        commitInstallRom(path)
    }

    fun cancel() {
        commitCancelUpdate()
    }

    fun merge() {
        commitMergeUpdate()
    }

    fun reset() {
        commitResetUpdate()
    }

    fun suspend() {
        commitSuspendUpdate()
    }

    fun resume() {
        commitResumeUpdate()
    }

    fun setSelectedRom(rom: Rom?) {
        _selectedRom.value = rom
    }

    fun launchTask(
        id: Any?,
        context: CoroutineContext = Dispatchers.Main,
        failed: suspend LoadStateCoroutineScope.(Throwable) -> Unit = { failure(it) },
        complete: suspend () -> Unit = {},
        block: suspend LoadStateCoroutineScope.() -> Unit,
    ) = suspendLaunch(id, context, failed, complete, block)
}