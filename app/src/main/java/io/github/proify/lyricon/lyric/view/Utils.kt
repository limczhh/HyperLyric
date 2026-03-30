/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.lyricon.lyric.view

import android.content.res.Resources
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.size
import kotlin.math.roundToInt

fun ViewGroup.getChildAtOrNull(index: Int): View? =
    if (index in 0..<size) getChildAt(index) else null

var View.visibilityIfChanged: Int
    get() = visibility
    set(value) {
        if (visibility != value) visibility = value
    }

fun View.hide() {
    if (visibility != View.GONE) visibility = View.GONE
}

fun View.show() {
    if (visibility != View.VISIBLE) visibility = View.VISIBLE
}

inline var View.visibleIfChanged: Boolean
    get() = isVisible
    set(value) {
        if (isVisible != value) isVisible = value
    }

internal inline val Int.dp: Int
    get() = toFloat().dp.roundToInt()

internal inline val Int.dpf
    get() = toFloat().dp

internal inline val Float.dp: Float
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        this,
        Resources.getSystem().displayMetrics
    )

internal inline val Float.sp
    get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP,
        this,
        Resources.getSystem().displayMetrics
    )