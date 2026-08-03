package com.loomytrip.mobile.data.repository

import com.loomytrip.mobile.data.model.TripActivity
import com.loomytrip.mobile.data.model.TripPlan

interface TripRepository {
    fun savedTrips(): List<TripPlan>
    fun initialItinerary(): List<TripActivity>
    fun moveActivity(activities: List<TripActivity>, id: String, direction: Int): List<TripActivity>
    fun reorderActivity(
        activities: List<TripActivity>,
        id: String,
        targetDay: Int,
        targetIndex: Int
    ): List<TripActivity>
    fun deleteActivity(activities: List<TripActivity>, id: String): List<TripActivity>
    fun restoreActivity(
        activities: List<TripActivity>,
        activity: TripActivity,
        targetIndex: Int
    ): List<TripActivity>
    fun addActivity(
        activities: List<TripActivity>,
        day: Int,
        title: String,
        startTime: String
    ): List<TripActivity>
}

class MockTripRepository : TripRepository {
    private val chiangMaiActivities = listOf(
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

    override fun savedTrips(): List<TripPlan> = listOf(
        TripPlan(
            id = "chiang-mai",
            title = "Chiang Mai",
            dateLabel = "3-day city break",
            totalDays = 3,
            activities = chiangMaiActivities
        ),
        TripPlan(
            id = "bangkok-weekend",
            title = "Bangkok Weekend",
            dateLabel = "2 days · Saved trip",
            totalDays = 2,
            activities = listOf(
                TripActivity("grand-palace", "The Grand Palace", "Landmark", 1, "09:00", 120, "Na Phra Lan Road", 13.7500, 100.4914),
                TripActivity("wat-pho", "Wat Pho", "Temple", 1, "11:30", 90, "Sanam Chai Road", 13.7465, 100.4930),
                TripActivity("yaowarat", "Yaowarat Road", "Food", 1, "18:00", 120, "Chinatown", 13.7400, 100.5100),
                TripActivity("jim-thompson", "Jim Thompson House", "Museum", 2, "10:00", 90, "Soi Kasemsan 2", 13.7492, 100.5283),
                TripActivity("chatuchak", "Chatuchak Market", "Market", 2, "14:00", 180, "Kamphaeng Phet 2 Road", 13.7999, 100.5500)
            )
        ),
        TripPlan(
            id = "phuket-coast",
            title = "Phuket Coast",
            dateLabel = "2 days · Saved trip",
            totalDays = 2,
            activities = listOf(
                TripActivity("old-phuket-town", "Old Phuket Town", "Neighbourhood", 1, "09:30", 120, "Thalang Road", 7.8840, 98.3890),
                TripActivity("khao-rang", "Khao Rang Viewpoint", "Viewpoint", 1, "13:30", 75, "Khao Rang Hill", 7.8970, 98.3807),
                TripActivity("kata-beach", "Kata Beach", "Beach", 2, "10:00", 180, "Karon District", 7.8207, 98.2983),
                TripActivity("promthep-cape", "Promthep Cape", "Viewpoint", 2, "17:00", 90, "Rawai District", 7.7593, 98.3037)
            )
        )
    )

    override fun initialItinerary(): List<TripActivity> = chiangMaiActivities

    override fun moveActivity(
        activities: List<TripActivity>,
        id: String,
        direction: Int
    ): List<TripActivity> {
        if (direction !in setOf(-1, 1)) return activities
        val current = activities.firstOrNull { it.id == id } ?: return activities
        val dayActivities = activities.filter { it.day == current.day }
        val currentIndex = dayActivities.indexOfFirst { it.id == id }
        val targetIndex = currentIndex + direction
        if (targetIndex !in dayActivities.indices) return activities
        return reorderActivity(activities, id, current.day, targetIndex)
    }

    override fun reorderActivity(
        activities: List<TripActivity>,
        id: String,
        targetDay: Int,
        targetIndex: Int
    ): List<TripActivity> {
        if (targetDay < 1) return activities
        val moving = activities.firstOrNull { it.id == id } ?: return activities
        val days = (activities.maxOfOrNull { it.day } ?: 1).coerceAtLeast(targetDay)
        val grouped = (1..days).associateWith { day ->
            activities.filter { it.day == day && it.id != id }.toMutableList()
        }
        val target = grouped.getValue(targetDay)
        target.add(targetIndex.coerceIn(0, target.size), moving.copy(day = targetDay))
        return (1..days).flatMap { grouped.getValue(it) }
    }

    override fun deleteActivity(activities: List<TripActivity>, id: String): List<TripActivity> =
        activities.filterNot { it.id == id }

    override fun restoreActivity(
        activities: List<TripActivity>,
        activity: TripActivity,
        targetIndex: Int
    ): List<TripActivity> {
        if (activities.any { it.id == activity.id }) return activities
        val days = (activities.maxOfOrNull { it.day } ?: 1).coerceAtLeast(activity.day)
        val grouped = (1..days).associateWith { day ->
            activities.filter { it.day == day }.toMutableList()
        }
        val target = grouped.getValue(activity.day)
        target.add(targetIndex.coerceIn(0, target.size), activity)
        return (1..days).flatMap { grouped.getValue(it) }
    }

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
