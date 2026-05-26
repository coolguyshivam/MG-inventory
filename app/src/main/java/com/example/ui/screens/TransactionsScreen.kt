package com.example.ui.screens

import android.net.Uri
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    // Real Camera and Gallery integration launchers
    val tempCameraUriState = remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.photoUriInput.value = uri.toString()
            Toast.makeText(context, "Gallery Image Linked!", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraUriState.value?.let { uri ->
                viewModel.photoUriInput.value = uri.toString()
                Toast.makeText(context, "Camera Snapshot Attached!", Toast.LENGTH_SHORT).show()
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
            FileProvider.getUriForFile(context, "com.example.fileprovider", tempFile)
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
    val description by viewModel.descriptionInput.collectAsState()
    val dateInMillis by viewModel.dateInMillisInput.collectAsState()
    val quantity by viewModel.quantityInput.collectAsState()
    val photoUri by viewModel.photoUriInput.collectAsState()

    val technician by viewModel.technicianNameInput.collectAsState()
    val repairReason by viewModel.repairReasonInput.collectAsState()

    var showScannerDialog by remember { mutableStateOf(false) }
    var showPhotoChooserDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Date formatting helper
    val formattedDate = remember(dateInMillis) {
        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        sdf.format(Date(dateInMillis))
    }

    val canSell by viewModel.canSell.collectAsState()
    val canRepair by viewModel.canRepair.collectAsState()
    
    val isActionAllowed = if (activeSelection == 3) canRepair else canSell

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

        AnimatedVisibility(visible = errMessage != null) {
            errMessage?.let { msg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Error, "Error", tint = MaterialTheme.colorScheme.error)
                        Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // FORM FIELDS WRAPPER
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. Serial Number with Barcode Scanner overlay (IMEI)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = serialNumber,
                    onValueChange = {
                        viewModel.serialNumberInput.value = it
                        viewModel.clearFormErrorAndSuccess()
                    },
                    label = { Text("Serial Number *") },
                    placeholder = { Text("IMEI-xxxxx-xxxxx...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("form_serial_input"),
                    leadingIcon = { Icon(Icons.Default.Dialpad, "Keypad") }
                )

                IconButton(
                    onClick = { showScannerDialog = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = themeColorAndLabel.first,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .testTag("form_serial_scan_button")
                ) {
                    Icon(Icons.Default.QrCodeScanner, "Form Scanner")
                }
            }

            // Quick AutoFill Indicator for Sales/Returns
            if ((activeSelection == 1 || activeSelection == 2) && serialNumber.isNotBlank()) {
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

            // 2. Model field
            OutlinedTextField(
                value = model,
                onValueChange = {
                    viewModel.modelInput.value = it
                    viewModel.clearFormErrorAndSuccess()
                },
                label = { Text("Model *") },
                placeholder = { Text("E.g., Pixel 9 Pro, Galaxy S25") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_model_input"),
                leadingIcon = { Icon(Icons.Default.Devices, "Model Device") }
            )

            // 3. Name field
            OutlinedTextField(
                value = name,
                onValueChange = {
                    viewModel.nameInput.value = it
                    viewModel.clearFormErrorAndSuccess()
                },
                label = { Text("Name *") },
                placeholder = { Text("E.g., Google Pixel Slate") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("form_name_input"),
                leadingIcon = { Icon(Icons.Default.Info, "Item Name Description") }
            )

            // Split Aadhaar and Phone row (Optional fields)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 4. Phone Number (Optional)
                OutlinedTextField(
                    value = phone,
                    onValueChange = { viewModel.phoneInput.value = it },
                    label = { Text("Phone (optional)") },
                    placeholder = { Text("10 digit") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("form_phone_input"),
                    leadingIcon = { Icon(Icons.Default.Phone, "Tel phone") }
                )

                // 5. Aadhaar Number (Optional)
                OutlinedTextField(
                    value = aadhaar,
                    onValueChange = { viewModel.aadhaarInput.value = it },
                    label = { Text("Aadhaar (optional)") },
                    placeholder = { Text("12 digit number") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("form_aadhaar_input"),
                    leadingIcon = { Icon(Icons.Default.Fingerprint, "Aadhaar Card") }
                )
            }

            // 6. Transaction Amount and Date selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Pricing value field
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        viewModel.amountInput.value = it
                        viewModel.clearFormErrorAndSuccess()
                    },
                    label = { Text("Amount *") },
                    placeholder = { Text("₹ 0.00") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("form_amount_input"),
                    leadingIcon = { Icon(Icons.Default.CurrencyRupee, "Currency") }
                )

                // Interactive Date Selector - Clicking opens Date Picker dialog
                OutlinedTextField(
                    value = formattedDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date Selector *") },
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Default.CalendarToday, "DatePicker") },
                    trailingIcon = {
                        IconButton(
                            onClick = { showDatePicker = true }
                        ) {
                            Icon(Icons.Default.DateRange, "Pick calendar Date")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showDatePicker = true }
                        .testTag("form_date_field")
                )
            }

            // 7. Quantity and Photo Attacher Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quantity Counter (Value editable, defaults to 1)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { if (quantity > 1) viewModel.quantityInput.value = quantity - 1 },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Remove, "Minus Quantity")
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Quantity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$quantity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        }

                        IconButton(
                            onClick = { viewModel.quantityInput.value = quantity + 1 },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, "Add Quantity")
                        }
                    }
                }

                // Photo Selection card (Simulates camera click or choose from Gallery nicely with design options)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showPhotoChooserDialog = true }
                        .border(
                            width = 1.dp,
                            color = if (photoUri != null) themeColorAndLabel.first else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(14.dp)
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (photoUri != null) themeColorAndLabel.first.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
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
                            imageVector = if (photoUri != null) Icons.Default.AddPhotoAlternate else Icons.Default.CameraAlt,
                            contentDescription = "Camera picker icon",
                            tint = if (photoUri != null) themeColorAndLabel.first else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (photoUri != null) "Photo Selected" else "Attach Photo *",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (photoUri != null) themeColorAndLabel.first else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (photoUri != null) photoUri!! else "Camera/Gallery",
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
                            onValueChange = { viewModel.technicianNameInput.value = it },
                            label = { Text("Technician Assigned *") },
                            placeholder = { Text("E.g., John Miller") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = repairReason,
                            onValueChange = { viewModel.repairReasonInput.value = it },
                            label = { Text("Reason for Issue *") },
                            placeholder = { Text("E.g., Port faulty, key issue") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 8. Description box
            OutlinedTextField(
                value = description,
                onValueChange = {
                    viewModel.descriptionInput.value = it
                    viewModel.clearFormErrorAndSuccess()
                },
                label = { Text("Description *") },
                placeholder = { Text("Provide notes on condition, buyer/vender logs, serial updates...") },
                shape = RoundedCornerShape(14.dp),
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
                onClick = { viewModel.executeTransaction() },
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
                                0 -> "Log Purchase Inbound"
                                1 -> "Log Sale Outbound"
                                2 -> "Log Return Inbound"
                                else -> "Log Repair Dispatch"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // Barcode reader frame modal
        if (showScannerDialog) {
            BarcodeScannerMockDialog(
                onDismissRequest = { showScannerDialog = false },
                onBarcodeScanned = { scannedImei ->
                    viewModel.serialNumberInput.value = scannedImei
                },
                suggestedImeis = suggestedImeis
            )
        }

        // Photo picker Mock Selector trigger (Rule 9)
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
                                        cameraLauncher.launch(uri)
                                    } else {
                                        viewModel.photoUriInput.value = "camera_snapshot.jpg"
                                        android.widget.Toast.makeText(context, "Clicked Photo via Camera (Demo)!", android.widget.Toast.LENGTH_SHORT).show()
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
                                    galleryLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
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
    }
}

val ColorsAmber = Color(0xFFF59E0B)
