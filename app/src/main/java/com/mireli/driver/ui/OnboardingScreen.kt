package com.mireli.driver.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mireli.driver.OnboardingViewModel
import com.mireli.driver.domain.*

@Composable
fun OnboardingScreen(onClose: () -> Unit, vm: OnboardingViewModel = viewModel()) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val unreadable by vm.unreadable.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var selectedDocument by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteAll by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = selectedDocument
        selectedDocument = null
        if (uri != null && id != null) vm.attach(id, uri)
    }
    BackHandler { if (step > 0) step-- else onClose() }
    Scaffold(bottomBar = {
        Surface(shadowElevation = 4.dp) {
            Row(Modifier.navigationBarsPadding().fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { if (step == 0) onClose() else step-- }, enabled = !busy) { Text("Back") }
                if (step < 2) Button(onClick = { step++ }, enabled = !busy && !unreadable && draft.localNoticeAccepted,
                    modifier = Modifier.weight(1f)) { Text(if (step == 0) "Documents" else "Review application") }
                else Button(onClick = vm::check, enabled = !busy && !unreadable, modifier = Modifier.weight(1f)) { Text("Check application") }
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding(), contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                DriverBrandHeader("Driver application")
                Spacer(Modifier.height(20.dp))
                DriverHeading("Let’s set up your account", "Your driver profile, vehicle and supporting documents.")
                Spacer(Modifier.height(20.dp))
                DriverStepProgress(step,listOf("Your profile", "Upload", "Review"))
                Text("${step + 1} of 3 · ${listOf("Driver & vehicle", "Documents", "Review")[step]}", color = MaterialTheme.colorScheme.primary)
                LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text("TEST BUILD · Use sample details and documents only. Drafts are encrypted on this phone. Nothing is uploaded to Mireli and no driver approval is granted.", Modifier.padding(16.dp))
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (errors.isNotEmpty()) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Needs attention", fontWeight = FontWeight.Bold)
                        errors.forEach { Text("• $it") }
                    }
                }
            }
            if (!unreadable) when (step) {
                0 -> item { ProfileEditor(draft, busy, vm::saveProfile) }
                1 -> {
                    item { Text("Attach a readable PDF, JPEG or PNG (up to 10 MB each). Use the document's actual expiry; no automatic validity period is assumed.") }
                    items(OnboardingPolicy.applicable(draft.ownsVehicle), key = { it.id }) { requirement ->
                        DocumentEditor(requirement, draft.evidence[requirement.id], busy,
                            attach = { selectedDocument = requirement.id; picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png")) },
                            expiry = { vm.expiry(requirement.id, it) }, remove = { vm.remove(requirement.id) })
                    }
                }
                2 -> {
                    item {
                        Text(draft.fullName.ifBlank { "Driver name missing" }, style = MaterialTheme.typography.titleLarge)
                        Text("${draft.plate.ifBlank { "Vehicle missing" }} · ${draft.capacity.ifBlank { "—" }} passenger seats")
                        Text("Phone verification: not connected")
                        Text("Documents saved: ${draft.evidence.size}")
                    }
                    items(OnboardingPolicy.applicable(draft.ownsVehicle).filter { it.required }, key = { it.id }) { requirement ->
                        Text("${if (draft.evidence.containsKey(requirement.id)) "Saved locally" else "Missing"} · ${requirement.title}")
                    }
                    item {
                        Card {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (draft.checkedAt != null) "Local checklist complete" else "Ready for a completeness check", fontWeight = FontWeight.Bold)
                                Text("Submission: not connected. Review: not started. Driver eligibility: not approved.")
                                Text("The live service must verify your phone, receive the documents privately and record a compliance officer's decision before you can accept real journeys.")
                                Text("Company contact: mirelisgr001@gmail.com. Do not email identity documents.")
                            }
                        }
                    }
                }
            }
            item { TextButton(onClick = { deleteAll = true }, enabled = !busy) { Text("Delete local application and attachments") } }
        }
    }
    if (deleteAll) AlertDialog(onDismissRequest = { deleteAll = false }, title = { Text("Delete local draft?") },
        text = { Text("This removes the encrypted copies saved by this app. Original files elsewhere on your phone are not changed. No server records exist for this local draft.") },
        confirmButton = { TextButton(onClick = { deleteAll = false; step = 0; vm.clear() }) { Text("Delete draft") } },
        dismissButton = { TextButton(onClick = { deleteAll = false }) { Text("Keep draft") } })
}

@Composable
private fun ProfileEditor(draft: OnboardingDraft, busy: Boolean, save: (OnboardingDraft) -> Unit) {
    var name by rememberSaveable(draft.fullName) { mutableStateOf(draft.fullName) }
    var phone by rememberSaveable(draft.phone) { mutableStateOf(draft.phone) }
    var plate by rememberSaveable(draft.plate) { mutableStateOf(draft.plate) }
    var capacity by rememberSaveable(draft.capacity) { mutableStateOf(draft.capacity) }
    var licence by rememberSaveable(draft.licenceClass) { mutableStateOf(draft.licenceClass) }
    var owner by rememberSaveable(draft.ownsVehicle) { mutableStateOf(draft.ownsVehicle) }
    var notice by rememberSaveable(draft.localNoticeAccepted) { mutableStateOf(draft.localNoticeAccepted) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(name, { name = it.take(100) }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
        OutlinedTextField(phone, { phone = it.take(20) }, label = { Text("Kenyan mobile number") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
        OutlinedTextField(plate, { plate = it.take(15).uppercase() }, label = { Text("Vehicle registration") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
        OutlinedTextField(capacity, { capacity = it.filter(Char::isDigit).take(2) }, label = { Text("Passenger capacity") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
        OutlinedTextField(licence, { licence = it.take(20) }, label = { Text("Driving licence class") }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy)
        Row(Modifier.fillMaxWidth().toggleable(owner, enabled = !busy, role = Role.Checkbox, onValueChange = { owner = it })) { Checkbox(owner, null, enabled = !busy); Text("I own this vehicle", Modifier.padding(top = 12.dp)) }
        Row(Modifier.fillMaxWidth().toggleable(notice, enabled = !busy, role = Role.Checkbox, onValueChange = { notice = it })) { Checkbox(notice, null, enabled = !busy); Text("I understand this is a local test draft. I will use sample documents; nothing is submitted or approved.", Modifier.padding(top = 8.dp)) }
        Button(onClick = { save(draft.copy(fullName = name.trim(), phone = phone.trim(), plate = plate.trim(), capacity = capacity,
            licenceClass = licence.trim(), ownsVehicle = owner, localNoticeAccepted = notice)) }, enabled = !busy && notice,
            modifier = Modifier.fillMaxWidth()) { Text("Save driver & vehicle") }
        Text("Save your changes before continuing. The saved draft survives app restarts.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DocumentEditor(requirement: DocumentRequirement, evidence: DocumentEvidence?, busy: Boolean,
    attach: () -> Unit, expiry: (String) -> Unit, remove: () -> Unit) {
    var date by rememberSaveable(evidence?.storageId, evidence?.expiry) { mutableStateOf(evidence?.expiry ?: "") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(requirement.title, style = MaterialTheme.typography.titleMedium)
            Text(if (requirement.required) "Required by this checklist" else "Optional · only if requested", style = MaterialTheme.typography.labelMedium)
            Text(requirement.guidance, style = MaterialTheme.typography.bodySmall)
            Text(if (evidence == null) "No attachment" else "Encrypted on this phone · ${evidence.size / 1024} KB · not uploaded")
            if (evidence != null && requirement.expires) {
                OutlinedTextField(date, { date = it.take(10) }, label = { Text("Expiry · YYYY-MM-DD") }, singleLine = true, enabled = !busy)
                TextButton(onClick = { expiry(date) }, enabled = !busy && date != evidence.expiry) { Text("Save expiry") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = attach, enabled = !busy) { Text(if (evidence == null) "Attach document" else "Replace document") }
                if (evidence != null) TextButton(onClick = remove, enabled = !busy) { Text("Remove") }
            }
        }
    }
}
