package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandStockScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val stockItems by viewModel.brandStockItems.collectAsStateWithLifecycle()
    val transactions by viewModel.brandStockTransactions.collectAsStateWithLifecycle()
    val currentUser by viewModel.loggedInUser.collectAsStateWithLifecycle()

    var activeSubTab by remember { mutableStateOf(0) } // 0 = Active Stock, 1 = Audit Transaction Logs
    
    // Filters
    val brands = listOf("Oppo", "Vivo", "Samsung", "OnePlus", "Realme", "Motorola", "Infinix", "Tecno", "Apple", "Google", "Redmi", "Others")
    var selectedBrand by remember { mutableStateOf<String?>(null) } // null = All Brands
    var selectedWarehouse by remember { mutableStateOf("Combined") } // "Combined", "G", "O"
    var searchQuery by remember { mutableStateOf("") }

    // Dialog state
    var showAddStockDialog by remember { mutableStateOf(false) }
    var showSellStockDialog by remember { mutableStateOf(false) }
    
    // Status feedback state
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }

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
                        text = { Text("Transit / Audit Logs", fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Default.ReceiptLong, "Audit logs transaction list") }
                    )
                }
            }

            // Brand selection chips scroll
            item {
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedBrand == null,
                        onClick = { selectedBrand = null },
                        label = { Text("All Brands") },
                        leadingIcon = if (selectedBrand == null) {
                            { Icon(Icons.Default.Check, "Selected", modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.testTag("brand_chip_all")
                    )
                    
                    brands.forEach { brandName ->
                        val isSelected = selectedBrand == brandName
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedBrand = brandName },
                            label = { Text(brandName) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Default.Check, "Selected", modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.testTag("brand_chip_$brandName")
                        )
                    }
                }
            }

            // Warehouse selector row (make filter both or single warehouse)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Warehouse:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    val warehouses = listOf("Combined", "G", "O")
                    warehouses.forEach { wh ->
                        val isSelected = selectedWarehouse == wh
                        ElevatedAssistChip(
                            onClick = { selectedWarehouse = wh },
                            label = {
                                Text(
                                    text = if (wh == "Combined") "Both Warehouses" else "Warehouse $wh",
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = AssistChipDefaults.elevatedAssistChipColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                labelColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            ),
                            border = if (isSelected) BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary) else null,
                            modifier = Modifier.testTag("warehouse_filter_chip_$wh")
                        )
                    }
                }
            }

            // Search Bar input field
            item {
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
                        .fillMaxWidth()
                        .testTag("brand_stock_search_bar")
                )
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

            // Action Buttons Row (IN-Flow & OUT-Flow)
            item {
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
                        BrandStockItemCard(item)
                    }
                }
            } else {
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
                        BrandTransactionCard(tx)
                    }
                }
            }
        }
    }

    // ================== DIALOGS ==================

    // 1. ADD / PURCHASE IN DIALOG
    if (showAddStockDialog) {
        var addBrand by remember { mutableStateOf("Oppo") }
        var addVariant by remember { mutableStateOf("") }
        var addColor by remember { mutableStateOf("") }
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

                    // Variant input
                    OutlinedTextField(
                        value = addVariant,
                        onValueChange = { addVariant = it },
                        label = { Text("Model Variant (e.g. 8GB/128GB)") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_brand_variant")
                    )

                    // Color input
                    OutlinedTextField(
                        value = addColor,
                        onValueChange = { addColor = it },
                        label = { Text("Color (e.g. Midnight Black)") },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_brand_color")
                    )

                    // IMEI input
                    OutlinedTextField(
                        value = addImei,
                        onValueChange = { addImei = it.trim() },
                        label = { Text("Unique IMEI / Serial Number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("add_brand_imei")
                    )

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

                    // IMEI field
                    OutlinedTextField(
                        value = sellImei,
                        onValueChange = { sellImei = it.trim() },
                        label = { Text("Enter/Scan unique device IMEI") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("sell_brand_imei")
                    )

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

                    // Warehouse Sold from
                    Text("Warehouse sold from:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = sellWh == "G",
                                onClick = { sellWh = "G" },
                                modifier = Modifier.testTag("sell_wh_radio_G"),
                                enabled = matchingItem == null // if item found, force its warehouse
                            )
                            Text("Warehouse G")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = sellWh == "O",
                                onClick = { sellWh = "O" },
                                modifier = Modifier.testTag("sell_wh_radio_O"),
                                enabled = matchingItem == null // if item found, force its warehouse
                            )
                            Text("Warehouse O")
                        }
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
}

@Composable
fun BrandStockItemCard(item: BrandStockItem) {
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
fun BrandTransactionCard(tx: BrandStockTransaction) {
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

                // Warehouse badge
                Text(
                    text = "Warehouse ${tx.warehouse.uppercase()}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
