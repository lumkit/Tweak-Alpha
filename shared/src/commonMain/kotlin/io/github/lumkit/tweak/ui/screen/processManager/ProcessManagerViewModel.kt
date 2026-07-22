package io.github.lumkit.tweak.ui.screen.processManager

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.utils.ProcessUtils
import io.github.lumkit.tweak.model.ProcessInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.seconds

enum class ProcessFilterMode {
    AndroidUser,
    AndroidSystem,
    Android,
    Other,
    All,
}

enum class ProcessSortMode {
    Cpu,
    Res,
    Pid,
    /** 保持采样原始顺序，不额外排序 */
    None,
}

class ProcessManagerViewModel : BaseViewModel() {
    private val _supported = MutableStateFlow(false)
    val supported = _supported.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private val _processes = MutableStateFlow<List<ProcessInfo>>(emptyList())
    val processes = _processes.asStateFlow()

    private val _filterMode = MutableStateFlow(ProcessFilterMode.AndroidUser)
    val filterMode = _filterMode.asStateFlow()

    private val _sortMode = MutableStateFlow(ProcessSortMode.Cpu)
    val sortMode = _sortMode.asStateFlow()

    private val _searchMode = MutableStateFlow(false)
    val searchMode = _searchMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _detail = MutableStateFlow<ProcessInfo?>(null)
    val detail = _detail.asStateFlow()

    private val _detailLoading = MutableStateFlow(false)
    val detailLoading = _detailLoading.asStateFlow()

    val displayProcesses = combine(
        _processes,
        _filterMode,
        _sortMode,
        _searchQuery,
    ) { list, filter, sort, query ->
        val filtered = list
            .asSequence()
            .filter { matchesFilter(it, filter) }
            .filter { matchesQuery(it, query) }
        when (sort) {
            ProcessSortMode.None -> filtered.toList()
            else -> filtered.sortedWith(sortComparator(sort)).toList()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _supported.value = ProcessUtils.supported()
        }
    }

    fun setFilterMode(mode: ProcessFilterMode) {
        _filterMode.value = mode
    }

    fun setSortMode(mode: ProcessSortMode) {
        _sortMode.value = mode
    }

    fun setSearchMode(enabled: Boolean) {
        _searchMode.value = enabled
        if (!enabled) {
            _searchQuery.value = ""
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun startAutoRefresh() {
        if (refreshJob?.isActive == true) {
            return
        }
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshOnce()
                delay(3.seconds)
            }
        }
    }

    fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    fun refreshOnce() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!_supported.value) {
                _supported.value = ProcessUtils.supported()
            }
            if (!_supported.value) {
                _loading.value = false
                return@launch
            }
            _processes.value = ProcessUtils.getAllProcess()
            _loading.value = false
        }
    }

    fun openDetail(process: ProcessInfo) {
        viewModelScope.launch {
            _detailLoading.value = true
            _detail.value = process
            val detail = withContext(Dispatchers.IO) {
                ProcessUtils.getProcessDetail(process.pid)?.withAppMeta()
                    ?: process
            }
            _detail.value = detail
            _detailLoading.value = false
        }
    }

    fun dismissDetail() {
        _detail.value = null
        _detailLoading.value = false
    }

    fun killProcess(pid: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            ProcessUtils.killProcess(pid)
            dismissDetail()
            refreshOnce()
        }
    }

    fun killApp(process: ProcessInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            ProcessUtils.killProcess(process)
            dismissDetail()
            refreshOnce()
        }
    }

    fun indexOfTarget(
        list: List<ProcessInfo>,
        scrollToPackage: String,
        scrollToPid: Int,
    ): Int {
        if (scrollToPid > 0) {
            val byPid = list.indexOfFirst { it.pid == scrollToPid }
            if (byPid >= 0) {
                return byPid
            }
        }
        if (scrollToPackage.isNotBlank()) {
            val byPackage = list.indexOfFirst {
                it.appPackageName == scrollToPackage || it.name.startsWith("$scrollToPackage:")
            }
            if (byPackage >= 0) {
                return byPackage
            }
        }
        return -1
    }

    private fun matchesFilter(info: ProcessInfo, filter: ProcessFilterMode): Boolean {
        return when (filter) {
            ProcessFilterMode.AndroidUser -> info.isAndroidUserProcess
            ProcessFilterMode.AndroidSystem -> info.isSystemProcess
            ProcessFilterMode.Android -> info.isAndroidProcess
            ProcessFilterMode.Other -> info.isOtherProcess
            ProcessFilterMode.All -> true
        }
    }

    private fun matchesQuery(info: ProcessInfo, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) {
            return true
        }
        return info.displayName.contains(q, ignoreCase = true) ||
            info.name.contains(q, ignoreCase = true) ||
            info.user.contains(q, ignoreCase = true) ||
            info.command.contains(q, ignoreCase = true) ||
            info.cmdline.contains(q, ignoreCase = true) ||
            info.pid.toString().contains(q)
    }

    private fun sortComparator(sort: ProcessSortMode): Comparator<ProcessInfo> {
        return when (sort) {
            ProcessSortMode.Cpu -> compareByDescending { it.cpu }
            ProcessSortMode.Res -> compareByDescending { it.res }
            ProcessSortMode.Pid -> compareByDescending { it.pid }
            ProcessSortMode.None -> compareBy { 0 }
        }
    }
}
