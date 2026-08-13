package com.loomytrip.mobile.data.repository

import com.loomytrip.mobile.data.network.CrowdHintDto
import com.loomytrip.mobile.data.network.MapConfigDto
import com.loomytrip.mobile.data.network.NearbyRecommendationDto
import com.loomytrip.mobile.data.network.TripRouteDto
import com.loomytrip.mobile.data.network.mapApi
import com.loomytrip.mobile.data.network.tripApi

object MapRepository {
    suspend fun getConfig(): MapConfigDto = mapApi.getConfig()

    suspend fun getRoute(tripId: Long, day: Int): TripRouteDto =
        tripApi.getRoute(tripId, day)

    suspend fun getNearby(latitude: Double, longitude: Double): List<NearbyRecommendationDto> =
        mapApi.getNearby(latitude, longitude).items

    suspend fun getCrowdHint(date: String?): CrowdHintDto =
        mapApi.getCrowdHint(date)
}
