package com.mireli.driver.domain

import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class NavigationTest {
    // Pure in-memory coordinates; no records are seeded into any service.
    private val terminus=NavigationStop("t","Terminus",-4.01,39.60)
    private val coast=NavigationStop("c","Coast",-4.04,39.70)
    @Test fun inboundAndOutboundUseTheCorrectPickupAndDestination(){
        val stops=listOf(terminus,coast)
        assertEquals(terminus,DriverNavigation.primaryStop(stops,"FROM_TERMINUS","accepted"))
        assertEquals(coast,DriverNavigation.primaryStop(stops,"FROM_TERMINUS","in_progress"))
        assertEquals(coast,DriverNavigation.primaryStop(stops,"TO_TERMINUS","at_pickup"))
        assertEquals(terminus,DriverNavigation.primaryStop(stops,"TO_TERMINUS","in_progress"))
    }
    @Test fun invalidLocationsNeverSilentlyRouteToAnotherStop(){
        assertNull(DriverNavigation.primaryStop(listOf(terminus.copy(latitude=Double.NaN),coast),"FROM_TERMINUS","accepted"))
        assertNull(DriverNavigation.directions(coast.copy(longitude=181.0)))
        assertNull(DriverNavigation.directions(coast.copy(latitude=0.0,longitude=0.0)))
        assertNull(DriverNavigation.primaryStop(emptyList(),"TO_TERMINUS","accepted"))
        assertNull(DriverNavigation.primaryStop(listOf(terminus),"UNKNOWN","accepted"))
        assertNull(DriverNavigation.primaryStop(listOf(terminus),"TO_TERMINUS","completed"))
    }
    @Test fun mapsTargetsAreHttpsDrivingDirectionsWithoutPassengerIdentity(){
        val url=DriverNavigation.directions(coast)!!
        assertEquals("www.google.com",URI(url).host)
        assertTrue(url.startsWith("https://www.google.com/maps/dir/?api=1"))
        assertTrue(url.contains("travelmode=driving"));assertTrue(url.contains("dir_action=navigate"))
        assertFalse(url.contains(coast.name))
    }
    @Test fun addressInputCannotChangeDestinationHostOrAddUrlParameters(){
        val url=DriverNavigation.addressSearch("Nyali &destination=javascript:alert(1)#<script>")!!
        assertEquals("www.google.com",URI(url).host)
        assertNull(URI(url).fragment)
        assertFalse(url.contains("&destination="));assertFalse(url.contains("<script>"))
        assertNull(DriverNavigation.addressSearch("A\nB"));assertNull(DriverNavigation.addressSearch(" "))
    }
}
