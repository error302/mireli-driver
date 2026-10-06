package com.mireli.driver
import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mireli.driver.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class DriverServiceState(val loading:Boolean=true,val signedIn:Boolean=false,val challengeId:String?=null,
    val demoCode:String?=null,val challengePhone:String?=null,val onboarding:JSONObject?=null,val earnings:JSONObject?=null,val support:JSONObject?=null,val supportSent:Int=0,val eligibility:JSONObject?=null,val trips:JSONObject?=null,val pendingTripAction:Boolean=false,val error:String?=null)
class DriverServicesViewModel(application:Application):AndroidViewModel(application) {
    private val api=DriverApi()
    private val sessions=DriverSessionStore(application)
    private val documents=OnboardingStore(application)
    private var session:DriverSession?=null
    private var driverId:String?=null
    private var supportRequest:Pair<String,String>?=null
    private val mutable=MutableStateFlow(DriverServiceState())
    val state=mutable.asStateFlow()
    init {viewModelScope.launch {
        session=withContext(Dispatchers.IO){sessions.load()}
        mutable.value=DriverServiceState(loading=false,signedIn=session!=null)
        if(session!=null)refresh()
    }}
    private fun work(operation:suspend()->Unit) {
        if(mutable.value.loading)return
        mutable.value=mutable.value.copy(loading=true,error=null)
        viewModelScope.launch {
            try{operation()}
            catch(cancelled:CancellationException){throw cancelled}
            catch(error:Exception){
                if(error is DriverServiceException && error.status==401){withContext(Dispatchers.IO){sessions.clear()};session=null;mutable.value=DriverServiceState(loading=true,error="Your session expired. Sign in again.")}
                else mutable.value=mutable.value.copy(error=if(error is DriverServiceException)error.message else "The action could not be confirmed. Check your connection and refresh before retrying.")
            }finally{mutable.value=mutable.value.copy(loading=false)}
        }
    }
    fun requestCode(phone:String)=work {
        val status=api.request("/status")
        if(!status.has("simulation") || status.getBoolean("simulation")!=BuildConfig.DRIVER_SERVICE_TEST)throw DriverServiceException(503,"This build cannot connect to that service environment.")
        val result=api.request("/auth/challenges","POST",json=JSONObject().put("phone",phone))
        mutable.value=mutable.value.copy(challengeId=result.getString("challengeId"),challengePhone=phone,demoCode=if(BuildConfig.DRIVER_SERVICE_TEST)result.optString("demoCode").takeIf {it.isNotBlank()} else null)
    }
    fun verifyCode(code:String)=work {
        val challenge=mutable.value.challengeId?:return@work
        val result=api.request("/auth/sessions","POST",json=JSONObject().put("challengeId",challenge).put("code",code).put("deviceId",withContext(Dispatchers.IO){sessions.deviceId}))
        if(result.getBoolean("simulation")!=BuildConfig.DRIVER_SERVICE_TEST)throw DriverServiceException(503,"Service environment does not match this build.")
        session=DriverSession(result.getString("token"),result.getString("expiresAt"))
        withContext(Dispatchers.IO){sessions.save(session!!)}
        mutable.value=mutable.value.copy(signedIn=true,challengeId=null,demoCode=null)
        loadAccount()
    }
    private suspend fun loadAccount()=coroutineScope {
        val token=session?.token?:return@coroutineScope
        val onboarding=async{api.request("/onboarding",token=token)}
        val earnings=async{api.request("/earnings",token=token)}
        val me=async{api.request("/me",token=token)}
        val support=async{api.request("/support",token=token)}
        val trips=async{api.request("/trips",token=token)}
        val statement=earnings.await();require(statement.getString("currency")=="KES" && statement.getString("unit")=="minor")
        val account=me.await();driverId=account.getJSONObject("driver").getString("id")
        val pending=withContext(Dispatchers.IO){sessions.savedAction(driverId!!)!=null}
        mutable.value=mutable.value.copy(onboarding=onboarding.await(),earnings=statement,support=support.await(),eligibility=account.getJSONObject("eligibility"),trips=trips.await(),pendingTripAction=pending)
    }
    fun changePhone(){if(!mutable.value.loading)mutable.value=mutable.value.copy(challengeId=null,challengePhone=null,demoCode=null,error=null)}
    fun support(category:String,message:String)=work {
        val fingerprint="$category|${message.trim()}"
        val id=supportRequest?.takeIf{it.first==fingerprint}?.second?:java.util.UUID.randomUUID().toString().also{supportRequest=fingerprint to it}
        val response=api.request("/support","POST",session?.token,json=JSONObject().put("clientActionId",id).put("category",category).put("message",message.trim()))
        mutable.value=mutable.value.copy(support=response,supportSent=mutable.value.supportSent+1);supportRequest=null
    }
    fun refresh()=work {loadAccount()}
    fun payoutDestination(name:String)=work {
        val version=mutable.value.earnings?.getJSONObject("destination")?.getInt("version")?:return@work
        api.request("/payout-destination","POST",session?.token,json=JSONObject().put("expectedVersion",version).put("accountName",name.trim()).put("acknowledged",true))
        loadAccount()
    }
    fun tripCommand(trip:JSONObject,action:String,details:JSONObject=JSONObject())=work {
        val id=driverId?:throw DriverServiceException(401,"Sign in again.")
        if(withContext(Dispatchers.IO){sessions.savedAction(id)!=null})throw DriverServiceException(409,"Check the saved action before sending another trip change.")
        val body=JSONObject(details.toString()).put("action",action).put("expectedVersion",trip.getInt("version")).put("clientActionId",java.util.UUID.randomUUID().toString())
        val pending=JSONObject().put("path","/trips/${trip.getString("id")}/commands").put("body",body)
        withContext(Dispatchers.IO){sessions.saveAction(id,pending)}
        mutable.value=mutable.value.copy(pendingTripAction=true)
        sendSavedAction(id,pending)
    }
    fun retryTripAction()=work {
        val id=driverId?:throw DriverServiceException(401,"Sign in again.")
        val pending=withContext(Dispatchers.IO){sessions.savedAction(id)}?:return@work
        sendSavedAction(id,pending)
    }
    private suspend fun sendSavedAction(id:String,pending:JSONObject) {
        try {
            api.request(pending.getString("path"),"POST",session?.token,json=pending.getJSONObject("body"))
        }catch(error:DriverServiceException){
            if(error.status in 400..499 && error.status!=401 && error.status!=429){withContext(Dispatchers.IO){sessions.clearAction(id)};mutable.value=mutable.value.copy(pendingTripAction=false)}
            throw error
        }
        withContext(Dispatchers.IO){sessions.clearAction(id)};mutable.value=mutable.value.copy(pendingTripAction=false)
        loadAccount()
    }
    fun profile(name:String,plate:String,capacity:String,licence:String,cabType:String,owner:Boolean)=work {
        val current=mutable.value.onboarding?:return@work
        val updated=api.request("/onboarding","PUT",session?.token,json=JSONObject().put("expectedVersion",current.getJSONObject("application").getInt("version"))
            .put("fullName",name).put("plate",plate).put("capacity",capacity.toIntOrNull()?:0).put("licenceClass",licence).put("cabType",cabType).put("ownsVehicle",owner))
        mutable.value=mutable.value.copy(onboarding=updated);loadAccount()
    }
    fun upload(type:String,expiry:String,uri:Uri)=work {
        val current=mutable.value.onboarding?:return@work
        val evidence=withContext(Dispatchers.IO){documents.attach(uri)}
        try {
            if(evidence.size>current.getInt("maxDocumentBytes"))throw DriverServiceException(413,"This server accepts files up to ${current.getInt("maxDocumentBytes")/1024/1024} MB. Choose a smaller copy.")
            val bytes=withContext(Dispatchers.IO){documents.attachmentBytes(evidence)}
            val updated=api.request("/documents/$type","POST",session?.token,bytes=bytes,headers=mapOf("Content-Type" to evidence.mime,"x-application-version" to current.getJSONObject("application").getInt("version").toString(),"x-document-expiry" to expiry))
            mutable.value=mutable.value.copy(onboarding=updated);loadAccount()
        }finally{withContext(Dispatchers.IO){documents.remove(evidence)}}
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
        try{if(token!=null)api.request("/auth/session","DELETE",token)}catch(_:Exception){revoked=false}
        withContext(Dispatchers.IO){sessions.clear()};session=null;driverId=null;supportRequest=null
        mutable.value=DriverServiceState(loading=true,error=if(revoked)null else "Signed out on this phone. Server revocation could not be confirmed; the session still has its expiry limit.")
    }
}
