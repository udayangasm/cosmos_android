package com.pos.cosmos.api

import android.util.Log
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object RetrofitClient {
    private val BASE_URL: String get() = ApiConfig.RETROFIT_BASE_URL
    private var isDebugBuild = true  // Set this to false in release builds

    // Create a trust manager that doesn't validate certificate chains
    private fun getUnsafeTrustManager(): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    }

    // Create SSL context that accepts all certificates
    private fun getUnsafeSSLContext(): SSLContext {
        val sslContext = SSLContext.getInstance("SSL")
        val trustManager = getUnsafeTrustManager()
        sslContext.init(null, arrayOf<TrustManager>(trustManager), java.security.SecureRandom())
        return sslContext
    }

    private val httpClient = OkHttpClient.Builder().apply {
        // For development: Accept self-signed certificates
        val sslContext = getUnsafeSSLContext()
        sslSocketFactory(sslContext.socketFactory, getUnsafeTrustManager())
        hostnameVerifier { _, _ -> true }

        // Increase timeout to 30 seconds for slow servers
        connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)

        // Add interceptor to include Celonis-Auth header for all requests
        addInterceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .header("Celonis-Auth", "totally_secret")
                .build()
            chain.proceed(newRequest)
        }

        // Only enable HTTP logging in debug builds to prevent credential exposure in release
        if (isDebugBuild) {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d("OkHttp", message)
            }
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
            addInterceptor(loggingInterceptor)
        }
    }.build()

    private val gson = GsonBuilder()
        .setLenient()
        .create()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ApiService::class.java)
    }
}
