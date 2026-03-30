/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.common.util

import android.os.Parcelable
import android.view.View
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.lang.ref.WeakReference

@Serializable
@Parcelize
data class ViewTreeNode(
    var id: String? = null,
    var name: String,
    var children: List<ViewTreeNode>? = null,
    @Transient
    @IgnoredOnParcel
    var view: WeakReference<View>? = null
) : Parcelable {

//    fun findById(id: String?): ViewTreeNode? {
//        if (this.id == id) return this
//        children?.forEach {
//            it.findById(id)?.let { node ->
//                return node
//            }
//        }
//        return null
//    }
}