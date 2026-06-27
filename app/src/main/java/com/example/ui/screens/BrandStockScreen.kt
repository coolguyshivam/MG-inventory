package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.BrandStockItem
import com.example.data.model.BrandStockTransaction
import com.example.ui.viewmodel.StockViewModel
import com.example.util.AppUtils
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.text.SimpleDateFormat
import java.util.*

data class ModelStockSummary(
    val brand: String,
    val modelName: String,
    val count: Int,
    val color: String = "",
    val specs: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandStockScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val stockItems by viewModel.brandStockItems.collectAsStateWithLifecycle()
    val transactions by viewModel.brandStockTransactions.collectAsStateWithLifecycle()
    val currentUser by viewModel.loggedInUser.collectAsStateWithLifecycle()
    val brandVariants by viewModel.brandVariants.collectAsStateWithLifecycle()

    val isAdmin = currentUser?.role?.equals("Admin", ignoreCase = true) == true

    var activeSubTab by remember { mutableStateOf(0) } // 0 = Active Stock, 1 = Logs, 2 = Items
    
    // Filters
    val brands = listOf("Oppo", "Vivo", "Samsung", "OnePlus", "Realme", "Motorola", "Infinix", "Tecno", "Apple", "Google", "Redmi", "Others")
    var selectedBrand by remember { mutableStateOf<String?>(null) } // null = All Brands
    var selectedWarehouse by remember { mutableStateOf("Combined") } // "Combined", "G", "O"
    var searchQuery by remember { mutableStateOf("") }

    // Status feedback state
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }

    // Dialog state
    var showAddStockDialog by remember { mutableStateOf(false) }
    var showSellStockDialog by remember { mutableStateOf(false) }
    var sellImei by remember { mutableStateOf("") }
    var showCsvHelpDialog by remember { mutableStateOf(false) }
    var showExportCsvDialog by remember { mutableStateOf(false) }
    var showAddVariantDialog by remember { mutableStateOf(false) }
    var showImportCsvFileDialog by remember { mutableStateOf(false) }

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importBrandStockCsv(context, uri) { success, dups, errs, msg ->
                statusMessage = msg
                isErrorStatus = success == 0
            }
        }
    }

    // Scanner state
    var showQrScannerDialog by remember { mutableStateOf(false) }
    var qrScannerCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var scannerModeInOrOut by remember { mutableStateOf(true) } // true = In, false = Out

    val composeScope = rememberCoroutineScope()
    var isAnalyzingImage by remember { mutableStateOf(false) }
    var detectedSerials by remember { mutableStateOf<List<String>>(emptyList()) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isAnalyzingImage = true
            scanError = null
            detectedSerials = emptyList()
            composeScope.launch {
                try {
                    val result = com.example.util.GeminiScanner.extractSerialsFromImage(context, uri)
                    if (result.isNotEmpty()) {
                        detectedSerials = result
                    } else {
                        scanError = "No serials/IMEIs detected in this photo. Please try another image."
                    }
                } catch (e: Exception) {
                    scanError = "Failed to analyze: ${e.localizedMessage}"
                } finally {
                    isAnalyzingImage = false
                }
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = tempPhotoUri
        if (success && uri != null) {
            isAnalyzingImage = true
            scanError = null
            detectedSerials = emptyList()
            composeScope.launch {
                try {
                    val result = com.example.util.GeminiScanner.extractSerialsFromImage(context, uri)
                    if (result.isNotEmpty()) {
                        detectedSerials = result
                    } else {
                        scanError = "No serials/IMEIs detected in this photo. Please check image lighting."
                    }
                } catch (e: Exception) {
                    scanError = "Failed to analyze: ${e.localizedMessage}"
                } finally {
                    isAnalyzingImage = false
                }
            }
        }
    }

    // Delete confirmation dialogs
    var itemToDelete by remember { mutableStateOf<BrandStockItem?>(null) }
    var transactionToDelete by remember { mutableStateOf<BrandStockTransaction?>(null) }
    var variantToDelete by remember { mutableStateOf<com.example.data.model.BrandVariant?>(null) }

    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    var showImeiConfirmDialog by remember { mutableStateOf(false) }
    var pendingImeisToConfirm by remember { mutableStateOf<List<String>>(emptyList()) }
    var onConfirmProceedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    

    // Prefill state for Stock Inwards Dialog
    var prefillBrand by remember { mutableStateOf("Oppo") }
    var prefillVariant by remember { mutableStateOf("") }
    var prefillColor by remember { mutableStateOf("") }



    // Dynamic metrics calculation for stock health & shortages
    val variantStockCounts = remember(stockItems, brandVariants) {
        val definedModels = brandVariants.map { it.brand to it.modelName }.toSet()
        val activeModels = stockItems.map { it.brand to it.variant }.toSet()
        val allModels = (definedModels + activeModels).toList()
        
        allModels.map { (brandName, variantName) ->
            val matches = stockItems.filter { it.brand.equals(brandName, ignoreCase = true) && it.variant.equals(variantName, ignoreCase = true) }
            val count = matches.size
            val presetMatch = brandVariants.firstOrNull { it.brand.equals(brandName, ignoreCase = true) && it.modelName.equals(variantName, ignoreCase = true) }
            val color = presetMatch?.color ?: matches.firstOrNull()?.color ?: ""
            val specs = presetMatch?.specs ?: ""
            ModelStockSummary(
                brand = brandName,
                modelName = variantName,
                count = count,
                color = color,
                specs = specs
            )
        }.sortedWith(compareBy<ModelStockSummary> { it.count }.thenBy { it.brand })
    }

    // Filter items logic
    val filteredStockItems = remember(stockItems, selectedBrand, selectedWarehouse, searchQuery) {
        stockItems.filter { item ->
            val matchesBrand = selectedBrand == null || item.brand.equals(selectedBrand, ignoreCase = true)
            val matchesWarehouse = selectedWarehouse == "Combined" || item.warehouse.equals(selectedWarehouse, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() || 
                    item.imei.contains(searchQuery, ignoreCase = true) || 
                    item.variant.contains(searchQuery, ignoreCase = true) || 
                    item.color.contains(searchQuery, ignoreCase = true)
            matchesBrand && matchesWarehouse && matchesSearch
        }.sortedByDescending { item -> item.addedDate }
    }

    val filteredTransactions = remember(transactions, selectedBrand, selectedWarehouse, searchQuery) {
        transactions.filter { tx ->
            val matchesBrand = selectedBrand == null || tx.brand.equals(selectedBrand, ignoreCase = true)
            val matchesWarehouse = selectedWarehouse == "Combined" || tx.warehouse.equals(selectedWarehouse, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() || 
                    tx.imei.contains(searchQuery, ignoreCase = true) || 
                    tx.variant.contains(searchQuery, ignoreCase = true) || 
                    tx.color.contains(searchQuery, ignoreCase = true) ||
                    tx.operator.contains(searchQuery, ignoreCase = true)
            matchesBrand && matchesWarehouse && matchesSearch
        }.sortedByDescending { tx -> tx.dateInMillis }
    }

    val filteredVariants = remember(brandVariants, selectedBrand) {
        brandVariants.filter { v ->
            selectedBrand == null || v.brand.equals(selectedBrand, ignoreCase = true)
        }.sortedBy { v -> v.modelName }
    }

    // IMEI Lifetime Tracker Logic - builds history from active stock items and transaction logs!
    val imeiQuery = searchQuery.trim()
    val trackRecords = remember(imeiQuery, stockItems, transactions) {
        if (imeiQuery.isNotBlank() && imeiQuery.length >= 4) {
            val activeMatch = stockItems.firstOrNull { it.imei.equals(imeiQuery, ignoreCase = true) }
            val matchingTxs = transactions.filter { it.imei.equals(imeiQuery, ignoreCase = true) }.sortedBy { it.dateInMillis }
            
            if (activeMatch != null || matchingTxs.isNotEmpty()) {
                val brandName = activeMatch?.brand ?: matchingTxs.firstOrNull()?.brand ?: "Unknown"
                val variantName = activeMatch?.variant ?: matchingTxs.firstOrNull()?.variant ?: "Unknown"
                val colorName = activeMatch?.color ?: matchingTxs.firstOrNull()?.color ?: "Unknown"
                Triple(activeMatch, matchingTxs, BrandStockItem(brand = brandName, variant = variantName, color = colorName, imei = imeiQuery))
            } else {
                null
            }
        } else {
            null
        }
    }

    // Summary Statistics calculations based on selected/filtered items
    val totalInStock = filteredStockItems.size
    val gCount = filteredStockItems.count { it.warehouse.equals("G", ignoreCase = true) }
    val oCount = filteredStockItems.count { it.warehouse.equals("O", ignoreCase = true) }

    val groupedStock = remember(filteredStockItems) {
        filteredStockItems.groupBy { "${it.brand.uppercase()} ${it.variant}" }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("brand_stock_lazy_column"),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Elegant Simple Header (Title) - Branding card removed
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "Warehouse Stock Manager",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "v2.3.2",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Intelligent Edition",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.outline,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Export CSV Reports Button
                    FilledTonalButton(
                        onClick = { showExportCsvDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("export_csv_reports_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "Export CSV Reports",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reports", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Sub-Tab Row options
            item {
                TabRow(
                    selectedTabIndex = activeSubTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = activeSubTab == 0,
                        onClick = { activeSubTab = 0 },
                        text = { Text("Active Stock", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.PhoneAndroid, "Active Inventory List") }
                    )
                    Tab(
                        selected = activeSubTab == 1,
                        onClick = { activeSubTab = 1 },
                        text = { Text("Logs", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.ReceiptLong, "Audit logs transaction list") }
                    )
                    Tab(
                        selected = activeSubTab == 2,
                        onClick = { activeSubTab = 2 },
                        text = { Text("Items", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.Category, "Predefined item variants") }
                    )
                }
            }

            // Brand & Warehouse selection filters (Compact layout to reduce vertical empty space)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Brand Selection Label and Scrollable Chips Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BrandingWatermark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Brand:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        val scrollState = rememberScrollState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(scrollState),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = selectedBrand == null,
                                onClick = { selectedBrand = null },
                                label = { Text("All Brands", fontSize = 12.sp) },
                                leadingIcon = if (selectedBrand == null) {
                                    { Icon(Icons.Default.Check, "Selected", modifier = Modifier.size(14.dp)) }
                                } else null,
                                modifier = Modifier.height(28.dp).testTag("brand_chip_all")
                            )
                            
                            brands.forEach { brandName ->
                                val isSelected = selectedBrand == brandName
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedBrand = brandName },
                                    label = { Text(brandName, fontSize = 12.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, "Selected", modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    modifier = Modifier.height(28.dp).testTag("brand_chip_$brandName")
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                        // Warehouse Dropdown Filter Row (Any / Both options)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Warehouse Filter:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            var expandedWhMenu by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { expandedWhMenu = true },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f),
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ),
                                    modifier = Modifier.height(30.dp).testTag("warehouse_filter_dropdown_button")
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = when (selectedWarehouse) {
                                                "Combined" -> "Both Warehouses"
                                                else -> "Warehouse $selectedWarehouse"
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = expandedWhMenu,
                                    onDismissRequest = { expandedWhMenu = false }
                                ) {
                                    listOf("Combined", "G", "O").forEach { wh ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = if (wh == "Combined") "Both Warehouses" else "Warehouse $wh",
                                                    fontWeight = if (selectedWarehouse == wh) FontWeight.ExtraBold else FontWeight.Normal,
                                                    fontSize = 13.sp
                                                )
                                            },
                                            onClick = {
                                                selectedWarehouse = wh
                                                expandedWhMenu = false
                                            },
                                            leadingIcon = if (selectedWarehouse == wh) {
                                                { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                                            } else null,
                                            modifier = Modifier.testTag("dropdown_warehouse_$wh")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar input field
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search by IMEI, variant, color...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, "Clear search query")
                                }
                            }
                        } else null,
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("brand_stock_search_bar")
                    )

                    IconButton(
                        onClick = {
                            scannerModeInOrOut = false
                            qrScannerCallback = { scannedResult ->
                                searchQuery = scannedResult
                                showQrScannerDialog = false
                            }
                            showQrScannerDialog = true
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Scan IMEI Barcode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // IMEI Track & Trace timeline timeline panel (Triggered dynamically!)
            if (trackRecords != null) {
                val activeObj = trackRecords.first
                val matchTxs = trackRecords.second
                val devInfo = trackRecords.third
                
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Timeline, "Tracking live imei details", tint = MaterialTheme.colorScheme.primary)
                                    Text("IMEI Trace History Log", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (activeObj != null) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (activeObj != null) "IN WAREHOUSE ${activeObj.warehouse}" else "DISPATCHED / OUT",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        color = if (activeObj != null) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                }
                            }

                            Text(
                                text = "Device: ${devInfo.brand.uppercase()} - ${devInfo.variant} (${devInfo.color})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "IMEI Trace: ${devInfo.imei}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f))

                            // Timeline steps
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                val inTx = matchTxs.firstOrNull { it.type.equals("IN", ignoreCase = true) }
                                val outTx = matchTxs.firstOrNull { it.type.equals("OUT", ignoreCase = true) }

                                // 1. IN Record step
                                if (inTx != null || activeObj != null) {
                                    val opName = inTx?.operator ?: activeObj?.addedByUser ?: "Unknown"
                                    val logDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                                        Date(inTx?.dateInMillis ?: activeObj?.addedDate ?: System.currentTimeMillis())
                                    )
                                    val activeWh = inTx?.warehouse ?: activeObj?.warehouse ?: "G"
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Incoming intake record",
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text("Intake / Purchase IN", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                                            Text("Warehouse: Warehouse $activeWh  |  By: $opName", style = MaterialTheme.typography.labelSmall)
                                            Text("Recorded: $logDate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f))
                                        }
                                    }
                                }

                                // 2. OUT Record step
                                if (outTx != null) {
                                    val opName = outTx.operator
                                    val logDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(outTx.dateInMillis))
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Cancel,
                                            contentDescription = "Outgoing dispatch record",
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text("Sale / Dispatch OUT", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
                                            Text("Warehouse: Warehouse ${outTx.warehouse}  |  By: $opName", style = MaterialTheme.typography.labelSmall)
                                            Text("Notes: ${outTx.notes ?: "regular sale"}", style = MaterialTheme.typography.labelSmall)
                                            Text("Recorded: $logDate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f))
                                        }
                                    }
                                } else if (activeObj == null && matchTxs.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RemoveCircle,
                                            contentDescription = "Unavailable",
                                            tint = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text("Inactive Status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                            Text("No longer resides in physical warehouse stock.", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Summary Statistics Badge Card
            item {
                if (activeSubTab == 2) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Items Created", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${brandVariants.size} models", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }
                            VerticalDivider(modifier = Modifier.height(30.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Filtered Items", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${filteredVariants.size} variants", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "CURRENT PHYSICAL STOCK STATUS",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.6.sp
                                    )
                                    Text(
                                        text = "$totalInStock devices in stock",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                val leaderBrand = remember(stockItems) {
                                    if (stockItems.isEmpty()) "None"
                                    else stockItems.groupBy { it.brand }
                                        .maxByOrNull { it.value.size }?.key ?: "None"
                                }
                                if (leaderBrand != "None") {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "★ Leader: ${leaderBrand.uppercase()}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            
                            // Dual-tone split ratio bar between G and O warehouses
                            if (totalInStock > 0) {
                                val ratio = gCount.toFloat() / totalInStock.toFloat()
                                val gPercent = (ratio * 100).toInt()
                                val oPercent = 100 - gPercent
                                
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(CircleShape)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .weight(if (ratio > 0f) ratio else 0.001f)
                                                .background(Color(0xFF00ACC1)) // Cyan-teal for G
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .weight(if ((1f - ratio) > 0f) (1f - ratio) else 0.001f)
                                                .background(Color(0xFFFF9800)) // Amber-orange for O
                                        )
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF00ACC1)))
                                            Text(
                                                text = "Wh G: $gCount ($gPercent%)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Wh O: $oCount ($oPercent%)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFFF9800)))
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                )
                                Text(
                                    text = "No current stock resides across both warehouses.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons Row (IN-Flow & OUT-Flow) or "Add Items" button
            item {
                if (activeSubTab == 2) {
                    Button(
                        onClick = { showAddVariantDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_add_brand_variant_preset"),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Add, "Add items")
                            Text("Add Items", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showAddStockDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_brand_stock_in"),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.ArrowUpward, "Inward")
                                Text("Stock In (Purchase)", fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = { showSellStockDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_brand_stock_out"),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.ArrowDownward, "Outward")
                                Text("Stock Out (Sale)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                csvPickerLauncher.launch("*/*")
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_brand_stock_csv_import"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, "Upload File")
                                Text("Import Stock via CSV/Excel", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        IconButton(
                            onClick = { showCsvHelpDialog = true },
                            modifier = Modifier
                                .size(44.dp)
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Help,
                                contentDescription = "Show CSV Format Guide",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // Feedback Status Message Banner
            if (statusMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isErrorStatus) MaterialTheme.colorScheme.errorContainer else Color(0xFFE8F5E9)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = statusMessage!!,
                                color = if (isErrorStatus) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF1B5E20),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { statusMessage = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Banner",
                                    tint = if (isErrorStatus) MaterialTheme.colorScheme.onErrorContainer else Color(0xFF1B5E20)
                                )
                            }
                        }
                    }
                }
            }

            // Actual List content
            if (activeSubTab == 0) {
                // ACTIVE STOCK ITEMS
                
                // Active Stock List Subtitle Header
                item {
                    Text(
                        text = "Current Physical Stock Inventory",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 4.dp)
                    )
                }

                // ACTIVE STOCK ITEMS
                if (filteredStockItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Smartphone,
                                    contentDescription = "No phones in warehouse",
                                    modifier = Modifier.size(60.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = "No active stock items match your criteria.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    groupedStock.forEach { (modelName, itemsList) ->
                        item(key = modelName) {
                            val isExpanded = expandedGroups[modelName] ?: false
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isExpanded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        expandedGroups[modelName] = !isExpanded
                                    }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    // Header Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = modelName,
                                                fontWeight = FontWeight.ExtraBold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${itemsList.size} in stock",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            // Split summary of G vs O for this model:
                                            val gSubCount = itemsList.count { it.warehouse.equals("G", ignoreCase = true) }
                                            val oSubCount = itemsList.size - gSubCount
                                            if (gSubCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFFE0F7FA))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("G: $gSubCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF006064))
                                                }
                                            }
                                            if (oSubCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFFFFF3E0))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text("O: $oSubCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                                }
                                            }

                                            Icon(
                                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (isExpanded) "Collapse list" else "Expand list"
                                            )
                                        }
                                    }

                                    if (isExpanded) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Collapsible IMEI lists grouped inside Card
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            itemsList.forEach { subItem ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            Text(
                                                                text = subItem.imei,
                                                                fontWeight = FontWeight.ExtraBold,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.testTag("stock_item_imei_${subItem.imei}")
                                                            )

                                                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                                            val ctx = androidx.compose.ui.platform.LocalContext.current
                                                            Icon(
                                                                imageVector = Icons.Default.ContentCopy,
                                                                contentDescription = "Copy IMEI",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                                modifier = Modifier
                                                                    .size(14.dp)
                                                                    .clickable {
                                                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(subItem.imei))
                                                                        Toast.makeText(ctx, "IMEI Copied!", Toast.LENGTH_SHORT).show()
                                                                    }
                                                            )
                                                        }
                                                        
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier.padding(top = 4.dp)
                                                        ) {
                                                            // Color field manually entered for each IMEI
                                                            Text(
                                                                text = "Color: ${subItem.color.ifBlank { "Unknown" }}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Medium,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            
                                                            Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 10.sp)

                                                            // Wh badge
                                                            Text(
                                                                text = "Wh: ${subItem.warehouse}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (subItem.warehouse.equals("G", ignoreCase = true)) Color(0xFF006064) else Color(0xFFE65100)
                                                            )
                                                        }
                                                    }

                                                    // Inline mini control buttons
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        // Trace/timeline search
                                                        IconButton(
                                                            onClick = { searchQuery = subItem.imei },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.History,
                                                                contentDescription = "Trace IMEI",
                                                                tint = MaterialTheme.colorScheme.secondary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }

                                                        // Out (Dispatch / Sale)
                                                        IconButton(
                                                            onClick = { 
                                                                sellImei = subItem.imei
                                                                showSellStockDialog = true 
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.ArrowDownward,
                                                                contentDescription = "Stock Out",
                                                                tint = Color(0xFFE53935),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }

                                                        // Delete (if Admin)
                                                        if (isAdmin) {
                                                            IconButton(
                                                                onClick = { itemToDelete = subItem },
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Delete,
                                                                    contentDescription = "Delete",
                                                                    tint = MaterialTheme.colorScheme.error,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (activeSubTab == 1) {
                // AUDIT LOG TRANSACTIONS
                if (filteredTransactions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = "No receipts logged",
                                    modifier = Modifier.size(60.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = "No intake or outflow transactions found.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredTransactions) { tx ->
                        BrandTransactionCard(tx, isAdmin) {
                            transactionToDelete = tx
                        }
                    }
                }
            } else {
                // ITEM DEFINITIONS
                if (filteredVariants.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Category,
                                    contentDescription = "No variant presets",
                                    modifier = Modifier.size(60.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = "No items found for this brand.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Tap 'Add Items' above to create one.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                } else {
                    items(filteredVariants) { preset ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(preset.brand.uppercase(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
                                    }
                                    Text(preset.modelName, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text("Specs: ${preset.specs}", fontSize = 10.sp) }
                                        )
                                    }
                                }
                                if (isAdmin) {
                                    IconButton(
                                        onClick = { variantToDelete = preset },
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Icon(Icons.Default.Delete, "Delete item definition")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ================== DIALOGS ==================

    // COMPREHENSIVE MULTI-FORMAT REPORT GENERATOR (CSV, EXCEL, PDF)
    fun triggerExport(context: Context, reportType: String, format: String) {
        try {
            val headers: List<String>
            val rows = mutableListOf<List<String>>()
            
            when (reportType) {
                "stock" -> {
                    headers = listOf("Brand", "Model Variant", "IMEI_Serial", "Warehouse", "Color", "Added Date")
                    stockItems.forEach { item ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.addedDate))
                        rows.add(listOf(
                            item.brand.replace(",", " "),
                            item.variant.replace(",", " "),
                            item.imei.replace(",", " "),
                            item.warehouse.replace(",", " "),
                            item.color.replace(",", " "),
                            dateStr
                        ))
                    }
                }
                "transactions" -> {
                    headers = listOf("Date", "IMEI_Serial", "Type", "Brand", "Model Variant", "Color", "Warehouse", "Operator", "Notes")
                    transactions.forEach { tx ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(tx.dateInMillis))
                        rows.add(listOf(
                            dateStr,
                            tx.imei.replace(",", " "),
                            tx.type.replace(",", " "),
                            tx.brand.replace(",", " "),
                            tx.variant.replace(",", " "),
                            tx.color.replace(",", " "),
                            tx.warehouse.replace(",", " "),
                            tx.operator.replace(",", " "),
                            (tx.notes ?: "").replace(",", " ")
                        ))
                    }
                }
                else -> { // "items"
                    headers = listOf("Brand", "Model", "Specs", "Color")
                    brandVariants.forEach { v ->
                        rows.add(listOf(
                            v.brand.replace(",", " "),
                            v.modelName.replace(",", " "),
                            v.specs.replace(",", " "),
                            v.color.replace(",", " ")
                        ))
                    }
                }
            }
            
            when (format) {
                "csv" -> {
                    val csvString = (listOf(headers.joinToString(",")) + rows.map { it.joinToString(",") }).joinToString("\n")
                    val filename = "${reportType}_report_${System.currentTimeMillis()}.csv"
                    val cacheDir = File(context.cacheDir, "reports")
                    if (!cacheDir.exists()) cacheDir.mkdirs()
                    val file = File(cacheDir, filename)
                    file.writeText(csvString)
                    
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "$reportType CSV Report")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share/Export CSV Report"))
                }
                "excel" -> {
                    val reportTitle = when(reportType) {
                        "stock" -> "Active Warehouse Stock Report"
                        "transactions" -> "Detailed Transaction Action Logs"
                        else -> "Saved Item Catalog Definitions"
                    }
                    val htmlBuilder = java.lang.StringBuilder()
                    htmlBuilder.append("<html><head><meta charset=\"UTF-8\">")
                    htmlBuilder.append("<style>")
                    htmlBuilder.append("table { border-collapse: collapse; width: 100%; font-family: sans-serif; }")
                    htmlBuilder.append("th { background-color: #1F4E79; color: white; font-weight: bold; padding: 8px; border: 1px solid #D9D9D9; }")
                    htmlBuilder.append("td { padding: 6px 12px; border: 1px solid #D9D9D9; font-size: 11pt; }")
                    htmlBuilder.append("tr:nth-child(even) { background-color: #F2F2F2; }")
                    htmlBuilder.append("h2 { color: #1F4E79; margin-bottom: 4px; }")
                    htmlBuilder.append(".timestamp { color: #595959; font-size: 9pt; margin-bottom: 20px; }")
                    htmlBuilder.append("</style></head><body>")
                    
                    htmlBuilder.append("<h2>").append(reportTitle).append("</h2>")
                    htmlBuilder.append("<p class=\"timestamp\">Generated on: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())).append("</p>")
                    
                    htmlBuilder.append("<table>")
                    htmlBuilder.append("<tr>")
                    headers.forEach { h -> htmlBuilder.append("<th>").append(h).append("</th>") }
                    htmlBuilder.append("</tr>")
                    
                    rows.forEach { r ->
                        htmlBuilder.append("<tr>")
                        r.forEach { c -> htmlBuilder.append("<td>").append(c).append("</td>") }
                        htmlBuilder.append("</tr>")
                    }
                    htmlBuilder.append("</table></body></html>")
                    
                    val filename = "${reportType}_report_${System.currentTimeMillis()}.xls"
                    val cacheDir = File(context.cacheDir, "reports")
                    if (!cacheDir.exists()) cacheDir.mkdirs()
                    val file = File(cacheDir, filename)
                    file.writeText(htmlBuilder.toString())
                    
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.ms-excel"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "$reportType Excel Report")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share/Export Excel Report"))
                }
                "pdf" -> {
                    val pdfDocument = android.graphics.pdf.PdfDocument()
                    val titlePaint = android.graphics.Paint().apply {
                        isFakeBoldText = true
                        textSize = 14f
                        color = android.graphics.Color.BLACK
                    }
                    val headerPaint = android.graphics.Paint().apply {
                        isFakeBoldText = true
                        textSize = 10f
                        color = android.graphics.Color.WHITE
                    }
                    val contentPaint = android.graphics.Paint().apply {
                        textSize = 9f
                        color = android.graphics.Color.BLACK
                    }
                    val footerPaint = android.graphics.Paint().apply {
                        textSize = 8f
                        color = android.graphics.Color.GRAY
                    }
                    
                    val A4_WIDTH = 595
                    val A4_HEIGHT = 842
                    val MARGIN = 30
                    
                    var pageNum = 1
                    var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNum).create()
                    var page = pdfDocument.startPage(pageInfo)
                    var canvas = page.canvas
                    canvas.drawColor(android.graphics.Color.WHITE)
                    
                    var yPos = MARGIN + 20f
                    
                    fun drawHeaderAndTitle(canv: android.graphics.Canvas) {
                        val titleText = when(reportType) {
                            "stock" -> "Active Warehouse Stock Report"
                            "transactions" -> "Detailed Transaction Action Logs"
                            else -> "Saved Item Catalog Definitions"
                        }
                        canv.drawText(titleText, MARGIN.toFloat(), yPos, titlePaint)
                        
                        yPos += 15
                        canv.drawText("Generated on: " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()), MARGIN.toFloat(), yPos, footerPaint)
                        
                        yPos += 25
                        
                        val headerBgPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.rgb(46, 82, 135)
                        }
                        canv.drawRect(MARGIN.toFloat(), yPos - 12f, (A4_WIDTH - MARGIN).toFloat(), yPos + 8f, headerBgPaint)
                        
                        val columnWidth = (A4_WIDTH - 2 * MARGIN) / headers.size.toFloat()
                        headers.forEachIndexed { i, hName ->
                            canv.drawText(hName, MARGIN + i * columnWidth + 4, yPos, headerPaint)
                        }
                        yPos += 18f
                    }
                    
                    drawHeaderAndTitle(canvas)
                    
                    val zebraPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.rgb(245, 247, 250)
                    }
                    
                    rows.forEachIndexed { idx, rValues ->
                        if (yPos > A4_HEIGHT - MARGIN - 30) {
                            canvas.drawText("Page $pageNum", (A4_WIDTH / 2).toFloat(), (A4_HEIGHT - 20).toFloat(), footerPaint)
                            pdfDocument.finishPage(page)
                            
                            pageNum++
                            pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, pageNum).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            canvas.drawColor(android.graphics.Color.WHITE)
                            yPos = MARGIN + 30f
                            drawHeaderAndTitle(canvas)
                        }
                        
                        if (idx % 2 == 1) {
                            canvas.drawRect(MARGIN.toFloat(), yPos - 10f, (A4_WIDTH - MARGIN).toFloat(), yPos + 4f, zebraPaint)
                        }
                        
                        val linePaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.rgb(230, 230, 230)
                            strokeWidth = 0.5f
                        }
                        canvas.drawLine(MARGIN.toFloat(), yPos + 4f, (A4_WIDTH - MARGIN).toFloat(), yPos + 4f, linePaint)
                        
                        val columnWidth = (A4_WIDTH - 2 * MARGIN) / headers.size.toFloat()
                        rValues.forEachIndexed { colIdx, cellValue ->
                            val maxChar = if (headers.size > 6) 12 else 18
                            val truncatedText = if (cellValue.length > maxChar) cellValue.take(maxChar - 3) + "..." else cellValue
                            canvas.drawText(truncatedText, MARGIN + colIdx * columnWidth + 4, yPos, contentPaint)
                        }
                        yPos += 16f
                    }
                    
                    canvas.drawText("Page $pageNum", (A4_WIDTH / 2).toFloat(), (A4_HEIGHT - 20).toFloat(), footerPaint)
                    pdfDocument.finishPage(page)
                    
                    val filename = "${reportType}_report_${System.currentTimeMillis()}.pdf"
                    val cacheDir = File(context.cacheDir, "reports")
                    if (!cacheDir.exists()) cacheDir.mkdirs()
                    val file = File(cacheDir, filename)
                    
                    pdfDocument.writeTo(java.io.FileOutputStream(file))
                    pdfDocument.close()
                    
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "$reportType PDF Report")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share/Export PDF Report"))
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    if (showCsvHelpDialog) {
        AlertDialog(
            onDismissRequest = { showCsvHelpDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "CSV Guide",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("CSV Import Guide", fontWeight = FontWeight.Black)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "To import stock in bulk, upload a CSV file matching the column header structure below (order does not matter).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Required Columns Section
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "EXPECTED COLS (CASE-INSENSITIVE):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            listOf(
                                "imei" to "15-digit unique serial number (Required)",
                                "brand" to "e.g. Oppo, Vivo, Samsung, Realme (Required)",
                                "model" to "Model / Variant label (Required)",
                                "specs" to "RAM, storage, or configuration specs (Optional)",
                                "color" to "Device paint color name (Optional)",
                                "warehouse" to "Target warehouse 'G' or 'O' (Optional, defaults to G)"
                            ).forEach { (col, desc) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                    Column {
                                        Text(col, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    // Sample CSV Section
                    Text(
                        "SAMPLE CSV FILE STRUCTURE:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    val sampleCsvContent = """
                        imei,brand,model,specs,color,warehouse
                        862509100258102,Oppo,Reno 11,12GB 256GB,Midnight Black,G
                        862509100258203,Vivo,V29,8GB 256GB,Noble Black,O
                        862509100258305,Samsung,S24,12GB 512GB,Amber Yellow,G
                    """.trimIndent()

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = sampleCsvContent,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Copy action button
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    Button(
                        onClick = {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(sampleCsvContent))
                            Toast.makeText(context, "Sample CSV Template Copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy Template")
                            Text("Copy CSV Template", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCsvHelpDialog = false }) {
                    Text("Close Guide", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showExportCsvDialog) {
        AlertDialog(
            onDismissRequest = { showExportCsvDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = "Reports",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Warehouse Reports Hub", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 1. ACTIVE STOCK REPORT CARD
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Active Physical Stock Report", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Reflects current filtered warehouse listings", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { triggerExport(context, "stock", "csv") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("export_active_stock_csv"),
                                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { triggerExport(context, "stock", "excel") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("export_active_stock_excel"),
                                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("Excel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { triggerExport(context, "stock", "pdf") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("export_active_stock_pdf"),
                                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 2. TRANSACTION LOGS CARD
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Transaction Action History Logs", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Full warehouse ledger of stock movements", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { triggerExport(context, "transactions", "csv") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("export_transactions_csv"),
                                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { triggerExport(context, "transactions", "excel") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("export_transactions_excel"),
                                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("Excel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { triggerExport(context, "transactions", "pdf") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("export_transactions_pdf"),
                                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // 3. CATALOG CARD
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Predefined Item Spec Catalog", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Matches inventory template master database", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { triggerExport(context, "items", "csv") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("export_catalog_csv"),
                                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { triggerExport(context, "items", "excel") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("export_catalog_excel"),
                                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("Excel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { triggerExport(context, "items", "pdf") },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("export_catalog_pdf"),
                                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(12.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportCsvDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // 1. ADD / PURCHASE IN DIALOG
    if (showAddStockDialog) {
        var addBrand by remember(prefillBrand) { mutableStateOf(prefillBrand) }
        var addVariant by remember(prefillVariant) { mutableStateOf(prefillVariant) }
        var addColor by remember(prefillColor) { mutableStateOf(prefillColor) }
        var stockInRows by remember { mutableStateOf(listOf(Pair("", prefillColor))) }
        var addWh by remember { mutableStateOf("G") }
        var addDate by remember { mutableStateOf(System.currentTimeMillis()) }
        var isSubmitting by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf<String?>(null) }
        var expandedBrandDropdown by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showAddStockDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ArrowUpward, "Inward", tint = Color(0xFF4CAF50))
                    Text("Record Purchase IN", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (inputError != null) {
                        Text(
                            text = inputError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Combined Brand Dropdown and Model Variant in exactly 1 Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Brand Dropdown Selector
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { expandedBrandDropdown = true },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = addBrand,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Icon(Icons.Default.ArrowDropDown, "Select Brand Options", modifier = Modifier.size(18.dp))
                                }
                            }
                            DropdownMenu(
                                expanded = expandedBrandDropdown,
                                onDismissRequest = { expandedBrandDropdown = false }
                            ) {
                                brands.forEach { b ->
                                    DropdownMenuItem(
                                        text = { Text(b) },
                                        onClick = {
                                            addBrand = b
                                            expandedBrandDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        // Smart type-ahead autocompletion presets for Variant
                        val matchingPresets = remember(brandVariants, addBrand) {
                            brandVariants.filter { it.brand.equals(addBrand, ignoreCase = true) }
                        }
                        var showVariantSuggestions by remember { mutableStateOf(false) }
                        val typedSuggestions = remember(addVariant, matchingPresets) {
                            if (addVariant.isBlank()) {
                                matchingPresets
                            } else {
                                matchingPresets.filter {
                                    it.modelName.contains(addVariant, ignoreCase = true) ||
                                    it.specs.contains(addVariant, ignoreCase = true)
                                }
                            }
                        }

                        // Variant input with modern type-ahead dropdown
                        Box(modifier = Modifier.weight(2.2f)) {
                            OutlinedTextField(
                                value = addVariant,
                                onValueChange = {
                                    addVariant = it
                                    showVariantSuggestions = true
                                },
                                placeholder = { Text("Model Variant Specs", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp)) },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .testTag("add_brand_variant")
                            )

                            if (showVariantSuggestions && typedSuggestions.isNotEmpty()) {
                                DropdownMenu(
                                    expanded = showVariantSuggestions,
                                    onDismissRequest = { showVariantSuggestions = false },
                                    properties = androidx.compose.ui.window.PopupProperties(focusable = false),
                                    modifier = Modifier.fillMaxWidth(0.65f)
                                ) {
                                    typedSuggestions.forEach { preset ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(preset.modelName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    val detailText = if (preset.color.isBlank()) preset.specs else "${preset.specs} | ${preset.color}"
                                                    Text(detailText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, fontSize = 11.sp)
                                                }
                                            },
                                            onClick = {
                                                addVariant = "${preset.modelName} ${preset.specs}".trim()
                                                if (preset.color.isNotBlank()) {
                                                    addColor = preset.color
                                                    // Pre-fill colors of any empty row
                                                    stockInRows = stockInRows.map { 
                                                        if (it.second.isBlank() || it.second == "Unknown") Pair(it.first, preset.color) else it 
                                                    }
                                                }
                                                showVariantSuggestions = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Device Serials and Colors:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                    // Unified visual column layout representing sleek compact device records
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        stockInRows.forEachIndexed { index, row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // Unified index number indicator
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.width(20.dp)
                                )

                                // Real-time high-density IMEI Field (comfortable fit for 15/16 digits)
                                OutlinedTextField(
                                    value = row.first,
                                    onValueChange = { newVal ->
                                        if (newVal.contains(",") || newVal.contains(" ") || newVal.contains("\n")) {
                                            val split = newVal.split(Regex("[,\\s\\n]+")).map { it.trim() }.filter { it.isNotBlank() }
                                            if (split.size > 1) {
                                                stockInRows = stockInRows.toMutableList().apply {
                                                    val currentColor = this[index].second
                                                    this[index] = Pair(split[0], currentColor)
                                                    for (i in 1 until split.size) {
                                                        add(Pair(split[i], currentColor))
                                                    }
                                                }
                                            } else {
                                                stockInRows = stockInRows.toMutableList().apply {
                                                    this[index] = Pair(newVal, this[index].second)
                                                }
                                            }
                                        } else {
                                            stockInRows = stockInRows.toMutableList().apply {
                                                this[index] = Pair(newVal, this[index].second)
                                            }
                                        }
                                    },
                                    placeholder = { Text("IMEI (15-16 Digits)", fontSize = 12.sp) },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(1.8f)
                                        .height(56.dp)
                                        .testTag("add_brand_imei_$index")
                                )

                                // Direct integrated inline QR / Barcode Scanner launcher button
                                IconButton(
                                    onClick = {
                                        scannerModeInOrOut = true
                                        qrScannerCallback = { scannedResult ->
                                            stockInRows = stockInRows.toMutableList().apply {
                                                this[index] = Pair(scannedResult, this[index].second)
                                            }
                                            showQrScannerDialog = false
                                        }
                                        showQrScannerDialog = true
                                    },
                                    modifier = Modifier
                                        .height(56.dp)
                                        .width(44.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = "Scan IMEI",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Color input taking about half-row size
                                OutlinedTextField(
                                    value = row.second,
                                    onValueChange = { newVal ->
                                        stockInRows = stockInRows.toMutableList().apply {
                                            this[index] = Pair(this[index].first, newVal)
                                        }
                                    },
                                    placeholder = { Text("Color", fontSize = 12.sp) },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .height(56.dp)
                                        .testTag("add_brand_color_$index")
                                )

                                // Delete option if there are multiple serials
                                if (stockInRows.size > 1) {
                                    IconButton(
                                        onClick = {
                                            stockInRows = stockInRows.toMutableList().apply {
                                                removeAt(index)
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove item",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                    }

                    // Button to add another device row beautifully
                    TextButton(
                        onClick = {
                            val nextDefaultsColor = stockInRows.lastOrNull()?.second?.ifBlank { addColor } ?: addColor
                            stockInRows = stockInRows + Pair("", nextDefaultsColor)
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Icon(Icons.Default.Add, "Add device row", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Another Device Serial", fontSize = 13.sp)
                    }

                    // Warehouse selection radio buttons
                    Text("Target Warehouse:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = addWh == "G",
                                onClick = { addWh = "G" },
                                modifier = Modifier.testTag("wh_radio_G")
                            )
                            Text("G Warehouse")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = addWh == "O",
                                onClick = { addWh = "O" },
                                modifier = Modifier.testTag("wh_radio_O")
                            )
                            Text("O Warehouse")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val validRows = stockInRows.map { Pair(it.first.trim(), it.second.trim()) }.filter { it.first.isNotBlank() }
                        if (addVariant.isBlank() || validRows.isEmpty()) {
                            inputError = "Variant and at least one valid IMEI are required!"
                            return@Button
                        }
                        // Validate if model/variant is available in Items
                        val isValidPreset = brandVariants.any { 
                            it.brand.equals(addBrand, ignoreCase = true) && 
                            (it.modelName.equals(addVariant, ignoreCase = true) || 
                             "${it.modelName} ${it.specs}".trim().equals(addVariant, ignoreCase = true)) 
                        }
                        if (!isValidPreset) {
                            inputError = "Error: Variant '$addVariant' is not defined under Items first!"
                            return@Button
                        }

                        val proceedSubmission = {
                            isSubmitting = true
                            viewModel.addMultipleBrandStockItemsWithColors(
                                brand = addBrand,
                                variant = addVariant,
                                itemsWithColors = validRows,
                                warehouse = addWh,
                                date = addDate
                            ) { successCount, failedList ->
                                isSubmitting = false
                                if (successCount > 0) {
                                    statusMessage = "Successfully added $successCount items to Warehouse $addWh!"
                                    if (failedList.isNotEmpty()) {
                                        statusMessage += " (Skipped ${failedList.size} duplicates: ${failedList.joinToString()})"
                                    }
                                    isErrorStatus = false
                                    showAddStockDialog = false
                                } else {
                                    inputError = "Duplicate IMEI(s) found! None of the entered IMEIs could be registered."
                                }
                            }
                        }

                        // Run Luhn algorithm on each of the valid IMEIs
                        val invalidImeis = validRows.map { it.first }.filter { !AppUtils.isValidImei(it) }
                        if (invalidImeis.isNotEmpty()) {
                            pendingImeisToConfirm = invalidImeis
                            showImeiConfirmDialog = true
                            onConfirmProceedAction = {
                                proceedSubmission()
                            }
                        } else {
                            proceedSubmission()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text("Add to Stock")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddStockDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showImeiConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImeiConfirmDialog = false },
            title = { Text("Invalid IMEI(s) Detected", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The following IMEI numbers are not valid according to standard Luhn algorithm:")
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
                    Text("Go Back and Correct")
                }
            }
        )
    }

    // 2. DISPATCH / SALE OUT DIALOG
    if (showSellStockDialog) {
        var sellWh by remember { mutableStateOf("G") }
        var sellNotes by remember { mutableStateOf("") }
        var checkingImei by remember { mutableStateOf(false) }
        var isSubmitting by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf<String?>(null) }

        // Multi-IMEI match and verified items collection
        var recognizedItems by remember { mutableStateOf<List<BrandStockItem>>(emptyList()) }
        var unrecognizedImeis by remember { mutableStateOf<List<String>>(emptyList()) }

        // Find match dynamically on IMEI string update
        LaunchedEffect(sellImei) {
            val queryList = sellImei.split(Regex("[,\\s\\n]+")).map { it.trim() }.filter { it.isNotBlank() }
            if (queryList.isNotEmpty()) {
                checkingImei = true
                val matches = mutableListOf<BrandStockItem>()
                val unmatched = mutableListOf<String>()
                for (code in queryList) {
                    val item = viewModel.findBrandStockItemByImei(code)
                    if (item != null) {
                        matches.add(item)
                    } else {
                        unmatched.add(code)
                    }
                }
                recognizedItems = matches
                unrecognizedImeis = unmatched
                if (matches.isNotEmpty()) {
                    sellWh = matches[0].warehouse // Default dispatch location to first matching warehouse
                }
                checkingImei = false
            } else {
                recognizedItems = emptyList()
                unrecognizedImeis = emptyList()
                checkingImei = false
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) { showSellStockDialog = false; sellImei = "" } },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ArrowDownward, "Outward", tint = Color(0xFFE53935))
                    Text("Model Dispatch / Sale OUT", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (inputError != null) {
                        Text(
                            text = inputError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Multi-IMEI text field (full width)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sellImei,
                            onValueChange = { sellImei = it },
                            label = { Text("Device IMEI(s) to Dispatch") },
                            placeholder = { Text("Enter device IMEIs (comma, space or new line separated)") },
                            singleLine = false,
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sell_brand_imei")
                        )

                        IconButton(
                            onClick = {
                                scannerModeInOrOut = false
                                qrScannerCallback = { scannedResult ->
                                    if (sellImei.isBlank()) {
                                        sellImei = scannedResult
                                    } else {
                                        sellImei = sellImei.trim() + "\n" + scannedResult
                                    }
                                    showQrScannerDialog = false
                                }
                                showQrScannerDialog = true
                            },
                            modifier = Modifier
                                .size(52.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Scan IMEI Barcode",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Verification diagnostics feedback
                    if (checkingImei) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp))
                            Text("Searching stock records...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        if (recognizedItems.isNotEmpty()) {
                            Text(
                                text = "✔ Matched Active Stock (${recognizedItems.size}):",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            recognizedItems.forEach { item ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("IMEI: ${item.imei}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("Wh: ${item.warehouse}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("${item.brand} - ${item.variant}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Color: ${item.color}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }

                        if (unrecognizedImeis.isNotEmpty()) {
                            Text(
                                text = "⚠ Not Found in Active Inventory (${unrecognizedImeis.size}):",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = unrecognizedImeis.joinToString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Optional Notes
                    OutlinedTextField(
                        value = sellNotes,
                        onValueChange = { sellNotes = it },
                        label = { Text("Comment/Sale Notes (Optional)") },
                        singleLine = false,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sell_brand_notes")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val imeiList = sellImei.split(Regex("[,\\s\\n]+")).map { it.trim() }.filter { it.isNotBlank() }
                        if (imeiList.isEmpty()) {
                            inputError = "Please enter at least one IMEI to sell!"
                            return@Button
                        }
                        if (recognizedItems.isEmpty()) {
                            inputError = "No valid matching devices found in active stock!"
                            return@Button
                        }

                        isSubmitting = true
                        viewModel.sellMultipleBrandStockItems(
                            imeis = recognizedItems.map { it.imei },
                            warehouse = sellWh,
                            date = System.currentTimeMillis(),
                            notes = sellNotes.ifBlank { "Regular sales dispatch (Bulk)" }
                        ) { successCount, failedList ->
                            isSubmitting = false
                            if (successCount > 0) {
                                statusMessage = "Successfully dispatched $successCount device(s) outwards!"
                                val ignoredCount = imeiList.size - successCount
                                if (ignoredCount > 0) {
                                    statusMessage += " (Skipped $ignoredCount invalid/untracked IMEIs)"
                                }
                                isErrorStatus = false
                                showSellStockDialog = false
                                sellImei = ""
                            } else {
                                inputError = "Failed to sell devices. Please check that entered IMEIs correspond to actual available stock!"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    enabled = !isSubmitting && recognizedItems.isNotEmpty()
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text("Confirm Out")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSellStockDialog = false; sellImei = "" },
                    enabled = !isSubmitting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Deletion Dialogs & Scanner Overlay

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, "Warning", tint = MaterialTheme.colorScheme.error)
                    Text("Delete Stock Item?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to delete this active stock item?")
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Brand: ${item.brand}", fontWeight = FontWeight.Bold)
                            Text("Variant: ${item.variant}")
                            Text("IMEI: ${item.imei}", fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("This action is destructive and irreversible. Only authorized Admin users can perform this deletion.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentImei = item.imei
                        viewModel.deleteBrandStockItem(item.id) { success ->
                            if (success) {
                                statusMessage = "Successfully deleted stock item with IMEI: $currentImei"
                                isErrorStatus = false
                            } else {
                                statusMessage = "Failed to delete item. Please verify permissions."
                                isErrorStatus = true
                            }
                            itemToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    transactionToDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, "Warning", tint = MaterialTheme.colorScheme.error)
                    Text("Delete Transaction Log?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to delete this historical transaction entry?")
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Brand: ${tx.brand} | Type: ${tx.type}", fontWeight = FontWeight.Bold)
                            Text("Variant: ${tx.variant}")
                            Text("IMEI: ${tx.imei}", fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("Warning: Deleting transaction logs does not automatically replenish or revert current physical warehouse stock.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentImei = tx.imei
                        viewModel.deleteBrandTransaction(tx.id) { success ->
                            if (success) {
                                statusMessage = "Successfully deleted transaction log of IMEI: $currentImei"
                                isErrorStatus = false
                            } else {
                                statusMessage = "Failed to delete log. Please verify permissions."
                                isErrorStatus = true
                            }
                            transactionToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Log")
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    variantToDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { variantToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, "Warning", tint = MaterialTheme.colorScheme.error)
                    Text("Delete Item Definition?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Are you sure you want to delete this predefined item? This will remove it from the autofill options on 'Stock In'.")
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Model: ${preset.modelName}", fontWeight = FontWeight.Bold)
                            Text("Specs/Variant: ${preset.specs}")
                            if (preset.color.isNotBlank()) {
                                Text("Color: ${preset.color}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val model = preset.modelName
                        viewModel.deleteBrandVariant(preset.id) { success ->
                            if (success) {
                                statusMessage = "Successfully deleted item definition: $model"
                                isErrorStatus = false
                            } else {
                                statusMessage = "Failed to delete item definition."
                                isErrorStatus = true
                            }
                            variantToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Item")
                }
            },
            dismissButton = {
                TextButton(onClick = { variantToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddVariantDialog) {
        var presetBrand by remember { mutableStateOf(selectedBrand ?: "Oppo") }
        var presetModel by remember { mutableStateOf("") }
        var presetSpecs by remember { mutableStateOf("8GB/128GB") }
        var isPresetSubmitting by remember { mutableStateOf(false) }
        var presetError by remember { mutableStateOf<String?>(null) }
        var expandedPresetBrandDropdown by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isPresetSubmitting) showAddVariantDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Category, "Category", tint = MaterialTheme.colorScheme.primary)
                    Text("Add Items", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Save standard configurations details to instantly autofill them when recording purchases.")

                    if (presetError != null) {
                        Text(presetError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    // Brand tag selection
                    Text("Tag Model to Brand:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Box {
                        OutlinedButton(
                            onClick = { expandedPresetBrandDropdown = true },
                            modifier = Modifier.fillMaxWidth().testTag("preset_brand_dropdown_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Selected Brand:  $presetBrand")
                                Icon(Icons.Default.ArrowDropDown, "Select Brand options")
                            }
                        }
                        DropdownMenu(
                            expanded = expandedPresetBrandDropdown,
                            onDismissRequest = { expandedPresetBrandDropdown = false }
                        ) {
                            brands.forEach { b ->
                                DropdownMenuItem(
                                    text = { Text(b) },
                                    onClick = {
                                        presetBrand = b
                                        expandedPresetBrandDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = presetModel,
                        onValueChange = { presetModel = it },
                        label = { Text("Model Name (e.g., Reno 11 Pro)") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("preset_model_input")
                    )

                    OutlinedTextField(
                        value = presetSpecs,
                        onValueChange = { presetSpecs = it },
                        label = { Text("RAM / Storage (e.g., 12GB/256GB)") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("preset_specs_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (presetModel.isBlank() || presetSpecs.isBlank()) {
                            presetError = "Model name and specs/variant are required."
                            return@Button
                        }
                        isPresetSubmitting = true
                        viewModel.addBrandVariant(
                            brand = presetBrand,
                            modelName = presetModel.trim(),
                            specs = presetSpecs.trim(),
                            color = ""
                        ) { success ->
                            isPresetSubmitting = false
                            if (success) {
                                statusMessage = "Successfully created new item: '$presetModel' under $presetBrand"
                                isErrorStatus = false
                                showAddVariantDialog = false
                            } else {
                                presetError = "Could not register item. Please try again."
                            }
                        }
                    },
                    enabled = !isPresetSubmitting
                ) {
                    if (isPresetSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text("Save Item")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddVariantDialog = false },
                    enabled = !isPresetSubmitting
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    val triggerLaserScan: () -> Unit = {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            val scanner = GmsBarcodeScanning.getClient(context, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val rawValue = barcode.rawValue?.trim()
                    val isValidImei = !rawValue.isNullOrBlank() && rawValue.length >= 8 && rawValue.all { it.isLetterOrDigit() }
                    if (isValidImei) {
                        qrScannerCallback?.invoke(rawValue)
                        showQrScannerDialog = false
                        Toast.makeText(context, "Scanned: $rawValue", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Laser did not detect properly. Starting camera label scanner...", Toast.LENGTH_LONG).show()
                        try {
                            val tempFile = File.createTempFile("scan_photo_", ".jpg", context.cacheDir).apply {
                                deleteOnExit()
                            }
                            val pkg = context.packageName
                            val uri = FileProvider.getUriForFile(context, "$pkg.fileprovider", tempFile)
                            tempPhotoUri = uri
                            cameraLauncher.launch(uri)
                        } catch (t: Throwable) {
                            Toast.makeText(context, "Unable to initiate camera: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Laser scanning cancelled/failed. Starting camera label scanner...", Toast.LENGTH_LONG).show()
                    try {
                        val tempFile = File.createTempFile("scan_photo_", ".jpg", context.cacheDir).apply {
                            deleteOnExit()
                        }
                        val pkg = context.packageName
                        val uri = FileProvider.getUriForFile(context, "$pkg.fileprovider", tempFile)
                        tempPhotoUri = uri
                        cameraLauncher.launch(uri)
                    } catch (t: Throwable) {
                        Toast.makeText(context, "Unable to initiate camera: ${t.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
        } catch (t: Throwable) {
            Toast.makeText(context, "Failed to launch laser: ${t.localizedMessage}. Starting camera...", Toast.LENGTH_SHORT).show()
            try {
                val tempFile = File.createTempFile("scan_photo_", ".jpg", context.cacheDir).apply {
                    deleteOnExit()
                }
                val pkg = context.packageName
                val uri = FileProvider.getUriForFile(context, "$pkg.fileprovider", tempFile)
                tempPhotoUri = uri
                cameraLauncher.launch(uri)
            } catch (t2: Throwable) {
                Toast.makeText(context, "Unable to initiate camera: ${t2.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showQrScannerDialog) {
        LaunchedEffect(Unit) {
            triggerLaserScan()
        }
        AlertDialog(
            onDismissRequest = {
                showQrScannerDialog = false
                detectedSerials = emptyList()
                scanError = null
                isAnalyzingImage = false
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Intelligent Device Scanner",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text("Smart Box/Screen Scanner", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Standard scans can easily capture irrelevant barcodes on the product box. Use our intelligent engine to correctly locate specific identifiers, even from screenshots!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Option 1: Live laser scan
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                triggerLaserScan()
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Laser scan icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Quick Laser Barcode Scanner", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Text("Aim at a single standard barcode on the packaging box.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Option 2: Camera Capture (Box / Screen Area)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                try {
                                    val tempFile = File.createTempFile("scan_photo_", ".jpg", context.cacheDir).apply {
                                        deleteOnExit()
                                    }
                                    val pkg = context.packageName
                                    val uri = FileProvider.getUriForFile(context, "$pkg.fileprovider", tempFile)
                                    tempPhotoUri = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Unable to initiate camera: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Snap camera icon",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Snap Photo of Box Labels", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text("Snapshot multiple labels. Gemini AI auto-identifies all valid IMEIs & codes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Option 3: Choose Gallery Screenshot / Image
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                imagePickerLauncher.launch("image/*")
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Gallery selection icon",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Choose Screenshot / Image", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text("Select digital screenshot or invoice list from the phone gallery.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // Progress Loader
                    if (isAnalyzingImage) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(
                                    text = "Analyzing image with Gemini AI... Finding all 15-character IMEIs / Serials.",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Error text
                    if (scanError != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = scanError!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Detected serial cards list
                    if (detectedSerials.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Detected Numbers (Tap to Insert):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            
                            detectedSerials.forEach { serial ->
                                Card(
                                    onClick = {
                                        qrScannerCallback?.invoke(serial)
                                        showQrScannerDialog = false
                                        detectedSerials = emptyList()
                                        scanError = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Device found",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = serial,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = "Select & Autofill",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showQrScannerDialog = false
                        detectedSerials = emptyList()
                        scanError = null
                        isAnalyzingImage = false
                    }
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun BrandStockItemCard(
    item: BrandStockItem,
    isAdmin: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onTraceClick: ((String) -> Unit)? = null,
    onDispatchClick: ((String) -> Unit)? = null
) {
    val dateStr = remember(item.addedDate) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.addedDate))
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = item.brand.uppercase(),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Warehouse badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (item.warehouse.equals("G", ignoreCase = true)) Color(0xFFE0F7FA) else Color(0xFFFFF3E0)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Warehouse ${item.warehouse.uppercase()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (item.warehouse.equals("G", ignoreCase = true)) Color(0xFF006064) else Color(0xFFE65100)
                        )
                    }

                    if (isAdmin && onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete from stock",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Phone Details
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.variant,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Color: ${item.color}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            // Footer logs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "IMEI / Serial",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.imei,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        // Copy to Clipboard shortcut
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy IMEI",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(14.dp)
                                .clickable {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(item.imei))
                                    Toast.makeText(context, "IMEI Copied to Clipboard!", Toast.LENGTH_SHORT).show()
                                }
                        )
                    }
                }

                // Inline quick action shortcuts
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onTraceClick != null) {
                        SuggestionChip(
                            onClick = { onTraceClick(item.imei) },
                            label = { 
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.History, null, modifier = Modifier.size(10.dp))
                                    Text("Trace", fontSize = 10.sp)
                                }
                            },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                    if (onDispatchClick != null) {
                        SuggestionChip(
                            onClick = { onDispatchClick(item.imei) },
                            label = { 
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(10.dp))
                                    Text("Out", fontSize = 10.sp)
                                }
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                labelColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Added by ${item.addedByUser}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun BrandTransactionCard(
    tx: BrandStockTransaction,
    isAdmin: Boolean = false,
    onDelete: (() -> Unit)? = null
) {
    val dateStr = remember(tx.dateInMillis) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(tx.dateInMillis))
    }
    val isIn = tx.type.equals("IN", ignoreCase = true)
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // IN or OUT Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isIn) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isIn) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = tx.type,
                            tint = if (isIn) Color(0xFF2E7D32) else Color(0xFFC62828),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isIn) "IN (Intake)" else "OUT (Dispatch)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = if (isIn) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Warehouse badge
                    Text(
                        text = "Warehouse ${tx.warehouse.uppercase()}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isAdmin && onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp),
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete transaction",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Specs
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${tx.brand.uppercase()} - ${tx.variant}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Color: ${tx.color}  |  IMEI: ${tx.imei}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!tx.notes.isNullOrBlank()) {
                    Text(
                        text = "Notes: ${tx.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Footer Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Operator: ${tx.operator}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
