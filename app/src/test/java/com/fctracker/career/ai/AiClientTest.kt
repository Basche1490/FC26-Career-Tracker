package com.fctracker.career.ai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiClientTest {

    @Test
    fun everyProviderHasADefaultModel() {
        assertTrue(AiClient.defaultModel("Google Gemini").startsWith("gemini"))
        assertTrue(AiClient.defaultModel("Claude").startsWith("claude"))
        assertTrue(AiClient.defaultModel("Claude Haiku").startsWith("claude")) // label from older saves
        assertTrue(AiClient.defaultModel("ChatGPT 3.5").startsWith("gpt")) // label from older saves
        assertTrue(AiClient.defaultModel("Grok").startsWith("grok"))
        assertEquals("gpt-3.5-turbo", AiClient.defaultModel("Other")) // what the earlier app always sent
    }

    @Test
    fun missingKeyIsExplainedWithoutAnyNetworkCall() = runBlocking {
        assertTrue(AiClient.ask("hi", "Claude", "", "").contains("API key needed"))
        assertTrue(AiClient.ask("hi", "Other", "https://example.invalid", "  ").contains("API key needed"))
    }

    @Test
    fun providerErrorsAreShownInPlainWords() {
        val body = """{"error":{"message":"models/gemini-1.5-flash-latest is not found","status":"NOT_FOUND"}}"""
        val text = AiClient.errorText(404, body, "gemini-1.5-flash-latest")
        assertTrue(text.startsWith("Error 404: models/gemini-1.5-flash-latest is not found"))
        assertTrue(text.contains("Settings"))

        assertTrue(AiClient.errorText(401, """{"error":"bad key"}""", "m").contains("API key"))
        assertTrue(AiClient.errorText(429, "", "m").contains("Rate limit"))
        assertEquals("Error 500.", AiClient.errorText(500, "<html>oops</html>", "m"))
    }
}
