package com.mireli.driver.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mireli.driver.BuildConfig
import com.mireli.driver.domain.DriverNavigation
import com.mireli.driver.domain.DriverRoute
import com.mireli.driver.domain.GeoPoint
import com.mireli.driver.domain.NavigationStop
import com.mireli.driver.domain.RouteManeuver
import com.mireli.driver.domain.distanceMeters
import com.mireli.driver.domain.nearestRoutePointIndex
import com.mireli.driver.domain.remainingDistanceMeters
import com.mireli.driver.domain.remainingDurationSeconds
import org.json.JSONObject
import org.maplibre.android.MapLibre
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

typealias RouteRequest=(tripId:String,stageId:String,origin:GeoPoint,onComplete:(Result<JSONObject>)->Unit)->Unit

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
    var lastRouteRequest by rememberSaveable(tripId){mutableLongStateOf(0L)}
    var routeRequestRevision by remember(tripId){mutableIntStateOf(0)}
    var offRouteSamples by remember(tripId){mutableIntStateOf(0)}
    var currentLocation by remember(tripId){mutableStateOf<GeoPoint?>(null)}
    val navigate=onNavigate?:{url:String->openMaps(context,url)}
    val stages=trip.optJSONArray("stages")
    val stops=(0 until (stages?.length()?:0)).map{index->val s=stages!!.getJSONObject(index)
        NavigationStop(s.optString("id",index.toString()),s.optString("name"),s.optDouble("latitude",Double.NaN),s.optDouble("longitude",Double.NaN))}
    val direction=trip.optString("direction");val phase=trip.optString("phase")
    val defaultTarget=DriverNavigation.primaryStop(stops,direction,phase)
    var selectedStopId by rememberSaveable(tripId){mutableStateOf(defaultTarget?.id)}
    LaunchedEffect(tripId,defaultTarget?.id){selectedStopId=defaultTarget?.id;route=null;routeError=null;offRouteSamples=0;lastRouteRequest=0L;routeRequestRevision++}
    val target=stops.firstOrNull{it.id==selectedStopId}?.takeIf{it.valid}
    val ordered=DriverNavigation.orderedStops(stops,direction)
    val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){result->
        val allowed=result[Manifest.permission.ACCESS_FINE_LOCATION]==true||result[Manifest.permission.ACCESS_COARSE_LOCATION]==true
        if(allowed){locationError=null;navigating=true}else locationError="Location access is needed for live driver position and route guidance."
    }
    val hasLocationPermission=context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED

    DisposableEffect(tripId,navigating,hasLocationPermission) {
        if(!navigating||!hasLocationPermission){onDispose{} } else {
            val manager=context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var listening=false
            val listener=object:LocationListener {
                override fun onLocationChanged(location:Location){
                    val point=GeoPoint(location.latitude,location.longitude)
                    if(point.valid){currentLocation=point;locationError=null}
                }
                @Deprecated("Deprecated by Android") override fun onStatusChanged(provider:String?,status:Int,extras:Bundle?)=Unit
                override fun onProviderEnabled(provider:String) {locationError=null}
                override fun onProviderDisabled(provider:String) {if(currentLocation==null)locationError="Turn on device location to start route guidance."}
            }
            fun startListening() {
                if(listening)return
                listening=true
                try {
                    val providers=if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
                        listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER) else listOf(LocationManager.NETWORK_PROVIDER)
                    providers.forEach {provider->
                        manager.getLastKnownLocation(provider)?.let{last->listener.onLocationChanged(last)}
                        if(manager.isProviderEnabled(provider))manager.requestLocationUpdates(provider,3000L,8f,listener,Looper.getMainLooper())
                    }
                    if(currentLocation==null)locationError="Waiting for a GPS fix…"
                }catch(_:SecurityException){locationError="Location permission is needed while navigation is active."}
            }
            fun stopListening() {
                if(listening){runCatching{manager.removeUpdates(listener)};listening=false;currentLocation=null}
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
        val now=System.currentTimeMillis()
        if(shouldRequest&&now-lastRouteRequest>=30000L) {
            val request=onRequestRoute
            if(request==null){routeError="In-app route service is not connected on this build.";return@LaunchedEffect}
            routeLoading=true;routeError=null;lastRouteRequest=now
            request(tripId,stop.id,point){result->
                if(navigating&&selectedStopId==stop.id) {
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
    val nextManeuver=route?.let{activeRoute->activeRoute.maneuvers.firstOrNull{it.endShapeIndex> (nearest?:-1)}?:activeRoute.maneuvers.lastOrNull()}
    val maneuverDistance=route?.let{activeRoute->currentLocation?.let{point->
        nextManeuver?.let{step->activeRoute.geometry.getOrNull(step.endShapeIndex)?.let{distanceMeters(point,it).toInt()}}
    }}
    val remaining=route?.let{activeRoute->nearest?.let{remainingDurationSeconds(activeRoute,it)}}
    val eta=remaining?.let{seconds->DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Africa/Nairobi")).format(Instant.now().plusSeconds(seconds.toLong()))}
    val distanceLeft=route?.let{activeRoute->nearest?.let{remainingDistanceMeters(activeRoute,it)}}
    val atStop=currentLocation?.let{point->target?.let{distanceMeters(point,GeoPoint(it.latitude,it.longitude))<35.0}}==true

    HorizontalDivider()
    Text("Navigation",style=MaterialTheme.typography.titleMedium)
    if(target!=null) {
        Text(if(phase=="in_progress")"Route destination · ${target.name}" else "Route pickup · ${target.name}")
        NavigationMap(target,currentLocation,route,Modifier.fillMaxWidth().height(300.dp))
        Text("© OpenStreetMap contributors · OpenFreeMap",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(ordered.size>1) {
            Text("Assigned route stops",style=MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                items(ordered,key={it.id}) {stop->FilterChip(selected=stop.id==target.id,onClick={selectedStopId=stop.id;route=null;routeError=null;offRouteSamples=0;lastRouteRequest=0L;routeRequestRevision++},label={Text(stop.name)})}
            }
        }
        if(!navigating)Button(onClick={disclosure=true},modifier=Modifier.fillMaxWidth()) {
            Icon(MireliIcons.LocationOn,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Start in-app navigation")
        } else {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick={navigating=false;routeLoading=false;route=null;currentLocation=null},modifier=Modifier.weight(1f)){Text("End navigation")}
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
    } else Text("The route location is missing. Contact dispatch before travelling.")

    if(disclosure)AlertDialog(onDismissRequest={disclosure=false},title={Text("Allow location for navigation")},
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

@Composable private fun NavigationMap(target:NavigationStop,location:GeoPoint?,route:DriverRoute?,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    var map by remember{mutableStateOf<MapLibreMap?>(null)}
    var loaded by remember{mutableStateOf(false)}
    var failed by remember{mutableStateOf(false)}
    var targetMarker by remember(map){mutableStateOf<Marker?>(null)}
    var driverMarker by remember(map){mutableStateOf<Marker?>(null)}
    var routeLine by remember(map){mutableStateOf<Polyline?>(null)}
    var cameraInitialized by remember(map){mutableStateOf(false)}
    val mapView=remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context)
    }
    DisposableEffect(mapView,owner) {
        var started=false;var resumed=false
        fun start(){if(!started){mapView.onStart();started=true}}
        fun resume(){start();if(!resumed){mapView.onResume();resumed=true}}
        fun pause(){if(resumed){mapView.onPause();resumed=false}}
        fun stop(){pause();if(started){mapView.onStop();started=false}}
        mapView.onCreate(Bundle())
        val observer=LifecycleEventObserver{_,event->when(event){
            Lifecycle.Event.ON_START->start();Lifecycle.Event.ON_RESUME->resume();Lifecycle.Event.ON_PAUSE->pause();Lifecycle.Event.ON_STOP->stop();else->Unit
        }}
        owner.lifecycle.addObserver(observer)
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))start()
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))resume()
        mapView.addOnDidFailLoadingMapListener{_->failed=true}
        mapView.getMapAsync{readyMap->
            map=readyMap
            readyMap.uiSettings.isLogoEnabled=true
            readyMap.uiSettings.isAttributionEnabled=true
            readyMap.setStyle(BuildConfig.MAP_STYLE_URL){loaded=true;failed=false}
        }
        onDispose{owner.lifecycle.removeObserver(observer);stop();mapView.onDestroy();map=null}
    }
    LaunchedEffect(map,loaded,target.id) {
        val ready=map?.takeIf{loaded}?:return@LaunchedEffect
        targetMarker?.let{runCatching{ready.removeAnnotation(it)}}
        targetMarker=ready.addMarker(MarkerOptions().position(LatLng(target.latitude,target.longitude)).title(target.name))
        if(!cameraInitialized){ready.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(target.latitude,target.longitude)).zoom(13.5).build()));cameraInitialized=true}
    }
    LaunchedEffect(map,loaded,location) {
        val ready=map?.takeIf{loaded}?:return@LaunchedEffect
        val point=location
        if(point==null){driverMarker?.let{runCatching{ready.removeAnnotation(it)}};driverMarker=null}
        else if(driverMarker==null){
            driverMarker=ready.addMarker(MarkerOptions().position(LatLng(point.latitude,point.longitude)).title("Your location"))
            if(!cameraInitialized){ready.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder().target(LatLng(point.latitude,point.longitude)).zoom(15.5).build()));cameraInitialized=true}
        }else driverMarker?.position=LatLng(point.latitude,point.longitude)
    }
    LaunchedEffect(map,loaded,route) {
        val ready=map?.takeIf{loaded}?:return@LaunchedEffect
        routeLine?.let{runCatching{ready.removeAnnotation(it)}};routeLine=null
        route?.takeIf{it.geometry.size>=2}?.let{activeRoute->
            val points=activeRoute.geometry.map{LatLng(it.latitude,it.longitude)}
            routeLine=ready.addPolyline(PolylineOptions().addAll(points).color(AndroidColor.rgb(0,128,120)).width(6f))
            val bounds=LatLngBounds.Builder().also{builder->points.forEach{point->builder.include(point)}}.build()
            ready.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,36))
        }
    }
    Box(modifier) {
        AndroidView(factory={mapView},modifier=Modifier.fillMaxSize())
        if(!loaded&&!failed)CircularProgressIndicator(Modifier.align(Alignment.Center))
        if(failed)Text("Map tiles are unavailable. Try again when you have a connection.",Modifier.align(Alignment.Center).padding(20.dp),color=MaterialTheme.colorScheme.error)
    }
}

@Composable fun PassengerAddressMap(address:String) {
    val context=LocalContext.current
    val url=DriverNavigation.addressSearch(address)?:return
    var confirm by remember{mutableStateOf(false)};var failed by remember{mutableStateOf(false)}
    TextButton(onClick={confirm=true}){Text("Find address in Maps")}
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Find the agreed address")},text={Text("This sends the address to Google Maps. Confirm the correct result with the passenger before driving; an address search is not a verified pickup pin.")},confirmButton={TextButton(onClick={confirm=false;failed=!openMaps(context,url)}){Text("Open Maps")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
    if(failed)Text("No maps app or browser could open.",color=MaterialTheme.colorScheme.error)
}
