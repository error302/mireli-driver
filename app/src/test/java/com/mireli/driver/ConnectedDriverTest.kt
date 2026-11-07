package com.mireli.driver

import com.mireli.driver.data.DriverServiceException
import com.mireli.driver.data.OnboardingStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ConnectedDriverTest {
    @Test fun receiptMustMatchTheTripVersionAndActionBeforeDiscardingTheSavedCommand() {
        val command = JSONObject().put("action", "board").put("expectedVersion", 2)
        val receipt = JSONObject().put("tripId", "trip-a").put("version", 3).put("phase", "at_pickup")
        assertTrue(receipt.confirmsTripAction("/trips/trip-a/commands", command))
        assertFalse(receipt.confirmsTripAction("/trips/trip-b/commands", command))
        assertFalse(receipt.put("version", "3").confirmsTripAction("/trips/trip-a/commands", command))
        assertFalse(receipt.put("version", 2).confirmsTripAction("/trips/trip-a/commands", command))
        assertFalse(receipt.put("version", 3).put("phase", "completed").confirmsTripAction("/trips/trip-a/commands", command))
    }

    @Test fun onlyKnownApiRejectionsCanClearPendingCommandsOrSessions() {
        assertTrue(DriverServiceException(401, "expired", "SESSION_EXPIRED", true).sessionRejected)
        assertFalse(DriverServiceException(401, "invalid code", "INVALID_CHALLENGE", false).sessionRejected)
        assertFalse(DriverServiceException(401, "proxy error", usedSession = true).sessionRejected)
        assertTrue(DriverServiceException(404, "reassigned", "ASSIGNMENT_NOT_FOUND", true).tripActionRejected)
        assertFalse(DriverServiceException(404, "proxy error", usedSession = true).tripActionRejected)
        assertFalse(DriverServiceException(408, "timeout", "TIMEOUT", true).tripActionRejected)
        assertFalse(DriverServiceException(429, "limited", "RATE_LIMITED", true).tripActionRejected)
    }

    @Test fun connectedUploadAcceptsTheExactLimitAndRejectsOversizedOrMismatchedContent() {
        val pdf = "%PDF-1.4\nSynthetic test\n%%EOF".toByteArray()
        assertArrayEquals(pdf, OnboardingStore.readDocument("application/pdf", pdf.inputStream(), pdf.size))
        assertThrows(IllegalArgumentException::class.java) {
            OnboardingStore.readDocument("application/pdf", pdf.inputStream(), pdf.size - 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OnboardingStore.readDocument("image/png", pdf.inputStream(), pdf.size)
        }
    }
}
