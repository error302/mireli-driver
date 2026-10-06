package com.mireli.driver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mireli.driver.data.RepositoryFactory
import com.mireli.driver.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.util.UUID

class DriverViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RepositoryFactory.create(application)
    val trips = repository.trips
    val pendingCount = repository.pendingCount
    val connected = repository.connected
    val syncMessage = repository.syncMessage
    init { viewModelScope.launch {
        try { repository.setConnected(repository.connected.value) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Saved queue remains available for an explicit retry. */ }
    } }
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    fun dismissMessage() { _message.value = null }
    fun command(trip: Trip, command: TripCommand) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                when (val result = repository.execute(trip.id, trip.version, UUID.randomUUID().toString(), command)) {
                    is Change.Applied -> _message.value = "Preview updated"
                    is Change.Rejected -> _message.value = result.reason
                    is Change.Queued -> _message.value = "Waiting to sync · not confirmed"
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Unable to save. Please try again." }
            finally { _busy.value = false }
        }
    }
    fun setConnected(value: Boolean) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { repository.setConnected(value) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Unable to sync. Your saved actions remain on this phone." }
            finally { _busy.value = false }
        }
    }
    fun reset() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { repository.reset(); _message.value = "Preview trips reset" }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Unable to reset. Please try again." }
            finally { _busy.value = false }
        }
    }
}
