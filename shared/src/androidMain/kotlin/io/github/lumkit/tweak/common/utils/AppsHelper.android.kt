package io.github.lumkit.tweak.common.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.Const
import io.github.lumkit.tweak.common.shell.ReusableShells
import io.github.lumkit.tweak.model.RuntimeModeStore
import io.github.lumkit.tweak.model.RuntimeMode
import io.github.lumkit.tweak.model.asNativeFileBackend
import io.github.lumkit.tweak.sharednative.NativeFileBundles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Clock

private const val TAG = "AppsHelper"

actual object AppsHelper {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val iconCacheDir: String
        get() {
            val dir = File(Const.Path.cachePath, "apps/icons")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir.absolutePath
        }

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    actual val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    actual val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart?.takeIf(String::isNotBlank) ?: return
            scope.launch { handlePackageBroadcast(intent.action, packageName, intent) }
        }
    }

    private var isInit = false

    actual fun init() {
        if (isInit) return
        isInit = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(
            application,
            packageReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )

        scope.launch { refresh() }
    }

    actual fun getIconPath(packageName: String): String {
        return File(iconCacheDir, "$packageName.webp").absolutePath
    }

    actual suspend fun refresh() {
        withContext(Dispatchers.IO) {
            if (_isRefreshing.value) return@withContext
            _isRefreshing.value = true
            try {
                val time = Clock.System.now().toEpochMilliseconds()
                val pm = application.packageManager
                val cacheDir = iconCacheDir

                val list = fetchInstalledAppsViaBinder(pm, cacheDir)
                    ?: loadInstalledAppsDirectly(pm, cacheDir)

                _apps.value = list.sortedBy { it.appName }
                logD(
                    "AppsHelper refresh cost ${Clock.System.now().toEpochMilliseconds() - time}ms, ${list.size} apps",
                    TAG,
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private suspend fun handlePackageBroadcast(
        action: String?,
        packageName: String,
        intent: Intent,
    ) = withContext(Dispatchers.IO) {
        if (_isRefreshing.value) return@withContext
        val time = Clock.System.now().toEpochMilliseconds()
        when (action) {
            Intent.ACTION_PACKAGE_REMOVED -> {
                if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                    val existing = _apps.value.find { it.packageName == packageName }
                    if (existing?.isSystemApp == true) {
                        markSystemAppUninstalled(packageName)
                    } else {
                        removePackage(packageName)
                    }
                    logD(
                        "AppsHelper remove package cost ${Clock.System.now().toEpochMilliseconds() - time}ms, $packageName",
                        TAG,
                    )
                }
            }

            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_CHANGED,
            -> {
                upsertPackage(packageName)
                logD(
                    "AppsHelper upsert package cost ${Clock.System.now().toEpochMilliseconds() - time}ms, $packageName",
                    TAG,
                )
            }
        }
    }

    private suspend fun fetchInstalledAppsViaBinder(
        pm: PackageManager,
        cacheDir: String,
    ): List<AppInfo>? = withContext(Dispatchers.IO) {
        val runtimeMode = RuntimeModeStore.mode.filterNotNull().first()
        val bundle = runCatching {
            when (runtimeMode) {
                RuntimeMode.Root -> RootFileServiceConnectionManager.getService().listInstalledApps()
                RuntimeMode.Shizuku -> ShizukuFileServiceConnectionManager.getService().listInstalledApps()
                RuntimeMode.Unknow -> null
            }
        }.getOrElse { throwable ->
            logE("fetchInstalledAppsViaBinder failed: ${throwable.message}", throwable, TAG)
            return@withContext null
        } ?: return@withContext null

        bundle.classLoader = javaClass.classLoader
        if (!bundle.getBoolean(NativeFileBundles.KEY_SUCCESS)) {
            logE("fetchInstalledAppsViaBinder failed: ${bundle.getString(NativeFileBundles.KEY_MESSAGE)}", null, TAG)
            return@withContext null
        }

        val entries = bundle.getParcelableArrayListCompat(NativeFileBundles.KEY_BUNDLE_LIST).orEmpty()
        coroutineScope {
            entries.map { appBundle ->
                async {
                    runCatching { appBundle.toAppInfo(pm, cacheDir) }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun fetchAppInfoViaBinder(
        packageName: String,
        pm: PackageManager,
        cacheDir: String,
    ): AppInfo? = withContext(Dispatchers.IO) {
        val runtimeMode = RuntimeModeStore.mode.filterNotNull().first()
        val bundle = runCatching {
            when (runtimeMode) {
                RuntimeMode.Root -> RootFileServiceConnectionManager.getService().getInstalledApp(packageName)
                RuntimeMode.Shizuku -> ShizukuFileServiceConnectionManager.getService().getInstalledApp(packageName)
                RuntimeMode.Unknow -> null
            }
        }.getOrElse { throwable ->
            logE("fetchAppInfoViaBinder failed: ${throwable.message}", throwable, TAG)
            return@withContext null
        } ?: return@withContext null

        bundle.classLoader = javaClass.classLoader
        if (!bundle.getBoolean(NativeFileBundles.KEY_SUCCESS)) {
            logE("fetchAppInfoViaBinder failed: ${bundle.getString(NativeFileBundles.KEY_MESSAGE)}", null, TAG)
            return@withContext null
        }

        bundle.getBundle(NativeFileBundles.KEY_BUNDLE)?.toAppInfo(pm, cacheDir)
    }

    private suspend fun loadInstalledAppsDirectly(
        pm: PackageManager,
        cacheDir: String,
    ): List<AppInfo> = withContext(Dispatchers.IO) {
        val matchFlags = installedPackagesMatchFlags()
        val packageInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(matchFlags))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(matchFlags.toInt())
        }

        coroutineScope {
            packageInfos.map { packageInfo ->
                async {
                    runCatching { packageInfo.toAppInfo(pm, cacheDir) }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun loadSingleInstalledAppDirectly(
        packageName: String,
        pm: PackageManager,
        cacheDir: String,
    ): AppInfo? = withContext(Dispatchers.IO) {
        val packageInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(installedPackagesMatchFlags()),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, installedPackagesMatchFlags().toInt())
            }
        }.getOrNull() ?: return@withContext null
        runCatching { packageInfo.toAppInfo(pm, cacheDir) }.getOrNull()
    }

    private suspend fun resolveInstalledApp(
        packageName: String,
        pm: PackageManager = application.packageManager,
        cacheDir: String = iconCacheDir,
    ): AppInfo? {
        return fetchAppInfoViaBinder(packageName, pm, cacheDir)
            ?: loadSingleInstalledAppDirectly(packageName, pm, cacheDir)
    }

    private suspend fun upsertPackage(packageName: String): AppInfo? {
        val pm = application.packageManager
        val cacheDir = iconCacheDir
        val appInfo = resolveInstalledApp(packageName, pm, cacheDir) ?: return null
        _apps.value = _apps.value
            .filterNot { it.packageName == packageName }
            .plus(appInfo)
            .sortedBy { it.appName }
        return appInfo
    }

    private fun removePackage(packageName: String) {
        File(iconCacheDir, "$packageName.webp").takeIf(File::exists)?.delete()
        _apps.value = _apps.value.filterNot { it.packageName == packageName }
    }

    private fun PackageInfo.toAppInfo(pm: PackageManager, cacheDir: String): AppInfo {
        val appInfo = applicationInfo ?: error("applicationInfo is null for $packageName")
        val result = buildAppInfo(
            pm = pm,
            cacheDir = cacheDir,
            packageName = packageName,
            appName = runCatching { appInfo.loadLabel(pm).toString() }.getOrNull().orEmpty(),
            versionName = versionName.orEmpty(),
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                longVersionCode
            } else {
                @Suppress("DEPRECATION")
                versionCode.toLong()
            },
            uid = appInfo.uid,
            dataDir = appInfo.dataDir.orEmpty(),
            sourceDir = appInfo.sourceDir.orEmpty(),
            minSdk = appInfo.minSdkVersion,
            targetSdk = appInfo.targetSdkVersion,
            firstInstallTime = firstInstallTime,
            lastUpdateTime = lastUpdateTime,
            abiList = appInfo.resolveAbiList(),
            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            state = resolveState(pm, packageName, appInfo),
        )
        if (result.state == AppState.UNINSTALLED && !result.isSystemApp) {
            error("skip uninstalled user package $packageName")
        }
        return result
    }

    private fun Bundle.toAppInfo(pm: PackageManager, cacheDir: String): AppInfo {
        val packageName = getString(NativeFileBundles.KEY_PACKAGE_NAME).orEmpty()
        val state = runCatching {
            AppState.valueOf(getString(NativeFileBundles.KEY_APP_STATE).orEmpty())
        }.getOrDefault(AppState.ENABLED)
        val result = buildAppInfo(
            pm = pm,
            cacheDir = cacheDir,
            packageName = packageName,
            appName = getString(NativeFileBundles.KEY_APP_NAME).orEmpty(),
            versionName = getString(NativeFileBundles.KEY_VERSION_NAME).orEmpty(),
            versionCode = getLong(NativeFileBundles.KEY_VERSION_CODE),
            uid = getInt(NativeFileBundles.KEY_UID),
            dataDir = getString(NativeFileBundles.KEY_DATA_DIR).orEmpty(),
            sourceDir = getString(NativeFileBundles.KEY_SOURCE_DIR).orEmpty(),
            minSdk = getInt(NativeFileBundles.KEY_MIN_SDK),
            targetSdk = getInt(NativeFileBundles.KEY_TARGET_SDK),
            firstInstallTime = getLong(NativeFileBundles.KEY_FIRST_INSTALL_TIME),
            lastUpdateTime = getLong(NativeFileBundles.KEY_LAST_UPDATE_TIME),
            abiList = getStringArrayList(NativeFileBundles.KEY_ABI_LIST)
                ?.mapNotNull(::parseAppAbi)
                .orEmpty(),
            isSystemApp = getBoolean(NativeFileBundles.KEY_IS_SYSTEM_APP),
            isUpdatedSystemApp = getBoolean(NativeFileBundles.KEY_IS_UPDATED_SYSTEM_APP),
            state = state,
        )
        if (result.state == AppState.UNINSTALLED && !result.isSystemApp) {
            error("skip uninstalled user package $packageName")
        }
        return result
    }

    private fun buildAppInfo(
        pm: PackageManager,
        cacheDir: String,
        packageName: String,
        appName: String,
        versionName: String,
        versionCode: Long,
        uid: Int,
        dataDir: String,
        sourceDir: String,
        minSdk: Int,
        targetSdk: Int,
        firstInstallTime: Long,
        lastUpdateTime: Long,
        abiList: List<AppAbi>,
        isSystemApp: Boolean,
        isUpdatedSystemApp: Boolean,
        state: AppState,
    ): AppInfo {
        val iconFile = File(cacheDir, "$packageName.webp")
        cacheIconIfNeeded(pm, packageName, sourceDir, lastUpdateTime, iconFile)
        return AppInfo(
            packageName = packageName,
            appName = appName.ifBlank { packageName },
            versionName = versionName,
            versionCode = versionCode,
            uid = uid,
            dataDir = dataDir,
            sourceDir = sourceDir,
            minSdk = minSdk,
            targetSdk = targetSdk,
            firstInstallTime = firstInstallTime,
            lastUpdateTime = lastUpdateTime,
            abiList = abiList,
            iconPath = iconFile.absolutePath,
            isSystemApp = isSystemApp,
            isUpdatedSystemApp = isUpdatedSystemApp,
            state = state,
        )
    }

    private fun cacheIconIfNeeded(
        pm: PackageManager,
        packageName: String,
        sourceDir: String,
        lastUpdateTime: Long,
        iconFile: File,
    ) {
        if (iconFile.exists() && iconFile.lastModified() >= lastUpdateTime) {
            return
        }
        runCatching {
            val drawable = loadAppIcon(pm, packageName, sourceDir) ?: return
            val bitmap = drawable.toBitmap(144, 144)
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            FileOutputStream(iconFile).use { out ->
                bitmap.compress(format, 80, out)
            }
        }
    }

    private fun loadAppIcon(
        pm: PackageManager,
        packageName: String,
        sourceDir: String,
    ) = runCatching {
        pm.getApplicationIcon(packageName)
    }.getOrNull() ?: runCatching {
        val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(sourceDir, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(sourceDir, 0)
        }
        val appInfo = archiveInfo?.applicationInfo ?: return@runCatching null
        appInfo.sourceDir = sourceDir
        appInfo.publicSourceDir = sourceDir
        appInfo.loadIcon(pm)
    }.getOrNull()

    private fun resolveState(
        pm: PackageManager,
        packageName: String,
        appInfo: ApplicationInfo,
    ): AppState {
        if (appInfo.isUninstalledForCurrentUser()) {
            return AppState.UNINSTALLED
        }
        val setting = runCatching { pm.getApplicationEnabledSetting(packageName) }
            .getOrNull() ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        return when (setting) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> AppState.FROZEN
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> AppState.DISABLED
            else -> if (appInfo.enabled) AppState.ENABLED else AppState.DISABLED
        }
    }

    actual suspend fun uninstall(packageName: String): AppOperationResult {
        if (isProtectedPackage(packageName)) {
            return AppOperationResult.Failure("该应用不允许卸载")
        }
        val info = _apps.value.find { it.packageName == packageName }
        return if (info?.isSystemApp == true) {
            uninstallSystemAppForCurrentUser(info)
        } else {
            runPrivileged("uninstall", "pm uninstall ${shellQuote(packageName)}") {
                removePackage(packageName)
            }
        }
    }

    actual suspend fun restoreSystemApp(packageName: String): AppOperationResult {
        val info = _apps.value.find { it.packageName == packageName }
        val commands = buildList {
            add("cmd package install-existing --user 0 ${shellQuote(packageName)}")
            add("pm install-existing --user 0 ${shellQuote(packageName)}")
            val sourceDir = info?.sourceDir.orEmpty()
            if (sourceDir.isNotBlank()) {
                add("pm install -r ${shellQuote(sourceDir)}")
            }
        }
        return runPrivilegedFallback(
            operation = "restoreSystemApp",
            commands = commands,
            onSuccess = { upsertPackage(packageName) },
        )
    }

    actual suspend fun extractApk(
        packageName: String,
        targetDir: String,
        onProgress: ((copiedBytes: Long, totalBytes: Long, fileName: String) -> Unit)?,
    ): AppOperationResult = withContext(Dispatchers.IO) {
        val info = _apps.value.find { it.packageName == packageName }
            ?: return@withContext AppOperationResult.Failure("未找到应用：$packageName")

        val appInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0L),
                )
            } else {
                @Suppress("DEPRECATION")
                application.packageManager.getApplicationInfo(packageName, 0)
            }
        }.getOrNull()

        val sources = buildList {
            val base = appInfo?.sourceDir ?: info.sourceDir
            if (base.isNotBlank()) add(base)
            appInfo?.splitSourceDirs?.forEach { if (!it.isNullOrBlank()) add(it) }
        }
        if (sources.isEmpty()) {
            return@withContext AppOperationResult.Failure("未找到 APK 源文件")
        }

        val runtimeMode = RuntimeModeStore.mode.filterNotNull().first()
        val backend = runtimeMode.asNativeFileBackend()
        if (backend == NativeFileBackend.User) {
            return@withContext AppOperationResult.Failure("当前运行模式不支持特权文件提取")
        }

        val destDir = targetDir.trimEnd('/')
        Files.mkdirs(destDir).getOrNull()
            ?: return@withContext AppOperationResult.Failure("无法创建目标目录：$destDir")

        val totalBytes = sources.sumOf { path ->
            Files.length(path).getOrNull()?.coerceAtLeast(0L) ?: 0L
        }.coerceAtLeast(1L)

        var copiedBytes = 0L
        val buffer = ByteArray(64 * 1024)
        var lastFileName = ""
        try {
            for (source in sources) {
                val fileName = buildExtractedApkFileName(
                    appName = info.appName.ifBlank { packageName },
                    versionName = info.versionName,
                    versionCode = info.versionCode,
                    sourcePath = source,
                    sourceCount = sources.size,
                )
                lastFileName = fileName
                val destPath = destDir joinPath fileName
                onProgress?.invoke(copiedBytes, totalBytes, fileName)

                openPrivilegedReadOnlyFd(backend, source).use { readPfd ->
                    openPrivilegedWriteOnlyFd(
                        backend = backend,
                        path = destPath,
                        create = true,
                        truncate = true,
                    ).use { writePfd ->
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(readPfd).use { input ->
                            android.os.ParcelFileDescriptor.AutoCloseOutputStream(writePfd).use { output ->
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    output.write(buffer, 0, read)
                                    copiedBytes += read
                                    onProgress?.invoke(copiedBytes, totalBytes, fileName)
                                }
                                output.flush()
                            }
                        }
                    }
                }
            }
            onProgress?.invoke(totalBytes, totalBytes, lastFileName)
            AppOperationResult.Success
        } catch (throwable: Throwable) {
            AppOperationResult.Failure(throwable.message ?: "提取失败")
        }
    }

    actual suspend fun setDisabled(packageName: String, disabled: Boolean): AppOperationResult {
        val command = if (disabled) "pm disable $packageName" else "pm enable $packageName"
        return runPrivileged("setDisabled", command) { upsertPackage(packageName) }
    }

    actual suspend fun setFrozen(packageName: String, frozen: Boolean): AppOperationResult {
        val command = if (frozen) {
            "pm disable-user --user 0 $packageName"
        } else {
            "pm enable $packageName"
        }
        val result = runPrivileged("setFrozen", command) { upsertPackage(packageName) }
        if (!frozen || result is AppOperationResult.Failure) {
            return result
        }

        val appInfo = apps.value.find { it.packageName == packageName }
        return if (appInfo?.state != AppState.ENABLED) {
            AppOperationResult.Success
        } else {
            if (appInfo.isSystemApp) {
                AppOperationResult.Failure("冻结失败：系统应用不能被冻结")
            } else {
                AppOperationResult.Failure("冻结失败")
            }
        }
    }

    actual suspend fun forceStop(packageName: String): AppOperationResult = withContext(Dispatchers.IO) {
        val commands = listOf(
            "am force-stop --user 0 '$packageName'",
            "am force-stop '$packageName'",
        )
        runPrivilegedFallback(
            operation = "forceStop",
            commands = commands,
            onSuccess = { }
        )
    }

    actual suspend fun launch(packageName: String): AppOperationResult = withContext(Dispatchers.IO) {
        val component = runCatching {
            application.packageManager.getLaunchIntentForPackage(packageName)?.component
        }.getOrNull() ?: return@withContext AppOperationResult.Failure("未找到可启动入口：$packageName")

        val target = "${component.packageName}/${component.className}"
        val output = runCatching {
            ReusableShells.execSync("am start -n '$target'")
        }.getOrElse { return@withContext AppOperationResult.Failure(it.message ?: "启动失败") }

        if (output.contains("Error", ignoreCase = true) ||
            output.contains("Exception", ignoreCase = true)
        ) {
            AppOperationResult.Failure(output.ifBlank { "启动失败" })
        } else {
            AppOperationResult.Success
        }
    }

    /**
     * 执行特权 `pm` 命令并根据输出判定结果，成功后回调 [onSuccess]（用于刷新列表）。
     */
    private suspend fun runPrivileged(
        operation: String,
        command: String,
        onSuccess: suspend () -> Unit,
    ): AppOperationResult = withContext(Dispatchers.IO) {
        val output = runCatching { ReusableShells.execSync(command) }
            .getOrElse {
                logE("$operation failed: ${it.message}", it, TAG)
                return@withContext AppOperationResult.Failure(it.message ?: "$operation 执行失败")
            }

        // pm 命令：卸载成功输出 "Success"，启用/禁用/冻结成功输出 "new state: ..."；
        // 失败则包含 Failure / Error / Exception 等关键字。
        val failed = FAILURE_KEYWORDS.any { output.contains(it, ignoreCase = true) }

        if (failed) {
            AppOperationResult.Failure(output.ifBlank { "$operation 执行失败" })
        } else {
            onSuccess()
            AppOperationResult.Success
        }
    }

    /**
     * 依次尝试多个特权命令，兼容不同 Android 版本/ROM 的命令参数差异。
     */
    private suspend fun runPrivilegedFallback(
        operation: String,
        commands: List<String>,
        onSuccess: suspend () -> Unit,
    ): AppOperationResult = withContext(Dispatchers.IO) {
        var lastFailure: AppOperationResult.Failure? = null

        for (command in commands.distinct()) {
            val outputResult = runCatching { ReusableShells.execSync(command) }
            val output = outputResult.getOrNull()
            if (output == null) {
                val throwable = outputResult.exceptionOrNull()
                logE("$operation failed: ${throwable?.message}", throwable, TAG)
                lastFailure = AppOperationResult.Failure(
                    throwable?.message ?: "$operation 执行失败"
                )
                continue
            }

            val failed = FAILURE_KEYWORDS.any { output.contains(it, ignoreCase = true) } ||
                FORCE_STOP_FALLBACK_KEYWORDS.any { output.contains(it, ignoreCase = true) }

            if (!failed) {
                onSuccess()
                return@withContext AppOperationResult.Success
            }

            lastFailure = AppOperationResult.Failure(output.ifBlank { "$operation 执行失败" })
        }

        lastFailure ?: AppOperationResult.Failure("$operation 执行失败")
    }

    private suspend fun uninstallSystemAppForCurrentUser(info: AppInfo): AppOperationResult {
        val pkg = shellQuote(info.packageName)
        if (info.isUpdatedSystemApp) {
            runPrivileged("uninstall-update", "pm uninstall $pkg") { }
        }
        return runPrivileged("uninstall-user", "pm uninstall --user 0 $pkg") {
            markSystemAppUninstalled(info.packageName)
        }
    }

    private suspend fun markSystemAppUninstalled(packageName: String) {
        val refreshed = upsertPackage(packageName)
        if (refreshed?.state == AppState.UNINSTALLED) {
            return
        }
        val existing = _apps.value.find { it.packageName == packageName }
        if (existing != null) {
            _apps.value = _apps.value.map { app ->
                if (app.packageName == packageName) {
                    app.copy(state = AppState.UNINSTALLED)
                } else {
                    app
                }
            }
        }
    }

    private fun isProtectedPackage(packageName: String): Boolean {
        return packageName == application.packageName || packageName in PROTECTED_PACKAGES
    }

    private fun shellQuote(value: String): String {
        return "'${value.replace("'", "")}'"
    }

    private fun installedPackagesMatchFlags(): Long {
        return PackageManager.MATCH_DISABLED_COMPONENTS.toLong() or
            PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong()
    }

    private val FAILURE_KEYWORDS = listOf(
        "Failure",
        "Error",
        "Exception",
        "not found",
        "Unknown package",
        "not installed",
        "Permission",
        "denied",
    )

    private val PROTECTED_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
    )

    private val FORCE_STOP_FALLBACK_KEYWORDS = listOf(
        "Unknown option",
        "Unknown user",
        "IllegalArgumentException",
    )
}

private fun ApplicationInfo.isUninstalledForCurrentUser(): Boolean {
    val installed = (flags and ApplicationInfo.FLAG_INSTALLED) != 0
    if (!installed) {
        return true
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return false
    }
    return runCatching {
        ApplicationInfo::class.java.getDeclaredField("hiddenUntilInstalled").apply {
            isAccessible = true
        }.getBoolean(this)
    }.getOrDefault(false)
}

private fun ApplicationInfo.resolveAbiList(): List<AppAbi> {
    val values = linkedSetOf<AppAbi>()
    readHiddenAbiField("primaryCpuAbi")?.toAppAbi()?.let(values::add)
    readHiddenAbiField("secondaryCpuAbi")?.toAppAbi()?.let(values::add)
    nativeLibraryDir?.toAppAbiFromPath()?.let(values::add)
    sourceDir?.toAppAbiFromPath()?.let(values::add)
    return values.toList()
}

@SuppressLint("PrivateApi")
private fun ApplicationInfo.readHiddenAbiField(fieldName: String): String? {
    return runCatching {
        ApplicationInfo::class.java.getDeclaredField(fieldName).apply {
            isAccessible = true
        }.get(this) as? String
    }.getOrNull()?.takeIf(String::isNotBlank)
}

private fun String.toAppAbi(): AppAbi? {
    val normalized = lowercase()
    return when {
        normalized.startsWith("arm64-v8a") || normalized.startsWith("arm64") -> AppAbi.ARM64_V8A
        normalized.startsWith("armeabi-v7a") -> AppAbi.ARMEABI_V7A
        normalized == "armeabi" -> AppAbi.ARMEABI
        normalized.startsWith("x86_64") -> AppAbi.X86_64
        normalized == "x86" -> AppAbi.X86
        normalized.startsWith("mips64") -> AppAbi.MIPS64
        normalized.startsWith("mips") -> AppAbi.MIPS
        normalized.startsWith("riscv64") -> AppAbi.RISCV64
        else -> null
    }
}

/**
 * 生成提取 APK 文件名：`【应用名】-【版本名称（版本号）】.后缀`
 * 多 APK（split）时，非 base 包在版本段后追加原 split 标识以免重名。
 */
private fun buildExtractedApkFileName(
    appName: String,
    versionName: String,
    versionCode: Long,
    sourcePath: String,
    sourceCount: Int,
): String {
    val safeName = sanitizeExtractFileNameComponent(appName)
    val safeVersion = sanitizeExtractFileNameComponent(
        versionName.ifBlank { versionCode.toString() },
    )
    val stem = "$safeName-$safeVersion($versionCode)"
    val originalName = sourcePath.substringAfterLast('/').ifBlank { "base.apk" }
    val extension = originalName.substringAfterLast('.', missingDelimiterValue = "apk")
        .ifBlank { "apk" }
    if (sourceCount <= 1) {
        return "$stem.$extension"
    }
    val originalStem = originalName.substringBeforeLast('.', originalName)
    if (originalStem.equals("base", ignoreCase = true)) {
        return "$stem.$extension"
    }
    val splitLabel = originalStem
        .removePrefix("split_")
        .let(::sanitizeExtractFileNameComponent)
    return "$stem-$splitLabel.$extension"
}

private fun sanitizeExtractFileNameComponent(value: String): String {
    return value.map { ch ->
        when {
            ch.code < 32 || ch in "\\/:*?\"<>|" -> '_'
            else -> ch
        }
    }.joinToString("")
        .trim()
        .trim('.')
        .ifBlank { "unknown" }
}

private fun String.toAppAbiFromPath(): AppAbi? {
    val normalized = lowercase()
    return when {
        "/lib64/" in normalized ||
            "/arm64-v8a/" in normalized ||
            "/arm64/" in normalized -> AppAbi.ARM64_V8A
        "/armeabi-v7a/" in normalized -> AppAbi.ARMEABI_V7A
        "/armeabi/" in normalized -> AppAbi.ARMEABI
        "/x86_64/" in normalized -> AppAbi.X86_64
        "/x86/" in normalized -> AppAbi.X86
        "/mips64/" in normalized -> AppAbi.MIPS64
        "/mips/" in normalized -> AppAbi.MIPS
        "/riscv64/" in normalized -> AppAbi.RISCV64
        else -> null
    }
}

private fun parseAppAbi(value: String): AppAbi? {
    return runCatching { AppAbi.valueOf(value) }.getOrNull()
        ?: AppAbi.entries.firstOrNull { it.abiName.equals(value, ignoreCase = true) }
}

@Suppress("DEPRECATION")
private fun Bundle.getParcelableArrayListCompat(key: String): List<Bundle>? {
    val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayList(key, Bundle::class.java)
    } else {
        getParcelableArrayList(key)
    }
    return list?.filterIsInstance<Bundle>()
}
