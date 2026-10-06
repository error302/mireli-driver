package com.mireli.driver

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.mireli.driver.data.OnboardingStore
import com.mireli.driver.domain.OnboardingDraft
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OnboardingStoreTest {
    @Test fun encryptedDraftSurvivesRestartAndCanBeDeleted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OnboardingStore(context)
        store.clear()
        try {
            val sample = OnboardingDraft(fullName = "SAMPLE PRIVATE DRIVER", phone = "0712345678", localNoticeAccepted = true)
            store.save(sample)
            assertEquals(sample, OnboardingStore(context).load())
            val bytes = File(context.noBackupFilesDir, "onboarding-v1/draft.enc").readBytes()
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains(sample.fullName))
            store.clear()
            assertEquals(OnboardingDraft(), store.load())
        } finally { store.clear() }
    }
    @Test fun changedCiphertextFailsClosed() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OnboardingStore(context)
        store.clear()
        try {
            store.save(OnboardingDraft(fullName = "Sample"))
            val file = File(context.noBackupFilesDir, "onboarding-v1/draft.enc")
            val bytes = file.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file.writeBytes(bytes)
            assertTrue(runCatching { store.load() }.isFailure)
        } finally { store.clear() }
    }
    @Test fun renamedExecutableCannotMasqueradeAsPdf() {
        assertFalse(OnboardingStore.validSignature("application/pdf", "MZ executable".toByteArray()))
        assertTrue(OnboardingStore.validSignature("application/pdf", "%PDF-1.7 sample".toByteArray()))
    }
    @Test fun attachmentsAreEncryptedBoundedAndRemovable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = OnboardingStore(context)
        store.clear()
        try {
            val sample = "%PDF-1.7\nSYNTHETIC DOCUMENT ONLY".toByteArray()
            val evidence = store.importDocument("application/pdf", sample.inputStream())
            val file = File(context.noBackupFilesDir, "onboarding-v1/${evidence.storageId}.enc")
            assertTrue(file.exists())
            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("SYNTHETIC DOCUMENT"))
            assertEquals(sample.size, evidence.size)
            store.remove(evidence)
            assertFalse(file.exists())
            val oversized = ByteArray(OnboardingStore.MAX_BYTES + 1)
            sample.copyInto(oversized)
            assertTrue(runCatching { store.importDocument("application/pdf", oversized.inputStream()) }.isFailure)
        } finally { store.clear() }
    }
}
