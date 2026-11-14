package com.mireli.driver.ui
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val schedule by vm.trainSchedule.collectAsStateWithLifecycle()
    val profilePhoto by vm.profilePhoto.collectAsStateWithLifecycle()
    var expandedDocumentGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var guidedApplication by rememberSaveable { mutableStateOf(false) }
    var applicationStep by rememberSaveable { mutableStateOf(0) }
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
    LaunchedEffect(state.signedIn, section, lifecycle) {
        if (state.signedIn && section == 4) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { vm.refreshTimetable() }
        }
    }
    LaunchedEffect(state.signedIn, section) {
        if (state.signedIn && section == 0) vm.loadProfilePhoto()
    }
    var clock by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(state.challengeId,state.resendAt){code="";while(state.challengeId!=null&&clock<state.resendAt){clock=System.currentTimeMillis();delay(1000)};clock=System.currentTimeMillis()}
    var selectedType by rememberSaveable {mutableStateOf<String?>(null)};var selectedExpiry by rememberSaveable {mutableStateOf("")}
    var selectedFile by rememberSaveable { mutableStateOf<Uri?>(null) }
    var removeDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    var showPayoutDetails by rememberSaveable { mutableStateOf(false) }
    var supportFromEarnings by rememberSaveable { mutableStateOf(false) }
    var previouslySignedIn by remember {mutableStateOf(false)}
    LaunchedEffect(state.signedIn) {
        if (state.signedIn) {
            if (entry == DriverEntry.Register) {
                guidedApplication = true
                applicationStep = 0
                section = 0
            } else if (entry == DriverEntry.SignIn) {
                guidedApplication = false
                section = 4
            }
            entry = DriverEntry.Welcome
        }
        if(previouslySignedIn && !state.signedIn) {
            email="";code="";supportMessage="";payoutName="";payoutAcknowledged=false
            supportCategory="trip";accepted=false;selectedType=null;selectedExpiry="";selectedFile=null;removeDocumentId=null;tripHistory=false;highlightedTripId=null;section=4
            guidedApplication = false
            applicationStep = 0
            expandedDocumentGroup = null
            showPayoutDetails = false
            supportFromEarnings = false
            payoutPhone = ""
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
    LaunchedEffect(state.signedIn, section, guidedApplication, applicationStep) {
        val visibleSection = if (state.signedIn) section else -1
        if (lastListSection != visibleSection || guidedApplication) {
            lastListSection = visibleSection
            focus.clearFocus()
            keyboard?.hide()
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(state.supportSent){if(state.supportSent>0){supportMessage="";focus.clearFocus();keyboard?.hide()}}
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (selectedType != null && uri != null) selectedFile = uri
        else selectedType = null
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(vm::uploadProfilePhoto)
    }
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
            onRegisterFromSignIn = if (entry == DriverEntry.SignIn && state.error != null && state.registrationOpen == true) {
                { vm.changeEmail(); code = ""; entry = DriverEntry.Register }
            } else null,
            supportAddressFailed = showSupportAddress,
            onSupport = {
                val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", state.supportEmail, null))
                showSupportAddress = runCatching { context.startActivity(emailIntent) }.isFailure
            }
        )
        return
    }
    selectedFile?.let { file ->
        val type = selectedType
        val requirement = onboarding?.optJSONArray("requirements")?.objects()
            ?.firstOrNull { it.optString("id") == type }
        if (type != null && requirement != null) {
            val dismiss = { selectedFile = null; selectedType = null; selectedExpiry = "" }
            DocumentSelectionDialog(
                title = requirement.getString("title"),
                uri = file,
                expires = selectedExpiry,
                canUpload = !state.loading && app?.optString("status") in listOf("draft", "changes_requested", "approved"),
                onRemove = dismiss,
                onUpload = { vm.upload(type, selectedExpiry, file); dismiss() },
            )
        }
    }
    removeDocumentId?.let { id ->
        val dismiss = { removeDocumentId = null }
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("Remove this document?") },
            text = { Text(if (app?.optString("status") == "submitted")
                "Your application will return to draft. Upload the correct file and submit again."
                else "This file will be removed from review.") },
            confirmButton = {
                TextButton(enabled = !state.loading, onClick = { vm.removeDocument(id); dismiss() }) {
                    Text("Remove document", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
        )
    }
    val applicationStatus = app?.optString("status")
    val editingApplication = applicationStatus in listOf("draft", "changes_requested")
    val leaveApplication = {
        guidedApplication = false
        applicationStep = 0
        accepted = false
        section = 4
    }
    BackHandler(enabled = guidedApplication) {
        if (!state.loading) {
            if (editingApplication && applicationStep > 0) applicationStep--
            else leaveApplication()
        }
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
    val eligible = state.eligibility?.optBoolean("eligible") == true
    val trips = state.trips?.optJSONArray("trips")?.objects()
    val assignment = if (section == 3 && !tripHistory && state.tripDataCurrent) {
        trips?.filter { it.isActiveTransfer() }?.minWithOrNull(
            compareBy<JSONObject> { it.optString("id") != highlightedTripId }.then(transferOrder())
        )
    } else null
    val primaryAction = assignment?.primaryTripAction()
    val busy = state.loading || state.pendingTripAction || !state.tripDataCurrent
    var confirmTripAction by rememberSaveable(assignment?.optString("id"), assignment?.optInt("version")) {
        mutableStateOf(false)
    }
    if (confirmTripAction && assignment != null && primaryAction != null) {
        TripCommandDialog(assignment, primaryAction.first, null, busy, eligible, vm::tripCommand) {
            confirmTripAction = false
        }
    }
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            Column {
                if (assignment != null && primaryAction != null && (eligible || primaryAction.first == "complete")) {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = MireliDesign.Space.md,
                            vertical = MireliDesign.Space.xs)) {
                            Text(assignment.optString("routeName").ifBlank { "Current assignment" },
                                style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            DepartureButton(onClick = { confirmTripAction = true }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth()) { Text(primaryAction.second) }
                        }
                    }
                }
                if (!guidedApplication) NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    listOf(
                        4 to ("Home" to MireliIcons.Home),
                        3 to ("Trips" to MireliIcons.CarFront),
                        1 to ("Earnings" to MireliIcons.AccountBalanceWallet),
                        0 to ("Account" to MireliIcons.PersonOutline),
                    ).forEach { (target, item) ->
                        NavigationBarItem(
                            selected = section == target,
                            onClick = {
                                if (target == 3 && section != 3) highlightedTripId = null
                                section = target
                            },
                            icon = { Icon(item.second, null) },
                            label = { Text(item.first) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.surfaceContainer,
                                selectedIconColor = MaterialTheme.colorScheme.secondary,
                                selectedTextColor = MaterialTheme.colorScheme.secondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().testTag("driver_service_list"),state=listState,contentPadding=PaddingValues(horizontal=MireliDesign.Space.md,vertical=MireliDesign.Space.md),verticalArrangement=Arrangement.spacedBy(if (guidedApplication) MireliDesign.Space.md else MireliDesign.Space.sm)) {
            if(section==4)item { DriverHomeTopBar(onSupport = { section = 2 }) }
            if (guidedApplication) item {
                DeparturePageTitle("Driver application",
                    if (editingApplication) "Step ${applicationStep + 1} of 3" else null)
                if (app == null && !state.loading) {
                    TextButton(onClick = vm::refresh) { Text("Reload application") }
                }
            }
            if(section==0 && !guidedApplication)item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { DeparturePageTitle("Account") }
                    IconButton(onClick = { vm.refresh(); vm.loadProfilePhoto() }, enabled = !state.loading) {
                        Icon(MireliIcons.Refresh, "Refresh account", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if (section == 1) item { DeparturePageTitle("Earnings") }
            if(section==3)item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { DeparturePageTitle("Trips", "Departure times · EAT") }
                    IconButton(onClick = { navTarget = NavTarget(null, null) }) {
                        Icon(MireliIcons.Map, "Open live map", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if(section==2)item {
                DeparturePageTitle("Driver support", "For emergencies, contact local emergency services directly.")
                TextButton(onClick = {
                    section = if (supportFromEarnings) 1 else 0
                    supportFromEarnings = false
                }) { Text(if (supportFromEarnings) "Back to earnings" else "Back to account") }
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
                    item {
                        DriverHomeDashboard(
                            name = onboarding?.optJSONObject("profile")?.optString("fullName")
                                ?.takeIf{it.isNotBlank()&&it!="New applicant"},
                            eligible = state.eligibility?.optBoolean("eligible")==true,
                            verified = state.eligibility?.optBoolean("verified")==true,
                            applicationStatus = app?.optString("status"),
                            tripsCurrent = state.tripDataCurrent,
                            trips = trips,
                            onOpenTrips = { highlightedTripId=null; tripHistory=false; section=3 },
                            onRefresh = vm::refresh,
                            onOpenAccount = { section=0 },
                            onOpenTrip = { id -> highlightedTripId=id; tripHistory=false; section=3 },
                            onStartNavigation = { tripId, stop -> navTarget=NavTarget(tripId,stop) },
                            onOpenMap = { navTarget=NavTarget(null,null) },
                            renewalNote = renewalNote(state.eligibility),
                            schedule = schedule,
                            onRefreshTimetable = vm::refreshTimetable
                        )
                    }
                }else if(section==0&&onboarding!=null&&app!=null) {
                    val applicationStatus = app.getString("status")
                    val verified = state.eligibility?.optBoolean("verified") == true
                    val canEdit = !state.loading && applicationStatus in listOf("draft", "changes_requested", "approved")
                    val documentLimit = onboarding.getInt("maxDocumentBytes")
                    val profile = onboarding.getJSONObject("profile")
                    val documents = app.getJSONArray("documents").objects().associateBy { it.getString("type") }
                    val graceUntil = state.eligibility?.opt("graceUntil") as? String
                    val requirements = onboarding.getJSONArray("requirements").objects()
                    val required = requirements.filter { it.getBoolean("required") }
                    val onFile = required.count {
                        val evidence = documents[it.getString("id")]
                        evidence?.optString("state") in setOf("pending_review", "approved")
                    }
                    if (!guidedApplication) item {
                        MireliDriverCard(profile, applicationStatus, verified,
                            state.eligibility?.optBoolean("eligible") == true, profilePhoto, !state.loading,
                            onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onRemovePhoto = vm::removeProfilePhoto)
                        renewalNote(state.eligibility)?.let { DepartureNote(it) }
                        (app.opt("reviewReason") as? String)?.takeIf { it.isNotBlank() }?.let { DepartureNote(it) }
                        if (editingApplication) TextButton(
                            onClick = { applicationStep = 0; guidedApplication = true },
                            enabled = !state.loading,
                        ) { Text("Resume guided application", color = MaterialTheme.colorScheme.secondary) }
                    }
                    val profileReady = profile.optString("fullName").let { it != "New applicant" && it.trim().length >= 3 } &&
                        profile.optString("plate").isNotBlank() && (profile.opt("phone") as? String).orEmpty().isNotBlank() &&
                        app.optString("licenceClass").isNotBlank() && profile.optString("cabType").isNotBlank()
                    val reviewReady = profileReady && required.all { requirement ->
                        val evidence = documents[requirement.getString("id")]
                        evidence?.optString("state") in setOf("pending_review", "approved") &&
                            (!requirement.getBoolean("expires") || OnboardingPolicy.isCurrentDocumentDate(
                                (evidence?.opt("expiresAt") as? String)?.take(10).orEmpty()))
                    }
                    if (guidedApplication && !editingApplication) item {
                        val status = when (applicationStatus) {
                            "submitted" -> "Application submitted"
                            "approved" -> "Application approved"
                            else -> applicationStatus.readableStatus()
                        }
                        Text(status, style = MaterialTheme.typography.headlineMedium)
                        if (applicationStatus == "submitted") DepartureNote("Mireli will review your documents. Track your status in Account.")
                        DepartureButton(onClick = leaveApplication, modifier = Modifier.fillMaxWidth()) { Text("Go to Home") }
                    }
                    if (guidedApplication && editingApplication && applicationStep == 0) item {
                        DepartureSection("Driver & vehicle")
                        (app.opt("reviewReason") as? String)?.takeIf { it.isNotBlank() }?.let { DepartureNote(it) }
                        ServerProfile(onboarding, state.loading, vm::profile)
                        DepartureButton(onClick = { applicationStep = 1 }, enabled = profileReady && !state.loading,
                            modifier = Modifier.fillMaxWidth()) { Text("Continue to documents") }
                    }
                    if (!guidedApplication || (editingApplication && applicationStep == 1)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                DepartureSection("Verification checklist")
                                if (required.isNotEmpty()) {
                                    Text("$onFile of ${required.size} required documents on file",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    LinearProgressIndicator(progress = { onFile.toFloat() / required.size },
                                        modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (guidedApplication || expandedDocumentGroup != null) {
                                    DepartureNote("PDF, JPG or PNG · up to ${documentLimit / (1024 * 1024)} MB")
                                }
                            }
                        }
                        val sections = requirements.groupBy(::documentSection)
                        for (group in listOf("Driver credentials", "Vehicle documents", "Optional documents")) {
                            val rows = sections[group] ?: continue
                            val expanded = expandedDocumentGroup == group
                            item(key = group) {
                                if (guidedApplication) {
                                    DepartureSection(group)
                                } else {
                                    val count = rows.count {
                                        documents[it.getString("id")]?.optString("state") in setOf("pending_review", "approved")
                                    }
                                    AccountDocumentGroup(group, "$count/${rows.size}", expanded) {
                                        expandedDocumentGroup = if (expanded) null else group
                                    }
                                }
                            }
                            if (!guidedApplication && !expanded) continue
                            itemsIndexed(rows, key = { _, requirement -> requirement.getString("id") }) { index, requirement ->
                                val id = requirement.getString("id")
                                DriverDocumentRow(requirement, documents[id], graceUntil, canEdit, state.documentUpload?.takeIf { it.type == id },
                                    Modifier.staggerIn(index.coerceAtMost(5)), showRequirement = false,
                                    onRemove = documents[id]?.takeIf {
                                        onboarding.optBoolean("canRemovePendingDocument") && !state.loading &&
                                            applicationStatus in listOf("draft", "changes_requested", "submitted", "approved") &&
                                            it.optString("state") == "pending_review"
                                    }?.getString("id")?.let { documentId -> { removeDocumentId = documentId } },
                                ) { expiry ->
                                    selectedType = id
                                    selectedExpiry = expiry
                                    picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png"))
                                }
                            }
                        }
                        if (guidedApplication) item {
                            DepartureButton(onClick = { applicationStep = 2 }, enabled = reviewReady && !state.loading,
                                modifier = Modifier.fillMaxWidth()) { Text("Continue to review") }
                        }
                    }
                    if (!guidedApplication) item {
                        DepartureDisclosure("Driver & vehicle", if (!profileReady) "Complete your profile before submitting" else null) {
                            ServerProfile(onboarding, state.loading, vm::profile)
                        }
                    }
                    if (guidedApplication && editingApplication && applicationStep == 2) item {
                      Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        DepartureSection("Submit for review")
                        if (guidedApplication) {
                            DepartureNote("${profile.optString("fullName")} · ${profile.optString("plate")}")
                            DepartureNote("$onFile of ${required.size} required documents on file")
                        }
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
                        DepartureButton(onClick={vm.submit(accepted)},enabled=canEdit&&policiesAvailable&&accepted&&applicationStatus in listOf("draft","changes_requested")&&(!guidedApplication || reviewReady),modifier=Modifier.fillMaxWidth()){Text("Submit for compliance review")}
                    }}
                }else if(section==1) {
                    fun shown(minor:Long):String=if(hideBalance)"KSh ••••" else money(minor)
                    state.earnings?.let{statement->
                        val totals = statement.getJSONArray("totals").objects()
                        val lines = statement.getJSONArray("lines").objects()
                        val paid = totals.filter { it.getString("status") == "completed" }.sumOf { it.getLong("netMinor") }
                        item {
                            EarningsOverview(
                                paid = shown(paid), hidden = hideBalance,
                                onToggleBalance = {
                                    hideBalance = !hideBalance
                                    earningsPrefs.edit().putBoolean("hideBalance", hideBalance).apply()
                                },
                                onPayoutDetails = { showPayoutDetails = true },
                                payoutStatus = statement.getJSONObject("destination").getString("status").let { status ->
                                    when (status) {
                                        "approved" -> (statement.opt("destinationMasked") as? String)
                                            ?.takeIf { it.isNotBlank() }?.let { "M-Pesa payout number $it" } ?: "M-Pesa payout details approved"
                                        "not_configured" -> "Set up your M-Pesa payout number"
                                        "pending", "pending_review" -> "M-Pesa payout details in review"
                                        "rejected" -> "M-Pesa payout details need attention"
                                        else -> "M-Pesa · ${status.readableStatus()}"
                                    }
                                },
                                onPayoutSupport = {
                                    supportCategory = "payout"
                                    supportFromEarnings = true
                                    section = 2
                                },
                            )
                        }
                        val pendingTotals = totals.filterNot { it.getString("status") == "completed" }
                        if (pendingTotals.isNotEmpty()) item {
                            Column(verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)) {
                                DepartureSection("Unsettled earnings")
                                pendingTotals.forEach {
                                    DepartureValueRow(it.getString("status").settlementLabel(), shown(it.getLong("netMinor")))
                                }
                            }
                        }
                        item {
                            DepartureSection("Settlements")
                            if (lines.isEmpty()) DepartureNote("No settlement records yet.")
                        }
                        itemsIndexed(lines, key = { _, line -> line.getString("id") }) { index, line ->
                            DepartureSettlement(line, hideBalance, Modifier.staggerIn(index.coerceAtMost(5)))
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
                    if (!tripHistory && !eligible && visibleTrips.isNotEmpty()) item {
                        DepartureDisclosure("Driver approval needed") {
                            TextButton(onClick = { section = 0 }) { Text("Check documents") }
                        }
                    }
                    itemsIndexed(visibleTrips,key={_, trip -> trip.getString("id")}){index, trip->
                        LiveTripCard(trip,state.loading||state.pendingTripAction||!state.tripDataCurrent,
                            state.eligibility?.optBoolean("eligible")==true,vm::tripCommand,routeRequest,
                            Modifier.staggerIn(index.coerceAtMost(5)), openInitially = trip.optString("id") == highlightedTripId ||
                                (!tripHistory && index == 0),
                            onStartNavigation = { stop -> navTarget = NavTarget(trip.optString("id"), stop) },
                            onSearchAddress = if (state.destinationSearchOpen) { address ->
                                navTarget = NavTarget(null, null, address)
                            } else null,
                            primaryActionAnchored = assignment?.optString("id") == trip.optString("id"))
                    }
                    if(visibleTrips.isEmpty())item {
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(when {
                                state.trips==null && state.loading -> "Loading trips…"
                                state.trips==null -> "Trip list unavailable"
                                tripHistory -> "No past trips"
                                else -> "No current assignments"
                            }, style = MaterialTheme.typography.titleMedium)
                            if (state.trips != null || !state.loading) DepartureNote(when {
                                state.trips==null -> "Refresh your account to check for transfers."
                                tripHistory -> "Completed, cancelled and declined trips will show here."
                                !eligible -> "Assignments open after verification."
                                else -> "New assignments will appear here."
                            })
                            if (!tripHistory && !eligible) TextButton(onClick = { section = 0 }) { Text("Continue onboarding") }
                        }
                    }
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
                if (guidedApplication && editingApplication) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs)) {
                        if (applicationStep > 0) TextButton(onClick = { applicationStep-- }, enabled = !state.loading) { Text("Back") }
                        TextButton(onClick = leaveApplication, enabled = !state.loading) { Text("Finish later") }
                    }
                }
                if(section==0 && !guidedApplication)item {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        DepartureSection("Settings")
                        DriverActionTile("Driver support","",MireliIcons.HeadsetMic) { section=2 }
                        AppearanceToggle(showSettings=true)
                    }
                }
                if(section==0 && !guidedApplication)item {
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        DepartureSection("Privacy")
                        if (onboarding != null) DriverPrivacyNotice(onboarding, state.supportEmail)
                        DriverActionTile("Request account deletion","",MireliIcons.PrivacyTip) {
                            supportCategory="account"
                            supportMessage="I am requesting deletion of my Mireli Driver account. Please let me know if you need any further information to handle my request."
                            section=2
                        }
                    }
                }
                if(section==0 && !guidedApplication)item {
                    TextButton(onClick={com.mireli.driver.service.TripTracking.stop(trackingContext);vm.signOut()},
                        enabled=!state.loading,colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) { Text("Sign out") }
                }
        }
    }
    val payoutStatement = state.earnings
    if (section == 1 && showPayoutDetails && payoutStatement != null) {
        val destination = payoutStatement.getJSONObject("destination")
        val status = destination.getString("status")
        val masked = (payoutStatement.opt("destinationMasked") as? String)?.takeIf { it.isNotBlank() }
        AlertDialog(
            onDismissRequest = { showPayoutDetails = false },
            title = { Text("M-Pesa payouts") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs),
                ) {
                    DepartureValueRow("Payout number", masked ?: "Not set")
                    DepartureValueRow("Beneficiary", status.readableStatus())
                    (destination.opt("reviewNote") as? String)?.takeIf { it.isNotBlank() }?.let { DepartureNote(it) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (status in listOf("not_configured", "rejected")) {
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
            },
            confirmButton = { TextButton(onClick = { showPayoutDetails = false }) { Text("Done") } },
        )
    }
    navTarget?.let{ target->
        DriverNavigationScreen(
            tripId=target.tripId, stop=target.stop, onRequestRoute=routeRequest, onClose={navTarget=null},
            onSearch = if (state.destinationSearchOpen) vm::searchDestinations else null,
            onDestinationRoute = if (state.destinationSearchOpen) vm::destinationRoute else null,
            satelliteToken = if (state.satelliteOpen) vm.satelliteSessionToken() else null,
            initialQuery = target.query
        )
    }
    }
}

private data class NavTarget(val tripId:String?,val stop:NavigationStop?,val query: String? = null)

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
