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
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun LiveTripCard(trip:JSONObject,busy:Boolean,eligible:Boolean,command:(JSONObject,String,JSONObject)->Unit) {
    val id=trip.getString("id");val phase=trip.getString("phase");val context=LocalContext.current
    var expanded by rememberSaveable(id){mutableStateOf(false)}
    var action by rememberSaveable(id,trip.getInt("version")){mutableStateOf<String?>(null)}
    var booking by remember(id,trip.getInt("version")){mutableStateOf<JSONObject?>(null)}
    var code by rememberSaveable(id,trip.getInt("version")){mutableStateOf("")}
    var count by rememberSaveable(id,trip.getInt("version")){mutableStateOf("")}
    var reason by rememberSaveable(id,trip.getInt("version")){mutableStateOf("")}
    val canAct=!busy&&eligible
    val passengers=trip.getJSONArray("passengers")
    Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(trip.getString("routeName"),style=MaterialTheme.typography.titleLarge)
        Text("${trip.getString("serviceType").replaceFirstChar{it.uppercase()}} · ${phase.replace('_',' ')}")
        val reporting=runCatching {DateTimeFormatter.ofPattern("EEE d MMM · HH:mm").withZone(ZoneId.of("Africa/Nairobi")).format(Instant.parse(trip.getString("departureAt")))}.getOrDefault(trip.getString("departureAt"))
        Text("Departure: $reporting EAT")
        Text(if(trip.getString("direction")=="TO_TERMINUS")"To Mombasa SGR Terminus" else "From Mombasa SGR Terminus")
        if(phase=="assigned") {
            Button(onClick={action="accept"},enabled=canAct,modifier=Modifier.fillMaxWidth()){Text("Accept assignment")}
            TextButton(onClick={action="decline"},enabled=canAct){Text("Decline with a reason")}
        }
        if(phase=="accepted")Button(onClick={action="arrive"},enabled=canAct,modifier=Modifier.fillMaxWidth()){Text("Arrived at pickup")}
        if(phase in listOf("accepted","at_pickup","in_progress")) {
            TripNavigation(trip)
            TextButton(onClick={expanded=!expanded}){Text(if(expanded)"Hide manifest" else "View passenger manifest")}
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
                OutlinedButton(onClick={booking=p;count=p.getInt("remaining").toString();code="";action="board"},enabled=canAct){Text("Board this party")}
                TextButton(onClick={booking=p;reason="";action="no_show"},enabled=canAct){Text("Report remaining seats as no-show")}
            }
        }
        if(phase=="at_pickup")Button(onClick={action="start"},enabled=canAct,modifier=Modifier.fillMaxWidth()){Text("Start journey")}
        if(phase=="in_progress")Button(onClick={action="complete"},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Complete delivered journey")}
        Text("Reference: $id",style=MaterialTheme.typography.bodySmall)
    }}
    action?.let{selected->AlertDialog(onDismissRequest={action=null},title={Text(when(selected){"board"->"Confirm boarding";"no_show"->"Report a no-show";"complete"->"Confirm delivery";else->selected.replace('_',' ').replaceFirstChar{it.uppercase()}})},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text(when(selected){
            "board"->"Ask this party for their booking code and enter the number of people who boarded."
            "no_show"->"Report only after departure time and a reasonable attempt to contact the party. Held funds go to operations review."
            "complete"->"Confirm only when you have delivered the boarded passengers. This creates settlement records; it does not confirm an M-Pesa transfer."
            "start"->"Resolve every booked seat and check your vehicle capacity before departure."
            "decline"->"Dispatch will receive your reason and release this assignment."
            "arrive"->"Confirm that you are at the agreed pickup point and ready to board."
            else->"Confirm that you can serve this assignment."
        })
        if(selected=="board") {
            OutlinedTextField(code,{code=it.take(40).uppercase()},label={Text("Passenger booking code")},singleLine=true)
            OutlinedTextField(count,{count=it.filter(Char::isDigit).take(2)},label={Text("People boarded now")},singleLine=true)
        }
        if(selected in listOf("decline","no_show"))OutlinedTextField(reason,{reason=it.take(200)},label={Text("Reason")},minLines=2)
    }},confirmButton={TextButton(enabled=!busy&&(selected!="board"||(code.trim().length>=4&&(count.toIntOrNull()?:0)>0))&&(selected !in listOf("decline","no_show")||reason.trim().length>=5),onClick={
        val details=JSONObject();booking?.let{details.put("bookingId",it.getString("id"))}
        if(selected=="board")details.put("code",code).put("count",count.toInt())
        if(selected in listOf("decline","no_show"))details.put("reason",reason.trim())
        action=null;command(trip,selected,details)
    }){Text("Confirm")}},dismissButton={TextButton(onClick={action=null}){Text("Cancel")}})}
}
