package com.example.upaos.data.api

import com.example.upaos.data.model.CanvasTodoItem
import com.example.upaos.data.model.CanvasUser
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.util.concurrent.TimeUnit

interface CanvasApiService {

    @GET("api/v1/users/self")
    suspend fun getUserSelf(
        @Header("Authorization") authHeader: String
    ): Response<CanvasUser>

    @GET("api/v1/users/self/todo")
    suspend fun getTodoList(
        @Header("Authorization") authHeader: String
    ): Response<List<CanvasTodoItem>>
}

object CanvasApiClient {
    private const val BASE_URL = "https://upao.instructure.com/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    val apiService: CanvasApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(CanvasApiService::class.java)
    }
}
