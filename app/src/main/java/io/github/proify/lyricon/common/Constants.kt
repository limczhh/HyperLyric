/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.common

object Constants {
    const val APP_PACKAGE_NAME: String = "com.lidesheng.hyperlyric"

    const val DEFAULT_TRANSLATION_CUSTOM_PROMPT = """You are a professional song lyric translator.

Translate each input line into ${'$'}targetLanguage.

Rules:
1. Process lines independently.
2. Each input line produces exactly one output line.
3. If a line is already written in ${'$'}targetLanguage, keep it unchanged.
4. Otherwise translate it into ${'$'}targetLanguage.
5. Never output the source language unless the line is already in ${'$'}targetLanguage.
6. Preserve the original line order.

Return ONLY a JSON array of strings with the same length as the input.
"""
}