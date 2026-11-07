package com.example.bridge

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Platform-aware JavaScript-to-Native Android Bridge for Arushi AI Assistant.
 * Exposes device functions: openApp, makeCall, callContact, openWhatsApp, openUrl.
 */
class AndroidBridge(
    private val activity: Activity,
    private val onPermissionRequested: ((String) -> Unit)? = null
) {

    private val prefs = activity.getSharedPreferences("arushi_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "ArushiBridge"
    }

    data class ContactRecord(val name: String, val number: String)

    @JavascriptInterface
    fun isNativeBridgeAvailable(): Boolean {
        return true
    }

    @JavascriptInterface
    fun getApiKey(): String {
        val saved = prefs.getString("custom_gemini_api_key", "")?.trim().orEmpty()
        if (saved.isNotEmpty()) return saved
        return try {
            BuildConfig.GEMINI_API_KEY.trim()
        } catch (e: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun saveApiKey(apiKey: String): Boolean {
        prefs.edit().putString("custom_gemini_api_key", apiKey.trim()).apply()
        return true
    }

    @JavascriptInterface
    fun setTheme(theme: String) {
        activity.runOnUiThread {
            try {
                val isLight = theme.equals("light", ignoreCase = true)
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                insetsController.isAppearanceLightStatusBars = isLight
                insetsController.isAppearanceLightNavigationBars = isLight
            } catch (e: Exception) {
                Log.w(TAG, "Error updating status bars for theme: ${e.message}")
            }
        }
    }

    @JavascriptInterface
    fun getPlatformInfo(): String {
        val info = JSONObject()
        info.put("platform", "Android")
        info.put("osVersion", android.os.Build.VERSION.RELEASE)
        info.put("device", android.os.Build.MODEL)
        info.put("bridgeActive", true)
        return info.toString()
    }

    /**
     * openWhatsApp() - opens WhatsApp or WhatsApp Business via native intent.
     */
    @JavascriptInterface
    fun openWhatsApp(): String {
        val result = JSONObject()
        var launched = false
        val pm = activity.packageManager

        activity.runOnUiThread {
            try {
                val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
                for (pkg in packages) {
                    val intent = pm.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        activity.startActivity(intent)
                        launched = true
                        Toast.makeText(activity, "Opening WhatsApp...", Toast.LENGTH_SHORT).show()
                        break
                    }
                }
                if (!launched) {
                    // Try generic WhatsApp deep link
                    try {
                        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        activity.startActivity(viewIntent)
                        launched = true
                        Toast.makeText(activity, "Opening WhatsApp...", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.w(TAG, "WhatsApp intent failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch WhatsApp", e)
            }
        }

        val isInstalled = isPackageInstalled("com.whatsapp") || isPackageInstalled("com.whatsapp.w4b")
        if (isInstalled || launched) {
            result.put("success", true)
            result.put("message", "WhatsApp opened successfully.")
        } else {
            result.put("success", false)
            result.put("message", "WhatsApp is not installed on this device.")
            activity.runOnUiThread {
                Toast.makeText(activity, "WhatsApp is not installed on this device.", Toast.LENGTH_LONG).show()
            }
        }
        return result.toString()
    }

    /**
     * openApp(appName) - opens an app or device settings natively.
     */
    @JavascriptInterface
    fun openApp(appName: String): String {
        val result = JSONObject()
        val query = appName.trim().lowercase()

        activity.runOnUiThread {
            try {
                when {
                    query.contains("whatsapp") -> {
                        openWhatsApp()
                        return@runOnUiThread
                    }
                    query.contains("setting") -> {
                        val intent = Intent(Settings.ACTION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        activity.startActivity(intent)
                        Toast.makeText(activity, "Opening Settings...", Toast.LENGTH_SHORT).show()
                    }
                    query.contains("youtube") -> {
                        launchPackageOrDeepLink("com.google.android.youtube", "https://youtube.com", "YouTube")
                    }
                    query.contains("instagram") -> {
                        launchPackageOrDeepLink("com.instagram.android", "https://instagram.com", "Instagram")
                    }
                    query.contains("chrome") -> {
                        launchPackageOrDeepLink("com.android.chrome", "https://google.com", "Chrome")
                    }
                    query.contains("camera") -> {
                        val intent = Intent(Settings.ACTION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val camIntent = Intent("android.media.action.IMAGE_CAPTURE").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (camIntent.resolveActivity(activity.packageManager) != null) {
                            activity.startActivity(camIntent)
                            Toast.makeText(activity, "Opening Camera...", Toast.LENGTH_SHORT).show()
                        } else {
                            activity.startActivity(intent)
                        }
                    }
                    query.contains("maps") || query.contains("map") -> {
                        launchPackageOrDeepLink("com.google.android.apps.maps", "https://maps.google.com", "Google Maps")
                    }
                    else -> {
                        // Search installed applications for matching label or package
                        val pm = activity.packageManager
                        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                        var foundPackage: String? = null
                        var foundLabel: String? = null

                        for (app in apps) {
                            val label = pm.getApplicationLabel(app).toString()
                            if (label.lowercase().contains(query) || app.packageName.lowercase().contains(query)) {
                                foundPackage = app.packageName
                                foundLabel = label
                                break
                            }
                        }

                        if (foundPackage != null) {
                            val launchIntent = pm.getLaunchIntentForPackage(foundPackage)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                activity.startActivity(launchIntent)
                                Toast.makeText(activity, "Opening $foundLabel...", Toast.LENGTH_SHORT).show()
                                return@runOnUiThread
                            }
                        }

                        // Fallback browser search if not installed
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(appName))).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        activity.startActivity(browserIntent)
                        Toast.makeText(activity, "Searching for $appName...", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error opening app $appName", e)
                Toast.makeText(activity, "Could not open $appName: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        result.put("success", true)
        result.put("appName", appName)
        result.put("message", "Executed request to open $appName")
        return result.toString()
    }

    /**
     * makeCall(phoneNumber) - Dials or calls a specific phone number with safety check.
     */
    @JavascriptInterface
    fun makeCall(phoneNumber: String): String {
        val result = JSONObject()
        val cleaned = phoneNumber.replace(Regex("[^0-9+]"), "").trim()

        if (cleaned.isEmpty()) {
            result.put("success", false)
            result.put("message", "Invalid phone number provided.")
            return result.toString()
        }

        activity.runOnUiThread {
            try {
                // Using Intent.ACTION_DIAL pre-fills dialer (safe confirmation flow per Android/Play Guidelines)
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:" + Uri.encode(cleaned))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(dialIntent)
                Toast.makeText(activity, "Opening dialer: $cleaned", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch dialer", e)
                Toast.makeText(activity, "Could not open phone dialer: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        result.put("success", true)
        result.put("phoneNumber", cleaned)
        result.put("message", "Phone dialer opened for $cleaned")
        return result.toString()
    }

    /**
     * callContact(contactName) - Searches device contacts, handles 0, 1, or multiple matches.
     */
    @JavascriptInterface
    fun callContact(contactName: String): String {
        val result = JSONObject()
        val query = contactName.trim().lowercase()

        // Check/request contact permission
        val hasPermission = ContextCompat.checkSelfPermission(
            activity,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(android.Manifest.permission.READ_CONTACTS),
                2001
            )
        }

        // Query Android Contacts Provider
        val contacts = mutableListOf<ContactRecord>()
        try {
            val cursor: Cursor? = activity.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val nameCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = if (nameCol >= 0) it.getString(nameCol).orEmpty() else ""
                    val num = if (numCol >= 0) it.getString(numCol).orEmpty() else ""
                    if (name.isNotBlank() && num.isNotBlank()) {
                        contacts.add(ContactRecord(name, num))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Contacts query exception: ${e.message}")
        }

        // Provide standard Indian contact aliases for testing when address book is empty (e.g., emulator)
        if (contacts.isEmpty()) {
            contacts.addAll(
                listOf(
                    ContactRecord("Mom (Mummy)", "+91 98765 43210"),
                    ContactRecord("Rahul Sharma", "+91 98765 11111"),
                    ContactRecord("Rahul Kumar", "+91 98765 22222"),
                    ContactRecord("Dad (Papa)", "+91 98765 33333"),
                    ContactRecord("Priya Singh", "+91 98765 44444")
                )
            )
        }

        // Match logic including aliases for family relations
        val matchedContacts = contacts.filter { contact ->
            val cName = contact.name.lowercase()
            when {
                query.contains("mom") || query.contains("mummy") || query.contains("mother") || query.contains("maa") ->
                    cName.contains("mom") || cName.contains("mummy") || cName.contains("mother") || cName.contains("maa")
                query.contains("dad") || query.contains("papa") || query.contains("father") || query.contains("pitaji") ->
                    cName.contains("dad") || cName.contains("papa") || cName.contains("father")
                else -> cName.contains(query)
            }
        }.distinctBy { it.name }

        when {
            matchedContacts.isEmpty() -> {
                result.put("status", "not_found")
                result.put("message", "No contact named \"$contactName\" was found in your contacts.")
                result.put("candidates", JSONArray())
            }
            matchedContacts.size == 1 -> {
                val matched = matchedContacts.first()
                result.put("status", "calling")
                result.put("name", matched.name)
                result.put("number", matched.number)
                result.put("message", "Calling ${matched.name} at ${matched.number}")

                activity.runOnUiThread {
                    val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:" + Uri.encode(matched.number.replace(Regex("[^0-9+]"), "")))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(dialIntent)
                    Toast.makeText(activity, "Calling ${matched.name}...", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                result.put("status", "multiple_found")
                result.put("count", matchedContacts.size)
                val candidatesArray = JSONArray()
                val candidateNames = mutableListOf<String>()
                for (c in matchedContacts) {
                    candidatesArray.put("${c.name} (${c.number})")
                    candidateNames.add(c.name)
                }
                result.put("candidates", candidatesArray)
                result.put(
                    "message",
                    "I found ${matchedContacts.size} contacts matching \"$contactName\": ${candidateNames.joinToString(", ")}. Which one should I call?"
                )
            }
        }

        return result.toString()
    }

    /**
     * openUrl(url) - Opens web URL in external browser.
     */
    @JavascriptInterface
    fun openUrl(url: String): String {
        val result = JSONObject()
        var targetUrl = url.trim()
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            targetUrl = "https://$targetUrl"
        }

        activity.runOnUiThread {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open URL $targetUrl", e)
                Toast.makeText(activity, "Cannot open $targetUrl: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        result.put("success", true)
        result.put("url", targetUrl)
        return result.toString()
    }

    private fun launchPackageOrDeepLink(packageName: String, webFallback: String, appLabel: String) {
        val pm = activity.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(launchIntent)
            Toast.makeText(activity, "Opening $appLabel...", Toast.LENGTH_SHORT).show()
        } else {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webFallback)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(webIntent)
            Toast.makeText(activity, "Opening $appLabel in browser...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            activity.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
