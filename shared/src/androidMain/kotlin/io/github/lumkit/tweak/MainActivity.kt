package io.github.lumkit.tweak

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.lumkit.tweak.common.ConstCommon
import io.github.lumkit.tweak.model.NavigationIntent
import io.github.lumkit.tweak.model.NavigationViewModel
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
        }
    }

    /**
     * 处理导航意图
     */
    private fun handleIntentForNavIntent(intent: Intent?) {
        if (intent?.action == ConstCommon.Navigation.ACTION_OPEN_SYSTEM_UPDATE) {
            val navIntent = json.decodeFromString<NavigationIntent>(intent.getStringExtra("nav_intent") ?: "{}")
            NavigationViewModel.navigate(navIntent)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}