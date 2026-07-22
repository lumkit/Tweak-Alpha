package io.github.lumkit.tweak

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.lumkit.tweak.common.ConstCommon
import io.github.lumkit.tweak.model.CrashReporter
import io.github.lumkit.tweak.model.NavigationIntent
import io.github.lumkit.tweak.model.NavigationViewModel
import io.github.lumkit.tweak.model.parseDeeplinkRoute
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntentForNavIntent(intent)

        setContent {
            App()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentForNavIntent(intent)
    }

    private val json by lazy {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "type"
        }
    }

    /**
     * 处理导航意图：
     * 1. `crash_report`：内部崩溃通道（Intent 直传，跳过通用深链）
     * 2. `nav_intent`：完整 [NavigationIntent] JSON（通知 / 服务 PendingIntent）
     * 3. `route`：adb 简易路由名 + 可选参数 extras
     */
    private fun handleIntentForNavIntent(intent: Intent?) {
        if (intent?.action != ConstCommon.Navigation.ACTION_DEEPLINK_SELF) {
            return
        }

        if (CrashReporter.absorbCrashReportFromIntent(intent)) {
            return
        }

        val navIntentJson = intent.getStringExtra(ConstCommon.Navigation.EXTRA_NAV_INTENT)
            ?.takeIf { it.isNotBlank() && it != "{}" }
        if (navIntentJson != null) {
            runCatching {
                json.decodeFromString<NavigationIntent>(navIntentJson)
            }.getOrNull()?.let { NavigationViewModel.navigate(it) }
            return
        }

        val route = intent.getStringExtra(ConstCommon.Navigation.EXTRA_ROUTE)
            ?.takeIf { it.isNotBlank() }
            ?: return
        val screen = parseDeeplinkRoute(
            route = route,
            stringExtra = { key -> intent.getStringExtra(key) },
            intExtra = { key ->
                if (intent.hasExtra(key)) intent.getIntExtra(key, Int.MIN_VALUE)
                    .takeUnless { it == Int.MIN_VALUE }
                else null
            },
            longExtra = { key ->
                if (intent.hasExtra(key)) intent.getLongExtra(key, Long.MIN_VALUE)
                    .takeUnless { it == Long.MIN_VALUE }
                else null
            },
        ) ?: return

        NavigationViewModel.navigate(screen)
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
