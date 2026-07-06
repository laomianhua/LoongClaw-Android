package com.littlehelper.gateway

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.littlehelper.MainActivity
import com.littlehelper.R
import com.littlehelper.shell.model.ConnectionState

/**
 * Phase B：前台 Service，降低后台 WebSocket 被系统回收的概率。
 * 不改变 [GatewayConnectionManager] 重连策略与间隔。
 */
class GatewayConnectionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val state = intent?.getStringExtra(EXTRA_CONNECTION_STATE)
                    ?.let { runCatching { ConnectionState.valueOf(it) }.getOrNull() }
                    ?: ConnectionState.CONNECTING
                val notification = buildNotification(state)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                return START_STICKY
            }
        }
    }

    private fun ensureNotificationChannel() {
        ensureNotificationChannel(this)
    }

    private fun buildNotification(state: ConnectionState): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(notificationText(state))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun notificationText(state: ConnectionState): String = when (state) {
        ConnectionState.ONLINE -> getString(R.string.gateway_service_status_online)
        ConnectionState.CONNECTING -> getString(R.string.gateway_service_status_connecting)
        ConnectionState.DEGRADED -> getString(R.string.gateway_service_status_degraded)
        ConnectionState.DISCONNECTED -> getString(R.string.gateway_service_status_disconnected)
    }

    companion object {
        private const val CHANNEL_ID = "gateway_connection"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START = "com.littlehelper.gateway.START"
        private const val ACTION_STOP = "com.littlehelper.gateway.STOP"
        private const val EXTRA_CONNECTION_STATE = "connection_state"

        fun start(context: Context, state: ConnectionState = ConnectionState.CONNECTING) {
            val intent = Intent(context, GatewayConnectionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CONNECTION_STATE, state.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateState(context: Context, state: ConnectionState) {
            ensureNotificationChannel(context)
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val text = when (state) {
                ConnectionState.ONLINE -> context.getString(R.string.gateway_service_status_online)
                ConnectionState.CONNECTING -> context.getString(R.string.gateway_service_status_connecting)
                ConnectionState.DEGRADED -> context.getString(R.string.gateway_service_status_degraded)
                ConnectionState.DISCONNECTED -> context.getString(R.string.gateway_service_status_disconnected)
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, GatewayConnectionService::class.java).apply {
                    action = ACTION_STOP
                },
            )
        }

        private fun ensureNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.gateway_service_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.gateway_service_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
