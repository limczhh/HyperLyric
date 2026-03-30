/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.proify.lyricon.common.util

import android.view.View
import android.view.ViewGroup
import androidx.core.view.forEach
import io.github.proify.lyricon.common.util.ResourceMapper.getIdName
import java.lang.ref.WeakReference

object ViewHierarchyParser {
    fun buildNodeTree(viewGroup: ViewGroup): ViewTreeNode {
        val name = viewGroup.javaClass.name

        val children = mutableListOf<ViewTreeNode>()

        viewGroup.forEach { view ->
            createNodeFromView(view)?.let { node ->
                children.add(node)
            }
        }

        return ViewTreeNode(
            id = getResourceName(viewGroup),
            name = name,
            children = children,
            view = WeakReference(viewGroup),
        )
    }

    private fun createNodeFromView(view: View): ViewTreeNode? =
        if (view is ViewGroup) {
            buildNodeTree(view)
        } else {
            val name = view.javaClass.name
            ViewTreeNode(
                id = getResourceName(view),
                name = name,
                view = WeakReference(view),
            )
        }

    fun getResourceName(view: View): String? = getIdName(view)
}