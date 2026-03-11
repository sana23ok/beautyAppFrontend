package com.example.beautyappfrontend.utils

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object DebugLogger {
    private const val ENDPOINT = "http://127.0.0.1:7785/ingest/f7f64bd7-02ba-46c5-b9d6-f6444d2a9891"
    private const val SESSION_ID = "a0a51a"

    fun log(
        runId: String,
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
    ) {
        Thread {
            try {
                val payload = JSONObject().apply {
                    put("sessionId", SESSION_ID)
                    put("runId", runId)
                    put("hypothesisId", hypothesisId)
                    put("location", location)
                    put("message", message)
                    put("data", JSONObject(data))
                    put("timestamp", System.currentTimeMillis())
                }

                val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Debug-Session-Id", SESSION_ID)
                }

                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                connection.responseCode
                connection.disconnect()
            } catch (_: Exception) {
            }
        }.start()
    }
}
