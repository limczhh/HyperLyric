package com.lidesheng.hyperlyric.root

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.lidesheng.hyperlyric.BuildConfig

/** Loads the shared, bundled monochrome music-app icons for Root/SystemUI surfaces. */
internal object MonochromeAppIconAssets {
    private const val DEFAULT_ASSET_PACKAGE = "com.apple.android.music"

    private val assetByPackage = mapOf(
        "cn.kuwo.player" to "cn.kuwo.player",
        "cn.wenyu.bodian" to "cn.wenyu.bodian",
        "com.apple.android.music" to "com.apple.android.music",
        "com.hihonor.cloudmusic" to "com.netease.cloudmusic",
        "com.kugou.android" to "com.kugou.android",
        "com.kugou.android.lite" to "com.kugou.android.lite",
        "com.luna.music" to "com.luna.music",
        "com.miui.player" to "com.miui.player",
        "com.netease.cloudmusic" to "com.netease.cloudmusic",
        "com.salt.music" to "com.salt.music",
        "com.tencent.qqmusic" to "com.tencent.qqmusic",
    )

    private val bitmapCache = mutableMapOf<String, Bitmap?>()
    private var moduleContext: Context? = null

    @Synchronized
    fun load(context: Context, packageName: String?): Bitmap? {
        val assetName = packageName
            ?.takeIf(String::isNotBlank)
            ?.let(assetByPackage::get)
            ?: DEFAULT_ASSET_PACKAGE
        return loadAsset(context, assetName)
            ?: if (assetName != DEFAULT_ASSET_PACKAGE) {
                loadAsset(context, DEFAULT_ASSET_PACKAGE)
            } else {
                null
            }
    }

    private fun loadAsset(context: Context, assetName: String): Bitmap? {
        if (bitmapCache.containsKey(assetName)) return bitmapCache[assetName]
        val bitmap = runCatching {
            val sourceContext = moduleContext ?: context.createPackageContext(
                BuildConfig.APPLICATION_ID,
                Context.CONTEXT_IGNORE_SECURITY,
            ).also { moduleContext = it }
            sourceContext.assets.open("monochrome_app_icons/$assetName.png").use {
                BitmapFactory.decodeStream(it)
            }
        }.getOrNull()
        bitmapCache[assetName] = bitmap
        return bitmap
    }
}
