/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

@file:Suppress("unused")

package io.github.proify.android.extensions

import android.view.View
import android.view.ViewGroup

var View.visibilityIfChanged: Int
    get() = visibility
    set(value) {
        if (visibility != value) visibility = value
    }


fun ViewGroup.getChildAtOrNull(index: Int): View? =
    if (childCount > index) getChildAt(index) else null