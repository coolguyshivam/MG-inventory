package com.example.ui.viewmodel

import android.content.Context
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

data class Partner(
    val name: String,
    val phone: String,
    val aadhaar: String
)

class StockViewModel(private val repository: InventoryRepository) : ViewModel() {

    // --- Theme State (Default to Light Theme) ---
    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    // --- Authentication & Biometric State ---
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _loggedInUser = MutableStateFlow<User?>(null)
    val loggedInUser: StateFlow<User?> = _loggedInUser.asStateFlow()

    val showBiometricLinkingDialog = MutableStateFlow(false)
    val tempPendingUser = MutableStateFlow<User?>(null)

    fun getBiometricRegisteredUser(context: Context): String? {
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        return prefs.getString("biometric_username", null)
    }

    fun registerBiometrics(context: Context, username: String) {
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("biometric_username", username).apply()
    }

    fun completeLogin(user: User) {
        _isLoggedIn.value = true
        _loggedInUser.value = user
        _loginError.value = null
        _activeTab.value = 0
        triggerCloudSync()
    }

    // Permissions based on Role
    // Admin: canManageUsers, canManageInventory, canRepair, canViewAnalytics, canSell, canDelete
    // Manager: canManageInventory, canRepair, canViewAnalytics, canSell, canDelete
    // Operator: canManageInventory, canRepair
    // MIS: canViewAnalytics
    // Sales: canSell, canViewAnalytics
    
    val canManageUsers = _loggedInUser.map { it?.role == "Admin" }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canManageInventory = _loggedInUser.map { it?.role in listOf("Admin", "Manager", "Operator") }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canRepair = _loggedInUser.map { it?.role in listOf("Admin", "Manager", "Operator") }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canViewAnalytics = _loggedInUser.map { it?.role == "Admin" }.stateIn(viewModelScope, SharingStarted.Eagerly, false) // Restricted to Admin Only
    val canSeePrice = _loggedInUser.map { it?.role in listOf("Admin", "Manager") }.stateIn(viewModelScope, SharingStarted.Eagerly, false) // Restricted to Admin & Manager
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

    // --- Regular Partners/Vendors & Ledger auto-population list ---
    val regularPartners = listOf(
        Partner("Shrinath Telecom", "9876543210", "123456789012"),
        Partner("Rajat Distributors", "9988776655", "987654321098"),
        Partner("Ananya Mobile Point", "9123456789", "111122223333"),
        Partner("Sandeep Enterprises", "7766554433", "444455556666")
    )

    fun startDirectSale(item: InventoryItem) {
        _activeTab.value = 1 // Navigate to Transactions
        _transactionSelection.value = 1 // Choose "Sale" state category
        serialNumberInput.value = item.serialNumber
        modelInput.value = item.model
        // Empty all other fields as per Rule 7 and clean UI criteria
        nameInput.value = ""
        phoneInput.value = ""
        aadhaarInput.value = ""
        amountInput.value = ""
        descriptionInput.value = ""
        quantityInput.value = 1
        photoUriInput.value = null
        clearFormErrorAndSuccess()
    }

    // --- Search autofit tracking (Rule 7 logic & reactivity) ---
    init {
        // Seed initial admin user eagerly so biometric and standard logins are fully functional instantly
        viewModelScope.launch {
            try {
                val count = repository.getUserCount()
                if (count == 0) {
                    repository.insertUser(User("admin", "admin", "Admin"))
                }
                
                // Seed default items and audit histories if empty to restore vanished cards
                val currentItems = repository.allInventoryItems.first()
                if (currentItems.isEmpty()) {
                    repository.purchaseProduct(
                        serialNumber = "354920056123456",
                        model = "Pixel 9 Pro",
                        name = "Google Pixel 9 Pro (128GB)",
                        phoneNumber = "9876543210",
                        aadhaarNumber = "123456789012",
                        amount = 99999.00,
                        description = "Brand new inbound stock from Delhi distributor",
                        dateInMillis = System.currentTimeMillis() - 86400000 * 2,
                        quantity = 5,
                        photoUri = "ic_phone_blue",
                        userId = "admin"
                    )
                    
                    repository.purchaseProduct(
                        serialNumber = "880439821876543",
                        model = "Galaxy S25 Ultra",
                        name = "Samsung Galaxy S25 Ultra",
                        phoneNumber = "9988776655",
                        aadhaarNumber = "987654321098",
                        amount = 129000.00,
                        description = "Pre-owned high tier premium stock intake",
                        dateInMillis = System.currentTimeMillis() - 86400000,
                        quantity = 3,
                        photoUri = "ic_phone_amber",
                        userId = "admin"
                    )
                    
                    repository.purchaseProduct(
                        serialNumber = "998247716900124",
                        model = "iPhone 16 Pro Max",
                        name = "Apple iPhone 16 Pro Max",
                        phoneNumber = "9123456789",
                        aadhaarNumber = "111122223333",
                        amount = 144900.00,
                        description = "Direct store incoming purchase",
                        dateInMillis = System.currentTimeMillis() - 3600000 * 4,
                        quantity = 2,
                        photoUri = "ic_watch",
                        userId = "admin"
                    )
                    
                    repository.directRepair(
                        serialNumber = "123456789012345",
                        model = "OnePlus 12",
                        name = "OnePlus 12 Black Onyx",
                        phoneNumber = "7766554433",
                        aadhaarNumber = "444455556666",
                        amount = 64999.00,
                        description = "Display flickering issue repair inbound log",
                        dateInMillis = System.currentTimeMillis() - 3600000 * 2,
                        quantity = 1,
                        photoUri = "ic_tablet",
                        userId = "admin",
                        technicianName = "John Miller",
                        repairReason = "Screen Replacement"
                    )
                }
            } catch (e: Exception) {
                // Safe exception catch during database startup
            }
        }

        // Collect modern combination of selection state category and search serial input
        viewModelScope.launch {
            try {
                combine(serialNumberInput, _transactionSelection) { sn, selection ->
                    Pair(sn, selection)
                }.collect { (sn, selection) ->
                    try {
                        val trimmedSn = sn.trim()
                        if (trimmedSn.isNotBlank()) {
                            val matchingItem = repository.getItemBySerialNumber(trimmedSn)
                            if (matchingItem != null) {
                                if (selection == 1) { // SALE Category
                                    // Rule 7: Model number should autofill, but other fields should be empty.
                                    modelInput.value = matchingItem.model
                                    nameInput.value = ""
                                    phoneInput.value = ""
                                    aadhaarInput.value = ""
                                    amountInput.value = ""
                                    descriptionInput.value = ""
                                } else if (selection == 2) { // RETURN Category
                                    // Rule 7: For returns, all details should be copied.
                                    modelInput.value = matchingItem.model
                                    nameInput.value = matchingItem.name
                                    phoneInput.value = matchingItem.phoneNumber ?: ""
                                    aadhaarInput.value = matchingItem.aadhaarNumber ?: ""
                                    amountInput.value = matchingItem.amount.toString()
                                    descriptionInput.value = matchingItem.description
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
    fun login(context: Context, usernameStr: String, passwordStr: String) {
        viewModelScope.launch {
            try {
                val username = usernameStr.trim()
                val user = repository.getUserByUsername(username)
                
                if (user != null) {
                    if (user.passwordHash == passwordStr) {
                        val registeredUser = getBiometricRegisteredUser(context)
                        if (registeredUser == username) {
                            completeLogin(user)
                        } else {
                            // First time login - prompt to enable biometric login
                            tempPendingUser.value = user
                            showBiometricLinkingDialog.value = true
                        }
                    } else {
                        _loginError.value = "Invalid username or password."
                    }
                } else {
                    // Seed Admin if database has no users and admin/admin is tried
                    val userCount = repository.getUserCount()
                    if (userCount == 0 && username == "admin" && passwordStr == "admin") {
                        val adminUser = User("admin", "admin", "Admin")
                        repository.insertUser(adminUser)
                        val registeredUser = getBiometricRegisteredUser(context)
                        if (registeredUser == "admin") {
                            completeLogin(adminUser)
                        } else {
                            tempPendingUser.value = adminUser
                            showBiometricLinkingDialog.value = true
                        }
                    } else {
                        _loginError.value = "User not found or invalid credentials."
                    }
                }
            } catch (e: Exception) {
                _loginError.value = "Sign-in error: ${e.message}"
            }
        }
    }

    fun biometricLogin(context: Context) {
        viewModelScope.launch {
            try {
                val registeredUser = getBiometricRegisteredUser(context)
                if (registeredUser != null) {
                    val user = repository.getUserByUsername(registeredUser)
                    if (user != null) {
                        _isLoggedIn.value = true
                        _loggedInUser.value = user
                        _loginError.value = null
                        _activeTab.value = 0
                        triggerCloudSync()
                    } else {
                        _loginError.value = "Biometric account not found. Please log in with password to re-link."
                    }
                } else {
                    _loginError.value = "Biometrics not set up yet. Log in with password once to link fingerprint."
                }
            } catch (e: Exception) {
                _loginError.value = "Biometric authentication failed: ${e.message}"
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

        // IMEI - exactly 15 numeric digits (Rule 3)
        if (!serialNumber.matches(Regex("^\\d{15}$"))) {
            _transactionError.value = "IMEI must be exactly 15 numeric digits."
            return
        }

        // Phone - 10 digits starting with 6-9 (Rule 3)
        if (phone.isNotBlank() && !phone.matches(Regex("^[6-9]\\d{9}$"))) {
            _transactionError.value = "Phone number must be optional or a valid 10-digit number starting with 6-9."
            return
        }

        // Aadhaar - exactly 12 numeric digits (Rule 3)
        if (aadhaar.isNotBlank() && !aadhaar.matches(Regex("^\\d{12}$"))) {
            _transactionError.value = "Aadhaar number must be optional or a valid 12-digit numeric code."
            return
        }

        // Selecting future dates is not allowed (Rule 14)
        val now = System.currentTimeMillis()
        if (dateInMillis > now + 60_000) { // 1 min buffer
            _transactionError.value = "Selecting future dates is not allowed."
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
                    val existing = repository.getItemBySerialNumber(serialNumber)
                    if (existing != null && (existing.quantity > 0 || existing.isUnderRepair)) {
                        _transactionError.value = "Cannot purchase back: Item with IMEI/Serial '$serialNumber' is already in inventory or repair."
                        _isUploadingTransaction.value = false
                        return@launch
                    }

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
