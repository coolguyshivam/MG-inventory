package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object GeminiScanner {
    private const val TAG = "GeminiScanner"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Sends the image base64 to the Gemini API to extract potential IMEIs and Serials.
     */
    suspend fun extractSerialsFromImage(context: Context, imageUri: Uri): List<String> = withContext(Dispatchers.IO) {
        val base64Data = AppUtils.uriToBase64(context, imageUri)
        if (base64Data.isNullOrBlank()) {
            Log.e(TAG, "Failed to convert image to Base64")
            return@withContext emptyList()
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is missing or is placeholder")
            return@withContext emptyList()
        }

        // Multimodal prompt designed for box OCR and screenshot reading
        val prompt = "This is an image of a mobile phone box label, tag, or screenshot showing mobile device identifier details. " +
                "Carefully scan the image and identify any IMEI numbers (usually 15 digits), serial numbers (S/N), or barcode values. " +
                "List all unique discovered numbers as a clean comma-separated list like: 869384028401938, 869384028401946. " +
                "Do NOT include any introduction, formatting or other words in your response. " +
                "Output only the raw comma-separated list of found numbers."

        try {
            // Build the JSON request object using org.json
            val inlineDataObj = JSONObject().apply {
                put("mimeType", "image/jpeg")
                put("data", base64Data)
            }

            val partTextObj = JSONObject().apply {
                put("text", prompt)
            }

            val partImageObj = JSONObject().apply {
                put("inlineData", inlineDataObj)
            }

            val partsArray = JSONArray().apply {
                put(partTextObj)
                put(partImageObj)
            }

            val contentObj = JSONObject().apply {
                put("parts", partsArray)
            }

            val contentsArray = JSONArray().apply {
                put(contentObj)
            }

            val requestJson = JSONObject().apply {
                put("contents", contentsArray)
            }

            val requestJsonString = requestJson.toString()

            val mediaType = "application/json".toMediaType()
            val requestBody = requestJsonString.toRequestBody(mediaType)

            // Try gemini-3.5-flash and gemini-2.5-flash sequentially.
            val models = listOf("gemini-3.5-flash", "gemini-2.5-flash")
            var errorString = ""

            for (model in models) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()

                try {
                    httpClient.newCall(request).execute().use { response ->
                        val bodyString = response.body?.string() ?: ""
                        if (response.isSuccessful && bodyString.isNotBlank()) {
                            val responseJson = JSONObject(bodyString)
                            val candidates = responseJson.optJSONArray("candidates")
                            val candidate = candidates?.optJSONObject(0)
                            val content = candidate?.optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            val part = parts?.optJSONObject(0)
                            val textResult = part?.optString("text") ?: ""

                            if (textResult.isNotBlank()) {
                                val discovered = parseNumbersFromText(textResult)
                                if (discovered.isNotEmpty()) {
                                    return@withContext discovered
                                }
                            }
                        } else {
                            errorString = "API failed ($model): Code ${response.code} – ${response.message}"
                            Log.w(TAG, errorString)
                        }
                    }
                } catch (e: Exception) {
                    errorString = "Request failed ($model): ${e.localizedMessage}"
                    Log.e(TAG, "Error invoking model $model", e)
                }
            }
            Log.w(TAG, "All models failed. Result: $errorString")
        } catch (je: Exception) {
            Log.e(TAG, "JSON or request creation error", je)
        }

        return@withContext emptyList()
    }

    private fun parseNumbersFromText(text: String): List<String> {
        val candidates = text.split(Regex("[\\s,;\\n|/]+"))
            .map { it.replace(Regex("^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$"), "") }
            .filter { it.isNotBlank() }
        
        val results = mutableSetOf<String>()
        for (w in candidates) {
            if (w.length in 14..16 && w.all { it.isDigit() }) {
                results.add(w)
            } else if (w.length in 8..18 && w.any { it.isDigit() } && w.any { it.isLetter() }) {
                results.add(w) // Barcode or Serial
            }
        }
        return results.toList()
    }
}
