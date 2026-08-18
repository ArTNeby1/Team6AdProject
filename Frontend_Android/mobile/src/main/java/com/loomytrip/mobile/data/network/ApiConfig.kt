package com.loomytrip.mobile.data.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.loomytrip.mobile.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
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
import retrofit2.http.Query

object ApiConfig {
    val baseUrl: String = "${BuildConfig.BACKEND_BASE_URL.trimEnd('/')}/api/v1/"

    fun publicTripUrl(shareToken: String): String = "${baseUrl}public/trips/$shareToken"
}

private val Context.sessionDataStore by preferencesDataStore(name = "loomytrip_session")

object TokenStore {
    private val tokenKey = stringPreferencesKey("access_token")
    private val emailKey = stringPreferencesKey("email")

    private var dataStore: DataStore<Preferences>? = null

    @Volatile
    var token: String? = null
        private set

    @Volatile
    var email: String? = null
        private set

    suspend fun initialize(context: Context) {
        if (dataStore != null) return
        val store = context.applicationContext.sessionDataStore
        dataStore = store
        val saved = store.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .first()
        token = saved[tokenKey]
        email = saved[emailKey]
    }

    suspend fun save(accessToken: String, signedInEmail: String) {
        val store = checkNotNull(dataStore) { "TokenStore must be initialized before login." }
        token = accessToken
        email = signedInEmail
        store.edit { preferences ->
            preferences[tokenKey] = accessToken
            preferences[emailKey] = signedInEmail
        }
    }

    fun hasSession(): Boolean = !token.isNullOrBlank() && !email.isNullOrBlank()

    suspend fun clear() {
        token = null
        email = null
        dataStore?.edit { it.clear() }
    }
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
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
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

    @GET("trips/{id}")
    suspend fun getTrip(@Path("id") tripId: Long): TripDto

    @DELETE("trips/{id}")
    suspend fun deleteTrip(@Path("id") tripId: Long)

    @PUT("trips/{id}")
    suspend fun updateTrip(@Path("id") tripId: Long, @Body request: UpdateTripRequest): TripDto

    @POST("trips/{id}/schedules")
    suspend fun addSchedules(@Path("id") tripId: Long, @Body request: AddSchedulesRequest): TripDto

    @PUT("trips/{id}/schedules/bulk")
    suspend fun bulkUpdateSchedules(@Path("id") tripId: Long, @Body request: BulkUpdateSchedulesRequest): TripDto

    @DELETE("trips/{id}/schedules/{scheduleId}")
    suspend fun deleteSchedule(@Path("id") tripId: Long, @Path("scheduleId") scheduleId: Long): TripDto

    @GET("trips/{id}/route")
    suspend fun getRoute(@Path("id") tripId: Long, @Query("day") day: Int): TripRouteDto

    @POST("trips/{id}/generate")
    suspend fun generateItinerary(@Path("id") tripId: Long): GenerateItineraryResponseDto

    @POST("trips/{id}/share")
    suspend fun shareTrip(@Path("id") tripId: Long): ShareTripResponseDto

    @DELETE("trips/{id}/share")
    suspend fun unshareTrip(@Path("id") tripId: Long): ShareTripResponseDto
}

interface UserApi {
    @GET("users/me")
    suspend fun getMyProfile(): UserProfileDto

    @PUT("users/me")
    suspend fun updateMyProfile(@Body request: UpdateUserProfileRequest): UserProfileDto
}

interface MapApi {
    @GET("map/config")
    suspend fun getConfig(): MapConfigDto

    @GET("map/nearby")
    suspend fun getNearby(
        @Query("lat") latitude: Double,
        @Query("lng") longitude: Double,
        @Query("topN") topN: Int = 5
    ): RecommendationResponseDto

    @GET("map/crowd")
    suspend fun getCrowdHint(@Query("date") date: String? = null): CrowdHintDto
}

interface PlanningApi {
    @GET("planning-sessions")
    suspend fun getSessions(): List<PlanningSessionSummaryDto>

    @POST("planning-sessions")
    suspend fun createSession(@Body request: CreatePlanningSessionRequest): PlanningSessionDetailDto

    @GET("planning-sessions/{id}")
    suspend fun getSession(@Path("id") sessionId: Long): PlanningSessionDetailDto

    @POST("planning-sessions/{id}/messages")
    suspend fun addMessage(@Path("id") sessionId: Long, @Body request: CreateChatMessageRequest)

    @POST("planning-sessions/{id}/refine")
    suspend fun refine(@Path("id") sessionId: Long): PlanningSessionDetailDto

    @POST("planning-sessions/{id}/validate-places")
    suspend fun validatePlaces(@Path("id") sessionId: Long): PlanningSessionDetailDto

    @PUT("planning-sessions/draft-places/{placeId}")
    suspend fun updateDraftPlace(@Path("placeId") placeId: Long, @Body request: UpdateDraftPlaceRequest)

    @DELETE("planning-sessions/draft-places/{placeId}")
    suspend fun deleteDraftPlace(@Path("placeId") placeId: Long)

    @POST("planning-sessions/{id}/confirm")
    suspend fun confirm(
        @Path("id") sessionId: Long,
        @Body request: ConfirmSessionRequest
    ): ConfirmSessionResponseDto
}

val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
val tripApi: TripApi by lazy { retrofit.create(TripApi::class.java) }
val planningApi: PlanningApi by lazy { retrofit.create(PlanningApi::class.java) }
val userApi: UserApi by lazy { retrofit.create(UserApi::class.java) }
val mapApi: MapApi by lazy { retrofit.create(MapApi::class.java) }
