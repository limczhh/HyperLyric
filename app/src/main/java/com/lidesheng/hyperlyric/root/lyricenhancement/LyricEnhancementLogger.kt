package com.lidesheng.hyperlyric.root.lyricenhancement

import com.lidesheng.hyperlyric.common.HyperLogger
import com.lidesheng.hyperlyric.root.utils.HookLogger

internal class LyricEnhancementLogger(
    private val tag: String,
    private val delegate: HyperLogger = HookLogger,
) {
    fun debug(message: String) = delegate.d(tag, message)

    fun info(message: String) = delegate.i(tag, message)

    fun warn(message: String, error: Throwable? = null) {
        delegate.w(tag, message, error)
    }

    fun error(message: String, error: Throwable? = null) {
        delegate.e(tag, message, error)
    }

    fun withTag(childTag: String): LyricEnhancementLogger =
        LyricEnhancementLogger("$tag/$childTag", delegate)
}
