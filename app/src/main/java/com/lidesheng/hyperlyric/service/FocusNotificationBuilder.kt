package com.lidesheng.hyperlyric.service

import org.json.JSONObject

class FocusNotificationBuilder(
    private val uiState: NotificationManagerHelper.UiState,
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
        json.put("bigIslandArea", buildBigIslandArea())
        json.put("smallIslandArea", buildSmallIslandArea())
        return json
    }

    private fun buildBigIslandArea(): JSONObject {
        val json = JSONObject()
        
        // 大岛左侧内容 (图片/文本 组合)
        val imageTextLeft = JSONObject()
        imageTextLeft.put("type", 1)
        
        if (uiState.disableLyricSplit) {
            // 关闭分割模式：仅显示专辑封面
            imageTextLeft.put("picInfo", buildPicInfo(1))
        } else if (uiState.showIslandLeftAlbum) {
            // 开启左侧封面模式：封面 + 文本
            imageTextLeft.put("picInfo", buildPicInfo(1))
            val textInfo = JSONObject()
            textInfo.put("title", uiState.islandTitleLeft)
            imageTextLeft.put("textInfo", textInfo)
        } else {
            // 纯文本模式
            val textInfo = JSONObject()
            textInfo.put("title", uiState.islandTitleLeft)
            imageTextLeft.put("textInfo", textInfo)
        }
        json.put("imageTextInfoLeft", imageTextLeft)

        // 大岛主文本区 (右侧)
        val islandTitleText = JSONObject()
        islandTitleText.put("title", if (uiState.disableLyricSplit) uiState.notificationTitleLeft else uiState.title)
        json.put("textInfo", islandTitleText)
        
        return json
    }

    private fun buildSmallIslandArea(): JSONObject {
        val json = JSONObject()
        // 小岛胶囊内部内容 (封面+圆形进度表)
        val combinePicInfo = JSONObject()
        combinePicInfo.put("picInfo", buildPicInfo(1))
        
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
        return json
    }

    private fun buildPicInfo(type: Int): JSONObject {
        val json = JSONObject()
        json.put("type", type)
        json.put("pic", "miui.focus.pic_album")
        if (type == 2) {
            json.put("picDark", "miui.focus.pic_album")
        }
        return json
    }

    private fun buildOS2ProgressInfo(): JSONObject {
        val json = JSONObject()
        json.put("progress", uiState.progress)
        json.put("colorProgress", getColorHex(uiState.color))
        json.put("colorProgressEnd", getColorHex(uiState.colorEnd))
        return json
    }

    private fun buildOS3MultiProgressInfo(): JSONObject {
        val json = JSONObject()
        json.put("title", uiState.songInfo)
        json.put("progress", uiState.progress)
        json.put("color", getColorHex(uiState.color))
        return json
    }

    private fun getColorHex(color: Int): String {
        return if (color != 0) {
            String.format("#%06X", 0xFFFFFF and color)
        } else {
            "#2C2C2C"
        }
    }
}
