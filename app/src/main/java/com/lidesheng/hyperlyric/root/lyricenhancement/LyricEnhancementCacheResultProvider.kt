package com.lidesheng.hyperlyric.root.lyricenhancement

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import com.lidesheng.hyperlyric.common.LyricEnhancementCacheResultChannel

/** App-process endpoint for bounded cache-operation results submitted by SystemUI. */
class LyricEnhancementCacheResultProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != LyricEnhancementCacheResultChannel.METHOD_SUBMIT) {
            return super.call(method, arg, extras)
        }
        val context = context ?: return result(false)
        if (!isSystemUiCaller(context)) return result(false)
        return result(
            LyricEnhancementCacheResultChannel.acceptFromSystemUi(
                context = context,
                requestId = extras?.getString(
                    LyricEnhancementCacheResultChannel.EXTRA_REQUEST_ID
                ),
                responseToken = extras?.getString(
                    LyricEnhancementCacheResultChannel.EXTRA_RESPONSE_TOKEN
                ),
                encodedResponse = extras?.getString(
                    LyricEnhancementCacheResultChannel.EXTRA_RESPONSE
                )
            )
        )
    }

    override fun getType(uri: Uri): String? = null

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private fun isSystemUiCaller(context: Context): Boolean = runCatching {
        context.packageManager.getPackagesForUid(Binder.getCallingUid())
            ?.contains(SYSTEM_UI_PACKAGE) == true
    }.getOrDefault(false)

    private fun result(accepted: Boolean): Bundle = Bundle().apply {
        putBoolean(LyricEnhancementCacheResultChannel.EXTRA_ACCEPTED, accepted)
    }

    private companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
