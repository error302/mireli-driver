package com.mireli.driver.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mireli.driver.domain.DriverNavigation
import com.mireli.driver.domain.NavigationStop
import org.json.JSONObject

/** Opens only our constructed HTTPS Maps URLs; no WebView or arbitrary intent URIs. */
internal fun openMaps(context:Context,url:String):Boolean {
    val uri=Uri.parse(url)
    if(uri.scheme!="https" || uri.host!="www.google.com" || !uri.path.orEmpty().startsWith("/maps/"))return false
    val intent=Intent(Intent.ACTION_VIEW,uri)
    return runCatching{context.startActivity(Intent(intent).setPackage("com.google.android.apps.maps"));true}
        .getOrElse{runCatching{context.startActivity(intent);true}.getOrDefault(false)}
}

@Composable fun TripNavigation(trip:JSONObject,onNavigate:((String)->Boolean)?=null) {
    val context=LocalContext.current
    var showStops by rememberSaveable(trip.getString("id")){mutableStateOf(false)}
    var failed by remember {mutableStateOf(false)}
    val navigate=onNavigate?:{url:String->openMaps(context,url)}
    val stages=trip.optJSONArray("stages")
    val stops=(0 until (stages?.length()?:0)).map{index->val s=stages!!.getJSONObject(index)
        NavigationStop(s.optString("id",index.toString()),s.optString("name"),s.optDouble("latitude",Double.NaN),s.optDouble("longitude",Double.NaN))}
    val direction=trip.getString("direction");val phase=trip.getString("phase")
    val target=DriverNavigation.primaryStop(stops,direction,phase)
    HorizontalDivider()
    Text("Navigation",style=MaterialTheme.typography.titleMedium)
    if(target!=null) {
        Text(if(phase=="in_progress")"Route destination · ${target.name}" else "Route pickup · ${target.name}")
        Button(onClick={failed=!navigate(DriverNavigation.directions(target)!!)},modifier=Modifier.fillMaxWidth()){
            Icon(MireliIcons.LocationOn,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text(if(phase=="in_progress")"Navigate to destination" else "Navigate to pickup")
        }
    } else Text("The route location is missing. Contact dispatch before travelling.")
    Text("Directions open in Google Maps or your browser. Use the passenger's agreed stop; confirm any home address before departure.",style=MaterialTheme.typography.bodySmall)
    TextButton(onClick={showStops=!showStops}){Text(if(showStops)"Hide route stops" else "Choose a route stop")}
    if(showStops)DriverNavigation.orderedStops(stops,direction).forEach{stop->
        OutlinedButton(onClick={DriverNavigation.directions(stop)?.let{failed=!navigate(it)}},enabled=stop.valid,modifier=Modifier.fillMaxWidth()){Text(stop.name.ifBlank{"Location unavailable"})}
    }
    if(failed)Text("No maps app or browser could open. Install one and try again.",color=MaterialTheme.colorScheme.error)
}

@Composable fun PassengerAddressMap(address:String) {
    val context=LocalContext.current
    val url=DriverNavigation.addressSearch(address)?:return
    var confirm by remember {mutableStateOf(false)};var failed by remember {mutableStateOf(false)}
    TextButton(onClick={confirm=true}){Text("Find address in Maps")}
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Find the agreed address")},text={Text("This sends the address to Google Maps. Confirm the correct result with the passenger before driving; an address search is not a verified pickup pin.")},confirmButton={TextButton(onClick={confirm=false;failed=!openMaps(context,url)}){Text("Open Maps")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
    if(failed)Text("No maps app or browser could open.",color=MaterialTheme.colorScheme.error)
}
