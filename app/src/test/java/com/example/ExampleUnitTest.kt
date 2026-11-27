package com.example

import com.example.ai.GeminiConfig
import com.example.ai.GeminiRest
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun defaultProxyUrlAndValidationWorkAsExpected() {
    assertEquals(
      "https://late-feather-3c04webforge-ai.alrwysm239.workers.dev",
      GeminiConfig.DEFAULT_PROXY_URL,
    )
    assertEquals("", GeminiConfig.validateProxyUrl("   ").getOrNull())
    assertTrue(GeminiConfig.validateProxyUrl("http://insecure.workers.dev").isFailure)
    assertTrue(GeminiConfig.validateProxyUrl("random-text").isFailure)
    assertEquals(
      "https://custom.example.workers.dev",
      GeminiConfig.validateProxyUrl("  https://custom.example.workers.dev/  ").getOrNull(),
    )
    assertEquals(
      "https://late-feather-3c04webforge-ai.alrwysm239.workers.dev/v1beta/models/gemini-2.5-flash:generateContent",
      GeminiRest.buildEndpointUrl(GeminiConfig.DEFAULT_PROXY_URL, GeminiConfig.MODEL, stream = false),
    )
    assertEquals(
      "https://late-feather-3c04webforge-ai.alrwysm239.workers.dev/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse",
      GeminiRest.buildEndpointUrl(GeminiConfig.DEFAULT_PROXY_URL, GeminiConfig.MODEL, stream = true),
    )
  }
}
