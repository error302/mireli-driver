package com.mireli.driver.domain

/**
 * Built-in destination catalog.
 *
 * The places provider answers text search by matching place *titles*, so a driver in
 * Mombasa who types "SGR" only ever gets "SGR Nairobi Terminus" — Mombasa's own station
 * is titled "Mombasa Terminus" and can never match. These well-known Kenyan destinations
 * carry the words drivers actually type (sgr, ferry, airport, beach, hospital) and are
 * merged ahead of provider results, nearest first. The catalog also keeps common
 * destinations searchable when the provider is unreachable.
 */
internal data class KnownPlace(
    val id: String,
    val name: String,
    val detail: String,
    val point: GeoPoint,
    val aliases: List<String>,
)

object KnownPlaces {
    private fun place(
        id: String,
        name: String,
        detail: String,
        latitude: Double,
        longitude: Double,
        vararg aliases: String,
    ) = KnownPlace(id, name, detail, GeoPoint(latitude, longitude), aliases.toList())

    private val places = listOf(
        // Mombasa and the coast — the pilot operating area.
        place("known-mombasa-terminus", "SGR Mombasa Terminus", "Miritini, Mombasa", -4.0214, 39.5802,
            "sgr", "terminus", "train station", "station", "miritini", "mombasa terminus"),
        place("known-moi-airport", "Moi International Airport", "Mombasa", -4.0348, 39.5942,
            "airport", "moi airport", "mba"),
        place("known-likoni-ferry", "Likoni Ferry", "Likoni Channel, Mombasa", -4.0795, 39.6652,
            "ferry", "likoni ferry terminal"),
        place("known-mtongwe-ferry", "Mtongwe Ferry", "Mtongwe, Mombasa", -4.0894, 39.6342,
            "ferry", "mtongwe ferry terminal"),
        place("known-mombasa-cbd", "Mombasa CBD", "Mwembe Tayari, Mombasa", -4.0531, 39.6672,
            "town", "city centre", "mwembe tayari", "mombasa town"),
        place("known-fort-jesus", "Fort Jesus", "Old Town, Mombasa", -4.0626, 39.6797,
            "fort jesus", "old town"),
        place("known-nyali-bridge", "Nyali Bridge", "Nyali, Mombasa", -4.0436, 39.6711,
            "nyali bridge", "bridge"),
        place("known-nyali", "Nyali", "Nyali, Mombasa", -4.0169, 39.7167, "nyali"),
        place("known-bamburi", "Bamburi", "Bamburi, Mombasa", -3.9939, 39.7231, "bamburi"),
        place("known-kenyatta-beach", "Kenyatta Public Beach", "Bamburi, Mombasa", -3.9833, 39.7333,
            "beach", "public beach", "pirates beach"),
        place("known-shanzu", "Shanzu", "Shanzu, Mombasa", -3.9722, 39.7411, "shanzu", "beach"),
        place("known-mtwapa", "Mtwapa", "Mtwapa, Kilifi", -3.9531, 39.7458, "mtwapa"),
        place("known-changamwe", "Changamwe", "Changamwe, Mombasa", -4.0167, 39.6333, "changamwe"),
        place("known-port-reitz", "Port Reitz", "Changamwe, Mombasa", -4.0469, 39.5933, "port reitz"),
        place("known-coast-general", "Coast General Hospital", "Mombasa", -4.0497, 39.6747,
            "hospital", "coast general", "referral hospital"),
        place("known-aga-khan-mombasa", "Aga Khan Hospital", "Kizingo, Mombasa", -4.0597, 39.6692,
            "hospital", "aga khan"),
        place("known-mombasa-hospital", "Mombasa Hospital", "Mombasa", -4.0569, 39.6764, "hospital"),
        place("known-likoni", "Likoni", "Likoni, Mombasa", -4.0861, 39.6489, "likoni town"),
        place("known-dongo-kundu", "Dongo Kundu Bypass", "Miritini, Mombasa", -4.0322, 39.5694,
            "bypass", "dongo kundu"),
        place("known-vipingo", "Vipingo", "Vipingo, Kilifi", -3.8167, 39.8333, "vipingo"),
        place("known-kilifi", "Kilifi", "Kilifi Town", -3.6333, 39.8500, "kilifi"),
        place("known-watamu", "Watamu", "Watamu, Kilifi", -3.3500, 40.0167, "watamu", "beach"),
        place("known-malindi", "Malindi", "Malindi Town", -3.2175, 40.1191, "malindi"),
        place("known-malindi-airport", "Malindi Airport", "Malindi", -3.2294, 40.1017,
            "airport", "malindi airport"),
        place("known-diani", "Diani Beach", "Diani, Kwale", -4.2797, 39.5781,
            "diani", "beach", "ukunda"),
        place("known-ukunda", "Ukunda", "Ukunda, Kwale", -4.2877, 39.5653, "ukunda", "diani"),
        place("known-ukunda-airstrip", "Ukunda Airstrip", "Ukunda, Kwale", -4.2933, 39.5711,
            "airport", "airstrip", "diani airport", "ukunda airport"),
        place("known-lamu", "Lamu", "Lamu Town", -2.2717, 40.9020, "lamu"),
        place("known-msambweni", "Msambweni", "Msambweni, Kwale", -4.4667, 39.4833, "msambweni"),
        place("known-kwale", "Kwale", "Kwale Town", -4.1833, 39.4500, "kwale"),
        // SGR stations along the Mombasa–Nairobi line.
        place("known-mariakani-sgr", "Mariakani SGR Station", "Mariakani", -3.8500, 39.4667,
            "sgr", "mariakani", "train station", "station"),
        place("known-voi-sgr", "Voi SGR Station", "Voi", -3.3833, 38.5667,
            "sgr", "voi", "train station", "station"),
        place("known-mtito-sgr", "Mtito Andei SGR Station", "Mtito Andei", -2.6833, 38.1667,
            "sgr", "mtito andei", "train station", "station"),
        place("known-kibwezi-sgr", "Kibwezi SGR Station", "Kibwezi", -2.4167, 37.9667,
            "sgr", "kibwezi", "train station", "station"),
        place("known-emali-sgr", "Emali SGR Station", "Emali", -2.0834, 37.4667,
            "sgr", "emali", "train station", "station"),
        place("known-athi-river-sgr", "Athi River SGR Station", "Athi River", -1.4590, 36.9790,
            "sgr", "athi river", "train station", "station"),
        place("known-nairobi-terminus", "SGR Nairobi Terminus", "Syokimau, Nairobi", -1.3538, 36.8983,
            "sgr", "nairobi terminus", "syokimau", "train station", "station"),
        // Nairobi and upcountry.
        place("known-jkia", "Jomo Kenyatta International Airport", "JKIA, Nairobi", -1.3192, 36.9278,
            "airport", "jkia", "jomo kenyatta", "international airport"),
        place("known-wilson-airport", "Wilson Airport", "Langata, Nairobi", -1.3217, 36.8158,
            "airport", "wilson"),
        place("known-nairobi-cbd", "Nairobi CBD", "Nairobi", -1.2864, 36.8172,
            "town", "city centre", "nairobi town"),
        place("known-westlands", "Westlands", "Westlands, Nairobi", -1.2635, 36.8085, "westlands"),
        place("known-karen", "Karen", "Karen, Nairobi", -1.3237, 36.7083, "karen"),
        place("known-thika", "Thika", "Thika Town", -1.0333, 37.0693, "thika"),
        place("known-machakos", "Machakos", "Machakos Town", -1.5167, 37.2667, "machakos"),
        place("known-nakuru", "Nakuru", "Nakuru City", -0.3031, 36.0800, "nakuru"),
        place("known-eldoret", "Eldoret", "Eldoret Town", 0.5143, 35.2698, "eldoret"),
        place("known-kisumu", "Kisumu", "Kisumu City", -0.0917, 34.7679, "kisumu"),
    )

    private fun normalize(value: String): String =
        value.lowercase()
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    /**
     * Catalog matches for [query], nearest to [near] first when a position is known.
     * A place matches when the whole query appears in its name/detail/aliases, or when
     * every word of the query does ("sgr mombasa" finds Mombasa Terminus, not Nairobi).
     */
    fun search(query: String, near: GeoPoint?): List<DriverDestination> {
        val q = normalize(query)
        if (q.length < 2) return emptyList()
        val tokens = q.split(' ')
        val matches = places.filter { candidate ->
            val haystack = normalize(candidate.name + ' ' + candidate.detail + ' ' + candidate.aliases.joinToString(" "))
            haystack.contains(q) || (tokens.size > 1 && tokens.all { token -> haystack.contains(token) })
        }
        val ordered = if (near != null && near.valid) {
            matches.sortedBy { distanceMeters(near, it.point) }
        } else {
            matches
        }
        return ordered.take(MAX_RESULTS).map { DriverDestination(it.id, it.name, it.detail, it.point) }
    }

    private const val MAX_RESULTS = 6
}
