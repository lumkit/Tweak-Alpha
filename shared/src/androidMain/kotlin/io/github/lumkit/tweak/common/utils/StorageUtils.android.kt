package io.github.lumkit.tweak.common.utils

import io.github.lumkit.tweak.sharednative.StorageBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual object PlatformStorageSource {
    actual suspend fun getTotalBytes(path: String): Long {
        return withContext(Dispatchers.IO) {
            StorageBridge.getTotalBytes(path)
        }
    }

    actual suspend fun getUsedBytes(path: String): Long {
        return withContext(Dispatchers.IO) {
            StorageBridge.getUsedBytes(path)
        }
    }

    actual suspend fun getUserProfiles(): List<String> {
        return withContext(Dispatchers.IO) {
            StorageBridge.getUserProfiles()?.toList() ?: emptyList()
        }
    }
}
