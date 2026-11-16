package com.mireli.driver.data
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class DriverSession(val token:String,val expiresAt:String?)
class DriverSessionStore(context:Context) {
    private val file=File(context.noBackupFilesDir,"driver-session.enc")
    private val tripFile = File(context.noBackupFilesDir, "driver-active-trip.enc")
    private val supportFile = File(context.noBackupFilesDir, "driver-support-draft.enc")
    private val prefs=context.getSharedPreferences("driver_install",Context.MODE_PRIVATE)
    val deviceId:String get()=prefs.getString("device",null)?:UUID.randomUUID().toString().also {check(prefs.edit().putString("device",it).commit())}
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (store.getKey("mireli_driver_session",null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {init(KeyGenParameterSpec.Builder("mireli_driver_session",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())}.generateKey()
    }
    private fun writeEncrypted(target:File,json:JSONObject) {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key())}
        val bytes=json.toString().toByteArray(Charsets.UTF_8)
        val encoded=cipher.iv+cipher.doFinal(bytes);val atomic=AtomicFile(target);val out=atomic.startWrite()
        try{out.write(encoded);atomic.finishWrite(out)}catch(error:Exception){atomic.failWrite(out);throw error}
    }
    private fun readEncrypted(target:File):JSONObject {
        val bytes=AtomicFile(target).openRead().use {it.readBytes()};require(bytes.size in 28..16384)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))}
        return JSONObject(cipher.doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.UTF_8))
    }
    fun save(session:DriverSession)=writeEncrypted(file,JSONObject().put("token",session.token)
        .put("expiresAt",session.expiresAt ?: JSONObject.NULL))
    fun load():DriverSession? {
        if(!file.exists())return null
        return runCatching {
            val json=readEncrypted(file)
            check(json.has("expiresAt"))
            val expiresAt = if (json.isNull("expiresAt")) null else json.getString("expiresAt")
            DriverSession(json.getString("token"), expiresAt)
                .takeIf { expiresAt == null || Instant.parse(expiresAt).isAfter(Instant.now()) }
        }.getOrNull()
    }
    fun clear() {
        AtomicFile(file).delete()
        AtomicFile(tripFile).delete()
        AtomicFile(supportFile).delete()
    }

    fun saveTrip(session: DriverSession, snapshot: JSONObject?) {
        if (snapshot == null) AtomicFile(tripFile).delete()
        else writeEncrypted(tripFile, JSONObject().put("session", sessionFingerprint(session)).put("trip", snapshot))
    }

    fun savedTrip(session: DriverSession): JSONObject? = runCatching {
        val cached = readEncrypted(tripFile)
        cached.getJSONObject("trip").takeIf { cached.getString("session") == sessionFingerprint(session) }
    }.getOrNull()

    fun saveSupportDraft(session: DriverSession, draft: SupportDraft) {
        if (draft == SupportDraft()) AtomicFile(supportFile).delete()
        else writeEncrypted(supportFile, JSONObject()
            .put("session", sessionFingerprint(session))
            .put("category", draft.category)
            .put("message", draft.message)
            .put("requestId", draft.requestId))
    }

    fun savedSupportDraft(session: DriverSession): SupportDraft = runCatching {
        val json = readEncrypted(supportFile)
        check(json.getString("session") == sessionFingerprint(session))
        SupportDraft(json.getString("category"), json.getString("message"), json.opt("requestId") as? String)
    }.getOrDefault(SupportDraft())

    private fun sessionFingerprint(session: DriverSession): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(session.token.toByteArray())
            .joinToString("") { "%02x".format(it) }
    private fun actionFile(driverId:String):File {
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(driverId.toByteArray()).joinToString(""){"%02x".format(it)}
        return File(file.parentFile,"driver-action-$hash.enc")
    }
    fun savedAction(driverId:String):JSONObject?=actionFile(driverId).let {if(it.exists())readEncrypted(it) else null}
    fun saveAction(driverId:String,action:JSONObject)=writeEncrypted(actionFile(driverId),action)
    fun clearAction(driverId:String)=AtomicFile(actionFile(driverId)).delete()
}

data class SupportDraft(val category: String = "trip", val message: String = "", val requestId: String? = null)
