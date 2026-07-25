package com.mihai.logger

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class WatchDataListener : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                val prefs = getSharedPreferences("WatchPrefs", Context.MODE_PRIVATE)

                if (path == "/top_activities") {
                    val top3 = dataMap.getStringArray("top3") ?: return

                    prefs.edit().apply {
                        putString("top1", top3.getOrNull(0) ?: "Trading Work")
                        putString("top2", top3.getOrNull(1) ?: "Matei")
                        putString("top3", top3.getOrNull(2) ?: "Food")
                        apply()
                    }

                    // Force layout recreation for the Tile Service
                    val updater = TileService.getUpdater(this)
                    updater.requestUpdate(LoggerTileService::class.java)
                }

                else if (path == "/active_timer") {
                    val act = dataMap.getString("activity")
                    val startTime = dataMap.getLong("start_time")

                    prefs.edit().apply {
                        if (act.isNullOrEmpty()) {
                            remove("active_activity")
                            remove("active_start_time")
                        } else {
                            putString("active_activity", act)
                            putLong("active_start_time", startTime)
                        }
                        apply()
                    }
                }
            }
        }
    }
}