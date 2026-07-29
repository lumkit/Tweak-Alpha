package io.github.lumkit.tweak.server.battery

fun interface BatterySampler {
    fun sample(): BatterySample?
}
