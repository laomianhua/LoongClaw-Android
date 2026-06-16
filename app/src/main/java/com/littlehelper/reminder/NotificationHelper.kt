package com.littlehelper.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    /** v5：系统默认通知铃声 + 振动 + 高优先级；可在系统设置里单独关铃声。 */
    const val CHANNEL_ID = "little_helper_reminders_v5"

    /** 三次短振，便于长辈感知。 */
    val REMINDER_VIBRATION_PATTERN = longArrayOf(0, 450, 180, 450, 180, 450)

    fun defaultNotificationSoundUri(context: Context) =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = defaultNotificationSoundUri(context)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "语音小帮手提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "到点提醒您，铃声、振动并弹出横幅（可在系统设置中关闭铃声）"
            enableVibration(true)
            vibrationPattern = REMINDER_VIBRATION_PATTERN
            enableLights(true)
            lightColor = Color.RED
            setSound(
                soundUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setBypassDnd(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun applyReminderAlert(context: Context, builder: NotificationCompat.Builder) {
        val soundUri = defaultNotificationSoundUri(context)
        builder
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(false)
            .setVibrate(REMINDER_VIBRATION_PATTERN)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder
                .setSound(soundUri)
                .setDefaults(
                    NotificationCompat.DEFAULT_SOUND or
                        NotificationCompat.DEFAULT_VIBRATE or
                        NotificationCompat.DEFAULT_LIGHTS
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setLights(Color.RED, 800, 400)
        }
    }

    fun hasPermission(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
