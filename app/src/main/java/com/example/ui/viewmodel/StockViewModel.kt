package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.HistoryEvent
import com.example.data.model.InventoryItem
import com.example.data.model.User
import com.example.data.repository.InventoryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class StockViewModel(private val repository: InventoryRepository) : ViewModel() {

    // --- Authentication & Biometric State ---
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loggedInUser = MutableStateFlow<User?>(null)
    val loggedInUser: StateFlow<User?> = _loggedInUser.asStateFlow()

    // Permissions based on Role
    // Admin: canManageUsers, canManageInventory, canRepair, canViewAnalytics, canSell, canDelete
    // Manager: canManageInventory, canRepair, canViewAnalytics, canSell, canDelete
    // Operator: canManageInventory, canRepair
    // MIS: canViewAnalytics
    // Sales: canSell, canViewAnalytics
    
    val canManageUsers = _loggedInUser.map { it?.role == "Admin" }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canManageInventory = _loggedInUser.map { it?.role in listOf("Admin", "Manager", "Operator") }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canRepair = _loggedInUser.map { it?.role in listOf("Admin", "Manager", "Operator") }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canViewAnalytics = _loggedInUser.map { it?.role in listOf("Admin", "Manager", "MIS", "Sales") }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canSell = _loggedInUser.map { it?.role in listOf("Admin", "Manager", "Sales") }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canDelete = _loggedInUser.map { it?.role in listOf("Admin", "Manager") }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    // --- Cloud Syncing Status ---
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncedTime = MutableStateFlow(System.currentTimeMillis())
    val lastSyncedTime: StateFlow<Long> = _lastSyncedTime.asStateFlow()

    // --- Pull to Refresh ---
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // --- Active App Navigation Tab (0: Inventory, 1: Transactions, 2: Analytics, 3: History, 4: Logout confirmation) ---
    private val _activeTab = MutableStateFlow(0)
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    // --- Inventory Page State ---
    val inventoryItems: StateFlow<List<InventoryItem>> = repository.allInventoryItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _inventorySearchTerm = MutableStateFlow("")
    val inventorySearchTerm: StateFlow<String> = _inventorySearchTerm.asStateFlow()

    // Sorting Option: "Date", "Name", "Quantity", "Price" (Asc/Desc)
    private val _inventorySortOption = MutableStateFlow("Date")
    val inventorySortOption: StateFlow<String> = _inventorySortOption.asStateFlow()

    private val _inventorySortAscending = MutableStateFlow(false)
    val inventorySortAscending: StateFlow<Boolean> = _inventorySortAscending.asStateFlow()

    // Active sub-tab under Inventory Page (0 = Stock List, 1 = Repair List)
    private val _inventorySubTab = MutableStateFlow(0)
    val inventorySubTab: StateFlow<Int> = _inventorySubTab.asStateFlow()

    // Set of item IDs where the purchase price has been revealed using the "eye"
    private val _revealedPrices = MutableStateFlow<Set<Int>>(emptySet())
    val revealedPrices: StateFlow<Set<Int>> = _revealedPrices.asStateFlow()

    // --- Transactions Page State ---
    // Sub-tab under Transactions Page (0 = Purchase, 1 = Sale, 2 = Return, 3 = Repair)
    private val _transactionSelection = MutableStateFlow(0)
    val transactionSelection: StateFlow<Int> = _transactionSelection.asStateFlow()

    // Form Fields
    var serialNumberInput = MutableStateFlow("")
    var modelInput = MutableStateFlow("")
    var nameInput = MutableStateFlow("")
    var phoneInput = MutableStateFlow("")
    var aadhaarInput = MutableStateFlow("")
    var amountInput = MutableStateFlow("")
    var descriptionInput = MutableStateFlow("")
    var dateInMillisInput = MutableStateFlow(System.currentTimeMillis())
    var quantityInput = MutableStateFlow(1)
    var photoUriInput = MutableStateFlow<String?>(null)

    // Form fields specific to Transactions-Repair Mode
    var technicianNameInput = MutableStateFlow("")
    var repairReasonInput = MutableStateFlow("")

    private val _isUploadingTransaction = MutableStateFlow(false)
    val isUploadingTransaction: StateFlow<Boolean> = _isUploadingTransaction.asStateFlow()

    private val _transactionError = MutableStateFlow<String?>(null)
    val transactionError: StateFlow<String?> = _transactionError.asStateFlow()

    private val _transactionSuccessMessage = MutableStateFlow<String?>(null)
    val transactionSuccessMessage: StateFlow<String?> = _transactionSuccessMessage.asStateFlow()

    // --- Search autofit tracking ---
    init {
        // Automatically fetch details when serialNumber is scanned or typed in Sales or Returns
        viewModelScope.launch {
            serialNumberInput.collect { sn ->
                if (sn.isNotBlank() && (_transactionSelection.value == 1 || _transactionSelection.value == 2)) {
                    val matchingItem = repository.getItemBySerialNumber(sn.trim())
                    if (matchingItem != null) {
                        modelInput.value = matchingItem.model
                        nameInput.value = matchingItem.name
                        amountInput.value = matchingItem.amount.toString()
                        descriptionInput.value = matchingItem.description
                    }
                }
            }
        }
    }

    // --- History Page State ---
    val historyEvents: StateFlow<List<HistoryEvent>> = repository.allHistoryEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _historySearchTerm = MutableStateFlow("")
    val historySearchTerm: StateFlow<String> = _historySearchTerm.asStateFlow()

    // Sort by Date, Type, Amount
    private val _historySortOption = MutableStateFlow("Timestamp")
    val historySortOption: StateFlow<String> = _historySortOption.asStateFlow()

    // Action filter: "All", "PURCHASE", "SALE", "REPAIR_SENT", "REPAIR_RETURNED", "RETURN", "EDIT", "DELETE"
    private val _historyTypeFilter = MutableStateFlow("All")
    val historyTypeFilter: StateFlow<String> = _historyTypeFilter.asStateFlow()

    // --- Auth Actions ---
    fun login(usernameStr: String, passwordStr: String) {
        viewModelScope.launch {
            val username = usernameStr.trim()
            val user = repository.getUserByUsername(username)
            
            if (user != null) {
                if (user.passwordHash == passwordStr) {
                    _isLoggedIn.value = true
                    _loggedInUser.value = user
                    _loginError.value = null
                    _activeTab.value = 0
                    triggerCloudSync()
                } else {
                    _loginError.value = "Invalid username or password."
                }
            } else {
                // Seed Admin if database has no users and admin/admin is tried
                val userCount = repository.getUserCount()
                if (userCount == 0 && username == "admin" && passwordStr == "admin") {
                    val adminUser = User("admin", "admin", "Admin")
                    repository.insertUser(adminUser)
                    _isLoggedIn.value = true
                    _loggedInUser.value = adminUser
                    _loginError.value = null
                    _activeTab.value = 0
                    triggerCloudSync()
                } else {
                    _loginError.value = "User not found or invalid credentials."
                }
            }
        }
    }

    fun biometricLogin() {
        viewModelScope.launch {
            var user = repository.getUserByUsername("admin")
            if (user == null) {
                val userCount = repository.getUserCount()
                if (userCount == 0) {
                    val adminUser = User("admin", "admin", "Admin")
                    repository.insertUser(adminUser)
                    user = adminUser
                }
            }
            if (user != null) {
                _isLoggedIn.value = true
                _loggedInUser.value = user
                _loginError.value = null
                _activeTab.value = 0
                triggerCloudSync()
            } else {
                _loginError.value = "Default administrator user could not be initialized."
            }
        }
    }

    fun logout() {
        _isLoggedIn.value = false
        _loggedInUser.value = null
        _activeTab.value = 0
    }

    // --- Cloud Sync Mock ---
    fun triggerCloudSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            delay(1800) // simulation latency
            _lastSyncedTime.value = System.currentTimeMillis()
            _isSyncing.value = false
        }
    }

    fun refreshAllPages() {
        viewModelScope.launch {
            _isRefreshing.value = true
            triggerCloudSync()
            delay(1200)
            _isRefreshing.value = false
        }
    }

    // --- Global Nav ---
    fun setTab(index: Int) {
        _activeTab.value = index
    }

    // --- Price Reveal toggle ---
    fun togglePriceReveal(itemId: Int) {
        val currentSet = _revealedPrices.value
        if (currentSet.contains(itemId)) {
            _revealedPrices.value = currentSet - itemId
        } else {
            _revealedPrices.value = currentSet + itemId
        }
    }

    // --- Inventory subtab toggle (0 = All, 1 = Repair) ---
    fun setInventorySubTab(subTab: Int) {
        _inventorySubTab.value = subTab
    }

    fun setInventorySearchTerm(term: String) {
        _inventorySearchTerm.value = term
    }

    fun setInventorySortOption(option: String) {
        _inventorySortOption.value = option
    }

    fun toggleInventorySortOrder() {
        _inventorySortAscending.value = !_inventorySortAscending.value
    }

    // --- History search & Filter ---
    fun setHistorySearchTerm(term: String) {
        _historySearchTerm.value = term
    }

    fun setHistoryTypeFilter(filter: String) {
        _historyTypeFilter.value = filter
    }

    fun setHistorySortOption(option: String) {
        _historySortOption.value = option
    }

    val allUsers: StateFlow<List<User>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addUser(username: String, passwordHash: String, role: String) {
        viewModelScope.launch {
            repository.insertUser(User(username, passwordHash, role))
        }
    }
    
    fun deleteUser(user: User) {
        viewModelScope.launch {
            repository.deleteUser(user)
        }
    }
    
    // --- Actions under Inventory cards ---
    fun markItemForRepair(itemId: Int, technician: String, reason: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            val success = repository.sendItemToRepair(itemId, technician, reason, _loggedInUser.value?.username ?: "admin")
            if (success) {
                triggerCloudSync()
            }
        }
    }

    fun resolveRepairItem(itemId: Int) {
        viewModelScope.launch {
            _isSyncing.value = true
            val success = repository.returnItemFromRepair(itemId, _loggedInUser.value?.username ?: "admin")
            if (success) {
                triggerCloudSync()
            }
        }
    }

    fun editInventoryItem(itemId: Int, updatedItem: InventoryItem) {
        viewModelScope.launch {
            _isSyncing.value = true
            val success = repository.updateInventoryItem(updatedItem, _loggedInUser.value?.username ?: "admin")
            if (success) {
                triggerCloudSync()
            }
        }
    }

    fun deleteInventoryItem(itemId: Int) {
        viewModelScope.launch {
            _isSyncing.value = true
            val success = repository.deleteInventoryItem(itemId, _loggedInUser.value?.username ?: "admin")
            if (success) {
                triggerCloudSync()
            }
        }
    }

    // --- Transaction Form Actions ---
    fun setTransactionSelection(selection: Int) {
        _transactionSelection.value = selection
        clearFormErrorAndSuccess()

        // Sync inputs conditionally based on what's matching in stock
        val currentSN = serialNumberInput.value
        if (currentSN.isNotBlank() && (selection == 1 || selection == 2)) {
            viewModelScope.launch {
                val matchingItem = repository.getItemBySerialNumber(currentSN.trim())
                if (matchingItem != null) {
                    modelInput.value = matchingItem.model
                    nameInput.value = matchingItem.name
                    amountInput.value = matchingItem.amount.toString()
                    descriptionInput.value = matchingItem.description
                }
            }
        }
    }

    fun clearFormErrorAndSuccess() {
        _transactionError.value = null
        _transactionSuccessMessage.value = null
    }

    fun resetTransactionForm() {
        serialNumberInput.value = ""
        modelInput.value = ""
        nameInput.value = ""
        phoneInput.value = ""
        aadhaarInput.value = ""
        amountInput.value = ""
        descriptionInput.value = ""
        dateInMillisInput.value = System.currentTimeMillis()
        quantityInput.value = 1
        photoUriInput.value = null
        technicianNameInput.value = ""
        repairReasonInput.value = ""
        _transactionError.value = null
        _transactionSuccessMessage.value = null
    }

    fun executeTransaction() {
        val typeId = _transactionSelection.value // 0: Purchase, 1: Sale, 2: Return, 3: Repair
        val serialNumber = serialNumberInput.value.trim()
        val model = modelInput.value.trim()
        val name = nameInput.value.trim()
        val phone = phoneInput.value.trim()
        val aadhaar = aadhaarInput.value.trim()
        val amountStr = amountInput.value.trim()
        val desc = descriptionInput.value.trim()
        val dateInMillis = dateInMillisInput.value
        val qty = quantityInput.value
        val photo = photoUriInput.value

        // Validation
        if (serialNumber.isBlank() || model.isBlank() || name.isBlank() || amountStr.isBlank()) {
            _transactionError.value = "All fields are mandatory except those marked optional."
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount < 0) {
            _transactionError.value = "Amount must be a non-negative number."
            return
        }

        if (qty <= 0) {
            _transactionError.value = "Quantity must be at least 1."
            return
        }

        // Repair validates extra technician state
        val tech = technicianNameInput.value.trim()
        val reason = repairReasonInput.value.trim()
        if (typeId == 3) {
            if (tech.isBlank() || reason.isBlank()) {
                _transactionError.value = "Technician name and Repair reason are mandatory for repair category."
                return
            }
        }

        viewModelScope.launch {
            _isUploadingTransaction.value = true
            _transactionError.value = null
            delay(1500) // simulation latency so user sees loading flow

            val activeUser = _loggedInUser.value?.username ?: "admin"
            var success = false

            when (typeId) {
                0 -> { // Purchase
                    success = repository.purchaseProduct(
                        serialNumber = serialNumber,
                        model = model,
                        name = name,
                        phoneNumber = phone.ifBlank { null },
                        aadhaarNumber = aadhaar.ifBlank { null },
                        amount = amount,
                        description = desc,
                        dateInMillis = dateInMillis,
                        quantity = qty,
                        photoUri = photo,
                        userId = activeUser
                    )
                    if (success) _transactionSuccessMessage.value = "Purchase logged successfully! Added to stock."
                }
                1 -> { // Sale
                    // Verify stock availability
                    val stockItem = repository.getItemBySerialNumber(serialNumber)
                    if (stockItem == null) {
                        _transactionError.value = "Item with Serial Number/IMEI '$serialNumber' is not in stock!"
                        _isUploadingTransaction.value = false
                        return@launch
                    }
                    if (stockItem.isUnderRepair) {
                        _transactionError.value = "Cannot sell. Item is currently out for repair."
                        _isUploadingTransaction.value = false
                        return@launch
                    }

                    success = repository.saleProduct(
                        serialNumber = serialNumber,
                        model = model,
                        name = name,
                        phoneNumber = phone.ifBlank { null },
                        aadhaarNumber = aadhaar.ifBlank { null },
                        amount = amount,
                        description = desc,
                        dateInMillis = dateInMillis,
                        quantity = qty,
                        photoUri = photo,
                        userId = activeUser
                    )
                    if (success) _transactionSuccessMessage.value = "Sale logged successfully! Removed from stock."
                }
                2 -> { // Return
                    success = repository.returnProduct(
                        serialNumber = serialNumber,
                        model = model,
                        name = name,
                        phoneNumber = phone.ifBlank { null },
                        aadhaarNumber = aadhaar.ifBlank { null },
                        amount = amount,
                        description = desc,
                        dateInMillis = dateInMillis,
                        quantity = qty,
                        photoUri = photo,
                        userId = activeUser
                    )
                    if (success) _transactionSuccessMessage.value = "Return logged successfully! Added to stock."
                }
                3 -> { // Repair
                    success = repository.directRepair(
                        serialNumber = serialNumber,
                        model = model,
                        name = name,
                        phoneNumber = phone.ifBlank { null },
                        aadhaarNumber = aadhaar.ifBlank { null },
                        amount = amount,
                        description = desc,
                        dateInMillis = dateInMillis,
                        quantity = qty,
                        photoUri = photo,
                        userId = activeUser,
                        technicianName = tech,
                        repairReason = reason
                    )
                    if (success) _transactionSuccessMessage.value = "Repair logged successfully! Item is added to repair pool."
                }
            }

            _isUploadingTransaction.value = false
            if (success) {
                resetTransactionForm()
                triggerCloudSync()
            } else {
                _transactionError.value = "Failed to finalize database query record. Check parameters."
            }
        }
    }
}

// Simple Factory provider
class ViewModelFactory(private val repository: InventoryRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StockViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StockViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
