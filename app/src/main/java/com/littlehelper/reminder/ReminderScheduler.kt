package com.littlehelper.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.littlehelper.data.MemoryRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleIfNeeded(record: MemoryRecord) {
        scheduleIfNeeded(record, record.summary)
    }

    fun scheduleIfNeeded(record: MemoryRecord, reminderText: String) {
        var triggerAt = buildTriggerTime(record) ?: return

        if (triggerAt <= System.currentTimeMillis()) {
            // 每日固定时刻提醒（如「每天晚上九点半」）：当天时刻已过，自动推到明天同一时刻。
            if (record.isDailyRecurring()) {
                val time = ReminderTimeParser.parseEventTime(record.eventTime) ?: return
                triggerAt = LocalDateTime.of(LocalDate.now().plusDays(1), time)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else {
                return  // 一次性过期事件，静默跳过
            }
        }

        val alarmDate = record.formattedDateForAlarm?.takeIf { it.isNotBlank() }
            ?: record.eventDate.orEmpty()

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_RECORD_ID, record.id)
            putExtra(ReminderReceiver.EXTRA_TITLE, "语音小帮手提醒")
            putExtra(
                ReminderReceiver.EXTRA_MESSAGE,
                reminderText.ifBlank { record.summary }
            )
            putExtra(ReminderReceiver.EXTRA_EVENT_DATE, alarmDate)
            putExtra(ReminderReceiver.EXTRA_IS_RECURRING, record.isRecurring)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            record.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            pendingIntent
        )
    }

    fun rescheduleAll(records: List<MemoryRecord>) {
        records.forEach { record ->
            scheduleIfNeeded(record)
        }
    }

    fun cancelReminder(recordId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            recordId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun buildTriggerTime(record: MemoryRecord): Long? {
        return ReminderTimeParser.resolveTriggerMillis(
            formattedDateForAlarm = record.formattedDateForAlarm,
            eventDate = record.eventDate,
            eventTime = record.eventTime
        )
    }
}

/** 每天固定时刻提醒的判断：isRecurring=true、有 event_time、且不是生日类别。 */
private fun MemoryRecord.isDailyRecurring(): Boolean =
    isRecurring && !eventTime.isNullOrBlank() && category != "birthday"
