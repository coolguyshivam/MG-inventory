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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.components.BarcodeScannerMockDialog
import com.example.ui.viewmodel.StockViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
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
    val tempCameraUriState = remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val currentUris = viewModel.photoUriInput.value
            val urisArray = if (currentUris.isNullOrBlank()) emptyList() else currentUris.split(",")
            
            val newUrisStr = uris.mapNotNull { uri ->
                com.example.util.AppUtils.uriToBase64(context, uri)
            }
            
            if (urisArray.size + newUrisStr.size <= 10) {
                viewModel.photoUriInput.value = (urisArray + newUrisStr).joinToString(",")
                Toast.makeText(context, "Gallery Images Attached & Sync-optimized!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Maximum 10 photos allowed total", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraUriState.value?.let { uri ->
                val currentUris = viewModel.photoUriInput.value
                val urisArray = if (currentUris.isNullOrBlank()) emptyList() else currentUris.split(",")
                if (urisArray.size < 10) {
                    val base64 = com.example.util.AppUtils.uriToBase64(context, uri)
                    if (base64 != null) {
                        viewModel.photoUriInput.value = (urisArray + base64).joinToString(",")
                        Toast.makeText(context, "Camera Snapshot Attached & Sync-optimized!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Error: Failed to compress snapshot.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "Maximum 10 photos allowed", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Camera capture cancelled or failed.", Toast.LENGTH_SHORT).show()
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
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ViewModel form states
    val activeSelection by viewModel.transactionSelection.collectAsState() // 0: Purchase, 1: Sale, 2: Return, 3: Repair
    val isUploading by viewModel.isUploadingTransaction.collectAsState()
    val errMessage by viewModel.transactionError.collectAsState()
    val successMessage by viewModel.transactionSuccessMessage.collectAsState()
    val rawItems by viewModel.inventoryItems.collectAsState()
    val suggestedImeis = remember(rawItems) { rawItems.map { it.serialNumber } }

    val serialNumber by viewModel.serialNumberInput.collectAsState()
    val model by viewModel.modelInput.collectAsState()
    val name by viewModel.nameInput.collectAsState()
    val phone by viewModel.phoneInput.collectAsState()
    val aadhaar by viewModel.aadhaarInput.collectAsState()
    val amount by viewModel.amountInput.collectAsState()
    val address by viewModel.addressInput.collectAsState()
    val description by viewModel.descriptionInput.collectAsState()
    val dateInMillis by viewModel.dateInMillisInput.collectAsState()
    val quantity by viewModel.quantityInput.collectAsState()
    val photoUri by viewModel.photoUriInput.collectAsState()

    val technician by viewModel.technicianNameInput.collectAsState()
    val repairReason by viewModel.repairReasonInput.collectAsState()

    var showPhotoChooserDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var scannerIndex by remember { mutableStateOf<Int?>(null) }
    val transactionSubItems by viewModel.transactionSubItems.collectAsState()

    // Date formatting helper
    val formattedDate = remember(dateInMillis) {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        sdf.format(Date(dateInMillis))
    }

    val canSell by viewModel.canSell.collectAsState()
    val canRepair by viewModel.canRepair.collectAsState()
    
    val isActionAllowed = if (activeSelection == 3) canRepair else canSell

    // Real-time validation touched trackers
    val imeiTouched = remember { mutableStateMapOf<Int, Boolean>() }
    val priceTouched = remember { mutableStateMapOf<Int, Boolean>() }
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

    val modelError = if (modelTouched.value && model.isBlank()) "Model is a mandatory field" else null
    val nameError = if (nameTouched.value && name.isBlank()) "Name is a mandatory field" else null
    val phoneError = if (phoneTouched.value && phone.isNotEmpty() && !phone.matches(Regex("^[6-9]\\d{9}$"))) "Must start with 6-9 and be 10 digits" else null
    val aadhaarError = if (aadhaarTouched.value && aadhaar.isNotEmpty() && !aadhaar.matches(Regex("^\\d{12}$"))) "Must be exactly 12 numeric digits" else null
    val techError = if (activeSelection == 3 && techTouched.value && technician.isBlank()) "Technician Assigned is mandatory" else null
    val repairReasonError = if (activeSelection == 3 && repairReasonTouched.value && repairReason.isBlank()) "Reason for Issue is mandatory" else null
    val addressError = if (addressTouched.value && address.isBlank()) "Address is a mandatory field" else null

    fun getRealtimeError(): String? {
        if (model.isBlank() && modelTouched.value) return "Model is mandatory"
        if (name.isBlank() && nameTouched.value) return "Name is mandatory"
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
        }
        phoneTouched.value = true
        aadhaarTouched.value = true
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                            modifier = Modifier.size(32.dp)
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
                                    viewModel.updateSubItem(index, it, subItem.amount)
                                    viewModel.clearFormErrorAndSuccess()
                                    imeiTouched[index] = true
                                },
                                label = { Text("IMEI/Serial Number *") },
                                placeholder = { Text("Enter 15-digit IMEI") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                isError = imeiErr != null,
                                supportingText = if (imeiErr != null) { { Text(imeiErr, color = MaterialTheme.colorScheme.error) } } else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                trailingIcon = {
                                    val iconAlpha = if (subItem.serialNumber.isNotEmpty()) 0.4f else 1f
                                    IconButton(
                                        onClick = { scannerIndex = index },
                                        modifier = Modifier.alpha(iconAlpha)
                                    ) {
                                        Icon(Icons.Default.QrCodeScanner, "Scanner", tint = themeColorAndLabel.first)
                                    }
                                }
                            )

                            OutlinedTextField(
                                value = subItem.amount,
                                onValueChange = { 
                                    viewModel.updateSubItem(index, subItem.serialNumber, it) 
                                    viewModel.clearFormErrorAndSuccess()
                                    priceTouched[index] = true
                                },
                                label = { Text("Price (₹) *") },
                                placeholder = { Text("Enter item cost") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                isError = priceErr != null,
                                supportingText = if (priceErr != null) { { Text(priceErr, color = MaterialTheme.colorScheme.error) } } else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
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
                        viewModel.modelInput.value = it
                        viewModel.clearFormErrorAndSuccess()
                        modelTouched.value = true
                    },
                    label = { Text("Model *") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    isError = modelError != null,
                    supportingText = if (modelError != null) { { Text(modelError, color = MaterialTheme.colorScheme.error) } } else null
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        viewModel.nameInput.value = it
                        viewModel.clearFormErrorAndSuccess()
                        nameTouched.value = true
                    },
                    label = { Text("Name *") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                    isError = nameError != null,
                    supportingText = if (nameError != null) { { Text(nameError, color = MaterialTheme.colorScheme.error) } } else null
                )
            }

            // General Info: Phone & Aadhaar (With Stack style for full visibility of numbers)
            OutlinedTextField(
                value = phone,
                onValueChange = { 
                    viewModel.phoneInput.value = it 
                    phoneTouched.value = true
                },
                label = { Text("Phone Number (Optional)") },
                placeholder = { Text("Enter 10-digit phone number") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                isError = phoneError != null,
                supportingText = if (phoneError != null) { { Text(phoneError, color = MaterialTheme.colorScheme.error) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = aadhaar,
                onValueChange = { 
                    viewModel.aadhaarInput.value = it 
                    aadhaarTouched.value = true
                },
                label = { Text("Aadhaar Number (Optional)") },
                placeholder = { Text("Enter 12-digit Aadhaar") },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
                isError = aadhaarError != null,
                supportingText = if (aadhaarError != null) { { Text(aadhaarError, color = MaterialTheme.colorScheme.error) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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
                                viewModel.technicianNameInput.value = it 
                                techTouched.value = true
                            },
                            label = { Text("Technician Assigned *") },
                            placeholder = { Text("E.g., John Miller") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            isError = techError != null,
                            supportingText = if (techError != null) { { Text(techError, color = MaterialTheme.colorScheme.error) } } else null
                        )
                        OutlinedTextField(
                            value = repairReason,
                            onValueChange = { 
                                viewModel.repairReasonInput.value = it 
                                repairReasonTouched.value = true
                            },
                            label = { Text("Reason for Issue *") },
                            placeholder = { Text("E.g., Port faulty, key issue") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
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
                    viewModel.addressInput.value = it
                    viewModel.clearFormErrorAndSuccess()
                    addressTouched.value = true
                },
                label = { Text("Address *") },
                placeholder = { Text("Enter party or storage address...") },
                shape = RoundedCornerShape(14.dp),
                isError = addressError != null,
                supportingText = if (addressError != null) { { Text(addressError, color = MaterialTheme.colorScheme.error) } } else null,
                trailingIcon = {
                    IconButton(onClick = {
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
                    }) {
                        Icon(Icons.Default.Mic, contentDescription = "Dictate Address")
                    }
                },
                modifier = Modifier.fillMaxWidth()
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
                trailingIcon = {
                    IconButton(onClick = {
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
                    }) {
                        Icon(Icons.Default.Mic, contentDescription = "Dictate Description")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .testTag("form_description_input")
            )

            Spacer(modifier = Modifier.height(8.dp))
            
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
                    triggerAllTouched()
                    val validationErr = getRealtimeError()
                    if (validationErr == null) {
                        viewModel.executeTransaction()
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



        // Photo picker Mock Selector trigger (Rule 9)
        scannerIndex?.let { index ->
            BarcodeScannerMockDialog(
                onDismissRequest = { scannerIndex = null },
                onBarcodeScanned = { scannedImei -> 
                    val currentAmount = transactionSubItems.getOrNull(index)?.amount ?: ""
                    viewModel.updateSubItem(index, scannedImei, currentAmount)
                    scannerIndex = null
                },
                suggestedImeis = suggestedImeis
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
                                    val uri = createTempImageUri()
                                    if (uri != null) {
                                        tempCameraUriState.value = uri
                                        try {
                                            cameraLauncher.launch(uri)
                                        } catch (e: Exception) {
                                            android.widget.Toast.makeText(context, "No camera app found", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        val currentUris = viewModel.photoUriInput.value
                                        val urisArray = if (currentUris.isNullOrBlank()) emptyList() else currentUris.split(",")
                                        if (urisArray.size < 10) {
                                            viewModel.photoUriInput.value = (urisArray + "camera_snapshot.jpg").joinToString(",")
                                            android.widget.Toast.makeText(context, "Clicked Photo via Camera (Demo)!", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "Maximum 10 photos allowed", android.widget.Toast.LENGTH_SHORT).show()
                                        }
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
                    }
                }
            )
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
        
        Spacer(modifier = Modifier.height(96.dp)) // ensure we can scroll past the bottom floating error banner!
    }

    // Floating Validation Error Banner near the submit button
    val activeError = getRealtimeError()
    AnimatedVisibility(
        visible = activeError != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
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
}
}

val ColorsAmber = Color(0xFFF59E0B)
