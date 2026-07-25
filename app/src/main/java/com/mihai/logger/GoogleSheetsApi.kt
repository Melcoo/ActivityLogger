package com.mihai.logger

// 👇 THESE IMPORTS ARE CRITICAL
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

interface GoogleSheetsService {
    @POST("exec")
    suspend fun logActivity(@Body entry: LogEntry): ScriptResponse
}

object RetrofitClient {
    private const val FULL_URL = "https://script.google.com/macros/s/AKfycbzCyrHWSs5HWSzJy9KgOAxnJz-HD92bTMGjODStnAUMAjzUa6rCkAojx9JeRENRBJ_hdA/exec"

    // This logic automatically fixes the URL for Retrofit
    private val BASE_URL = if (FULL_URL.endsWith("/exec")) {
        FULL_URL.substringBeforeLast("exec")
    } else {
        FULL_URL
    }

    val api: GoogleSheetsService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleSheetsService::class.java)
    }
}