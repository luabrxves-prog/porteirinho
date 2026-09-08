package com.rondasafe.app.presentation.admin.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rondasafe.app.core.result.UiState
import com.rondasafe.app.data.model.GuardDto
import com.rondasafe.app.data.repository.GuardRepository
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GuardsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application

    private val _guards = MutableStateFlow<UiState<List<GuardDto>>>(UiState.Loading)
    val guards: StateFlow<UiState<List<GuardDto>>> = _guards.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh(includeArchived: Boolean = false) {
        viewModelScope.launch {
            _guards.value = UiState.Loading
            _error.value = null
            runCatching { GuardRepository.list(includeArchived).filter { includeArchived || it.active } }
                .onSuccess { _guards.value = UiState.Success(it) }
                .onFailure {
                    val message = it.message ?: "Não foi possível carregar os porteiros."
                    _guards.value = UiState.Error(message)
                    _error.value = message
                }
        }
    }

    fun create(name: String, photoUri: Uri?, onCreated: (String, String) -> Unit) = action {
        val photo = photoUri?.let { readPhoto(it) }
        val response = GuardRepository.createWithPhoto(
            name = name,
            photoBytes = photo?.bytes,
            contentType = photo?.contentType,
            extension = photo?.extension ?: "jpg",
        )
        val pin = response.temporaryPin ?: error("PIN temporário não retornado.")
        onCreated(name.trim(), pin)
        refresh()
    }

    fun uploadPhoto(guardId: String, uri: Uri, onSaved: () -> Unit) = action {
        val photo = readPhoto(uri)
        GuardRepository.uploadPhoto(guardId, photo.bytes, photo.contentType, photo.extension)
        onSaved()
        refresh()
    }

    fun resetPin(guard: GuardDto, onReset: (String, String) -> Unit) = action {
        val response = GuardRepository.resetPin(guard.id)
        val pin = response.temporaryPin ?: error("PIN temporário não retornado.")
        onReset(guard.name, pin)
        refresh()
    }

    fun archive(guardId: String) = action {
        GuardRepository.archive(guardId)
        refresh()
    }

    fun restore(guardId: String) = action {
        GuardRepository.restore(guardId)
        refresh(includeArchived = true)
    }

    fun clearError() {
        _error.value = null
    }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _error.value = null
            runCatching { block() }
                .onFailure { _error.value = it.message ?: "Ocorreu um erro inesperado." }
            _busy.value = false
        }
    }

    private suspend fun readPhoto(uri: Uri): SelectedPhoto = withContext(Dispatchers.IO) {
        val resolver = app.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Não foi possível ler a foto.")
        require(bytes.size <= 5 * 1024 * 1024) { "A foto deve ter no máximo 5 MB." }
        val mime = resolver.getType(uri) ?: "image/jpeg"
        val contentType = ContentType.parse(mime)
        val extension = when (mime.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        SelectedPhoto(bytes, contentType, extension)
    }

    private data class SelectedPhoto(
        val bytes: ByteArray,
        val contentType: ContentType,
        val extension: String,
    )
}
