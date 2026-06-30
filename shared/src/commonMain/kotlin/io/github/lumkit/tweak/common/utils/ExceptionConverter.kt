package io.github.lumkit.tweak.common.utils

fun Exception.convert(): Exception = when (this) {

    else -> this
}