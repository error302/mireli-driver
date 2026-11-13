package com.mireli.driver.domain

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder

/**
 * Free-form destination lookup and route preview.
 *
 * Geocoding uses Photon (OpenStreetMap) biased around the Kenyan coast; routing uses the public
 * OSRM demo server with `alternatives=false`, which is the shortest recommended driving path.
 * Both are public community services: the production backend should proxy or replace them (see
 * the operations handoff), and the results here are always labelled as a preview, never as an
 * authorized dispatch route.
 */
object PlaceSearch {
    data class Place(val id: String, val name: String, val detail: String, val point: GeoPoint)

    data class Result(val route: DriverRoute, val destination: GeoPoint)

    suspend fun search(query: String, near: GeoPoint?): List<Place> {
        require(query.trim().length >= 3) { "Type at least three characters." }
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val bias = near?.let { "&lat=${it.latitude}&lon=${it.longitude}" } ?: ""
        val body = get("https://photon.komoot.io/api?q=$encoded&limit=5&lang=en&bbox=33.0,-5.1,42.4,5.6$bias")
        val features = org.json.JSONObject(body).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { index ->
            val feature = features.optJSONObject(index) ?: return@mapNotNull null
            val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
            val properties = feature.optJSONObject("properties") ?: return@mapNotNull null
            val point = GeoPoint(coordinates.optDouble(1), coordinates.optDouble(0))
            if (!point.valid) return@mapNotNull null
            val name = properties.optString("name").takeIf { it.isNotBlank() }
                ?: properties.optString("street").takeIf { it.isNotBlank() }
                ?: query.trim()
            val detail = listOf("city", "county", "state", "country")
                .mapNotNull { properties.optString(it).takeIf { part -> part.isNotBlank() } }
                .distinct().joinToString(", ")
            Place(properties.optString("osm_id", index.toString()), name, detail, point)
        }
    }

    /** The recommended (shortest-driving-time) path from the driver's position to the destination. */
    suspend fun route(from: GeoPoint, to: GeoPoint): Result {
        val path = "https://router.project-osrm.org/route/v1/driving/" +
            "${from.longitude},${from.latitude};${to.longitude},${to.latitude}" +
            "?overview=full&geometries=geojson&alternatives=false&steps=true"
        val body = get(path)
        val json = org.json.JSONObject(body)
        require(json.optString("code") == "Ok") { "No road route found to that destination." }
        val routes = json.getJSONArray("routes")
        require(routes.length() > 0) { "No road route found to that destination." }
        val route = routes.getJSONObject(0)
        val geometry = route.getJSONObject("geometry").getJSONArray("coordinates").let { points ->
            (0 until points.length()).map { index ->
                GeoPoint(points.getJSONArray(index).optDouble(1), points.getJSONArray(index).optDouble(0))
            }.filter { it.valid && it.latitude in -5.2..5.6 && it.longitude in 33.0..42.4 }
        }
        require(geometry.size >= 2) { "The route service returned unusable directions." }
        val legs = route.getJSONArray("legs")
        val maneuvers = buildList {
            (0 until legs.length()).forEach { leg ->
                val steps = legs.getJSONObject(leg).optJSONArray("steps") ?: return@forEach
                (0 until steps.length()).forEach { stepIndex ->
                    val step = steps.getJSONObject(stepIndex)
                    val endShape = step.optInt("geometry_index", -1)
                    val endShapeIndex = if (endShape in geometry.indices) endShape else geometry.lastIndex
                    val maneuver = step.optJSONObject("maneuver")
                    val type = maneuver?.optString("type").orEmpty()
                    val modifier = maneuver?.optString("modifier").orEmpty()
                    val road = step.optString("name").takeIf { it.isNotBlank() }
                    val instruction = when {
                        type == "depart" -> "Start toward ${road ?: "the destination"}"
                        type == "arrive" -> "Arrive at the destination"
                        type == "roundabout" || type == "rotary" -> "At the roundabout take the exit toward ${road ?: "the destination"}"
                        modifier == "left" -> "Turn left onto ${road ?: "the continuing road"}"
                        modifier == "right" -> "Turn right onto ${road ?: "the continuing road"}"
                        modifier == "uturn" -> "Make a U-turn"
                        else -> "Continue onto ${road ?: "the road"}"
                    }
                    add(RouteManeuver(instruction.take(240), step.optInt("distance", 0).coerceAtLeast(0),
                        step.optInt("duration", 0).coerceAtLeast(0), (endShapeIndex - 1).coerceIn(0, geometry.lastIndex), endShapeIndex))
                }
            }
        }
        return Result(
            DriverRoute(geometry, route.getInt("distance").coerceAtLeast(0),
                route.getInt("duration").coerceAtLeast(0), maneuvers.ifEmpty {
                    listOf(RouteManeuver("Head to the destination", route.getInt("distance").coerceAtLeast(0),
                        route.getInt("duration").coerceAtLeast(0), 0, geometry.lastIndex))
                }),
            to
        )
    }

    private fun get(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8000
            connection.readTimeout = 15000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "MireliDriver/0.9 (Android)")
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            require(status in 200..299) { "The map service is unreachable right now." }
            require(text.length <= 4 * 1024 * 1024) { "The map service returned too much data." }
            text
        } finally {
            connection.disconnect()
        }
    }
}
