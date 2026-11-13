package com.mireli.driver.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.mireli.driver.domain.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
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
            if (json.isNull("checkedAt")) null else json.getString("checkedAt"),
            vehicleType = json.optString("vehicleType"))
    }
    fun save(draft: OnboardingDraft) {
        val docs = JSONObject()
        draft.evidence.forEach { (id, item) -> docs.put(id, JSONObject().put("storageId", item.storageId)
            .put("mime", item.mime).put("size", item.size).put("expiry", item.expiry).put("attachedAt", item.attachedAt)) }
        val json = JSONObject().put("fullName", draft.fullName).put("phone", draft.phone).put("plate", draft.plate)
            .put("capacity", draft.capacity).put("licenceClass", draft.licenceClass).put("ownsVehicle", draft.ownsVehicle)
            .put("localNoticeAccepted", draft.localNoticeAccepted).put("evidence", docs)
            .put("vehicleType", draft.vehicleType)
            .put("checkedAt", draft.checkedAt ?: JSONObject.NULL).put("policyVersion", OnboardingPolicy.VERSION)
        encrypt(draftFile, json.toString().toByteArray())
    }
    fun attach(uri: Uri): DocumentEvidence {
        val (mime, input) = openDocument(context, uri)
        return importDocument(mime, input)
    }
    internal fun importDocument(mime: String, input: InputStream): DocumentEvidence {
        val (resolvedMime, bytes) = readDocument(mime, input, MAX_BYTES)
        val id = UUID.randomUUID().toString()
        encrypt(File(directory, "$id.enc"), bytes)
        return DocumentEvidence(id, resolvedMime, bytes.size, attachedAt = Instant.now().toString())
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
    fun clear() {
        directory.listFiles()?.forEach { check(it.delete()) { "Could not clear the local draft." } }
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(KEY) }
    }
    companion object {
        private const val KEY = "mireli_onboarding_v1"
        const val MAX_BYTES = 10 * 1024 * 1024
        private val supportedTypes = setOf("application/pdf", "image/jpeg", "image/png")

        /** What the bytes actually are. Providers often mislabel photos, so the signature wins. */
        internal fun sniffMime(bytes: ByteArray): String? = when {
            bytes.size >= 5 && bytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-" -> "application/pdf"
            bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
            bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 13, 10, 26, 10)) -> "image/png"
            else -> null
        }

        private fun openDocument(context: Context, uri: Uri): Pair<String, InputStream> {
            val mime = context.contentResolver.getType(uri)
            require(mime != null && (mime in supportedTypes || mime.startsWith("image/"))) { "Choose a PDF or photo of the document." }
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("The selected document could not be opened.")
            return mime to input
        }

        /** Connected uploads keep the bounded document in memory and retain no local attachment. */
        internal fun readUpload(context: Context, uri: Uri, serverLimit: Int): Pair<String, ByteArray> {
            val maxBytes = serverLimit.coerceAtMost(MAX_BYTES)
            require(maxBytes > 0) { "Document uploads are currently unavailable." }
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("The selected document could not be opened.")
            return readOriginal(input, maxBytes)
        }

        internal fun readOriginal(input: InputStream, maxBytes: Int): Pair<String, ByteArray> {
            val bytes = readBounded(input, maxBytes)
            val mime = sniffMime(bytes) ?: throw IllegalArgumentException("Choose an original PDF, JPG or PNG file.")
            return mime to bytes
        }

        internal fun readDocument(declaredMime: String, input: InputStream, maxBytes: Int): Pair<String, ByteArray> =
            resolveUpload(declaredMime, readBounded(input, maxBytes), maxBytes)

        private fun readBounded(input: InputStream, maxBytes: Int): ByteArray = input.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                require(read <= maxBytes - output.size()) { "Choose a smaller document. The file exceeds the upload limit." }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }

        /**
         * The bytes decide the document type, because file providers frequently mislabel camera
         * photos. Anything else that still declares itself an image is decoded and re-encoded as
         * JPEG, which is what phones produce for photographs of documents today.
         */
        internal fun resolveUpload(declaredMime: String, bytes: ByteArray, maxBytes: Int): Pair<String, ByteArray> {
            val sniffed = sniffMime(bytes)
            if (sniffed != null) return sniffed to bytes
            if (declaredMime.startsWith("image/")) {
                val jpeg = convertToJpeg(bytes)
                    ?: throw IllegalArgumentException("This photo format is not supported. Set your camera to JPEG or attach a PDF.")
                require(jpeg.size <= maxBytes) { "Choose a smaller document. The file exceeds the upload limit." }
                return "image/jpeg" to jpeg
            }
            throw IllegalArgumentException("Choose a PDF or photo of the document.")
        }

        private fun convertToJpeg(raw: ByteArray): ByteArray? {
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 3_000) {
                options.inSampleSize *= 2
            }
            options.inJustDecodeBounds = false
            val decoded = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, options) ?: return null
            val oriented = applyExifOrientation(raw, decoded)
            val flat = if (oriented.config == Bitmap.Config.ARGB_8888 && oriented.hasAlpha()) {
                val canvas = Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
                val painter = android.graphics.Paint(0)
                canvas.eraseColor(android.graphics.Color.WHITE)
                android.graphics.Canvas(canvas).drawBitmap(oriented, 0f, 0f, painter)
                canvas
            } else oriented
            val output = ByteArrayOutputStream()
            return if (flat.compress(Bitmap.CompressFormat.JPEG, 92, output)) output.toByteArray() else null
        }

        private fun applyExifOrientation(raw: ByteArray, decoded: Bitmap): Bitmap {
            val orientation = runCatching {
                androidx.exifinterface.media.ExifInterface(java.io.ByteArrayInputStream(raw))
                    .getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, 1)
            }.getOrDefault(1)
            val degrees = when (orientation) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return decoded
            val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
            return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }

        fun validSignature(mime: String, bytes: ByteArray): Boolean = sniffMime(bytes) == mime
    }
}
