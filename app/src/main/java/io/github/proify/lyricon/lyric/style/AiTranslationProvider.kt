package io.github.proify.lyricon.lyric.style

enum class AiTranslationProvider(val provider: String, val model: String, val url: String) {
    OPENAI(
        "openai",
        "gpt-4o-mini",
        "https://api.openai.com/v1"
    ),
}
