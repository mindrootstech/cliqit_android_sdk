package com.cliqit.android.sdk

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.view.accessibility.AccessibilityManager
import java.util.*

class DeviceInfo {


    fun getDeviceInfoMap(context: Context,installReferrer: String? = null): MutableMap<String, Any?> {
        val ctx = context
        val locale = Locale.getDefault()
        val metrics = ctx.resources.displayMetrics
        val configuration = ctx.resources.configuration

        return mutableMapOf(
            "deviceModelClass" to "android",
            "platform" to getOS(),
            "packageName" to getPackageName(context),
            "androidSha256" to (getAppSignature(context) ?: ""),
            "osVersionMajor" to Build.VERSION.RELEASE.split(".")[0],
            "osVersionMajorMinor" to Build.VERSION.RELEASE,
            "locale" to locale.toLanguageTag(),
            "timezone" to TimeZone.getDefault().id,
            "screenBucket" to getScreenBucket(metrics.densityDpi),
            "connectionType" to getConnectionType(ctx),
            "appOpenAt" to System.currentTimeMillis(),
            "appVersion" to (context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"),
            "androidInstallReferrer" to installReferrer,
            "clipboardToken" to getClipboardContent(ctx),
            "clickSessionId" to UUID.randomUUID().toString(),
            "batteryLevel" to getBatteryLevel(ctx),
            "batteryCharging" to isBatteryCharging(ctx),
            "languagesOrdered" to getLanguagesOrdered(),
            "colorScheme" to getColorScheme(configuration),
            "hourCycle" to if (DateFormat.is24HourFormat(ctx)) "h24" else "h12",
            "currency" to try { Currency.getInstance(locale).currencyCode } catch (e: Exception) { "INR" },
            "regionCode" to locale.country,
            "dynamicTypeSize" to getDynamicTypeSize(configuration.fontScale),
            "boldText" to isBoldTextEnabled(ctx),
            "reduceMotion" to isReduceMotionEnabled(ctx),
            "increaseContrast" to isIncreaseContrastEnabled(ctx),
            "hardwareConcurrency" to Runtime.getRuntime().availableProcessors(),
            "clockSkewMs" to 0,
            "devicePixelRatioBucket" to getPixelRatioBucket(metrics.density),
            "canvasHash" to "",
            "gpuRenderer" to "",
            "audioFingerprint" to ""
        )
    }

    fun getPackageName(context: Context): String? {
        return context.packageName;
    }

    fun getOS(): String = "android"

    fun getDeviceClass(): String = getOS()

    fun getOSVersion(): String = Build.VERSION.RELEASE

    fun getTimestamp(): Long = System.currentTimeMillis()

    fun getTimezone(): String = TimeZone.getDefault().id

    fun getPixelRatio(context: Context): Float = context.resources.displayMetrics.density

    fun getToken(context: Context): String? {
        return getClipboardContent(context)
    }
    fun getAppSignature(context: Context): String? {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            signatures?.firstOrNull()?.let { signature ->
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val digest = md.digest(signature.toByteArray())
                digest.joinToString("") { "%02x".format(it) }
            }
        } catch (e: Exception) {
            Log.e("TAG", "Error getting app signature", e)
            null
        }
    }

    fun getScreenWidth(context: Context): Int {
        return context.resources.displayMetrics.widthPixels
    }

    fun getScreenHeight(context: Context): Int {
        return context.resources.displayMetrics.heightPixels
    }

    private fun getScreenBucket(densityDpi: Int): String {
        return when {
            densityDpi <= 160 -> "small"
            densityDpi <= 240 -> "medium"
            densityDpi <= 320 -> "large"
            densityDpi <= 480 -> "xlarge"
            else -> "xxlarge"
        }
    }

    private fun getPixelRatioBucket(density: Float): String {
        return when {
            density <= 1.0f -> "low"
            density <= 1.5f -> "medium"
            density <= 2.0f -> "high"
            density <= 3.0f -> "xhigh"
            else -> "xxhigh"
        }
    }

    private fun getConnectionType(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return "none"
        val capabilities = cm.getNetworkCapabilities(network) ?: return "none"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "other"
        }
    }

    private fun getClipboardContent(context: Context): String? {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            if (clipboard.hasPrimaryClip()) {
                clipboard.primaryClip?.getItemAt(0)?.text?.toString()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getBatteryLevel(context: Context): Float {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level != -1 && scale != -1) level / scale.toFloat() else -1f
    }

    private fun isBatteryCharging(context: Context): Boolean {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun getLanguagesOrdered(): String {
        val list = LocaleList.getDefault()
        val languages = mutableListOf<String>()
        for (i in 0 until list.size()) {
            languages.add(list.get(i).toLanguageTag())
        }
        return languages.joinToString(",")
    }

    private fun getColorScheme(configuration: Configuration): String {
        val nightModeFlags = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return when (nightModeFlags) {
            Configuration.UI_MODE_NIGHT_YES -> "dark"
            Configuration.UI_MODE_NIGHT_NO -> "light"
            else -> "light"
        }
    }

    private fun getDynamicTypeSize(fontScale: Float): String {
        return when {
            fontScale <= 0.85f -> "S"
            fontScale <= 1.0f -> "M"
            fontScale <= 1.15f -> "L"
            fontScale <= 1.3f -> "XL"
            else -> "XXL"
        }
    }

    private fun isBoldTextEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.resources.configuration.fontWeightAdjustment > 0
        } else {
            false
        }
    }

    private fun isReduceMotionEnabled(context: Context): Boolean {
        return try {
            val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
            scale == 0f
        } catch (e: Exception) {
            false
        }
    }

    private fun isIncreaseContrastEnabled(context: Context): Boolean {
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            // Use reflection or check if the property exists to be safe against different SDK versions in this environment
            val method = am.javaClass.getMethod("isHighTextContrastEnabled")
            method.invoke(am) as Boolean
        } catch (e: Exception) {
            false
        }
    }
}
