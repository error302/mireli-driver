package com.mireli.driver.domain

import java.time.LocalDate

/** Product checklist, subject to operator review; not an assertion of statutory validity periods. */
data class DocumentRequirement(val id: String, val title: String, val guidance: String,
    val expires: Boolean = false, val required: Boolean = true, val leasedOnly: Boolean = false)

object OnboardingPolicy {
    const val VERSION = "mireli-checklist-2026-10-04"
    val documents = listOf(
        DocumentRequirement("identity", "National ID or passport", "Clear identity evidence. Use a sample document in this test build."),
        DocumentRequirement("licence", "Driving licence", "Include the licence class and expiry printed on the document.", true),
        DocumentRequirement("psv_badge", "PSV driver badge", "Compliance must verify this with the issuing authority.", true),
        DocumentRequirement("good_conduct", "Certificate of good conduct", "Compliance will verify its issue date and acceptance period."),
        DocumentRequirement("kra_pin", "KRA PIN certificate", "Requirement applicability must be confirmed by the operator."),
        DocumentRequirement("insurance", "PSV insurance", "Cover must apply to this vehicle and the intended passenger service.", true),
        DocumentRequirement("inspection", "Vehicle inspection certificate", "Evidence of roadworthiness for this vehicle.", true),
        DocumentRequirement("speed_governor", "Speed governor certificate", "Compliance must confirm applicability and validity for this vehicle.", true),
        DocumentRequirement("logbook", "Logbook or e-logbook", "Evidence of the vehicle's registered owner."),
        DocumentRequirement("vehicle_permit", "Vehicle PSV licence / route permit", "The operator must verify the permit appropriate to the service.", true),
        DocumentRequirement("lease", "Lease / owner authorization", "Required in this checklist when you do not own the vehicle.", leasedOnly = true),
        DocumentRequirement("medical", "Medical fitness certificate", "Optional here. Supply only if requested by compliance; applicability is unverified.", true, false),
    )
    fun applicable(ownsVehicle: Boolean) = documents.filter { !it.leasedOnly || !ownsVehicle }
    fun normalizePhone(value: String): String? {
        val clean = value.filterNot { it.isWhitespace() || it == '-' }
        val digits = when {
            clean.startsWith("+254") -> clean.drop(1)
            clean.startsWith("0") -> "254" + clean.drop(1)
            else -> clean
        }
        return digits.takeIf { Regex("254[17][0-9]{8}").matches(it) }
    }
    fun validate(draft: OnboardingDraft, today: LocalDate): List<String> = buildList {
        if (!draft.localNoticeAccepted) add("Read and accept the local test-data notice.")
        if (draft.fullName.trim().length !in 3..100) add("Enter the driver's full name (3–100 characters).")
        if (normalizePhone(draft.phone) == null) add("Enter a valid Kenyan mobile number.")
        if (!Regex("[A-Z0-9][A-Z0-9 -]{3,14}").matches(draft.plate.trim().uppercase())) add("Enter the vehicle registration.")
        if (draft.capacity.toIntOrNull() !in 1..60) add("Enter a passenger capacity between 1 and 60.")
        if (draft.licenceClass.trim().isEmpty()) add("Enter the driving licence class.")
        applicable(draft.ownsVehicle).filter { it.required || draft.evidence.containsKey(it.id) }.forEach { requirement ->
            val item = draft.evidence[requirement.id]
            if (item == null) add("Attach ${requirement.title}.")
            else if (requirement.expires) {
                val expiry = runCatching { LocalDate.parse(item.expiry) }.getOrNull()
                if (expiry == null) add("Enter ${requirement.title} expiry as YYYY-MM-DD.")
                else if (expiry.isBefore(today)) add("${requirement.title} has expired. Attach current evidence.")
            }
        }
    }
}

data class DocumentEvidence(val storageId: String, val mime: String, val size: Int,
    val expiry: String = "", val attachedAt: String)
data class OnboardingDraft(
    val fullName: String = "", val phone: String = "", val plate: String = "",
    val capacity: String = "", val licenceClass: String = "", val ownsVehicle: Boolean = true,
    val localNoticeAccepted: Boolean = false, val evidence: Map<String, DocumentEvidence> = emptyMap(),
    val checkedAt: String? = null,
)
