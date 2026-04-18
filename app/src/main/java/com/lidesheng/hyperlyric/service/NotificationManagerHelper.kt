package com.lidesheng.hyperlyric.service

import com.lidesheng.hyperlyric.Constants
import com.lidesheng.hyperlyric.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
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
        val focusNotificationType: Int = 0,
        val showAlbumArt: Boolean = true
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

    fun createNotificationChannel(notificationManager: NotificationManager) {
        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "实时通知歌词",
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
                "焦点通知歌词",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "焦点通知仅在澎湃3设备上生效，其他设备或系统可以关闭该通知"
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
        val prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
        val showAlbumSmallIcon = prefs.getBoolean(Constants.KEY_NORMAL_NOTIFICATION_ALBUM, Constants.DEFAULT_NORMAL_NOTIFICATION_ALBUM)

        val smallIconCompat = when {
            showAlbumSmallIcon && uiState.notificationAlbumBitmap != null && !uiState.notificationAlbumBitmap.isRecycled ->
                getLabelIcon(uiState.notificationAlbumBitmap) ?: androidx.core.graphics.drawable.IconCompat.createWithResource(context, R.drawable.lyrictile)

            else ->
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
            .setShortCriticalText(uiState.notificationTitleLeft)
            .setContentTitle(uiState.notificationTitleLeft)
            .setSubText(uiState.songInfo)
            .setContentText(uiState.notificationTitleRight)

        if (duration > 1000 && showProgress) {
            try {
                val remaining = 100 - uiState.progress
                val segments = ArrayList<NotificationCompat.ProgressStyle.Segment>(2)

                if (uiState.progress > 0) {
                    segments.add(NotificationCompat.ProgressStyle.Segment(uiState.progress).setColor(uiState.color))
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
        val albumIcon = getAlbumIcon(uiState.notificationAlbumBitmap)
            ?: android.graphics.drawable.Icon.createWithResource(context, R.drawable.lyrictile)
        picsBundle.putParcelable("miui.focus.pic_album", albumIcon)
        extras.putBundle("miui.focus.pics", picsBundle)
        
        builder.addExtras(extras)
        return builder.build()
    }



    fun cancelFocusNotification(notificationManager: NotificationManager) {
        try {
            notificationManager.cancel(FOCUS_NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelNormalNotification(notificationManager: NotificationManager) {
        try {
            notificationManager.cancel(NORMAL_NOTIFICATION_ID)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }



    private fun getClickPendingIntent(context: Context, targetPackageName: String): PendingIntent? {
        val prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)
        val action = prefs.getInt(Constants.KEY_NOTIFICATION_CLICK_ACTION, Constants.DEFAULT_NOTIFICATION_CLICK_ACTION)

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
}
