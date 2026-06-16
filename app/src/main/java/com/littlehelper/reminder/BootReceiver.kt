package com.littlehelper.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.littlehelper.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        scope.launch {
            val records = AppDatabase.getInstance(context).memoryDao().getWithEventDate(limit = 100)
            ReminderScheduler(context).rescheduleAll(records)
            TodoDailyResetReceiver.runDailyTodoResetIfNewDay(context.applicationContext)
            TodoDailyResetScheduler(context.applicationContext).scheduleNextMidnight()
        }
    }
}
