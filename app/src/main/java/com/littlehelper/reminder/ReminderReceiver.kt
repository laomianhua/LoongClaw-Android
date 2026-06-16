package com.littlehelper.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.littlehelper.MainActivity
import com.littlehelper.R
import com.littlehelper.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.littlehelper.data.MemoryRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ReminderReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val message = intent.getStringExtra(EXTRA_MESSAGE).orEmpty()
        val eventDate = intent.getStringExtra(EXTRA_EVENT_DATE)
        val isRecurring = intent.getBooleanExtra(EXTRA_IS_RECURRING, false)

        NotificationHelper.ensureChannel(context)
        ReminderVibrator.vibrateReminder(context.applicationContext)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_RECORD_ID, recordId)
            putExtra(EXTRA_MESSAGE, message)
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            context,
            recordId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "语音小帮手提醒" })
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingOpenIntent)
            .also { NotificationHelper.applyReminderAlert(context, it) }
            .build()

        NotificationManagerCompat.from(context).notify(recordId.toInt(), notification)

        if (isRecurring) {
            scope.launch {
                val record = AppDatabase.getInstance(context).memoryDao().getById(recordId)
                    ?: return@launch
                if (record.isDailyRecurring()) {
                    // 「每天XX:XX」— 明天同一时刻再响
                    rescheduleNextDay(context, record, message)
                } else if (!eventDate.isNullOrBlank()) {
                    // 生日/年度纪念日 — 明年同一天再响
                    rescheduleNextYear(context, recordId, eventDate, message)
                }
            }
        }
    }

    private suspend fun rescheduleNextDay(
        context: Context,
        record: MemoryRecord,
        message: String
    ) {
        val time = ReminderTimeParser.parseEventTime(record.eventTime) ?: return
        val nextDay = LocalDate.now().plusDays(1)
        val updated = record.copy(formattedDateForAlarm = nextDay.toString())
        AppDatabase.getInstance(context).memoryDao().update(updated)
        ReminderScheduler(context).scheduleIfNeeded(updated, message)
    }

    private suspend fun rescheduleNextYear(
        context: Context,
        recordId: Long,
        eventDate: String,
        message: String
    ) {
        val record = AppDatabase.getInstance(context).memoryDao().getById(recordId) ?: return
        val isoDate = record.formattedDateForAlarm?.takeIf { it.isNotBlank() }?.let { parseIsoDate(it) }
            ?: parseIsoDate(eventDate)
            ?: return
        val nextDate = isoDate.plusYears(1)
        val updated = record.copy(formattedDateForAlarm = nextDate.toString())
        AppDatabase.getInstance(context).memoryDao().update(updated)
        ReminderScheduler(context).scheduleIfNeeded(updated, message)
    }

    private fun parseIsoDate(dateString: String): LocalDate? {
        return try {
            LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: Exception) {
            null
        }
    }

    /** 每天固定时刻提醒：isRecurring=true、有 event_time、且不是生日类别。 */
    private fun MemoryRecord.isDailyRecurring(): Boolean =
        isRecurring && !eventTime.isNullOrBlank() && category != "birthday"

    companion object {
        const val EXTRA_RECORD_ID = "extra_record_id"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_EVENT_DATE = "extra_event_date"
        const val EXTRA_IS_RECURRING = "extra_is_recurring"
    }
}
