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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mireli.driver.BuildConfig
import com.mireli.driver.DriverServiceState
import com.mireli.driver.DriverServicesViewModel
import com.mireli.driver.domain.OnboardingPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

private fun JSONArray.objects()=(0 until length()).map {getJSONObject(it)}
private fun policyLink(raw: String): String? = runCatching {
    URI(raw).takeIf { it.scheme == "https" && it.host != null && it.userInfo == null }?.toASCIIString()
}.getOrNull()
@Composable private fun OnboardingStep(number:String,label:String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.surface) {
            Text(number,Modifier.padding(horizontal=11.dp,vertical=6.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
        }
        Text(label,style=MaterialTheme.typography.bodyMedium)
    }
}
@Composable fun DriverServicesScreen(onClose:(()->Unit)?=null,vm:DriverServicesViewModel=viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var email by rememberSaveable {mutableStateOf("")};var code by remember {mutableStateOf("")}
    var section by rememberSaveable {mutableIntStateOf(4)};var accepted by rememberSaveable {mutableStateOf(false)}
    var supportCategory by rememberSaveable {mutableStateOf("trip")};var supportMessage by remember {mutableStateOf("")}
    var payoutName by remember {mutableStateOf("")};var payoutAcknowledged by remember {mutableStateOf(false)}
    var tripHistory by rememberSaveable {mutableStateOf(false)}
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
    Scaffold(containerColor=MaterialTheme.colorScheme.background,bottomBar={if(state.signedIn)NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
        NavigationBarItem(section==4,{section=4},icon={Icon(MireliIcons.Home,null)},label={Text("Home")})
        NavigationBarItem(section==3,{section=3},icon={Icon(MireliIcons.Calendar,null)},label={Text("Trips")})
        NavigationBarItem(section==0,{section=0},icon={Icon(MireliIcons.PersonOutline,null)},label={Text("Account")})
        NavigationBarItem(section==1,{section=1},icon={Icon(MireliIcons.AccountBalanceWallet,null)},label={Text("Earnings")})
        NavigationBarItem(section==2,{section=2},icon={Icon(MireliIcons.HeadsetMic,null)},label={Text("Support")})
    }}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().testTag("driver_service_list"),state=listState,contentPadding=PaddingValues(horizontal=18.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {DriverBrandHeader(if(state.signedIn) "Driver account" else "Mombasa · SGR transfers")}
            if(onClose!=null)item {TextButton(onClick=onClose){Text("Back to preview")}}
            if(BuildConfig.DRIVER_SERVICE_TEST)item {Card {Text("STAGING · Simulated sign-in and payouts",Modifier.padding(16.dp))}}
            if(state.loading)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            state.error?.takeIf{state.signedIn||state.emailSignInOpen==true}?.let {error->item {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)){Text(error,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onErrorContainer)}} }
            if(!state.signedIn) {
                item {AppearanceToggle(showSettings=false)}
                item {SignInBoard(state.challengeId != null)}
                item {
                    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            if(state.emailSignInOpen!=true||state.registrationOpen!=true) {
                                SignInServiceNotice(state)
                            } else if (state.challengeId==null) {
                                Text("Enter your email to sign in or begin your driver application.",
                                    style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedTextField(email,{email=it.take(254)},label={Text("Email address")},
                                leadingIcon={Icon(MireliIcons.Mail,null)},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),
                                singleLine=true,enabled=state.emailSignInOpen==true && state.challengeId==null && !state.loading,
                                colors=OutlinedTextFieldDefaults.colors(disabledLabelColor=MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledLeadingIconColor=MaterialTheme.colorScheme.onSurfaceVariant,
                                    disabledBorderColor=MaterialTheme.colorScheme.outline),
                                modifier=Modifier.fillMaxWidth().testTag("driver_email_field"))
                            if(state.challengeId==null) {
                                Button(onClick={vm.requestCode(email)},enabled=!state.loading&&state.emailSignInOpen==true&&email.isNotBlank(),
                                    modifier=Modifier.fillMaxWidth().heightIn(min=50.dp).testTag("driver_send_code"),
                                    shape=RoundedCornerShape(12.dp),colors=ButtonDefaults.buttonColors(
                                        disabledContainerColor=MaterialTheme.colorScheme.surfaceVariant,
                                        disabledContentColor=MaterialTheme.colorScheme.onSurfaceVariant)) {
                                    Text(if(state.emailSignInOpen==true)"Continue with email" else if(state.loading)"Checking sign-in…" else "Sign-in unavailable")
                                }
                                if (state.emailSignInOpen == true) Text("We will send a one-time verification code to your email.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                DriverStepProgress(0,listOf("Verify email","Documents","Review"))
                                Text("Check your email",style=MaterialTheme.typography.titleLarge)
                                Text("Code requested for ${state.challengeEmail}",style=MaterialTheme.typography.bodyMedium)
                                TextButton(onClick={vm.changeEmail();code=""},enabled=!state.loading){Text("Use a different email")}
                                state.demoCode?.let {sample->Text("Sample verification code: $sample",style=MaterialTheme.typography.bodySmall)}
                                OutlinedTextField(code,{code=it.filter(Char::isDigit).take(6)},textStyle=MaterialTheme.typography.headlineMedium.copy(letterSpacing=androidx.compose.ui.unit.TextUnit(8f,androidx.compose.ui.unit.TextUnitType.Sp)),label={Text("6-digit verification code")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),singleLine=true,enabled=!state.loading,modifier=Modifier.fillMaxWidth())
                                Button(onClick={vm.verifyCode(code)},enabled=!state.loading&&code.length==6,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(28.dp)){Text("Verify and continue")}
                                TextButton(onClick={vm.requestCode(state.challengeEmail?:email)},enabled=!state.loading&&clock>=state.resendAt){Text(if(clock<state.resendAt)"Resend in ${((state.resendAt-clock+999)/1000).coerceAtLeast(0)}s" else "Resend verification code")}
                            }
                        }
                    }
                }
                if(state.emailSignInOpen!=true||state.registrationOpen!=true)item {
                    OutlinedButton(onClick=vm::refreshServiceStatus,enabled=!state.loading,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()) {
                        Icon(MireliIcons.Refresh,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Retry connection")
                    }
                }
                if (state.emailSignInOpen == true && state.registrationOpen == true) item {
                    Card(Modifier.fillMaxWidth().testTag("driver_sign_in_promo"),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Icon(MireliIcons.Train,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(24.dp))
                                Text("Mombasa SGR transfers",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
                            }
                            Text("Apply to drive with Mireli",style=MaterialTheme.typography.titleLarge)
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                            OnboardingStep("1","Verify your email")
                            OnboardingStep("2","Share driver and vehicle documents")
                            OnboardingStep("3","Wait for compliance review")
                            Text("Assignments become available only after your account and vehicle are approved.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    TextButton(onClick={
                        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", state.supportEmail, null))
                        showSupportAddress = runCatching { context.startActivity(emailIntent) }.isFailure
                    }) {
                        Icon(MireliIcons.HeadsetMic,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Contact driver support")
                    }
                    if (showSupportAddress) Text("Email driver support at ${state.supportEmail}.")
                }
                item {Text("Do not email identity documents. Upload them only through the private in-app flow when it is available.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }else {
                item {FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    OutlinedButton(onClick=vm::refresh,enabled=!state.loading) {
                        Icon(MireliIcons.Refresh,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Refresh account")
                    }
                    TextButton(onClick=vm::signOut,enabled=!state.loading){Text("Sign out")}
                }}
                if(state.pendingTripAction)item {Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("One saved trip action needs confirmation",style=MaterialTheme.typography.titleMedium)
                    Text("The app keeps the same action ID after a connection failure or restart. Check it before making another trip change.")
                    Button(onClick=vm::retryTripAction,enabled=!state.loading){Text("Check saved action")}
                }}}
                if(section in listOf(4,3) && !state.loading && !state.tripDataCurrent)item {
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
                    item {Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        DriverHomeOverview(onboarding?.optJSONObject("profile"),app,state.eligibility?.optBoolean("eligible")==true,
                            trips,state.loading,activeNavigation=activeTrip?.let { trip -> {
                                Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
                                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                        TripNavigation(trip,onRequestRoute=routeRequest)
                                        OutlinedButton(onClick={tripHistory=false;section=3},modifier=Modifier.fillMaxWidth()){Text("Open journey & passengers")}
                                    }
                                }
                            } }) {
                                if(it==3)tripHistory=false
                                section=it
                            }
                    }}
                }else if(section==0&&onboarding!=null&&app!=null) {
                    item {DriverHeading("Driver account","Manage your profile, vehicle and documents.");AppearanceToggle(showSettings=false)}
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
                    item {DriverHeading("Earnings & payouts","Track each settlement and your payout destination.")}
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
                        DriverHeading("Trips","Mombasa SGR transfers assigned to you")
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
    }
}

@Composable
private fun SignInBoard(verifyingEmail:Boolean) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=StationNavy) {
        Row(Modifier.padding(start=18.dp,end=8.dp,top=14.dp,bottom=14.dp),
            horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(if(verifyingEmail)"Verify your email" else "Driver sign-in",Modifier.weight(1f).testTag("driver_sign_in_title"),
                color=Color.White,style=MaterialTheme.typography.headlineMedium)
            StationLines(Modifier.size(width=76.dp,height=64.dp))
        }
    }
}

@Composable
private fun SignInServiceNotice(state:DriverServiceState) {
    val open = state.emailSignInOpen == true
    val title = when {
        open -> "New driver applications are paused"
        state.emailSignInOpen == false -> "Driver sign-in is closed"
        state.loading -> "Checking driver sign-in"
        else -> "Driver service unavailable"
    }
    val detail = state.error?.takeIf { !open } ?: when {
        open -> "Use the email linked to your existing Mireli driver account."
        state.emailSignInOpen == false -> state.serviceMessage ?: "Check again later or contact driver support."
        state.loading -> "Checking whether email sign-in is available."
        else -> "Check your connection and try again."
    }
    DriverNotice(if(open)MireliIcons.Info else MireliIcons.Lock,title,detail)
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
        Text("Driver & vehicle",style=MaterialTheme.typography.titleLarge)
        OutlinedTextField(contactPhone,{contactPhone=it.take(20)},label={Text("Kenyan contact phone")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),singleLine=true,enabled=editable&&storedPhone.isBlank(),modifier=Modifier.fillMaxWidth())
        if (storedPhone.isNotBlank()) Text("Contact Support to change this number.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(name,{name=it.take(100)},label={Text("Driver full name")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(plate,{plate=it.take(15).uppercase()},label={Text("Registration plate")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(capacity,{capacity=it.filter(Char::isDigit).take(2)},label={Text("Passenger seats")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(licence,{licence=it.take(20)},label={Text("Licence class")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(cab,{cab=it.take(80)},label={Text("Vehicle type")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().toggleable(owner,enabled=editable,role=Role.Checkbox,onValueChange={owner=it})){Checkbox(owner,null);Text("I own this vehicle")}
        Button(onClick={save(name,plate,capacity,licence,cab,owner,contactPhone)},enabled=editable&&valid&&changed,modifier=Modifier.fillMaxWidth()){Text("Save profile to Mireli")}
    }
}
