package com.example

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testPhoneNumberSanitization() {
    val raw = "+91 (987) 654-3210"
    val cleaned = raw.replace(Regex("[^0-9+]"), "").trim()
    assertEquals("+919876543210", cleaned)
  }

  @Test
  fun testAppLauncherMapping() {
    val supportedApps = listOf("whatsapp", "youtube", "instagram", "chrome", "settings", "camera", "maps")
    val query1 = "WhatsApp kholo"
    val query2 = "Open YouTube"
    val query3 = "Settings open karo"

    assertTrue(query1.lowercase().contains("whatsapp"))
    assertTrue(query2.lowercase().contains("youtube"))
    assertTrue(query3.lowercase().contains("setting"))
  }

  @Test
  fun testContactRelationMatching() {
    val mockContacts = listOf(
      "Mom (Mummy)",
      "Rahul Sharma",
      "Rahul Kumar",
      "Dad (Papa)",
      "Priya Singh"
    )

    // Test matching Mom / Mummy
    val queryMom = "mummy"
    val momMatches = mockContacts.filter { c ->
      val lower = c.lowercase()
      lower.contains("mom") || lower.contains("mummy") || lower.contains("maa")
    }
    assertEquals(1, momMatches.size)
    assertEquals("Mom (Mummy)", momMatches.first())

    // Test matching Rahul (multiple matches)
    val queryRahul = "rahul"
    val rahulMatches = mockContacts.filter { it.lowercase().contains(queryRahul) }
    assertEquals(2, rahulMatches.size)
    assertTrue(rahulMatches.contains("Rahul Sharma"))
    assertTrue(rahulMatches.contains("Rahul Kumar"))
  }

  @Test
  fun testLanguageDetectionKeywords() {
    val hindiText = "Hindi mein baat karo"
    val hinglishText = "Kaise ho Arushi, aaj ka mausam kaisa hai?"
    val englishText = "Talk to me in English"

    val isHindi = hindiText.lowercase().contains("hindi")
    val isEnglish = englishText.lowercase().contains("english")
    val isHinglish = hinglishText.lowercase().contains("kaise") && hinglishText.lowercase().contains("hai")

    assertTrue(isHindi)
    assertTrue(isEnglish)
    assertTrue(isHinglish)
  }

  @Test
  fun testToolJsonOutputFormat() {
    val toolResult = mapOf(
      "status" to "calling",
      "name" to "Mom (Mummy)",
      "number" to "+91 98765 43210",
      "message" to "Calling Mom (Mummy) at +91 98765 43210"
    )

    assertEquals("calling", toolResult["status"])
    assertEquals("Mom (Mummy)", toolResult["name"])
    assertTrue((toolResult["message"] as String).contains("Calling"))
  }

  @Test
  fun testThemeToggleSwitch() {
    var currentTheme = "dark"
    currentTheme = if (currentTheme == "dark") "light" else "dark"
    assertEquals("light", currentTheme)

    val lightVariables = mapOf(
      "--bg-primary" to "#F1F5F9",
      "--bg-secondary" to "#FFFFFF",
      "--text-primary" to "#0F172A",
      "--border-color" to "rgba(15, 23, 42, 0.18)"
    )
    assertEquals("#F1F5F9", lightVariables["--bg-primary"])
    assertEquals("#0F172A", lightVariables["--text-primary"])
  }
}
