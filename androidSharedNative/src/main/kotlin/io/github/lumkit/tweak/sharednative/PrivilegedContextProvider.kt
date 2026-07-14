package io.github.lumkit.tweak.sharednative

import android.app.Application
import android.content.Context

internal object PrivilegedContextProvider {
    fun requireContext(): Context {
        resolveCurrentApplication()?.let { return it.applicationContext }
        resolveSystemContext()?.let { return it }
        throw IllegalStateException("Unable to resolve privileged context")
    }

    fun requirePackageManagerContext(): Context {
        resolveSystemContext()?.let { return it }
        return requireContext()
    }

    private fun resolveCurrentApplication(): Application? {
        return runCatching {
            val clazz = Class.forName("android.app.ActivityThread")
            val method = clazz.getDeclaredMethod("currentApplication")
            method.isAccessible = true
            method.invoke(null) as? Application
        }.getOrNull()
    }

    private fun resolveSystemContext(): Context? {
        return runCatching {
            val clazz = Class.forName("android.app.ActivityThread")
            val currentThread = clazz.getDeclaredMethod("currentActivityThread").apply {
                isAccessible = true
            }.invoke(null) ?: return@runCatching null
            currentThread.javaClass.getDeclaredMethod("getSystemContext").apply {
                isAccessible = true
            }.invoke(currentThread) as? Context
        }.getOrNull()
    }
}
