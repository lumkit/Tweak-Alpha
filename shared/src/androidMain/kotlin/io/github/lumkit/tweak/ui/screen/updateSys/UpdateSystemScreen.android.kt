package io.github.lumkit.tweak.ui.screen.updateSys

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.github.lumkit.tweak.common.utils.KernelProps

@Composable
actual fun rememberAndroidVersionDetail(): State<String> {
    val info = remember { mutableStateOf("Loading...") }

    LaunchedEffect(Unit) {
        info.value = "Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT})"
    }

    return info
}

@Composable
actual fun rememberAliveSlot(): State<String> {
    val slot = remember { mutableStateOf("Loading...") }

    LaunchedEffect(Unit) {
        slot.value = KernelProps.getSystemProp("ro.boot.slot_suffix")
            .replace("[^A-Za-z]".toRegex(), "")
            .uppercase()
    }

    return slot
}