package com.mihai.logger

import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

class WearMainActivity : ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val allActivities = listOf("Matei", "Food", "NQ Live", "Trading Work", "Money Mgmt", "Shopping", "Housework", "Outside Stuff")

    private lateinit var prefs: SharedPreferences
    private var activeActivity by mutableStateOf<String?>(null)
    private var activeStartTime by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("WatchPrefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)

        // Load initial active state
        activeActivity = prefs.getString("active_activity", null)
        activeStartTime = prefs.getLong("active_start_time", 0L)

        // Process incoming intent (e.g. from clicking the widget)
        handleIntent(intent)

        setContent {
            MaterialTheme {
                if (activeActivity != null) {
                    // Show Ongoing Active Timer
                    ActiveTimerWatchScreen(
                        activityName = activeActivity!!,
                        startTime = activeStartTime,
                        onStopClick = {
                            sendCommandToPhone(path = "/stop_timer", payload = "")
                            // Assume stopped instantly to prevent UI lag
                            activeActivity = null
                            finish()
                        }
                    )
                } else {
                    // Show Full List Menu (New Google Home / Timer UI)
                    ActivityMenuScreen(
                        onActivityStart = { activityName ->
                            sendCommandToPhone(path = "/start_timer", payload = activityName)
                            // Assume started instantly to prevent UI lag
                            activeActivity = activityName
                            finish()
                        }
                    )
                }
            }
        }
    }

    // MANDATORY for Tile intents to work properly without stacking activities
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        var act = intent?.getStringExtra("START_ACTIVITY")

        // AGGRESSIVE FALLBACK: Glance secretly prefixes ActionParameter keys.
        // This loop safely searches all intent extras to find your selected activity instantly.
        if (act == null && intent?.extras != null) {
            for (key in intent.extras!!.keySet()) {
                val value = intent.extras!!.get(key)?.toString()
                if (value != null && allActivities.contains(value)) {
                    act = value
                    break
                }
            }
        }

        if (act != null) {
            // 1. Optimistically update the UI instantly!
            activeActivity = act
            activeStartTime = System.currentTimeMillis()

            // 2. Save it locally so the watch remembers it immediately
            prefs.edit().apply {
                putString("active_activity", activeActivity)
                putLong("active_start_time", activeStartTime)
                apply()
            }

            // 3. Send command to phone in the background
            sendCommandToPhone("/start_timer", act)

            // Clear the intent so it doesn't fire again if the activity rotates/recreates
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
                // Using the native coroutine .await() instead of Tasks.await()
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) {
                    runOnUiThread { Toast.makeText(this@WearMainActivity, "No phone connection", Toast.LENGTH_SHORT).show() }
                    return@launch
                }
                for (node in nodes) {
                    messageClient.sendMessage(node.id, path, payload.toByteArray()).await()
                }
                // (Toast removed for silent background operation)
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@WearMainActivity, "Link failed", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    @Composable
    fun WearActivityButton(name: String, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .height(44.dp)
                .background(Color(0xFF1E1E1E), RoundedCornerShape(22.dp))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(text = name, color = Color.White, fontSize = 13.sp)
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
            Text(formatDuration(currentTime - startTime), color = Color(0xFF2196F3), fontSize = 24.sp, fontFamily = FontFamily.Monospace)
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

    fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = (millis / (1000 * 60 * 60))
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}

@Composable
fun ActivityPill(
    text: String,
    backgroundColor: Color,
    textColor: Color = Color.Black,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 76.dp, height = 84.dp) // Tall pill shape
            .clip(RoundedCornerShape(32.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.button,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
fun ActivityMenuScreen(
    onActivityStart: (String) -> Unit
) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Optional Title
        item {
            Text(
                text = "Activities",
                color = Color.White,
                style = MaterialTheme.typography.caption1,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Row 1
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActivityPill(
                    text = "Trading",
                    backgroundColor = Color(0xFF00E5FF), // Cyan
                    onClick = { onActivityStart("Trading") }
                )
                ActivityPill(
                    text = "Matei",
                    backgroundColor = Color(0xFF2979FF), // Blue
                    onClick = { onActivityStart("Matei") }
                )
            }
        }

        // Row 2
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActivityPill(
                    text = "Food",
                    backgroundColor = Color(0xFFFF9100), // Orange
                    onClick = { onActivityStart("Food") }
                )
                ActivityPill(
                    text = "ALL",
                    backgroundColor = Color(0xFF424242), // Dark Gray
                    textColor = Color.White,
                    onClick = { onActivityStart("ALL") }
                )
            }
        }
    }
}