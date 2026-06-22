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

    private val _printPriceInPdf = MutableStateFlow(false)
    val printPriceInPdf: StateFlow<Boolean> = _printPriceInPdf.asStateFlow()

    fun loadAppIconStyle(context: Context) {
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        _appIconStyle.value = prefs.getString("app_icon_style", "Classic Slate") ?: "Classic Slate"
    }

    fun setAppIconStyle(context: Context, style: String) {
        _appIconStyle.value = style
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_icon_style", style).apply()
    }

    fun loadPrintPriceInPdf(context: Context) {
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        _printPriceInPdf.value = prefs.getBoolean("print_price_in_pdf", false)
    }

    fun setPrintPriceInPdf(context: Context, enabled: Boolean) {
        _printPriceInPdf.value = enabled
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("print_price_in_pdf", enabled).apply()
    }

    // --- WhatsApp Notification Settings ---
    private val _whatsAppWebhookUrl = MutableStateFlow("")
    val whatsAppWebhookUrl: StateFlow<String> = _whatsAppWebhookUrl.asStateFlow()

    private val _whatsAppEnable = MutableStateFlow(false)
    val whatsAppEnable: StateFlow<Boolean> = _whatsAppEnable.asStateFlow()

    private val _whatsappSparePhoneEnable = MutableStateFlow(false)
    val whatsappSparePhoneEnable: StateFlow<Boolean> = _whatsappSparePhoneEnable.asStateFlow()

    private val _whatsappTargetPhone = MutableStateFlow("")
    val whatsappTargetPhone: StateFlow<String> = _whatsappTargetPhone.asStateFlow()

    fun loadWhatsAppSettings(context: Context) {
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        _whatsAppWebhookUrl.value = prefs.getString("whatsapp_webhook_url", "") ?: ""
        _whatsAppEnable.value = prefs.getBoolean("whatsapp_enable", false)
        _whatsappSparePhoneEnable.value = prefs.getBoolean("whatsapp_spare_phone_enable", false)
        _whatsappTargetPhone.value = prefs.getString("whatsapp_target_phone", "") ?: ""
    }

    fun saveWhatsAppSettings(context: Context, url: String, enabled: Boolean, sparePhoneEnabled: Boolean, targetPhone: String) {
        _whatsAppWebhookUrl.value = url
        _whatsAppEnable.value = enabled
        _whatsappSparePhoneEnable.value = sparePhoneEnabled
        _whatsappTargetPhone.value = targetPhone
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("whatsapp_webhook_url", url)
            .putBoolean("whatsapp_enable", enabled)
            .putBoolean("whatsapp_spare_phone_enable", sparePhoneEnabled)
            .putString("whatsapp_target_phone", targetPhone)
            .apply()
    }

    fun triggerWhatsAppMessage(context: Context, employeeName: String, timeStr: String, status: String, activity: String) {
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        val urlStr = prefs.getString("whatsapp_webhook_url", "") ?: ""
        val enabled = prefs.getBoolean("whatsapp_enable", false)
        val sparePhoneEnabled = prefs.getBoolean("whatsapp_spare_phone_enable", false)
        
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val isCheckingIn = activity.lowercase().contains("in") || status.lowercase().contains("present") && !activity.lowercase().contains("out")
        val verbOption = if (isCheckingIn) "Check-In" else "Check-Out"
        val message = """
            *Employee:* $employeeName
            📅 *Date:* $todayStr
            ⏱ *$verbOption:* $timeStr
        """.trimIndent()
        
        if (sparePhoneEnabled) {
            val title = "[WhatsApp Automation] Check-In"
            com.example.util.AppUtils.postWhatsAppAutomationNotification(context, title, message)
        }
        
        if (enabled && urlStr.isNotBlank()) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val url = java.net.URL(urlStr)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    
                    val escapedMessage = message.replace("\n", "\\n").replace("\"", "\\\"")
                    val json = """
                        {
                            "text": "$escapedMessage",
                            "message": "$escapedMessage",
                            "employeeName": "$employeeName",
                            "time": "$timeStr",
                            "status": "$status",
                            "activity": "$activity"
                        }
                    """.trimIndent()
                    
                    conn.outputStream.use { os ->
                        val input = json.toByteArray(Charsets.UTF_8)
                        os.write(input, 0, input.size)
                    }
                    
                    val responseCode = conn.responseCode
                    android.util.Log.d("StockViewModel", "WhatsApp webhook background push completed with code: $responseCode")
                    conn.disconnect()
                } catch (e: Exception) {
                    android.util.Log.e("StockViewModel", "WhatsApp Webhook background push error", e)
                }
            }
        }
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
        val raw = prefs.getString("biometric_username", null) ?: return null
        return com.example.util.AppUtils.decrypt(raw)
    }

    fun registerBiometrics(context: Context, username: String) {
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        val encrypted = com.example.util.AppUtils.encrypt(username)
        prefs.edit().putString("biometric_username", encrypted).apply()
    }

    fun saveLoggedInSession(context: Context, user: User) {
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        val encryptedUser = com.example.util.AppUtils.encrypt(user.username)
        val encryptedRole = com.example.util.AppUtils.encrypt(user.role)
        prefs.edit()
            .putString("session_username", encryptedUser)
            .putString("session_role", encryptedRole)
            .apply()
    }

    fun checkAutoLogin(context: Context) {
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        val encryptedUser = prefs.getString("session_username", null)
        val encryptedRole = prefs.getString("session_role", null)
        if (!encryptedUser.isNullOrEmpty() && !encryptedRole.isNullOrEmpty()) {
            val username = com.example.util.AppUtils.decrypt(encryptedUser)
            val role = com.example.util.AppUtils.decrypt(encryptedRole)
            if (username.isNotEmpty()) {
                val user = User(username, "", role)
                _loggedInUser.value = user
                _isLoggedIn.value = true
                triggerCloudSync()
            }
        }
    }

    fun completeLogin(user: User, context: Context? = null) {
        _isLoggedIn.value = true
        _loggedInUser.value = user
        _loginError.value = null
        _activeTab.value = 0
        if (context != null) {
            saveLoggedInSession(context, user)
        }
        triggerCloudSync()
    }

    fun changeUserPassword(username: String, newPass: String) {
        viewModelScope.launch {
            val user = repository.getUserByUsername(username)
            if (user != null) {
                val hashed = com.example.util.AppUtils.hashPassword(newPass)
                val updated = user.copy(passwordHash = hashed)
                repository.updateUser(updated)
            }
        }
    }

    fun resetAdminPasswordToDefault() {
        viewModelScope.launch {
            val hashed = com.example.util.AppUtils.hashPassword("admin")
            val adminUser = User("admin", hashed, "Admin")
            repository.insertUser(adminUser)
        }
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
    var descriptionInput = MutableStateFlow("BH - \nSale price - \nCondition - ")
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
        descriptionInput.value = item.description.ifBlank { "BH - \nSale price - \nCondition - " }
        quantityInput.value = 1
        photoUriInput.value = null
        transactionSubItems.value = listOf(TransactionSubItem(serialNumber = item.serialNumber, amount = item.amount.toInt().toString()))
        syncAggregatedFormState()
        clearFormErrorAndSuccess()
    }

    // --- Search autofit tracking (Rule 7 logic & reactivity) ---
    init {
        // Run background migration to identify legacy large Base64 photos in DB and upload them to Cloud Storage automatically
        viewModelScope.launch {
            try {
                com.example.util.AppUtils.migrateExistingDbBase64Photos(repository)
            } catch (e: Exception) {
                // Safe migration catch
            }
        }

        // Seed initial admin user eagerly so biometric and standard logins are fully functional instantly
        viewModelScope.launch {
            try {
                val count = repository.getUserCount()
                if (count == 0) {
                    val hashedAdmin = com.example.util.AppUtils.hashPassword("admin")
                    repository.insertUser(User("admin", hashedAdmin, "Admin"))
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
                            if (selection == 2) { // RETURN Category
                                // Try finding last sales transaction for the targeted IMEI first
                                val lastSale = repository.getLastSaleEventBySerialNumber(trimmedSn)
                                if (lastSale != null) {
                                    modelInput.value = lastSale.model
                                    nameInput.value = lastSale.name
                                    phoneInput.value = lastSale.phoneNumber ?: ""
                                    aadhaarInput.value = lastSale.aadhaarNumber ?: ""
                                    amountInput.value = lastSale.amount.toString()
                                    descriptionInput.value = lastSale.description.ifBlank { "BH - \nSale price - \nCondition - " }
                                } else {
                                    // Fallback to active inventory matching
                                    val matchingItem = repository.getItemBySerialNumber(trimmedSn)
                                    if (matchingItem != null) {
                                        modelInput.value = matchingItem.model
                                        nameInput.value = matchingItem.name
                                        phoneInput.value = matchingItem.phoneNumber ?: ""
                                        aadhaarInput.value = matchingItem.aadhaarNumber ?: ""
                                        amountInput.value = matchingItem.amount.toString()
                                        descriptionInput.value = matchingItem.description.ifBlank { "BH - \nSale price - \nCondition - " }
                                    }
                                }
                            } else {
                                // Other categories like SALE, etc. use active stock item
                                val matchingItem = repository.getItemBySerialNumber(trimmedSn)
                                if (matchingItem != null) {
                                    if (selection == 1) { // SALE Category
                                        // Rule 7: Model number should autofill, but other fields should be empty.
                                        modelInput.value = matchingItem.model
                                        nameInput.value = ""
                                        phoneInput.value = ""
                                        aadhaarInput.value = ""
                                        amountInput.value = ""
                                        descriptionInput.value = "BH - \nSale price - \nCondition - "
                                    }
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
        
        // Automatic background party selection on name typing is disabled to allow users to type freely.
        // It should only be selected when explicitly chosen from the dropdown list or selection dialog.
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
            val username = usernameStr.trim()
            try {
                // Safeguard against offline context or permission blocks by wrapping fetch
                val user = try {
                    repository.getUserByUsername(username)
                } catch (e: Exception) {
                    Log.e("StockViewModel", "Firebase user fetch error, falling back locally: ${e.message}")
                    null
                }
                
                if (user != null) {
                    val hashedInput = com.example.util.AppUtils.hashPassword(passwordStr)
                    val md5Input = com.example.util.AppUtils.md5(passwordStr)
                    
                    if (user.passwordHash == hashedInput || user.passwordHash == md5Input || user.passwordHash == passwordStr) {
                        // Password matches! Migrate/Upgrade legacy password to secure SHA-256 hash automatically
                        var finalUser = user
                        if (user.passwordHash != hashedInput) {
                            finalUser = user.copy(passwordHash = hashedInput)
                            try {
                                repository.updateUser(finalUser)
                            } catch (e: Exception) {
                                Log.w("StockViewModel", "Could not upgrade password hash on cloud: ${e.message}")
                            }
                        }
                        
                        val registeredUser = getBiometricRegisteredUser(context)
                        if (registeredUser == username) {
                            completeLogin(finalUser, context)
                        } else {
                            // First time login - prompt to enable biometric login
                            tempPendingUser.value = finalUser
                            showBiometricLinkingDialog.value = true
                        }
                    } else {
                        _loginError.value = "Invalid username or password."
                    }
                } else {
                    // Try Failsafe local admin login if username is admin and password is admin
                    if (username == "admin" && passwordStr == "admin") {
                        val secureAdminHash = com.example.util.AppUtils.hashPassword("admin")
                        val adminUser = User("admin", secureAdminHash, "Admin")
                        
                        try {
                            repository.insertUser(adminUser)
                        } catch (e: Exception) {
                            Log.e("StockViewModel", "Could not write seeded admin to Firebase. Proceeding with Local Failsafe session: ${e.message}")
                            android.widget.Toast.makeText(context, "Local Failsafe Mode Active (Offline/Restricted cloud permissions)", android.widget.Toast.LENGTH_LONG).show()
                        }
                        
                        val registeredUser = getBiometricRegisteredUser(context)
                        if (registeredUser == "admin") {
                            completeLogin(adminUser, context)
                        } else {
                            tempPendingUser.value = adminUser
                            showBiometricLinkingDialog.value = true
                        }
                    } else {
                        _loginError.value = "User not found or invalid credentials."
                    }
                }
            } catch (e: Exception) {
                // If it's the admin user, allow a local fallback session to prevent locking out the tester
                if (username == "admin" && passwordStr == "admin") {
                    val secureAdminHash = com.example.util.AppUtils.hashPassword("admin")
                    val adminUser = User("admin", secureAdminHash, "Admin")
                    android.widget.Toast.makeText(context, "Logged in via Failsafe Local Admin", android.widget.Toast.LENGTH_LONG).show()
                    completeLogin(adminUser, context)
                } else {
                    _loginError.value = "Sign-in error: ${e.message}"
                }
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
                        saveLoggedInSession(context, user)
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

    fun logout(context: Context? = null) {
        _isLoggedIn.value = false
        _loggedInUser.value = null
        _activeTab.value = 0
        if (context != null) {
            val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .remove("session_username")
                .remove("session_role")
                .apply()
        }
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

    fun markCheckIn(context: Context, selfieBase64: String, location: String, targetUserId: String? = null) {
        val currentUsername = targetUserId ?: _loggedInUser.value?.username ?: return
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val todayStr = sdf.format(java.util.Date(now))

                val record = AttendanceRecord(
                    id = java.util.UUID.randomUUID().toString(),
                    userId = currentUsername,
                    userName = currentUsername,
                    dateString = todayStr,
                    checkInTime = now,
                    checkInSelfieBase64 = selfieBase64,
                    checkInLocationSpec = location,
                    status = "Present"
                )
                repository.insertAttendanceRecord(record)

                val message = "Employee ${currentUsername} checked in at ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(now))} from $location."
                val notification = NotificationLog(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "New Check-In!",
                    message = message,
                    timestamp = now,
                    type = "CHECK_IN"
                )
                repository.insertNotification(notification)
                triggerWhatsAppMessage(
                    context = context,
                    employeeName = currentUsername,
                    timeStr = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(now)),
                    status = "Present",
                    activity = "Check In"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun markCheckOut(context: Context, selfieBase64: String, location: String, targetUserId: String? = null) {
        val currentUsername = targetUserId ?: _loggedInUser.value?.username ?: return
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val todayStr = sdf.format(java.util.Date(now))

                val existing = allAttendanceRecords.value.find { it.userId == currentUsername && it.dateString == todayStr }
                val updated = if (existing != null) {
                    existing.copy(
                        checkOutTime = now,
                        checkOutSelfieBase64 = selfieBase64,
                        checkOutLocationSpec = location
                    )
                } else {
                    AttendanceRecord(
                        id = java.util.UUID.randomUUID().toString(),
                        userId = currentUsername,
                        userName = currentUsername,
                        dateString = todayStr,
                        checkInTime = now - 3600000,
                        checkOutTime = now,
                        checkOutSelfieBase64 = selfieBase64,
                        checkOutLocationSpec = location,
                        status = "Present"
                    )
                }
                repository.insertAttendanceRecord(updated)

                val message = "Employee ${currentUsername} checked out at ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(now))}."
                val notification = NotificationLog(
                    id = java.util.UUID.randomUUID().toString(),
                    title = "New Check-Out!",
                    message = message,
                    timestamp = now,
                    type = "CHECK_OUT"
                )
                repository.insertNotification(notification)
                triggerWhatsAppMessage(
                    context = context,
                    employeeName = currentUsername,
                    timeStr = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(now)),
                    status = "Present",
                    activity = "Check Out"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun applyForLeave(startDate: String, endDate: String, type: String, reason: String) {
        val currentUser = _loggedInUser.value ?: return
        val hasOverlap = allLeaveApplications.value.any { leave ->
            leave.userId == currentUser.username &&
            (leave.status == "Approved" || leave.status == "Pending") &&
            startDate <= leave.endDateString &&
            leave.startDateString <= endDate
        }
        if (hasOverlap) {
            return
        }
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
            } catch (e: java.lang.Exception) {
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
            val hashed = com.example.util.AppUtils.hashPassword(passwordHash)
            repository.insertUser(User(username, hashed, role))
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
                    descriptionInput.value = matchingItem.description.ifBlank { "BH - \nSale price - \nCondition - " }
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
        descriptionInput.value = "BH - \nSale price - \nCondition - "
        dateInMillisInput.value = System.currentTimeMillis()
        quantityInput.value = 1
        photoUriInput.value = null
        technicianNameInput.value = ""
        repairReasonInput.value = ""
        _transactionError.value = null
    }

    fun addSubItem() {
        quantityInput.value += 1
        transactionSubItems.value = transactionSubItems.value + TransactionSubItem()
        syncAggregatedFormState()
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
        _transactionError.value = null
        _transactionSuccessMessage.value = null
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
        if (model.isBlank() || name.isBlank() || address.isBlank()) {
            _transactionError.value = "Model, Name, and Address fields are mandatory."
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

        // Phone - mandatory valid 10-digit number starting with 6-9
        if (phone.isBlank()) {
            _transactionError.value = "Phone number is mandatory."
            return
        }
        if (!phone.matches(Regex("^[6-9]\\d{9}$"))) {
            _transactionError.value = "Phone number must be a valid 10-digit number starting with 6-9."
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
                // Process and upload transaction-level photos exactly once before loop
                val uploadedPhoto = com.example.util.AppUtils.processAndUploadPhotos(photo)

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
                                    photoUri = uploadedPhoto,
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
                                    photoUri = uploadedPhoto,
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
                                    photoUri = uploadedPhoto,
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
                                    photoUri = uploadedPhoto,
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

    fun addParty(name: String, phone: String, aadhaar: String, address: String = "") {
        viewModelScope.launch {
            try {
                val party = com.example.data.model.Party(name = name.trim(), phoneNumber = phone.trim(), aadhaarNumber = aadhaar.trim(), address = address.trim())
                repository.addParty(party)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun editParty(partyId: String, name: String, phone: String, aadhaar: String, address: String = "") {
        viewModelScope.launch {
            try {
                repository.editParty(partyId, name.trim(), phone.trim(), aadhaar.trim(), address.trim())
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
            if (_loggedInUser.value?.role != "Admin") {
                Log.e("StockViewModel", "Unauthorized ledger payment attempt blocked.")
                return@launch
            }
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
                val existingParties = allParties.value
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

    // --- Brand Stock Module Extension ---
    val brandStockItems: StateFlow<List<com.example.data.model.BrandStockItem>> = repository.allBrandStockItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val brandStockTransactions: StateFlow<List<com.example.data.model.BrandStockTransaction>> = repository.allBrandStockTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addBrandStockItem(
        brand: String,
        variant: String,
        color: String,
        imei: String,
        warehouse: String,
        date: Long,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val creator = loggedInUser.value?.username ?: "Unknown"
            val item = com.example.data.model.BrandStockItem(
                imei = imei.trim(),
                brand = brand,
                variant = variant.trim(),
                color = color.trim(),
                warehouse = warehouse,
                addedByUser = creator,
                addedDate = date,
                lastUpdated = System.currentTimeMillis()
            )
            val tx = com.example.data.model.BrandStockTransaction(
                imei = imei.trim(),
                brand = brand,
                variant = variant.trim(),
                color = color.trim(),
                warehouse = warehouse,
                type = "IN",
                dateInMillis = date,
                operator = creator,
                notes = "Initial purchase intake"
            )
            val success = repository.addBrandStock(item, tx)
            onResult(success)
        }
    }

    fun sellBrandStockItem(
        imei: String,
        warehouse: String,
        date: Long,
        notes: String? = null,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val seller = loggedInUser.value?.username ?: "Unknown"
            val success = repository.sellBrandStock(imei.trim(), warehouse, seller, date, notes)
            onResult(success)
        }
    }

    suspend fun findBrandStockItemByImei(imei: String): com.example.data.model.BrandStockItem? {
        return repository.getBrandStockItemByImei(imei)
    }

    // Standard pre-defined item variants
    val brandVariants: StateFlow<List<com.example.data.model.BrandVariant>> = repository.allBrandVariants
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addBrandVariant(
        brand: String,
        modelName: String,
        specs: String,
        color: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val variant = com.example.data.model.BrandVariant(
                brand = brand,
                modelName = modelName.trim(),
                specs = specs.trim(),
                color = color.trim()
            )
            val success = repository.addBrandVariant(variant)
            onResult(success)
        }
    }

    fun deleteBrandVariant(id: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.deleteBrandVariant(id)
            onResult(success)
        }
    }

    fun deleteBrandStockItem(id: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.deleteBrandStockItem(id)
            onResult(success)
        }
    }

    fun deleteBrandTransaction(id: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.deleteBrandTransaction(id)
            onResult(success)
        }
    }

    // --- Continuous Continuous Multi-item Stock Actions ---
    fun addMultipleBrandStockItems(
        brand: String,
        variant: String,
        color: String,
        imeis: List<String>,
        warehouse: String,
        date: Long,
        onResult: (successCount: Int, failedImeis: List<String>) -> Unit
    ) {
        viewModelScope.launch {
            val creator = loggedInUser.value?.username ?: "Unknown"
            var successCount = 0
            val failedImeis = mutableListOf<String>()

            for (rawImei in imeis) {
                val imei = rawImei.trim()
                if (imei.isBlank()) continue

                val item = com.example.data.model.BrandStockItem(
                    imei = imei,
                    brand = brand,
                    variant = variant.trim(),
                    color = color.trim(),
                    warehouse = warehouse,
                    addedByUser = creator,
                    addedDate = date,
                    lastUpdated = System.currentTimeMillis()
                )
                val tx = com.example.data.model.BrandStockTransaction(
                    imei = imei,
                    brand = brand,
                    variant = variant.trim(),
                    color = color.trim(),
                    warehouse = warehouse,
                    type = "IN",
                    dateInMillis = date,
                    operator = creator,
                    notes = "Purchase intake (Bulk)"
                )

                // Check duplicate IMEI first to prevent duplicate active items
                val existing = repository.getBrandStockItemByImei(imei)
                if (existing != null) {
                    failedImeis.add(imei)
                    continue
                }

                val success = repository.addBrandStock(item, tx)
                if (success) {
                    successCount++
                } else {
                    failedImeis.add(imei)
                }
            }
            onResult(successCount, failedImeis)
        }
    }

    fun addMultipleBrandStockItemsWithColors(
        brand: String,
        variant: String,
        itemsWithColors: List<Pair<String, String>>, // Pair of (IMEI, Color)
        warehouse: String,
        date: Long,
        onResult: (successCount: Int, failedImeis: List<String>) -> Unit
    ) {
        viewModelScope.launch {
            val creator = loggedInUser.value?.username ?: "Unknown"
            var successCount = 0
            val failedImeis = mutableListOf<String>()

            for (p in itemsWithColors) {
                val imei = p.first.trim()
                val color = p.second.trim()
                if (imei.isBlank()) continue

                val item = com.example.data.model.BrandStockItem(
                    imei = imei,
                    brand = brand,
                    variant = variant.trim(),
                    color = color.ifBlank { "Unknown" },
                    warehouse = warehouse,
                    addedByUser = creator,
                    addedDate = date,
                    lastUpdated = System.currentTimeMillis()
                )
                val tx = com.example.data.model.BrandStockTransaction(
                    imei = imei,
                    brand = brand,
                    variant = variant.trim(),
                    color = color.ifBlank { "Unknown" },
                    warehouse = warehouse,
                    type = "IN",
                    dateInMillis = date,
                    operator = creator,
                    notes = "Purchase intake (Bulk colored)"
                )

                // Check duplicate IMEI first to prevent duplicate active items
                val existing = repository.getBrandStockItemByImei(imei)
                if (existing != null) {
                    failedImeis.add(imei)
                    continue
                }

                val success = repository.addBrandStock(item, tx)
                if (success) {
                    successCount++
                } else {
                    failedImeis.add(imei)
                }
            }
            onResult(successCount, failedImeis)
        }
    }

    fun sellMultipleBrandStockItems(
        imeis: List<String>,
        warehouse: String,
        date: Long,
        notes: String? = null,
        onResult: (successCount: Int, failedImeis: List<String>) -> Unit
    ) {
        viewModelScope.launch {
            val seller = loggedInUser.value?.username ?: "Unknown"
            var successCount = 0
            val failedImeis = mutableListOf<String>()

            for (rawImei in imeis) {
                val imei = rawImei.trim()
                if (imei.isBlank()) continue

                val success = repository.sellBrandStock(imei, warehouse, seller, date, notes)
                if (success) {
                    successCount++
                } else {
                    failedImeis.add(imei)
                }
            }
            onResult(successCount, failedImeis)
        }
    }

    // --- CSV File Stock Import Parser ---
    fun importBrandStockCsv(
        context: Context,
        uri: android.net.Uri,
        onResult: (successCount: Int, duplicatedCount: Int, errorCount: Int, message: String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    onResult(0, 0, 1, "Failed to open selected file. Please make sure the app has read permissions.")
                    return@launch
                }
                val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                val lines = mutableListOf<String>()
                var line: String? = reader.readLine()
                while (line != null) {
                    if (line.isNotBlank()) {
                        lines.add(line)
                    }
                    line = reader.readLine()
                }
                inputStream.close()

                if (lines.isEmpty()) {
                    onResult(0, 0, 0, "Selected file is completely empty.")
                    return@launch
                }

                // Identify headers and delimiter from the first line
                val firstLine = lines[0]
                val delimiter = if (firstLine.contains(";")) ";" else ","
                
                // Safe CSV parsing of header line
                val rawHeaders = parseCsvLine(firstLine, delimiter).map { it.lowercase() }
                
                // Detect if first line contains common keywords as a header
                val hasHeader = rawHeaders.any { 
                    it == "imei" || it == "brand" || it == "variant" || it == "model" || it == "color" || it == "warehouse" || it == "specs" 
                }

                var startIdx = 0
                var imeiIdx = 0
                var brandIdx = 1
                var modelIdx = 2
                var specsIdx = 3
                var colorIdx = 4
                var whIdx = 5

                if (hasHeader) {
                    startIdx = 1
                    imeiIdx = rawHeaders.indexOfFirst { it.contains("imei") || it.contains("serial") || it.contains("id") || it.contains("code") }.let { if (it == -1) 0 else it }
                    brandIdx = rawHeaders.indexOfFirst { it.contains("brand") || it.contains("make") || it.contains("company") }.let { if (it == -1) 1 else it }
                    modelIdx = rawHeaders.indexOfFirst { it.contains("model") || it.contains("variant") || it.contains("product") || it == "name" }.let { if (it == -1) 2 else it }
                    specsIdx = rawHeaders.indexOfFirst { it.contains("spec") || it.contains("ram") || it.contains("storage") || it.contains("size") }.let { if (it == -1) 3 else it }
                    colorIdx = rawHeaders.indexOfFirst { it.contains("color") || it.contains("colour") || it.contains("shade") }.let { if (it == -1) 4 else it }
                    whIdx = rawHeaders.indexOfFirst { it.contains("warehouse") || it.contains("wh") || it.contains("loc") }.let { if (it == -1) 5 else it }
                }

                var success = 0
                var dupCount = 0
                var errCount = 0

                val currentVariants = brandVariants.value
                val creator = loggedInUser.value?.username ?: "CSV Bulk Import"

                for (i in startIdx until lines.size) {
                    val row = lines[i]
                    val cols = parseCsvLine(row, delimiter)
                    if (cols.isEmpty()) {
                        errCount++
                        continue
                    }

                    fun getCol(idx: Int, default: String) = if (idx >= 0 && idx < cols.size) cols[idx] else default

                    val rawImei = getCol(imeiIdx, "")
                    if (rawImei.isBlank()) {
                        errCount++
                        continue
                    }

                    val brand = getCol(brandIdx, "Generic").ifBlank { "Generic" }
                    val rawModel = getCol(modelIdx, "Smartphone").ifBlank { "Smartphone" }
                    val specs = getCol(specsIdx, "Base").ifBlank { "Base" }
                    val color = getCol(colorIdx, "Black").ifBlank { "Black" }
                    val wh = getCol(whIdx, "G").let { 
                        if (it.uppercase().startsWith("O") || it.uppercase() == "O") "O" else "G"
                    }

                    // Auto-seed or verify Brand Variant model definitions inside Inventory items catalog
                    val fullVariantName = "${rawModel} ${specs}".trim()
                    val matchedPreset = currentVariants.find { 
                        it.brand.equals(brand, ignoreCase = true) && 
                        (it.modelName.equals(rawModel, ignoreCase = true) || 
                         "${it.modelName} ${it.specs}".trim().equals(fullVariantName, ignoreCase = true)) 
                    }

                    if (matchedPreset == null) {
                        val newPreset = com.example.data.model.BrandVariant(
                            brand = brand,
                            modelName = rawModel,
                            specs = specs,
                            color = color
                        )
                        try {
                            repository.addBrandVariant(newPreset)
                        } catch (e: Exception) {
                            Log.e("StockViewModel", "Could not seed brand variant: ${e.message}")
                        }
                    }

                    // Strict unique IMEI verification
                    val existingItem = repository.getBrandStockItemByImei(rawImei)
                    if (existingItem != null) {
                        dupCount++
                        continue
                    }

                    val stockItem = com.example.data.model.BrandStockItem(
                        imei = rawImei,
                        brand = brand,
                        variant = fullVariantName,
                        color = color,
                        warehouse = wh,
                        addedByUser = creator,
                        addedDate = System.currentTimeMillis(),
                        lastUpdated = System.currentTimeMillis()
                    )

                    val stockTx = com.example.data.model.BrandStockTransaction(
                        imei = rawImei,
                        brand = brand,
                        variant = fullVariantName,
                        color = color,
                        warehouse = wh,
                        type = "IN",
                        dateInMillis = System.currentTimeMillis(),
                        operator = creator,
                        notes = "Imported via CSV Stock Sheet"
                    )

                    val result = repository.addBrandStock(stockItem, stockTx)
                    if (result) {
                        success++
                    } else {
                        dupCount++
                    }
                }

                onResult(
                    success, 
                    dupCount, 
                    errCount, 
                    "Catalog synced completely! Successfully imported $success stock records. Ignored $dupCount duplicates, skipped $errCount faulty rows."
                )
            } catch (e: Exception) {
                onResult(0, 0, 1, "CSV Import Error: ${e.localizedMessage}")
            }
        }
    }

    private fun parseCsvLine(line: String, delimiter: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c.toString() == delimiter && !inQuotes) {
                result.add(current.toString().trim().replace(Regex("^\"|\"$"), ""))
                current = StringBuilder()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString().trim().replace(Regex("^\"|\"$"), ""))
        return result
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
