package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.components.SmartImeiScannerDialog
import com.example.ui.viewmodel.StockViewModel
import com.example.util.AppUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import coil.compose.AsyncImage
import coil.request.ImageRequest

val ColorsAmber = Color(0xFFF59E0B)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]
                val currentDescription = viewModel.descriptionInput.value
                val separator = if (currentDescription.isNotBlank()) " " else ""
                viewModel.descriptionInput.value = currentDescription + separator + spokenText
            }
        }
    }

    val speechLauncherAddress = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]
                val currentAddress = viewModel.addressInput.value
                val separator = if (currentAddress.isNotBlank()) " " else ""
                viewModel.addressInput.value = currentAddress + separator + spokenText
            }
        }
    }

    // Real Camera and Gallery integration launchers
    val tempCameraUriStringState = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var isProcessingPhotos by remember { mutableStateOf(false) }

    fun handleSelectedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val currentUris = viewModel.photoUriInput.value
        val urisArray = if (currentUris.isNullOrBlank()) emptyList() else currentUris.split(",")
        val remainingSlots = (10 - urisArray.size).coerceAtLeast(0)
        if (remainingSlots <= 0) {
            Toast.makeText(context, "Maximum 10 photos allowed", Toast.LENGTH_SHORT).show()
            return
        }
        val acceptedUris = if (uris.size > remainingSlots) {
            Toast.makeText(
                context,
                "Maximum 10 photos allowed. Only first $remainingSlots attached.",
                Toast.LENGTH_SHORT
            ).show()
            uris.take(remainingSlots)
        } else {
            uris
        }

        if (acceptedUris.isNotEmpty()) {
            isProcessingPhotos = true
            coroutineScope.launch {
                try {
                    val newUrisStr = withContext(Dispatchers.IO) {
                        acceptedUris.mapNotNull { uri ->
                            try {
                                com.example.util.AppUtils.uriToHighResLocalFile(context, uri)
                            } catch (t: Throwable) {
                                t.printStackTrace()
                                null
                            }
                        }
                    }
                    if (newUrisStr.isNotEmpty()) {
                        viewModel.photoUriInput.value = (urisArray + newUrisStr).joinToString(",")
                        Toast.makeText(context, "Photo(s) attached successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to process photo files.", Toast.LENGTH_SHORT).show()
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                    Toast.makeText(context, "Error processing photos: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessingPhotos = false
                }
            }
        }
    }

    // Modern Zero-Permission Photo Picker (Android 13+ & Google Play services backport)
    val visualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris ->
        try {
            handleSelectedUris(uris)
        } catch (t: Throwable) {
            t.printStackTrace()
            Toast.makeText(context, "Gallery selection error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        try {
            handleSelectedUris(uris)
        } catch (t: Throwable) {
            t.printStackTrace()
            Toast.makeText(context, "Gallery selection error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun createTempImageUri(): Uri? {
        return try {
            val directory = File(context.cacheDir, "camera_photos")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val tempFile = File.createTempFile("photo_${System.currentTimeMillis()}", ".jpg", directory)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        try {
            if (success) {
                tempCameraUriStringState.value?.let { uriStr ->
                    val uri = Uri.parse(uriStr)
                    val currentUris = viewModel.photoUriInput.value
                    val urisArray = if (currentUris.isNullOrBlank()) emptyList() else currentUris.split(",")
                    if (urisArray.size < 10) {
                        isProcessingPhotos = true
                        coroutineScope.launch {
                            try {
                                val localUri = withContext(Dispatchers.IO) {
                                    com.example.util.AppUtils.uriToHighResLocalFile(context, uri)
                                }
                                if (localUri != null) {
                                    viewModel.photoUriInput.value = (urisArray + localUri).joinToString(",")
                                    Toast.makeText(context, "Camera Snapshot Attached!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed to save snapshot file.", Toast.LENGTH_SHORT).show()
                                }
                            } catch (t: Throwable) {
                                t.printStackTrace()
                                Toast.makeText(context, "Failed to process snapshot: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isProcessingPhotos = false
                            }
                        }
                    } else {
                        Toast.makeText(context, "Maximum 10 photos allowed", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "Camera capture cancelled or failed.", Toast.LENGTH_SHORT).show()
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            Toast.makeText(context, "Camera error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchCameraSafely(uri: Uri) {
        try {
            tempCameraUriStringState.value = uri.toString()
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri)
                clipData = android.content.ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                val resInfoList = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                for (resolveInfo in resInfoList) {
                    val pkg = resolveInfo.activityInfo?.packageName ?: continue
                    try {
                        context.grantUriPermission(pkg, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            cameraLauncher.launch(uri)
        } catch (t: Throwable) {
            t.printStackTrace()
            Toast.makeText(context, "No camera app found or camera launch failed: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        try {
            if (granted) {
                val uri = createTempImageUri()
                if (uri != null) {
                    launchCameraSafely(uri)
                } else {
                    Toast.makeText(context, "Could not create temporary file for picture.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Camera permission is required to take a transaction photo.", Toast.LENGTH_LONG).show()
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            Toast.makeText(context, "Camera permission error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // ViewModel form states
    val activeSelection by viewModel.transactionSelection.collectAsStateWithLifecycle() // 0: Purchase, 1: Sale, 2: Return, 3: Repair
    val isUploading by viewModel.isUploadingTransaction.collectAsStateWithLifecycle()
    val errMessage by viewModel.transactionError.collectAsStateWithLifecycle()
    val successMessage by viewModel.transactionSuccessMessage.collectAsStateWithLifecycle()
    val rawItems by viewModel.inventoryItems.collectAsStateWithLifecycle()
    val suggestedImeis = remember(rawItems) { rawItems.map { it.serialNumber } }

    val serialNumber by viewModel.serialNumberInput.collectAsStateWithLifecycle()
    val model by viewModel.modelInput.collectAsStateWithLifecycle()
    val name by viewModel.nameInput.collectAsStateWithLifecycle()
    val phone by viewModel.phoneInput.collectAsStateWithLifecycle()
    val aadhaar by viewModel.aadhaarInput.collectAsStateWithLifecycle()
    val amount by viewModel.amountInput.collectAsStateWithLifecycle()
    val address by viewModel.addressInput.collectAsStateWithLifecycle()
    val description by viewModel.descriptionInput.collectAsStateWithLifecycle()
    val dateInMillis by viewModel.dateInMillisInput.collectAsStateWithLifecycle()
    val quantity by viewModel.quantityInput.collectAsStateWithLifecycle()
    val photoUri by viewModel.photoUriInput.collectAsStateWithLifecycle()
    val allParties by viewModel.allParties.collectAsStateWithLifecycle()
    val combinedParties = allParties
    var nameFocused by remember { mutableStateOf(false) }
    var showPartySelectionDialog by remember { mutableStateOf(false) }
    var partySearchQuery by remember { mutableStateOf("") }

    val technician by viewModel.technicianNameInput.collectAsStateWithLifecycle()
    val repairReason by viewModel.repairReasonInput.collectAsStateWithLifecycle()

    var showPhotoChooserDialog by remember { mutableStateOf(false) }
    var viewingPhotoUri by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var scannerIndex by remember { mutableStateOf<Int?>(null) }
    val transactionSubItems by viewModel.transactionSubItems.collectAsStateWithLifecycle()

    var showImeiConfirmDialog by remember { mutableStateOf(false) }
    var pendingImeisToConfirm by remember { mutableStateOf<List<String>>(emptyList()) }
    var onConfirmProceedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Date formatting helper
    val formattedDate = remember(dateInMillis) {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        sdf.format(Date(dateInMillis))
    }

    val canManageInventory by viewModel.canManageInventory.collectAsStateWithLifecycle()
    val canSell by viewModel.canSell.collectAsStateWithLifecycle()
    val canRepair by viewModel.canRepair.collectAsStateWithLifecycle()
    
    val isActionAllowed = when (activeSelection) {
        0 -> canManageInventory
        1 -> canSell
        2 -> canManageInventory || canSell
        3 -> canRepair
        else -> true
    }

    // Focus requesters to smoothly traverse form inputs on Enter / IME Next
    val modelFocusRequester = remember { FocusRequester() }
    val nameFocusRequester = remember { FocusRequester() }
    val phoneFocusRequester = remember { FocusRequester() }
    val aadhaarFocusRequester = remember { FocusRequester() }
    val techFocusRequester = remember { FocusRequester() }
    val repairReasonFocusRequester = remember { FocusRequester() }
    val addressFocusRequester = remember { FocusRequester() }
    val descriptionFocusRequester = remember { FocusRequester() }
    val imeiFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val priceFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }

    fun safeRequestFocus(requester: FocusRequester) {
        try {
            requester.requestFocus()
        } catch (_: Exception) {
            focusManager.moveFocus(FocusDirection.Next)
        }
    }

    // Real-time validation touched trackers
    val imeiTouched = remember { mutableStateMapOf<Int, Boolean>() }
    val priceTouched = remember { mutableStateMapOf<Int, Boolean>() }
    val salePriceTouched = remember { mutableStateMapOf<Int, Boolean>() }
    val modelTouched = remember { mutableStateOf(false) }
    val nameTouched = remember { mutableStateOf(false) }
    val phoneTouched = remember { mutableStateOf(false) }
    val aadhaarTouched = remember { mutableStateOf(false) }
    val techTouched = remember { mutableStateOf(false) }
    val repairReasonTouched = remember { mutableStateOf(false) }
    val addressTouched = remember { mutableStateOf(false) }

    // Aggregate error calculation helpers
    val inputImeis = remember(transactionSubItems) { transactionSubItems.map { it.serialNumber.trim() } }
    val serialsHaveDuplicates = remember(inputImeis) { inputImeis.size != inputImeis.distinct().size }

    fun getImeiError(index: Int, valStr: String): String? {
        if (imeiTouched[index] != true) return null
        if (valStr.trim().isEmpty()) return "IMEI/Serial is mandatory"
        if (!valStr.trim().matches(Regex("^\\d{15}$"))) return "IMEI must be exactly 15 numeric digits"
        if (serialsHaveDuplicates && inputImeis.count { it == valStr.trim() } > 1) {
            return "Duplicate IMEI number in transaction"
        }
        return null
    }

    fun getPriceError(index: Int, valStr: String): String? {
        if (priceTouched[index] != true) return null
        if (valStr.trim().isEmpty()) return "Price is mandatory"
        val amt = valStr.trim().toDoubleOrNull()
        if (amt == null || amt < 0) return "Must be a non-negative number"
        return null
    }

    fun getSalePriceError(index: Int, valStr: String): String? {
        if (activeSelection != 0) return null
        if (salePriceTouched[index] != true) return null
        if (valStr.trim().isEmpty()) return null
        val amt = valStr.trim().toDoubleOrNull()
        if (amt == null || amt <= 0) return "Must be a positive number"
        return null
    }

    val ignoreFieldError = successMessage != null || isUploading
    val modelError = if (!ignoreFieldError && modelTouched.value && model.isBlank()) "Model is a mandatory field" else null
    val nameError = if (!ignoreFieldError && nameTouched.value && name.isBlank()) "Name is a mandatory field" else null
    val phoneError = if (!ignoreFieldError && phoneTouched.value) {
        if (phone.isBlank()) "Phone number is a mandatory field"
        else if (!phone.matches(Regex("^[6-9]\\d{9}$"))) "Must start with 6-9 and be 10 digits"
        else null
    } else null
    val aadhaarError = if (!ignoreFieldError && aadhaarTouched.value && aadhaar.isNotEmpty() && !aadhaar.matches(Regex("^\\d{12}$"))) "Must be exactly 12 numeric digits" else null
    val techError = if (!ignoreFieldError && activeSelection == 3 && techTouched.value && technician.isBlank()) "Technician Assigned is mandatory" else null
    val repairReasonError = if (!ignoreFieldError && activeSelection == 3 && repairReasonTouched.value && repairReason.isBlank()) "Reason for Issue is mandatory" else null
    val addressError = if (!ignoreFieldError && addressTouched.value && address.isBlank()) "Address is a mandatory field" else null

    fun getRealtimeError(): String? {
        if (successMessage != null || isUploading) return null
        if (model.isBlank() && modelTouched.value) return "Model is mandatory"
        if (name.isBlank() && nameTouched.value) return "Name is mandatory"
        if (phone.isBlank() && phoneTouched.value) return "Phone number is mandatory"
        if (address.isBlank() && addressTouched.value) return "Address is mandatory"
        if (activeSelection == 3) {
            if (technician.isBlank() && techTouched.value) return "Technician Assigned is mandatory"
            if (repairReason.isBlank() && repairReasonTouched.value) return "Reason for Issue is mandatory"
        }
        for (idx in transactionSubItems.indices) {
            val sErr = getImeiError(idx, transactionSubItems[idx].serialNumber)
            if (sErr != null) return sErr
            val pErr = getPriceError(idx, transactionSubItems[idx].amount)
            if (pErr != null) return pErr
            val spErr = getSalePriceError(idx, transactionSubItems[idx].salePrice)
            if (spErr != null) return spErr
        }
        if (serialsHaveDuplicates) return "Duplicate IMEI numbers are not allowed"
        if (phoneError != null) return phoneError
        if (aadhaarError != null) return aadhaarError
        
        val now = System.currentTimeMillis()
        if (dateInMillis > now + 60_000) return "Selecting future dates is not allowed"
        
        return errMessage
    }

    val triggerAllTouched = {
        modelTouched.value = true
        nameTouched.value = true
        addressTouched.value = true
        if (activeSelection == 3) {
            techTouched.value = true
            repairReasonTouched.value = true
        }
        transactionSubItems.forEachIndexed { index, _ ->
            imeiTouched[index] = true
            priceTouched[index] = true
            salePriceTouched[index] = true
        }
        phoneTouched.value = true
        aadhaarTouched.value = true
    }

    val resetAllTouched = {
        modelTouched.value = false
        nameTouched.value = false
        addressTouched.value = false
        phoneTouched.value = false
        aadhaarTouched.value = false
        techTouched.value = false
        repairReasonTouched.value = false
        imeiTouched.clear()
        priceTouched.clear()
    }

    LaunchedEffect(successMessage) {
        if (successMessage != null) {
            resetAllTouched()
            delay(4000L)
            viewModel.clearFormErrorAndSuccess()
        }
    }

    // Dynamic banner/style details based on tab modes
    val themeColorAndLabel = remember(activeSelection) {
        when (activeSelection) {
            0 -> Triple(Color(0xFF3B82F6), "INBOUND PURCHASE", Icons.Default.ShoppingCart)
            1 -> Triple(Color(0xFF10B981), "OUTBOUND SALE", Icons.Default.Sell)
            2 -> Triple(Color(0xFF9333EA), "PRODUCT RETURN", Icons.AutoMirrored.Filled.AssignmentReturn)
            else -> Triple(Color(0xFFEAB308), "REPAIR SUBMISSION", Icons.Default.Build)
        }
    }

    // Modern list of photo choices for simulated attachment (Creative and functional!)
    val mockPhotoPresets = listOf(
        Pair("Pixel Tech Box", "ic_phone_blue"),
        Pair("Pro Cam Module", "ic_phone_amber"),
        Pair("Smart Watch Module", "ic_watch"),
        Pair("Tablet Slate Module", "ic_tablet"),
        Pair("Network Modem Unit", "ic_router")
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Tab selectors at the top representing Modes
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            TabRow(
                selectedTabIndex = activeSelection,
                containerColor = Color.Transparent,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeSelection]),
                        color = themeColorAndLabel.first
                    )
                }
            ) {
                // Four Categories selectable
                val tabs = listOf("Purchase", "Sale", "Return", "Repair")
                tabs.forEachIndexed { index, text ->
                    Tab(
                        selected = activeSelection == index,
                        onClick = { viewModel.setTransactionSelection(index) },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            text = text,
                            fontWeight = FontWeight.Bold,
                            color = if (activeSelection == index) themeColorAndLabel.first else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }


        // Success message or error reporting block
        AnimatedVisibility(visible = successMessage != null) {
            successMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD1FAE5)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, "Success", tint = Color(0xFF10B981))
                        Text(msg, color = Color(0xFF065F46), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }



        // FORM FIELDS WRAPPER
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // TOP ROW: Date & Quantity
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Date Selector
                Card(
                    modifier = Modifier.weight(1f).clickable { showDatePicker = true },
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, "Date", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(formattedDate, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Quantity Selector
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { viewModel.removeSubItem() },
                            modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Default.Remove, "Minus Quantity", modifier = Modifier.size(16.dp)) }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Qty", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$quantity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        }

                        IconButton(
                            onClick = { viewModel.addSubItem() },
                            modifier = Modifier.size(32.dp).focusProperties { canFocus = false }
                        ) { Icon(Icons.Default.Add, "Add Quantity", modifier = Modifier.size(16.dp)) }
                    }
                }
            }

            // DYNAMIC ITEMS COLLECTION
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                transactionSubItems.forEachIndexed { index, subItem ->
                    val imeiErr = getImeiError(index, subItem.serialNumber)
                    val priceErr = getPriceError(index, subItem.amount)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Item ${index + 1}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            
                            OutlinedTextField(
                                value = subItem.serialNumber,
                                onValueChange = { 
                                    val clean = it.replace("\n", "").replace("\r", "")
                                    if (activeSelection == 1) {
                                        val matched = rawItems.find { item -> item.serialNumber.isNotBlank() && item.serialNumber.trim().equals(clean.trim(), ignoreCase = true) }
                                        if (matched != null) {
                                            val effSale = com.example.util.AppUtils.getEffectiveSalePrice(matched)
                                            val effMin = com.example.util.AppUtils.getEffectiveMinSalePrice(matched)
                                            if (viewModel.modelInput.value.isBlank()) {
                                                viewModel.modelInput.value = matched.model
                                            }
                                            val defaultAmt = if (subItem.amount.isBlank() && effSale > 0.0) effSale.toInt().toString() else subItem.amount
                                            viewModel.updateSubItem(index, clean, defaultAmt, sp = if (effSale > 0.0) effSale.toInt().toString() else "", msp = if (effMin > 0.0) effMin.toInt().toString() else "")
                                        } else {
                                            viewModel.updateSubItem(index, clean, subItem.amount)
                                        }
                                    } else {
                                        viewModel.updateSubItem(index, clean, subItem.amount)
                                    }
                                    viewModel.clearFormErrorAndSuccess()
                                    imeiTouched[index] = true
                                    if (it.contains("\n") || it.contains("\r")) {
                                        safeRequestFocus(priceFocusRequesters.getOrPut(index) { FocusRequester() })
                                    }
                                },
                                label = { Text("IMEI/Serial Number *") },
                                placeholder = { Text("Enter 15-digit IMEI") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(imeiFocusRequesters.getOrPut(index) { FocusRequester() })
                                    .onKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                            safeRequestFocus(priceFocusRequesters.getOrPut(index) { FocusRequester() })
                                            true
                                        } else false
                                    },
                                isError = imeiErr != null,
                                supportingText = if (imeiErr != null) { { Text(imeiErr, color = MaterialTheme.colorScheme.error) } } else null,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { safeRequestFocus(priceFocusRequesters.getOrPut(index) { FocusRequester() }) }
                                ),
                                trailingIcon = {
                                    val iconAlpha = if (subItem.serialNumber.isNotEmpty()) 0.4f else 1f
                                    IconButton(
                                        onClick = { scannerIndex = index },
                                        modifier = Modifier.alpha(iconAlpha).focusProperties { canFocus = false }
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, "Scanner", tint = themeColorAndLabel.first)
                                    }
                                }
                            )

                            val priceLabel = when (activeSelection) {
                                0 -> "Purchase Cost (₹) *"
                                1 -> "Closed Sale Price (₹) *"
                                2 -> "Refund Amount (₹) *"
                                3 -> "Repair Cost (₹) *"
                                else -> "Price (₹) *"
                            }
                            val pricePlaceholder = when (activeSelection) {
                                0 -> "Enter purchase cost"
                                1 -> "Enter actual price deal closed at"
                                2 -> "Enter refund amount"
                                3 -> "Enter repair cost"
                                else -> "Enter item cost"
                            }

                            OutlinedTextField(
                                value = subItem.amount,
                                onValueChange = { 
                                    val clean = it.replace("\n", "").replace("\r", "")
                                    viewModel.updateSubItem(index, subItem.serialNumber, clean) 
                                    viewModel.clearFormErrorAndSuccess()
                                    priceTouched[index] = true
                                    if (it.contains("\n") || it.contains("\r")) {
                                        if (index + 1 < transactionSubItems.size) {
                                            safeRequestFocus(imeiFocusRequesters.getOrPut(index + 1) { FocusRequester() })
                                        } else {
                                            safeRequestFocus(modelFocusRequester)
                                        }
                                    }
                                },
                                label = { Text(priceLabel) },
                                placeholder = { Text(pricePlaceholder) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(priceFocusRequesters.getOrPut(index) { FocusRequester() })
                                    .onKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                            if (index + 1 < transactionSubItems.size) {
                                                safeRequestFocus(imeiFocusRequesters.getOrPut(index + 1) { FocusRequester() })
                                            } else {
                                                safeRequestFocus(modelFocusRequester)
                                            }
                                            true
                                        } else false
                                    },
                                isError = priceErr != null,
                                supportingText = if (priceErr != null) { { Text(priceErr, color = MaterialTheme.colorScheme.error) } } else null,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { 
                                        if (index + 1 < transactionSubItems.size) {
                                            safeRequestFocus(imeiFocusRequesters.getOrPut(index + 1) { FocusRequester() })
                                        } else {
                                            safeRequestFocus(modelFocusRequester)
                                        }
                                    }
                                )
                            )

                            // Dedicated Selling Price Limits Section (Purchase Mode)
                            if (activeSelection == 0) {
                                val salePriceErr = getSalePriceError(index, subItem.salePrice)
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Sell,
                                                contentDescription = "Selling price configuration",
                                                tint = Color(0xFF15803D),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Selling Price Limits (For Inventory Cards & Salesman)",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF15803D)
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedTextField(
                                                value = subItem.salePrice,
                                                onValueChange = { clean ->
                                                    val cleanVal = clean.replace("\n", "").replace("\r", "")
                                                    viewModel.updateSubItem(index, subItem.serialNumber, subItem.amount, sp = cleanVal, msp = subItem.minSalePrice)
                                                    viewModel.clearFormErrorAndSuccess()
                                                    salePriceTouched[index] = true
                                                },
                                                label = { Text("Expected Sale Price (₹)") },
                                                placeholder = { Text("Target selling price (Optional)") },
                                                singleLine = true,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f),
                                                isError = salePriceErr != null,
                                                supportingText = if (salePriceErr != null) { { Text(salePriceErr, color = MaterialTheme.colorScheme.error) } } else { { Text("Optional", style = MaterialTheme.typography.labelSmall) } },
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Number,
                                                    imeAction = ImeAction.Next
                                                )
                                            )

                                            OutlinedTextField(
                                                value = subItem.minSalePrice,
                                                onValueChange = { clean ->
                                                    val cleanVal = clean.replace("\n", "").replace("\r", "")
                                                    viewModel.updateSubItem(index, subItem.serialNumber, subItem.amount, sp = subItem.salePrice, msp = cleanVal)
                                                    viewModel.clearFormErrorAndSuccess()
                                                },
                                                label = { Text("Min Sale Price (₹)") },
                                                placeholder = { Text("Min floor limit") },
                                                singleLine = true,
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.weight(1f),
                                                supportingText = { Text("Optional", style = MaterialTheme.typography.labelSmall) },
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Number,
                                                    imeAction = ImeAction.Next
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // Dedicated Sale Pricing Guidance (Sale Mode)
                            if (activeSelection == 1) {
                                val matchedItem = rawItems.find { it.serialNumber.isNotBlank() && it.serialNumber.trim().equals(subItem.serialNumber.trim(), ignoreCase = true) }
                                val effSale = if (matchedItem != null) com.example.util.AppUtils.getEffectiveSalePrice(matchedItem) else subItem.salePrice.toDoubleOrNull() ?: 0.0
                                val effMin = if (matchedItem != null) com.example.util.AppUtils.getEffectiveMinSalePrice(matchedItem) else subItem.minSalePrice.toDoubleOrNull() ?: 0.0
                                val enteredAmt = subItem.amount.toDoubleOrNull() ?: 0.0
                                val isBelowMin = effMin > 0.0 && enteredAmt > 0.0 && enteredAmt < effMin

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isBelowMin) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else Color(0xFFF0FDF4)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isBelowMin) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else Color(0xFF86EFAC))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(
                                                    imageVector = Icons.Default.Sell,
                                                    contentDescription = null,
                                                    tint = if (isBelowMin) MaterialTheme.colorScheme.error else Color(0xFF15803D),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Expected & Min Sale Price Options",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isBelowMin) MaterialTheme.colorScheme.error else Color(0xFF15803D)
                                                )
                                            }
                                            if (effSale > 0.0 && subItem.amount.isBlank()) {
                                                TextButton(
                                                    onClick = {
                                                        viewModel.updateSubItem(index, subItem.serialNumber, effSale.toInt().toString(), sp = effSale.toInt().toString(), msp = if (effMin > 0.0) effMin.toInt().toString() else "")
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text("Apply Expected", fontSize = 11.sp)
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Expected: ${if (effSale > 0.0) "₹" + String.format("%,.0f", effSale) else "Not configured"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF15803D)
                                            )
                                            Text(
                                                text = "Minimum Allowed: ${if (effMin > 0.0) "₹" + String.format("%,.0f", effMin) else "No floor limit"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB45309)
                                            )
                                        }

                                        if (isBelowMin) {
                                            Text(
                                                text = "⚠️ Warning: Deal price ₹${String.format("%,.0f", enteredAmt)} is below minimum allowed sale price (₹${String.format("%,.0f", effMin)})!",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick AutoFill Indicator for Sales/Returns
            if ((activeSelection == 1 || activeSelection == 2) && transactionSubItems.any { it.serialNumber.isNotBlank() }) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, "Auto", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Interactive Autofill activated on matching active IMEI entries", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // General Item Info Row 1: Model & Name
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        val clean = it.replace("\n", "").replace("\r", "")
                        viewModel.modelInput.value = clean
                        viewModel.clearFormErrorAndSuccess()
                        modelTouched.value = true
                        if (it.contains("\n") || it.contains("\r")) {
                            safeRequestFocus(nameFocusRequester)
                        }
                    },
                    label = { Text("Model *") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(modelFocusRequester)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                safeRequestFocus(nameFocusRequester)
                                true
                            } else false
                        },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { safeRequestFocus(nameFocusRequester) }
                    ),
                    isError = modelError != null,
                    supportingText = if (modelError != null) { { Text(modelError, color = MaterialTheme.colorScheme.error) } } else null
                )

                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            val clean = it.replace("\n", "").replace("\r", "")
                            viewModel.nameInput.value = clean
                            viewModel.clearFormErrorAndSuccess()
                            nameTouched.value = true
                            if (it.contains("\n") || it.contains("\r")) {
                                safeRequestFocus(phoneFocusRequester)
                            }
                        },
                        label = { Text("Name *") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = { showPartySelectionDialog = true },
                                modifier = Modifier.focusProperties { canFocus = false }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = "Select Registered Party",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocusRequester)
                            .onFocusChanged { nameFocused = it.isFocused }
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                    safeRequestFocus(phoneFocusRequester)
                                    true
                                } else false
                            },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { safeRequestFocus(phoneFocusRequester) }
                        ),
                        isError = nameError != null,
                        supportingText = if (nameError != null) { { Text(nameError, color = MaterialTheme.colorScheme.error) } } else null
                    )

                    // Autocomplete name dropdown (Only displays suggestions if name has content & is focused)
                    val filteredDropdownParties = remember(name, combinedParties) {
                        if (name.isBlank() || name.length < 2) {
                            emptyList()
                        } else {
                            combinedParties.filter { it.name.contains(name, ignoreCase = true) }
                        }
                    }

                    if (nameFocused && filteredDropdownParties.isNotEmpty()) {
                        DropdownMenu(
                            expanded = true,
                            onDismissRequest = { /* Allow typing or dismissing */ },
                            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            filteredDropdownParties.forEach { party ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = party.name,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            if (party.phoneNumber.isNotBlank()) {
                                                Text(
                                                    text = "📞 ${party.phoneNumber}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.nameInput.value = party.name
                                        viewModel.phoneInput.value = party.phoneNumber
                                        viewModel.aadhaarInput.value = party.aadhaarNumber ?: ""
                                        viewModel.addressInput.value = party.address
                                        
                                        // Reset touched states so we don't trigger validation warnings
                                        nameTouched.value = false
                                        phoneTouched.value = false
                                        aadhaarTouched.value = false
                                        addressTouched.value = false
                                        
                                        nameFocused = false
                                    },
                                    modifier = Modifier.testTag("dropdown_name_select_${party.name}")
                                )
                            }
                        }
                    }
                }
            }

            // General Info: Phone & Aadhaar (With Stack style for full visibility of numbers)
            OutlinedTextField(
                value = phone,
                onValueChange = { 
                    val clean = it.replace("\n", "").replace("\r", "")
                    viewModel.phoneInput.value = clean 
                    phoneTouched.value = true
                    if (it.contains("\n") || it.contains("\r")) {
                        safeRequestFocus(aadhaarFocusRequester)
                    }
                },
                label = { Text("Phone Number *") },
                placeholder = { Text("Enter 10-digit phone number") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(phoneFocusRequester)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                            safeRequestFocus(aadhaarFocusRequester)
                            true
                        } else false
                    },
                isError = phoneError != null,
                supportingText = if (phoneError != null) { { Text(phoneError, color = MaterialTheme.colorScheme.error) } } else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { safeRequestFocus(aadhaarFocusRequester) }
                )
            )

            OutlinedTextField(
                value = aadhaar,
                onValueChange = { 
                    val clean = it.replace("\n", "").replace("\r", "")
                    viewModel.aadhaarInput.value = clean 
                    aadhaarTouched.value = true
                    if (it.contains("\n") || it.contains("\r")) {
                        if (activeSelection == 3) {
                            safeRequestFocus(techFocusRequester)
                        } else {
                            safeRequestFocus(addressFocusRequester)
                        }
                    }
                },
                label = { Text("Aadhaar Number (Optional)") },
                placeholder = { Text("Enter 12-digit Aadhaar") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(aadhaarFocusRequester)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                            if (activeSelection == 3) {
                                safeRequestFocus(techFocusRequester)
                            } else {
                                safeRequestFocus(addressFocusRequester)
                            }
                            true
                        } else false
                    },
                isError = aadhaarError != null,
                supportingText = if (aadhaarError != null) { { Text(aadhaarError, color = MaterialTheme.colorScheme.error) } } else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { 
                        if (activeSelection == 3) {
                            safeRequestFocus(techFocusRequester)
                        } else {
                            safeRequestFocus(addressFocusRequester)
                        }
                    }
                )
            )

            // Total Summary & Photo Attacher Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Aggregated Total Read-Only Box
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Total Amount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            text = "₹ $amount",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                val photoCount = if (photoUri.isNullOrBlank()) 0 else photoUri!!.split(",").size
                // Photo Selection card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showPhotoChooserDialog = true }
                        .border(
                            width = 1.dp,
                            color = if (photoCount > 0) themeColorAndLabel.first else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (photoCount > 0) themeColorAndLabel.first.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (photoCount > 0) Icons.Default.AddPhotoAlternate else Icons.Default.CameraAlt,
                            contentDescription = "Camera picker icon",
                            tint = if (photoCount > 0) themeColorAndLabel.first else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (photoCount > 0) "$photoCount Photos Added" else "Attach Photo(s)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (photoCount > 0) themeColorAndLabel.first else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (photoCount > 0) "(Tap to add more, max 10)" else "Camera/Gallery",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            val photoList = remember(photoUri) {
                if (photoUri.isNullOrBlank()) emptyList()
                else photoUri!!.split(",").filter { it.isNotBlank() }
            }
            if (photoList.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(photoList.size) { idx ->
                        val singleUri = photoList[idx]
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                .clickable { viewingPhotoUri = singleUri }
                        ) {
                            coil.compose.AsyncImage(
                                model = com.example.util.AppUtils.resolveImageModel(singleUri, thumbnail = true),
                                contentDescription = "Attached transaction photo $idx",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            // Remove photo button
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                                    .size(20.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .clickable {
                                        val updated = photoList.toMutableList()
                                        updated.removeAt(idx)
                                        viewModel.photoUriInput.value = if (updated.isEmpty()) null else updated.joinToString(",")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Interactive extra fields specifically for transaction mode is REPAIR!
            AnimatedVisibility(
                visible = activeSelection == 3,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = ColorsAmber.copy(alpha = 0.06f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ColorsAmber.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "🛠️ Repair Logistics Info Box",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ColorsAmber
                        )
                        Text(
                            text = "This item will be added to the Stock Directory and placed immediately in the Out-For-Repair section.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                        )
                        OutlinedTextField(
                            value = technician,
                            onValueChange = { 
                                val clean = it.replace("\n", "").replace("\r", "")
                                viewModel.technicianNameInput.value = clean 
                                techTouched.value = true
                                if (it.contains("\n") || it.contains("\r")) {
                                    safeRequestFocus(repairReasonFocusRequester)
                                }
                            },
                            label = { Text("Technician Assigned *") },
                            placeholder = { Text("E.g., John Miller") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(techFocusRequester)
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                        safeRequestFocus(repairReasonFocusRequester)
                                        true
                                    } else false
                                },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { safeRequestFocus(repairReasonFocusRequester) }
                            ),
                            isError = techError != null,
                            supportingText = if (techError != null) { { Text(techError, color = MaterialTheme.colorScheme.error) } } else null
                        )
                        OutlinedTextField(
                            value = repairReason,
                            onValueChange = { 
                                val clean = it.replace("\n", "").replace("\r", "")
                                viewModel.repairReasonInput.value = clean 
                                repairReasonTouched.value = true
                                if (it.contains("\n") || it.contains("\r")) {
                                    safeRequestFocus(addressFocusRequester)
                                }
                            },
                            label = { Text("Reason for Issue *") },
                            placeholder = { Text("E.g., Port faulty, key issue") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(repairReasonFocusRequester)
                                .onKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                                        safeRequestFocus(addressFocusRequester)
                                        true
                                    } else false
                                },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { safeRequestFocus(addressFocusRequester) }
                            ),
                            isError = repairReasonError != null,
                            supportingText = if (repairReasonError != null) { { Text(repairReasonError, color = MaterialTheme.colorScheme.error) } } else null
                        )
                    }
                }
            }

            // Address box (Mandatory)
            OutlinedTextField(
                value = address,
                onValueChange = {
                    val clean = it.replace("\n", "").replace("\r", "")
                    viewModel.addressInput.value = clean
                    viewModel.clearFormErrorAndSuccess()
                    addressTouched.value = true
                    if (it.contains("\n") || it.contains("\r")) {
                        safeRequestFocus(descriptionFocusRequester)
                    }
                },
                label = { Text("Address *") },
                placeholder = { Text("Enter party or storage address...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { safeRequestFocus(descriptionFocusRequester) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(addressFocusRequester)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                            safeRequestFocus(descriptionFocusRequester)
                            true
                        } else false
                    },
                isError = addressError != null,
                supportingText = if (addressError != null) { { Text(addressError, color = MaterialTheme.colorScheme.error) } } else null,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now in Hindi or English")
                            }
                            try {
                                speechLauncherAddress.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Speech recognizer not available", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.focusProperties { canFocus = false }
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Dictate Address")
                    }
                }
            )

            // 8. Description box (Optional)
            OutlinedTextField(
                value = description,
                onValueChange = {
                    viewModel.descriptionInput.value = it
                    viewModel.clearFormErrorAndSuccess()
                },
                label = { Text("Description (Optional)") },
                placeholder = { Text("Provide notes on condition, buyer/vender logs, serial updates...") },
                shape = RoundedCornerShape(14.dp),
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { focusManager.clearFocus() }
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now in Hindi or English")
                            }
                            try {
                                speechLauncher.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Speech recognizer not available", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.focusProperties { canFocus = false }
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Dictate Description")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .focusRequester(descriptionFocusRequester)
                    .testTag("form_description_input")
            )

            // Inline Validation Error Banner right above submit
            val activeError = getRealtimeError()
            AnimatedVisibility(
                visible = activeError != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Validation Alert",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Column {
                                Text(
                                    text = "Validation Issue Spotted",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = activeError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.clearFormErrorAndSuccess() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            
            if (!isActionAllowed) {
                Text(
                    text = "You do not have permission to perform this action.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Submitting CTA button with uploader feedback
            Button(
                onClick = { 
                    viewModel.clearFormErrorAndSuccess()
                    triggerAllTouched()
                    val validationErr = getRealtimeError()
                    if (validationErr == null) {
                        val invalidImeis = transactionSubItems
                            .map { it.serialNumber.trim() }
                            .filter { !AppUtils.isValidImei(it) }
                        if (invalidImeis.isNotEmpty()) {
                            pendingImeisToConfirm = invalidImeis
                            showImeiConfirmDialog = true
                            onConfirmProceedAction = {
                                viewModel.executeTransaction()
                            }
                        } else {
                            viewModel.executeTransaction()
                        }
                    } else {
                        Toast.makeText(context, "Please correct the highlighted validation errors.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isUploading && isActionAllowed,
                colors = ButtonDefaults.buttonColors(containerColor = themeColorAndLabel.first),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("form_submit_button")
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Uploading To Cloud secure database...", fontWeight = FontWeight.Bold)
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudUpload, "Cloud write icon")
                        Text(
                            text = when (activeSelection) {
                                0 -> "Submit Purchase"
                                1 -> "Submit Sale"
                                2 -> "Submit Return"
                                else -> "Submit Repair"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }



        // Smart Barcode & Box IMEI Scanner
        scannerIndex?.let { index ->
            SmartImeiScannerDialog(
                onDismissRequest = { scannerIndex = null },
                onBarcodeScanned = { scannedImei -> 
                    val currentAmount = transactionSubItems.getOrNull(index)?.amount ?: ""
                    if (activeSelection == 1) {
                        val matched = rawItems.find { item -> item.serialNumber.isNotBlank() && item.serialNumber.trim().equals(scannedImei.trim(), ignoreCase = true) }
                        if (matched != null) {
                            val effSale = com.example.util.AppUtils.getEffectiveSalePrice(matched)
                            val effMin = com.example.util.AppUtils.getEffectiveMinSalePrice(matched)
                            if (viewModel.modelInput.value.isBlank()) {
                                viewModel.modelInput.value = matched.model
                            }
                            val defaultAmt = if (currentAmount.isBlank() && effSale > 0.0) effSale.toInt().toString() else currentAmount
                            viewModel.updateSubItem(index, scannedImei, defaultAmt, sp = if (effSale > 0.0) effSale.toInt().toString() else "", msp = if (effMin > 0.0) effMin.toInt().toString() else "")
                        } else {
                            viewModel.updateSubItem(index, scannedImei, currentAmount)
                        }
                    } else {
                        viewModel.updateSubItem(index, scannedImei, currentAmount)
                    }
                    scannerIndex = null
                },
                suggestedImeis = suggestedImeis
            )
        }

        if (showImeiConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showImeiConfirmDialog = false },
                title = { Text("Invalid IMEI(s) Detected", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("The following IMEI numbers fail standard Luhn algorithm check:")
                        pendingImeisToConfirm.forEach {
                            Text("• $it", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        }
                        Text("Do you want to keep them as serial numbers anyway, or correct them?")
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showImeiConfirmDialog = false
                        onConfirmProceedAction?.invoke()
                    }) {
                        Text("Keep Serial")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImeiConfirmDialog = false }) {
                        Text("Go Back & Correct")
                    }
                }
            )
        }

        if (showPhotoChooserDialog) {
            val context = androidx.compose.ui.platform.LocalContext.current
            AlertDialog(
                onDismissRequest = { showPhotoChooserDialog = false },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showPhotoChooserDialog = false }) {
                        Text("Cancel Selection")
                    }
                },
                title = { Text("Select Photo Source", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Option 1: Click Photo via Camera
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val currentUris = viewModel.photoUriInput.value
                                        val urisArray = if (currentUris.isNullOrBlank()) emptyList() else currentUris.split(",")
                                        
                                        if (urisArray.size >= 10) {
                                            Toast.makeText(context, "Maximum 10 photos allowed", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val hasCameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                                context, android.Manifest.permission.CAMERA
                                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                            if (hasCameraPermission) {
                                                val uri = createTempImageUri()
                                                if (uri != null) {
                                                    launchCameraSafely(uri)
                                                } else {
                                                    Toast.makeText(context, "Error: Failed to create temporary file for picture.", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                            }
                                        }
                                    } catch (t: Throwable) {
                                        t.printStackTrace()
                                        Toast.makeText(context, "Camera action error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                    showPhotoChooserDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Capture option click",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Click Photo",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // Option 2: Upload from Gallery
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        galleryLauncher.launch("image/*")
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "No gallery app found", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    showPhotoChooserDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Gallery option click",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Upload from Gallery",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        // Option 3: Choose Sample Product Asset (Safe instant fallback)
                        Text(
                            text = "Or choose sample device asset:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(mockPhotoPresets.size) { pIdx ->
                                val preset = mockPhotoPresets[pIdx]
                                Surface(
                                    onClick = {
                                        val currentUris = viewModel.photoUriInput.value
                                        val urisArray = if (currentUris.isNullOrBlank()) emptyList() else currentUris.split(",")
                                        if (urisArray.size < 10) {
                                            viewModel.photoUriInput.value = (urisArray + preset.second).joinToString(",")
                                            Toast.makeText(context, "${preset.first} photo attached!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Maximum 10 photos allowed", Toast.LENGTH_SHORT).show()
                                        }
                                        showPhotoChooserDialog = false
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Smartphone, contentDescription = null, modifier = Modifier.size(16.dp), tint = themeColorAndLabel.first)
                                        Text(preset.first, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }

        // Full Screen Photo Viewer when tapping an attached thumbnail
        if (viewingPhotoUri != null) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { viewingPhotoUri = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    coil.compose.AsyncImage(
                        model = com.example.util.AppUtils.resolveImageModel(viewingPhotoUri),
                        contentDescription = "Full Screen Transaction Photo",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                    IconButton(
                        onClick = { viewingPhotoUri = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                            .background(Color.Black.copy(alpha = 0.6f), androidx.compose.foundation.shape.CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Viewer", tint = Color.White)
                    }
                }
            }
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = dateInMillis
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let {
                                viewModel.dateInMillisInput.value = it
                            }
                            showDatePicker = false
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showPartySelectionDialog) {
        AlertDialog(
            onDismissRequest = { showPartySelectionDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "Parties",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Select Ledger Party", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = partySearchQuery,
                        onValueChange = { partySearchQuery = it },
                        label = { Text("Search Party by Name...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val filtered = remember(partySearchQuery, combinedParties) {
                        if (partySearchQuery.isBlank()) {
                            combinedParties
                        } else {
                            combinedParties.filter { it.name.contains(partySearchQuery, ignoreCase = true) }
                        }
                    }

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No ledger parties found.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filtered) { party ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.nameInput.value = party.name
                                            viewModel.phoneInput.value = party.phoneNumber
                                            viewModel.aadhaarInput.value = party.aadhaarNumber ?: ""
                                            viewModel.addressInput.value = party.address
                                            
                                            // Reset touched states so we don't trigger validation warnings
                                            nameTouched.value = false
                                            phoneTouched.value = false
                                            aadhaarTouched.value = false
                                            addressTouched.value = false
                                            
                                            showPartySelectionDialog = false
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = party.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (party.phoneNumber.isNotBlank()) {
                                            Text(
                                                text = "📞 Phone: ${party.phoneNumber}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (party.address.isNotBlank()) {
                                            Text(
                                                text = "📍 Address: ${party.address}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPartySelectionDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (isProcessingPhotos) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = themeColorAndLabel.first
                    )
                    Text(
                        text = "Optimizing & Attaching Images...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Please wait, scaling high-res photos for lightning-fast sync.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
}

