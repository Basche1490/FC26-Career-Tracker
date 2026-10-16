package com.fctracker.career.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sends one prompt to the AI provider chosen in Settings and returns the answer as plain text.
 *
 * Provider model names change often and old ones get shut down, so the model is a setting. When it is left
 * blank, [defaultModel] supplies a current one.
 */
object AiClient {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    private val JSON = "application/json".toMediaType()

    /** Built-in model per provider, used while the Model setting is blank. */
    fun defaultModel(agent: String): String = when {
        agent.contains("Gemini", true) -> "gemini-3.5-flash-lite"
        agent.contains("Claude", true) -> "claude-haiku-5-5"
        agent.contains("Grok", true) -> "grok-4.7"
        agent.contains("ChatGPT", true) -> "gpt-5-mini"
        else -> "" // "Other": there is no sensible default, the user must enter one
    }

    suspend fun ask(prompt: String, agent: String, apiUrl: String, apiKey: String, model: String = ""): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext "API key needed. Please provide your API key in Settings."
            val chosenModel = model.trim().ifBlank { defaultModel(agent) }
            if (chosenModel.isBlank()) {
                return@withContext "Model name needed. Enter the model your provider offers in Settings > AI Assistant."
            }
            try {
                val request = when {
                    agent.contains("Gemini", true) -> geminiRequest(prompt, apiKey, chosenModel)
                    agent.contains("Claude", true) -> claudeRequest(prompt, apiKey, chosenModel)
                    else -> openAiStyleRequest(prompt, agent, apiUrl, apiKey, chosenModel)
                }
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@use errorText(response.code, body, chosenModel)
                    val json = JSONObject(body)
                    when {
                        agent.contains("Gemini", true) -> parseGemini(json)
                        agent.contains("Claude", true) -> parseClaude(json)
                        else -> parseOpenAiStyle(json)
                    }
                }
            } catch (e: Exception) {
                "Error: ${e.message ?: e.javaClass.simpleName}"
            }
        }

    // -- requests ------------------------------------------------------------------------------------

    private fun geminiRequest(prompt: String, apiKey: String, model: String): Request {
        val body = JSONObject().put(
            "contents",
            JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))
        )
        return Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            // The key goes in a header, not in the URL, so it never ends up in logs or proxies.
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    private fun claudeRequest(prompt: String, apiKey: String, model: String): Request {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 2048)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        return Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    private fun openAiStyleRequest(prompt: String, agent: String, apiUrl: String, apiKey: String, model: String): Request {
        val body = JSONObject()
            .put("model", model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        val url = when {
            agent == "Other" && apiUrl.isNotBlank() ->
                if (apiUrl.endsWith("/chat/completions")) apiUrl else apiUrl.trimEnd('/') + "/chat/completions"
            agent.contains("Grok", true) -> "https://api.x.ai/v1/chat/completions"
            else -> "https://api.openai.com/v1/chat/completions"
        }
        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    // -- responses -----------------------------------------------------------------------------------

    private fun parseGemini(json: JSONObject): String {
        val candidates = json.optJSONArray("candidates")
        val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        if (parts == null || parts.length() == 0) {
            val reason = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            return if (reason.isNotBlank()) "The AI did not answer (blocked: $reason)." else "The AI returned an empty answer."
        }
        return (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
    }

    private fun parseClaude(json: JSONObject): String {
        val content = json.optJSONArray("content") ?: return "The AI returned an empty answer."
        return (0 until content.length())
            .map { content.getJSONObject(it) }
            .filter { it.optString("type", "text") == "text" }
            .joinToString("") { it.optString("text") }
            .ifBlank { "The AI returned an empty answer." }
    }

    private fun parseOpenAiStyle(json: JSONObject): String =
        json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?.takeIf { it.isNotBlank() } ?: "The AI returned an empty answer."

    /** Pulls the provider's own message out of an error body and adds a hint for the usual causes. */
    internal fun errorText(code: Int, body: String, model: String): String {
        val providerMessage = try {
            val err = JSONObject(body).opt("error")
            when (err) {
                is JSONObject -> err.optString("message")
                is String -> err
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
        val hint = when (code) {
            401, 403 -> " Check the API key in Settings."
            404 -> " The model \"$model\" may not exist (any more). Change it in Settings > AI Assistant."
            429 -> " Rate limit or quota reached; try again later."
            else -> ""
        }
        val detail = if (providerMessage.isNotBlank()) ": $providerMessage" else ""
        return "Error $code$detail.$hint"
    }
}
