package com.mireli.driver.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** The navigation screen renders from [navigationPhase]; these are the transitions that matter. */
class NavigationPhaseTest {

    @Test
    fun `arriving outranks a stale route error`() {
        assertEquals(
            NavPhase.ARRIVED,
            navigationPhase(atStop = true, hasFix = true, offRoute = false, loading = false, routeError = "boom", hasRoute = true)
        )
    }

    @Test
    fun `no fix outranks off route`() {
        assertEquals(
            NavPhase.NO_FIX,
            navigationPhase(atStop = false, hasFix = false, offRoute = true, loading = false, routeError = null, hasRoute = true)
        )
    }

    @Test
    fun `off route wins over a route that is still loaded`() {
        assertEquals(
            NavPhase.OFF_ROUTE,
            navigationPhase(atStop = false, hasFix = true, offRoute = true, loading = false, routeError = null, hasRoute = true)
        )
    }

    @Test
    fun `an error without a retry surfaces failure`() {
        assertEquals(
            NavPhase.FAILED,
            navigationPhase(atStop = false, hasFix = true, offRoute = false, loading = false, routeError = "Directions are unavailable.", hasRoute = false)
        )
    }

    @Test
    fun `an error while retrying shows routing`() {
        assertEquals(
            NavPhase.ROUTING,
            navigationPhase(atStop = false, hasFix = true, offRoute = false, loading = true, routeError = "old failure", hasRoute = false)
        )
    }

    @Test
    fun `a fix with a route guides`() {
        assertEquals(
            NavPhase.GUIDING,
            navigationPhase(atStop = false, hasFix = true, offRoute = false, loading = false, routeError = null, hasRoute = true)
        )
    }
}
