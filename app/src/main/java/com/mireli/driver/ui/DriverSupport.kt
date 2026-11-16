package com.mireli.driver.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mireli.driver.DriverServiceState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val activityTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.forLanguageTag("en-KE"))
    .withZone(ZoneId.of("Africa/Nairobi"))

internal fun activityTimestamp(raw: String): String? =
    runCatching { activityTime.format(Instant.parse(raw)) }.getOrNull()

internal fun LazyListScope.driverSupportItems(
    state: DriverServiceState,
    editDraft: (String, String) -> Unit,
    send: () -> Unit,
    refresh: () -> Unit,
) {
    item {
        val context = LocalContext.current
        var contactError by remember { mutableStateOf<String?>(null) }
        DepartureNote(state.supportEmail)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                contactError = runCatching {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", state.supportEmail, null)))
                }.exceptionOrNull()?.let { "Open your email app and write to ${state.supportEmail}." }
            }) { Text("Email support") }
            state.supportPhone?.let { phone ->
                TextButton(onClick = {
                    contactError = runCatching {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
                    }.exceptionOrNull()?.let { "Call $phone from your phone app." }
                }) { Text("Call $phone") }
            }
        }
        contactError?.let { DepartureNote(it) }
    }
    item {
        val draft = state.supportDraft
        val pending = draft.requestId != null
        val editable = !state.loading && !pending
        if (state.supportSent > 0) {
            Text("Support case sent. Replies appear under Your cases.", color = MaterialTheme.colorScheme.primary)
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("trip", "account", "documents", "payout", "safety").forEach { category ->
                FilterChip(
                    selected = draft.category == category,
                    onClick = { editDraft(category, draft.message) },
                    enabled = editable,
                    label = { Text(category.readableStatus()) },
                )
            }
        }
        TextField(
            draft.message, { editDraft(draft.category, it) },
            label = { Text("How can we help?") }, modifier = Modifier.fillMaxWidth(),
            minLines = 3, readOnly = !editable, colors = departureFieldColors(),
        )
        DepartureNote(
            if (pending) "Delivery is unconfirmed. Retry when you’re connected."
            else "Keep ID numbers and documents out of support messages."
        )
        DepartureButton(
            onClick = send,
            enabled = !state.loading && draft.message.trim().length >= 10,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (pending) "Retry sending" else "Send support case") }
    }
    val rows = state.support?.optJSONArray("cases")
    val cases = (0 until (rows?.length() ?: 0)).mapNotNull { rows?.optJSONObject(it) }
    item {
        DepartureSection("Your cases")
        when {
            state.supportError != null -> DepartureNote(state.supportError)
            state.support == null && state.loading -> DepartureNote("Loading support cases…")
            state.support != null && cases.isEmpty() -> DepartureNote("You haven’t sent any support cases yet.")
        }
        TextButton(onClick = refresh, enabled = !state.loading) { Text("Refresh cases") }
    }
    items(cases, key = { it.getString("id") }) { case ->
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HorizontalDivider()
            Text("${case.getString("category").readableStatus()} · ${case.getString("status").readableStatus()}",
                style = MaterialTheme.typography.titleMedium)
            Text("Reference ${case.getString("id")}", style = MaterialTheme.typography.bodySmall)
            activityTimestamp(case.optString("createdAt"))?.let { DepartureNote("Sent $it") }
            Text(case.getString("message"))
            (case.opt("reply") as? String)?.takeIf { it.isNotBlank() }?.let { reply ->
                activityTimestamp(case.optString("updatedAt"))?.let { DepartureNote("Mireli replied $it") }
                Text(reply)
            }
        }
    }
}
