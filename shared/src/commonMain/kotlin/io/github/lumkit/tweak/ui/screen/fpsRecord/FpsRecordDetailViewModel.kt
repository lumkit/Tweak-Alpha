package io.github.lumkit.tweak.ui.screen.fpsRecord

import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.base.LoadSlot
import io.github.lumkit.tweak.common.database.fps.repos.FpsRecordRepository
import io.github.lumkit.tweak.common.database.fps.table.FpsRecordDetailAggregate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FpsRecordDetailViewModel(sessionId: Long) : BaseViewModel() {

    private val repository by lazy { FpsRecordRepository() }

    private val loadDetailSlot = LoadSlot()

    private val _detail = MutableStateFlow<FpsRecordDetailAggregate?>(null)
    val detail = _detail.asStateFlow()

    init {
        suspendLaunch(loadDetailSlot) {
            loading()
            _detail.update {
                repository.queryRecordDetail(sessionId)
            }
            success()
        }
    }

}
