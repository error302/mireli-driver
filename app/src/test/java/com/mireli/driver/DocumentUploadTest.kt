package com.mireli.driver

import com.mireli.driver.data.OnboardingStore
import org.junit.Assert.*
import org.junit.Test

class DocumentUploadTest {
    @Test fun uploadPreservesTheOriginalPdfBytes() {
        val original = "%PDF-1.4\n% synthetic fixture\n%%EOF".toByteArray()
        val (type, bytes) = OnboardingStore.readOriginal(original.inputStream(), original.size)
        assertEquals("application/pdf", type)
        assertArrayEquals(original, bytes)
    }

    @Test fun unsupportedAndOversizedFilesAreRejectedBeforeUpload() {
        val unknown = "pretends to be a PDF".byteInputStream()
        assertThrows(IllegalArgumentException::class.java) { OnboardingStore.readOriginal(unknown, 100) }
        val oversized = "%PDF-1.4\n".byteInputStream()
        assertThrows(IllegalArgumentException::class.java) { OnboardingStore.readOriginal(oversized, 8) }
    }
}
