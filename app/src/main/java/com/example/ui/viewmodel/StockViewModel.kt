package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.HistoryEvent
import com.example.data.model.InventoryItem
import com.example.data.model.User
import com.example.data.model.AttendanceRecord
import com.example.data.model.LeaveApplication
import com.example.data.model.NotificationLog
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

    // --- App Icon & Theme Style ---
    private val _appIconStyle = MutableStateFlow("Classic Slate")
    val appIconStyle: StateFlow<String> = _appIconStyle.asStateFlow()

    fun loadAppIconStyle(context: Context) {
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        _appIconStyle.value = prefs.getString("app_icon_style", "Classic Slate") ?: "Classic Slate"
    }

    fun setAppIconStyle(context: Context, style: String) {
        _appIconStyle.value = style
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_icon_style", style).apply()
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
    val canManageInventory = _loggedInUser.map { it?.role in listOf("Admin", "Manager", "Operator", "MIS") }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canRepair = _loggedInUser.map { it?.role in listOf("Admin", "Manager", "Operator", "MIS") }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canViewAnalytics = _loggedInUser.map { it?.role == "Admin" }.stateIn(viewModelScope, SharingStarted.Eagerly, false) // Restricted to Admin Only
    val canSeePrice = _loggedInUser.map { it?.role in listOf("Admin", "Manager", "MIS") }.stateIn(viewModelScope, SharingStarted.Eagerly, false) // Restricted to Admin & Manager
    val canSell = _loggedInUser.map { it?.role in listOf("Admin", "Manager", "Sales", "MIS") }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canDelete = _loggedInUser.map { it?.role in listOf("Admin", "Manager", "MIS") }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val canViewLedger = _loggedInUser.map { it?.role in listOf("Admin", "Manager", "MIS") }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    val allParties: StateFlow<List<com.example.data.model.Party>> = repository.allParties
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLedgerEntries: StateFlow<List<com.example.data.model.LedgerEntry>> = repository.allLedgerEntries
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
    private val _revealedPrices = MutableStateFlow<Set<String>>(emptySet())
    val revealedPrices: StateFlow<Set<String>> = _revealedPrices.asStateFlow()

    // --- Transactions Page State ---
    // Sub-tab under Transactions Page (0 = Purchase, 1 = Sale, 2 = Return, 3 = Repair)
    private val _transactionSelection = MutableStateFlow(0)
    val transactionSelection: StateFlow<Int> = _transactionSelection.asStateFlow()

    // Form Fields
    var serialNumberInput = MutableStateFlow("") // Maintained for single or global operations, but can be synced to subItems[0]
    var modelInput = MutableStateFlow("")
    var nameInput = MutableStateFlow("")
    var phoneInput = MutableStateFlow("")
    var aadhaarInput = MutableStateFlow("")
    var amountInput = MutableStateFlow("")
    var addressInput = MutableStateFlow("")
    var descriptionInput = MutableStateFlow("")
    var dateInMillisInput = MutableStateFlow(System.currentTimeMillis())
    
    data class TransactionSubItem(
        val serialNumber: String = "",
        val amount: String = ""
    )
    val transactionSubItems = MutableStateFlow(listOf(TransactionSubItem()))
    
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
        transactionSubItems.value = listOf(TransactionSubItem(serialNumber = item.serialNumber, amount = item.amount.toInt().toString()))
        syncAggregatedFormState()
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
        
        viewModelScope.launch {
            try {
                combine(allUsers, allAttendanceRecords, allNotifications) { users, attendance, notifications ->
                    Triple(users, attendance, notifications)
                }.collect { (users, attendance, notifications) ->
                    if (users.isNotEmpty() && attendance.isNotEmpty()) {
                        checkAndTriggerAbsences(users, attendance, notifications)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        viewModelScope.launch {
            try {
                nameInput.collect { typedName ->
                    val nameStr = typedName.trim()
                    if (nameStr.isNotEmpty()) {
                        val all = allParties.value
                        val party = all.find { it.name.equals(nameStr, ignoreCase = true) }
                        if (party != null) {
                            if (phoneInput.value.isBlank()) phoneInput.value = party.phoneNumber
                            if (aadhaarInput.value.isBlank()) aadhaarInput.value = party.aadhaarNumber
                        }
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

    // --- Cloud Sync Realtime (Now handled by Firestore listeners) ---
    fun triggerCloudSync() {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                delay(1000) // simulation delay for nice UI feedback
            } catch (e: Exception) {
                Log.e("StockViewModel", "Error in sync pipeline", e)
            } finally {
                _lastSyncedTime.value = System.currentTimeMillis()
                _isSyncing.value = false
            }
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
    fun togglePriceReveal(itemId: String) {
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

    val allAttendanceRecords: StateFlow<List<AttendanceRecord>> = repository.allAttendanceRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLeaveApplications: StateFlow<List<LeaveApplication>> = repository.allLeaveApplications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotifications: StateFlow<List<NotificationLog>> = repository.allNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markCheckIn(context: Context, selfieBase64: String, location: String) {
        val currentUser = _loggedInUser.value ?: return
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val todayStr = sdf.format(java.util.Date(now))

                val record = AttendanceRecord(
                    id = java.util.UUID.randomUUID().toString(),
                    userId = currentUser.username,
                    userName = currentUser.username,
                    dateString = todayStr,
                    checkInTime = now,
                    checkInSelfieBase64 = selfieBase64,
                    checkInLocationSpec = location,
                    status = "Present"
                )
                repository.insertAttendanceRecord(record)

                val message = "Employee ${currentUser.username} checked in at ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(now))} from $location."
                val notification = NotificationLog(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "New Check-In!",
                    message = message,
                    timestamp = now,
                    type = "CHECK_IN"
                )
                repository.insertNotification(notification)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun markCheckOut(context: Context, selfieBase64: String, location: String) {
        val currentUser = _loggedInUser.value ?: return
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val todayStr = sdf.format(java.util.Date(now))

                val existing = allAttendanceRecords.value.find { it.userId == currentUser.username && it.dateString == todayStr }
                val updated = if (existing != null) {
                    existing.copy(
                        checkOutTime = now,
                        checkOutSelfieBase64 = selfieBase64,
                        checkOutLocationSpec = location
                    )
                } else {
                    AttendanceRecord(
                        id = java.util.UUID.randomUUID().toString(),
                        userId = currentUser.username,
                        userName = currentUser.username,
                        dateString = todayStr,
                        checkInTime = now - 3600000,
                        checkOutTime = now,
                        checkOutSelfieBase64 = selfieBase64,
                        checkOutLocationSpec = location,
                        status = "Present"
                    )
                }
                repository.insertAttendanceRecord(updated)

                val message = "Employee ${currentUser.username} checked out at ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(now))}."
                val notification = NotificationLog(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "New Check-Out!",
                    message = message,
                    timestamp = now,
                    type = "CHECK_OUT"
                )
                repository.insertNotification(notification)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun applyForLeave(startDate: String, endDate: String, type: String, reason: String) {
        val currentUser = _loggedInUser.value ?: return
        viewModelScope.launch {
            try {
                val leave = LeaveApplication(
                    id = java.util.UUID.randomUUID().toString(),
                    userId = currentUser.username,
                    userName = currentUser.username,
                    startDateString = startDate,
                    endDateString = endDate,
                    leaveType = type,
                    reason = reason,
                    status = "Pending"
                )
                repository.insertLeaveApplication(leave)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun approveOrRejectLeave(leave: LeaveApplication, status: String, approverName: String) {
        viewModelScope.launch {
            try {
                val updatedLeave = leave.copy(
                    status = status,
                    approvedBy = approverName
                )
                repository.updateLeaveApplication(updatedLeave)

                if (status == "Approved") {
                    val dates = getDatesList(leave.startDateString, leave.endDateString)
                    for (d in dates) {
                        val record = AttendanceRecord(
                            id = java.util.UUID.randomUUID().toString(),
                            userId = leave.userId,
                            userName = leave.userName,
                            dateString = d,
                            status = "On Leave",
                            notes = "Leave Approved: ${leave.reason} (${leave.leaveType})"
                        )
                        repository.insertAttendanceRecord(record)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun modifyAttendance(userId: String, userName: String, dateString: String, status: String, notes: String) {
        viewModelScope.launch {
            try {
                val existing = allAttendanceRecords.value.find { it.userId == userId && it.dateString == dateString }
                val updated = if (existing != null) {
                    existing.copy(
                        status = status,
                        notes = notes
                    )
                } else {
                    AttendanceRecord(
                        id = java.util.UUID.randomUUID().toString(),
                        userId = userId,
                        userName = userName,
                        dateString = dateString,
                        checkInTime = if (status == "Present") System.currentTimeMillis() else 0,
                        status = status,
                        notes = notes
                    )
                }
                repository.insertAttendanceRecord(updated)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getDatesList(startStr: String, endStr: String): List<String> {
        val result = mutableListOf<String>()
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val startDate = sdf.parse(startStr)
            val endDate = sdf.parse(endStr)
            
            if (startDate != null && endDate != null) {
                val calendar = java.util.Calendar.getInstance()
                calendar.time = startDate
                while (!calendar.time.after(endDate)) {
                    result.add(sdf.format(calendar.time))
                    calendar.add(java.util.Calendar.DATE, 1)
                }
            }
        } catch (e: Exception) {
            result.add(startStr)
        }
        return result
    }

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
    fun markItemForRepair(itemId: String, technician: String, reason: String) {
        viewModelScope.launch {
            try {
                _isSyncing.value = true
                val success = repository.sendItemToRepair(itemId, technician, reason, _loggedInUser.value?.username ?: "admin")
                if (success) {
                    triggerCloudSync()
                }
            } catch (e: Exception) {
                Log.e("StockViewModel", "Error marking repair", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun resolveRepairItem(itemId: String) {
        viewModelScope.launch {
            try {
                _isSyncing.value = true
                val success = repository.returnItemFromRepair(itemId, _loggedInUser.value?.username ?: "admin")
                if (success) {
                    triggerCloudSync()
                }
            } catch (e: Exception) {
                Log.e("StockViewModel", "Error resolving repair", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun editInventoryItem(itemId: String, updatedItem: InventoryItem) {
        viewModelScope.launch {
            try {
                _isSyncing.value = true
                val success = repository.updateInventoryItem(updatedItem, _loggedInUser.value?.username ?: "admin")
                if (success) {
                    triggerCloudSync()
                }
            } catch (e: Exception) {
                Log.e("StockViewModel", "Error editing item", e)
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun deleteInventoryItem(itemId: String) {
        viewModelScope.launch {
            try {
                _isSyncing.value = true
                val success = repository.deleteInventoryItem(itemId, _loggedInUser.value?.username ?: "admin")
                if (success) {
                    triggerCloudSync()
                }
            } catch (e: Exception) {
                Log.e("StockViewModel", "Error deleting item", e)
            } finally {
                _isSyncing.value = false
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
        transactionSubItems.value = listOf(TransactionSubItem())
        serialNumberInput.value = ""
        modelInput.value = ""
        nameInput.value = ""
        phoneInput.value = ""
        aadhaarInput.value = ""
        amountInput.value = ""
        addressInput.value = ""
        descriptionInput.value = ""
        dateInMillisInput.value = System.currentTimeMillis()
        quantityInput.value = 1
        photoUriInput.value = null
        technicianNameInput.value = ""
        repairReasonInput.value = ""
        _transactionError.value = null
        _transactionSuccessMessage.value = null
    }

    fun addSubItem() {
        quantityInput.value += 1
        transactionSubItems.value = transactionSubItems.value + TransactionSubItem()
    }

    fun removeSubItem() {
        if (quantityInput.value > 1) {
            quantityInput.value -= 1
            transactionSubItems.value = transactionSubItems.value.dropLast(1)
            syncAggregatedFormState()
        }
    }

    fun updateSubItem(index: Int, sn: String, amt: String) {
        val items = transactionSubItems.value.toMutableList()
        items[index] = items[index].copy(serialNumber = sn, amount = amt)
        transactionSubItems.value = items
        syncAggregatedFormState()
    }
    
    private fun syncAggregatedFormState() {
        val items = transactionSubItems.value
        amountInput.value = items.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }.toString()
        if (items.isNotEmpty()) {
            serialNumberInput.value = items[0].serialNumber
        }
    }

    fun executeTransaction() {
        val typeId = _transactionSelection.value // 0: Purchase, 1: Sale, 2: Return, 3: Repair
        val model = modelInput.value.trim()
        val name = nameInput.value.trim()
        val phone = phoneInput.value.trim()
        val aadhaar = aadhaarInput.value.trim()
        val address = addressInput.value.trim()
        val rawDesc = descriptionInput.value.trim()
        val desc = if (address.isNotBlank()) "Address: $address\n$rawDesc" else rawDesc
        val dateInMillis = dateInMillisInput.value
        val itemsToProcess = transactionSubItems.value
        val photo = photoUriInput.value

        // Validation
        if (model.isBlank() || name.isBlank()) {
            _transactionError.value = "Model and Name fields are mandatory."
            return
        }

        val inputImeis = itemsToProcess.map { it.serialNumber.trim() }
        if (inputImeis.size != inputImeis.distinct().size) {
            _transactionError.value = "Duplicate IMEI numbers are not allowed in the same transaction."
            return
        }

        for (item in itemsToProcess) {
            val serialNumber = item.serialNumber.trim()
            val amountStr = item.amount.trim()

            if (serialNumber.isBlank() || amountStr.isBlank()) {
                _transactionError.value = "All fields are mandatory except those marked optional."
                return
            }

            // IMEI - exactly 15 numeric digits (Rule 3)
            if (!serialNumber.matches(Regex("^\\d{15}$"))) {
                _transactionError.value = "IMEI must be exactly 15 numeric digits for item $serialNumber."
                return
            }
            
            val amount = amountStr.toDoubleOrNull()
            if (amount == null || amount < 0) {
                _transactionError.value = "Amount must be a non-negative number for item $serialNumber."
                return
            }
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
            try {
                _isUploadingTransaction.value = true
                _transactionError.value = null
                delay(1500) // simulation latency so user sees loading flow

                val activeUser = _loggedInUser.value?.username ?: "admin"
                var allSuccess = true

                // PRE-VALIDATION: Ensure all items pass basic checks so it's all-or-nothing
                for (item in itemsToProcess) {
                    val sn = item.serialNumber.trim()
                    when (typeId) {
                        0 -> { // Purchase
                            val existing = repository.getItemBySerialNumber(sn)
                            if (existing != null && (existing.quantity > 0 || existing.isUnderRepair)) {
                                _transactionError.value = "Failed at item: '$sn'. Cannot purchase back, already in inventory or repair."
                                _isUploadingTransaction.value = false
                                return@launch
                            }
                        }
                        1 -> { // Sale
                            val stockItem = repository.getItemBySerialNumber(sn)
                            if (stockItem == null) {
                                _transactionError.value = "Failed at item: '$sn'. Not found in stock."
                                _isUploadingTransaction.value = false
                                return@launch
                            }
                            if (stockItem.isUnderRepair) {
                                _transactionError.value = "Failed at item: '$sn'. Cannot sell, out for repair."
                                _isUploadingTransaction.value = false
                                return@launch
                            }
                        }
                        // Returns and Repair don't have strictly blocking conditions here since DB updates handle it, 
                        // but you could add if you want to ensure the items exist.
                        3 -> { // Repair
                            val stockItem = repository.getItemBySerialNumber(sn)
                            if (stockItem == null || stockItem.quantity <= 0) {
                                _transactionError.value = "Failed at item: '$sn'. Not found in stock to send for repair."
                                _isUploadingTransaction.value = false
                                return@launch
                            }
                        }
                    }
                }

                val timeoutResult = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                    for (item in itemsToProcess) {
                        val serialNumber = item.serialNumber.trim()
                        val amount = item.amount.trim().toDoubleOrNull() ?: 0.0
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
                                    quantity = 1,
                                    photoUri = photo,
                                    userId = activeUser
                                )
                            }
                            1 -> { // Sale
                                success = repository.saleProduct(
                                    serialNumber = serialNumber,
                                    model = model,
                                    name = name,
                                    phoneNumber = phone.ifBlank { null },
                                    aadhaarNumber = aadhaar.ifBlank { null },
                                    amount = amount,
                                    description = desc,
                                    dateInMillis = dateInMillis,
                                    quantity = 1,
                                    photoUri = photo,
                                    userId = activeUser
                                )
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
                                    quantity = 1,
                                    photoUri = photo,
                                    userId = activeUser
                                )
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
                                    quantity = 1,
                                    photoUri = photo,
                                    userId = activeUser,
                                    technicianName = tech,
                                    repairReason = reason
                                )
                            }
                        }
                        if (!success) {
                            allSuccess = false
                            break
                        }
                    }
                    allSuccess
                } // End timeout block

                if (timeoutResult == null && !allSuccess && _transactionError.value == null) {
                    _transactionError.value = "Transaction timed out. Cloud database might be unreachable."
                }

                _isUploadingTransaction.value = false
                if (allSuccess && _transactionError.value == null) {
                    when (typeId) {
                        0 -> _transactionSuccessMessage.value = "Purchase logged successfully! Added ${itemsToProcess.size} item(s)."
                        1 -> _transactionSuccessMessage.value = "Sale logged successfully! Removed ${itemsToProcess.size} item(s)."
                        2 -> _transactionSuccessMessage.value = "Return logged successfully! Added ${itemsToProcess.size} item(s)."
                        3 -> _transactionSuccessMessage.value = "Repair logged successfully! ${itemsToProcess.size} item(s) sent to repair."
                    }
                    resetTransactionForm()
                    triggerCloudSync()
                } else {
                    if (_transactionError.value == null) {
                        _transactionError.value = "Failed to finalize database query record. Check parameters."
                    }
                }
            } catch (e: Exception) {
                Log.e("StockViewModel", "Error in transaction", e)
                _transactionError.value = "Transaction failed: ${e.message}"
            } finally {
                _isUploadingTransaction.value = false
            }
        }
    }

    fun addParty(name: String, phone: String, aadhaar: String) {
        viewModelScope.launch {
            try {
                val party = com.example.data.model.Party(name = name.trim(), phoneNumber = phone.trim(), aadhaarNumber = aadhaar.trim())
                repository.addParty(party)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun editParty(partyId: String, name: String, phone: String, aadhaar: String) {
        viewModelScope.launch {
            try {
                repository.editParty(partyId, name.trim(), phone.trim(), aadhaar.trim())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deleteParty(partyId: String) {
        viewModelScope.launch {
            try {
                repository.deleteParty(partyId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addLedgerPayment(partyId: String, amount: Double, type: String, description: String) {
        viewModelScope.launch {
            try {
                val entry = com.example.data.model.LedgerEntry(
                    partyId = partyId,
                    amount = amount,
                    type = type,
                    description = description
                )
                repository.addLedgerEntry(entry)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun recordSalaryPayment(employeeName: String, amount: Double, description: String) {
        viewModelScope.launch {
            try {
                val existingParties = repository.allParties.first()
                var party = existingParties.find { it.name.trim().lowercase() == employeeName.trim().lowercase() }
                if (party == null) {
                    val newParty = com.example.data.model.Party(
                        name = employeeName.trim(),
                        phoneNumber = "Staff Salary",
                        aadhaarNumber = ""
                    )
                    repository.addParty(newParty)
                    party = newParty
                }
                
                val ledgerEntry = com.example.data.model.LedgerEntry(
                    partyId = party.id,
                    amount = amount,
                    type = "PAYMENT_OUT",
                    description = description
                )
                repository.addLedgerEntry(ledgerEntry)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun checkAndTriggerAbsences(users: List<User>, attendance: List<AttendanceRecord>, notifications: List<NotificationLog>) {
        try {
            val calendar = java.util.Calendar.getInstance()
            val year = calendar.get(java.util.Calendar.YEAR)
            val month = calendar.get(java.util.Calendar.MONTH) // 0-indexed
            val dayOfMonth = calendar.get(java.util.Calendar.DAY_OF_MONTH)
            
            val monthName = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            
            for (user in users) {
                if (user.role == "Admin" || user.role == "Manager") {
                    continue // Manager and Admin do not require to apply for leaves and do not trigger absence alerts
                }
                
                var absentDaysCount = 0
                val absentDaysDates = mutableListOf<String>()
                for (day in 1 until dayOfMonth) {
                    val dateStr = String.format("%04d-%02d-%02d", year, month + 1, day)
                    val record = attendance.find { it.userId == user.username && it.dateString == dateStr }
                    if (record == null) {
                        absentDaysCount++
                        absentDaysDates.add(dateStr)
                    } else if (record.status == "Absent") {
                        absentDaysCount++
                        absentDaysDates.add(dateStr)
                    }
                }
                
                if (absentDaysCount > 4) {
                    val alertId = "ABSENCE_ALERT_${user.username}_${year}_${month + 1}"
                    val alreadyNotified = notifications.any { it.id == alertId }
                    if (!alreadyNotified) {
                        viewModelScope.launch {
                            try {
                                val alertMsg = "Employee ${user.username} has been absent for $absentDaysCount days in $monthName (Dates: ${absentDaysDates.take(5).joinToString(", ")}...). This warning has been shared with him, his manager, and admin."
                                val notification = NotificationLog(
                                    id = alertId,
                                    title = "Excessive Absences alert: ${user.username}",
                                    message = alertMsg,
                                    timestamp = System.currentTimeMillis(),
                                    type = "ABSENCE_ALERT"
                                )
                                repository.insertNotification(notification)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
