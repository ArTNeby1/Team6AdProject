package com.loomytrip.mobile.data.repository

import android.content.Context
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject

data class LocalReview(
    val id: String,
    val author: String,
    val rating: Int,
    val content: String,
    val date: String,
    val isUserReview: Boolean = false
)

data class LocalDestination(
    val id: String,
    val city: String,
    val name: String,
    val category: String,
    val imageUrl: String,
    val description: String,
    val address: String,
    val openingHours: String,
    val price: String,
    val recommendedDuration: String,
    val tags: List<String>,
    val sampleReviews: List<LocalReview>
)

object LocalExploreRepository {
    private const val PREFS_NAME = "local_destination_reviews"

    private val destinations = listOf(
        LocalDestination(
            id = "chiang-mai-wat-chedi-luang",
            city = "Chiang Mai",
            name = "Wat Chedi Luang",
            category = "Temple",
            imageUrl = "https://images.unsplash.com/photo-1528181304800-259b08848526?w=1200&h=700&fit=crop",
            description = "A historic temple complex in the old city, known for its large ruined chedi and quiet courtyards.",
            address = "103 Prapokkloa Road, Chiang Mai",
            openingHours = "08:00 – 17:00",
            price = "40 THB",
            recommendedDuration = "1–2 hours",
            tags = listOf("Old City", "Culture", "Architecture"),
            sampleReviews = listOf(
                sampleReview("cm-wcl-1", "Mina", 5, "Peaceful in the morning and easy to include in an old-city walking route."),
                sampleReview("cm-wcl-2", "Daniel", 4, "The chedi is impressive. Bring water because there is little shade at noon.")
            )
        ),
        LocalDestination(
            id = "chiang-mai-tha-phae-gate",
            city = "Chiang Mai",
            name = "Tha Phae Gate",
            category = "Landmark",
            imageUrl = "https://d2e5ushqwiltxm.cloudfront.net/wp-content/uploads/sites/286/2023/01/17080923/Tha-Phae-City-Gate-2400-%C3%97-1600px.png",
            description = "The best-known gate of Chiang Mai's old city and a convenient starting point for markets and walking routes.",
            address = "Tha Phae Road, Chiang Mai",
            openingHours = "Open all day",
            price = "Free",
            recommendedDuration = "30–60 minutes",
            tags = listOf("Landmark", "Old City", "Photography"),
            sampleReviews = listOf(
                sampleReview("cm-tpg-1", "Arun", 4, "A useful meeting point. It becomes much livelier near the Sunday market."),
                sampleReview("cm-tpg-2", "Sophie", 4, "Nice for photos early in the day before the square gets busy.")
            )
        ),
        LocalDestination(
            id = "chiang-mai-sunday-market",
            city = "Chiang Mai",
            name = "Sunday Night Market",
            category = "Market",
            imageUrl = "https://www.thailandtravel.or.jp/wp-content/uploads/2017/08/0314_159_DSC5742-808x539.jpg",
            description = "A long Sunday evening market with northern Thai food, crafts, music and busy pedestrian streets.",
            address = "Ratchadamnoen Road, Chiang Mai",
            openingHours = "Sunday 17:00 – 23:00",
            price = "Free entry",
            recommendedDuration = "2–3 hours",
            tags = listOf("Food", "Shopping", "Night market"),
            sampleReviews = listOf(
                sampleReview("cm-snm-1", "James", 5, "Lots of food choices and small handmade gifts. Arrive before seven."),
                sampleReview("cm-snm-2", "Nok", 4, "Very crowded later in the evening, but the atmosphere is worth it.")
            )
        ),
        LocalDestination(
            id = "kyoto-fushimi-inari",
            city = "Kyoto",
            name = "Fushimi Inari Taisha",
            category = "Shrine",
            imageUrl = "https://images.unsplash.com/photo-1478436127897-769e1b3f0f36?w=1200&h=700&fit=crop",
            description = "A hillside Shinto shrine famous for thousands of vermilion torii gates and forest walking paths.",
            address = "68 Fukakusa Yabunouchicho, Kyoto",
            openingHours = "Open all day",
            price = "Free",
            recommendedDuration = "2–3 hours",
            tags = listOf("Shrine", "Walking", "Photography"),
            sampleReviews = listOf(
                sampleReview("ky-fi-1", "Aya", 5, "Start early if you want a quieter walk through the lower gates."),
                sampleReview("ky-fi-2", "Marco", 5, "The route continues much farther uphill than it first appears. Wear good shoes.")
            )
        ),
        LocalDestination(
            id = "kyoto-kiyomizudera",
            city = "Kyoto",
            name = "Kiyomizu-dera",
            category = "Temple",
            imageUrl = "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?w=1200&h=700&fit=crop",
            description = "A celebrated Buddhist temple with a wooden stage overlooking eastern Kyoto and seasonal scenery.",
            address = "1-294 Kiyomizu, Higashiyama Ward, Kyoto",
            openingHours = "06:00 – 18:00",
            price = "500 JPY",
            recommendedDuration = "1.5–2 hours",
            tags = listOf("Temple", "Viewpoint", "Historic district"),
            sampleReviews = listOf(
                sampleReview("ky-km-1", "Hana", 5, "The morning view is beautiful and the nearby streets are easy to explore afterwards."),
                sampleReview("ky-km-2", "Leo", 4, "Busy but well organised. Allow extra time for the walk up through Higashiyama.")
            )
        ),
        LocalDestination(
            id = "kyoto-arashiyama",
            city = "Kyoto",
            name = "Arashiyama Bamboo Grove",
            category = "Nature",
            imageUrl = "https://images.unsplash.com/photo-1528360983277-13d401cdc186?w=1200&h=700&fit=crop",
            description = "A short atmospheric path through tall bamboo, close to temples, gardens and the Katsura River.",
            address = "Sagaogurayama Tabuchiyamacho, Kyoto",
            openingHours = "Open all day",
            price = "Free",
            recommendedDuration = "1–2 hours",
            tags = listOf("Nature", "Walking", "Scenic"),
            sampleReviews = listOf(
                sampleReview("ky-ab-1", "Emma", 4, "The grove itself is short, so combine it with Tenryu-ji and the river area."),
                sampleReview("ky-ab-2", "Ken", 4, "Very calm around sunrise and extremely busy by late morning.")
            )
        ),
        LocalDestination(
            id = "bali-tanah-lot",
            city = "Bali",
            name = "Tanah Lot",
            category = "Temple",
            imageUrl = "https://images.unsplash.com/photo-1537996194471-e657df975ab4?w=1200&h=700&fit=crop",
            description = "A sea temple on a rocky outcrop, best known for its coastal setting and sunset views.",
            address = "Beraban, Kediri, Tabanan Regency, Bali",
            openingHours = "06:00 – 19:00",
            price = "75,000 IDR",
            recommendedDuration = "1.5–2 hours",
            tags = listOf("Temple", "Coast", "Sunset"),
            sampleReviews = listOf(
                sampleReview("ba-tl-1", "Rina", 5, "A dramatic setting at sunset, though traffic can be slow on the way back."),
                sampleReview("ba-tl-2", "Oliver", 4, "The coastal walk was better than expected. Check the tide before visiting.")
            )
        ),
        LocalDestination(
            id = "bali-tegalalang",
            city = "Bali",
            name = "Tegallalang Rice Terrace",
            category = "Landscape",
            imageUrl = "https://images.unsplash.com/photo-1539367628448-4bc5c9d171c8?w=1200&h=700&fit=crop",
            description = "Layered rice terraces north of Ubud with short walking paths, viewpoints and small cafés.",
            address = "Jalan Raya Tegallalang, Gianyar, Bali",
            openingHours = "07:00 – 18:00",
            price = "Around 25,000 IDR",
            recommendedDuration = "1–2 hours",
            tags = listOf("Nature", "Walking", "Ubud"),
            sampleReviews = listOf(
                sampleReview("ba-tr-1", "Mei", 4, "Go early for cooler weather and softer light across the terraces."),
                sampleReview("ba-tr-2", "Sam", 4, "The paths can be slippery after rain, but the landscape is lovely.")
            )
        ),
        LocalDestination(
            id = "bali-uluwatu",
            city = "Bali",
            name = "Uluwatu Temple",
            category = "Temple",
            imageUrl = "https://images.unsplash.com/photo-1533669955142-6a73332af4db?w=1200&h=700&fit=crop",
            description = "A cliff-top temple overlooking the Indian Ocean, with a popular evening Kecak performance nearby.",
            address = "Pecatu, South Kuta, Badung Regency, Bali",
            openingHours = "07:00 – 19:00",
            price = "50,000 IDR",
            recommendedDuration = "2–3 hours",
            tags = listOf("Temple", "Cliff", "Culture"),
            sampleReviews = listOf(
                sampleReview("ba-ut-1", "Aisha", 5, "The cliff path and sunset are excellent. Keep loose items away from the monkeys."),
                sampleReview("ba-ut-2", "Ben", 4, "Worth staying for the performance, but book the evening slot early.")
            )
        )
    )

    fun destinationsForCity(city: String): List<LocalDestination> =
        destinations.filter { it.city.equals(city, ignoreCase = true) }

    fun destination(id: String): LocalDestination? = destinations.firstOrNull { it.id == id }

    fun reviewsFor(context: Context, destination: LocalDestination): List<LocalReview> =
        userReviews(context, destination.id) + destination.sampleReviews

    fun addReview(context: Context, destinationId: String, rating: Int, content: String): LocalReview {
        val review = LocalReview(
            id = "local-${System.currentTimeMillis()}",
            author = "You",
            rating = rating.coerceIn(1, 5),
            content = content.trim(),
            date = LocalDate.now().toString(),
            isUserReview = true
        )
        val reviews = userReviews(context, destinationId).toMutableList().apply { add(0, review) }
        val json = JSONArray()
        reviews.forEach { item ->
            json.put(
                JSONObject()
                    .put("id", item.id)
                    .put("author", item.author)
                    .put("rating", item.rating)
                    .put("content", item.content)
                    .put("date", item.date)
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(destinationId, json.toString())
            .apply()
        return review
    }

    private fun userReviews(context: Context, destinationId: String): List<LocalReview> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(destinationId, null)
            ?: return emptyList()
        return runCatching {
            val json = JSONArray(raw)
            (0 until json.length()).map { index ->
                val item = json.getJSONObject(index)
                LocalReview(
                    id = item.getString("id"),
                    author = item.optString("author", "You"),
                    rating = item.optInt("rating", 5).coerceIn(1, 5),
                    content = item.getString("content"),
                    date = item.optString("date", "Today"),
                    isUserReview = true
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun sampleReview(id: String, author: String, rating: Int, content: String) =
        LocalReview(id, author, rating, content, "Sample", isUserReview = false)
}
