package com.mireli.driver.domain

import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val latitude:Double,val longitude:Double) {
    val valid get()=latitude.isFinite()&&longitude.isFinite()&&latitude in -90.0..90.0&&longitude in -180.0..180.0&&!(latitude==0.0&&longitude==0.0)
}
data class RouteManeuver(val instruction:String,val distanceMeters:Int,val durationSeconds:Int,val beginShapeIndex:Int,val endShapeIndex:Int)
data class DriverRoute(val geometry:List<GeoPoint>,val distanceMeters:Int,val durationSeconds:Int,val maneuvers:List<RouteManeuver>) {
    companion object {
        fun fromJson(json:JSONObject):DriverRoute {
            require(json.optString("provider")=="valhalla")
            val geometry=decodePolyline6(json.getString("geometry"))
            require(geometry.size>=2)
            val raw=json.getJSONArray("maneuvers")
            require(raw.length() in 1..120)
            val maneuvers=(0 until raw.length()).map {index->raw.getJSONObject(index).let {m->
                RouteManeuver(m.getString("instruction").filterNot(Char::isISOControl).take(240),m.getInt("distanceMeters").coerceAtLeast(0),
                    m.getInt("durationSeconds").coerceAtLeast(0),m.getInt("beginShapeIndex").coerceIn(0,geometry.lastIndex),m.getInt("endShapeIndex").coerceIn(0,geometry.lastIndex))
            }}
            return DriverRoute(geometry,json.getInt("distanceMeters").coerceAtLeast(0),json.getInt("durationSeconds").coerceAtLeast(0),maneuvers)
        }
    }
}

/** Valhalla's encoded polyline uses six decimal places; reject malformed/oversized data. */
fun decodePolyline6(encoded:String):List<GeoPoint> {
    require(encoded.length in 4..180000)
    var index=0;var latitude=0L;var longitude=0L
    val points=ArrayList<GeoPoint>()
    fun delta():Long {
        var result=0L;var shift=0;var value:Int
        do {
            require(index<encoded.length&&shift<=30)
            value=encoded[index++].code-63
            require(value in 0..63)
            result=result or ((value and 0x1f).toLong() shl shift)
            shift+=5
        }while(value>=0x20)
        return if(result and 1L!=0L) (result shr 1).inv() else result shr 1
    }
    while(index<encoded.length) {
        latitude+=delta();longitude+=delta()
        val point=GeoPoint(latitude/1_000_000.0,longitude/1_000_000.0)
        require(point.valid&&point.latitude in -5.2..-2.0&&point.longitude in 38.0..42.2&&points.size<20000)
        points+=point
    }
    require(points.size>=2)
    return points
}

fun distanceMeters(a:GeoPoint,b:GeoPoint):Double {
    val rad=Math.PI/180.0
    val lat1=a.latitude*rad;val lat2=b.latitude*rad;val dLat=lat2-lat1;val dLon=(b.longitude-a.longitude)*rad
    val h=sin(dLat/2)*sin(dLat/2)+cos(lat1)*cos(lat2)*sin(dLon/2)*sin(dLon/2)
    val safe=h.coerceIn(0.0,1.0)
    return 12_710_000*atan2(sqrt(safe),sqrt(1-safe))
}

fun nearestRoutePointIndex(route:DriverRoute,point:GeoPoint):Int = route.geometry.indices.minByOrNull { distanceMeters(route.geometry[it],point) } ?: 0

fun remainingDistanceMeters(route:DriverRoute,fromIndex:Int):Int {
    val index=fromIndex.coerceIn(0,route.geometry.lastIndex)
    return route.geometry.drop(index).zipWithNext().sumOf{(a,b)->distanceMeters(a,b).toInt()}
}
fun remainingDurationSeconds(route:DriverRoute,fromIndex:Int):Int = route.maneuvers.filter{it.endShapeIndex>fromIndex}.sumOf{it.durationSeconds}
