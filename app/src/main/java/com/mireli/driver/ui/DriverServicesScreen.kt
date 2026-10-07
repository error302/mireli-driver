package com.mireli.driver.ui
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mireli.driver.BuildConfig
import com.mireli.driver.R
import com.mireli.driver.DriverServicesViewModel
import org.json.JSONArray
import org.json.JSONObject

private fun JSONArray.objects()=(0 until length()).map {getJSONObject(it)}
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
    var phone by rememberSaveable {mutableStateOf("")};var code by rememberSaveable {mutableStateOf("")}
    var section by rememberSaveable {mutableIntStateOf(0)};var accepted by rememberSaveable {mutableStateOf(false)}
    var supportCategory by rememberSaveable {mutableStateOf("payout")};var supportMessage by rememberSaveable {mutableStateOf("")}
    var payoutName by rememberSaveable {mutableStateOf("")};var payoutAcknowledged by rememberSaveable {mutableStateOf(false)}
    var tripHistory by rememberSaveable {mutableStateOf(false)}
    var clock by remember {mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(state.challengeId,state.resendAt){code="";while(state.challengeId!=null&&clock<state.resendAt){clock=System.currentTimeMillis();delay(1000)};clock=System.currentTimeMillis()}
    var selectedType by rememberSaveable {mutableStateOf<String?>(null)};var selectedExpiry by rememberSaveable {mutableStateOf("")}
    val uriHandler=LocalUriHandler.current
    val listState=rememberLazyListState();val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    LaunchedEffect(state.signedIn,section){focus.clearFocus();keyboard?.hide();listState.scrollToItem(0)}
    LaunchedEffect(state.supportSent){if(state.supportSent>0){supportMessage="";focus.clearFocus();keyboard?.hide()}}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->val type=selectedType;selectedType=null;if(uri!=null&&type!=null)vm.upload(type,selectedExpiry,uri)}
    val onboarding=state.onboarding;val app=onboarding?.getJSONObject("application")
    Scaffold(bottomBar={if(state.signedIn)NavigationBar {
        NavigationBarItem(section==3,{section=3},icon={Icon(MireliIcons.Route,null)},label={Text("Today")})
        NavigationBarItem(section==0,{section=0},icon={Icon(MireliIcons.PersonOutline,null)},label={Text("Onboarding")})
        NavigationBarItem(section==1,{section=1},icon={Icon(MireliIcons.AccountBalanceWallet,null)},label={Text("Earnings")})
        NavigationBarItem(section==2,{section=2},icon={Icon(MireliIcons.HeadsetMic,null)},label={Text("Support")})
    }}){padding->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(),state=listState,contentPadding=PaddingValues(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){Image(painterResource(R.drawable.mireli_driver_logo),"Mireli",Modifier.size(46.dp));Column{Text("Mireli Driver",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(if(state.signedIn)"Your driver account" else "Mombasa · SGR transfers",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
            if(state.signedIn)item {AppearanceToggle()}
            if(onClose!=null)item {TextButton(onClick=onClose){Text("Back to preview")}}
            if(BuildConfig.DRIVER_SERVICE_TEST)item {Card {Text("STAGING · Test data; no real SMS or payouts.",Modifier.padding(16.dp))}}
            if(state.loading)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
            state.error?.takeIf{state.signedIn||state.registrationOpen==true}?.let {error->item {Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)){Text(error,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onErrorContainer)}} }
            if(!state.signedIn) {
                item {AppearanceToggle()}
                item {
                    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                                    Icon(MireliIcons.Lock,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(10.dp).size(20.dp))
                                }
                                Text("DRIVER ACCOUNT",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
                            }
                            Text("Sign in to drive",Modifier.testTag("driver_sign_in_title"),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
                            Text(if(state.phoneSignInOpen==true&&state.registrationOpen!=true)"Use the number linked to your existing driver account. New applications are paused."
                                else "Use your Kenyan mobile number to sign in or begin your driver application.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedTextField(phone,{phone=it.take(20)},label={Text("Kenyan mobile number")},placeholder={Text("07XX XXX XXX")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone),singleLine=true,enabled=state.challengeId==null,modifier=Modifier.fillMaxWidth().testTag("driver_phone_field"))
                            if(state.challengeId==null) {
                                Button(onClick={vm.requestCode(phone)},enabled=!state.loading&&state.phoneSignInOpen==true&&phone.isNotBlank(),modifier=Modifier.fillMaxWidth().heightIn(min=54.dp).testTag("driver_send_code"),shape=RoundedCornerShape(16.dp)) {
                                    Text(if(state.phoneSignInOpen==true)"Continue with phone" else if(state.loading)"Checking sign-in…" else "Sign-in unavailable")
                                }
                                Text("We’ll request a one-time SMS code only when phone sign-in is available.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("Code requested for ${state.challengePhone}",style=MaterialTheme.typography.bodyMedium)
                                TextButton(onClick={vm.changePhone();code=""},enabled=!state.loading){Text("Use a different number")}
                                state.demoCode?.let {sample->Text("Sample verification code: $sample",style=MaterialTheme.typography.bodySmall)}
                                OutlinedTextField(code,{code=it.filter(Char::isDigit).take(6)},label={Text("6-digit verification code")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),singleLine=true,enabled=!state.loading,modifier=Modifier.fillMaxWidth())
                                Button(onClick={vm.verifyCode(code)},enabled=!state.loading&&code.length==6,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(16.dp)){Text("Verify and continue")}
                                TextButton(onClick={vm.requestCode(state.challengePhone?:phone)},enabled=!state.loading&&clock>=state.resendAt){Text(if(clock<state.resendAt)"Resend in ${((state.resendAt-clock+999)/1000).coerceAtLeast(0)}s" else "Resend verification code")}
                            }
                        }
                    }
                }
                if(state.phoneSignInOpen!=true||state.registrationOpen!=true)item {
                    Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Icon(MireliIcons.Info,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(20.dp))
                                Text(when {state.error!=null->"Driver service unavailable";state.phoneSignInOpen==true->"New driver applications are paused";state.loading->"Connecting to driver sign-in";else->"Driver sign-in is not connected"},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                            }
                            Text(state.error?:when {
                                state.phoneSignInOpen==true&&state.registrationOpen!=true->"Existing drivers can sign in, but Mireli is not accepting new driver applications right now."
                                state.loading->"Checking whether the live driver service can accept a sign-in request."
                                else->state.serviceMessage?:"We can’t confirm phone sign-in at this server address yet."
                            },style=MaterialTheme.typography.bodyMedium)
                            Text("No SMS code has been sent. Checking service status does not send a code or determine driver eligibility.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick=vm::refreshServiceStatus,enabled=!state.loading,shape=RoundedCornerShape(14.dp)){Text("Retry connection")}
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth().testTag("driver_sign_in_promo"),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                Icon(MireliIcons.Train,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(24.dp))
                                Text("MOMBASA · SGR CONNECTION",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.SemiBold)
                            }
                            Text("Make the SGR journey easier.",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
                            Text("Join local drivers helping travellers connect with Mombasa’s SGR terminus.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                            OnboardingStep("1","Verify your phone")
                            OnboardingStep("2","Share driver and vehicle documents")
                            OnboardingStep("3","Wait for compliance review")
                            Text("Assignments become available only after your account and vehicle are approved.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {TextButton(onClick={uriHandler.openUri("mailto:mirelisgr001@gmail.com")}){Icon(MireliIcons.HeadsetMic,null,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("Contact driver support")}}
                item {Text("Do not email identity documents. Upload them only through the private in-app flow when it is available.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }else {
                item {Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){OutlinedButton(onClick=vm::refresh,enabled=!state.loading){Text("Refresh account")};TextButton(onClick=vm::signOut,enabled=!state.loading){Text("Sign out")}}}
                if(state.pendingTripAction)item {Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    Text("One saved trip action needs confirmation",style=MaterialTheme.typography.titleMedium)
                    Text("The app keeps the same action ID after a connection failure or restart. Check it before making another trip change.")
                    Button(onClick=vm::retryTripAction,enabled=!state.loading){Text("Check saved action")}
                }}}
                if(section==0&&onboarding!=null&&app!=null) {
                    item {Card {Column(Modifier.padding(16.dp)) {
                        Text("Application: ${app.getString("status").replace('_',' ')}",style=MaterialTheme.typography.titleMedium)
                        Text(if(state.eligibility?.optBoolean("eligible")==true)"Eligible for new assignments" else "Approval required before new assignments")
                        app.optString("reviewReason").takeIf{it.isNotBlank()&&it!="null"}?.let{Text(it)}
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
                            OutlinedButton(onClick={selectedType=id;selectedExpiry=expiry;picker.launch(arrayOf("application/pdf","image/jpeg","image/png"))},enabled=!state.loading&&app.getString("status") in listOf("draft","changes_requested","approved")) {Text(if(evidence==null)"Upload document" else "Replace evidence")}
                        }}
                    }
                    item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("privacyUrl" to "Privacy notice","termsUrl" to "Driver terms").forEach{(key,label)->onboarding.optString(key).takeIf{it.startsWith("https://")}?.let{url->TextButton(onClick={uriHandler.openUri(url)}){Text(label)}}}
                        Row(Modifier.fillMaxWidth().toggleable(accepted,enabled=!state.loading,role=Role.Checkbox,onValueChange={accepted=it})){Checkbox(accepted,null);Text(if(BuildConfig.DRIVER_SERVICE_TEST)"I acknowledge this synthetic application and test checklist." else "I have read the current driver terms and privacy notice.")}
                        Button(onClick={vm.submit(accepted)},enabled=!state.loading&&accepted&&app.getString("status") in listOf("draft","changes_requested"),modifier=Modifier.fillMaxWidth()){Text("Submit for compliance review")}
                    }}
                }else if(section==1) {
                    item {Text("Earnings & payouts",style=MaterialTheme.typography.titleLarge);Text("Paid amounts have confirmed settlement. Processing transfers still await confirmation.")}
                    state.earnings?.let{statement->
                        item {Text("Payout destination: ${statement.optString("destinationMasked","Not set")}")}
                        val destination=statement.getJSONObject("destination")
                        item {Card{Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                            Text("Beneficiary: ${destination.getString("status").replace('_',' ')}",style=MaterialTheme.typography.titleMedium)
                            Text("Payout setup uses your verified account phone. Finance checks your account name and identity evidence before approval. Use Support if you need a different phone.")
                            destination.optString("reviewNote").takeIf{it.isNotBlank()&&it!="null"}?.let{Text(it)}
                            if(destination.getString("status") in listOf("not_configured","rejected")) {
                                OutlinedTextField(payoutName,{payoutName=it.take(100)},label={Text("Name on your M-Pesa account")},enabled=!state.loading,modifier=Modifier.fillMaxWidth())
                                Row(Modifier.fillMaxWidth().toggleable(payoutAcknowledged,enabled=!state.loading,role=Role.Checkbox,onValueChange={payoutAcknowledged=it})){Checkbox(payoutAcknowledged,null);Text("This verified phone is my M-Pesa account.")}
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
                    val visibleTrips=allTrips.filter{(it.getString("phase") in listOf("completed","cancelled"))==tripHistory}
                    item {Text(if(tripHistory)"Recent trip history" else "Your assignments",style=MaterialTheme.typography.titleLarge);Text("Refresh for the latest bookings. Use Support for dispatch changes.");TextButton(onClick={tripHistory=!tripHistory}){Text(if(tripHistory)"Show active assignments" else "View recent trip history")}}
                    if(state.eligibility?.optBoolean("eligible")!=true)item {Card{Text("Current document approval is required to accept, board or start. Your application status is available under Onboarding.",Modifier.padding(16.dp))}}
                    items(visibleTrips,key={it.getString("id")}){trip->
                        LiveTripCard(trip,state.loading||state.pendingTripAction,state.eligibility?.optBoolean("eligible")==true,vm::tripCommand)
                    }
                    if(visibleTrips.isEmpty())item {Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)){Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                        Icon(MireliIcons.Route,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(32.dp))
                        Text(if(tripHistory)"Your journey history starts here" else "Ready for your next assignment",style=MaterialTheme.typography.titleLarge)
                        Text(if(tripHistory)"Completed trips will appear here." else if(state.eligibility?.optBoolean("eligible")!=true)"Complete onboarding first. Once approved, dispatch will assign your first SGR transfer." else "You have no assigned trips yet. Refresh to check for your next SGR transfer.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(!tripHistory&&state.eligibility?.optBoolean("eligible")!=true)OutlinedButton(onClick={section=0}){Text("Continue onboarding")}
                    }}}
                }else if(section==2) {
                    item {Text("Driver support",style=MaterialTheme.typography.titleLarge);Text("Create a case for a document, account or payout issue. This is not an emergency channel; contact local emergency services directly if immediate help is needed.")}
                    if(state.supportSent>0)item {Text("Support case sent. Mireli's replies appear below.",color=MaterialTheme.colorScheme.primary)}
                    item {Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("payout","documents","account").forEach{category->FilterChip(selected=supportCategory==category,onClick={supportCategory=category},label={Text(category.replaceFirstChar{it.uppercase()})})}}}
                    item {OutlinedTextField(supportMessage,{supportMessage=it.take(2000)},label={Text("How can we help?")},modifier=Modifier.fillMaxWidth(),minLines=3,enabled=!state.loading)}
                    item {Button(onClick={vm.support(supportCategory,supportMessage)},enabled=!state.loading&&supportMessage.trim().length>=10,modifier=Modifier.fillMaxWidth()){Text("Send support case")}}
                    item {Text("Keep ID numbers and document images out of support messages. Use the private document upload flow.",style=MaterialTheme.typography.bodySmall)}
                    items(state.support?.getJSONArray("cases")?.objects()?:emptyList(),key={it.getString("id")}){case->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("${case.getString("category")} · ${case.getString("status")}",style=MaterialTheme.typography.titleMedium);Text("Reference: ${case.getString("id")}",style=MaterialTheme.typography.bodySmall);Text(case.getString("message"))
                        case.optString("reply").takeIf{it.isNotBlank()&&it!="null"}?.let{Text("Mireli: $it")}
                    }}}
                }
            }
        }
    }
}
@Composable private fun ServerProfile(data:JSONObject,busy:Boolean,save:(String,String,String,String,String,Boolean)->Unit) {
    val profile=data.getJSONObject("profile");val app=data.getJSONObject("application");val version=app.getInt("version")
    var name by rememberSaveable(version){mutableStateOf(profile.getString("fullName").takeIf{it!="New applicant"}?:"")}
    var plate by rememberSaveable(version){mutableStateOf(profile.getString("plate"))};var capacity by rememberSaveable(version){mutableStateOf(profile.getInt("capacity").toString())}
    var licence by rememberSaveable(version){mutableStateOf(app.getString("licenceClass"))};var cab by rememberSaveable(version){mutableStateOf(profile.getString("cabType"))}
    var owner by rememberSaveable(version){mutableStateOf(app.getBoolean("ownsVehicle"))}
    val editable=!busy&&app.getString("status") in listOf("draft","changes_requested","approved")
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Driver & vehicle",style=MaterialTheme.typography.titleLarge);Text("Verified phone: ${profile.getString("phone")}")
        OutlinedTextField(name,{name=it.take(100)},label={Text("Driver full name")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(plate,{plate=it.take(15).uppercase()},label={Text("Registration plate")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(capacity,{capacity=it.filter(Char::isDigit).take(2)},label={Text("Passenger seats")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(licence,{licence=it.take(20)},label={Text("Licence class")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(cab,{cab=it.take(80)},label={Text("Vehicle type")},singleLine=true,enabled=editable,modifier=Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().toggleable(owner,enabled=editable,role=Role.Checkbox,onValueChange={owner=it})){Checkbox(owner,null);Text("I own this vehicle")}
        Button(onClick={save(name,plate,capacity,licence,cab,owner)},enabled=editable,modifier=Modifier.fillMaxWidth()){Text("Save profile to Mireli")}
    }
}
