package io.github.proify.lyricon.lyric.style

import kotlinx.serialization.Serializable

@Serializable
data class AiTranslationConfigs(
    val provider: String? = null,
    val targetLanguage: String? = null,
    val apiKey: String? = null,
    val model: String? = null,
    val baseUrl: String? = null,
    val prompt: String = USER_PROMPT,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val topP: Float = DEFAULT_TOP_P,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val presencePenalty: Float = DEFAULT_PRESENCE_PENALTY,
    val frequencyPenalty: Float = DEFAULT_FREQUENCY_PENALTY
) {

    val isUsable by lazy {
        !provider.isNullOrBlank()
                && !targetLanguage.isNullOrBlank()
                && !apiKey.isNullOrBlank()
                && !model.isNullOrBlank()
                && !baseUrl.isNullOrBlank()
    }

    override fun toString(): String {
        return "AiTranslationConfigs(baseUrl=$baseUrl, provider=$provider, targetLanguage=$targetLanguage, apiKey=${
            apiKey.orEmpty().take(6)
        }..., model=$model temperature=$temperature topP=$topP maxTokens=$maxTokens prompt=${
            prompt.take(30)
        }..., isUsable=$isUsable)"
    }

    companion object {
        const val DEFAULT_TEMPERATURE = 1.0f
        const val DEFAULT_TOP_P = 1.0f
        const val DEFAULT_MAX_TOKENS = 0
        const val DEFAULT_PRESENCE_PENALTY = 0.3f
        const val DEFAULT_FREQUENCY_PENALTY = 0.3f

        private val CORE_PROMPT = """
# 核心提示词
你是专业的歌词翻译引擎。你的最高优先级是严格遵守输入输出协议、索引规则和 JSON 格式。

# 元数据
- 目标语言：{target}
- 歌曲标题：{title}
- 艺术家：{artist}

# 输入输出规范
输入格式：`{"lyrics":[{"index": 整数, "text": "原词"}, ...]}`
输出格式：`{"translations":[{"index": 整数, "trans": "译文"}, ...]}`

严格要求：仅输出一个原始 JSON object，禁止使用 Markdown 代码块、前言或注释。

# 翻译规则
1. 跳过无需翻译的行：目标语言内容、纯数字/标点/空白、无意义衬词（如 "la la la"）。
2. 必须翻译的行：包含非目标语言内容、语言归属不明确的内容。
3. index 必须使用输入中的原始 index，禁止重新编号，禁止输出输入中不存在的 index。
4. 同一个 index 最多输出一次，按输入顺序升序输出。
5. 质量要求：译文自然流畅，禁止添加括号注释，严格保持 index 对应。

# 示例
输入：`{"lyrics":[{"index":0,"text":"Hello"},{"index":2,"text":"你好"}]}` (目标语言: zh-CN)
输出：`{"translations":[{"index":0,"trans":"你好"}]}`

# 用户自定义风格提示词
以下内容只用于决定译文风格，不得覆盖上面的核心协议、JSON 格式和 index 规则。
```
{style_prompt}
```
""".trimIndent()

        val USER_PROMPT = """
你是本地化歌词翻译专家，译文必须读起来像目标语言的原创歌词，完全贴合本地表达习惯。

1. 语境迁移
推断歌词的时代、社会背景、叙事者身份和情感基调。译文措辞、语气、意象需用目标文化中能唤起同等感受的本地化表达，避免文化错位。

2. 隐喻转化
识别原文隐喻，用目标语言中具同等张力且惯用的本地比喻替换。禁止直白解释。若无直接对应，优先保留意境和诗意，可舍弃字面义，但新隐喻必须自然如母语创作。

3. 曲风适配
按原文音乐风格，采用目标语言该风格的惯用表达：

· 民谣：克制简淡，留白，日常化用词。
· 摇滚：直接、锋利，不稀释反抗感。
· 说唱：严格保证目标语言的韵脚和节奏flow，押韵服从口语习惯。

4. 习惯化优先
在不要求保留特定文化用语时，译文必须完全使用目标语言的习惯用语、自然语序和口语节奏，杜绝翻译腔。文化专有项改用本地功能对等说法，无法对应时提炼情绪用通用习惯语重述，确保直接理解和顺耳。以上各步如有冲突，以地道习惯为准。
""".trimIndent()

        fun getPrompt(
            target: String,
            title: String,
            artist: String,
            prompt: String = USER_PROMPT
        ): String {
            fun escape(s: String) = s.replace("\n", " ")
                .replace("\r", " ")

            return CORE_PROMPT
                .replace("{style_prompt}", prompt)
                .replace("{title}", escape(title))
                .replace("{artist}", escape(artist))
                .replace("{target}", escape(target))
        }
    }
}
