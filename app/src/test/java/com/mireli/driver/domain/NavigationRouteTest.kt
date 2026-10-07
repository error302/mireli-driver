package com.mireli.driver.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class NavigationRouteTest {
    private fun encodePolyline6(points:List<GeoPoint>):String {
        var previousLat=0;var previousLon=0
        fun encodeDelta(delta:Int):String {
            var value=if(delta<0)(delta.toLong() shl 1).inv() else (delta.toLong() shl 1)
            val out=StringBuilder()
            while(value>=0x20){out.append(((0x20L or (value and 0x1f))+63).toInt().toChar());value=value shr 5}
            out.append((value+63).toInt().toChar())
            return out.toString()
        }
        return buildString {
            points.forEach {point->
                val lat=(point.latitude*1_000_000).roundToInt();val lon=(point.longitude*1_000_000).roundToInt()
                append(encodeDelta(lat-previousLat));append(encodeDelta(lon-previousLon));previousLat=lat;previousLon=lon
            }
        }
    }

    @Test fun decodesValhallaSixDecimalGeometryAndRejectsMalformedPoints(){
        val expected=listOf(GeoPoint(-4.01,39.60),GeoPoint(-4.011,39.602),GeoPoint(-4.015,39.61))
        assertEquals(expected,decodePolyline6(encodePolyline6(expected)))
        assertThrows(IllegalArgumentException::class.java){decodePolyline6("!!!!")}
        assertThrows(IllegalArgumentException::class.java){decodePolyline6(encodePolyline6(listOf(GeoPoint(0.0,0.0),GeoPoint(0.1,0.1))))}
    }

    @Test fun routeInstructionsAndRemainingGuidanceAreBounded(){
        val points=listOf(GeoPoint(-4.01,39.60),GeoPoint(-4.011,39.602),GeoPoint(-4.015,39.61))
        val json=JSONObject().put("provider","valhalla").put("geometry",encodePolyline6(points)).put("distanceMeters",1700).put("durationSeconds",300)
            .put("maneuvers",JSONArray().put(JSONObject().put("instruction","Continue").put("distanceMeters",700).put("durationSeconds",120).put("beginShapeIndex",0).put("endShapeIndex",1))
                .put(JSONObject().put("instruction","Turn left").put("distanceMeters",1000).put("durationSeconds",180).put("beginShapeIndex",1).put("endShapeIndex",2)))
        val route=DriverRoute.fromJson(json)
        assertEquals("Turn left",route.maneuvers.last().instruction)
        assertEquals(180,remainingDurationSeconds(route,1))
        assertTrue(remainingDistanceMeters(route,1)>0)
        assertTrue(distanceMeters(points.first(),points.last())>0)
    }
}
