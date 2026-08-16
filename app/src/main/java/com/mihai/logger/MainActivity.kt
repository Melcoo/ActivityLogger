package com.mihai.logger

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import java.text.SimpleDateFormat
import java.util.*

val DarkBackground = Color(0xFF121212)
val SurfaceColor = Color(0xFF1E1E1E)
val TextPrimary = Color(0xFFEEEEEE)
val TextSecondary = Color(0xFFB0B0B0)
val AccentColor = Color(0xFF2196F3)

object TimerStorage {
    private const val PREF_NAME = "MyTimePrefs"
    private const val KEY_ACTIVITY = "current_activity"
    private const val KEY_START_TIME = "start_time"
    private const val KEY_HISTORY = "activity_history"

    private fun getPrefs(context: Context): SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun saveTimer(context: Context, activityName: String, startTime: Long) {
        getPrefs(context).edit().putString(KEY_ACTIVITY, activityName).putLong(KEY_START_TIME, startTime).apply()
    }

    fun clearTimer(context: Context) {
        getPrefs(context).edit().remove(KEY_ACTIVITY).remove(KEY_START_TIME).apply()
    }

    fun getSavedActivity(context: Context): String? = getPrefs(context).getString(KEY_ACTIVITY, null)
    fun getSavedStartTime(context: Context): Long = getPrefs(context).getLong(KEY_START_TIME, 0L)

    fun logCompletedActivity(context: Context, activityName: String) {
        val prefs = getPrefs(context)
        val currentHistory = prefs.getString(KEY_HISTORY, "") ?: ""
        val timestamp = System.currentTimeMillis()
        val newEntry = "$activityName|$timestamp"
        val updatedHistory = if (currentHistory.isEmpty()) newEntry else "$currentHistory,$newEntry"
        prefs.edit().putString(KEY_HISTORY, updatedHistory).apply()
    }

    fun getTop3ActivitiesLastWeek(context: Context): List<String> {
        val historyStr = getPrefs(context).getString(KEY_HISTORY, "") ?: return emptyList()
        val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        return historyStr.split(",").mapNotNull {
            val parts = it.split("|")
            if (parts.size == 2) Pair(parts[0], parts[1].toLongOrNull() ?: 0L) else null
        }.filter { it.second >= oneWeekAgo }.groupingBy { it.first }.eachCount().entries.sortedByDescending { it.value }.take(3).map { it.key }
    }
}

// Syncs Top 3 list to Watch
fun syncTop3ToWatch(context: Context) {
    val top3 = TimerStorage.getTop3ActivitiesLastWeek(context)
    val defaults = listOf("Trading Work", "Matei", "Food")
    val finalTop3 = (top3 + defaults).distinct().take(3)
    val putDataReq = PutDataMapRequest.create("/top_activities").apply {
        dataMap.putStringArray("top3", finalTop3.toTypedArray())
        dataMap.putLong("timestamp", System.currentTimeMillis())
    }.asPutDataRequest()
    Wearable.getDataClient(context).putDataItem(putDataReq)
}

// NEW: Syncs ongoing active timer state to Watch
fun syncActiveTimerToWatch(context: Context, activityName: String?, startTime: Long) {
    val putDataReq = com.google.android.gms.wearable.PutDataMapRequest.create("/active_timer").apply {
        // Force an empty string instead of null, and add a timestamp so DataClient ALWAYS syncs
        dataMap.putString("activity", activityName ?: "")
        dataMap.putLong("start_time", startTime)
        dataMap.putLong("timestamp", System.currentTimeMillis())
    }.asPutDataRequest()

    // Forces the phone to bypass battery optimization and sync instantly!
    putDataReq.setUrgent()

    com.google.android.gms.wearable.Wearable.getDataClient(context).putDataItem(putDataReq)
}

class MainActivity : ComponentActivity() {
    private var triggerVoice by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == "ACTION_START_VOICE") triggerVoice = true

        syncTop3ToWatch(this)
        // Ensure watch knows current state on boot
        syncActiveTimerToWatch(this, TimerStorage.getSavedActivity(this), TimerStorage.getSavedStartTime(this))

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = DarkBackground, surface = SurfaceColor, primary = AccentColor)) {
                MainScreen(voiceTrigger = triggerVoice, onVoiceHandled = { triggerVoice = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == "ACTION_START_VOICE") triggerVoice = true
    }

    fun sendTimerCommand(action: String, activityName: String? = null, startTime: Long = 0L) {
        val intent = Intent(this, TimerService::class.java).apply {
            this.action = action
            putExtra(TimerService.EXTRA_ACTIVITY_NAME, activityName)
            putExtra(TimerService.EXTRA_START_TIME, startTime)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }
}

@Composable
fun MainScreen(voiceTrigger: Boolean, onVoiceHandled: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var currentActivity by remember { mutableStateOf(TimerStorage.getSavedActivity(context)?.let { name -> myActivities.find { it.name == name } ?: ActivityItem(name, Icons.Default.Edit, Color.White) }) }
    var startTime by remember { mutableLongStateOf(TimerStorage.getSavedStartTime(context).takeIf { it > 0 } ?: 0L) }
    var comment by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var customActivityName by remember { mutableStateOf("") }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
        if (spokenText != null) comment = if (comment.isEmpty()) spokenText else "$comment $spokenText"
    }

    // NEW: Real-time UI Sync Listener for the Phone
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                val savedActivity = TimerStorage.getSavedActivity(context)
                val savedStartTime = TimerStorage.getSavedStartTime(context)

                if (savedActivity != null) {
                    currentActivity = myActivities.find { it.name == savedActivity } ?: ActivityItem(savedActivity, Icons.Default.Edit, Color.White)
                    startTime = savedStartTime
                } else {
                    currentActivity = null
                    startTime = 0L
                }
            }
        }

        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            android.content.IntentFilter("UPDATE_TIMER_UI"),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(voiceTrigger) {
        if (voiceTrigger) {
            onVoiceHandled()
            delay(300)
            speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ro-RO") })
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize(), containerColor = DarkBackground) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Activity", fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    Text("MY TIME", fontSize = 28.sp, fontWeight = FontWeight.Black, color = TextPrimary, letterSpacing = 2.sp)
                }
            }

            if (currentActivity == null) {
                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
                    items(myActivities) { item ->
                        ActivityButton(item) {
                            currentActivity = item
                            startTime = System.currentTimeMillis()
                            TimerStorage.saveTimer(context, item.name, startTime)
                            syncActiveTimerToWatch(context, item.name, startTime) // Tell watch timer started
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = SurfaceColor, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextField(value = customActivityName, onValueChange = { customActivityName = it }, placeholder = { Text("Custom activity...", color = Color.Gray) }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary), modifier = Modifier.weight(1f), singleLine = true)
                        IconButton(onClick = { if (customActivityName.isNotBlank()) { currentActivity = ActivityItem(customActivityName, Icons.Default.Edit, Color.White); startTime = System.currentTimeMillis(); customActivityName = ""; TimerStorage.saveTimer(context, currentActivity!!.name, startTime); syncActiveTimerToWatch(context, currentActivity!!.name, startTime) } }, enabled = customActivityName.isNotBlank(), modifier = Modifier.background(if (customActivityName.isNotBlank()) AccentColor else Color.Gray, CircleShape).size(48.dp)) { Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = Color.Black) }
                    }
                }
            } else {
                LaunchedEffect(Unit) { (context as? MainActivity)?.sendTimerCommand(TimerService.ACTION_START, currentActivity!!.name, startTime) }
                ActiveTimerScreen(
                    activity = currentActivity!!, initialStartTime = startTime, comment = comment, isSending = isSending, onCommentChange = { comment = it },
                    onFinish = { finalStart, finalEnd ->
                        val actName = currentActivity?.name ?: return@ActiveTimerScreen

                        isSending = true
                        (context as? MainActivity)?.sendTimerCommand(TimerService.ACTION_STOP)
                        TimerStorage.clearTimer(context)
                        syncActiveTimerToWatch(context, null, 0L) // Tell watch timer stopped
                        TimerStorage.logCompletedActivity(context, actName)
                        syncTop3ToWatch(context)

                        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
                        val logEntry = LogEntry(activity = actName, startTime = sdf.format(Date(finalStart)), endTime = sdf.format(Date(finalEnd)), duration = formatDuration(finalEnd - finalStart), comment = comment)
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                RetrofitClient.api.logActivity(logEntry)
                                withContext(Dispatchers.Main) { isSending = false; currentActivity = null; comment = ""; Toast.makeText(context, "Log Saved!", Toast.LENGTH_SHORT).show() }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    isSending = false; Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                    if (comment.isNotEmpty()) clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(comment))
                                    currentActivity = null; comment = ""
                                }
                            }
                        }
                    },
                    onCancel = { (context as? MainActivity)?.sendTimerCommand(TimerService.ACTION_STOP); TimerStorage.clearTimer(context); syncActiveTimerToWatch(context, null, 0L); currentActivity = null; comment = "" },
                    onUpdateStartTime = { newTime ->
                        val actName = currentActivity?.name ?: return@ActiveTimerScreen
                        (context as? MainActivity)?.sendTimerCommand(TimerService.ACTION_UPDATE, actName, newTime)
                        TimerStorage.saveTimer(context, actName, newTime)
                        syncActiveTimerToWatch(context, actName, newTime)
                    },
                    onVoiceRequest = { speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ro-RO") }) }
                )
            }
        }
    }
}

@Composable
fun ActiveTimerScreen(
    activity: ActivityItem,
    initialStartTime: Long,
    comment: String,
    isSending: Boolean,
    onCommentChange: (String) -> Unit,
    onFinish: (Long, Long) -> Unit,
    onCancel: () -> Unit,
    onUpdateStartTime: (Long) -> Unit,
    onVoiceRequest: () -> Unit
) {
    var currentStartTime by remember { mutableLongStateOf(initialStartTime) }
    var endOffset by remember { mutableLongStateOf(0L) }
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val focusManager = LocalFocusManager.current
    val fiveMinMillis = 5 * 60 * 1000L

    LaunchedEffect(Unit) {
        while(true) {
            currentTime = System.currentTimeMillis()
            delay(1000.milliseconds)
        }
    }

    val effectiveEndTime = currentTime + endOffset
    val durationMillis = effectiveEndTime - currentStartTime
    val canShiftStartLater = durationMillis > fiveMinMillis
    val canShiftEndEarlier = durationMillis > fiveMinMillis
    val canShiftEndLater = true

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(100.dp).background(Brush.radialGradient(listOf(activity.color.copy(alpha = 0.3f), Color.Transparent))))
            Icon(activity.icon, contentDescription = null, tint = activity.color, modifier = Modifier.size(60.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(activity.name.uppercase(), color = TextSecondary, fontSize = 16.sp, letterSpacing = 2.sp)

        Text(
            text = formatDuration(durationMillis),
            color = if (durationMillis < 0) Color.Red else TextPrimary,
            fontSize = 64.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Start: ${SimpleDateFormat("HH:mm", Locale.US).format(Date(currentStartTime))}", color = Color.Gray, fontSize = 12.sp)
            val offsetLabel = if (endOffset == 0L) "" else if (endOffset > 0) "(+${endOffset/60000}m)" else "(${endOffset/60000}m)"
            Text(
                text = "End: ${SimpleDateFormat("HH:mm", Locale.US).format(Date(effectiveEndTime))} $offsetLabel",
                color = if (endOffset != 0L) AccentColor else Color.Gray,
                fontSize = 12.sp,
                fontWeight = if (endOffset != 0L) FontWeight.Bold else FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // ADJUST START
        Text("ADJUST START", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { currentStartTime -= fiveMinMillis; onUpdateStartTime(currentStartTime) }, colors = ButtonDefaults.buttonColors(containerColor = SurfaceColor), shape = RoundedCornerShape(12.dp), modifier = Modifier.height(40.dp)) { Text("-5m", color = TextSecondary) }
            Spacer(modifier = Modifier.width(16.dp)); Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray); Spacer(modifier = Modifier.width(16.dp))
            Button(onClick = { currentStartTime += fiveMinMillis; onUpdateStartTime(currentStartTime) }, enabled = canShiftStartLater, colors = ButtonDefaults.buttonColors(containerColor = SurfaceColor), shape = RoundedCornerShape(12.dp), modifier = Modifier.height(40.dp)) { Text("+5m", color = TextSecondary) }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // INPUT
        OutlinedTextField(
            value = comment, onValueChange = onCommentChange, placeholder = { Text("Add a note...", color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = TextFieldDefaults.colors(focusedContainerColor = SurfaceColor, unfocusedContainerColor = SurfaceColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, focusedIndicatorColor = AccentColor, unfocusedIndicatorColor = Color.Transparent),
            trailingIcon = { IconButton(onClick = onVoiceRequest) { Icon(Icons.Rounded.Mic, contentDescription = "Voice", tint = AccentColor) } }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ADJUST END & FINISH
        Text("ADJUST END & FINISH", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { endOffset -= fiveMinMillis }, enabled = canShiftEndEarlier && !isSending, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), shape = RoundedCornerShape(12.dp), modifier = Modifier.height(50.dp)) { Text("-5m") }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onFinish(currentStartTime, effectiveEndTime) }, colors = ButtonDefaults.buttonColors(containerColor = activity.color), shape = RoundedCornerShape(16.dp), modifier = Modifier.height(50.dp).weight(1f), enabled = !isSending) {
                if (isSending) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp)) else Text("FINISH", fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { endOffset += fiveMinMillis }, enabled = canShiftEndLater && !isSending, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), shape = RoundedCornerShape(12.dp), modifier = Modifier.height(50.dp)) { Text("+5m") }
        }

        Spacer(modifier = Modifier.height(32.dp))
        TextButton(onClick = onCancel, enabled = !isSending, modifier = Modifier.padding(bottom = 16.dp)) { Text("CANCEL SESSION", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
fun ActivityButton(item: ActivityItem, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SurfaceColor), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp), modifier = Modifier.height(130.dp).clickable { onClick() }) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(42.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
        }
    }
}

fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    val hours = (millis / (1000 * 60 * 60))
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}