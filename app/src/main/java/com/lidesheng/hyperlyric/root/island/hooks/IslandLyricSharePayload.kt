package com.lidesheng.hyperlyric.root.island.hooks

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.view.View
import com.lidesheng.hyperlyric.root.LyriconDataBridge
import com.lidesheng.hyperlyric.root.island.content.IslandMetadataContentAssembler
import com.lidesheng.hyperlyric.root.media.CurrentMediaInfoResolver
import com.lidesheng.hyperlyric.root.utils.HookLogger

internal data class IslandLyricSharePayload(
    val title: String,
    val content: String,
    val shareContent: String,
    val albumArt: Bitmap?
)

internal object IslandLyricSharePayloadBuilder {

    fun build(
        view: View,
        prefs: SharedPreferences,
        targetPackageName: String? = null
    ): IslandLyricSharePayload? {
        val packageName = targetPackageName
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: LyriconDataBridge.currentLyricPackageName
                ?.trim()
                ?.takeIf(String::isNotEmpty)
            ?: return null
        val mediaInfo = CurrentMediaInfoResolver.getMediaInfo(
            context = view.context,
            packageName = packageName,
            logger = HookLogger
        )
        val lines = IslandMetadataContentAssembler.buildConfiguredMusicInfoLines(
            prefs = prefs,
            mediaInfo = mediaInfo
        )
        val shareFields = listOfNotNull(
            lines.firstLine.takeIf(String::isNotBlank),
            lines.secondLine.takeIf(String::isNotBlank),
            currentLyricText()?.let { formatField("当前歌词", it) }
        )
        if (shareFields.isEmpty()) return null

        return IslandLyricSharePayload(
            title = lines.firstLine,
            content = lines.secondLine,
            shareContent = shareFields.joinToString("\n"),
            albumArt = mediaInfo.albumArt
        )
    }

    private fun currentLyricText(): String? {
        return LyriconDataBridge.currentLyric
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: LyriconDataBridge.currentLyricLine?.let { line ->
                line.text?.trim()?.takeIf(String::isNotEmpty)
                    ?: line.words
                        ?.joinToString("") { word -> word.text.orEmpty() }
                        ?.trim()
                        ?.takeIf(String::isNotEmpty)
            }
    }

    private fun formatField(label: String, value: String?): String? {
        return value
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { "$label：$it" }
    }
}
