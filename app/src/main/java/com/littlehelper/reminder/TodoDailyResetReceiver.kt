package com.littlehelper.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.littlehelper.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate

class TodoDailyResetReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        scope.launch {
            try {
                runDailyTodoReset(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "LittleHelperDB"

        suspend fun runDailyTodoReset(context: Context): Int {
            val dao = AppDatabase.getInstance(context).memoryDao()
            val resetCount = dao.resetDailyTodoDoneFlags()
            markResetCompleted(context)
            TodoDailyResetScheduler(context).scheduleNextMidnight()
            Log.i(TAG, "Daily todo done reset: $resetCount record(s)")
            return resetCount
        }

        /** App 启动补跑：若今日尚未执行过 0 点重置（例如午夜时手机关机），则立即重置。 */
        suspend fun runDailyTodoResetIfNewDay(context: Context): Int {
            if (isResetCompletedToday(context)) return 0
            return runDailyTodoReset(context)
        }

        private fun isResetCompletedToday(context: Context): Boolean {
            val prefs = context.getSharedPreferences(
                TodoDailyResetScheduler.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val last = prefs.getString(TodoDailyResetScheduler.KEY_LAST_RESET_DATE, null)
            return last == LocalDate.now().toString()
        }

        private fun markResetCompleted(context: Context) {
            context.getSharedPreferences(
                TodoDailyResetScheduler.PREFS_NAME,
                Context.MODE_PRIVATE
            ).edit()
                .putString(
                    TodoDailyResetScheduler.KEY_LAST_RESET_DATE,
                    LocalDate.now().toString()
                )
                .apply()
        }
    }
}
