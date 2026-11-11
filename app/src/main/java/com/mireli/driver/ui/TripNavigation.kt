package com.mireli.driver.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mireli.driver.BuildConfig
import com.mireli.driver.R
import com.mireli.driver.domain.DriverNavigation
import com.mireli.driver.domain.DriverRoute
import com.mireli.driver.domain.GeoPoint
import com.mireli.driver.domain.NavigationStop
import com.mireli.driver.domain.RouteManeuver
import com.mireli.driver.domain.bearingBetween
import com.mireli.driver.domain.distanceMeters
import com.mireli.driver.domain.nearestRoutePointIndex
import com.mireli.driver.domain.remainingDistanceMeters
import com.mireli.driver.domain.remainingDurationSeconds
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Polyline
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

typealias RouteRequest=(tripId:String,stageId:String,origin:GeoPoint,onComplete:(Result<JSONObject>)->Unit)->Unit
private const val LOCATION_MAX_AGE_MS = 45_000L

/** Heading is only trusted above this speed; below it the route bearing is used instead. */
private const val HEADING_MIN_SPEED_MPS = 1.5f

/** How long a position change is eased over, so the car glides instead of teleporting. */
private const val MARKER_GLIDE_MS = 900L

private const val VEHICLE_ICON_ID = "mireli-vehicle-icon"
private const val VEHICLE_LAYER_ID = "mireli-vehicle-layer"
private const val VEHICLE_SOURCE_ID = "mireli-vehicle-source"
private const val ACCURACY_LAYER_ID = "mireli-accuracy-layer"
private const val ACCURACY_SOURCE_ID = "mireli-accuracy-source"

/**
 * Resolves the heading to draw the car at.
 * Device bearing wins only when the driver is actually moving; otherwise the direction of
 * the route ahead is used, so the car does not spin while stationary at a stop.
 */
internal fun resolveHeading(
    deviceBearing:Float?,
    speedMps:Float?,
    previousHeading:Double?,
    route:DriverRoute?,
    point:GeoPoint
):Double {
    val moving=(speedMps?:0f)>HEADING_MIN_SPEED_MPS
    if(moving&&deviceBearing!=null&&!deviceBearing.isNaN())return deviceBearing.toDouble()
    val routeHeading=route?.let{active->
        val index=nearestRoutePointIndex(active,point)
        val behind=active.geometry.getOrNull(index)
        val ahead=active.geometry.getOrNull((index+1).coerceAtMost(active.geometry.lastIndex))
        if(ahead!=null&&behind!=null&&ahead!=behind)bearingBetween(behind,ahead) else null
    }
    if(routeHeading!=null)return routeHeading
    return previousHeading?:0.0
}

/** Opens only our constructed HTTPS Maps URLs as an explicit navigation fallback. */
internal fun openMaps(context:Context,url:String):Boolean {
    val uri=Uri.parse(url)
    if(uri.scheme!="https" || uri.host!="www.google.com" || !uri.path.orEmpty().startsWith("/maps/"))return false
    val intent=Intent(Intent.ACTION_VIEW,uri)
    return runCatching{context.startActivity(Intent(intent).setPackage("com.google.android.apps.maps"));true}
        .getOrElse{runCatching{context.startActivity(intent);true}.getOrDefault(false)}
}

@Composable fun TripNavigation(trip:JSONObject,onNavigate:((String)->Boolean)?=null,onRequestRoute:RouteRequest?=null) {
    val context=LocalContext.current
    val lifecycleOwner=LocalLifecycleOwner.current
    val tripId=trip.getString("id")
    var failed by remember {mutableStateOf(false)}
    var disclosure by rememberSaveable(tripId){mutableStateOf(false)}
    var navigating by rememberSaveable(tripId){mutableStateOf(false)}
    var locationError by remember(tripId){mutableStateOf<String?>(null)}
    var routeError by remember(tripId){mutableStateOf<String?>(null)}
    var route by remember(tripId){mutableStateOf<DriverRoute?>(null)}
    var routeLoading by remember(tripId){mutableStateOf(false)}
    var lastRouteRequest by remember(tripId){mutableLongStateOf(0L)}
    var routeRequestRevision by remember(tripId){mutableIntStateOf(0)}
    var offRouteSamples by remember(tripId){mutableIntStateOf(0)}
    var locationFix by remember(tripId){mutableStateOf<Location?>(null)}
    val currentLocation = locationFix?.let { GeoPoint(it.latitude, it.longitude) }
    LaunchedEffect(locationFix) {
        val fix = locationFix ?: return@LaunchedEffect
        val ageMs = (SystemClock.elapsedRealtimeNanos() - fix.elapsedRealtimeNanos) / 1_000_000
        delay((LOCATION_MAX_AGE_MS - ageMs).coerceAtLeast(0L))
        locationFix = null
        locationError = "Waiting for a fresh location fix…"
    }
    val routingAvailable by rememberUpdatedState(onRequestRoute != null)
    LaunchedEffect(routingAvailable) {
        if (!routingAvailable) {
            disclosure = false
            navigating = false
            route = null
            routeLoading = false
            lastRouteRequest = 0L
            routeRequestRevision++
        }
    }
    val navigate=onNavigate?:{url:String->openMaps(context,url)}
    val stops = trip.navigationStops()
    val direction=trip.optString("direction");val phase=trip.optString("phase")
    val defaultTarget = DriverNavigation.primaryStop(stops, direction, phase, trip.bookedStageIds(), trip.hasDoorAddress())
    var selectedStopId by rememberSaveable(tripId, phase, defaultTarget?.id){mutableStateOf(defaultTarget?.id)}
    LaunchedEffect(tripId,selectedStopId) {
        route = null; routeError = null; offRouteSamples = 0; lastRouteRequest = 0L
        routeLoading = false; routeRequestRevision++
    }
    val target=stops.firstOrNull{it.id==selectedStopId}?.takeIf{it.valid}
    val ordered=DriverNavigation.orderedStops(stops,direction)
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){result->
        if (!routingAvailable) return@rememberLauncherForActivityResult
        val allowed=result[Manifest.permission.ACCESS_FINE_LOCATION]==true||result[Manifest.permission.ACCESS_COARSE_LOCATION]==true
        if(allowed){locationError=null;navigating=true}else locationError="Location access is needed for live driver position and route guidance."
    }
    val hasLocationPermission=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED

    DisposableEffect(tripId,navigating,hasLocationPermission,routingAvailable) {
        if(!routingAvailable||!navigating||!hasLocationPermission){onDispose{} } else {
            val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var listening=false
            val listener=object:LocationListener {
                override fun onLocationChanged(location:Location){
                    val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
                    val point = GeoPoint(location.latitude, location.longitude)
                    val previous = locationFix
                    if (point.valid && ageMs in 0 until LOCATION_MAX_AGE_MS &&
                        (previous == null || location.elapsedRealtimeNanos > previous.elapsedRealtimeNanos)) {
                        locationFix = location
                        locationError = null
                    }
                }
                @Deprecated("Deprecated by Android") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?)=Unit
                override fun onProviderEnabled(provider:String) {locationError=null}
                override fun onProviderDisabled(provider:String) {if(locationFix==null)locationError="Turn on device location to start route guidance."}
            }
            fun startListening() {
                if(listening)return
                listening=true
                try {
                    val providers=if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
                        listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER) else listOf(LocationManager.NETWORK_PROVIDER)
                    providers.filter { it in manager.allProviders }.forEach {provider->
                        manager.getLastKnownLocation(provider)?.let{last->listener.onLocationChanged(last)}
                        manager.requestLocationUpdates(provider,3000L,0f,listener,Looper.getMainLooper())
                    }
                    if(locationFix==null)locationError="Waiting for a fresh location fix…"
                }catch(_:SecurityException){locationError="Location permission is needed while navigation is active."}
            }
            fun stopListening() {
                if(listening){runCatching{manager.removeUpdates(listener)};listening=false;locationFix=null}
            }
            val observer=LifecycleEventObserver{_,event->when(event){
                Lifecycle.Event.ON_START->startListening()
                Lifecycle.Event.ON_STOP->stopListening()
                else->Unit
            }}
            lifecycleOwner.lifecycle.addObserver(observer)
            if(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))startListening()
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                stopListening()
            }
        }
    }

    LaunchedEffect(navigating,currentLocation,route,target?.id,routeLoading,routeRequestRevision) {
        val point=currentLocation;val stop=target
        if(!navigating||point==null||stop==null||routeLoading)return@LaunchedEffect
        val currentRoute=route
        val offRoute=currentRoute?.let{distanceMeters(point,it.geometry[nearestRoutePointIndex(it,point)])}?:Double.MAX_VALUE
        offRouteSamples=if(currentRoute!=null&&offRoute>120.0)offRouteSamples+1 else 0
        val shouldRequest=currentRoute==null||offRouteSamples>=3
        val now=SystemClock.elapsedRealtime()
        if(shouldRequest&&(lastRouteRequest==0L||now-lastRouteRequest>=30000L)) {
            val request=onRequestRoute
            if(request==null)return@LaunchedEffect
            val revision = routeRequestRevision
            routeLoading=true;routeError=null;lastRouteRequest=now
            request(tripId,stop.id,point){result->
                if (revision != routeRequestRevision) return@request
                if(routingAvailable&&navigating&&selectedStopId==stop.id) {
                    result.onSuccess {json->
                        runCatching{DriverRoute.fromJson(json)}.onSuccess {fresh->route=fresh;routeError=null;offRouteSamples=0}
                            .onFailure {routeError="The route service returned unusable directions."}
                    }.onFailure {error->routeError=error.message?.take(220)?:"Route guidance is unavailable. Use your map app or contact dispatch."}
                }
                routeLoading=false
            }
        }
    }

    val nearest=route?.let{currentLocation?.let{point->nearestRoutePointIndex(it,point)}}
    val nextManeuver=nearest?.let{index->route?.let{activeRoute->activeRoute.maneuvers.firstOrNull{it.endShapeIndex>index}?:activeRoute.maneuvers.lastOrNull()}}
    val maneuverDistance=route?.let{activeRoute->currentLocation?.let{point->
        nextManeuver?.let{step->activeRoute.geometry.getOrNull(step.endShapeIndex)?.let{distanceMeters(point,it).toInt()}}
    }}
    val remaining=route?.let{activeRoute->nearest?.let{remainingDurationSeconds(activeRoute,it)}}
    val eta=remaining?.let{seconds->DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Africa/Nairobi")).format(Instant.now().plusSeconds(seconds.toLong()))}
    val distanceLeft=route?.let{activeRoute->nearest?.let{remainingDistanceMeters(activeRoute,it)}}
    val accuracy = locationFix?.takeIf { it.hasAccuracy() }?.accuracy
    val atStop = accuracy != null && currentLocation != null && target != null &&
        distanceMeters(currentLocation, GeoPoint(target.latitude, target.longitude)) + accuracy < 35.0

    HorizontalDivider()
    Text("Navigation",style=MaterialTheme.typography.titleMedium)
    if(target!=null) {
        Text(if(phase=="in_progress")"Route destination · ${target.name}" else "Route pickup · ${target.name}")
        NavigationMap(target,currentLocation,locationFix,route,Modifier.fillMaxWidth().height(300.dp))
        Text("© OpenStreetMap contributors · OpenFreeMap",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!routingAvailable) {
            Text("In-app directions are unavailable. Open Google Maps for directions to the assigned stop.",style=MaterialTheme.typography.bodySmall)
        } else if(!navigating)Button(onClick={disclosure=true},modifier=Modifier.fillMaxWidth()) {
            Icon(MireliIcons.LocationOn,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Start in-app navigation")
        } else {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick={navigating=false;routeLoading=false;route=null;locationFix=null;lastRouteRequest=0L;routeRequestRevision++},modifier=Modifier.weight(1f)){Text("End navigation")}
                OutlinedButton(onClick={route=null;routeError=null;offRouteSamples=0;lastRouteRequest=0L;routeRequestRevision++},enabled=!routeLoading,modifier=Modifier.weight(1f)){
                    Text(if(route==null)"Try route again" else "Recalculate")
                }
            }
            if(routeLoading)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(atStop)Text("You are at the assigned stop.",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            else nextManeuver?.let{maneuver->ManeuverCard(maneuver,maneuverDistance,route!!)}
            route?.let{Text("${distanceLabel(distanceLeft?:it.distanceMeters)} remaining · estimated arrival ${eta?:"—"} EAT · traffic is not live",style=MaterialTheme.typography.bodySmall)}
            locationError?.let{Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)}
            routeError?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        }
        TextButton(onClick={DriverNavigation.directions(target)?.let{failed=!navigate(it)}},modifier=Modifier.fillMaxWidth()) {
            Text("Open ${if(phase=="in_progress")"destination" else "pickup"} in Google Maps")
        }
        if(failed)Text("No maps app or browser could open. Install one and try again.",color=MaterialTheme.colorScheme.error)
    } else Text("Confirm the next pickup or drop-off in the manifest, then select the correct stop. Contact dispatch if the location is unclear.")

    val validStops = ordered.filter { it.valid }
    if (validStops.size > 1 || (target == null && validStops.isNotEmpty())) {
        Text("Route stops", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(validStops, key = { it.id }) { stop ->
                FilterChip(selected = stop.id == target?.id, onClick = { selectedStopId = stop.id }, label = { Text(stop.name) })
            }
        }
    }

    if(disclosure&&routingAvailable)AlertDialog(onDismissRequest={disclosure=false},title={Text("Allow location for navigation")},
        text={Text("Mireli uses your device location to show your position on the map and calculate directions to the selected trip stop. Your location is sent to Mireli’s routing service only when requesting or recalculating a route. The app does not request background location permission." )},
        confirmButton={TextButton(onClick={
            disclosure=false
            if(hasLocationPermission)navigating=true else permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
        }){Text("Continue")}},dismissButton={TextButton(onClick={disclosure=false}){Text("Cancel")}})
}

@Composable private fun ManeuverCard(maneuver:RouteManeuver,distance:Int?,route:DriverRoute) {
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("NEXT",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            Text(maneuver.instruction,style=MaterialTheme.typography.titleMedium)
            Text("${distanceLabel(distance?:maneuver.distanceMeters)} · ${route.durationSeconds/60} min remaining on this route",style=MaterialTheme.typography.bodySmall)
        }
    }
}

private fun distanceLabel(meters:Int):String=if(meters>=1000)"%.1f km".format(meters/1000.0) else "$meters m"

@Composable fun PassengerAddressMap(address:String) {
    val context=LocalContext.current
    val url=DriverNavigation.addressSearch(address)?:return
    var confirm by remember{mutableStateOf(false)};var failed by remember{mutableStateOf(false)}
    TextButton(onClick={confirm=true}){Text("Find address in Maps")}
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Find the agreed address")},text={Text("This sends the address to Google Maps. Confirm the correct result with the passenger before driving; an address search is not a verified pickup pin.")},confirmButton={TextButton(onClick={confirm=false;failed=!openMaps(context,url)}){Text("Open Maps")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
    if(failed)Text("No maps app or browser could open.",color=MaterialTheme.colorScheme.error)
}
