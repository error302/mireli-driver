package com.mireli.driver.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mireli.driver.domain.NavigationStop
import org.json.JSONObject

@Composable fun LiveTripCard(trip:JSONObject,busy:Boolean,eligible:Boolean,command:(JSONObject,String,JSONObject)->Unit,
    routeRequest:RouteRequest?=null, modifier:Modifier=Modifier, openInitially:Boolean=false,
    onStartNavigation: ((NavigationStop) -> Unit)? = null,
    onSearchAddress: ((String) -> Unit)? = null,
    showNavigation: Boolean = true, primaryActionAnchored: Boolean = false) {
    val id=trip.getString("id");val phase=trip.getString("phase");val context=LocalContext.current
    var expanded by rememberSaveable(id){mutableStateOf(false)}
    var action by rememberSaveable(id,trip.getInt("version")){mutableStateOf<String?>(null)}
    var bookingId by rememberSaveable(id,trip.getInt("version")){mutableStateOf<String?>(null)}
    val canAct=!busy&&eligible
    val passengers=trip.getJSONArray("passengers")
    Surface(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            val summary = listOfNotNull(phase.transferLabel(), trip.departureDay(),
                trip.departureClock()?.let { "$it EAT" }).joinToString(" · ")
            val title = if (primaryActionAnchored) "Trip details" else trip.getString("routeName")
            DepartureDisclosure(title, summary = summary.takeUnless { primaryActionAnchored }, initiallyExpanded = openInitially) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TransferEndpoints(trip, allowIntermediateStops = true)
                    val seats = trip.partySeats()
                    if (seats > 0) Text(if (seats == 1) "1 passenger" else "$seats passengers",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!primaryActionAnchored) trip.primaryTripAction()?.let { (command, label) ->
                        DepartureButton(onClick = { bookingId = null; action = command }, drivingAction = true,
                            enabled = if (command == "complete") !busy else canAct,
                            modifier = Modifier.fillMaxWidth()) { Text(label) }
                    }
                    if (phase == "assigned") {
                        TextButton(onClick = { bookingId = null; action = "decline" }, enabled = canAct) {
                            Text("Decline assignment", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    if(phase in listOf("accepted","at_pickup","in_progress")) {
                        if(phase=="at_pickup")Text("Check booking codes and account for each booked seat before departure.",
                            style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick={expanded=!expanded}){
                            Icon(MireliIcons.Groups,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text(if(expanded)"Hide manifest" else "View passenger manifest")
                        }
                    }
                    if(expanded)for(index in 0 until passengers.length()) {
                        val p=passengers.getJSONObject(index)
                        HorizontalDivider()
                        Text("${p.getString("name")} · ${p.getInt("seats")} seats",style=MaterialTheme.typography.titleMedium)
                        Text("${p.getInt("boarded")} boarded · ${p.getInt("noShow")} no-show · ${p.getInt("remaining")} waiting")
                        p.optString("stageName").takeIf{it.isNotBlank()&&it!="null"}?.let{Text("Stage: $it")}
                        p.optString("homeAddress").takeIf{it.isNotBlank()&&it!="null"}?.let{address->
                            Text("${if(trip.getString("direction")=="TO_TERMINUS")"Pickup" else "Drop-off"}: $address")
                            PassengerAddressMap(address, onSearchAddress)
                        }
                        p.optString("phone").takeIf{Regex("\\+?254[17][0-9]{8}").matches(it)}?.let{phone->
                            TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:$phone")))}}){Text("Call passenger")}
                        }
                        if(phase=="at_pickup"&&p.getInt("remaining")>0) {
                            OutlinedButton(onClick={bookingId=p.getString("id");action="board"},enabled=canAct){Text("Board this party")}
                            TextButton(onClick={bookingId=p.getString("id");action="no_show"},enabled=canAct){Text("Report remaining seats as no-show")}
                        }
                    }
                    if(showNavigation && phase in listOf("accepted","at_pickup","in_progress")) TripNavigation(trip, onRequestRoute = routeRequest, onStartNavigation = onStartNavigation)
                    DepartureNote("Trip reference · $id")
                }
            }
        }
    }
    action?.let { selected ->
        TripCommandDialog(trip, selected, bookingId, busy, eligible, command) {
            action = null
            bookingId = null
        }
    }
}

internal fun JSONObject.primaryTripAction(): Pair<String, String>? = when (optString("phase")) {
    "assigned" -> "accept" to "Accept assignment"
    "accepted" -> "arrive" to "Arrived at pickup"
    "at_pickup" -> "start" to "Start journey"
    "in_progress" -> "complete" to "Complete trip"
    else -> null
}

@Composable
internal fun TripCommandDialog(
    trip: JSONObject,
    action: String,
    bookingId: String?,
    busy: Boolean,
    eligible: Boolean,
    command: (JSONObject, String, JSONObject) -> Unit,
    onDismiss: () -> Unit,
) {
    val passengers = trip.getJSONArray("passengers")
    val booking = bookingId?.let { id ->
        (0 until passengers.length()).firstNotNullOfOrNull { index ->
            passengers.getJSONObject(index).takeIf { it.getString("id") == id && it.getInt("remaining") > 0 }
        }
    }
    var code by remember { mutableStateOf("") }
    var count by rememberSaveable(bookingId) { mutableStateOf(booking?.getInt("remaining")?.toString().orEmpty()) }
    var reason by remember { mutableStateOf("") }
    val needsBooking = action in listOf("board", "no_show")
    val seats = count.toIntOrNull() ?: 0
    val canConfirm = !busy && (eligible || action == "complete") && (!needsBooking || booking != null) &&
        (action != "board" || code.trim().length >= 4 && seats in 1..(booking?.getInt("remaining") ?: 0)) &&
        (action !in listOf("decline", "no_show") || reason.trim().length >= 5)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when (action) {
            "board" -> "Confirm boarding"
            "no_show" -> "Report a no-show"
            "complete" -> "Complete trip?"
            "arrive" -> "Confirm pickup arrival"
            "start" -> "Start journey?"
            "decline" -> "Decline assignment?"
            else -> "Accept assignment?"
        }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)) {
                Text(when (action) {
                    "board" -> "Enter the booking code and the number boarding."
                    "no_show" -> "Report after departure time and after trying to reach the passenger."
                    "complete" -> "Confirm all boarded passengers have been dropped off."
                    "start" -> "Account for every booked seat and check capacity."
                    "decline" -> "Dispatch will release this assignment."
                    "arrive" -> "Confirm you’re at the agreed pickup point."
                    else -> "Confirm you can serve this assignment."
                })
                if (needsBooking && booking == null) Text("Select a party with seats remaining.")
                if (action == "board") {
                    OutlinedTextField(code, { code = it.take(40).uppercase() },
                        label = { Text("Passenger booking code") }, singleLine = true)
                    OutlinedTextField(count, { count = it.filter(Char::isDigit).take(2) },
                        label = { Text("People boarded now") }, singleLine = true)
                }
                if (action in listOf("decline", "no_show")) {
                    OutlinedTextField(reason, { reason = it.take(200) }, label = { Text("Reason") }, minLines = 2)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canConfirm, onClick = {
                val details = JSONObject()
                if (needsBooking) booking?.let { details.put("bookingId", it.getString("id")) }
                if (action == "board") details.put("code", code).put("count", seats)
                if (action in listOf("decline", "no_show")) details.put("reason", reason.trim())
                onDismiss()
                command(trip, action, details)
            }) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
