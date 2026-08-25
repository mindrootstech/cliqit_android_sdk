# Cliqit DeepLink SDK for Android

The Cliqit DeepLink SDK provides a simple way to handle deep links, deferred deep links, and track events in your Android application.

## Features

- **Deep Link Handling**: Resolve deep links and extract metadata.
- **Deferred Deep Links**: Automatically handles deep links even if the app was not installed when the link was clicked.
- **Install Tracking**: Automatically tracks unique installations using the Google Play Install Referrer.
- **Event Tracking**: Track custom user events with metadata.
- **API Validation**: Verify your API key and app configuration.

## Installation

### 1. Add Repository

Ensure `mavenLocal()` or your hosted Maven repository is added to your `settings.gradle.kts` (or root `build.gradle.kts`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal() // Only if testing locally
    }
}
```

### 2. Add Dependency

Add the SDK to your app-level `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.cliqit:deeplink-sdk:1.0.0")
}
```

## Implementation

### 1. Initialize the SDK

Initialize the SDK in your `Application` class or the `onCreate` method of your main activity.

```kotlin
import com.cliqit.android.sdk.DeepLinkSDK

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize SDK
        DeepLinkSDK.init("YOUR_API_KEY", this)
        
        // Set up callback for deep link resolution
        DeepLinkSDK.setOnLinkCallback { data ->
            if (data != null) {
                Log.d("DeepLink", "Received data: $data")
                // Handle navigation or logic based on data
            }
        }
    }
}
```

### 2. Handle Incoming Deep Links

To capture deep links when the app is already running or opened via a link, call `handleIntent` in your Activity's `onNewIntent` or `onCreate`.

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    DeepLinkSDK.handleIntent(intent, this)
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    DeepLinkSDK.handleIntent(intent, this)
}
```

### 3. Track Custom Events

You can track user actions by calling `trackEvent`.

```kotlin
val eventData = mapOf("category" to "electronics", "price" to 299.99)
DeepLinkSDK.trackEvent("purchase", eventData, userId = "user_123")
```

### 4. Validate Configuration (Optional)

To verify that your API key and app signature are correctly configured:

```kotlin
DeepLinkSDK.sdkValidateApi(context)
```

## Requirements

- Min SDK: 28
- Internet Permission (Added automatically by SDK manifest)

## License

Copyright © 2026 Cliqit. All rights reserved.
