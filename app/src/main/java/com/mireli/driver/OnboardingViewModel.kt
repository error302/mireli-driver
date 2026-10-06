package com.mireli.driver

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mireli.driver.data.OnboardingStore
import com.mireli.driver.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    private val store = OnboardingStore(application)
    private val _draft = MutableStateFlow(OnboardingDraft())
    val draft = _draft.asStateFlow()
    private val _busy = MutableStateFlow(true)
    val busy = _busy.asStateFlow()
    private val _errors = MutableStateFlow<List<String>>(emptyList())
    val errors = _errors.asStateFlow()
    private val _unreadable = MutableStateFlow(false)
    val unreadable = _unreadable.asStateFlow()
    init {
        viewModelScope.launch {
            try { _draft.value = withContext(Dispatchers.IO) { store.load() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _unreadable.value = true; _errors.value = listOf("The saved draft cannot be decrypted. Clear local data to start again; no files have been uploaded.") }
            finally { _busy.value = false }
        }
    }
    private fun run(operation: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { operation() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _errors.value = listOf("Unable to save this change. Check device storage and try again.") }
            finally { _busy.value = false }
        }
    }
    fun saveProfile(next: OnboardingDraft) = run {
        if (_unreadable.value) return@run
        val updated = next.copy(evidence = _draft.value.evidence, checkedAt = null)
        withContext(Dispatchers.IO) { store.save(updated) }
        _draft.value = updated; _errors.value = emptyList()
    }
    fun attach(id: String, uri: Uri) = run {
        if (_unreadable.value || !_draft.value.localNoticeAccepted) return@run
        require(OnboardingPolicy.documents.any { it.id == id })
        val evidence = try { withContext(Dispatchers.IO) { store.attach(uri) } }
        catch (_: IllegalArgumentException) { _errors.value = listOf("Choose a valid PDF, JPEG or PNG up to 10 MB."); return@run }
        val old = _draft.value.evidence[id]
        val next = _draft.value.copy(evidence = _draft.value.evidence + (id to evidence), checkedAt = null)
        try { withContext(Dispatchers.IO) { store.save(next) } }
        catch (error: Exception) { withContext(Dispatchers.IO) { store.remove(evidence) }; throw error }
        _draft.value = next; _errors.value = emptyList()
        old?.let { withContext(Dispatchers.IO) { store.remove(it) } }
    }
    fun expiry(id: String, value: String) = run {
        val item = _draft.value.evidence[id] ?: return@run
        val next = _draft.value.copy(evidence = _draft.value.evidence + (id to item.copy(expiry = value)), checkedAt = null)
        withContext(Dispatchers.IO) { store.save(next) }; _draft.value = next; _errors.value = emptyList()
    }
    fun remove(id: String) = run {
        val item = _draft.value.evidence[id] ?: return@run
        val next = _draft.value.copy(evidence = _draft.value.evidence - id, checkedAt = null)
        withContext(Dispatchers.IO) { store.remove(item); store.save(next) }
        _draft.value = next; _errors.value = emptyList()
    }
    fun check() = run {
        val problems = OnboardingPolicy.validate(_draft.value, LocalDate.now(ZoneId.of("Africa/Nairobi"))) +
            withContext(Dispatchers.IO) { store.validateAttachments(_draft.value) }
        _errors.value = problems
        val next = _draft.value.copy(checkedAt = if (problems.isEmpty()) Instant.now().toString() else null)
        withContext(Dispatchers.IO) { store.save(next) }; _draft.value = next
    }
    fun clear() = run {
        withContext(Dispatchers.IO) { store.clear() }
        _draft.value = OnboardingDraft(); _errors.value = emptyList(); _unreadable.value = false
    }
}
