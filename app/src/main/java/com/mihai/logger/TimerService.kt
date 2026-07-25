package com.mihai.logger

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

class TimerService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val activityName = intent.getStringExtra(EXTRA_ACTIVITY_NAME) ?: "Activity"
                val startTime = intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis())
                startForegroundService(activityName, startTime)
            }
            ACTION_UPDATE -> {
                val activityName = intent.getStringExtra(EXTRA_ACTIVITY_NAME) ?: "Activity"
                val startTime = intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis())
                startForegroundService(activityName, startTime)
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundService(activityName: String, startTime: Long) {
        createNotificationChannel()

        if (isRunning) {
            serviceScope.coroutineContext.cancelChildren()
        }
        isRunning = true

        serviceScope.launch {
            // IMMEDIATE NOTIFICATION (Required within 5 seconds)
            val initialNotification = buildNotification(activityName, System.currentTimeMillis() - startTime)

            try {
                startForeground(
                    1,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback: Try standard start if special type fails
                try {
                    startForeground(1, initialNotification)
                } catch (_: Exception) {
                }
            }

            // Ticking Loop
            while (isActive) {
                val durationMillis = System.currentTimeMillis() - startTime
                val notification = buildNotification(activityName, durationMillis)

                // Silent update
                val manager = getSystemService(NotificationManager::class.java)
                if (ActivityCompat.checkSelfPermission(this@TimerService, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    manager.notify(1, notification)
                }

                delay(1.seconds)
            }
        }
    }

    private fun buildNotification(activityName: String, durationMillis: Long): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("My Time: $activityName")
            .setContentText(formatDuration(durationMillis))
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Active Timer Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceJob.cancel()
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }

    companion object {
        const val CHANNEL_ID = "TimerServiceChannel"
        const val ACTION_START = "ACTION_START"
        const val ACTION_UPDATE = "ACTION_UPDATE"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_ACTIVITY_NAME = "EXTRA_ACTIVITY_NAME"
        const val EXTRA_START_TIME = "EXTRA_START_TIME"
    }
}