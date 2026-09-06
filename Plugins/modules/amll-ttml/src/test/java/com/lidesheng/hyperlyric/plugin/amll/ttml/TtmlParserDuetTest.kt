package com.lidesheng.hyperlyric.plugin.amll.ttml

import com.lidesheng.hyperlyric.plugin.api.PluginLogger
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TtmlParser 对唱左右对齐状态机测试（对齐 amll-converter.ts toAmllLyrics Duet Logic）
 */
class TtmlParserDuetTest {

    private val debugMessages = mutableListOf<String>()

    private val logger = object : PluginLogger {
        override fun debug(message: String) {
            debugMessages.add(message)
        }
        override fun info(message: String) = Unit
        override fun warn(message: String, throwable: Throwable?) = Unit
        override fun error(message: String, throwable: Throwable?) = Unit
    }

    private val parser = TtmlParser(logger)

    /**
     * 构造测试 TTML：head 内 agent 定义（id 到 type，null 表示缺省 type）+ 正文行（agent 属性可选）。
     * 显式拼接（不用多行字符串插值 + trimIndent：插值块缩进不一致会把声明行挤出缩进，
     * 导致 XML 声明前出现空白而解析失败）。
     */
    private fun buildTtml(
        agents: List<Pair<String, String?>>,
        lineAgents: List<String?>
    ): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<tt xmlns=\"http://www.w3.org/ns/ttml\"\n")
        sb.append("    xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\">\n")
        sb.append("  <head>\n")
        sb.append("    <metadata>\n")
        for ((id, type) in agents) {
            if (type != null) {
                sb.append("      <ttm:agent type=\"$type\" xml:id=\"$id\"/>\n")
            } else {
                sb.append("      <ttm:agent xml:id=\"$id\"/>\n")
            }
        }
        sb.append("    </metadata>\n")
        sb.append("  </head>\n")
        sb.append("  <body>\n")
        lineAgents.forEachIndexed { index, agent ->
            val begin = index * 2000
            val end = begin + 1500
            val agentAttr = if (agent != null) " ttm:agent=\"$agent\"" else ""
            sb.append("    <p begin=\"${begin}ms\" end=\"${end}ms\"$agentAttr>line $index</p>\n")
        }
        sb.append("  </body>\n")
        sb.append("</tt>")
        return sb.toString()
    }

    /** 断言解析结果的 isAlignedRight 序列（同时校验行数） */
    private fun assertAligned(
        ttml: String,
        expected: List<Boolean>,
        duetEnabled: Boolean = true
    ) {
        val lines = parser.parse(ttml, duetEnabled = duetEnabled)
            ?: throw AssertionError(
                "parse 返回 null: duetEnabled=$duetEnabled, debug=$debugMessages"
            )
        assertEquals(expected.size, lines.size)
        expected.forEachIndexed { index, expectedAligned ->
            assertEquals("line $index isAlignedRight", expectedAligned, lines[index].isAlignedRight)
        }
    }

    @Test
    fun personOtherAlternatingFlipsPerAgentChange() {
        // v1(person) → v2(other) → v1：首行左侧，v2 翻转右侧，回 v1 再翻转左侧
        assertAligned(
            buildTtml(
                agents = listOf("v1" to "person", "v2" to "other"),
                lineAgents = listOf("v1", "v2", "v1")
            ),
            expected = listOf(false, true, false)
        )
    }

    @Test
    fun sameAgentKeepsSideAndGroupDoesNotParticipate() {
        // v1 → v2 → v2（保持右侧）→ v4(group 恒 false 且不参与交替）→ v2（仍右侧）→ v1（翻转左侧）
        assertAligned(
            buildTtml(
                agents = listOf(
                    "v1" to "person",
                    "v2" to "person",
                    "v4" to "group"
                ),
                lineAgents = listOf("v1", "v2", "v2", "v4", "v2", "v1")
            ),
            expected = listOf(false, true, true, false, true, false)
        )
    }

    @Test
    fun noAgentDefinitionsYieldsAllFalse() {
        // head 无 ttm:agent 定义（行均无 agent 属性）：与现状完全一致，全部左侧
        assertAligned(
            buildTtml(agents = emptyList(), lineAgents = listOf(null, null, null)),
            expected = listOf(false, false, false)
        )
    }

    @Test
    fun duetDisabledYieldsAllFalseEvenWithDuetInfo() {
        // 开关关闭：即使 TTML 含对唱信息也全部 false
        assertAligned(
            buildTtml(
                agents = listOf("v1" to "person", "v2" to "other"),
                lineAgents = listOf("v1", "v2", "v1")
            ),
            expected = listOf(false, false, false),
            duetEnabled = false
        )
    }

    @Test
    fun missingAgentTypeStartsLeft() {
        // head 定义了 agent 但缺 type 属性：首个非 group 行左侧起，切换 agent 仍正常翻转
        assertAligned(
            buildTtml(
                agents = listOf("v1" to null, "v2" to null),
                lineAgents = listOf("v1", "v2")
            ),
            expected = listOf(false, true)
        )
    }
}
