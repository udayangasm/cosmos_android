package com.pos.cosmos.api

object ApiConfig {
    // ============================================================
    // TOGGLE THIS TO SWITCH BETWEEN LOCAL AND PRODUCTION
    // ============================================================
    private const val USE_LOCAL = false

    // Local development server (127.0.0.1 works over USB via `adb reverse`)
    private const val LOCAL_HOST = "127.0.0.1"
    private const val LOCAL_BASE_8080 = "http://$LOCAL_HOST:8080"
    private const val LOCAL_BASE_8090 = "http://$LOCAL_HOST:8090"

    // Production server
    private const val PROD_BASE_8080 = "https://cosmospos.com:8080"
    private const val PROD_BASE_8090 = "https://cosmospos.com:8090"

    val BASE_URL_8080: String get() = if (USE_LOCAL) LOCAL_BASE_8080 else PROD_BASE_8080
    val BASE_URL_8090: String get() = if (USE_LOCAL) LOCAL_BASE_8090 else PROD_BASE_8090

    // For Retrofit base URL (port 8090 with trailing slash)
    val RETROFIT_BASE_URL: String get() = "${BASE_URL_8090}/"
}
