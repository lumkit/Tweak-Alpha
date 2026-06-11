package io.github.lumkit.tweak.common.utils

import androidx.compose.runtime.mutableStateOf
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object LibSuX {

    val isRoot = mutableStateOf(false)

    suspend fun checkRoot(): Boolean = suspendCancellableCoroutine { continuation ->
        Shell.getShell {
            val root = it.isRoot
            this.isRoot.value = root
            continuation.resume(root)
        }
    }

}