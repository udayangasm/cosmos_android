package com.pos.cosmos.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Url

interface ApiService {

    @POST("user/login")
    suspend fun login(@Body credentials: Map<String, String>): Response<JsonObject>

    @GET("user/profile")
    suspend fun getUserProfile(@Header("Authorization") token: String): Response<JsonObject>

    // Get bill templates from the 8080 port server
    @GET
    suspend fun getBillTemplates(
        @Url url: String,
        @Header("Authorization") token: String
    ): Response<JsonArray>

    // Dashboard endpoints
    @GET("dashboard/overview")
    suspend fun getDashboardOverview(@Header("Authorization") token: String): Response<JsonObject>

    @GET("dashboard/sales-stats")
    suspend fun getSalesStatistics(@Header("Authorization") token: String): Response<JsonArray>

    @GET("dashboard/top-selling")
    suspend fun getTopSellingItems(@Header("Authorization") token: String): Response<JsonArray>

    // Account summary endpoint (same as web app) - on bill service (port 8080)
    @GET
    suspend fun getAccountSummary(
        @Url url: String,
        @Header("Authorization") token: String
    ): Response<JsonObject>

    // Get user permissions by token
    @GET("admin/getUserPermissionsByToken")
    suspend fun getUserPermissions(@Header("Authorization") token: String): Response<JsonObject>
}
