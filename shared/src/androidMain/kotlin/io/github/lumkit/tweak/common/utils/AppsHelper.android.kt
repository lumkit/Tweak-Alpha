package io.github.lumkit.tweak.common.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import io.github.lumkit.tweak.application
import io.github.lumkit.tweak.common.Const
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.time.Clock

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

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            scope.launch { refresh() }
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
            val time = Clock.System.now().toEpochMilliseconds()
            val pm = application.packageManager
            val cacheDir = iconCacheDir

            // 通过 Launcher Intent 查询所有可启动应用，无需 QUERY_ALL_PACKAGES 权限
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }

            // 按包名去重
            val uniqueActivities = linkedMapOf<String, android.content.pm.ActivityInfo>()
            resolveInfos
                .mapNotNull { it.activityInfo }
                .forEach { ai ->
                    uniqueActivities.putIfAbsent(ai.packageName, ai)
                }

            // 并行处理每个应用的信息采集和图标缓存
            val list = coroutineScope {
                uniqueActivities.values.map { ai ->
                    async {
                        val pkg = ai.packageName
                        runCatching {
                            val pkgInfo = runCatching {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                                } else {
                                    @Suppress("DEPRECATION")
                                    pm.getPackageInfo(pkg, 0)
                                }
                            }.getOrNull()

                            val appName = runCatching { ai.loadLabel(pm).toString() }.getOrNull().orEmpty()
                            val versionName = pkgInfo?.versionName.orEmpty()
                            val versionCode = pkgInfo?.let {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode
                                else @Suppress("DEPRECATION") it.versionCode.toLong()
                            } ?: 0L
                            val isSystemApp = (ai.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) ?: 0) != 0

                            // 缓存图标
                            val iconFile = File(cacheDir, "$pkg.webp")
                            if (!iconFile.exists()) {
                                runCatching {
                                    val drawable = ai.loadIcon(pm)
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

                            AppInfo(
                                packageName = pkg,
                                appName = appName,
                                versionName = versionName,
                                versionCode = versionCode,
                                iconPath = iconFile.absolutePath,
                                isSystemApp = isSystemApp,
                            )
                        }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }

            _apps.value = list.sortedBy { it.appName }
            logD("AppsHelper refresh cost ${Clock.System.now().toEpochMilliseconds() - time}ms, ${list.size} apps")
        }
    }
}
