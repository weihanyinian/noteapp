package com.example.mymind.network

import retrofit2.http.Body
import retrofit2.http.POST

interface AiApiService {
    @POST("chat/completions")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
}
