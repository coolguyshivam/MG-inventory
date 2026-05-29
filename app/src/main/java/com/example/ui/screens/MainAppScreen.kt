package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.model.HistoryEvent
import com.example.data.model.InventoryItem
import com.example.ui.viewmodel.StockViewModel
import com.example.util.AppUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: StockViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Auth State (Pin authentication for ledger security)
    var isAuthenticated by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf(false) }

    // Navigation State
    var currentTab by remember { mutableStateOf(0) } // 0: Form, 1: Inventory, 2: History, 3: Analytics

    if (!isAuthenticated) {
        // Biometric / PIN entry Gate
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 400.dp)
                    .testTag("pin_login_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "MOBILE GALLERY",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Inventory Ledger Authorization",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4) pinInput = it
                            authError = false
                        },
                        label = { Text("Enter 4-Digit Passcode") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        isError = authError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pin_input_field")
                    )
                    
                    if (authError) {
                        Text(
                            text = "Invalid Passcode. Enter 1234 for demo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = {
                            if (pinInput == "1234") {
                                isAuthenticated = true
                            } else {
                                authError = true
                                pinInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("login_submit_btn")
                    ) {
                        Text("Verify Identity")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextButton(
                        onClick = {
                            // Biometric simulation quick click helper
                            isAuthenticated = true
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Face, contentDescription = "Face ID")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Fast FaceID Bypass")
                        }
                    }
                }
            }
        }
    } else {
        // Logged In App Interface
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "MOBILE GALLERY",
                                fontWeight = FontWeight.Black,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                "Premium Inventory & Ledger Dashboard",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                isAuthenticated = false
                                pinInput = ""
                            }
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Lock Screen")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.AddCircle, contentDescription = "Add Tx") },
                        label = { Text("Add Form") },
                        modifier = Modifier.testTag("nav_tab_form")
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.List, contentDescription = "Stock") },
                        label = { Text("Inventory") },
                        modifier = Modifier.testTag("nav_tab_inventory")
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Default.Refresh, contentDescription = "Logs") },
                        label = { Text("History") },
                        modifier = Modifier.testTag("nav_tab_history")
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = { Icon(Icons.Default.Info, contentDescription = "Charts") },
                        label = { Text("Analytics") },
                        modifier = Modifier.testTag("nav_tab_analytics")
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    0 -> FormTabScreen(viewModel)
                    1 -> InventoryTabScreen(viewModel)
                    2 -> HistoryTabScreen(viewModel)
                    3 -> AnalyticsTabScreen(viewModel)
                }
            }
        }
    }
}

// ==========================================
// 1. ADD TRANSACTION FORM TAB
// ==========================================
@Composable
fun FormTabScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    
    // Form Inputs
    var actionMode by remember { mutableStateOf("PURCHASE") } // PURCHASE, SALE, RETURN, REPAIR_SENT, REPAIR_RETURNED
    var model by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var serialNumber by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var aadhaarNumber by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf(1) }
    var address by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    
    // Attached Photo UrIs (Stored as local strings first, uploaded altogether on submit)
    var attachedPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Temporary Camera URI
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    
    // Async execution flow
    val isUploading by viewModel.isUploading.collectAsState()

    // Activity Launchers for Photo Actions
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                attachedPhotos = attachedPhotos + uri.toString()
                Toast.makeText(context, "Photo attached locally!", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && cameraTempUri != null) {
                attachedPhotos = attachedPhotos + cameraTempUri.toString()
                Toast.makeText(context, "Captured snapshot attached locally!", Toast.LENGTH_SHORT).show()
            }
        }
    )

    fun triggerCamera() {
        try {
            val cacheDirectory = File(context.cacheDir, "pictures").apply { mkdirs() }
            val file = File(cacheDirectory, "pic_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "com.example.fileprovider",
                file
            )
            cameraTempUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to launch Camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Select Mode of Transaction",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Single choice row
                    val modes = listOf("PURCHASE", "SALE", "RETURN", "REPAIR_SENT", "REPAIR_RETURNED")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        modes.forEach { mode ->
                            val isSelected = actionMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                    .clickable { actionMode = mode }
                                    .padding(vertical = 8.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.replace("_", " "),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "${actionMode} TRANSACTION LOG",
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Brand & Model Name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("model_field"),
                leadingIcon = { Icon(Icons.Default.Call, "Model") }
            )
        }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Customer / Party Name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("name_field"),
                leadingIcon = { Icon(Icons.Default.Person, "Name") }
            )
        }

        item {
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Contact Phone String") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phone_field"),
                leadingIcon = { Icon(Icons.Default.Call, "Phone") }
            )
        }

        item {
            OutlinedTextField(
                value = serialNumber,
                onValueChange = { serialNumber = it },
                label = { Text("IMEI / Serial key") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("serial_field"),
                leadingIcon = { Icon(Icons.Default.Info, "Serial") }
            )
        }

        item {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Disbursed Amount (INR)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("amount_field"),
                leadingIcon = { Icon(Icons.Default.ShoppingCart, "Amount") }
            )
        }

        item {
            OutlinedTextField(
                value = aadhaarNumber,
                onValueChange = { aadhaarNumber = it },
                label = { Text("Aadhaar ID Card Key") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("aadhaar_field"),
                leadingIcon = { Icon(Icons.Default.AccountBox, "Aadhaar") }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Quantity:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (quantity > 1) quantity-- },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Clear, "Subtract")
                    }
                    Text(
                        text = "$quantity",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    IconButton(
                        onClick = { quantity++ },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Add, "Add")
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Full Home Address (Optional)") },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("address_field"),
                leadingIcon = { Icon(Icons.Default.LocationOn, "Address") }
            )
        }

        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Short Transaction Log Details / Notes") },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("description_field"),
                leadingIcon = { Icon(Icons.Default.Edit, "Notes") }
            )
        }

        // Snapshots / Attachment Controls
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Attach Verification Snapshots",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Captured photos are compressed and uploaded securely on save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { triggerCamera() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            enabled = !isUploading
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Camera")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Camera Snap")
                        }

                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            enabled = !isUploading
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Gallery")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gallery File")
                        }
                    }

                    if (attachedPhotos.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Current Attachments (${attachedPhotos.size}):", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Display attached images list Horizontally
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            attachedPhotos.forEachIndexed { index, p ->
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                ) {
                                    AsyncImage(
                                        model = p,
                                        contentDescription = "Attachment $index",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Remove button
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(Color.Red, CircleShape)
                                            .align(Alignment.TopEnd)
                                            .clickable {
                                                attachedPhotos = attachedPhotos.filterIndexed { i, _ -> i != index }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Del", tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Submit Button
        item {
            if (isUploading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Uploading snapshots & saving records. Please wait...", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        if (model.isBlank() || serialNumber.isBlank()) {
                            Toast.makeText(context, "Please enter at least Brand Model and Serial Number / IMEI.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val amountVal = amountText.toDoubleOrNull() ?: 0.0
                        viewModel.executeTransaction(
                            context = context,
                            actionType = actionMode,
                            model = model,
                            name = name,
                            phone = phoneNumber,
                            serialNumber = serialNumber,
                            amount = amountVal,
                            aadhaarNumber = aadhaarNumber,
                            quantity = quantity,
                            address = address,
                            description = description,
                            localPhotoUris = attachedPhotos,
                            onComplete = { success, msg ->
                                if (success) {
                                    Toast.makeText(context, msg ?: "Transaction completed!", Toast.LENGTH_LONG).show()
                                    // Reset fields
                                    model = ""
                                    name = ""
                                    phoneNumber = ""
                                    serialNumber = ""
                                    amountText = ""
                                    aadhaarNumber = ""
                                    quantity = 1
                                    address = ""
                                    description = ""
                                    attachedPhotos = emptyList()
                                } else {
                                    Toast.makeText(context, "Error: $msg", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("submit_transaction_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Save Transaction & Log Stock", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ==========================================
// 2. INVENTORY STOCK LIST TAB
// ==========================================
@Composable
fun InventoryTabScreen(viewModel: StockViewModel) {
    val items by viewModel.allInventory.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items else {
            items.filter {
                it.model.contains(searchQuery, ignoreCase = true) ||
                it.serialNumber.contains(searchQuery, ignoreCase = true) ||
                it.status.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Stock (Model / IMEI / Status)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            leadingIcon = { Icon(Icons.Default.Search, "Search") },
            singleLine = true
        )

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.List, "Empty State", modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No inventory stock records present.", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.model, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row {
                                    Text("IMEI: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(item.serialNumber, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                }
                                Row {
                                    Text("Qty: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("${item.quantity} units", style = MaterialTheme.typography.bodySmall)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Est Price: ", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text("INR ${String.format(Locale.getDefault(), "%,.2f", item.price)}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text("Owner/Vendor: ${item.supplierOrCustomerName}", style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                val badgeColor = when (item.status) {
                                    "STOCK" -> Color(0xFF4CAF50)
                                    "SOLD" -> Color(0xFFE91E63)
                                    "REPAIR" -> Color(0xFFFF9800)
                                    "RETURNED" -> Color(0xFF757575)
                                    else -> Color.Gray
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(badgeColor)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    val statusLabel = when (item.status) {
                                        "STOCK" -> "In Stock"
                                        "SOLD" -> "Sold out"
                                        "REPAIR" -> "In Repair"
                                        "RETURNED" -> "Returned"
                                        else -> item.status
                                    }
                                    Text(statusLabel, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            IconButton(
                                onClick = { viewModel.deleteItem(item) }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete from stock", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. HISTORY LEDGER EVENT TAB WITH SLIP POPUP
// ==========================================
@Composable
fun HistoryTabScreen(viewModel: StockViewModel) {
    val events by viewModel.allEvents.collectAsState()
    var selectedEventForPrint by remember { mutableStateOf<HistoryEvent?>(null) }

    if (events.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Refresh, "Empty Logs", modifier = Modifier.size(64.dp), tint = Color.LightGray)
                Spacer(modifier = Modifier.height(12.dp))
                Text("No ledger transaction slips found in feed.", color = Color.Gray)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Transaction Stream (${events.size} logs)",
                modifier = Modifier.padding(16.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(events) { event ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedEventForPrint = event },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                when (event.actionType) {
                                                    "PURCHASE" -> Color(0xFF1E88E5)
                                                    "SALE" -> Color(0xFF43A047)
                                                    "RETURN" -> Color(0xFF757575)
                                                    else -> Color(0xFFE53935)
                                                }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(event.actionType, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = SimpleDateFormat("dd MMM hh:mm a", Locale.getDefault()).format(Date(event.timestamp)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(event.model, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Customer: ${event.name}", style = MaterialTheme.typography.bodyMedium)
                                Text("Serial/IMEI: ${event.serialNumber}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                
                                val amountLabel = "INR ${String.format(Locale.getDefault(), "%,.2f", event.amount)}"
                                Text(amountLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            }

                            Row {
                                IconButton(
                                    onClick = { viewModel.deleteEvent(event.id) }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete entry", tint = Color.Red)
                                }
                                Icon(Icons.Default.ArrowForward, contentDescription = "Configure print", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterVertically))
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal popup custom slip customizer
    selectedEventForPrint?.let { event ->
        CustomPrintDialog(
            event = event,
            onDismiss = { selectedEventForPrint = null }
        )
    }
}

// Custom printable terms dialog
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPrintDialog(
    event: HistoryEvent,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    // De-construct photos
    val photosList = remember(event.photoUri) {
        if (!event.photoUri.isNullOrBlank()) {
            event.photoUri.split(",")
        } else emptyList()
    }

    // Default Hindi and English structures based strictly on actionType
    val defaultTerms = remember(event.actionType, event.timestamp) {
        val sdfDate = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
        val formattedDateVal = sdfDate.format(Date(event.timestamp))
        when (event.actionType) {
            "SALE" -> {
                "उपरोक्त सभी तथ्य बिल्कुल सही है।\n" +
                "मैने ये मोबाइल आज पूरा चेक कर के मोबाइल गैलरी से लिया है और मैं इससे संतुष्ट हूँ।\n" +
                "अब से इस मोबाइल की सारी जिम्मेदारी केवल मेरी है।\n\n\n" +
                "Sign                     Date: $formattedDateVal\n\n" +
                "1. WARRANTY ASSISTANCE: No warranty/guarantee for the used phones. In case any phone is eligible, it will be told separately and shall be valid only if it is written on this paper.\n\n" +
                "2. REFUND POLICY: All processed sales are final. Absolutely no cash refunds. Unopened, untampered items may be considered for exchange or store ledger credit notes within 24 hours of receipt at the sole discretion of the store."
            }
            "RETURN" -> {
                "उपरोक्त सभी तथ्य बिल्कुल सही है।\n" +
                "मैने आज ये मोबाइल जिसका मै खुद स्वामी हु, स्वेच्छा से मोबाइल गैलरी को दिया है।\n" +
                "उपरोक्त फोन पर किसी भी प्रकार का ऋण, ब्याज या क्लेम बाकी नहीं है। इसका किसी भी लोन/फाइनेंस कंपनी से कोई संबंध नहीं है। यदि इसपे कोई लोन रिकवरी होती है तो उसकी सारी जिम्मेदारी मेरी होगी और किसी की नहीं होगी।\n" +
                "आज से इस फोन का मालिक मै नहीं हू।\n\n\n" +
                "Sign                     Date: $formattedDateVal\n\n" +
                "1. Seller/Customer is solely responsible for the all the previous repairs, finances and other tasks related to this phone. The buyer-store does not have any responsibility of any finance emi's or and any wrong doings in the past. Any EMIs due on this phone shall be paid by the seller-customer. Buyer can independently format it now.\n\n" +
                "2. REFUND POLICY: All processed sales are final. Absolutely no cash refunds. Unopened, untampered items may be considered for exchange or store ledger credit notes within 24 hours of receipt at the sole discretion of the store."
            }
            "REPAIR_SENT", "REPAIR_RETURNED" -> {
                "उपरोक्त सभी तथ्य बिल्कुल सही है।\n" +
                "मैने आज ये मोबाइल जिसका मै खुद स्वामी हु, स्वेच्छा से मोबाइल गैलरी को दिया है।\n" +
                "उपरोक्त फोन पर किसी भी प्रकार का ऋण, ब्याज या क्लेम बाकी नहीं है। इसका किसी भी लोन/फाइनेंस कंपनी से कोई संबंध नहीं है। यदि इसपे कोई लोन रिकवरी होती है तो उसकी सारी जिम्मेदारी मेरी होगी और किसी की नहीं होगी।\n" +
                "आज से इस फोन का मालिक मै नहीं हू।\n\n\n" +
                "Sign                     Date: $formattedDateVal\n\n" +
                "1. Seller/Customer is solely responsible for the all the previous repairs, finances and other tasks related to this phone. The buyer-store does not have any responsibility of any finance emi's or and any wrong doings in the past. Any EMIs due on this phone shall be paid by the seller-customer. Buyer can independently format it now.\n\n" +
                "2. REFUND POLICY: All processed sales are final. Absolutely no cash refunds. Unopened, untampered items may be considered for exchange or store ledger credit notes within 24 hours of receipt at the sole discretion of the store.\n\n" +
                "3. OUT-FOR-REPAIR DEVICES: Repair hand-overs are registered entirely at client's risk. Please backup/clone personal user files. Retailer is not liable for data loss or software degradation during repair."
            }
            else -> { // PURCHASE (Purchase category) or fallback
                "उपरोक्त सभी तथ्य बिल्कुल सही है।\n" +
                "मैने आज ये मोबाइल जिसका मै खुद स्वामी हु, स्वेच्छा से मोबाइल गैलरी को दिया है।\n" +
                "उपरोक्त फोन पर किसी भी प्रकार का ऋण, ब्याज या क्लेम बाकी नहीं है। इसका किसी भी लोन/फाइनेंस कंपनी से कोई संबंध नहीं है। यदि इसपे कोई लोन रिकवरी होती है तो उसकी सारी जिम्मेदारी मेरी होगी और किसी की नहीं होगी।\n" +
                "आज से इस फोन का मालिक मै नहीं हू।\n\n\n" +
                "Sign                     Date: $formattedDateVal\n\n" +
                "1. Seller/Customer is solely responsible for the all the previous repairs, finances and other tasks related to this phone. The buyer-store does not have any responsibility of any finance emi's or and any wrong doings in the past. Any EMIs due on this phone shall be paid by the seller-customer. Buyer can independently format it now.\n\n" +
                "2. REFUND POLICY: All processed sales are final. Absolutely no cash refunds. Unopened, untampered items may be considered for exchange or store ledger credit notes within 24 hours of receipt at the sole discretion of the store."
            }
        }
    }

    var termsCustomString by remember { mutableStateOf(defaultTerms) }
    var selectedPhotoIdsForPrint by remember { mutableStateOf<List<String>>(photosList) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    "Print Invoice Verification Sheet",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Customize Hindi/English declarations & attachment files before printing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable fields
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Edit Declaration Terms Text:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = termsCustomString,
                        onValueChange = { termsCustomString = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        maxLines = 15,
                        textStyle = MaterialTheme.typography.bodySmall
                    )

                    if (photosList.isNotEmpty()) {
                        Text(
                            "Select Snapshots to Include:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            photosList.forEach { photoUri ->
                                val isSelected = selectedPhotoIdsForPrint.contains(photoUri)
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .clickable {
                                            selectedPhotoIdsForPrint = if (isSelected) {
                                                selectedPhotoIdsForPrint.filter { it != photoUri }
                                            } else {
                                                selectedPhotoIdsForPrint + photoUri
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = photoUri,
                                        contentDescription = "Photo Attachment",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape
                                                )
                                                .align(Alignment.TopEnd),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            "No snapshots attached in transaction log.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            AppUtils.printHistoryEventCustom(
                                context,
                                event,
                                termsCustomString,
                                selectedPhotoIdsForPrint
                            )
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Printer")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Trigger Print")
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. ANALYTICS CHARTS TAB
// ==========================================
@Composable
fun AnalyticsTabScreen(viewModel: StockViewModel) {
    val events by viewModel.allEvents.collectAsState()

    val totalPurchasedAmount = remember(events) {
        events.filter { it.actionType == "PURCHASE" }.sumOf { it.amount }
    }
    val totalSalesAmount = remember(events) {
        events.filter { it.actionType == "SALE" }.sumOf { it.amount }
    }
    val purchaseCount = remember(events) {
        events.count { it.actionType == "PURCHASE" }
    }
    val saleCount = remember(events) {
        events.count { it.actionType == "SALE" }
    }
    val repairsCount = remember(events) {
        events.count { it.actionType == "REPAIR_SENT" || it.actionType == "REPAIR_RETURNED" }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "FINANCIAL STATEMENT & LEDGERS",
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Summary Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F1E88E5))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Purchases", style = MaterialTheme.typography.bodySmall, color = Color(0xFF1565C0))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "INR ${String.format(Locale.getDefault(), "%,.2f", totalPurchasedAmount)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF0D47A1)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("$purchaseCount transaction logs", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1F43A047))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Revenue (Sales)", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "INR ${String.format(Locale.getDefault(), "%,.2f", totalSalesAmount)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF1B5E20)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("$saleCount transaction logs", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }

        // Action distribution card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Transaction Distribution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.LightGray)
                    ) {
                        val total = (purchaseCount + saleCount + repairsCount).coerceAtLeast(1).toFloat()
                        val purPercent = purchaseCount / total
                        val salePercent = saleCount / total
                        val repairPercent = repairsCount / total

                        if (purchaseCount > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(purPercent.coerceAtLeast(0.01f))
                                    .background(Color(0xFF1E88E5))
                            )
                        }
                        if (saleCount > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(salePercent.coerceAtLeast(0.01f))
                                    .background(Color(0xFF43A047))
                            )
                        }
                        if (repairsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(repairPercent.coerceAtLeast(0.01f))
                                    .background(Color(0xFFFF9800))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).background(Color(0xFF1E88E5), CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Purchases ($purchaseCount)", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).background(Color(0xFF43A047), CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sales ($saleCount)", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).background(Color(0xFFFF9800), CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Repairs ($repairsCount)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Reset Database logic card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Danger Zone",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "This will wipe all local Room database events and inventory stock instantly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.clearAll() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, "Delete")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset All System Ledger Data")
                    }
                }
            }
        }
    }
}
