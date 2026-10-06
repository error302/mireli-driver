package com.mireli.driver.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class OnboardingTest {
    private val today = LocalDate.of(2026, 10, 4)
    private fun complete(owns: Boolean = true): OnboardingDraft = OnboardingDraft(
        fullName = "Sample Driver", phone = "0712345678", plate = "KAA 123A", capacity = "10",
        licenceClass = "D1", ownsVehicle = owns, localNoticeAccepted = true,
        evidence = OnboardingPolicy.applicable(owns).filter { it.required }.associate { it.id to
            DocumentEvidence("sample", "application/pdf", 100, "2027-10-04", "2026-10-04T00:00:00Z") })
    @Test fun completeDraftPassesWithoutGrantingApproval() {
        assertTrue(OnboardingPolicy.validate(complete(), today).isEmpty())
        assertNull(complete().checkedAt)
    }
    @Test fun leasedVehicleNeedsOwnerAuthorization() {
        val draft = complete().copy(ownsVehicle = false)
        assertTrue(OnboardingPolicy.validate(draft, today).any { it.contains("Lease") })
        assertTrue(OnboardingPolicy.validate(complete(false), today).isEmpty())
    }
    @Test fun optionalMedicalEvidenceIsNotMandatory() {
        assertFalse(complete().evidence.containsKey("medical"))
        assertTrue(OnboardingPolicy.validate(complete(), today).isEmpty())
    }
    @Test fun expiredEvidenceBlocksReadiness() {
        val draft = complete()
        val expired = draft.evidence.getValue("licence").copy(expiry = "2026-10-03")
        assertTrue(OnboardingPolicy.validate(draft.copy(evidence = draft.evidence + ("licence" to expired)), today).any { it.contains("expired") })
    }
    @Test fun invalidCalendarDateIsRejected() {
        val draft = complete()
        assertTrue(OnboardingPolicy.validate(draft.copy(evidence = draft.evidence + ("licence" to draft.evidence.getValue("licence").copy(expiry = "2027-02-30"))), today).any { it.contains("YYYY-MM-DD") })
    }
    @Test fun missingEvidenceAndNoticeAreReported() {
        val errors = OnboardingPolicy.validate(complete().copy(evidence = emptyMap(), localNoticeAccepted = false), today)
        assertTrue(errors.any { it.contains("notice") }); assertTrue(errors.any { it.contains("insurance") })
    }
    @Test fun phoneNormalizationIsStrict() {
        assertEquals("254712345678", OnboardingPolicy.normalizePhone("+254 712 345 678"))
        assertEquals("254112345678", OnboardingPolicy.normalizePhone("0112345678"))
        assertNull(OnboardingPolicy.normalizePhone("+123456789"))
        assertNull(OnboardingPolicy.normalizePhone("0712345678<script>"))
    }
    @Test fun unreasonableCapacityIsRejected() {
        assertTrue(OnboardingPolicy.validate(complete().copy(capacity = "0"), today).any { it.contains("capacity") })
    }
}
