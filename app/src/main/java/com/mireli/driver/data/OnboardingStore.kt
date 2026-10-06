package com.mireli.driver.data

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.mireli.driver.domain.*
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-local drafts only. AES-GCM keys remain in Android Keystore; no URI grants retained. */
class OnboardingStore(private val context: Context) {
    private val directory = File(context.noBackupFilesDir, "onboarding-v1").apply { mkdirs() }
    private val draftFile = File(directory, "draft.enc")
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(file: File, bytes: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.iv + cipher.doFinal(bytes)
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(encrypted); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
    private fun decrypt(file: File): ByteArray {
        val bytes = AtomicFile(file).openRead().use { it.readBytes() }
        require(bytes.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size))
    }
    fun load(): OnboardingDraft {
        if (!draftFile.exists() && !File(directory, "draft.enc.bak").exists()) return OnboardingDraft()
        val json = JSONObject(decrypt(draftFile).toString(Charsets.UTF_8))
        val docs = json.getJSONObject("evidence")
        val evidence = docs.keys().asSequence().associateWith { id ->
            val row = docs.getJSONObject(id)
            DocumentEvidence(row.getString("storageId"), row.getString("mime"), row.getInt("size"),
                row.optString("expiry"), row.getString("attachedAt"))
        }
        return OnboardingDraft(json.optString("fullName"), json.optString("phone"), json.optString("plate"),
            json.optString("capacity"), json.optString("licenceClass"), json.optBoolean("ownsVehicle", true),
            json.optBoolean("localNoticeAccepted"), evidence,
            if (json.isNull("checkedAt")) null else json.getString("checkedAt"))
    }
    fun save(draft: OnboardingDraft) {
        val docs = JSONObject()
        draft.evidence.forEach { (id, item) -> docs.put(id, JSONObject().put("storageId", item.storageId)
            .put("mime", item.mime).put("size", item.size).put("expiry", item.expiry).put("attachedAt", item.attachedAt)) }
        val json = JSONObject().put("fullName", draft.fullName).put("phone", draft.phone).put("plate", draft.plate)
            .put("capacity", draft.capacity).put("licenceClass", draft.licenceClass).put("ownsVehicle", draft.ownsVehicle)
            .put("localNoticeAccepted", draft.localNoticeAccepted).put("evidence", docs)
            .put("checkedAt", draft.checkedAt ?: JSONObject.NULL).put("policyVersion", OnboardingPolicy.VERSION)
        encrypt(draftFile, json.toString().toByteArray())
    }
    fun attach(uri: Uri): DocumentEvidence {
        val mime = context.contentResolver.getType(uri) ?: error("Choose a PDF, JPEG or PNG file.")
        require(mime in setOf("application/pdf", "image/jpeg", "image/png")) { "Only PDF, JPEG and PNG files are accepted." }
        val input = context.contentResolver.openInputStream(uri) ?: error("The selected document could not be opened.")
        return importDocument(mime, input)
    }
    internal fun importDocument(mime: String, input: java.io.InputStream): DocumentEvidence {
        val bytes = input.use {
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                require(output.size() + read <= MAX_BYTES) { "Choose a document smaller than 10 MB." }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
        require(validSignature(mime, bytes)) { "The file content does not match its document type." }
        val id = UUID.randomUUID().toString()
        encrypt(File(directory, "$id.enc"), bytes)
        return DocumentEvidence(id, mime, bytes.size, attachedAt = Instant.now().toString())
    }
    fun remove(item: DocumentEvidence) {
        require(Regex("[0-9a-f-]{36}").matches(item.storageId))
        val file = File(directory, "${item.storageId}.enc")
        check(!file.exists() || file.delete()) { "Could not remove the local attachment." }
    }
    fun validateAttachments(draft: OnboardingDraft): List<String> = draft.evidence.mapNotNull { (id, item) ->
        val valid = runCatching {
            require(Regex("[0-9a-f-]{36}").matches(item.storageId))
            val bytes = decrypt(File(directory, "${item.storageId}.enc"))
            bytes.size == item.size && validSignature(item.mime, bytes)
        }.getOrDefault(false)
        if (valid) null else "${OnboardingPolicy.documents.find { it.id == id }?.title ?: "Document"} cannot be read. Replace its attachment."
    }
    fun attachmentBytes(item:DocumentEvidence):ByteArray {
        require(Regex("[0-9a-f-]{36}").matches(item.storageId))
        val bytes=decrypt(File(directory,"${item.storageId}.enc"))
        require(bytes.size==item.size && validSignature(item.mime,bytes))
        return bytes
    }
    fun clear() {
        directory.listFiles()?.forEach { check(it.delete()) { "Could not clear the local draft." } }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(KEY) }
    }
    companion object {
        private const val KEY = "mireli_onboarding_v1"
        const val MAX_BYTES = 10 * 1024 * 1024
        fun validSignature(mime: String, bytes: ByteArray): Boolean = when (mime) {
            "application/pdf" -> bytes.size >= 5 && bytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-"
            "image/jpeg" -> bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
            "image/png" -> bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10))
            else -> false
        }
    }
}
