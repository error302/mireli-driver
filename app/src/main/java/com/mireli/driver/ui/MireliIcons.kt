package com.mireli.driver.ui
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Small original vector set; avoids bundling the entire extended Material icon catalog. */
object MireliIcons {
    val GridView by lazy { icon("GridView", "M3,3H9V9H3ZM15,3H21V9H15ZM3,15H9V21H3ZM15,15H21V21H15Z") }
    val Home by lazy { icon("Home", "M3,10L12,3L21,10M5,9V21H10V15H14V21H19V9") }
    val Calendar by lazy { icon("Calendar", "M5,5H19Q21,5 21,7V19Q21,21 19,21H5Q3,21 3,19V7Q3,5 5,5ZM7,3V7M17,3V7M3,10H21M8,15H11M14,15H17") }
    val Clock by lazy { icon("Clock", "M12,3A9,9 0,1 0,12,21A9,9 0,1 0,12,3M12,7V12L15,14") }
    val Mail by lazy { icon("Mail", "M5,5H19Q21,5 21,7V17Q21,19 19,19H5Q3,19 3,17V7Q3,5 5,5ZM4,7L12,13L20,7") }
    val Refresh by lazy { icon("Refresh", "M20,11A8,8 0,0 0,6,6L3,9M3,4V9H8M4,13A8,8 0,0 0,18,18L21,15M16,15H21V20") }
    val Cancel by lazy { icon("Cancel", "M12,3A9,9 0,1 0,12,21A9,9 0,1 0,12,3M9,9L15,15M15,9L9,15") }
    val Route by lazy { icon("Route", "M7,4A3,3 0,1 0,7,10A3,3 0,1 0,7,4M17,14A3,3 0,1 0,17,20A3,3 0,1 0,17,14M10,7H16C23,7 23,17 14,17") }
    val AccountBalanceWallet by lazy { icon("AccountBalanceWallet", "M4,5H19V9H4C1,9 1,5 4,5M3,8V20H21V9H3M17,13H21V17H17Z") }
    val PersonOutline by lazy { icon("PersonOutline", "M12,3A4,4 0,1 0,12,11A4,4 0,1 0,12,3M4,21V19C4,12 20,12 20,19V21") }
    val HeadsetMic by lazy { icon("HeadsetMic", "M4,14V11C4,1 20,1 20,11V17C20,21 17,21 14,21M4,11H7V17H4ZM17,11H20V17H17Z") }
    val Groups by lazy { icon("Groups", "M9,4A3,3 0,1 0,9,10A3,3 0,1 0,9,4M2,20V18C2,12 16,12 16,18V20M17,4C22,4 22,10 17,10M18,14C22,14 22,18 22,20") }
    val Info by lazy { icon("Info", "M12,2A10,10 0,1 0,12,22A10,10 0,1 0,12,2M12,11V17M12,7V7.2") }
    val VerifiedUser by lazy { icon("VerifiedUser", "M12,2L21,6V12C21,18 12,22 12,22C12,22 3,18 3,12V6ZM8,12L11,15L17,9") }
    val CheckCircleOutline by lazy { icon("CheckCircleOutline", "M12,2A10,10 0,1 0,12,22A10,10 0,1 0,12,2M7,12L10,15L17,8") }
    val Train by lazy { icon("Train", "M7,3H17Q20,3 20,7V16Q20,19 17,19H7Q4,19 4,16V7Q4,3 7,3ZM4,11H20M12,3V11M8,15H8.2M16,15H16.2M8,19L5,22M16,19L19,22") }
    val AccountBalance by lazy { icon("AccountBalance", "M3,8L12,3L21,8ZM3,21H21M6,11V18M12,11V18M18,11V18") }
    val Badge by lazy { icon("Badge", "M9,4V2H15V6H9ZM9,4H4V22H20V4H15M12,9A2,2 0,1 0,12,13A2,2 0,1 0,12,9M8,18C8,14 16,14 16,18") }
    val DirectionsCar by lazy { icon("DirectionsCar", "M3,12L6,5H18L21,12V20H18V17H6V20H3ZM3,12H21M6,15H7M17,15H18") }
    val PrivacyTip by lazy { icon("PrivacyTip", "M12,2L21,6V12C21,18 12,22 12,22C12,22 3,18 3,12V6ZM12,8V13M12,16V16.2") }
    val LocationOn by lazy { icon("LocationOn", "M12,22C12,22 4,14 4,9A8,8 0,1 1,20,9C20,14 12,22 12,22ZM12,6A3,3 0,1 0,12,12A3,3 0,1 0,12,6") }
    val PhoneAndroid by lazy { icon("PhoneAndroid", "M7,2H17Q19,2 19,4V20Q19,22 17,22H7Q5,22 5,20V4Q5,2 7,2ZM10,5H14M10,19H14") }
    val TripOrigin by lazy { icon("TripOrigin", "M12,4A8,8 0,1 0,12,20A8,8 0,1 0,12,4M12,9A3,3 0,1 0,12,15A3,3 0,1 0,12,9") }
    val Lock by lazy { icon("Lock", "M6,10H18V22H6ZM8,10V6C8,0 16,0 16,6V10M12,15V18") }
    private fun icon(name: String, path: String) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f
    ).addPath(pathData = addPathNodes(path), fill = null, stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.7f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round).build()
}
