package com.mireli.driver.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Original 24dp outline set drawn for a transport product: SGR trains, termini, driver
 * vehicles (car, boda, tuktuk, van, bus), passengers, fares and navigation. Stroked rather
 * than filled so a single set reads correctly on light, dark and emerald surfaces.
 */
object MireliIcons {
    // --- Trains and stations ------------------------------------------------
    /** Front-on train, the primary Mireli SGR mark. */
    val TrainFront by lazy { icon("TrainFront", "M8,3H16A4,4 0,0 1,20,7V15A4,4 0,0 1,16,19H8A4,4 0,0 1,4,15V7A4,4 0,0 1,8,3Z M7.5,6.5H16.5V10.5H7.5Z M4,12.5H20 M8,15.5H8.4M16,15.5H16.4 M8,19L6,22M16,19L18,22") }

    /** Side-on train carriage with windows, used for scheduled services. */
    val Train by lazy { icon("Train", "M6,3H18Q21,3 21,6V14Q21,17 18,17H6Q3,17 3,14V6Q3,3 6,3Z M3,10H21 M7,6.5H10V9H7Z M14,6.5H17V9H14Z M8,17L6,21M16,17L18,21 M2,21H9M15,21H22") }

    /** Rail track running to the horizon, for route and platform actions. */
    val Track by lazy { icon("Track", "M7,2L5,22M17,2L19,22 M5.5,7H18.5M6.2,12H17.8M7,17H17") }

    // --- Navigation ---------------------------------------------------------
    /** Turn instruction arrow. */
    val TurnArrow by lazy { icon("TurnArrow", "M9,21V11A3,3 0,0 1,12,8H19 M15,4L19,8L15,12") }

    val NavArrow by lazy { icon("NavArrow", "M12,3L21,20L12,16.5L3,20Z") }

    val Route by lazy { icon("Route", "M5,6A2.5,2.5 0,1 0,5,11A2.5,2.5 0,1 0,5,6M19,13A2.5,2.5 0,1 0,19,18A2.5,2.5 0,1 0,19,13M5,11V14C5,19 19,10 19,15V13") }

    /** Folded road map. */
    val Map by lazy { icon("Map", "M3,6L9,3L15,6L21,3V18L15,21L9,18L3,21Z M9,3V18M15,6V21") }

    val LocationOn by lazy { icon("LocationOn", "M12,22C12,22 4,14 4,9A8,8 0,1 1,20,9C20,14 12,22 12,22ZM12,6A3,3 0,1 0,12,12A3,3 0,1 0,12,6") }

    val TripOrigin by lazy { icon("TripOrigin", "M12,4A8,8 0,1 0,12,20A8,8 0,1 0,12,4M12,9A3,3 0,1 0,12,15A3,3 0,1 0,12,9") }

    /** Recentre the map on the driver. */
    val Compass by lazy { icon("Compass", "M12,3A9,9 0,1 0,12,21A9,9 0,1 0,12,3M15,9L13.4,13.4L9,15L10.6,10.6Z") }

    // --- Driver vehicles ----------------------------------------------------
    val DirectionsCar by lazy { icon("DirectionsCar", "M3,12L6,5H18L21,12V20H18V17H6V20H3ZM3,12H21M6.5,15H7M17,15H17.5") }

    /** Front of the driver's car: the mark for a ride the driver is on. */
    val CarFront by lazy { icon("CarFront", "M4.5,12L6,7.2C6.4,5.9 7.6,5 9,5H15C16.4,5 17.6,5.9 18,7.2L19.5,12 M4,12H20A1,1 0,0 1,21,13V16A2,2 0,0 1,19,18H5A2,2 0,0 1,3,16V13A1,1 0,0 1,4,12Z M6.5,10H17.5 M6.8,14.6H8.8M15.2,14.6H17.2 M6,18V20M18,18V20") }

    val Motorcycle by lazy { icon("Motorcycle", "M5,13A3,3 0,1 0,5,19A3,3 0,1 0,5,13M19,13A3,3 0,1 0,19,19A3,3 0,1 0,19,13M5,16L9,10H14L11,16H5M19,16L15,6H12M7,10H10") }

    val TukTuk by lazy { icon("TukTuk", "M2,16V6H12L18,10L21,12V16H19M2,16H4M9,16H14M4,6V12H12V6M12,12H20M6.5,14A2.5,2.5 0,1 0,6.5,19A2.5,2.5 0,1 0,6.5,14M16.5,14A2.5,2.5 0,1 0,16.5,19A2.5,2.5 0,1 0,16.5,14") }

    val Van by lazy { icon("Van", "M2,16V6H15L20,10L22,12V16H20M2,16H4M9,16H15M5,9H10V12H5ZM13,9H17L20,12H13ZM6.5,14A2.5,2.5 0,1 0,6.5,19A2.5,2.5 0,1 0,6.5,14M17.5,14A2.5,2.5 0,1 0,17.5,19A2.5,2.5 0,1 0,17.5,14") }

    val Bus by lazy { icon("Bus", "M2,16V7Q2,5 4,5H19Q22,5 22,8V16H20M2,16H4M9,16H15M5,8H9V12H5ZM12,8H19V12H12ZM6.5,14A2.5,2.5 0,1 0,6.5,19A2.5,2.5 0,1 0,6.5,14M17.5,14A2.5,2.5 0,1 0,17.5,19A2.5,2.5 0,1 0,17.5,14") }

    // --- People and seats ---------------------------------------------------
    val PersonOutline by lazy { icon("PersonOutline", "M12,3A4,4 0,1 0,12,11A4,4 0,1 0,12,3M4,21V19C4,12 20,12 20,19V21") }

    val Groups by lazy { icon("Groups", "M9,4A3,3 0,1 0,9,10A3,3 0,1 0,9,4M2,20V18C2,12 16,12 16,18V20M17,4C22,4 22,10 17,10M18,14C22,14 22,18 22,20") }

    /** Passenger seat, used for booked and boarded capacity. */
    val Seat by lazy { icon("Seat", "M7,4V13H17 M5,20V17H18A2,2 0,0 0,20,15V10 M5,17V20") }

    val Ticket by lazy { icon("Ticket", "M3,7H21V10A2,2 0,0 0,21,14V17H3V14A2,2 0,0 0,3,10Z M8.5,7V17") }

    val Badge by lazy { icon("Badge", "M9,4V2H15V6H9ZM9,4H4V22H20V4H15M12,9A2,2 0,1 0,12,13A2,2 0,1 0,12,9M8,18C8,14 16,14 16,18") }

    val VerifiedUser by lazy { icon("VerifiedUser", "M12,2L21,6V12C21,18 12,22 12,22C12,22 3,18 3,12V6ZM8,12L11,15L17,9") }

    val CheckCircleOutline by lazy { icon("CheckCircleOutline", "M12,2A10,10 0,1 0,12,22A10,10 0,1 0,12,2M7,12L10,15L17,8") }

    // --- Money, time, status ------------------------------------------------
    val AccountBalanceWallet by lazy { icon("AccountBalanceWallet", "M4,5H19V9H4C1,9 1,5 4,5M3,8V20H21V9H3M17,13H21V17H17Z") }

    val AccountBalance by lazy { icon("AccountBalance", "M3,8L12,3L21,8ZM3,21H21M6,11V18M12,11V18M18,11V18") }

    val Clock by lazy { icon("Clock", "M12,3A9,9 0,1 0,12,21A9,9 0,1 0,12,3M12,7V12L15,14") }

    val Calendar by lazy { icon("Calendar", "M5,5H19Q21,5 21,7V19Q21,21 19,21H5Q3,21 3,19V7Q3,5 5,5ZM7,3V7M17,3V7M3,10H21M8,15H11M14,15H17") }

    val Bell by lazy { icon("Bell", "M6,9A6,6 0,0 1,18,9V14L20,17H4L6,14Z M10,20A2,2 0,0 0,14,20") }

    /** Weekly fare bars. */
    val ChartBars by lazy { icon("ChartBars", "M4,20V13M9,20V7M14,20V11M19,20V4 M2,21H21") }

    val Signal by lazy { icon("Signal", "M2,15C8,9 16,9 22,15 M5,18C9,14 15,14 19,18 M9,21C10.5,19.5 13.5,19.5 15,21") }

    val Home by lazy { icon("Home", "M3,10L12,3L21,10M5,9V21H10V15H14V21H19V9") }

    val Refresh by lazy { icon("Refresh", "M20,11A8,8 0,0 0,6,6L3,9M3,4V9H8M4,13A8,8 0,0 0,18,18L21,15M16,15H21V20") }

    val Cancel by lazy { icon("Cancel", "M12,3A9,9 0,1 0,12,21A9,9 0,1 0,12,3M9,9L15,15M15,9L9,15") }

    val Info by lazy { icon("Info", "M12,2A10,10 0,1 0,12,22A10,10 0,1 0,12,2M12,11V17M12,7V7.2") }

    val HeadsetMic by lazy { icon("HeadsetMic", "M4,14V11C4,1 20,1 20,11V17C20,21 17,21 14,21M4,11H7V17H4ZM17,11H20V17H17Z") }

    val Mail by lazy { icon("Mail", "M5,5H19Q21,5 21,7V17Q21,19 19,19H5Q3,19 3,17V7Q3,5 5,5ZM4,7L12,13L20,7") }

    val PrivacyTip by lazy { icon("PrivacyTip", "M12,2L21,6V12C21,18 12,22 12,22C12,22 3,18 3,12V6ZM12,8V13M12,16V16.2") }

    val PhoneAndroid by lazy { icon("PhoneAndroid", "M7,2H17Q19,2 19,4V20Q19,22 17,22H7Q5,22 5,20V4Q5,2 7,2ZM10,5H14M10,19H14") }

    val Lock by lazy { icon("Lock", "M6,10H18V22H6ZM8,10V6C8,0 16,0 16,6V10M12,15V18") }

    val ChevronRight by lazy { icon("ChevronRight", "M9,5L16,12L9,19") }

    val ExpandMore by lazy { icon("ExpandMore", "M6,9L12,15L18,9") }

    val ExpandLess by lazy { icon("ExpandLess", "M6,15L12,9L18,15") }

    // --- Appearance ---------------------------------------------------------
    /** Sun, shown when tapping the control returns the app to the light theme. */
    val Sun by lazy { icon("Sun", "M12,8A4,4 0,1 0,12,16A4,4 0,1 0,12,8 M12,2V4M12,20V22M2,12H4M20,12H22M4.9,4.9L6.3,6.3M17.7,17.7L19.1,19.1M19.1,4.9L17.7,6.3M6.3,17.7L4.9,19.1") }

    /** Crescent moon, shown when tapping the control darkens the app. */
    val Moon by lazy { icon("Moon", "M20,14.5A8.5,8.5 0,1 1,9.5,4A7,7 0,1 0,20,14.5Z") }

    private fun icon(name: String, path: String) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).addPath(pathData = addPathNodes(path), fill = null, stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
}
