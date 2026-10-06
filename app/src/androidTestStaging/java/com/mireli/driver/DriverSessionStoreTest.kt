package com.mireli.driver
import androidx.test.core.app.ApplicationProvider
import com.mireli.driver.data.DriverSession
import com.mireli.driver.data.DriverSessionStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import android.content.Context
import java.io.File
import java.time.Instant
import java.util.UUID
class DriverSessionStoreTest {
    @Test fun sessionIsEncryptedAndExpiredSessionsAreNotLoaded(){
        val context=ApplicationProvider.getApplicationContext<Context>();val store=DriverSessionStore(context)
        val token="SYNTHETIC-TOKEN-${UUID.randomUUID()}"
        try{
            store.save(DriverSession(token,Instant.now().plusSeconds(300).toString()))
            assertEquals(token,DriverSessionStore(context).load()!!.token)
            assertFalse(File(context.noBackupFilesDir,"driver-session.enc").readBytes().toString(Charsets.UTF_8).contains(token))
            store.save(DriverSession(token,Instant.now().minusSeconds(1).toString()));assertNull(store.load())
        }finally{store.clear()}
    }
    @Test fun uncertainActionsAreAccountBoundEncryptedAndTamperingFailsClosed(){
        val context=ApplicationProvider.getApplicationContext<Context>();val store=DriverSessionStore(context)
        val owner="SYNTHETIC-${UUID.randomUUID()}";val other="SYNTHETIC-${UUID.randomUUID()}"
        val action=JSONObject().put("path","/trips/SYNTHETIC/commands").put("body",JSONObject().put("clientActionId",UUID.randomUUID().toString()).put("code","SECRET-TEST-CODE"))
        try{
            store.saveAction(owner,action)
            assertEquals(action.toString(),DriverSessionStore(context).savedAction(owner)!!.toString());assertNull(store.savedAction(other))
            val hash=java.security.MessageDigest.getInstance("SHA-256").digest(owner.toByteArray()).joinToString(""){"%02x".format(it)}
            val file=File(context.noBackupFilesDir,"driver-action-$hash.enc");val bytes=file.readBytes()
            assertFalse(bytes.toString(Charsets.UTF_8).contains("SECRET-TEST-CODE"));bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();file.writeBytes(bytes)
            assertTrue(runCatching{store.savedAction(owner)}.isFailure)
        }finally{store.clearAction(owner);store.clearAction(other)}
    }
}
