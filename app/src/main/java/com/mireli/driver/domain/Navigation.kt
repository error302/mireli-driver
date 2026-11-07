package com.mireli.driver.domain

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class NavigationStop(val id:String,val name:String,val latitude:Double,val longitude:Double) {
    val valid:Boolean get()=name.isNotBlank() && latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 && !(latitude==0.0&&longitude==0.0)
}
object DriverNavigation {
    /** The shared route contract orders stages from the terminus outwards. */
    fun orderedStops(stages:List<NavigationStop>,direction:String):List<NavigationStop> = when(direction){
        "FROM_TERMINUS"->stages
        "TO_TERMINUS"->stages.reversed()
        else->emptyList()
    }
    fun primaryStop(
        stages: List<NavigationStop>,
        direction: String,
        phase: String,
        bookedStageIds: Set<String> = emptySet(),
        hasDoorAddress: Boolean = false
    ): NavigationStop? {
        val pickingUp = when (phase) {
            "accepted", "at_pickup" -> true
            "in_progress" -> false
            else -> return null
        }
        val route = orderedStops(stages, direction)
        val fromTerminus = direction == "FROM_TERMINUS"
        if (pickingUp == fromTerminus) {
            return (if (fromTerminus) route.firstOrNull() else route.lastOrNull())?.takeIf { it.valid }
        }
        if (hasDoorAddress) return null
        val passengerStops = if (fromTerminus) route.drop(1) else route.dropLast(1)
        val target = if (bookedStageIds.isNotEmpty()) {
            passengerStops.firstOrNull { it.id in bookedStageIds }
        } else {
            passengerStops.singleOrNull()
        }
        return target?.takeIf { it.valid }
    }
    fun directions(stop:NavigationStop):String?=stop.takeIf{it.valid}?.let{
        "https://www.google.com/maps/dir/?api=1&destination=${encode("${it.latitude},${it.longitude}")}&travelmode=driving&dir_action=navigate"
    }
    fun addressSearch(address:String):String?=address.trim().takeIf{it.length in 3..300 && it.none{c->c.code<32}}?.let{
        "https://www.google.com/maps/search/?api=1&query=${encode(it)}"
    }
    private fun encode(value:String)=URLEncoder.encode(value,StandardCharsets.UTF_8.name())
}
