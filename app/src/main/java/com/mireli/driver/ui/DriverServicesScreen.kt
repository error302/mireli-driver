package com.mireli.driver.ui
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.time.Instant
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mireli.driver.BuildConfig
import com.mireli.driver.DriverServiceState
import com.mireli.driver.DriverServicesViewModel
import com.mireli.driver.domain.NavigationStop
import com.mireli.driver.domain.OnboardingPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

private fun JSONArray.objects()=(0 until length()).map {getJSONObject(it)}
private fun policyLink(raw: String): String? = runCatching {
    URI(raw).takeIf { it.scheme == "https" && it.host != null && it.userInfo == null }?.toASCIIString()
}.getOrNull()
@Composable fun DriverServicesScreen(onClose:(()->Unit)?=null,vm:DriverServicesViewModel=viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var email by rememberSaveable {mutableStateOf("")};var code by remember {mutableStateOf("")}
    var section by rememberSaveable {mutableIntStateOf(4)};var accepted by rememberSaveable {mutableStateOf(false)}
    var supportCategory by rememberSaveable {mutableStateOf("trip")};var supportMessage by remember {mutableStateOf("")}
    var payoutName by remember {mutableStateOf("")};var payoutAcknowledged by remember {mutableStateOf(false)}
    var tripHistory by rememberSaveable {mutableStateOf(false)}
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, vm, state.signedIn, section) {
        if (state.signedIn && section in listOf(3, 4, 5)) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    vm.refreshAssignments()
                    delay(30_000)
                }
            }
        }
    }
    var clock by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(state.challengeId,state.resendAt){code="";while(state.challengeId!=null&&clock<state.resendAt){clock=System.currentTimeMillis();delay(1000)};clock=System.currentTimeMillis()}
    var selectedType by rememberSaveable {mutableStateOf<String?>(null)};var selectedExpiry by rememberSaveable {mutableStateOf("")}
    var previouslySignedIn by remember {mutableStateOf(false)}
    LaunchedEffect(state.signedIn) {
        if(previouslySignedIn && !state.signedIn) {
            email="";code="";supportMessage="";payoutName="";payoutAcknowledged=false
            supportCategory="trip";accepted=false;selectedType=null;selectedExpiry="";tripHistory=false;section=4
        }
        previouslySignedIn=state.signedIn
    }
    val uriHandler=LocalUriHandler.current
    val context = LocalContext.current
    var showSupportAddress by rememberSaveable { mutableStateOf(false) }
    val routeRequest: RouteRequest? = if (state.navigationOpen) vm::navigationRoute else null
    val listState=rememberLazyListState();val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    var lastListSection by rememberSaveable { mutableIntStateOf(-2) }
    LaunchedEffect(state.signedIn, section) {
        val visibleSection = if (state.signedIn) section else -1
        if (lastListSection != visibleSection) {
            lastListSection = visibleSection
            focus.clearFocus()
            keyboard?.hide()
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(state.supportSent){if(state.supportSent>0){supportMessage="";focus.clearFocus();keyboard?.hide()}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->val type=selectedType;selectedType=null;if(uri!=null&&type!=null)vm.upload(type,selectedExpiry,uri)}
    val onboarding=state.onboarding;val app=onboarding?.getJSONObject("application")
    val privacyLink = policyLink(onboarding?.optString("privacyUrl").orEmpty())
    val termsLink = policyLink(onboarding?.optString("termsUrl").orEmpty())
    val policiesAvailable = BuildConfig.DRIVER_SERVICE_TEST || (privacyLink != null && termsLink != null)
    var policyLinkError by remember { mutableStateOf(false) }
    LaunchedEffect(onboarding?.optString("policyVersion"), privacyLink, termsLink) {
        accepted = false
        policyLinkError = false
    }
    if (!state.signedIn) {
        DriverSignInScreen(
            state = state,
            email = email,
            onEmailChange = { email = it },
            code = code,
            onCodeChange = { code = it },
            onRequestCode = { vm.requestCode(email) },
            onVerifyCode = { vm.verifyCode(code) },
            onChangeEmail = { vm.changeEmail(); code = "" },
            onRetry = vm::refreshServiceStatus,
            resendInSeconds = ((state.resendAt - clock + 999) / 1000).coerceAtLeast(0),
            onClose = onClose,
            supportAddressFailed = showSupportAddress,
            onSupport = {
                val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", state.supportEmail, null))
                showSupportAddress = runCatching { context.startActivity(emailIntent) }.isFailure
            }
        )
        return
    }
    var navTarget by remember{mutableStateOf<NavTarget?>(null)}
    // Background trip tracking follows the active trip no matter which section is open: it starts
    // when an assignment enters an active phase and stops when none remain.
    val trackingContext = androidx.compose.ui.platform.LocalContext.current
    val trackingTrip = remember(state.trips) {
        state.trips?.optJSONArray("trips")?.objects()
            ?.filter { it.optString("phase") in listOf("accepted", "at_pickup", "in_progress") }
            ?.minWithOrNull(transferOrder())
    }
    androidx.compose.runtime.LaunchedEffect(trackingTrip?.optString("id")) {
        val id = trackingTrip?.optString("id")?.takeIf { it.isNotBlank() }
        if (id == null) {
            com.mireli.driver.service.TripTracking.stop(trackingContext)
        } else if (androidx.core.content.ContextCompat.checkSelfPermission(
                trackingContext, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            com.mireli.driver.service.TripTracking.sync(
                trackingContext, id, trackingTrip?.terminusName()
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
    Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
        listOf(
            4 to ("Home" to MireliIcons.Home),
            3 to ("Trips" to MireliIcons.CarFront),
            5 to ("Trains" to MireliIcons.TrainFront),
            1 to ("Earnings" to MireliIcons.AccountBalanceWallet),
            0 to ("Account" to MireliIcons.PersonOutline)
        ).forEach { (target, item) ->
            NavigationBarItem(selected=section==target,onClick={section=target},
                icon={Icon(item.second,null)},label={Text(item.first)},
                colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer))
        }
    }}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().testTag("driver_service_list"),state=listState,contentPadding=PaddingValues(horizontal=18.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { if(section==4)DriverHomeTopBar{section=2} else DriverBrandHeader("Driver account") }
            if(onClose!=null)item {TextButton(onClick=onClose){Text("Back to preview")}}
            if(BuildConfig.DRIVER_SERVICE_TEST)item {Card {Text("STAGING · Simulated sign-in and payouts",Modifier.padding(16.dp))}}
            if(state.loading)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            state.error?.let {error->item {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)){Text(error,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onErrorContainer)}} }
                item {FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    OutlinedButton(onClick=vm::refresh,enabled=!state.loading) {
                        Icon(MireliIcons.Refresh,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Refresh account")
                    }
                    TextButton(onClick={com.mireli.driver.service.TripTracking.stop(trackingContext);vm.signOut()},enabled=!state.loading){Text("Sign out")}
                }}
                if(state.pendingTripAction)item {Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("One saved trip action needs confirmation",style=MaterialTheme.typography.titleMedium)
                    Text("The app keeps the same action ID after a connection failure or restart. Check it before making another trip change.")
                    Button(onClick=vm::retryTripAction,enabled=!state.loading){Text("Check saved action")}
                }}}
                if(section in listOf(4,3,5) && !state.loading && !state.tripDataCurrent)item {
                    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        DriverNotice(MireliIcons.Refresh,"Assignments need a refresh",
                            "Refresh to check for changes before updating a trip.")
                        OutlinedButton(onClick=vm::refresh,shape=RoundedCornerShape(12.dp)) {Text("Refresh assignments")}
                    }
                }
                if(section==4) {
                    val trips=state.trips?.optJSONArray("trips")?.objects()
                    val activeTrip=trips?.filter{it.optString("phase") in listOf("accepted","at_pickup","in_progress")}
                        ?.minWithOrNull(transferOrder())
                    item {
                        DriverHomeDashboard(
                            name = onboarding?.optJSONObject("profile")?.optString("fullName")
                                ?.takeIf{it.isNotBlank()&&it!="New applicant"},
                            eligible = state.eligibility?.optBoolean("eligible")==true,
                            trips = trips,
                            earnings = state.earnings,
                            activeNavigation = activeTrip?.let { trip -> {
                                Surface(shape=RoundedCornerShape(MireliDesign.Radius.card),color=MaterialTheme.colorScheme.surface) {
                                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                        TripNavigation(trip,onRequestRoute=routeRequest)
                                        OutlinedButton(onClick={tripHistory=false;section=3},modifier=Modifier.fillMaxWidth()){Text("Open journey & passengers")}
                                    }
                                }
                            } },
                            onOpenTrips = { tripHistory=false; section=3 },
                            onOpenTrains = { section=5 },
                            onOpenEarnings = { section=1 },
                            onOpenAccount = { section=0 },
                            onOpenTrip = { tripHistory=false; section=3 },
                            onStartNavigation = { tripId, stop -> navTarget=NavTarget(tripId,stop) },
                            onOpenMap = { navTarget=NavTarget(null,null) }
                        )
                    }
                }else if(section==5) {
                    val allTrips=state.trips?.optJSONArray("trips")?.objects()
                    item {
                        StationArrivalsPanel(
                            trips = allTrips,
                            eligible = state.eligibility?.optBoolean("eligible")==true,
                            stationHint = onboarding?.optJSONObject("profile")?.optString("stationHint")?.takeIf{it.isNotBlank()},
                            onOpenTrip = { tripHistory=false; section=3 },
                            onOpenTrips = { tripHistory=false; section=3 }
                        )
                    }
                }else if(section==0&&onboarding!=null&&app!=null) {
                    item {DriverHeading("Driver account","Manage your profile, vehicle and documents.")}
                    item {DriverStepProgress(if(app.getString("status")=="approved")2 else 1,listOf("Email verified","Documents","Review"))}
                    item {Card {Column(Modifier.padding(16.dp)) {
                        Text("Application: ${app.getString("status").replace('_',' ')}",style=MaterialTheme.typography.titleMedium)
                        Text(if(state.eligibility?.optBoolean("eligible")==true)"Eligible for new assignments" else "Approval required before new assignments")
                        app.optString("reviewReason").takeIf{it.isNotBlank()&&it!="null"}?.let{Text(it)}
                        if (app.getString("status") == "approved") {
                            Text("Saving profile changes or replacing evidence sends the application back for review.")
                        }
                    }}}
                    item {ServerProfile(onboarding,state.loading,vm::profile)}
                    item {Text("Driver documents",style=MaterialTheme.typography.titleLarge);Text("Documents go to Mireli's private review service. Uploading does not grant approval. This server accepts files up to ${onboarding.getInt("maxDocumentBytes")/1024/1024} MB.")}
                    val documents=app.getJSONArray("documents").objects()
                    items(onboarding.getJSONArray("requirements").objects(),key={it.getString("id")}) {requirement->
                        val id=requirement.getString("id");val evidence=documents.firstOrNull{it.getString("type")==id}
                        var expiry by rememberSaveable(id,app.getInt("version")){mutableStateOf(evidence?.optString("expiresAt")?.take(10)?.takeIf{it!="null"}?:"")}
                        Card(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(requirement.getString("title"),style=MaterialTheme.typography.titleMedium)
                            Text(if(requirement.getBoolean("required"))"Required" else "Optional, if requested")
                            Text(if(evidence==null)"Not uploaded" else "${evidence.getString("state").replace('_',' ')} · scan ${evidence.getString("scanState").replace('_',' ')}")
                            evidence?.optString("reviewReason")?.takeIf{it.isNotBlank()&&it!="null"}?.let{Text(it)}
                            if(requirement.getBoolean("expires"))OutlinedTextField(expiry,{expiry=it.take(10)},label={Text("Expiry · YYYY-MM-DD")},singleLine=true,enabled=!state.loading)
                            OutlinedButton(onClick={selectedType=id;selectedExpiry=expiry;picker.launch(arrayOf("application/pdf","image/jpeg","image/png"))},enabled=policiesAvailable&&!state.loading&&app.getString("status") in listOf("draft","changes_requested","approved")) {Text(if(evidence==null)"Upload document" else "Replace evidence")}
                        }}
                    }
                    item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("Driver terms" to termsLink, "Privacy notice" to privacyLink).forEach { (label, url) ->
                            if (url != null) TextButton(onClick={policyLinkError = runCatching { uriHandler.openUri(url) }.isFailure}) {Text(label)}
                        }
                        if (policyLinkError) Text("A browser is needed to open these policies.",color=MaterialTheme.colorScheme.error)
                        if (!policiesAvailable) Text("Driver terms and the privacy notice are required before uploading or submitting. Contact Mireli Support.")
                        Row(Modifier.fillMaxWidth().toggleable(accepted,enabled=policiesAvailable&&!state.loading,role=Role.Checkbox,onValueChange={accepted=it})){Checkbox(accepted,null,enabled=policiesAvailable&&!state.loading);Text(if(BuildConfig.DRIVER_SERVICE_TEST)"I acknowledge this synthetic application and test checklist." else "I have read the current driver terms and privacy notice.")}
                        Button(onClick={vm.submit(accepted)},enabled=policiesAvailable&&!state.loading&&accepted&&app.getString("status") in listOf("draft","changes_requested"),modifier=Modifier.fillMaxWidth()){Text("Submit for compliance review")}
                    }}
                }else if(section==1) {
                    item {PremiumHero("Earnings & payouts","Track each settlement and your payout destination.",eyebrow="Earnings")}
                    state.earnings?.let{statement->
                        item {DriverEarningsHero("Confirmed paid earnings",statement.getJSONArray("totals").objects().filter{it.getString("status")=="completed"}.sumOf{it.getLong("netMinor")},"Returned statement totals · pending transfers are shown separately below")}
                        item {Text("Payout destination: ${statement.optString("destinationMasked","Not set")}")}
                        val destination=statement.getJSONObject("destination")
                        item {Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Text("Beneficiary: ${destination.getString("status").replace('_',' ')}",style=MaterialTheme.typography.titleMedium)
                            Text("Payout setup uses the phone recorded in your driver profile. Email verification does not verify an M-Pesa account. Finance checks your account name and identity evidence before approval. Use Support if you need a different phone.")
                            destination.optString("reviewNote").takeIf{it.isNotBlank()&&it!="null"}?.let{Text(it)}
                            if(destination.getString("status") in listOf("not_configured","rejected")) {
                                OutlinedTextField(payoutName,{payoutName=it.take(100)},label={Text("Name on your M-Pesa account")},enabled=!state.loading,modifier=Modifier.fillMaxWidth())
                                Row(Modifier.fillMaxWidth().toggleable(payoutAcknowledged,enabled=!state.loading,role=Role.Checkbox,onValueChange={payoutAcknowledged=it})){Checkbox(payoutAcknowledged,null);Text("This profile phone is my M-Pesa account.")}
                                Button(onClick={vm.payoutDestination(payoutName)},enabled=!state.loading&&payoutAcknowledged&&payoutName.trim().length>=3){Text("Request beneficiary review")}
                            }
                        }}}
                        items(statement.getJSONArray("totals").objects()){total->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(total.getString("status").replace('_',' '));Text(money(total.getLong("netMinor")),style=MaterialTheme.typography.headlineSmall)}}}
                        items(statement.getJSONArray("lines").objects(),key={it.getString("id")}){line->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(line.getString("routeName"),style=MaterialTheme.typography.titleMedium);Text("${line.getString("serviceType")} · ${line.getString("status").replace('_',' ')}")
                            Text("Gross ${money(line.getLong("grossMinor"))} · Commission ${money(line.getLong("commissionMinor"))}")
                            Text("Home surcharge ${money(line.getLong("surchargeMinor"))} · Net ${money(line.getLong("netMinor"))}")
                            line.optString("failureReason").takeIf{it.isNotBlank()&&it!="null"}?.let{Text(it)}
                        }}}
                        if(statement.getJSONArray("lines").length()==0)item {Text("No settlement records yet. Completed eligible journeys will appear here.")}
                    }
                }else if(section==3) {
                    val allTrips=state.trips?.getJSONArray("trips")?.objects()?:emptyList()
                    val visibleTrips=if(tripHistory)allTrips.filterNot{it.isActiveTransfer()} else allTrips.filter{it.isActiveTransfer()}.sortedWith(transferOrder())
                    item {
                        PremiumHero("Trips","Mombasa SGR transfers assigned to you",eyebrow="Assignments")
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected=!tripHistory,onClick={tripHistory=false},label={Text("Assignments")},
                                leadingIcon={Icon(MireliIcons.Calendar,null,Modifier.size(16.dp))})
                            FilterChip(selected=tripHistory,onClick={tripHistory=true},label={Text("History")},
                                leadingIcon={Icon(MireliIcons.Clock,null,Modifier.size(16.dp))})
                        }
                    }
                    if(!tripHistory&&state.eligibility?.optBoolean("eligible")!=true)item {
                        DriverNotice(MireliIcons.VerifiedUser,"Driver approval needed",
                            "You need current document approval to accept assignments, board passengers or start a journey. Check Account for your application status.")
                    }
                    items(visibleTrips,key={it.getString("id")}){trip->
                        LiveTripCard(trip,state.loading||state.pendingTripAction||!state.tripDataCurrent,
                            state.eligibility?.optBoolean("eligible")==true,vm::tripCommand,routeRequest)
                    }
                    if(visibleTrips.isEmpty())item {Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        Icon(if(tripHistory)MireliIcons.Clock else MireliIcons.Calendar,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(28.dp))
                        Text(when {
                            state.trips==null -> "Trip list unavailable"
                            tripHistory -> "No past trips"
                            else -> "No current assignments"
                        },style=MaterialTheme.typography.titleLarge)
                        Text(when {
                            state.trips==null -> "Refresh your account to check for transfers."
                            tripHistory -> "Completed, cancelled and declined trips appear here."
                            state.eligibility?.optBoolean("eligible")!=true -> "After approval, your assigned transfers will appear here."
                            else -> "Refresh your account to check for new assignments."
                        },color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(!tripHistory&&state.eligibility?.optBoolean("eligible")!=true)OutlinedButton(onClick={section=0}){Text("Continue onboarding")}
                    }}}
                }else if(section==2) {
                    item {DriverHeading("Driver support","Get help with your account, documents or payouts.");Text("For emergencies, contact local emergency services directly.")}
                    if(state.supportSent>0)item {Text("Support case sent. Mireli's replies appear below.",color=MaterialTheme.colorScheme.primary)}
                    item {FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("trip","account","documents","payout","safety").forEach{category->FilterChip(selected=supportCategory==category,onClick={supportCategory=category},label={Text(category.replaceFirstChar{it.uppercase()})})}}}
                    item {OutlinedTextField(supportMessage,{supportMessage=it.take(2000)},label={Text("How can we help?")},modifier=Modifier.fillMaxWidth(),minLines=3,enabled=!state.loading)}
                    item {Button(onClick={vm.support(supportCategory,supportMessage)},enabled=!state.loading&&supportMessage.trim().length>=10,modifier=Modifier.fillMaxWidth()){Text("Send support case")}}
                    item {Text("Keep ID numbers and document images out of support messages. Use the private document upload flow.",style=MaterialTheme.typography.bodySmall)}
                    items(state.support?.getJSONArray("cases")?.objects()?:emptyList(),key={it.getString("id")}){case->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("${case.getString("category")} · ${case.getString("status")}",style=MaterialTheme.typography.titleMedium);Text("Reference: ${case.getString("id")}",style=MaterialTheme.typography.bodySmall);Text(case.getString("message"))
                        case.optString("reply").takeIf{it.isNotBlank()&&it!="null"}?.let{Text("Mireli: $it")}
                    }}}
                }
                if(section==0)item {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Help",style=MaterialTheme.typography.titleLarge)
                        DriverActionTile("Driver support","Trips, documents and payout cases",MireliIcons.HeadsetMic) { section=2 }
                        AppearanceToggle(showSettings=true)
                    }
                }
                if(section==0)item {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text("Privacy and account deletion",style=MaterialTheme.typography.titleLarge)
                        DriverActionTile("Request account deletion","Review a request before sending it to Mireli.",MireliIcons.PrivacyTip) {
                            supportCategory="account"
                            supportMessage="I am requesting deletion of my Mireli Driver account. Please let me know if you need any further information to handle my request."
                            section=2
                        }
                    }
                }
        }
    }
    navTarget?.let{ target->
        // The sheet carries the next scheduled SGR departure whenever dispatch has one for this
        // driver. It is a scheduled time from the assignment, never a live train position.
        val clock = rememberNow()
        val nextDeparture = state.trips?.optJSONArray("trips")?.objects().orEmpty()
            .mapNotNull { trip -> trip.departureInstant()?.takeIf { it.isAfter(clock) } }
            .minOrNull()
        DriverNavigationScreen(
            tripId=target.tripId, stop=target.stop, onRequestRoute=routeRequest, onClose={navTarget=null},
            nextTrain = nextDeparture?.let { countdown(clock, it) }
        )
    }
    }
}

private data class NavTarget(val tripId:String?,val stop:NavigationStop?)

@Composable private fun ServerProfile(data:JSONObject,busy:Boolean,save:(String,String,String,String,String,Boolean,String)->Unit) {
    val profile=data.getJSONObject("profile");val app=data.getJSONObject("application");val version=app.getInt("version")
    val storedName = profile.getString("fullName").takeUnless { it == "New applicant" }.orEmpty()
    val storedPhone = profile.opt("phone") as? String ?: ""
    var name by rememberSaveable(version){mutableStateOf(storedName)}
    var contactPhone by rememberSaveable(version){mutableStateOf(storedPhone)}
    var plate by rememberSaveable(version){mutableStateOf(profile.getString("plate"))};var capacity by rememberSaveable(version){mutableStateOf(profile.getInt("capacity").toString())}
    var licence by rememberSaveable(version){mutableStateOf(app.getString("licenceClass"))};var cab by rememberSaveable(version){mutableStateOf(profile.getString("cabType"))}
    var owner by rememberSaveable(version){mutableStateOf(app.getBoolean("ownsVehicle"))}
    val editable=!busy&&app.getString("status") in listOf("draft","changes_requested","approved")
    val phone = OnboardingPolicy.normalizePhone(contactPhone)
    val seatCount = capacity.toIntOrNull()
    val valid = phone != null && name.trim().length >= 3 && seatCount in 1..60 &&
        plate.trim().matches(Regex("[A-Z0-9 -]{4,15}")) && licence.isNotBlank() && cab.trim().length >= 2
    val changed = name.trim() != storedName || phone != OnboardingPolicy.normalizePhone(storedPhone) ||
        plate.trim() != profile.getString("plate") || seatCount != profile.getInt("capacity") ||
        licence.trim() != app.getString("licenceClass") || cab.trim() != profile.getString("cabType") ||
        owner != app.getBoolean("ownsVehicle")
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Driver & vehicle",style=MaterialTheme.typography.titleLarge)
        OutlinedTextField(contactPhone,{contactPhone=it.take(20)},label={Text("Kenyan contact phone")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),singleLine=true,enabled=editable&&storedPhone.isBlank(),modifier=Modifier.fillMaxWidth())
        if (storedPhone.isNotBlank()) Text("Contact Support to change this number.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(name,{name=it.take(100)},label={Text("Driver full name")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth())
        key(version) { VehicleTypePicker(cab, { cab = it }, enabled = editable) }
        OutlinedTextField(plate,{plate=it.take(15).uppercase()},label={Text("Registration plate")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(capacity,{capacity=it.filter(Char::isDigit).take(2)},label={Text("Passenger seats")},
            supportingText={Text("Use the approved capacity, excluding the driver.")},
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(licence,{licence=it.take(20)},label={Text("Licence class")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().toggleable(owner,enabled=editable,role=Role.Checkbox,onValueChange={owner=it})){Checkbox(owner,null);Text("I own this vehicle")}
        Button(onClick={save(name,plate,capacity,licence,cab,owner,contactPhone)},enabled=editable&&valid&&changed,modifier=Modifier.fillMaxWidth()){Text("Save profile to Mireli")}
    }
}
