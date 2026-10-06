package com.mireli.driver.data
import android.content.Context
import com.mireli.driver.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
object RepositoryFactory {
    const val isDemo = false
    fun create(context: Context): DriverRepository = object : DriverRepository {
        override val trips=MutableStateFlow<List<Trip>>(emptyList())
        override val pendingCount=MutableStateFlow(0)
        override val connected=MutableStateFlow(false)
        override val syncMessage=MutableStateFlow<String?>(null)
        override suspend fun execute(tripId:String,expectedVersion:Int,commandId:String,command:TripCommand):Change=Change.Rejected("Live trip operations are not connected.")
        override suspend fun setConnected(value:Boolean)=Unit
        override suspend fun reset()=Unit
    }
}
