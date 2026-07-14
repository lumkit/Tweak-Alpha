package io.github.lumkit.tweak.common.utils

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
import io.github.lumkit.tweak.model.GlobalViewModel
import io.github.lumkit.tweak.model.RuntimeMode
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

    actual fun init() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
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
                    removePackage(packageName)
                    logD(
                        "AppsHelper remove package cost ${Clock.System.now().toEpochMilliseconds() - time}ms, $packageName",
                        TAG,
                    )
                }
            }

            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
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
        val runtimeMode = GlobalViewModel.runtimeModeState.filterNotNull().first()
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
        val runtimeMode = GlobalViewModel.runtimeModeState.filterNotNull().first()
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
        val matchFlags = PackageManager.MATCH_DISABLED_COMPONENTS.toLong()
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
                    PackageManager.PackageInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
            }
        }.getOrNull() ?: return@withContext null
        runCatching { packageInfo.toAppInfo(pm, cacheDir) }.getOrNull()
    }

    private suspend fun upsertPackage(packageName: String) {
        val pm = application.packageManager
        val cacheDir = iconCacheDir
        val appInfo = fetchAppInfoViaBinder(packageName, pm, cacheDir)
            ?: loadSingleInstalledAppDirectly(packageName, pm, cacheDir)
            ?: return
        _apps.value = _apps.value
            .filterNot { it.packageName == packageName }
            .plus(appInfo)
            .sortedBy { it.appName }
    }

    private fun removePackage(packageName: String) {
        File(iconCacheDir, "$packageName.webp").takeIf(File::exists)?.delete()
        _apps.value = _apps.value.filterNot { it.packageName == packageName }
    }

    private fun PackageInfo.toAppInfo(pm: PackageManager, cacheDir: String): AppInfo {
        val appInfo = applicationInfo ?: error("applicationInfo is null for $packageName")
        return buildAppInfo(
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
            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            state = resolveState(pm, packageName, appInfo),
        )
    }

    private fun Bundle.toAppInfo(pm: PackageManager, cacheDir: String): AppInfo {
        val packageName = getString(NativeFileBundles.KEY_PACKAGE_NAME).orEmpty()
        val state = runCatching {
            AppState.valueOf(getString(NativeFileBundles.KEY_APP_STATE).orEmpty())
        }.getOrDefault(AppState.ENABLED)
        return buildAppInfo(
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
            isSystemApp = getBoolean(NativeFileBundles.KEY_IS_SYSTEM_APP),
            state = state,
        )
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
        isSystemApp: Boolean,
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
            iconPath = iconFile.absolutePath,
            isSystemApp = isSystemApp,
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
        val setting = runCatching { pm.getApplicationEnabledSetting(packageName) }
            .getOrNull() ?: PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        return when (setting) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> AppState.FROZEN
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> AppState.DISABLED
            else -> if (appInfo.enabled) AppState.ENABLED else AppState.DISABLED
        }
    }

    actual suspend fun uninstall(packageName: String): AppOperationResult =
        runPrivileged("uninstall", "pm uninstall $packageName") {
            removePackage(packageName)
        }

    actual suspend fun extractApk(packageName: String, targetDir: String): AppOperationResult =
        withContext(Dispatchers.IO) {
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

            val destDir = "${targetDir.trimEnd('/')}/$packageName"
            val commands = buildList {
                add("mkdir -p '$destDir'")
                sources.forEach { source ->
                    add("cp -f '$source' '$destDir/'")
                }
            }
            val output = runCatching { ReusableShells.execSync(commands) }
                .getOrElse { return@withContext AppOperationResult.Failure(it.message ?: "提取失败") }

            if (output.contains("error", ignoreCase = true) ||
                output.contains("No such file", ignoreCase = true)
            ) {
                AppOperationResult.Failure(output.ifBlank { "提取失败" })
            } else {
                AppOperationResult.Success
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
        return runPrivileged("setFrozen", command) { upsertPackage(packageName) }
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
