package com.mireli.driver.domain

/**
 * The navigation screen is a state machine that used to live in five booleans and a `when {}`
 * chain. Naming the states makes the transitions explicit and testable, and it stops impossible
 * combinations such as "arrived while loading a route" from rendering.
 */
enum class NavPhase {
    /** Permission granted or not, but no usable fix yet. */
    NO_FIX,

    /** Have a position, still waiting on directions. */
    ROUTING,

    /** A fix well away from the route; a fresh route is being requested. */
    OFF_ROUTE,

    /** Following the route. */
    GUIDING,

    /** Inside the arrival radius of the stop. */
    ARRIVED,

    /** The routing service answered with an error and we are not retrying right now. */
    FAILED,
}

/**
 * Arrival wins over everything else: once the driver is at the stop, a stale route error or a
 * late off-route sample must not put a "finding a way back" banner on screen.
 */
fun navigationPhase(
    atStop: Boolean,
    hasFix: Boolean,
    offRoute: Boolean,
    loading: Boolean,
    routeError: String?,
    hasRoute: Boolean,
): NavPhase = when {
    atStop -> NavPhase.ARRIVED
    !hasFix -> NavPhase.NO_FIX
    offRoute -> NavPhase.OFF_ROUTE
    routeError != null && !loading -> NavPhase.FAILED
    loading || !hasRoute -> NavPhase.ROUTING
    else -> NavPhase.GUIDING
}
