package com.mireli.driver.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable fun LiveTripCard(trip:JSONObject,busy:Boolean,eligible:Boolean,command:(JSONObject,String,JSONObject)->Unit,routeRequest:RouteRequest?=null) {
    val id=trip.getString("id");val phase=trip.getString("phase");val context=LocalContext.current
    var expanded by rememberSaveable(id){mutableStateOf(false)}
    var action by rememberSaveable(id,trip.getInt("version")){mutableStateOf<String?>(null)}
    var bookingId by rememberSaveable(id,trip.getInt("version")){mutableStateOf<String?>(null)}
    var code by remember(id,trip.getInt("version")){mutableStateOf("")}
    var count by rememberSaveable(id,trip.getInt("version")){mutableStateOf("")}
    var reason by remember(id,trip.getInt("version")){mutableStateOf("")}
    val canAct=!busy&&eligible
    val passengers=trip.getJSONArray("passengers")
    val booking=bookingId?.let{selectedId->
        (0 until passengers.length()).firstNotNullOfOrNull{index->
            passengers.getJSONObject(index).takeIf{it.getString("id")==selectedId && it.getInt("remaining")>0}
        }
    }
    OutlinedCard(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface)) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        TransferStatus(phase)
        Text(trip.getString("routeName"),style=MaterialTheme.typography.titleLarge)
        TransferSchedule(trip)
        TransferEndpoints(trip,allowIntermediateStops=true)
        if(phase=="completed") {
            DriverNotice(MireliIcons.AccountBalanceWallet,"Journey recorded","Check Earnings for settlement status.")
        }
        if(phase=="assigned") {
            Button(onClick={bookingId=null;action="accept"},enabled=canAct,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(12.dp)){
                Icon(MireliIcons.CheckCircleOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Accept assignment")
            }
            TextButton(onClick={bookingId=null;action="decline"},enabled=canAct){Text("Decline with a reason")}
        }
        if(phase=="accepted")Button(onClick={bookingId=null;action="arrive"},enabled=canAct,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(12.dp)){
            Icon(MireliIcons.LocationOn,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Arrived at pickup")
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
                PassengerAddressMap(address)
            }
            p.optString("phone").takeIf{Regex("\\+?254[17][0-9]{8}").matches(it)}?.let{phone->
                TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:$phone")))}}){Text("Call passenger")}
            }
            if(phase=="at_pickup"&&p.getInt("remaining")>0) {
                OutlinedButton(onClick={bookingId=p.getString("id");count=p.getInt("remaining").toString();code="";action="board"},enabled=canAct){Text("Board this party")}
                TextButton(onClick={bookingId=p.getString("id");reason="";action="no_show"},enabled=canAct){Text("Report remaining seats as no-show")}
            }
        }
        if(phase=="at_pickup")Button(onClick={bookingId=null;action="start"},enabled=canAct,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(12.dp)){
            Icon(MireliIcons.DirectionsCar,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Start journey")
        }
        if(phase=="in_progress")Button(onClick={bookingId=null;action="complete"},enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(12.dp)){
            Icon(MireliIcons.CheckCircleOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Complete delivered journey")
        }
        if(phase in listOf("accepted","at_pickup","in_progress")) TripNavigation(trip,onRequestRoute=routeRequest)
        Text("Reference: $id",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }}
    action?.let{selected->
        val needsBooking=selected in listOf("board","no_show")
        val seats=count.toIntOrNull()?:0
        val canConfirm=(if(selected=="complete")!busy else canAct) && (!needsBooking||booking!=null) &&
            (selected!="board" || code.trim().length>=4 && seats in 1..(booking?.getInt("remaining")?:0)) &&
            (selected !in listOf("decline","no_show") || reason.trim().length>=5)
        val dismiss={action=null;bookingId=null;code=""}
        AlertDialog(onDismissRequest=dismiss,title={Text(when(selected){"board"->"Confirm boarding";"no_show"->"Report a no-show";"complete"->"Confirm delivery";else->selected.replace('_',' ').replaceFirstChar{it.uppercase()}})},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(when(selected){
            "board"->"Ask this party for their booking code and enter the number of people who boarded."
            "no_show"->"Report only after departure time and a reasonable attempt to contact the party. Held funds go to operations review."
            "complete"->"Confirm only when you have delivered the boarded passengers. This creates settlement records; it does not confirm an M-Pesa transfer."
            "start"->"Resolve every booked seat and check your vehicle capacity before departure."
            "decline"->"Dispatch will receive your reason and release this assignment."
            "arrive"->"Confirm that you are at the agreed pickup point and ready to board."
            else->"Confirm that you can serve this assignment."
        })
        if(needsBooking && booking==null)Text("Close this dialog and select a party with seats remaining.")
        if(selected=="board") {
            OutlinedTextField(code,{code=it.take(40).uppercase()},label={Text("Passenger booking code")},singleLine=true)
            OutlinedTextField(count,{count=it.filter(Char::isDigit).take(2)},label={Text("People boarded now")},singleLine=true)
        }
        if(selected in listOf("decline","no_show"))OutlinedTextField(reason,{reason=it.take(200)},label={Text("Reason")},minLines=2)
    }},confirmButton={TextButton(enabled=canConfirm,onClick={
        val details=JSONObject()
        if(needsBooking)booking?.let{details.put("bookingId",it.getString("id"))}
        if(selected=="board")details.put("code",code).put("count",seats)
        if(selected in listOf("decline","no_show"))details.put("reason",reason.trim())
        dismiss();command(trip,selected,details)
    }){Text("Confirm")}},dismissButton={TextButton(onClick=dismiss){Text("Cancel")}})}
}
