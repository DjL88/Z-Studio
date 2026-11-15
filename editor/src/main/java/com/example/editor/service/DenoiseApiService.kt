package com.example.editor.service

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class DenoiseRequest(
    val imageBase64: String? = null,
    val imageUrl: String? = null
)

data class DenoiseResponse(
    val denoisedImageUrl: String? = null,
    val error: String? = null
)

interface DenoiseApiService {
    @POST("denoiseImage")
    suspend fun denoiseImage(@Body request: DenoiseRequest): Response<DenoiseResponse>
}

object DenoiseClient {
    // Standard Firebase Cloud Functions base URL placeholder (will fall back gracefully)
    private const val BASE_URL = "https://us-central1-your-project-id.cloudfunctions.net/"

    fun createService(customBaseUrl: String? = null): DenoiseApiService {
        val finalUrl = if (!customBaseUrl.isNullOrEmpty()) {
            if (customBaseUrl.endsWith("/")) customBaseUrl else "$customBaseUrl/"
        } else {
            BASE_URL
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(finalUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(DenoiseApiService::class.java)
    }
}
