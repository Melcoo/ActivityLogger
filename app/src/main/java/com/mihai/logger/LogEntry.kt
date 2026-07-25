package com.mihai.logger

// 👇 THESE IMPORTS ARE CRITICAL
import com.google.gson.annotations.SerializedName

data class LogEntry(
    @SerializedName("activity") val activity: String,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("endTime") val endTime: String,
    @SerializedName("duration") val duration: String,
    @SerializedName("comment") val comment: String,
)

data class ScriptResponse(
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String,
)