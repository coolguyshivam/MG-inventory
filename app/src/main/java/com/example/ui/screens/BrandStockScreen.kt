package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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

    // Dialog state
    var showAddStockDialog by remember { mutableStateOf(false) }
    var showSellStockDialog by remember { mutableStateOf(false) }
    var showAddVariantDialog by remember { mutableStateOf(false) }

    // Scanner state
    var showQrScannerDialog by remember { mutableStateOf(false) }
    var qrScannerCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }
    var scannerModeInOrOut by remember { mutableStateOf(true) } // true = In, false = Out

    // Delete confirmation dialogs
    var itemToDelete by remember { mutableStateOf<BrandStockItem?>(null) }
    var transactionToDelete by remember { mutableStateOf<BrandStockTransaction?>(null) }
    var variantToDelete by remember { mutableStateOf<com.example.data.model.BrandVariant?>(null) }
    
    // Status feedback state
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }

    // Prefill state for Stock Inwards Dialog
    var prefillBrand by remember { mutableStateOf("Oppo") }
    var prefillVariant by remember { mutableStateOf("") }
    var prefillColor by remember { mutableStateOf("") }

    // Toggle states for metrics and alerts panels
    var showHealthMetricsPanel by remember { mutableStateOf(true) }
    var showLowStockAlertsPanel by remember { mutableStateOf(true) }

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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Warehouse Stock Manager",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
                                Text("Total Stock", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$totalInStock units", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }
                            VerticalDivider(modifier = Modifier.height(30.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Warehouse G", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$gCount units", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                            VerticalDivider(modifier = Modifier.height(30.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Warehouse O", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$oCount units", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
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
                
                // --- SECTION A: STOCK HEALTH METRICS ---
                item {
                    val totalVariantsCount = variantStockCounts.size
                    val healthyCount = variantStockCounts.count { it.count >= 3 }
                    val criticalCount = variantStockCounts.count { it.count == 1 }
                    val stockoutCount = variantStockCounts.count { it.count == 0 }
                    val lowCount = variantStockCounts.count { it.count == 2 }

                    val healthPct = if (totalVariantsCount > 0) {
                        ((variantStockCounts.count { it.count >= 2 }.toFloat() / totalVariantsCount.toFloat()) * 100).toInt()
                    } else {
                        100
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("stock_health_metrics_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showHealthMetricsPanel = !showHealthMetricsPanel },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timeline,
                                        contentDescription = "Stock Health Metrics",
                                        tint = if (healthPct >= 80) Color(0xFF2E7D32) else if (healthPct >= 50) Color(0xFFEF6C00) else Color(0xFFC62828),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Stock Health Metrics",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (healthPct >= 80) Color(0xFFE8F5E9)
                                                else if (healthPct >= 50) Color(0xFFFFF3E0)
                                                else Color(0xFFFFEBEE)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "$healthPct% Healthy",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (healthPct >= 80) Color(0xFF2E7D32) else if (healthPct >= 50) Color(0xFFE65100) else Color(0xFFC62828)
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showHealthMetricsPanel) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                        contentDescription = if (showHealthMetricsPanel) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (showHealthMetricsPanel) {
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                val statusText: String
                                val statusDesc: String
                                val statusColor: Color
                                val statusBg: Color
                                if (healthPct >= 80) {
                                    statusText = "Optimal Stock Levels"
                                    statusDesc = "Most variants are adequately supplied. Keep up the regular dispatch checkups."
                                    statusColor = Color(0xFF2E7D32)
                                    statusBg = Color(0xFFE8F5E9).copy(alpha = 0.5f)
                                } else if (healthPct >= 50) {
                                    statusText = "Moderate / Fragile Levels"
                                    statusDesc = "Several models require stock replenishment soon. Look at low stock alerts below."
                                    statusColor = Color(0xFFEF6C00)
                                    statusBg = Color(0xFFFFF3E0).copy(alpha = 0.5f)
                                } else {
                                    statusText = "Critical System Shortage"
                                    statusDesc = "Urgent attention required. High number of out-of-stock and critical stockouts."
                                    statusColor = Color(0xFFC62828)
                                    statusBg = Color(0xFFFFEBEE).copy(alpha = 0.5f)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(statusBg)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(statusColor)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(statusText, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = statusColor)
                                        Text(statusDesc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Overall Inventory Health Index", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$healthPct%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
                                    }
                                    LinearProgressIndicator(
                                        progress = { healthPct / 100f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = statusColor,
                                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Card(
                                        modifier = Modifier.weight(1.0f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("Healthy (>2 left)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$healthyCount Models", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF2E7D32))
                                        }
                                    }

                                    Card(
                                        modifier = Modifier.weight(1.0f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("Low Stock (1-2)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("${lowCount + criticalCount} Models", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFFEF6C00))
                                        }
                                    }

                                    Card(
                                        modifier = Modifier.weight(1.0f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text("Out of Stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("$stockoutCount Models", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC62828))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                val inCount = transactions.count { it.type.equals("IN", ignoreCase = true) }
                                val outCount = transactions.count { it.type.equals("OUT", ignoreCase = true) }
                                val turnoverRatio = if (inCount > 0) ((outCount.toFloat() / inCount.toFloat()) * 100).toInt() else 0

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .padding(10.dp)
                                            .fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("Inventory Turnover Ratio (Sales Out / Inward IN)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(
                                                text = "Total Intake logged: $inCount units  |  Dispatched units: $outCount",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                        Text(
                                            text = "$turnoverRatio%",
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- SECTION B: LOW STOCK ALERTS ---
                item {
                    val alerts = variantStockCounts.filter { it.count < 3 }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("low_stock_alerts_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showLowStockAlertsPanel = !showLowStockAlertsPanel },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Low Stock Alerts",
                                        tint = if (alerts.isNotEmpty()) Color(0xFFE53935) else Color(0xFF4CAF50),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Low Stock Alerts & Shortages",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (alerts.isEmpty()) Color(0xFFE8F5E9)
                                                else Color(0xFFFFEBEE)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (alerts.isEmpty()) "0 Alerts" else "${alerts.size} Shortages",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (alerts.isEmpty()) Color(0xFF2E7D32) else Color(0xFFC62828)
                                        )
                                    }
                                    Icon(
                                        imageVector = if (showLowStockAlertsPanel) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                        contentDescription = if (showLowStockAlertsPanel) "Collapse" else "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (showLowStockAlertsPanel) {
                                Spacer(modifier = Modifier.height(10.dp))

                                if (alerts.isEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFE8F5E9).copy(alpha = 0.5f))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Fully Stocked",
                                            tint = Color(0xFF2E7D32),
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Perfectly Stocked!",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = Color(0xFF2E7D32)
                                            )
                                            Text(
                                                text = "All predefined model variants have healthy levels (3+ items). No stock shortages detected.",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                } else {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        alerts.forEach { alert ->
                                            val (alertText, alertColor, alertBg) = when (alert.count) {
                                                0 -> Triple("OUT OF STOCK", Color(0xFFC62828), Color(0xFFFFEBEE))
                                                1 -> Triple("ONLY 1 LEFT!", Color(0xFFE65100), Color(0xFFFFF3E0))
                                                else -> Triple("2 LEFT (Low)", Color(0xFFF57F17), Color(0xFFFFFDE7))
                                            }

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .border(1.dp, alertColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .clip(RoundedCornerShape(5.dp))
                                                            .background(alertColor)
                                                    )
                                                    Column {
                                                        Text(
                                                            text = "${alert.brand.uppercase()} - ${alert.modelName}",
                                                            fontWeight = FontWeight.Bold,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (alert.specs.isNotBlank() || alert.color.isNotBlank()) {
                                                            Text(
                                                                text = listOfNotNull(alert.specs.ifBlank { null }, alert.color.ifBlank { null }).joinToString(" | "),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontSize = 10.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                            )
                                                        }
                                                    }
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(alertBg)
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = alertText,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = alertColor
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            prefillBrand = alert.brand
                                                            prefillVariant = alert.modelName
                                                            prefillColor = alert.color
                                                            showAddStockDialog = true
                                                        },
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .testTag("quick_inward_for_${alert.modelName}"),
                                                        colors = IconButtonDefaults.iconButtonColors(
                                                            containerColor = alertColor.copy(alpha = 0.08f),
                                                            contentColor = alertColor
                                                        )
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Add,
                                                            contentDescription = "Quick stock-in purchase inwards",
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
                
                // Active Stock List Subtitle Header
                item {
                    Text(
                        text = "Current Physical Stock Inventory List",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp)
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
                    items(filteredStockItems) { item ->
                        BrandStockItemCard(item, isAdmin) {
                            itemToDelete = item
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

    // 1. ADD / PURCHASE IN DIALOG
    if (showAddStockDialog) {
        var addBrand by remember(prefillBrand) { mutableStateOf(prefillBrand) }
        var addVariant by remember(prefillVariant) { mutableStateOf(prefillVariant) }
        var addColor by remember(prefillColor) { mutableStateOf(prefillColor) }
        var addImei by remember { mutableStateOf("") }
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (inputError != null) {
                        Text(
                            text = inputError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Brand Dropdown Selector
                    Box {
                        OutlinedButton(
                            onClick = { expandedBrandDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Select Brand:  $addBrand")
                                Icon(Icons.Default.ArrowDropDown, "Select Brand options")
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

                    // Presets autocompletion
                    val matchingPresets = remember(brandVariants, addBrand) {
                        brandVariants.filter { it.brand.equals(addBrand, ignoreCase = true) }
                    }
                    var expandedPresetDropdown by remember { mutableStateOf(false) }

                    if (matchingPresets.isNotEmpty()) {
                        Box {
                            OutlinedButton(
                                onClick = { expandedPresetDropdown = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("⚡ Autofill from Saved Items (${matchingPresets.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                            }
                            DropdownMenu(
                                expanded = expandedPresetDropdown,
                                onDismissRequest = { expandedPresetDropdown = false },
                                modifier = Modifier.fillMaxWidth(0.7f)
                            ) {
                                matchingPresets.forEach { preset ->
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
                                            }
                                            expandedPresetDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "💡 Tip: Go to the 'Items' tab to save items of $addBrand and autofill these instantly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    // Variant input
                    OutlinedTextField(
                        value = addVariant,
                        onValueChange = { addVariant = it },
                        label = { Text("Model Variant (e.g. Reno 11 Pro 12GB/256GB)") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_brand_variant")
                    )

                    // Color with option to enter in front of unique IMEI/Serial Number
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = addColor,
                            onValueChange = { addColor = it },
                            label = { Text("Color") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(0.44f).testTag("add_brand_color")
                        )

                        OutlinedTextField(
                            value = addImei,
                            onValueChange = { addImei = it.trim() },
                            label = { Text("IMEI / Serial") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(0.56f).testTag("add_brand_imei")
                        )

                        IconButton(
                            onClick = {
                                scannerModeInOrOut = true
                                qrScannerCallback = { scannedResult ->
                                    addImei = scannedResult
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
                        if (addVariant.isBlank() || addColor.isBlank() || addImei.isBlank()) {
                            inputError = "All fields (Variant, Color, IMEI) are required!"
                            return@Button
                        }
                        // Validate if model/variant is available in Items
                        val isValidPreset = brandVariants.any { 
                            it.brand.equals(addBrand, ignoreCase = true) && 
                            (it.modelName.equals(addVariant, ignoreCase = true) || 
                             "${it.modelName} ${it.specs}".trim().equals(addVariant, ignoreCase = true)) 
                        }
                        if (!isValidPreset) {
                            inputError = "Error: Variant '$addVariant' is not defined in inventory Items of $addBrand. It must be defined under Items first!"
                            return@Button
                        }
                        isSubmitting = true
                        viewModel.addBrandStockItem(
                            brand = addBrand,
                            variant = addVariant,
                            color = addColor,
                            imei = addImei,
                            warehouse = addWh,
                            date = addDate
                        ) { success ->
                            isSubmitting = false
                            if (success) {
                                statusMessage = "Successfully IN-taken phone IMEI: $addImei to Warehouse $addWh!"
                                isErrorStatus = false
                                showAddStockDialog = false
                            } else {
                                inputError = "Duplicate IMEI found in inventory! Please verify IMEI has not already been stock in."
                            }
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

    // 2. DISPATCH / SALE OUT DIALOG
    if (showSellStockDialog) {
        var sellImei by remember { mutableStateOf("") }
        var sellWh by remember { mutableStateOf("G") }
        var sellNotes by remember { mutableStateOf("") }
        var matchingItem by remember { mutableStateOf<BrandStockItem?>(null) }
        var checkingImei by remember { mutableStateOf(false) }
        var isSubmitting by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf<String?>(null) }

        // Find match on IMEI update
        LaunchedEffect(sellImei) {
            val query = sellImei.trim()
            if (query.length >= 4) {
                checkingImei = true
                val fetched = viewModel.findBrandStockItemByImei(query)
                matchingItem = fetched
                if (fetched != null) {
                    sellWh = fetched.warehouse // automatically switch to where it is stored
                    inputError = null
                }
                checkingImei = false
            } else {
                matchingItem = null
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showSellStockDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ArrowDownward, "Outward", tint = Color(0xFFE53935))
                    Text("Model Dispatch / Sale OUT", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (inputError != null) {
                        Text(
                            text = inputError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // IMEI field with QR scanner shortcut
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = sellImei,
                            onValueChange = { sellImei = it.trim() },
                            label = { Text("Enter/Scan unique device IMEI") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).testTag("sell_brand_imei")
                        )

                        IconButton(
                            onClick = {
                                scannerModeInOrOut = false
                                qrScannerCallback = { scannedResult ->
                                    sellImei = scannedResult
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

                    // Autocomplete indicators
                    if (checkingImei) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp))
                            Text("Searching active stock...", style = MaterialTheme.typography.bodySmall)
                        }
                    } else if (matchingItem != null) {
                        val item = matchingItem!!
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Match Found:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                    Text("Warehouse: ${item.warehouse}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Text("Device: ${item.brand} - ${item.variant}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Color: ${item.color}", style = MaterialTheme.typography.bodySmall)
                                Text("Operator In: ${item.addedByUser}", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                            }
                        }
                    } else if (sellImei.length >= 4) {
                        Text(
                            text = "⚠ IMEI not found in active inventory. Please verify code.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Stored warehouse details mapped automatically
                    if (matchingItem != null) {
                        LaunchedEffect(matchingItem) {
                            sellWh = matchingItem!!.warehouse
                        }
                        Text(
                            text = "Mapped Warehouse for Dispatch: Warehouse $sellWh",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Optional Notes
                    OutlinedTextField(
                        value = sellNotes,
                        onValueChange = { sellNotes = it },
                        label = { Text("Comment/Sale Notes (Optional)") },
                        singleLine = false,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("sell_brand_notes")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (matchingItem == null) {
                            inputError = "You can only dispatch details of a valid IMEI from active stock!"
                            return@Button
                        }
                        // Validate if variant is defined in pre-existing Items
                        val targetItem = matchingItem!!
                        val isValidPresetForSell = brandVariants.any {
                            it.brand.equals(targetItem.brand, ignoreCase = true) &&
                            (it.modelName.equals(targetItem.variant, ignoreCase = true) ||
                             "${it.modelName} ${it.specs}".trim().equals(targetItem.variant, ignoreCase = true))
                        }
                        if (!isValidPresetForSell) {
                            inputError = "Error: Variant '${targetItem.variant}' is not defined in inventory Items. Cannot sell."
                            return@Button
                        }

                        isSubmitting = true
                        viewModel.sellBrandStockItem(
                            imei = sellImei,
                            warehouse = sellWh,
                            date = System.currentTimeMillis(),
                            notes = sellNotes.ifBlank { "Regular sales dispatch" }
                        ) { success ->
                            isSubmitting = false
                            if (success) {
                                statusMessage = "Successfully dispatched model with IMEI: $sellImei (Out from Warehouse $sellWh)"
                                isErrorStatus = false
                                showSellStockDialog = false
                            } else {
                                inputError = "Failed to dispatch out device. Please check connection and try again."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    enabled = !isSubmitting && matchingItem != null
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
                    onClick = { showSellStockDialog = false },
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

    if (showQrScannerDialog) {
        var manualText by remember { mutableStateOf("") }
        var simulationProgress by remember { mutableStateOf(false) }

        val startProductionScanner = {
            try {
                val options = GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                    .build()
                val scanner = GmsBarcodeScanning.getClient(context, options)
                scanner.startScan()
                    .addOnSuccessListener { barcode ->
                        val rawValue = barcode.rawValue
                        if (!rawValue.isNullOrBlank()) {
                            qrScannerCallback?.invoke(rawValue)
                            showQrScannerDialog = false
                        } else {
                            Toast.makeText(context, "No barcode detected.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Live scan failed or play services code scanner not configured. Please use simulated scanner / manual entry.", Toast.LENGTH_LONG).show()
                    }
            } catch (t: Throwable) {
                Toast.makeText(context, "Live scan not available in this environment. Please use simulated scan.", Toast.LENGTH_LONG).show()
            }
        }

        AlertDialog(
            onDismissRequest = { showQrScannerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.QrCode, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Intelligent Barcode Scanner", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Position the smartphone barcode / QR code or IMEI label inside the viewfinder rectangle below.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )

                    // Viewfinder box representing camera feed
                    Box(
                        modifier = Modifier
                            .size(200.dp, 120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Active scanning grid",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(48.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Color(0xFF4CAF50))
                                .align(Alignment.Center)
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = { startProductionScanner() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth().testTag("btn_trigger_real_scan")
                        ) {
                            Icon(Icons.Default.CameraAlt, "Camera")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan with Camera (Real Device)")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Text("Or, key in IMEI/Serial code manually here:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = manualText,
                        onValueChange = { manualText = it },
                        placeholder = { Text("e.g. 869304859203847") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("manual_imei_scanner_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (manualText.isNotBlank()) {
                            qrScannerCallback?.invoke(manualText.trim())
                            showQrScannerDialog = false
                        }
                    },
                    enabled = manualText.isNotBlank()
                ) {
                    Text("Apply Code")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrScannerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BrandStockItemCard(
    item: BrandStockItem,
    isAdmin: Boolean = false,
    onDelete: (() -> Unit)? = null
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
                Column {
                    Text(
                        text = "IMEI / Serial",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Text(
                        text = item.imei,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "By ${item.addedByUser}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
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
