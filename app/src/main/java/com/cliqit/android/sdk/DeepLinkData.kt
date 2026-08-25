package com.cliqit.android.sdk

import android.net.Uri

data class DeepLinkData(
    val url: String,
    val subPath: String,
    val path: String,
    val parameters: Map<String, String>
) {
    companion object {
        fun fromUri(uri: Uri): DeepLinkData {
            val parameters = mutableMapOf<String, String>()
            uri.queryParameterNames.forEach { name ->
                uri.getQueryParameter(name)?.let { value ->
                    parameters[name] = value
                }
            }
            return DeepLinkData(
                url = uri.toString(),
                subPath = uri.path ?: "",
                path = uri.lastPathSegment ?: "",
                parameters = parameters
            )
        }
    }
}
