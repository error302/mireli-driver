package com.mireli.driver.ui
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
private enum class DriverEntry { Welcome, SignIn, Register }
private fun policyLink(raw: String): String? = runCatching {
    URI(raw).takeIf { it.scheme == "https" && it.host != null && it.userInfo == null }?.toASCIIString()
}.getOrNull()
@Composable fun DriverServicesScreen(onClose:(()->Unit)?=null,vm:DriverServicesViewModel=viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val starting by vm.startupLoading.collectAsStateWithLifecycle()
    var entry by rememberSaveable { mutableStateOf(DriverEntry.Welcome) }
    var email by rememberSaveable {mutableStateOf("")};var code by remember {mutableStateOf("")}
    var section by rememberSaveable {mutableIntStateOf(4)};var accepted by rememberSaveable {mutableStateOf(false)}
    var supportCategory by rememberSaveable {mutableStateOf("trip")};var supportMessage by remember {mutableStateOf("")}
    var payoutName by remember {mutableStateOf("")};var payoutAcknowledged by remember {mutableStateOf(false)}
    var payoutPhone by rememberSaveable {mutableStateOf("")}
    var tripHistory by rememberSaveable {mutableStateOf(false)}
    var highlightedTripId by rememberSaveable { mutableStateOf<String?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, vm, state.signedIn, section) {
        if (state.signedIn && section in listOf(3, 4)) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    vm.refreshAssignments()
                    delay(60_000)
                }
            }
        }
    }
    var clock by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(state.challengeId,state.resendAt){code="";while(state.challengeId!=null&&clock<state.resendAt){clock=System.currentTimeMillis();delay(1000)};clock=System.currentTimeMillis()}
    var selectedType by rememberSaveable {mutableStateOf<String?>(null)};var selectedExpiry by rememberSaveable {mutableStateOf("")}
    var previouslySignedIn by remember {mutableStateOf(false)}
    LaunchedEffect(state.signedIn) {
        if (state.signedIn) {
            if (entry == DriverEntry.Register) section = 0
            entry = DriverEntry.Welcome
        }
        if(previouslySignedIn && !state.signedIn) {
            email="";code="";supportMessage="";payoutName="";payoutAcknowledged=false
            supportCategory="trip";accepted=false;selectedType=null;selectedExpiry="";tripHistory=false;highlightedTripId=null;section=4
            entry = DriverEntry.Welcome
        }
        previouslySignedIn=state.signedIn
    }
    val uriHandler=LocalUriHandler.current
    val context = LocalContext.current
    val earningsPrefs = remember { context.getSharedPreferences("earnings", android.content.Context.MODE_PRIVATE) }
    var hideBalance by rememberSaveable { mutableStateOf(earningsPrefs.getBoolean("hideBalance", false)) }
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
    val onboarding=state.onboarding;val app=onboarding?.optJSONObject("application")
    val privacyLink = policyLink(onboarding?.optString("privacyUrl").orEmpty())
    val termsLink = policyLink(onboarding?.optString("termsUrl").orEmpty())
    val policiesAvailable = BuildConfig.DRIVER_SERVICE_TEST || (privacyLink != null && termsLink != null)
    var policyLinkError by remember { mutableStateOf(false) }
    LaunchedEffect(onboarding?.optString("policyVersion"), privacyLink, termsLink) {
        accepted = false
        policyLinkError = false
    }
    if (starting) {
        DriverLaunchLoading()
        return
    }
    if (!state.signedIn) {
        if (entry == DriverEntry.Welcome && state.challengeId == null) {
            DriverWelcome(
                onSignIn = { entry = DriverEntry.SignIn }, onRegister = { entry = DriverEntry.Register },
                message = state.error, onClose = onClose
            )
            return
        }
        val backToWelcome = {
            vm.changeEmail()
            code = ""
            entry = DriverEntry.Welcome
        }
        BackHandler { if (!state.loading) backToWelcome() }
        DriverSignInScreen(
            state = state,
            email = email,
            onEmailChange = { email = it },
            code = code,
            onCodeChange = { code = it },
            onRequestCode = { vm.requestCode(email, registering = entry == DriverEntry.Register) },
            onVerifyCode = { vm.verifyCode(code) },
            onChangeEmail = { vm.changeEmail(); code = "" },
            onRetry = vm::refreshServiceStatus,
            resendInSeconds = ((state.resendAt - clock + 999) / 1000).coerceAtLeast(0),
            registering = entry == DriverEntry.Register,
            onBackToWelcome = backToWelcome,
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
    Scaffold(containerColor=MaterialTheme.colorScheme.surface,bottomBar={NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
        listOf(
            4 to ("Home" to MireliIcons.Home),
            3 to ("Trips" to MireliIcons.CarFront),
            1 to ("Earnings" to MireliIcons.AccountBalanceWallet),
            0 to ("Account" to MireliIcons.PersonOutline)
        ).forEach { (target, item) ->
            NavigationBarItem(selected=section==target,onClick={
                if (target==3 && section!=3) highlightedTripId=null
                section=target
            },
                icon={
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){
                        HorizontalDivider(Modifier.width(14.dp),thickness=2.dp,
                            color=if(section==target) MaterialTheme.colorScheme.secondary else Color.Transparent)
                        Icon(item.second,null)
                    }
                },label={Text(item.first)},
                colors=NavigationBarItemDefaults.colors(indicatorColor=Color.Transparent,
                    selectedIconColor=MaterialTheme.colorScheme.secondary,
                    selectedTextColor=MaterialTheme.colorScheme.secondary))
        }
    }}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().testTag("driver_service_list"),state=listState,contentPadding=PaddingValues(horizontal=18.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(section==4)item { DriverHomeTopBar(onOpenMap = { navTarget = NavTarget(null, null) }, onSupport = { section=2 }) }
            if(section==0)item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { DeparturePageTitle("Driver account") }
                    IconButton(onClick = vm::refresh, enabled = !state.loading) {
                        Icon(MireliIcons.Refresh, "Refresh account", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if(section==1)item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { DeparturePageTitle("Earnings") }
                    IconButton(onClick = {
                        hideBalance = !hideBalance
                        earningsPrefs.edit().putBoolean("hideBalance", hideBalance).apply()
                    }) {
                        Icon(if (hideBalance) MireliIcons.Visibility else MireliIcons.VisibilityOff,
                            if (hideBalance) "Show balances" else "Hide balances", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if(section==3)item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { DeparturePageTitle("Your transfers", "Departure times in East Africa Time (EAT)") }
                    IconButton(onClick = { navTarget = NavTarget(null, null) }) {
                        Icon(MireliIcons.Map, "Open live map", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if(section==2)item {
                DeparturePageTitle("Driver support", "For emergencies, contact local emergency services directly.")
                TextButton(onClick = { section=0 }) { Text("Back to account") }
            }
            if(onClose!=null)item {TextButton(onClick=onClose){Text("Back to preview")}}
            if(BuildConfig.DRIVER_SERVICE_TEST)item {Card {Text("STAGING · Simulated sign-in and payouts",Modifier.padding(16.dp))}}
            if(state.loading)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            state.error?.let {error->item {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)){Text(error,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onErrorContainer)}} }
                if(state.pendingTripAction)item {Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("One saved trip action needs confirmation",style=MaterialTheme.typography.titleMedium)
                    Text("The app keeps the same action ID after a connection failure or restart. Check it before making another trip change.")
                    Button(onClick=vm::retryTripAction,enabled=!state.loading){Text("Check saved action")}
                }}}
                if((section==3 || section==4&&state.eligibility?.optBoolean("eligible")==true) &&
                    !state.loading && !state.tripDataCurrent)item {
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
                            verified = state.eligibility?.optBoolean("verified")==true,
                            applicationStatus = app?.optString("status"),
                            tripsCurrent = state.tripDataCurrent,
                            trips = trips,
                            activeNavigation = activeTrip?.let { trip -> {
                                Surface(shape=RoundedCornerShape(MireliDesign.Radius.card),color=MaterialTheme.colorScheme.surface) {
                                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                        TripNavigation(trip,onRequestRoute=routeRequest)
                                        OutlinedButton(onClick={highlightedTripId=trip.optString("id");tripHistory=false;section=3},modifier=Modifier.fillMaxWidth()){Text("Open journey & passengers")}
                                    }
                                }
                            } },
                            onOpenTrips = { highlightedTripId=null; tripHistory=false; section=3 },
                            onRefresh = vm::refresh,
                            onOpenAccount = { section=0 },
                            onOpenTrip = { id -> highlightedTripId=id; tripHistory=false; section=3 },
                            onStartNavigation = { tripId, stop -> navTarget=NavTarget(tripId,stop) },
                            onOpenMap = { navTarget=NavTarget(null,null) },
                            onOpenEarnings = { section=1 },
                            paidAmount = state.earnings?.getJSONArray("totals")?.objects()?.let { totals ->
                                val paid = totals.filter { it.getString("status") == "completed" }.sumOf { it.getLong("netMinor") }
                                if (hideBalance) "Paid · balance hidden" else "${money(paid)} paid"
                            },
                            renewalNote = renewalNote(state.eligibility)
                        )
                    }
                }else if(section==0&&onboarding!=null&&app!=null) {
                    val applicationStatus = app.getString("status")
                    val verified = state.eligibility?.optBoolean("verified") == true
                    val canEdit = !state.loading && applicationStatus in listOf("draft", "changes_requested", "approved")
                    val documentLimit = onboarding.getInt("maxDocumentBytes")
                    item {DriverOverviewSurface {
                        val name = onboarding.getJSONObject("profile").optString("fullName")
                            .takeUnless { it.isBlank() || it == "New applicant" }
                        DriverIdentity(name, verified, prominent = true)
                        DepartureValueRow("Application", if (applicationStatus == "submitted") "In review" else applicationStatus.readableStatus(),
                            confirmed = applicationStatus == "approved")
                        DepartureNote(if(state.eligibility?.optBoolean("eligible")==true)"Eligible for new assignments" else "Approval required before new assignments")
                        renewalNote(state.eligibility)?.let { DepartureNote(it) }
                        app.optString("reviewReason").takeIf{it.isNotBlank()&&it!="null"}?.let{Text(it)}
                        if (applicationStatus == "approved") {
                            DepartureNote("Mireli reviews replacement documents before they take effect.")
                        }
                    }}
                    item {
                        DepartureSection("Driver documents")
                        Spacer(Modifier.height(6.dp))
                        DepartureNote("PDF, JPG or PNG, up to ${documentLimit / (1024 * 1024)} MB each. Use a clear, complete copy of the original. Mireli reviews it privately.")
                    }
                    val documents = app.getJSONArray("documents").objects().associateBy { it.getString("type") }
                    val graceUntil = state.eligibility?.opt("graceUntil") as? String
                    val requirements = onboarding.getJSONArray("requirements").objects().sortedByDescending { it.getBoolean("required") }
                    itemsIndexed(requirements, key = { _, requirement -> requirement.getString("id") }) { index, requirement ->
                        val id = requirement.getString("id")
                        DriverDocumentRow(requirement, documents[id], graceUntil, canEdit, state.documentUpload?.takeIf { it.type == id },
                            Modifier.staggerIn(index.coerceAtMost(5))) { expiry ->
                            selectedType = id
                            selectedExpiry = expiry
                            picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png"))
                        }
                    }
                    item {
                        HorizontalDivider()
                        val profile = onboarding.getJSONObject("profile")
                        val profileMissing = profile.optString("fullName") == "New applicant" ||
                            profile.optString("plate").isBlank() || (profile.opt("phone") as? String).isNullOrBlank() ||
                            app.optString("licenceClass").isBlank() || profile.optString("cabType").isBlank()
                        DepartureDisclosure("Driver & vehicle", if (profileMissing) "Complete your profile before submitting" else null) {
                            ServerProfile(onboarding, state.loading, vm::profile)
                        }
                    }
                    item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        DepartureSection("Submit for review")
                        listOf("Driver terms" to termsLink, "Privacy notice" to privacyLink).forEach { (label, url) ->
                            if (url != null) TextButton(onClick={policyLinkError = runCatching { uriHandler.openUri(url) }.isFailure},
                                colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.secondary)) {Text(label)}
                        }
                        if (policyLinkError) Text("A browser is needed to open these policies.",color=MaterialTheme.colorScheme.error)
                        if (!policiesAvailable) Text("Driver terms and privacy notice are unavailable. Refresh your account before submitting.",
                            color=MaterialTheme.colorScheme.error)
                        Row(Modifier.fillMaxWidth().toggleable(accepted,enabled=canEdit&&policiesAvailable,role=Role.Checkbox,onValueChange={accepted=it}),verticalAlignment=Alignment.CenterVertically){
                            Checkbox(accepted,null,enabled=canEdit&&policiesAvailable)
                            Text(if(BuildConfig.DRIVER_SERVICE_TEST)"I acknowledge this synthetic application and test checklist." else "I have read the current driver terms and privacy notice.")
                        }
                        DepartureButton(onClick={vm.submit(accepted)},enabled=canEdit&&policiesAvailable&&accepted&&applicationStatus in listOf("draft","changes_requested"),modifier=Modifier.fillMaxWidth()){Text("Submit for compliance review")}
                        DepartureNote("Mireli reviews completed applications within one day of enrollment.")
                    }}
                }else if(section==1) {
                    fun shown(minor:Long):String=if(hideBalance)"KSh ••••" else money(minor)
                    state.earnings?.let{statement->
                        val totals = statement.getJSONArray("totals").objects()
                        val lines = statement.getJSONArray("lines").objects()
                        item { DriverOverviewSurface {
                            val paid = totals.filter { it.getString("status") == "completed" }.sumOf { it.getLong("netMinor") }
                            DepartureAmount("Confirmed paid earnings", shown(paid))
                            if (totals.any { it.getString("status") != "completed" }) HorizontalDivider()
                            totals.filterNot { it.getString("status") == "completed" }.forEach {
                                DepartureValueRow(it.getString("status").settlementLabel(), shown(it.getLong("netMinor")))
                            }
                        } }
                        item { DepartureSection("Settlements") }
                        if (lines.isEmpty()) item {
                            DepartureNote("No settlement records yet. Completed journeys with a settlement will appear here.")
                            TextButton(onClick = { tripHistory = true; section = 3 }) { Text("View trip history") }
                        }
                        itemsIndexed(lines, key = { _, line -> line.getString("id") }) { index, line ->
                            DepartureSettlement(line, hideBalance, Modifier.staggerIn(index.coerceAtMost(5)))
                        }
                        val destination=statement.getJSONObject("destination")
                        item {
                            val status = destination.getString("status")
                            val masked = (statement.opt("destinationMasked") as? String)?.takeIf { it.isNotBlank() }
                            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                DepartureSection("M-Pesa payouts")
                                DepartureValueRow("Payout number", masked ?: "Not set")
                                DepartureValueRow("Beneficiary", status.readableStatus(), confirmed = status == "approved")
                                (destination.opt("reviewNote") as? String)?.takeIf { it.isNotBlank() }?.let { DepartureNote(it) }
                                if (status in listOf("not_configured", "rejected")) {
                                    DepartureDisclosure("Set up payout details") {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            DepartureNote("Finance checks your account name and identity evidence before approving your M-Pesa number.")
                                            TextField(payoutName, { payoutName = it.take(100) }, Modifier.fillMaxWidth(),
                                                label = { Text("Name on your M-Pesa account") }, singleLine = true,
                                                enabled = !state.loading, colors = departureFieldColors())
                                            TextField(payoutPhone, { payoutPhone = it.take(20) }, Modifier.fillMaxWidth(),
                                                label = { Text("M-Pesa number (07XX… or 2547XX…)") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                                singleLine = true, enabled = !state.loading, colors = departureFieldColors())
                                            Row(Modifier.fillMaxWidth().toggleable(payoutAcknowledged, enabled = !state.loading,
                                                role = Role.Checkbox, onValueChange = { payoutAcknowledged = it }),
                                                verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(payoutAcknowledged, null)
                                                Text("This M-Pesa number and account name are mine.")
                                            }
                                            DepartureButton(onClick = { vm.payoutDestination(payoutName, payoutPhone) },
                                                enabled = !state.loading && payoutAcknowledged && payoutName.trim().length >= 3,
                                                modifier = Modifier.fillMaxWidth()) { Text("Request beneficiary review") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (state.earnings == null) item {
                        DepartureNote(if (state.loading) "Loading your statement…" else "Refresh to load your statement.")
                        if (!state.loading) TextButton(onClick = vm::refresh) { Text("Refresh earnings") }
                    }
                }else if(section==3) {
                    val allTrips=state.trips?.getJSONArray("trips")?.objects()?:emptyList()
                    val visibleTrips=if(tripHistory)allTrips.filterNot{it.isActiveTransfer()} else
                        allTrips.filter{it.isActiveTransfer()}.sortedWith(
                            compareBy<JSONObject> { it.optString("id") != highlightedTripId }.then(transferOrder()))
                    item {
                        DepartureTripTabs(tripHistory) { history ->
                            if (!history && tripHistory) highlightedTripId = null
                            tripHistory = history
                        }
                    }
                    if(!tripHistory&&state.eligibility?.optBoolean("eligible")!=true)item {
                        DriverNotice(MireliIcons.VerifiedUser,"Driver approval needed",
                            "You need current document approval to accept assignments, board passengers or start a journey. Check Account for your application status.")
                    }
                    itemsIndexed(visibleTrips,key={_, trip -> trip.getString("id")}){index, trip->
                        LiveTripCard(trip,state.loading||state.pendingTripAction||!state.tripDataCurrent,
                            state.eligibility?.optBoolean("eligible")==true,vm::tripCommand,routeRequest,
                            Modifier.staggerIn(index.coerceAtMost(5)), openInitially = trip.optString("id") == highlightedTripId ||
                                (!tripHistory && index == 0))
                    }
                    if(visibleTrips.isEmpty())item {Column(Modifier.padding(vertical=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                        Text(when {
                            state.trips==null && state.loading -> "Loading trips…"
                            state.trips==null -> "Trip list unavailable"
                            tripHistory -> "No past trips"
                            else -> "No current assignments"
                        },style=MaterialTheme.typography.titleLarge)
                        if (state.trips != null || !state.loading) DepartureNote(when {
                            state.trips==null -> "Refresh your account to check for transfers."
                            tripHistory -> "Completed, cancelled and declined trips appear here."
                            state.eligibility?.optBoolean("eligible")!=true -> "After approval, your assigned transfers will appear here."
                            else -> "Refresh your account to check for new assignments."
                        })
                        if(!tripHistory&&state.eligibility?.optBoolean("eligible")!=true)TextButton(onClick={section=0}){Text("Continue onboarding")}
                    }}
                }else if(section==2) {
                    if(state.supportSent>0)item {Text("Support case sent. Mireli's replies appear below.",color=MaterialTheme.colorScheme.primary)}
                    item {FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("trip","account","documents","payout","safety").forEach{category->FilterChip(selected=supportCategory==category,onClick={supportCategory=category},label={Text(category.replaceFirstChar{it.uppercase()})})}}}
                    item {TextField(supportMessage,{supportMessage=it.take(2000)},label={Text("How can we help?")},modifier=Modifier.fillMaxWidth(),minLines=3,enabled=!state.loading,colors=departureFieldColors())}
                    item {DepartureButton(onClick={vm.support(supportCategory,supportMessage)},enabled=!state.loading&&supportMessage.trim().length>=10,modifier=Modifier.fillMaxWidth()){Text("Send support case")}}
                    item {Text("Keep ID numbers and document images out of support messages. Use the private document upload flow.",style=MaterialTheme.typography.bodySmall)}
                    items(state.support?.getJSONArray("cases")?.objects()?:emptyList(),key={it.getString("id")}){case->Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        HorizontalDivider()
                        Text("${case.getString("category").readableStatus()} · ${case.getString("status").readableStatus()}",style=MaterialTheme.typography.titleMedium);Text("Reference: ${case.getString("id")}",style=MaterialTheme.typography.bodySmall);Text(case.getString("message"))
                        case.optString("reply").takeIf{it.isNotBlank()&&it!="null"}?.let{Text("Mireli: $it")}
                    }}
                }
                if(section==0)item {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        DepartureSection("Settings and support")
                        DriverActionTile("Driver support","Trips, documents and payout cases",MireliIcons.HeadsetMic) { section=2 }
                        AppearanceToggle(showSettings=true)
                    }
                }
                if(section==0)item {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        DepartureSection("Privacy and account deletion")
                        if (onboarding != null) DriverPrivacyNotice(onboarding, state.supportEmail)
                        DriverActionTile("Request account deletion","Review a request before sending it to Mireli.",MireliIcons.PrivacyTip) {
                            supportCategory="account"
                            supportMessage="I am requesting deletion of my Mireli Driver account. Please let me know if you need any further information to handle my request."
                            section=2
                        }
                    }
                }
                if(section==0)item {
                    TextButton(onClick={com.mireli.driver.service.TripTracking.stop(trackingContext);vm.signOut()},
                        enabled=!state.loading,colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) { Text("Sign out") }
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
            nextTrain = nextDeparture?.takeIf { target.tripId != null }?.let { countdown(clock, it) },
            onSearch = if (state.destinationSearchOpen) vm::searchDestinations else null,
            onDestinationRoute = if (state.destinationSearchOpen) vm::destinationRoute else null,
            satelliteToken = if (state.satelliteOpen) vm.satelliteSessionToken() else null
        )
    }
    }
}

private data class NavTarget(val tripId:String?,val stop:NavigationStop?)

private fun renewalNote(eligibility: JSONObject?): String? {
    val grace = eligibility?.opt("graceUntil") as? String
    if (grace != null) return "Renew by ${grace.take(10)} to keep your blue check. New assignments require current documents."
    return if ((eligibility?.optJSONArray("expiringDocuments")?.length() ?: 0) > 0) {
        "A required document expires within 30 days. Check your documents for the expiry date."
    } else null
}

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
        TextField(contactPhone,{contactPhone=it.take(20)},label={Text("Kenyan contact phone")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),singleLine=true,enabled=editable&&storedPhone.isBlank(),modifier=Modifier.fillMaxWidth(),colors=departureFieldColors())
        if (storedPhone.isNotBlank()) Text("Contact Support to change this number.", style = MaterialTheme.typography.bodySmall)
        TextField(name,{name=it.take(100)},label={Text("Driver full name")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth(),colors=departureFieldColors())
        key(version) { VehicleTypePicker(cab, { cab = it }, enabled = editable) }
        TextField(plate,{plate=it.take(15).uppercase()},label={Text("Registration plate")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth(),colors=departureFieldColors())
        TextField(capacity,{capacity=it.filter(Char::isDigit).take(2)},label={Text("Passenger seats")},
            supportingText={Text("Use the approved capacity, excluding the driver.")},
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth(),colors=departureFieldColors())
        TextField(licence,{licence=it.take(20)},label={Text("Licence class")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth(),colors=departureFieldColors())
        Row(Modifier.fillMaxWidth().toggleable(owner,enabled=editable,role=Role.Checkbox,onValueChange={owner=it}),verticalAlignment=Alignment.CenterVertically){Checkbox(owner,null);Text("I own this vehicle")}
        DepartureButton(onClick={save(name,plate,capacity,licence,cab,owner,contactPhone)},enabled=editable&&valid&&changed,modifier=Modifier.fillMaxWidth()){Text("Save profile to Mireli")}
    }
}
