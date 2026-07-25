package com.mihai.logger

import android.content.Intent
import android.os.Build
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhoneMessageListener : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        val currentTime = System.currentTimeMillis()

        if (event.path == "/start_timer") {
            val activityName = String(event.data)

            // 1. Save locally & Update Watch UI state
            TimerStorage.saveTimer(this, activityName, currentTime)
            syncActiveTimerToWatch(this, activityName, currentTime)

            // 2. Start Foreground Notification
            val timerIntent = Intent(this, TimerService::class.java).apply {
                action = TimerService.ACTION_START
                putExtra(TimerService.EXTRA_ACTIVITY_NAME, activityName)
                putExtra(TimerService.EXTRA_START_TIME, currentTime)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(timerIntent) else startService(timerIntent)

            // 3. Open UI
            startActivity(Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP })
        }

        else if (event.path == "/stop_timer") {
            // STOP command received from watch
            val currentActivity = TimerStorage.getSavedActivity(this)
            val startTime = TimerStorage.getSavedStartTime(this)

            if (currentActivity != null && startTime > 0) {
                // Clear local states
                TimerStorage.clearTimer(this)
                syncActiveTimerToWatch(this, null, 0L)
                TimerStorage.logCompletedActivity(this, currentActivity)
                syncTop3ToWatch(this)

                // Stop Notification Service
                startService(Intent(this, TimerService::class.java).apply { action = TimerService.ACTION_STOP })

                // Upload to Sheets directly in background
                CoroutineScope(Dispatchers.IO).launch {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
                    val logEntry = LogEntry(
                        activity = currentActivity,
                        startTime = sdf.format(Date(startTime)),
                        endTime = sdf.format(Date(currentTime)),
                        duration = formatDuration(currentTime - startTime),
                        comment = "Logged via Watch"
                    )
                    try {
                        RetrofitClient.api.logActivity(logEntry)
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}