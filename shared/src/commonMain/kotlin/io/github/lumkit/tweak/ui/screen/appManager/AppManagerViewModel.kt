package io.github.lumkit.tweak.ui.screen.appManager

import androidx.lifecycle.viewModelScope
import io.github.lumkit.tweak.common.base.BaseViewModel
import io.github.lumkit.tweak.common.feature.commitStartExtractApk
import io.github.lumkit.tweak.common.utils.AppInfo
import io.github.lumkit.tweak.common.utils.AppOperationResult
import io.github.lumkit.tweak.common.utils.AppState
import io.github.lumkit.tweak.common.utils.AppsHelper
import io.github.lumkit.tweak.common.utils.TweakDataStore
import io.github.lumkit.tweak.common.utils.logD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import tweak_alpha.shared.generated.resources.Res
import tweak_alpha.shared.generated.resources.text_app_uninstall
import tweak_alpha.shared.generated.resources.text_dialog_running_task
import tweak_alpha.shared.generated.resources.text_enable_app
import tweak_alpha.shared.generated.resources.text_extract_apk_already_running
import tweak_alpha.shared.generated.resources.text_force_app_failed
import tweak_alpha.shared.generated.resources.text_force_app_partial_success
import tweak_alpha.shared.generated.resources.text_force_app_success
import tweak_alpha.shared.generated.resources.text_operation_batch_failed
import tweak_alpha.shared.generated.resources.text_operation_batch_partial_success
import tweak_alpha.shared.generated.resources.text_operation_batch_success
import tweak_alpha.shared.generated.resources.text_unable_app

class AppManagerViewModel: BaseViewModel() {

    companion object {
        private const val TAG = "AppManagerViewModel"
    }

    private val _loadingState = MutableStateFlow(false)
    val loadingState = _loadingState.asStateFlow()

    private val _loadingTextRes = MutableStateFlow(Res.string.text_dialog_running_task)
    val loadingTextRes = _loadingTextRes.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps = _allApps.asStateFlow()

    private val _userApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val userApps = _userApps.asStateFlow()

    private val _systemApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val systemApps = _systemApps.asStateFlow()

    private val _unabledApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val unabledApps = _unabledApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchMode = MutableStateFlow(false)
    val searchMode = _searchMode.asStateFlow()

    private val _selectedMode = MutableStateFlow(false)
    val selectedMode = _selectedMode.asStateFlow()

    private val _selectedApps = MutableStateFlow<Set<String>>(emptySet())
    val selectedApps = _selectedApps.asStateFlow()

    private val _selectableAppPackageNames = MutableStateFlow<Set<String>>(emptySet())
    val selectableAppPackageNames = _selectableAppPackageNames.asStateFlow()

    val hasSelectedAllPackageNames = combine(selectedApps, selectableAppPackageNames) { selected, selectable ->
        selectable.isNotEmpty() && selected.size == selectable.size
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false
    )

    private val _targetAppInfo = MutableStateFlow<AppInfo?>(null)
    val targetAppInfo = _targetAppInfo.asStateFlow()

    init {
        viewModelScope.launch {
            AppsHelper.apps.collect {
                logD("app list=$it", TAG)
                _allApps.value = it.filter { info -> info.state == AppState.ENABLED }
                _userApps.value = it.filter { info -> info.isSystemApp.not() && info.state == AppState.ENABLED }
                _systemApps.value = it.filter { info -> info.isSystemApp && info.state == AppState.ENABLED }
                _unabledApps.value = it.filter { info -> info.state != AppState.ENABLED }
                _selectableAppPackageNames.value = it.map { info -> info.packageName }.toSet()
            }
        }
    }

    fun setSearchMode(searchMode: Boolean) {
        _searchMode.value = searchMode
    }

    fun setSearchQuery(searchQuery: String) {
        _searchQuery.value = searchQuery
    }

    fun setSelectedMode(selectedMode: Boolean) {
        _selectedMode.value = selectedMode
    }

    fun setSelectableAppPackageNames(selectableAppPackageNames: Set<String>) {
        _selectableAppPackageNames.value = selectableAppPackageNames
    }

    fun toggleSelectedAppInfo(packageName: String) {
        _selectedApps.update { selected ->
            if (packageName in selected) selected - packageName else selected + packageName
        }
    }

    fun toggleSelectedAllApps() {
        val selectable = selectableAppPackageNames.value.toSet()
        if (selectable.isEmpty()) {
            cleanSelectedPackageNames()
            return
        }
        _selectedApps.update { selected ->
            val current = selected.intersect(selectable)
            if (current.size == selectable.size) {
                emptySet()
            } else {
                selectable
            }
        }
    }

    fun cleanSelectedPackageNames() {
        _selectedApps.value = emptySet()
    }

    fun syncAppsOnResume() {
        viewModelScope.launch {
            AppsHelper.refresh()
        }
    }

    private data class BatchOperationSummary(
        val successCount: Int,
        val failures: List<Pair<String, String>>,
    )

    private fun consumeSelectedPackages(): List<String> {
        val selectedApps = _selectedApps.value.toList()
        _selectedMode.value = false
        _selectedApps.value = emptySet()
        return selectedApps
    }

    private suspend fun runBatchOperation(
        packages: List<String>,
        operation: suspend (String) -> AppOperationResult,
    ): BatchOperationSummary {
        var successCount = 0
        val failures = mutableListOf<Pair<String, String>>()

        packages.forEach { packageName ->
            when (val result = operation(packageName)) {
                AppOperationResult.Success -> {
                    successCount++
                }

                is AppOperationResult.Failure -> {
                    failures += packageName to result.message
                }
            }
        }

        return BatchOperationSummary(
            successCount = successCount,
            failures = failures,
        )
    }

    private suspend fun buildBatchOperationMessage(
        actionName: String,
        summary: BatchOperationSummary,
    ): String? {
        return when {
            summary.successCount == 0 && summary.failures.isEmpty() -> null
            summary.failures.isEmpty() -> {
                getString(Res.string.text_operation_batch_success).format(actionName, summary.successCount)
            }

            summary.successCount > 0 -> {
                getString(Res.string.text_operation_batch_partial_success).format(
                    actionName,
                    summary.successCount,
                    summary.failures.size,
                )
            }

            else -> {
                val firstFailure = summary.failures.firstOrNull()
                buildString {
                    append(getString(Res.string.text_operation_batch_failed).format(actionName))
                    firstFailure?.let { (packageName, message) ->
                        append("：")
                        append(packageName)
                        if (message.isNotBlank()) {
                            append("，")
                            append(message)
                        }
                    }
                }
            }
        }
    }

    fun forceKillSelectedApps() = suspendLaunch(
        id = "forceKillSelectedApps",
        complete = {
            _loadingState.value = false
        }
    ) {
        loading()
        _loadingState.value = true
        val selectedApps = consumeSelectedPackages()
        _loadingTextRes.value = Res.string.text_dialog_running_task

        if (selectedApps.isEmpty()) {
            success()
            return@suspendLaunch
        }

        val summary = runBatchOperation(selectedApps, AppsHelper::forceStop)
        success(buildForceKillMessage(summary))
    }

    fun forceKillApp(packageName: String) = suspendLaunch(
        id = "forceKillApp",
        complete = {
            _loadingState.value = false
        }
    ) {
        loading()
        _loadingState.value = true
        _loadingTextRes.value = Res.string.text_dialog_running_task
        val summary = runBatchOperation(listOf(packageName), AppsHelper::forceStop)
        success(buildForceKillMessage(summary))
    }

    fun unableSelectedApps() = suspendLaunch(
        id = "unableSelectedApps",
        complete = {
            _loadingState.value = false
        }
    ) {
        loading()
        _loadingState.value = true
        val selectedApps = consumeSelectedPackages()
        _loadingTextRes.value = Res.string.text_dialog_running_task

        if (selectedApps.isEmpty()) {
            success()
            return@suspendLaunch
        }

        val summary = runBatchOperation(selectedApps) { packageName ->
            AppsHelper.setFrozen(packageName, true)
        }
        success(buildBatchOperationMessage(getString(Res.string.text_unable_app), summary))
    }

    fun enableSelectedApps() = suspendLaunch(
        id = "enableSelectedApps",
        complete = {
            _loadingState.value = false
        }
    ) {
        loading()
        _loadingState.value = true
        val selectedApps = consumeSelectedPackages()
        _loadingTextRes.value = Res.string.text_dialog_running_task

        if (selectedApps.isEmpty()) {
            success()
            return@suspendLaunch
        }

        val stateByPackage = AppsHelper.apps.value.associateBy(
            keySelector = { it.packageName },
            valueTransform = { it.state },
        )
        val summary = runBatchOperation(selectedApps) { packageName ->
            restoreApp(packageName, stateByPackage[packageName])
        }
        success(buildBatchOperationMessage(getString(Res.string.text_enable_app), summary))
    }

    fun freezeApp(packageName: String) = suspendLaunch(
        id = "unableApp",
        complete = {
            _loadingState.value = false
        }
    ) {
        loading()
        _loadingState.value = true
        _loadingTextRes.value = Res.string.text_dialog_running_task
        val summary = runBatchOperation(listOf(packageName)) { pkg ->
            AppsHelper.setFrozen(pkg, true)
        }
        success(buildBatchOperationMessage(getString(Res.string.text_unable_app), summary))
    }

    fun enableApp(packageName: String, state: AppState) = suspendLaunch(
        id = "enableApp",
        complete = {
            _loadingState.value = false
        }
    ) {
        loading()
        _loadingState.value = true
        _loadingTextRes.value = Res.string.text_dialog_running_task
        val summary = runBatchOperation(listOf(packageName)) { pkg ->
            restoreApp(pkg, state)
        }
        success(buildBatchOperationMessage(getString(Res.string.text_enable_app), summary))
    }

    fun uninstallSelectedApps() = suspendLaunch(
        id = "uninstallSelectedApps",
        complete = {
            _loadingState.value = false
        }
    ) {
        loading()
        _loadingState.value = true
        val selectedApps = consumeSelectedPackages()
        _loadingTextRes.value = Res.string.text_dialog_running_task

        if (selectedApps.isEmpty()) {
            success()
            return@suspendLaunch
        }

        val summary = runBatchOperation(selectedApps, AppsHelper::uninstall)
        success(buildBatchOperationMessage(getString(Res.string.text_app_uninstall), summary))
    }

    fun uninstallApp(packageName: String) = suspendLaunch(
        id = "uninstallApp",
        complete = {
            _loadingState.value = false
        }
    ) {
        loading()
        _loadingState.value = true
        _loadingTextRes.value = Res.string.text_dialog_running_task
        val summary = runBatchOperation(listOf(packageName), AppsHelper::uninstall)
        success(buildBatchOperationMessage(getString(Res.string.text_app_uninstall), summary))
    }

    fun setTargetAppInfo(info: AppInfo?){
        _targetAppInfo.value = info
    }

    /**
     * 启动单个应用的安装包提取。
     */
    fun extractApk(packageName: String, appName: String) = suspendLaunch(
        id = "extractApk",
    ) {
        startExtractApk(
            listOf(
                ExtractApkTask(
                    packageName = packageName,
                    appName = appName,
                ),
            ),
        )
    }

    /**
     * 多选批量提取安装包：串行执行，共用一个进度 Dialog。
     */
    fun extractSelectedApps() = suspendLaunch(
        id = "extractApk",
    ) {
        val packages = consumeSelectedPackages()
        if (packages.isEmpty()) {
            return@suspendLaunch
        }
        val appsByPackage = AppsHelper.apps.value.associateBy { it.packageName }
        val tasks = packages.map { packageName ->
            val info = appsByPackage[packageName]
            ExtractApkTask(
                packageName = packageName,
                appName = info?.appName?.takeIf(String::isNotBlank) ?: packageName,
            )
        }
        startExtractApk(tasks)
    }

    private suspend fun BaseViewModel.LoadStateCoroutineScope.startExtractApk(
        tasks: List<ExtractApkTask>,
    ) {
        if (tasks.isEmpty()) {
            return
        }
        if (ExtractApkSession.isRunning()) {
            success(getString(Res.string.text_extract_apk_already_running))
            return
        }
        val targetDir = TweakDataStore.apkExportDir()
        setTargetAppInfo(null)
        commitStartExtractApk(
            tasks = tasks,
            targetDir = targetDir,
        )
        success()
    }

    /**
     * 按当前状态恢复应用：
     * - [AppState.DISABLED] 走 [AppsHelper.setDisabled]
     * - [AppState.FROZEN] 走 [AppsHelper.setFrozen]
     */
    private suspend fun restoreApp(
        packageName: String,
        state: AppState?,
    ): AppOperationResult {
        return when (state) {
            AppState.DISABLED -> AppsHelper.setDisabled(packageName, disabled = false)
            AppState.FROZEN -> AppsHelper.setFrozen(packageName, frozen = false)
            AppState.ENABLED, null -> AppsHelper.setFrozen(packageName, frozen = false)
        }
    }

    private suspend fun buildForceKillMessage(summary: BatchOperationSummary): String {
        return when {
            summary.failures.isEmpty() -> getString(Res.string.text_force_app_success)
                .format(summary.successCount)
            summary.successCount > 0 -> getString(Res.string.text_force_app_partial_success)
                .format(summary.successCount, summary.failures.size)
            else -> {
                val firstFailure = summary.failures.firstOrNull()
                buildString {
                    append(getString(Res.string.text_force_app_failed))
                    firstFailure?.let { (packageName, failureMessage) ->
                        append("：")
                        append(packageName)
                        if (failureMessage.isNotBlank()) {
                            append("，")
                            append(failureMessage)
                        }
                    }
                }
            }
        }
    }
}
