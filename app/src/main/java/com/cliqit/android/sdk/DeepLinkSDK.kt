package com.cliqit.android.sdk

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import androidx.core.content.edit
import kotlin.toString


object DeepLinkSDK {
    private const val TAG = "DeepLinkSDK"
    private var apiKey: String? = null
    private var initialized = false
    private var isDebuggable = false
    private var pendingUri: Uri? = null
    private val client = OkHttpClient()
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)
    // Base URL split out so we can reuse for different endpoints

    private const val PREFS_NAME = "deep_link_sdk_prefs"
    private const val PREF_SDK_INSTALLED = "sdk_installed"

    private var onLinkCallback: (( Map<String, Any>?) -> Unit)? = null
    var deviceInfo = DeviceInfo()
    /**
     * Set a callback to be notified when a deep link is detected or resolved.
     * The callback is executed on the Main thread.
     */
    fun setOnLinkCallback(callback: (Map<String, Any>?) -> Unit) {
        onLinkCallback = callback
    }

    /**
     * Initialize SDK and immediately perform unique-install check (mirrors Flutter behavior).
     * Call this from Application.onCreate or other startup code and pass application Context.
     */
    fun init(apiKey: String, context: Context) {
        if (initialized) return
        this.apiKey = apiKey
        initialized = true
        isDebuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable) Log.d(TAG, "Initialized successfully")

        // Capture any pending URI received via handleIntent before init
        val uriToHandle = pendingUri
        pendingUri = null

        // Launch the initial check (unique-install or link resolution)
        scope.launch {
            handleLink(uriToHandle, context.applicationContext)
        }
    }

    fun handleIntent(intent: Intent?, context: Context) {
        val uri = intent?.data ?: return
        if (!initialized) {
            if (isDebuggable) Log.d(TAG, "handleIntent called before init, caching URI: $uri")
            pendingUri = uri
            return
        }
        scope.launch {
            handleLink(uri, context.applicationContext)
        }
    }

    fun sdkValidateApi(context: Context) {
        // Fire-and-forget: run the suspend network call on the SDK scope
        scope.launch {
            val payload = mapOf(
                "platform" to deviceInfo.getOS(),
                "bundleId" to deviceInfo.getPackageName(context),
                "packageName" to deviceInfo.getPackageName(context),
                "androidSha256" to (deviceInfo.getAppSignature(context) ?: "")
            )

            apiPost(ApiConstants.VERIFY, payload)
        }
    }

    fun trackEvent(event: String, data: Map<String, Any>, userId: String? = null) {
        if (isDebuggable) Log.d(TAG, "[Event]: $event | Data: $data")
        val payload = mutableMapOf<String, Any>(
            "platform" to deviceInfo.getOS(),
            "type" to event,

            )
        userId?.let { payload["userId"] = it }

        // Fire-and-forget: run the suspend network call on the SDK scope
        scope.launch {
            apiPost(ApiConstants.EVENT, payload)
        }
    }

    // Single suspend function to send to a specific endpoint (e.g. "unique-install" or "events").
    // This merges previous blocking/non-blocking variants into one implementation.
    private suspend fun apiPost(endpoint: String, data: Map<String, Any?>): String? {
        val url = ApiConstants.BASE_URL + endpoint
        val jsonBody = gson.toJson(data)
        if (isDebuggable) Log.e(TAG, "API POST Request: $url | Body: $jsonBody")
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("x-api-key", "$apiKey")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (isDebuggable) Log.e(TAG, "API POST Response [${response.code}] from $endpoint: $responseBody")
                    if (response.isSuccessful) {
                        responseBody
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                if (isDebuggable) Log.e(TAG, "API POST Error for $endpoint", e)
                null
            }
        }
    }
    private suspend fun apiGet(endpoint: String, data: Map<String, Any?>?): String? {
        val uriBuilder = (ApiConstants.BASE_URL + endpoint).toUri().buildUpon()
        data?.forEach { (key, value) ->
            if (value != null) {
                uriBuilder.appendQueryParameter(key, value.toString())
            }
        }
        val url = uriBuilder.build().toString()
        Log.e(TAG, "API GET Request: $url")

        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("x-api-key", "$apiKey")
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()
                    if (isDebuggable) Log.e(TAG, "API GET Response [${response.code}] from $endpoint: $responseBody")
                    if (response.isSuccessful) {
                        responseBody
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                if (isDebuggable) Log.e(TAG, "API GET Error for $endpoint", e)
                null
            }
        }
    }


    // Public function mirroring the Flutter _checkUniqueInstall logic.
    // This will ensure a single "unique-install" event is sent once per device installation.
    suspend fun handleLink(uri: Uri?, context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isInstalled = prefs.getBoolean(PREF_SDK_INSTALLED, false)
        var responseJson: String? = "";

        val specificData = deviceInfo.getDeviceInfoMap(context)

        var isDeferred = false

        if (!isInstalled) {
            isDeferred = true
            val referrer = getInstallReferrer(context)

            referrer?.let { specificData["androidInstallReferrer"] = it }

            // send and wait for completion (suspending)
            responseJson = apiPost(ApiConstants.MATCH, specificData)

            prefs.edit { putBoolean(PREF_SDK_INSTALLED, true) }
        }else if(uri != null){
            isDeferred = false
            val deepLinkData = DeepLinkData.fromUri(uri)
            val slug = deepLinkData.path;

            responseJson = apiGet(ApiConstants.LINK+"/$slug", null)
        }else{
            withContext(Dispatchers.Main) {
                onLinkCallback?.invoke(null)
            }
            return
        }

        // Handle potential deferred deep link from server response
        responseJson?.let {
            try {
                val fullResponse = gson.fromJson(it, Map::class.java) as? Map<String, Any>
                val dataMap = (fullResponse?.get("data") as? Map<String, Any>)?.toMutableMap() ?: mutableMapOf()

                // Add top-level fields to the data map for convenience
                fullResponse?.get("status")?.let { s -> dataMap["status"] = s }
                fullResponse?.get("message")?.let { m -> dataMap["message"] = m }

                if (isDebuggable) Log.e(TAG, "After parse $dataMap")

                dataMap["isDeferred"] = isDeferred
                dataMap["source"] = if (isDeferred) "deferred" else "unknown"

                withContext(Dispatchers.Main) {
                    onLinkCallback?.invoke(dataMap)
                }
            } catch (e: Exception) {
                onLinkCallback?.invoke(
                    mapOf(
                        "error" to "Failed to parse deferred link response",
                        "details" to it
                    )
                )
                if (isDebuggable) Log.e(TAG, "Failed to parse deferred link response", e)
            }
        }
    }

    private suspend fun getInstallReferrer(context: Context): String? = suspendCancellableCoroutine { continuation ->
        val referrerClient = InstallReferrerClient.newBuilder(context).build()
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                when (responseCode) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> {
                        try {
                            val response = referrerClient.installReferrer
                            val referrerUrl = response.installReferrer
                            referrerClient.endConnection()
                            if (continuation.isActive) continuation.resume(referrerUrl)
                        } catch (e: Exception) {
                            if (isDebuggable) Log.e(TAG, "Error getting install referrer", e)
                            referrerClient.endConnection()
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                    else -> {
                        referrerClient.endConnection()
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                // No-op
            }
        })
    }
}