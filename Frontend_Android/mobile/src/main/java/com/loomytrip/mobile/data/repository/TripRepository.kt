package com.loomytrip.mobile.data.repository

import com.loomytrip.mobile.data.model.TripActivity

interface TripRepository {
    fun initialItinerary(): List<TripActivity>
    fun moveActivity(activities: List<TripActivity>, id: String, direction: Int): List<TripActivity>
    fun reorderActivity(
        activities: List<TripActivity>,
        id: String,
        targetDay: Int,
        targetIndex: Int
    ): List<TripActivity>
    fun deleteActivity(activities: List<TripActivity>, id: String): List<TripActivity>
    fun deleteDay(activities: List<TripActivity>, day: Int): List<TripActivity>
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
    fun updateActivity(
        activities: List<TripActivity>,
        id: String,
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
        val current = activities.firstOrNull { it.id == id } ?: return activities
        val dayItems = activities.filter { it.day == current.day }
        val currentIndex = dayItems.indexOfFirst { it.id == id }
        val targetIndex = currentIndex + direction
        if (targetIndex !in dayItems.indices) return activities
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
        val dayCount = (activities.maxOfOrNull { it.day } ?: 1).coerceAtLeast(targetDay)
        val grouped = (1..dayCount).associateWith { day ->
            activities.filter { it.day == day && it.id != id }.toMutableList()
        }
        val target = grouped.getValue(targetDay)
        target.add(targetIndex.coerceIn(0, target.size), moving.copy(day = targetDay))
        return (1..dayCount).flatMap { grouped.getValue(it) }
    }

    override fun deleteActivity(activities: List<TripActivity>, id: String): List<TripActivity> =
        activities.filterNot { it.id == id }

    override fun deleteDay(activities: List<TripActivity>, day: Int): List<TripActivity> {
        if (day < 1) return activities
        return activities
            .filterNot { it.day == day }
            .map { activity ->
                if (activity.day > day) activity.copy(day = activity.day - 1) else activity
            }
    }

    override fun restoreActivity(
        activities: List<TripActivity>,
        activity: TripActivity,
        targetIndex: Int
    ): List<TripActivity> {
        if (activities.any { it.id == activity.id }) return activities
        val dayCount = (activities.maxOfOrNull { it.day } ?: 1).coerceAtLeast(activity.day)
        val grouped = (1..dayCount).associateWith { day ->
            activities.filter { it.day == day }.toMutableList()
        }
        val target = grouped.getValue(activity.day)
        target.add(targetIndex.coerceIn(0, target.size), activity)
        return (1..dayCount).flatMap { grouped.getValue(it) }
    }

    override fun addActivity(
        activities: List<TripActivity>,
        day: Int,
        title: String,
        startTime: String
    ): List<TripActivity> {
        if (title.isBlank()) return activities
        val requestedStart = startTime.toMinutesOrNull() ?: DEFAULT_DAY_START_MINUTES
        val latestEnd = activities
            .asSequence()
            .filter { it.day == day }
            .mapNotNull { activity ->
                activity.startTime.toMinutesOrNull()?.plus(activity.durationMinutes)
            }
            .maxOrNull()
        val automaticStart = latestEnd?.plus(NEW_STOP_BUFFER_MINUTES) ?: DEFAULT_DAY_START_MINUTES
        val scheduledStart = maxOf(requestedStart, automaticStart).coerceAtMost(LATEST_START_MINUTES)
        val newActivity = TripActivity(
            id = "custom-${System.nanoTime()}",
            title = title.trim(),
            category = "Custom stop",
            day = day,
            startTime = scheduledStart.toClockTime(),
            durationMinutes = NEW_ACTIVITY_DURATION_MINUTES,
            address = "Address to be confirmed",
            latitude = 18.7883,
            longitude = 98.9853
        )
        val lastDayIndex = activities.indexOfLast { it.day == day }
        if (lastDayIndex < 0) return activities + newActivity
        return activities.toMutableList().apply { add(lastDayIndex + 1, newActivity) }
    }

    override fun updateActivity(
        activities: List<TripActivity>,
        id: String,
        startTime: String
    ): List<TripActivity> = activities.map { activity ->
        if (activity.id == id) activity.copy(startTime = startTime) else activity
    }
}

private const val DEFAULT_DAY_START_MINUTES = 9 * 60
private const val NEW_ACTIVITY_DURATION_MINUTES = 60
private const val NEW_STOP_BUFFER_MINUTES = 15
private const val LATEST_START_MINUTES = 23 * 60 + 45

private fun String.toMinutesOrNull(): Int? {
    val parts = split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

private fun Int.toClockTime(): String = "%02d:%02d".format(this / 60, this % 60)
