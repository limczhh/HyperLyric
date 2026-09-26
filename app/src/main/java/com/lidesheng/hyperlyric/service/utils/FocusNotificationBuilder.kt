package com.lidesheng.hyperlyric.service.utils

import org.json.JSONObject

class FocusNotificationBuilder(
    private val uiState: NotificationBuilder.UiState,
    private val showProgress: Boolean
) {
    /**
     * 构建小米焦点通知所需的 JSON 字符串 (param_v2)
     */
    fun build(): String {
        val root = JSONObject()
        val paramV2 = JSONObject()

        // 基础配置
        paramV2.put("islandFirstFloat", false)
        paramV2.put("updatable", true)
        paramV2.put("reopen", "reopen")
        paramV2.put("isShowNotification", uiState.focusShowNotification)

        // 1. 灵动岛区域 (param_island)
        paramV2.put("param_island", buildParamIsland())

        // 2. 基础展示区域 (baseInfo)
        paramV2.put("baseInfo", buildBaseInfo())

        // 3. 图片资源 (picInfo - 同级于 baseInfo)
        if (uiState.showAlbumArt) {
            paramV2.put("picInfo", buildPicInfo(2))
        }

        // 4. 样式特定字段 (OS2 / OS3)
        if (uiState.focusNotificationType == 1) {
            // OS2 兼容模式
            if (showProgress) {
                paramV2.put("progressInfo", buildOS2ProgressInfo())
            }
            paramV2.put("ticker", uiState.notificationTitleLeft)
            paramV2.put("tickerPic", "miui.focus.pic_album")
            paramV2.put("tickerPicDark", "miui.focus.pic_album")
        } else {
            // OS3 标准模式
            if (showProgress) {
                paramV2.put("multiProgressInfo", buildOS3MultiProgressInfo())
            }
        }

        // 5. AOD / 状态栏
        paramV2.put("aodTitle", uiState.notificationTitleLeft)
        paramV2.put("aodPic", "miui.focus.pic_album")

        root.put("param_v2", paramV2)
        return root.toString()
    }

    private fun buildParamIsland(): JSONObject {
        val json = JSONObject()
        if (uiState.highlightColorEnabled) {
            json.put("highlightColor", getColorHex(uiState.color)) // 使用高亮度主色
        }
        json.put("bigIslandArea", buildBigIslandArea())
        json.put("smallIslandArea", buildSmallIslandArea())
        return json
    }

    private fun buildBigIslandArea(): JSONObject {
        val json = JSONObject()

        // 大岛左侧内容 (图片/文本 组合)
        val imageTextLeft = JSONObject()
        imageTextLeft.put("type", 1)

        val style = uiState.islandLeftIconStyle
        val showPic = style in 0..2 // 0=app icon, 1=rounded, 2=circular all show pic; 3=none

        if (uiState.disableLyricSplit && showPic) {
            // 关闭分割模式：仅显示图标
            val picKey = if (style == 0) "miui.focus.pic_note" else "miui.focus.pic_album"
            imageTextLeft.put("picInfo", buildPicInfo(1, picKey))
        } else if (showPic) {
            // 图标 + 文本
            val picKey = if (style == 0) "miui.focus.pic_note" else "miui.focus.pic_album"
            imageTextLeft.put("picInfo", buildPicInfo(1, picKey))
            val textInfo = JSONObject()
            textInfo.put("title", uiState.islandTitleLeft)
            textInfo.put("showHighlightColor", uiState.highlightColorEnabled)
            imageTextLeft.put("textInfo", textInfo)
        } else {
            // 纯文本模式 (style == 3: 无)
            val textInfo = JSONObject()
            textInfo.put("title", uiState.islandTitleLeft)
            textInfo.put("showHighlightColor", uiState.highlightColorEnabled)
            imageTextLeft.put("textInfo", textInfo)
        }
        json.put("imageTextInfoLeft", imageTextLeft)

        // 大岛主文本区 (右侧)
        val islandTitleText = JSONObject()
        islandTitleText.put("title", uiState.title)
        islandTitleText.put("showHighlightColor", uiState.highlightColorEnabled)
        json.put("textInfo", islandTitleText)

        return json
    }

    private fun buildSmallIslandArea(): JSONObject {
        val json = JSONObject()
        // 小岛胶囊图片跟随左侧图标设置，进度环独立显示
        val combinePicInfo = JSONObject()
        val style = uiState.islandLeftIconStyle
        if (style in 0..2) {
            val picKey = if (style == 0) "miui.focus.pic_note" else "miui.focus.pic_album"
            combinePicInfo.put("picInfo", buildPicInfo(1, picKey))
        }
        
        if (showProgress) {
            val progressInfo = JSONObject()
            progressInfo.put("progress", uiState.progress)
            progressInfo.put("colorReach", getColorHex(uiState.colorEnd))
            progressInfo.put("isCCW", true)
            combinePicInfo.put("progressInfo", progressInfo)
        }
        
        json.put("combinePicInfo", combinePicInfo)
        return json
    }

    private fun buildBaseInfo(): JSONObject {
        val json = JSONObject()
        json.put("type", 2)
        json.put("title", uiState.notificationTitleLeft)
        // OS2 使用 songInfo，OS3 使用 lyric (notificationTitleRight)
        json.put("content", if (uiState.focusNotificationType == 1) uiState.songInfo else uiState.notificationTitleRight)

        if (uiState.songInfoHighlightColorEnabled) {
            val hex = getColorHex(uiState.color)
            json.put("colorTitle", hex)
            json.put("colorTitleDark", hex)
            json.put("colorContent", hex)
            json.put("colorContentDark", hex)
        }
        return json
    }

    private fun buildPicInfo(type: Int, picKey: String = "miui.focus.pic_album"): JSONObject {
        val json = JSONObject()
        json.put("type", type)
        json.put("pic", picKey)
        if (type == 2) {
            json.put("picDark", picKey)
        }
        return json
    }

    private fun buildOS2ProgressInfo(): JSONObject {
        val json = JSONObject()
        json.put("progress", uiState.progress)
        val color = if (uiState.progressColorEnabled) getColorHex(uiState.color) else "#3482FF"
        val colorEnd = if (uiState.progressColorEnabled) getColorHex(uiState.colorEnd) else "#3482FF"
        json.put("colorProgress", color)
        json.put("colorProgressEnd", colorEnd)
        return json
    }

    private fun buildOS3MultiProgressInfo(): JSONObject {
        val json = JSONObject()
        json.put("title", uiState.songInfo)
        json.put("progress", uiState.progress)
        if (uiState.progressColorEnabled) {
            json.put("color", getColorHex(uiState.color))
        }

        if (uiState.songInfoHighlightColorEnabled) {
            val hex = getColorHex(uiState.color)
            json.put("colorTitle", hex)
            json.put("colorTitleDark", hex)
            json.put("colorContent", hex)
            json.put("colorContentDark", hex)
        }
        return json
    }

    private fun getColorHex(color: Int): String {
        return if (color != 0) {
            // 返回 8 位颜色格式 (#FFRRGGBB)
            String.format("#FF%06X", 0xFFFFFF and color)
        } else {
            "#3482FF"
        }
    }
}
