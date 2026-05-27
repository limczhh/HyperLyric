package com.lidesheng.hyperlyric.service

import com.lidesheng.hyperlyric.utils.LogManager
import com.lidesheng.hyperlyric.ui.utils.Constants as UIConstants
import com.lidesheng.hyperlyric.service.Constants as ServiceConstants
import com.lidesheng.hyperlyric.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.app.NotificationCompat

object NotificationManagerHelper {
    private const val CHANNEL_ID = "hyper_lyric_live_v4"
    private const val CHANNEL_ID_FOCUS = "hyper_lyric_focus_v1"
    const val NORMAL_NOTIFICATION_ID = 2002
    const val FOCUS_NOTIFICATION_ID = 2003

    data class UiState(
        val title: String,
        val songLyric: String = "",
        val songInfo: String,
        val islandTitleLeft: String,
        val notificationTitleLeft: String = "",
        val notificationTitleRight: String = "",
        val albumBitmap: Bitmap? = null,
        val color: Int,
        val colorEnd: Int,
        val progress: Int,
        val isPlaying: Boolean,
        val targetPackageName: String = "",
        val showIslandLeftAlbum: Boolean = false,
        val disableLyricSplit: Boolean = false,
        val notificationAlbumBitmap: Bitmap? = null,
        val notificationAlbumBitmapCircular: Bitmap? = null,
        val islandLeftIconStyle: Int = 0,
        val focusNotificationType: Int = 0,
        val showAlbumArt: Boolean = true,
        val highlightColorEnabled: Boolean = false,
        val songInfoHighlightColorEnabled: Boolean = false,
        val progressColorEnabled: Boolean = true
    )

    private var lastAlbumBitmap: Bitmap? = null
    private var lastAlbumIcon: android.graphics.drawable.Icon? = null
    private var lastLabelBitmap: Bitmap? = null
    private var lastLabelIcon: androidx.core.graphics.drawable.IconCompat? = null

    private fun getAlbumIcon(bitmap: Bitmap?): android.graphics.drawable.Icon? {
        if (bitmap == null || bitmap.isRecycled) return null
        if (bitmap === lastAlbumBitmap && lastAlbumIcon != null) return lastAlbumIcon
        lastAlbumBitmap = bitmap
        lastAlbumIcon = android.graphics.drawable.Icon.createWithBitmap(bitmap)
        return lastAlbumIcon
    }

    private fun getLabelIcon(bitmap: Bitmap?): androidx.core.graphics.drawable.IconCompat? {
        if (bitmap == null || bitmap.isRecycled) return null
        if (bitmap === lastLabelBitmap && lastLabelIcon != null) return lastLabelIcon
        lastLabelBitmap = bitmap
        lastLabelIcon = androidx.core.graphics.drawable.IconCompat.createWithBitmap(bitmap)
        return lastLabelIcon
    }

    fun createNotificationChannel(context: Context, notificationManager: NotificationManager) {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_name_live),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                setShowBadge(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        if (notificationManager.getNotificationChannel(CHANNEL_ID_FOCUS) == null) {
            val focusChannel = NotificationChannel(
                CHANNEL_ID_FOCUS,
                context.getString(R.string.channel_name_focus),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                setShowBadge(false)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(focusChannel)
        }
    }


    fun buildNormalNotification(
        context: Context,
        uiState: UiState,
        duration: Long,
        showProgress: Boolean = true
    ): Notification {
        val selectedBitmap: Bitmap? = when (uiState.islandLeftIconStyle) {
            1 -> uiState.notificationAlbumBitmap?.takeIf { !it.isRecycled }
            2 -> uiState.notificationAlbumBitmapCircular?.takeIf { !it.isRecycled }
            else -> null
        }

        val smallIconCompat = if (selectedBitmap != null) {
            getLabelIcon(selectedBitmap) ?: androidx.core.graphics.drawable.IconCompat.createWithResource(context, R.drawable.lyrictile)
        } else {
            androidx.core.graphics.drawable.IconCompat.createWithResource(context, R.drawable.lyrictile)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIconCompat)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        val albumIcon = getAlbumIcon(uiState.notificationAlbumBitmap)
        if (albumIcon != null && uiState.showAlbumArt) {
            builder.setLargeIcon(albumIcon)
        }

        builder.setCustomContentView(null)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(getClickPendingIntent(context, uiState.targetPackageName))
            .setShortCriticalText(uiState.title)
            .setContentTitle(uiState.notificationTitleLeft)
            .setSubText(uiState.songInfo)
            .setContentText(uiState.notificationTitleRight)

        if (duration > 1000 && showProgress) {
            try {
                val remaining = 100 - uiState.progress
                val segments = ArrayList<NotificationCompat.ProgressStyle.Segment>(2)

                if (uiState.progress > 0) {
                    val segment = NotificationCompat.ProgressStyle.Segment(uiState.progress)
                    if (uiState.progressColorEnabled) {
                        segment.setColor(uiState.color)
                    }
                    segments.add(segment)
                }
                if (remaining > 0) {
                    segments.add(NotificationCompat.ProgressStyle.Segment(remaining).setColor(0x40FFFFFF))
                }

                val style = NotificationCompat.ProgressStyle()
                    .setProgressSegments(segments)
                    .setStyledByProgress(false)
                    .setProgress(uiState.progress)

                builder.setStyle(style)
            } catch (_: Exception) {
            }
        }

        builder.setRequestPromotedOngoing(true)
        val extras = Bundle()
        extras.putBoolean("android.extra.requestPromotedOngoing", true)
        builder.addExtras(extras)

        return builder.build()
    }

    fun buildFocusNotification(
        context: Context,
        uiState: UiState,
        showProgress: Boolean = true
    ): Notification {
        val paramIslandJson = FocusNotificationBuilder(uiState, showProgress).build()

        val smallIconCompat = androidx.core.graphics.drawable.IconCompat.createWithResource(context, R.drawable.lyrictile)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_FOCUS)
            .setSmallIcon(smallIconCompat)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCustomContentView(null)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(getClickPendingIntent(context, uiState.targetPackageName))
            .setShortCriticalText(uiState.title)
            .setContentTitle(uiState.notificationTitleLeft)
            .setSubText(uiState.songInfo)
            .setContentText(uiState.notificationTitleRight)

        val extras = Bundle()
        extras.putBoolean("mFocusNotification", true)
        extras.putString("miui.focus.param", paramIslandJson)
        
        if (uiState.color != 0) extras.putInt("mipush_focus_color", uiState.color)

        val picsBundle = Bundle()
        val albumBitmapForFocus: Bitmap? = when (uiState.islandLeftIconStyle) {
            2 -> uiState.notificationAlbumBitmapCircular?.takeIf { !it.isRecycled }
            else -> uiState.notificationAlbumBitmap?.takeIf { !it.isRecycled }
        }
        val albumIcon = getAlbumIcon(albumBitmapForFocus)
            ?: android.graphics.drawable.Icon.createWithResource(context, R.drawable.lyrictile)
        picsBundle.putParcelable("miui.focus.pic_album", albumIcon)

        if (uiState.islandLeftIconStyle == 0) {
            val noteBitmap = drawableToBitmap(context, R.drawable.lyrictile)
            val noteIcon = android.graphics.drawable.Icon.createWithBitmap(noteBitmap)
            picsBundle.putParcelable("miui.focus.pic_note", noteIcon)
        }

        extras.putBundle("miui.focus.pics", picsBundle)
        
        builder.addExtras(extras)
        return builder.build()
    }



    fun cancelFocusNotification(notificationManager: NotificationManager) {
        try {
            notificationManager.cancel(FOCUS_NOTIFICATION_ID)
        } catch (e: Exception) {
            LogManager.e("NotificationManagerHelper", "取消焦点通知失败", e)
        }
    }

    fun cancelNormalNotification(notificationManager: NotificationManager) {
        try {
            notificationManager.cancel(NORMAL_NOTIFICATION_ID)
        } catch (e: Exception) {
            LogManager.e("NotificationManagerHelper", "取消普通通知失败", e)
        }
    }



    private fun getClickPendingIntent(context: Context, targetPackageName: String): PendingIntent? {
        val prefs = context.getSharedPreferences(UIConstants.PREF_NAME, Context.MODE_PRIVATE)
        val action = prefs.getInt(ServiceConstants.KEY_NOTIFICATION_CLICK_ACTION, ServiceConstants.DEFAULT_NOTIFICATION_CLICK_ACTION)

        return when (action) {
            1 -> {
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                if (intent != null) {
                    PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                } else {
                    val mainIntent = Intent(context, com.lidesheng.hyperlyric.ui.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    PendingIntent.getActivity(
                        context,
                        0,
                        mainIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
            }
            2 -> {
                val intent = context.packageManager.getLaunchIntentForPackage(targetPackageName)
                if (intent != null) {
                    PendingIntent.getActivity(
                        context,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                } else {
                    val broadcastIntent = Intent("com.lidesheng.hyperlyric.ACTION_TOGGLE_PLAYBACK")
                    broadcastIntent.setPackage(context.packageName)
                    PendingIntent.getBroadcast(
                        context,
                        0,
                        broadcastIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                }
            }
            else -> {
                val intent = Intent("com.lidesheng.hyperlyric.ACTION_TOGGLE_PLAYBACK")
                intent.setPackage(context.packageName)
                PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
        }
    }

    private fun drawableToBitmap(context: Context, drawableResId: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, drawableResId)!!
        val size = 128
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }
}
