package com.lidesheng.hyperlyric.common

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicInfoLayoutPolicyTest {

    @Test
    fun fullWidthParenthesesAliasRemoved() {
        // 全角括号内的别名应被移除
        assertEquals(
            "痴人说梦",
            MusicInfoLayoutPolicy.stripTitleAlias("痴人说梦（《崩坏：星穹铁道》火花角色PV曲）")
        )
    }

    @Test
    fun halfWidthParenthesesRemovedAndTrimmed() {
        // 半角括号内容被移除并去除残留空格
        assertEquals(
            "Song Name",
            MusicInfoLayoutPolicy.stripTitleAlias("Song Name (Live)")
        )
    }

    @Test
    fun squareBracketsContentRemoved() {
        // 方括号内容被移除
        assertEquals(
            "Name",
            MusicInfoLayoutPolicy.stripTitleAlias("Name [Remix]")
        )
    }

    @Test
    fun fullWidthSquareBracketsContentRemoved() {
        // 全角方头括号内容被移除
        assertEquals(
            "歌名",
            MusicInfoLayoutPolicy.stripTitleAlias("歌名【角色PV】")
        )
    }

    @Test
    fun titleWithoutBracketsUnchanged() {
        // 无括号标题保持原样
        assertEquals(
            "普通歌名",
            MusicInfoLayoutPolicy.stripTitleAlias("普通歌名")
        )
    }

    @Test
    fun fallbackToOriginalWhenAllRemoved() {
        // 整段括号移除后为空时回退原标题
        assertEquals(
            "（纯音乐）",
            MusicInfoLayoutPolicy.stripTitleAlias("（纯音乐）")
        )
    }

    @Test
    fun multipleBracketGroupsAllRemoved() {
        // 多段括号全部移除
        assertEquals(
            "AB",
            MusicInfoLayoutPolicy.stripTitleAlias("A（x）B（y）")
        )
    }

    @Test
    fun consecutiveSpacesCollapsed() {
        // 多个连续空格折叠为单个空格
        assertEquals(
            "A B",
            MusicInfoLayoutPolicy.stripTitleAlias("A (x) B")
        )
    }

    @Test
    fun unpairedBracketsKeptAsIs() {
        // 不成对括号保持原样
        assertEquals(
            "歌名（未闭合",
            MusicInfoLayoutPolicy.stripTitleAlias("歌名（未闭合")
        )
    }
}
