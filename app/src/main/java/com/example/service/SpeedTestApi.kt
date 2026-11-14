package com.example.service

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.POST
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Retrofit 2 API interface for measuring real HTTP latency, streaming download throughput,
 * and upload bandwidth before connecting to a VPN server.
 */
interface SpeedTestApi {

    @HEAD
    suspend fun probeLatency(@Url url: String): Response<Void>

    @Streaming
    @GET
    suspend fun downloadChunk(@Url url: String): ResponseBody

    @POST
    suspend fun uploadChunk(
        @Url url: String,
        @Body payload: RequestBody
    ): Response<ResponseBody>
}
