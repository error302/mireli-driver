package com.mireli.driver
import android.app.Application
import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mireli.driver.data.*
import com.mireli.driver.domain.GeoPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

private const val DEFAULT_SUPPORT_EMAIL = "mirelisgr001@gmail.com"

data class DriverServiceState(val loading:Boolean=true,val signedIn:Boolean=false,val challengeId:String?=null,
    val demoCode:String?=null,val challengeEmail:String?=null,val onboarding:JSONObject?=null,val earnings:JSONObject?=null,val support:JSONObject?=null,val supportSent:Int=0,val eligibility:JSONObject?=null,val trips:JSONObject?=null,val tripDataCurrent:Boolean=false,val pendingTripAction:Boolean=false,val emailSignInOpen:Boolean?=null,val registrationOpen:Boolean?=null,val navigationOpen:Boolean=false,val serviceMessage:String?=null,val supportEmail:String=DEFAULT_SUPPORT_EMAIL,val resendAt:Long=0,val error:String?=null)
class DriverServicesViewModel(application:Application):AndroidViewModel(application) {
    private val api=DriverApi()
    private val sessions=DriverSessionStore(application)
    private var session:DriverSession?=null
    private var driverId:String?=null
    private var supportRequest:Pair<String,String>?=null
    private val mutable=MutableStateFlow(DriverServiceState())
    val state=mutable.asStateFlow()
    init {viewModelScope.launch {
        session=withContext(Dispatchers.IO){sessions.load()}
        mutable.value=DriverServiceState(loading=false,signedIn=session!=null)
        if(session!=null)refresh() else refreshServiceStatus()
    }}
    private fun work(operation:suspend()->Unit) {
        if(mutable.value.loading)return
        mutable.value=mutable.value.copy(loading=true,error=null)
        viewModelScope.launch {
            try{operation()}
            catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){
                if(error is DriverServiceException && error.sessionRejected){
                    clearSession("Your session expired. Sign in again.")
                }
                else mutable.value=mutable.value.copy(error=if(error is DriverServiceException)error.message else "The action could not be confirmed. Check your connection and refresh before retrying.")
            }finally{mutable.value=mutable.value.copy(loading=false)}
        }
    }
    private suspend fun serviceStatus() {
        val status = try {
            api.request("/status").also {
                if (it.optString("apiVersion") != "v1" || it.opt("simulation") != BuildConfig.DRIVER_SERVICE_TEST) {
                    throw DriverServiceException(503, "This build cannot connect to that service environment.")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutable.value = mutable.value.copy(emailSignInOpen = null, registrationOpen = null,
                navigationOpen = false, serviceMessage = null)
            throw error
        }
        val contactEmail = status.optString("supportEmail").takeIf { Patterns.EMAIL_ADDRESS.matcher(it).matches() }
        mutable.value = mutable.value.copy(
            emailSignInOpen = status.opt("emailSignInOpen") == true,
            registrationOpen = status.opt("registrationOpen") == true,
            navigationOpen = status.opt("navigationOpen") == true,
            serviceMessage = status.optString("message").takeIf { it.isNotBlank() && it != "null" },
            supportEmail = contactEmail ?: DEFAULT_SUPPORT_EMAIL
        )
    }
    fun refreshServiceStatus()=work {serviceStatus()}
    fun requestCode(email:String)=work {
        if(mutable.value.resendAt>System.currentTimeMillis())throw DriverServiceException(429,"Please wait before requesting another code.")
        serviceStatus()
        if(mutable.value.emailSignInOpen!=true)throw DriverServiceException(503,"Driver email sign-in is not available yet. Please check again later.")
        val result=api.request("/auth/email-challenges","POST",json=JSONObject().put("email",email.trim()))
        mutable.value=mutable.value.copy(challengeId=result.getString("challengeId"),challengeEmail=email.trim(),resendAt=System.currentTimeMillis()+result.optInt("cooldownSeconds",60).coerceIn(30,300)*1000L,demoCode=if(BuildConfig.DRIVER_SERVICE_TEST)result.optString("demoCode").takeIf {it.isNotBlank()} else null)
    }
    fun verifyCode(code:String)=work {
        val challenge=mutable.value.challengeId?:return@work
        val result=api.request("/auth/sessions","POST",json=JSONObject().put("challengeId",challenge).put("code",code).put("deviceId",withContext(Dispatchers.IO){sessions.deviceId}))
        if(result.opt("simulation")!=BuildConfig.DRIVER_SERVICE_TEST)throw DriverServiceException(503,"Service environment does not match this build.")
        val verifiedSession = DriverSession(result.getString("token"), result.getString("expiresAt"))
        withContext(Dispatchers.IO) { sessions.save(verifiedSession) }
        session = verifiedSession
        mutable.value=mutable.value.copy(signedIn=true,challengeId=null,demoCode=null)
        loadAccount()
    }
    private suspend fun loadAccount()=supervisorScope {
        val token=session?.token?:return@supervisorScope
        mutable.value = mutable.value.copy(tripDataCurrent = false)
        val onboarding=async{api.request("/onboarding",token=token)}
        val earnings=async{api.request("/earnings",token=token)}
        val me=async{api.request("/me",token=token)}
        val support=async{api.request("/support",token=token)}
        val trips=async{api.request("/trips",token=token)}
        val account = me.await()
        val accountId = account.getJSONObject("driver").getString("id")
        val assignments = trips.await()
        val pending = withContext(Dispatchers.IO) { sessions.savedAction(accountId) != null }
        driverId = accountId
        mutable.value = mutable.value.copy(eligibility = assignments.getJSONObject("eligibility"),
            trips = assignments, tripDataCurrent = true, pendingTripAction = pending)
        mutable.value = mutable.value.copy(onboarding = onboarding.await())
        mutable.value = mutable.value.copy(support = support.await())
        val statement = earnings.await()
        require(statement.getString("currency") == "KES" && statement.getString("unit") == "minor")
        mutable.value = mutable.value.copy(earnings = statement)
    }
    fun changeEmail(){if(!mutable.value.loading)mutable.value=mutable.value.copy(challengeId=null,challengeEmail=null,demoCode=null,error=null,resendAt=0)}
    fun support(category:String,message:String)=work {
        val fingerprint="$category|${message.trim()}"
        val id=supportRequest?.takeIf{it.first==fingerprint}?.second?:java.util.UUID.randomUUID().toString().also{supportRequest=fingerprint to it}
        val response=api.request("/support","POST",session?.token,json=JSONObject().put("clientActionId",id).put("category",category).put("message",message.trim()))
        mutable.value=mutable.value.copy(support=response,supportSent=mutable.value.supportSent+1);supportRequest=null
    }
    fun refresh()=work {
        mutable.value = mutable.value.copy(tripDataCurrent = false)
        serviceStatus()
        loadAccount()
    }
    fun payoutDestination(name:String)=work {
        val version=mutable.value.earnings?.getJSONObject("destination")?.getInt("version")?:return@work
        api.request("/payout-destination","POST",session?.token,json=JSONObject().put("expectedVersion",version).put("accountName",name.trim()).put("acknowledged",true))
        loadAccount()
    }
    fun tripCommand(trip:JSONObject,action:String,details:JSONObject=JSONObject())=work {
        val id=driverId?:throw DriverServiceException(401,"Sign in again.")
        if (!mutable.value.tripDataCurrent) throw DriverServiceException(409, "Refresh assignments before making another trip change.")
        if(withContext(Dispatchers.IO){sessions.savedAction(id)!=null})throw DriverServiceException(409,"Check the saved action before sending another trip change.")
        val body=JSONObject(details.toString()).put("action",action).put("expectedVersion",trip.getInt("version")).put("clientActionId",java.util.UUID.randomUUID().toString())
        val pending=JSONObject().put("path","/trips/${trip.getString("id")}/commands").put("body",body)
        withContext(Dispatchers.IO){sessions.saveAction(id,pending)}
        mutable.value=mutable.value.copy(pendingTripAction=true)
        sendSavedAction(id,pending)
    }
    fun navigationRoute(tripId:String,stageId:String,origin:GeoPoint,onComplete:(Result<JSONObject>)->Unit) {
        val token=session?.token
        if(token==null){onComplete(Result.failure(IllegalStateException("Sign in again to request directions.")));return}
        viewModelScope.launch {
            val result=runCatching {api.request("/navigation/route","POST",token,json=JSONObject()
                .put("tripId",tripId).put("stageId",stageId).put("origin",JSONObject().put("latitude",origin.latitude).put("longitude",origin.longitude)))}
            onComplete(result)
        }
    }
    fun retryTripAction()=work {
        val id=driverId?:throw DriverServiceException(401,"Sign in again.")
        val pending=withContext(Dispatchers.IO){sessions.savedAction(id)}?:return@work
        sendSavedAction(id,pending)
    }
    private suspend fun sendSavedAction(id:String,pending:JSONObject) {
        val path = pending.getString("path")
        val body = pending.getJSONObject("body")
        try {
            val receipt = api.request(path, "POST", session?.token, json = body)
            if (!receipt.confirmsTripAction(path, body)) {
                throw DriverServiceException(503, "Mireli did not return a matching trip receipt. Check the saved action again.")
            }
        } catch (error: DriverServiceException) {
            if (error.tripActionRejected) {
                withContext(Dispatchers.IO) { sessions.clearAction(id) }
                mutable.value = mutable.value.copy(pendingTripAction = false,
                    tripDataCurrent = mutable.value.tripDataCurrent && error.status !in setOf(403, 404, 409))
            }
            throw error
        }
        withContext(Dispatchers.IO) { sessions.clearAction(id) }
        mutable.value = mutable.value.copy(pendingTripAction = false, tripDataCurrent = false)
        loadAccount()
    }
    fun profile(name:String,plate:String,capacity:String,licence:String,cabType:String,owner:Boolean,phone:String)=work {
        val current=mutable.value.onboarding?:return@work
        val updated=api.request("/onboarding","PUT",session?.token,json=JSONObject().put("expectedVersion",current.getJSONObject("application").getInt("version"))
            .put("fullName",name).put("plate",plate).put("capacity",capacity.toIntOrNull()?:0).put("licenceClass",licence).put("cabType",cabType).put("ownsVehicle",owner).put("phone",phone.trim()))
        mutable.value=mutable.value.copy(onboarding=updated);loadAccount()
    }
    fun upload(type:String,expiry:String,uri:Uri)=work {
        val current=mutable.value.onboarding?:return@work
        val limit = current.getInt("maxDocumentBytes")
        val (mime, bytes) = try {
            withContext(Dispatchers.IO) { OnboardingStore.readUpload(getApplication(), uri, limit) }
        } catch (error: IllegalArgumentException) {
            throw DriverServiceException(400, error.message ?: "Choose a valid document.")
        } catch (_: java.io.IOException) {
            throw DriverServiceException(400, "The selected document could not be read. Choose it again.")
        } catch (_: SecurityException) {
            throw DriverServiceException(400, "Access to this document expired. Choose it again.")
        }
        val updated=api.request("/documents/$type","POST",session?.token,bytes=bytes,headers=mapOf("Content-Type" to mime,"x-application-version" to current.getJSONObject("application").getInt("version").toString(),"x-document-expiry" to expiry))
        mutable.value=mutable.value.copy(onboarding=updated);loadAccount()
    }
    fun submit(accepted:Boolean)=work {
        val current=mutable.value.onboarding?:return@work
        val updated=api.request("/onboarding/submissions","POST",session?.token,json=JSONObject().put("expectedVersion",current.getJSONObject("application").getInt("version"))
            .put("policyVersion",current.getString("policyVersion")).put("accepted",accepted))
        mutable.value=mutable.value.copy(onboarding=updated);loadAccount()
    }
    fun signOut()=work {
        val token=session?.token
        var revoked=true
        try {
            if (token != null) api.request("/auth/session", "DELETE", token)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            revoked = false
        }
        clearSession(if (revoked) null else "Signed out on this phone. Server revocation could not be confirmed; the session still has its expiry limit.")
    }
    private suspend fun clearSession(message: String?) {
        withContext(Dispatchers.IO) { sessions.clear() }
        session = null
        driverId = null
        supportRequest = null
        val previous = mutable.value
        mutable.value = DriverServiceState(
            emailSignInOpen = previous.emailSignInOpen,
            registrationOpen = previous.registrationOpen,
            serviceMessage = previous.serviceMessage,
            supportEmail = previous.supportEmail,
            error = message
        )
    }
}

internal fun JSONObject.confirmsTripAction(path: String, command: JSONObject): Boolean {
    val tripId = optString("tripId")
    val expectedVersion = command.getInt("expectedVersion") + 1
    val phase = when (command.optString("action")) {
        "accept" -> "accepted"
        "decline" -> "declined"
        "arrive", "board", "no_show" -> "at_pickup"
        "start" -> "in_progress"
        "complete" -> "completed"
        else -> return false
    }
    return tripId.isNotBlank() && path == "/trips/$tripId/commands" &&
        opt("version") == expectedVersion && optString("phase") == phase
}
