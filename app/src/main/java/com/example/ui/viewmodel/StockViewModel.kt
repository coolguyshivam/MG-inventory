package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.HistoryEvent
import com.example.data.model.InventoryItem

import com.example.data.repository.InventoryRepository
import com.example.util.AppUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StockViewModel(
    application: Application,
    private val repository: InventoryRepository
) : AndroidViewModel(application) {

    private val TAG = "StockViewModel"

    // Core state flows
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // Data streams
    val allEvents: StateFlow<List<HistoryEvent>> = repository.getAllEvents()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInventory: StateFlow<List<InventoryItem>> = repository.getAllInventoryItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())



    // Backwards compatibility stream names
    val allItems: StateFlow<List<InventoryItem>> = allInventory

    // Image uploading & compression
    fun uploadPhotoToForm(uri: Uri, onCompleted: (String) -> Unit) {
        viewModelScope.launch {
            _isUploading.value = true
            _errorMessage.value = null
            try {
                // Compress and Upload via AppUtils
                val resultUrl = AppUtils.compressAndUploadPhoto(getApplication(), uri.toString())
                onCompleted(resultUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Failed compressing & uploading photo: ", e)
                _errorMessage.value = "Failed to upload photo: ${e.message}"
            } finally {
                _isUploading.value = false
            }
        }
    }

    // Process high-level transactions & update stock status (used by older legacy screens)
    fun addTransaction(
        actionMode: String,
        model: String,
        name: String,
        phoneNumber: String?,
        serialNumber: String,
        amount: Double,
        aadhaarNumber: String?,
        quantity: Int,
        address: String,
        rawDescription: String,
        photos: List<String>
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null
            try {
                val csvPhotos = if (photos.isNotEmpty()) photos.joinToString(",") else null
                val finalDescriptionText = "Address: $address\n$rawDescription"

                val event = HistoryEvent(
                    actionType = actionMode,
                    model = model,
                    name = name,
                    phoneNumber = if (phoneNumber.isNullOrBlank()) null else phoneNumber,
                    serialNumber = serialNumber,
                    amount = amount,
                    aadhaarNumber = if (aadhaarNumber.isNullOrBlank()) null else aadhaarNumber,
                    quantity = quantity,
                    description = finalDescriptionText,
                    photoUri = csvPhotos,
                    timestamp = System.currentTimeMillis()
                )

                repository.insertEvent(event)

                // Sync status
                val status = when (actionMode) {
                    "PURCHASE" -> "In Stock"
                    "SALE" -> "Sold"
                    "RETURN" -> "Returned"
                    "REPAIR_SENT" -> "Repair"
                    "REPAIR_RETURNED" -> "In Stock"
                    else -> "In Stock"
                }

                val existingItem = repository.getItemBySerialNumber(serialNumber)
                if (existingItem != null) {
                    val updatedItem = existingItem.copy(
                        model = model,
                        price = amount,
                        quantity = quantity,
                        supplierOrCustomerName = name,
                        status = status,
                        updatedTimestamp = System.currentTimeMillis()
                    )
                    repository.updateItem(updatedItem)
                } else {
                    val newItem = InventoryItem(
                        model = model,
                        serialNumber = serialNumber,
                        price = amount,
                        quantity = quantity,
                        supplierOrCustomerName = name,
                        status = status,
                        updatedTimestamp = System.currentTimeMillis()
                    )
                    repository.insertItem(newItem)
                }

                _successMessage.value = "Transaction recorded successfully!"
            } catch (e: Exception) {
                _errorMessage.value = "Failed transaction: ${e.message}"
                Log.e(TAG, "Error: ", e)
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    // Modern transaction (modular screens)
    fun executeTransaction(
        context: android.content.Context,
        actionType: String,
        model: String,
        name: String,
        phone: String?,
        serialNumber: String,
        amount: Double,
        aadhaarNumber: String?,
        quantity: Int,
        address: String,
        description: String,
        localPhotoUris: List<String>,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            _isUploading.value = true
            try {
                val uploadedUrls = localPhotoUris.map { uri ->
                    AppUtils.compressAndUploadPhoto(context, uri)
                }
                val photoUrlsString = uploadedUrls.filter { it.isNotBlank() }.joinToString(",")
                val finalDescriptionText = "Address: $address\n$description"

                val event = HistoryEvent(
                    actionType = actionType,
                    model = model,
                    name = name,
                    phoneNumber = if (phone.isNullOrBlank()) null else phone,
                    serialNumber = serialNumber,
                    amount = amount,
                    aadhaarNumber = if (aadhaarNumber.isNullOrBlank()) null else aadhaarNumber,
                    quantity = quantity,
                    description = finalDescriptionText,
                    photoUri = photoUrlsString,
                    timestamp = System.currentTimeMillis()
                )

                repository.insertEvent(event)

                val status = when (actionType) {
                    "PURCHASE" -> "In Stock"
                    "SALE" -> "Sold"
                    "RETURN" -> "Returned"
                    "REPAIR_SENT" -> "Repair"
                    "REPAIR_RETURNED" -> "In Stock"
                    else -> "In Stock"
                }

                val existingItem = repository.getItemBySerialNumber(serialNumber)
                if (existingItem != null) {
                    val updatedItem = existingItem.copy(
                        model = model,
                        price = amount,
                        quantity = quantity,
                        supplierOrCustomerName = name,
                        status = status,
                        updatedTimestamp = System.currentTimeMillis()
                    )
                    repository.updateItem(updatedItem)
                } else {
                    val newItem = InventoryItem(
                        model = model,
                        serialNumber = serialNumber,
                        price = amount,
                        quantity = quantity,
                        supplierOrCustomerName = name,
                        status = status,
                        updatedTimestamp = System.currentTimeMillis()
                    )
                    repository.insertItem(newItem)
                }
                onComplete(true, "Transaction processed successfully!")
            } catch (e: Exception) {
                Log.e(TAG, "Error in execute", e)
                onComplete(false, "Failed transaction: ${e.message}")
            } finally {
                _isUploading.value = false
            }
        }
    }

    // Secondary overload to prevent compilation failure in modular screens
    fun executeTransaction(
        context: android.content.Context,
        actionType: String,
        model: String,
        name: String,
        phone: String?,
        serialNumber: String,
        amount: Double,
        aadhaarNumber: String?,
        quantity: Int,
        address: String,
        description: String,
        localPhotoUris: List<String>,
        onComplete: () -> Unit
    ) {
        executeTransaction(
            context,
            actionType,
            model,
            name,
            phone,
            serialNumber,
            amount,
            aadhaarNumber,
            quantity,
            address,
            description,
            localPhotoUris
        ) { _, _ ->
            onComplete()
        }
    }


    fun deleteItem(item: InventoryItem) {
        viewModelScope.launch {
            repository.deleteItemBySerial(item.serialNumber)
        }
    }

    fun deleteEvent(id: Int) {
        viewModelScope.launch {
            repository.deleteEventById(id)
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun clearAll() {
        viewModelScope.launch {
            try {
                repository.clearAll()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear database", e)
            }
        }
    }
}

class StockViewModelFactory(
    private val application: Application,
    private val repository: InventoryRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StockViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StockViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
