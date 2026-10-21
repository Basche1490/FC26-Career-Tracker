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
        // "Other" (any OpenAI-compatible endpoint): what the earlier app always sent. Local servers such as
        // LM Studio ignore the name; for others the user enters the right one in Settings.
        else -> "gpt-3.5-turbo"
    }

    /** One earlier turn of the conversation: "user" or "assistant", and what was said. */
    data class Turn(val role: String, val text: String)

    /**
     * Sends [prompt] (the newest message) with the [system] rules and data in the provider's own system slot, and
     * the earlier [history] as real conversation turns.
     */
    suspend fun ask(prompt: String, agent: String, apiUrl: String, apiKey: String, model: String = "", system: String? = null, history: List<Turn> = emptyList()): String =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext "API key needed. Please provide your API key in Settings."
            val chosenModel = model.trim().ifBlank { defaultModel(agent) }
            // Providers want the conversation to start with the person and to alternate.
            val turns = (history + Turn("user", prompt)).dropWhile { it.role != "user" }.fold(mutableListOf<Turn>()) { acc, t ->
                if (acc.isNotEmpty() && acc.last().role == t.role) acc[acc.lastIndex] = Turn(t.role, acc.last().text + "\n\n" + t.text) else acc.add(t)
                acc
            }
            try {
                val request = when {
                    agent.contains("Gemini", true) -> geminiRequest(turns, system, apiKey, chosenModel)
                    agent.contains("Claude", true) -> claudeRequest(turns, system, apiKey, chosenModel)
                    else -> openAiStyleRequest(turns, system, agent, apiUrl, apiKey, chosenModel)
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

    private fun geminiRequest(turns: List<Turn>, system: String?, apiKey: String, model: String): Request {
        val contents = JSONArray()
        turns.forEach { t ->
            contents.put(JSONObject().put("role", if (t.role == "user") "user" else "model").put("parts", JSONArray().put(JSONObject().put("text", t.text))))
        }
        val body = JSONObject().put("contents", contents)
        if (!system.isNullOrBlank()) body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        return Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            // The key goes in a header, not in the URL, so it never ends up in logs or proxies.
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    private fun claudeRequest(turns: List<Turn>, system: String?, apiKey: String, model: String): Request {
        val messages = JSONArray()
        turns.forEach { t -> messages.put(JSONObject().put("role", if (t.role == "user") "user" else "assistant").put("content", t.text)) }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 2048)
            .put("messages", messages)
        if (!system.isNullOrBlank()) body.put("system", system)
        return Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    private fun openAiStyleRequest(turns: List<Turn>, system: String?, agent: String, apiUrl: String, apiKey: String, model: String): Request {
        val messages = JSONArray()
        if (!system.isNullOrBlank()) messages.put(JSONObject().put("role", "system").put("content", system))
        turns.forEach { t -> messages.put(JSONObject().put("role", if (t.role == "user") "user" else "assistant").put("content", t.text)) }
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
        val url = when {
            agent == "Other" && apiUrl.isNotBlank() ->
                if (apiUrl.endsWith("/chat/completions") || apiUrl.endsWith("/messages")) apiUrl
                else apiUrl.trimEnd('/') + "/chat/completions"
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
