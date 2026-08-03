package com.loomytrip.mobile.data.repository

import com.loomytrip.mobile.data.model.TripActivity

interface TripRepository {
    fun initialItinerary(): List<TripActivity>
    fun moveActivity(activities: List<TripActivity>, id: String, direction: Int): List<TripActivity>
    fun deleteActivity(activities: List<TripActivity>, id: String): List<TripActivity>
    fun addActivity(
        activities: List<TripActivity>,
        day: Int,
        title: String,
        startTime: String
    ): List<TripActivity>
}

class MockTripRepository : TripRepository {
    override fun initialItinerary(): List<TripActivity> = listOf(
        TripActivity("wat-chedi-luang", "Wat Chedi Luang", "Temple", 1, "09:00", 90, "103 Prapokkloa Road", 18.7871, 98.9865),
        TripActivity("tha-phae-gate", "Tha Phae Gate", "Landmark", 1, "11:00", 60, "Moon Muang Road", 18.7877, 99.0001),
        TripActivity("nimman-road", "Nimman Road", "Food & shopping", 1, "14:00", 120, "Nimmanahaeminda Road", 18.7969, 98.9683),
        TripActivity("sunday-market", "Sunday Walking Street", "Market", 1, "18:00", 120, "Ratchadamnoen Road", 18.7884, 98.9928),
        TripActivity("wat-phra-that", "Wat Phra That Doi Suthep", "Temple", 2, "08:30", 120, "Doi Suthep Road", 18.8050, 98.9215),
        TripActivity("bhubing-palace", "Bhubing Palace", "Garden", 2, "11:30", 90, "Doi Buak Ha", 18.8102, 98.8995),
        TripActivity("chiang-mai-university", "Chiang Mai University", "Campus", 2, "14:30", 90, "239 Huay Kaew Road", 18.8020, 98.9526),
        TripActivity("elephant-nature-park", "Elephant Nature Park", "Nature", 3, "08:00", 300, "Mae Taeng District", 19.2167, 98.8583),
        TripActivity("warorot-market", "Warorot Market", "Market", 3, "17:00", 120, "Wichayanon Road", 18.7905, 99.0007)
    )

    override fun moveActivity(
        activities: List<TripActivity>,
        id: String,
        direction: Int
    ): List<TripActivity> {
        if (direction !in setOf(-1, 1)) return activities
        val currentIndex = activities.indexOfFirst { it.id == id }
        if (currentIndex < 0) return activities
        val current = activities[currentIndex]
        val sameDayIndices = activities.indices.filter { activities[it].day == current.day }
        val dayPosition = sameDayIndices.indexOf(currentIndex)
        val targetDayPosition = dayPosition + direction
        if (targetDayPosition !in sameDayIndices.indices) return activities

        val targetIndex = sameDayIndices[targetDayPosition]
        return activities.toMutableList().apply {
            val target = this[targetIndex]
            this[targetIndex] = current
            this[currentIndex] = target
        }
    }

    override fun deleteActivity(activities: List<TripActivity>, id: String): List<TripActivity> =
        activities.filterNot { it.id == id }

    override fun addActivity(
        activities: List<TripActivity>,
        day: Int,
        title: String,
        startTime: String
    ): List<TripActivity> {
        if (title.isBlank()) return activities
        val newActivity = TripActivity(
            id = "custom-${System.nanoTime()}",
            title = title.trim(),
            category = "Custom stop",
            day = day,
            startTime = startTime.ifBlank { "12:00" },
            durationMinutes = 60,
            address = "Address to be confirmed",
            latitude = 18.7883,
            longitude = 98.9853
        )
        val lastDayIndex = activities.indexOfLast { it.day == day }
        if (lastDayIndex < 0) return activities + newActivity
        return activities.toMutableList().apply { add(lastDayIndex + 1, newActivity) }
    }
}
