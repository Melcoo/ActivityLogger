package com.mihai.logger

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WearMainActivity : ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: SharedPreferences
    private var activeActivity by mutableStateOf<String?>(null)
    private var activeStartTime by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("WatchPrefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)

        activeActivity = prefs.getString("active_activity", null)
        activeStartTime = prefs.getLong("active_start_time", 0L)

        handleIntent(intent)

        setContent {
            MaterialTheme {
                val context = LocalContext.current

                DisposableEffect(context) {
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context?, intent: Intent?) {
                            activeActivity = prefs.getString("active_activity", null)
                            activeStartTime = prefs.getLong("active_start_time", 0L)
                        }
                    }
                    ContextCompat.registerReceiver(
                        context,
                        receiver,
                        IntentFilter("UPDATE_WATCH_UI"),
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                    onDispose { context.unregisterReceiver(receiver) }
                }

                if (activeActivity != null) {
                    ActiveTimerWatchScreen(
                        activityName = activeActivity!!,
                        startTime = activeStartTime,
                        onStopClick = {
                            sendCommandToPhone("/stop_timer", "")
                            activeActivity = null
                            // NO finish() here so app stays open
                        }
                    )
                } else {
                    ActivityMenuScreen(
                        onActivityStart = { act ->
                            sendCommandToPhone("/start_timer", act)
                            activeActivity = act
                            activeStartTime = System.currentTimeMillis()
                            // NO finish() here so app stays open
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        var act = intent?.getStringExtra("START_ACTIVITY")

        if (act == null && intent?.extras != null) {
            val allActivities = listOf("Matei", "Food", "Trading", "Money Mgmt", "Shopping", "Housework", "Outside Stuff", "Moto", "Misc")
            for (key in intent.extras!!.keySet()) {
                val value = intent.extras!!.get(key)?.toString()
                if (value != null && allActivities.contains(value)) {
                    act = value
                    break
                }
            }
        }

        if (act != null) {
            activeActivity = act
            activeStartTime = System.currentTimeMillis()

            prefs.edit().apply {
                putString("active_activity", activeActivity)
                putLong("active_start_time", activeStartTime)
                apply()
            }

            sendCommandToPhone("/start_timer", act)
            intent?.removeExtra("START_ACTIVITY")
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "active_activity" || key == "active_start_time") {
            activeActivity = prefs.getString("active_activity", null)
            activeStartTime = prefs.getLong("active_start_time", 0L)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    private fun sendCommandToPhone(path: String, payload: String) {
        val messageClient = Wearable.getMessageClient(this)
        val nodeClient = Wearable.getNodeClient(this)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val nodes = Tasks.await(nodeClient.connectedNodes)
                if (nodes.isEmpty()) {
                    runOnUiThread { Toast.makeText(this@WearMainActivity, "No phone connection", Toast.LENGTH_SHORT).show() }
                    return@launch
                }
                for (node in nodes) {
                    Tasks.await(messageClient.sendMessage(node.id, path, payload.toByteArray()))
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@WearMainActivity, "Link failed", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}

@Composable
fun ActivityMenuScreen(onActivityStart: (String) -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActivityPill("Trading Work", Color(0xFF00BCD4)) { onActivityStart("Trading Work") }
                ActivityPill("Matei", Color(0xFF2979FF)) { onActivityStart("Matei") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActivityPill("Food", Color(0xFFFF9100)) { onActivityStart("Food") }
                ActivityPill("NQ Live", Color(0xFF00E676), Color.Black) { onActivityStart("NQ Live") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActivityPill("Money Mgmt", Color(0xFFFFD740), Color.Black) { onActivityStart("Money Mgmt") }
                ActivityPill("Shopping", Color(0xFFD500F9)) { onActivityStart("Shopping") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                ActivityPill("Housework", Color(0xFF8D6E63)) { onActivityStart("Housework") }
                ActivityPill("Outside Stuff", Color(0xFFE0E0E0), Color.Black) { onActivityStart("Outside Stuff") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // 🟢 NEW: Moto button with the Motorcycle icon!
                ActivityPill("Moto", Color(0xFFE53935)) { onActivityStart("Moto") }
                ActivityPill("ALL", Color(0xFF333333)) { onActivityStart("ALL") }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
fun ActivityPill(text: String, backgroundColor: Color, textColor: Color = Color.White, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 76.dp, height = 84.dp)
            .clip(RoundedCornerShape(36.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
fun ActiveTimerWatchScreen(activityName: String, startTime: Long, onStopClick: () -> Unit) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) { currentTime = System.currentTimeMillis(); delay(1000) } }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF121212)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ACTIVE SESSION", color = Color.Gray, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(activityName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))

        val duration = currentTime - startTime
        val seconds = (duration / 1000) % 60
        val minutes = (duration / (1000 * 60)) % 60
        val hours = (duration / (1000 * 60 * 60))
        val timeString = String.format(java.util.Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

        Text(timeString, color = Color(0xFF2196F3), fontSize = 24.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onStopClick,
            colors = ButtonDefaults.primaryButtonColors(backgroundColor = Color(0xFFD32F2F)),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(imageVector = Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
        }
    }
}