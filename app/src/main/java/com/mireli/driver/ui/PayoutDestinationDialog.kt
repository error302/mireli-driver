package com.mireli.driver.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mireli.driver.domain.OnboardingPolicy
import org.json.JSONObject

@Composable
internal fun PayoutDestinationDialog(
    statement: JSONObject,
    loading: Boolean,
    error: String?,
    onSave: (String, String, Int, () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val destination = statement.getJSONObject("destination")
    val status = destination.getString("status")
    val version = destination.getInt("version")
    val masked = (statement.opt("destinationMasked") as? String)?.takeIf { it.isNotBlank() }
    val accountName = (destination.opt("accountName") as? String).orEmpty()
    val editable = statement.optBoolean("payoutDestinationPhoneEditable")
    var editVersion by rememberSaveable { mutableStateOf<Int?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val editing = editable && editVersion != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("M-Pesa payouts") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MireliDesign.Space.xs),
            ) {
                DepartureNote("Your beneficiary is the M-Pesa account that receives your Mireli earnings.")
                DepartureValueRow("Payout number", if (status == "not_configured") "Not set" else masked ?: "Not set")
                if (accountName.isNotBlank()) DepartureValueRow("Account holder", accountName)
                DepartureValueRow("Review status", when (status) {
                    "approved" -> "Approved"
                    "pending", "pending_review" -> "In review"
                    "rejected" -> "Needs attention"
                    else -> "Set up required"
                })
                (destination.opt("reviewNote") as? String)?.takeIf { it.isNotBlank() }?.let { DepartureNote(it) }
                if (submitted && (loading || status != "pending_review")) {
                    DepartureNote("Payout details submitted. Refresh Earnings to check the review status.")
                }
                if (status in listOf("pending", "pending_review")) {
                    DepartureNote("Payouts wait while Finance reviews these details.")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

                if (!editable) {
                    DepartureNote("Mireli’s payout service cannot save a new number yet.")
                } else if (editing) {
                    val validPhone = OnboardingPolicy.normalizePhone(phone) != null
                    val changedOnServer = editVersion != version
                    DepartureNote("Enter the name registered on your M-Pesa account. Finance verifies ownership before payouts use the new details.")
                    if (changedOnServer) DepartureNote("These details changed on Mireli. Cancel and reopen the editor to review them.")
                    TextField(name, { name = it.take(100) }, Modifier.fillMaxWidth(),
                        label = { Text("Registered M-Pesa name") }, singleLine = true,
                        enabled = !loading, colors = departureFieldColors())
                    TextField(phone, { phone = it.take(20) }, Modifier.fillMaxWidth(),
                        label = { Text("M-Pesa mobile number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true, enabled = !loading, colors = departureFieldColors())
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(confirmed, enabled = !loading, role = Role.Checkbox, onValueChange = { confirmed = it }),
                        verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(confirmed, onCheckedChange = null)
                        Text("I own this M-Pesa account.")
                    }
                    DepartureButton(
                        onClick = {
                            onSave(name, phone, requireNotNull(editVersion)) {
                                editVersion = null
                                submitted = true
                            }
                        },
                        enabled = !loading && !changedOnServer && confirmed && validPhone && name.trim().length >= 3,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Submit for review") }
                } else {
                    DepartureButton(
                        onClick = {
                            name = accountName
                            phone = ""
                            confirmed = false
                            submitted = false
                            editVersion = version
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (status == "not_configured") "Set up M-Pesa" else "Edit payout details") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (editing) editVersion = null else onDismiss() },
                enabled = !loading,
            ) { Text(if (editing) "Cancel edit" else "Done") }
        },
    )
}
