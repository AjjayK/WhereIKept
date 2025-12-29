package com.whereikept.app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whereikept.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * MainViewModel - Handles search and item list functionality.
 *
 * NOTE: Recording/capture functionality has been moved to CaptureViewModel.
 * This ViewModel is now focused on:
 * - Searching items
 * - Displaying item lists
 * - Manual item management
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = WhereIKeptDatabase.getDatabase(application)
    private val repository = WhereIKeptRepository(
        database.itemDao(),
        database.recordingDao(),
        database.imageDao()
    )

    // UI State
    data class UiState(
        val searchQuery: String = "",
        val searchResults: List<ItemEntity> = emptyList(),
        val isProcessing: Boolean = false,  // For search operations
        val error: String? = null,
        val successMessage: String? = null,
        val itemCount: Int = 0
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val allItems: StateFlow<List<ItemEntity>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        // Update item count
        viewModelScope.launch {
            allItems.collect { items ->
                _uiState.update { it.copy(itemCount = items.size) }
            }
        }
    }

    fun searchItems(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            try {
                val results = repository.searchItems(query)
                _uiState.update { it.copy(isProcessing = false, searchResults = results) }
            } catch (e: Exception) {
                Log.e(TAG, "Error searching: ${e.message}")
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        error = "Search error: ${e.message}"
                    )
                }
            }
        }
    }

    fun addItemManually(objectName: String, location: String, description: String = "") {
        if (objectName.isBlank() || location.isBlank()) return

        viewModelScope.launch {
            try {
                val item = ItemEntity(
                    objectName = objectName.trim(),
                    location = location.trim(),
                    description = description.trim(),
                    sourceType = "manual"
                )
                repository.insertItem(item)
                _uiState.update { it.copy(successMessage = "Item saved!") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save item: ${e.message}") }
            }
        }
    }

    fun deleteItem(item: ItemEntity) {
        viewModelScope.launch {
            try {
                repository.deleteItem(item)
                _uiState.update { it.copy(successMessage = "Item deleted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete item: ${e.message}") }
            }
        }
    }

    fun deleteAllItems() {
        viewModelScope.launch {
            try {
                repository.deleteAllItems()
                _uiState.update { it.copy(successMessage = "All items deleted") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to delete items: ${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

    companion object {
        private const val TAG = "MainViewModel"
    }
}
