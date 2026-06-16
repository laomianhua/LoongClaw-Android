package com.littlehelper.reminder

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** 到点主动振动，不依赖 HyperOS 等对「静音通知」渠道的振动限制。 */
object ReminderVibrator {
    fun vibrateReminder(context: Context) {
        val vibrator = resolveVibrator(context) ?: return
        if (!vibrator.hasVibrator()) return

        val pattern = NotificationHelper.REMINDER_VIBRATION_PATTERN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(pattern, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
