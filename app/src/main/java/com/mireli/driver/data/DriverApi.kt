package com.mireli.driver.data

import com.mireli.driver.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.io.ByteArrayOutputStream

class DriverApi {
    suspend fun request(path:String,method:String="GET",token:String?=null,json:JSONObject?=null,bytes:ByteArray?=null,headers:Map<String,String> = emptyMap()):JSONObject = withContext(Dispatchers.IO) {
        require(path.startsWith("/") && !path.contains(".."))
        val base=URI(BuildConfig.DRIVER_SERVICE_URL)
        require(base.scheme=="https" || (BuildConfig.DRIVER_SERVICE_TEST && base.scheme=="http" && base.host=="10.0.2.2"))
        val connection=base.resolve("/api/v1/driver$path").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod=method;connection.instanceFollowRedirects=false;connection.connectTimeout=10000;connection.readTimeout=20000
            connection.setRequestProperty("Accept","application/json")
            token?.let {connection.setRequestProperty("Authorization","Bearer $it")}
            val payload=bytes?:json?.toString()?.toByteArray(Charsets.UTF_8)
            if(payload!=null) {
                connection.doOutput=true;connection.setFixedLengthStreamingMode(payload.size)
                connection.setRequestProperty("Content-Type",if(bytes!=null)headers["Content-Type"]?:"application/octet-stream" else "application/json")
            }
            headers.forEach {(key,value)->connection.setRequestProperty(key,value)}
            if(payload!=null)connection.outputStream.use {it.write(payload)}
            val status=connection.responseCode
            val stream=if(status in 200..299)connection.inputStream else connection.errorStream
            val body=stream?.use { input->
                val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){val count=input.read(buffer);if(count<0)break;check(out.size()+count<=512*1024){"The service response is too large."};out.write(buffer,0,count)}
                out.toByteArray().toString(Charsets.UTF_8)
            }?:"{}"
            val result=runCatching {JSONObject(body)}.getOrNull()
            if(status !in 200..299)throw DriverServiceException(status,if(status==404)"Driver services are not available on this server yet." else result?.optString("error")?.takeIf {it.isNotBlank()}?:"The driver service is unavailable. Try again later.")
            result?:throw DriverServiceException(503,"The service returned an invalid response.")
        } finally {connection.disconnect()}
    }
}
class DriverServiceException(val status:Int,message:String):Exception(message)
