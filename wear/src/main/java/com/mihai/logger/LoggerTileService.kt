package com.mihai.logger

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.wear.tiles.GlanceTileService

@Suppress("DEPRECATION") // Suppresses the warning about GlanceTileService deprecation in alpha/beta versions
class LoggerTileService : GlanceTileService() {

    @SuppressLint("RestrictedApi") // Required to suppress the ColorProvider library group warning
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val prefs = context.getSharedPreferences("WatchPrefs", Context.MODE_PRIVATE)
        val top1 = prefs.getString("top1", "Trading Work")!!
        val top2 = prefs.getString("top2", "Matei")!!
        val top3 = prefs.getString("top3", "Food")!!

        fun getColor(name: String): Color = when (name) {
            "Matei" -> Color(0xFF2979FF)
            "Food" -> Color(0xFFFF9100)
            "NQ Live" -> Color(0xFF00E676)
            "Trading Work" -> Color(0xFF00BCD4)
            "Money Mgmt" -> Color(0xFFFFD740)
            "Shopping" -> Color(0xFFD500F9)
            "Housework" -> Color(0xFF8D6E63)
            "Outside Stuff" -> Color(0xFFE0E0E0)
            else -> Color(0xFF607D8B)
        }

        fun createAction(activityName: String?): Action {
            // Build an explicit intent so the system correctly extracts the extras
            val intent = Intent(context, WearMainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (activityName != null) {
                    putExtra("START_ACTIVITY", activityName)
                }
            }
            return actionStartActivity(intent)
        }

        Column(
            modifier = GlanceModifier.fillMaxSize().background(Color(0xFF121212)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                ActivityBubble(top1, getColor(top1), createAction(top1))
                Spacer(modifier = GlanceModifier.width(12.dp))
                ActivityBubble(top2, getColor(top2), createAction(top2))
            }
            Spacer(modifier = GlanceModifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                ActivityBubble(top3, getColor(top3), createAction(top3))
                Spacer(modifier = GlanceModifier.width(12.dp))
                ActivityBubble("ALL", Color(0xFF333333), createAction(null))
            }
        }
    }

    @SuppressLint("RestrictedApi")
    @Composable
    private fun ActivityBubble(name: String, color: Color, action: Action) {
        val displayName = if (name.length > 9) name.substring(0, 7) + ".." else name
        Box(
            modifier = GlanceModifier
                .size(62.dp)
                .background(color)
                .clickable(action),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayName,
                style = TextStyle(color = ColorProvider(Color.White), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.padding(4.dp)
            )
        }
    }
}