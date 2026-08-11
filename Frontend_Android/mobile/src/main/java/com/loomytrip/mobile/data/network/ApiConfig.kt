package com.loomytrip.mobile.data.network

import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

/**
 * Dev machine's LAN IP, reachable from a physical phone on the same Wi-Fi
 * (localhost/10.0.2.2 only work for the Android emulator, not a real device).
 * Update this if the machine's IP changes, and keep it in sync with
 * network_security_config.xml's cleartext allowlist.
 */
object ApiConfig {
    var baseUrl: String = "http://192.168.0.14:8091/api/v1/"
}

object TokenStore {
    var token: String? = null
}

private val authInterceptor = Interceptor { chain ->
    val request = chain.request().newBuilder().apply {
        TokenStore.token?.let { addHeader("Authorization", "Bearer $it") }
    }.build()
    chain.proceed(request)
}

private val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        // AI planning calls (create session / refine) go through Bedrock and can take a while.
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()
}

private val retrofit: Retrofit by lazy {
    Retrofit.Builder()
        .baseUrl(ApiConfig.baseUrl)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}

interface AuthApi {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponseDto

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponseDto
}

interface TripApi {
    @GET("trips")
    suspend fun getTrips(): List<TripDto>

    @POST("trips/{id}/schedules")
    suspend fun addSchedules(@Path("id") tripId: Long, @Body request: AddSchedulesRequest): TripDto

    @PUT("trips/{id}/schedules/bulk")
    suspend fun bulkUpdateSchedules(@Path("id") tripId: Long, @Body request: BulkUpdateSchedulesRequest): TripDto

    @DELETE("trips/{id}/schedules/{scheduleId}")
    suspend fun deleteSchedule(@Path("id") tripId: Long, @Path("scheduleId") scheduleId: Long)
}

interface PlanningApi {
    @POST("planning-sessions")
    suspend fun createSession(@Body request: CreatePlanningSessionRequest): PlanningSessionDetailDto

    @GET("planning-sessions/{id}")
    suspend fun getSession(@Path("id") sessionId: Long): PlanningSessionDetailDto

    @POST("planning-sessions/{id}/messages")
    suspend fun addMessage(@Path("id") sessionId: Long, @Body request: CreateChatMessageRequest)

    @POST("planning-sessions/{id}/refine")
    suspend fun refine(@Path("id") sessionId: Long): PlanningSessionDetailDto

    @PUT("planning-sessions/draft-places/{placeId}")
    suspend fun updateDraftPlace(@Path("placeId") placeId: Long, @Body request: UpdateDraftPlaceRequest)

    @DELETE("planning-sessions/draft-places/{placeId}")
    suspend fun deleteDraftPlace(@Path("placeId") placeId: Long)

    @POST("planning-sessions/{id}/confirm")
    suspend fun confirm(@Path("id") sessionId: Long): ConfirmSessionResponseDto
}

val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
val tripApi: TripApi by lazy { retrofit.create(TripApi::class.java) }
val planningApi: PlanningApi by lazy { retrofit.create(PlanningApi::class.java) }
