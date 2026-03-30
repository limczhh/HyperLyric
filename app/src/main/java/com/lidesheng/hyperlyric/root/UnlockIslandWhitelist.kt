package com.lidesheng.hyperlyric.root

import android.annotation.SuppressLint
import android.content.res.Resources
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule

object UnlockIslandWhitelist {
    private const val TARGET_RES_NAME = "config_dynamic_island_miniwindow_media_whitelist"

    private var targetResId: Int = -1
    private val checkedIds = mutableSetOf<Int>()
    internal lateinit var module: XposedModule

    // 自定义白名单列表
    private val CUSTOM_WHITELIST = arrayOf(
        "tv.danmaku.bili",              // 哔哩哔哩
        "tv.danmaku.bilibilihd",        // 哔哩哔哩HD
        "com.google.android.youtube",   // YouTube
        "com.ss.android.ugc.aweme",     // 抖音
        "com.ss.android.ugc.aweme.mobile",
        "com.tencent.qqmusic",          // QQ音乐
        "com.tencent.qqmusicpad",       // QQ音乐Pad
        "com.kugou.android",            // 酷狗音乐
        "com.kugou.android.lite",       // 酷狗音乐概念版
        "cn.kuwo.player",              // 酷我音乐
        "com.netease.cloudmusic",       // 网易云音乐
        "cmccwm.mobilemusic",           // 咪咕音乐
        "cn.wenyu.bodian",              // 波点音乐
        "com.luna.music",               // 汽水音乐
        "com.ikunshare.music.mobile",
        "com.miui.player",              // 小米音乐
        "com.apple.android.music",      // Apple Music
        "com.google.android.apps.youtube.music", // YT Music
        "com.spotify.music",            // Spotify
        "com.salt.music",               // Salt Player
        "com.xuncorp.qinalt.music",     // 青盐云听
        "com.maxmpz.audioplayer",       // Poweramp
        "yos.music.player",             // Flamingo
        "com.larus.nova",               // 汽水音乐
        "com.sumsg.musichub",           // Music Hub
        "com.miui.fm",                  // 小米收音机
        "com.xs.fm",                    // 喜马拉雅
        "com.xs.fm.lite",               // 喜马拉雅极速版
        "com.ximalaya.ting.android",    // 喜马拉雅
        "com.tencent.weread",           // 微信读书
        "com.dragon.read",              // 番茄小说
        "com.qidian.QDReader",          // 起点读书
        "com.tencent.mm",               // 微信
        "org.telegram.messenger",       // Telegram
        "com.sina.weibo",               // 微博
        "cn.toside.music.mobile"
    )

    @SuppressLint("DiscouragedApi")
    fun hook(xposedModule: XposedModule) {
        module = xposedModule

        val resId = try {
            Resources.getSystem().getIdentifier(TARGET_RES_NAME, "array", "android")
        } catch (_: Exception) {
            0
        }

        if (resId != 0) {
            targetResId = resId
        }

        try {
            val getStringArrayMethod = Resources::class.java.getDeclaredMethod(
                "getStringArray", Int::class.javaPrimitiveType
            )
            module.deoptimize(getStringArrayMethod)
            module.hook(getStringArrayMethod).intercept(GetStringArrayHooker())
        } catch (e: Exception) {
            module.log("[HyperLyric] [E] 白名单 Hook 注册失败: ${e.message}")
        }
    }

    class GetStringArrayHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val resId = chain.args[0] as? Int ?: return chain.proceed()

            if (targetResId != -1) {
                if (resId == targetResId) {
                    return CUSTOM_WHITELIST
                }
                return chain.proceed()
            }

            if (!checkedIds.add(resId)) {
                return chain.proceed()
            }

            try {
                val resources = chain.thisObject as? Resources ?: return chain.proceed()
                val resName = resources.getResourceEntryName(resId)
                if (resName == TARGET_RES_NAME) {
                    targetResId = resId
                    return CUSTOM_WHITELIST
                }
            } catch (_: Exception) {
            }
            return chain.proceed()
        }
    }
}
