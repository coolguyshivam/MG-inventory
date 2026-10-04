package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InventoryItem
import com.example.ui.components.SmartImeiScannerDialog
import com.example.ui.viewmodel.StockViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(viewModel: StockViewModel) {
    val rawItems by viewModel.inventoryItems.collectAsStateWithLifecycle()
    val suggestedImeis = remember(rawItems) { rawItems.map { it.serialNumber } }
    var showScanner by remember { mutableStateOf(false) }
    val searchWord by viewModel.inventorySearchTerm.collectAsStateWithLifecycle()
    val activeSubTab by viewModel.inventorySubTab.collectAsStateWithLifecycle() // 0 = Inventory, 1 = Repair
    val sortOption by viewModel.inventorySortOption.collectAsStateWithLifecycle()
    val sortAscending by viewModel.inventorySortAscending.collectAsStateWithLifecycle()
    val revealedSet by viewModel.revealedPrices.collectAsStateWithLifecycle()

    val canManageInventory by viewModel.canManageInventory.collectAsStateWithLifecycle()
    val canRepair by viewModel.canRepair.collectAsStateWithLifecycle()
    val canDelete by viewModel.canDelete.collectAsStateWithLifecycle()
    val canSeePrice by viewModel.canSeePrice.collectAsStateWithLifecycle()
    val canSeePurchasePrice by viewModel.canSeePurchasePrice.collectAsStateWithLifecycle()
    val canEditPricing by viewModel.canEditPricing.collectAsStateWithLifecycle()
    val canEditItemDetails by viewModel.canEditItemDetails.collectAsStateWithLifecycle()
    val canSell by viewModel.canSell.collectAsStateWithLifecycle()

    val loggedInUser by viewModel.loggedInUser.collectAsStateWithLifecycle()
    val isAdmin = remember(loggedInUser) { loggedInUser?.role == "Admin" }
    val isManager = remember(loggedInUser) { loggedInUser?.role == "Manager" }

    var showSortMenu by remember { mutableStateOf(false) }

    // Dialog state for "Dispatch to Repair"
    var repairDispatchItem by remember { mutableStateOf<InventoryItem?>(null) }
    var technicianName by remember { mutableStateOf("") }
    var repairReason by remember { mutableStateOf("") }

    // Dialog state for "Return from Repair"
    var repairReturnItem by remember { mutableStateOf<InventoryItem?>(null) }
    var repairCostInput by remember { mutableStateOf("") }

    // Dialog state for "Edit Item"
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var editSerialNumber by remember { mutableStateOf("") }
    var editModel by remember { mutableStateOf("") }
    var editName by remember { mutableStateOf("") }
    var editAmount by remember { mutableStateOf("") }
    var editSalePrice by remember { mutableStateOf("") }
    var editMinSalePrice by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }

    var selectedPhotosForViewer by remember { mutableStateOf<List<String>?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("mobile_gallery_prefs", android.content.Context.MODE_PRIVATE) }
    var showPurchasePriceOnCards by remember {
        mutableStateOf(prefs.getBoolean("show_purchase_price_on_cards", false))
    }

    var activeDateFilter by remember { mutableStateOf("All Time") }
    // time in millis
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    // Price Filter States
    var priceFilterMode by remember { mutableStateOf("Purchase") } // "Purchase" or "Sale"
    var activePriceFilter by remember { mutableStateOf("All Prices") }
    var customMinPrice by remember { mutableStateOf<Double?>(null) }
    var customMaxPrice by remember { mutableStateOf<Double?>(null) }
    var showPriceRangeDialog by remember { mutableStateOf(false) }
    var showPurchaseFilterSubmenu by remember { mutableStateOf(false) }
    var showSaleFilterSubmenu by remember { mutableStateOf(false) }

    // Filtering & Sorting math
    val filteredItems = remember(rawItems, searchWord, activeSubTab, sortOption, sortAscending, activeDateFilter, customStartDate, customEndDate, activePriceFilter, priceFilterMode, customMinPrice, customMaxPrice) {
        var resultList = rawItems.filter { item ->
            item.isUnderRepair == (activeSubTab == 1)
        }

        // Date Filter
        if (activeDateFilter != "All Time") {
            val now = System.currentTimeMillis()
            val startThreshold = when (activeDateFilter) {
                "Today" -> now - 86400000L
                "This Week" -> now - 86400000L * 7L
                "This Month" -> now - 86400000L * 30L
                "Custom" -> customStartDate ?: 0L
                else -> 0L
            }
            val endThreshold = when (activeDateFilter) {
                "Custom" -> customEndDate ?: Long.MAX_VALUE
                else -> Long.MAX_VALUE
            }
            if (activeDateFilter == "Custom") {
                if (customStartDate != null && customEndDate != null) {
                    resultList = resultList.filter { it.dateInMillis in startThreshold..endThreshold }
                }
            } else if (startThreshold > 0) {
                resultList = resultList.filter { it.dateInMillis >= startThreshold }
            }
        }

        // Price Filter (Purchase Price or Sale Price)
        if (activePriceFilter != "All Prices") {
            resultList = resultList.filter { item ->
                val p = if (priceFilterMode == "Purchase") {
                    item.amount
                } else {
                    val effP = com.example.util.AppUtils.getEffectiveSalePrice(item)
                    if (effP > 0.0) effP else item.amount
                }
                when (activePriceFilter) {
                    "< ₹10k" -> p in 0.01..10000.0
                    "₹10k - ₹20k" -> p in 10000.0..20000.0
                    "₹20k - ₹40k" -> p in 20000.0..40000.0
                    "> ₹40k" -> p > 40000.0
                    "Custom Price" -> {
                        val min = customMinPrice ?: 0.0
                        val max = customMaxPrice ?: Double.MAX_VALUE
                        p in min..max
                    }
                    else -> true
                }
            }
        }

        // Apply Search Term (IMEI check or Model check or description check)
        if (searchWord.isNotBlank()) {
            val key = searchWord.trim().lowercase()
            resultList = resultList.filter { item ->
                item.serialNumber.lowercase().contains(key) ||
                item.model.lowercase().contains(key) ||
                item.name.lowercase().contains(key) ||
                item.phoneNumber?.lowercase()?.contains(key) == true ||
                item.description.lowercase().contains(key)
            }
        }

        // Apply Sorting List
        resultList = when (sortOption) {
            "Sale Price" -> if (sortAscending) resultList.sortedBy { val eff = com.example.util.AppUtils.getEffectiveSalePrice(it); if (eff > 0.0) eff else it.amount } else resultList.sortedByDescending { val eff = com.example.util.AppUtils.getEffectiveSalePrice(it); if (eff > 0.0) eff else it.amount }
            "Purchase Price" -> if (sortAscending) resultList.sortedBy { it.amount } else resultList.sortedByDescending { it.amount }
            "Name" -> if (sortAscending) resultList.sortedBy { it.name } else resultList.sortedByDescending { it.name }
            "Quantity" -> if (sortAscending) resultList.sortedBy { it.quantity } else resultList.sortedByDescending { it.quantity }
            "Price" -> if (sortAscending) resultList.sortedBy { val eff = com.example.util.AppUtils.getEffectiveSalePrice(it); if (eff > 0.0) eff else it.amount } else resultList.sortedByDescending { val eff = com.example.util.AppUtils.getEffectiveSalePrice(it); if (eff > 0.0) eff else it.amount }
            else -> if (sortAscending) resultList.sortedBy { it.dateInMillis } else resultList.sortedByDescending { it.dateInMillis } // default date
        }

        resultList
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Safe Search bar and Barcode integrated scanner
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchWord,
                onValueChange = { viewModel.setInventorySearchTerm(it) },
                placeholder = { Text("Search IMEI or Model...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchWord.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.setInventorySearchTerm("") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search term",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("inventory_search_bar")
            )

            IconButton(
                onClick = { showScanner = true },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("inventory_scanner_button")
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Start scanning",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Filters & Sort options dropdown
            Box {
                val hasActivePriceFilter = activePriceFilter != "All Prices"
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            if (hasActivePriceFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .background(if (hasActivePriceFilter) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface)
                        .testTag("inventory_sort_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter and Sort categories",
                            tint = if (hasActivePriceFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        if (hasActivePriceFilter) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .align(Alignment.TopEnd)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        }
                    }
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.widthIn(min = 250.dp)
                ) {
                    if (canSeePurchasePrice) {
                        Text(
                            text = "PURCHASE (MANAGER & ADMIN)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        DropdownMenuItem(
                            text = { 
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Show Purchase Price on Cards", fontSize = 12.sp)
                                    Switch(
                                        checked = showPurchasePriceOnCards,
                                        onCheckedChange = { checked ->
                                            showPurchasePriceOnCards = checked
                                            prefs.edit().putBoolean("show_purchase_price_on_cards", checked).apply()
                                        }
                                    )
                                }
                            },
                            onClick = {
                                val next = !showPurchasePriceOnCards
                                showPurchasePriceOnCards = next
                                prefs.edit().putBoolean("show_purchase_price_on_cards", next).apply()
                            },
                            leadingIcon = {
                                Icon(if (showPurchasePriceOnCards) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Filter by Purchase Price", fontWeight = if (priceFilterMode == "Purchase" && activePriceFilter != "All Prices") FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                priceFilterMode = "Purchase"
                                showPurchaseFilterSubmenu = !showPurchaseFilterSubmenu
                            },
                            leadingIcon = {
                                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = if (priceFilterMode == "Purchase" && activePriceFilter != "All Prices") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            trailingIcon = {
                                Icon(if (showPurchaseFilterSubmenu) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                            }
                        )
                        if (showPurchaseFilterSubmenu) {
                            val purchaseRanges = listOf("All Prices", "< ₹10k", "₹10k - ₹20k", "₹20k - ₹40k", "> ₹40k", "Custom Price")
                            purchaseRanges.forEach { range ->
                                DropdownMenuItem(
                                    text = { Text(if (range == "All Prices") "All Purchase Prices" else "Purchase: $range", fontSize = 12.sp) },
                                    onClick = {
                                        priceFilterMode = "Purchase"
                                        activePriceFilter = range
                                        if (range == "Custom Price") {
                                            showPriceRangeDialog = true
                                        }
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (priceFilterMode == "Purchase" && activePriceFilter == range) {
                                            Icon(Icons.Default.Check, "Active", tint = MaterialTheme.colorScheme.primary)
                                        } else {
                                            Spacer(modifier = Modifier.size(24.dp))
                                        }
                                    },
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }

                        if (canManageInventory) {
                            DropdownMenuItem(
                                text = { Text("New Inbound Purchase") },
                                onClick = {
                                    showSortMenu = false
                                    viewModel.setTab(1)
                                    viewModel.setTransactionSelection(0)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.AddShoppingCart, contentDescription = "Add Purchase", tint = Color(0xFF15803D))
                                }
                            )
                        }

                        HorizontalDivider()
                    }

                    Text(
                        text = "SALE PRICING FILTER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF15803D),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                    DropdownMenuItem(
                        text = { Text("Filter by Sale Price", fontWeight = if (priceFilterMode == "Sale" && activePriceFilter != "All Prices") FontWeight.Bold else FontWeight.Normal) },
                        onClick = {
                            priceFilterMode = "Sale"
                            showSaleFilterSubmenu = !showSaleFilterSubmenu
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Sell, contentDescription = null, tint = if (priceFilterMode == "Sale" && activePriceFilter != "All Prices") Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            Icon(if (showSaleFilterSubmenu) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                        }
                    )
                    if (showSaleFilterSubmenu) {
                        val saleRanges = listOf("All Prices", "< ₹10k", "₹10k - ₹20k", "₹20k - ₹40k", "> ₹40k", "Custom Price")
                        saleRanges.forEach { range ->
                            DropdownMenuItem(
                                text = { Text(if (range == "All Prices") "All Sale Prices" else "Sale: $range", fontSize = 12.sp) },
                                onClick = {
                                    priceFilterMode = "Sale"
                                    activePriceFilter = range
                                    if (range == "Custom Price") {
                                        showPriceRangeDialog = true
                                    }
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (priceFilterMode == "Sale" && activePriceFilter == range) {
                                        Icon(Icons.Default.Check, "Active", tint = Color(0xFF15803D))
                                    } else {
                                        Spacer(modifier = Modifier.size(24.dp))
                                    }
                                },
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }

                    HorizontalDivider()
                    Text(
                        text = "SORT BY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                    DropdownMenuItem(
                        text = { Text("Sort by Date Created") },
                        onClick = {
                            viewModel.setInventorySortOption("Date")
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (sortOption == "Date") Icon(Icons.Default.Check, "Active")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Sort by Sale Price") },
                        onClick = {
                            viewModel.setInventorySortOption("Sale Price")
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (sortOption == "Sale Price" || sortOption == "Price") Icon(Icons.Default.Check, "Active")
                        }
                    )
                    if (canSeePurchasePrice) {
                        DropdownMenuItem(
                            text = { Text("Sort by Purchase Price") },
                            onClick = {
                                viewModel.setInventorySortOption("Purchase Price")
                                showSortMenu = false
                            },
                            leadingIcon = {
                                if (sortOption == "Purchase Price") Icon(Icons.Default.Check, "Active")
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Sort by Name") },
                        onClick = {
                            viewModel.setInventorySortOption("Name")
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (sortOption == "Name") Icon(Icons.Default.Check, "Active")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Sort by Stock Quantity") },
                        onClick = {
                            viewModel.setInventorySortOption("Quantity")
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (sortOption == "Quantity") Icon(Icons.Default.Check, "Active")
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (sortAscending) "Ordering: Ascending" else "Ordering: Descending") },
                        onClick = {
                            viewModel.toggleInventorySortOrder()
                            showSortMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = "Order Toggle"
                            )
                        }
                    )
                    if (activePriceFilter != "All Prices") {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Clear Price Filter", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                activePriceFilter = "All Prices"
                                customMinPrice = null
                                customMaxPrice = null
                                showSortMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        )
                    }
                }
            }
        }

        // Horizontal scrolling Date Filters & Active Price Filter chip
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            contentPadding = PaddingValues(end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val filters = listOf("All Time", "Today", "This Week", "This Month", "Custom")
            items(filters) { filter ->
                FilterChip(
                    selected = activeDateFilter == filter,
                    onClick = {
                        activeDateFilter = filter
                        if (filter == "Custom") {
                            showDatePickerDialog = true
                        }
                    },
                    label = { Text(filter) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
            if (activeDateFilter == "Custom" && customStartDate != null && customEndDate != null) {
                item {
                    val sdf = java.text.SimpleDateFormat("dd MMM", java.util.Locale.getDefault())
                    val formatted = "${sdf.format(java.util.Date(customStartDate!!))} - ${sdf.format(java.util.Date(customEndDate!!))}"
                    AssistChip(
                        onClick = { showDatePickerDialog = true },
                        label = { Text(formatted) },
                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = "Custom Date Range", modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            // Compact Active Price Filter Chip embedded from filter menu
            if (activePriceFilter != "All Prices") {
                item {
                    val filterLabel = if (activePriceFilter == "Custom Price") {
                        val minText = customMinPrice?.let { "₹${it.toInt()}" } ?: "₹0"
                        val maxText = customMaxPrice?.let { "₹${it.toInt()}" } ?: "∞"
                        "$priceFilterMode: $minText-$maxText"
                    } else {
                        "$priceFilterMode: $activePriceFilter"
                    }
                    FilterChip(
                        selected = true,
                        onClick = {
                            activePriceFilter = "All Prices"
                            customMinPrice = null
                            customMaxPrice = null
                        },
                        label = { Text("$filterLabel ✕", fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (priceFilterMode == "Purchase") Icons.Default.ShoppingCart else Icons.Default.Sell,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (priceFilterMode == "Purchase") MaterialTheme.colorScheme.primaryContainer else Color(0xFFDCFCE7),
                            selectedLabelColor = if (priceFilterMode == "Purchase") MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFF15803D),
                            selectedLeadingIconColor = if (priceFilterMode == "Purchase") MaterialTheme.colorScheme.onPrimaryContainer else Color(0xFF15803D)
                        )
                    )
                }
            }
        }

        // Sub-tabs segmenting list to standard Inventory vs active Repair pool
        val inventoryCount = remember(rawItems) { rawItems.count { !it.isUnderRepair } }
        val repairCount = remember(rawItems) { rawItems.count { it.isUnderRepair } }

        TabRow(
            selectedTabIndex = activeSubTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeSubTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            },
            divider = { HorizontalDivider(color = Color.Transparent) }
        ) {
            Tab(
                selected = activeSubTab == 0,
                onClick = { viewModel.setInventorySubTab(0) },
                modifier = Modifier.height(48.dp),
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text("Inventory ($inventoryCount)", fontWeight = if (activeSubTab == 0) FontWeight.SemiBold else FontWeight.Medium, fontSize = 14.sp)
            }
            Tab(
                selected = activeSubTab == 1,
                onClick = { viewModel.setInventorySubTab(1) },
                modifier = Modifier.height(48.dp),
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text("Repair ($repairCount)", fontWeight = if (activeSubTab == 1) FontWeight.SemiBold else FontWeight.Medium, fontSize = 14.sp)
            }
        }

        // Items listing column
        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (activeSubTab == 0) Icons.Default.Inventory else Icons.Default.BuildCircle,
                        contentDescription = "Empty folder descriptor",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    )
                    Text(
                        text = if (activeSubTab == 0) "No active stock found in inventory." else "No items currently registered out for repair.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("inventory_items_list"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    val isRevealed = revealedSet.contains(item.id)
                    var isCardExpanded by remember { mutableStateOf(false) }

                    InventoryCardItem(
                        item = item,
                        isPriceRevealed = isRevealed,
                        isCardExpanded = isCardExpanded,
                        canManageInventory = canManageInventory,
                        isAdmin = isAdmin,
                        isManager = isManager,
                        canEditItemDetails = canEditItemDetails,
                        canRepair = canRepair,
                        canDelete = canDelete,
                        canSeePrice = canSeePurchasePrice,
                        canSeePurchasePrice = canSeePurchasePrice,
                        showPurchasePriceOnCards = showPurchasePriceOnCards,
                        canEditPricing = canEditPricing,
                        canSell = canSell,
                        onCardTapped = { isCardExpanded = !isCardExpanded },
                        onEyeToggled = { viewModel.togglePriceReveal(item.id) },
                        onEditClicked = {
                            editingItem = item
                            editSerialNumber = item.serialNumber
                            editModel = item.model
                            editName = item.name
                            editAmount = if (item.amount > 0.0) item.amount.toInt().toString() else ""
                            val effSale = com.example.util.AppUtils.getEffectiveSalePrice(item)
                            val effMin = com.example.util.AppUtils.getEffectiveMinSalePrice(item)
                            editSalePrice = if (effSale > 0.0) effSale.toInt().toString() else ""
                            editMinSalePrice = if (effMin > 0.0) effMin.toInt().toString() else ""
                            editDesc = item.description.ifBlank { "BH - \nCondition - " }
                        },
                        onRepairClicked = {
                            // If standard stock, triggers send-to-repair popup
                            // If already repair tab, triggers return-from-repair operation
                            if (!item.isUnderRepair) {
                                repairDispatchItem = item
                                technicianName = ""
                                repairReason = ""
                            } else {
                                repairReturnItem = item
                                repairCostInput = ""
                            }
                        },
                        onDeleteClicked = {
                            viewModel.deleteInventoryItem(item.id)
                        },
                        onSellClicked = {
                            viewModel.startDirectSale(item)
                        },
                        onPhotoClick = {
                            selectedPhotosForViewer = it
                        }
                    )
                }
            }
        }

        if (showScanner) {
            SmartImeiScannerDialog(
                onDismissRequest = { showScanner = false },
                onBarcodeScanned = { viewModel.setInventorySearchTerm(it) },
                suggestedImeis = suggestedImeis
            )
        }

        // Dialogue Modal for adding repair context properties (Technician & Reason)
        repairDispatchItem?.let { item ->
            AlertDialog(
                onDismissRequest = { repairDispatchItem = null },
                confirmButton = {
                    Button(
                        onClick = {
                            if (technicianName.isNotBlank() && repairReason.isNotBlank()) {
                                viewModel.markItemForRepair(item.id, technicianName, repairReason)
                                repairDispatchItem = null
                            }
                        },
                        enabled = technicianName.isNotBlank() && repairReason.isNotBlank()
                    ) {
                        Text("Send Out")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { repairDispatchItem = null }) {
                        Text("Cancel")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Build, "Assemble", tint = MaterialTheme.colorScheme.primary)
                        Text("Dispatch to Repair")
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Item: ${item.name} (${item.model})\nIMEI: ${item.serialNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        OutlinedTextField(
                            value = technicianName,
                            onValueChange = { technicianName = it },
                            label = { Text("Technician Name *") },
                            placeholder = { Text("E.g., John Miller") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = repairReason,
                            onValueChange = { repairReason = it },
                            label = { Text("Reason for Repair *") },
                            placeholder = { Text("E.g., Screen replacement, system lock") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )
        }

        // Dialogue Modal for Returning from Repair (adding repair cost optionally)
        repairReturnItem?.let { item ->
            AlertDialog(
                onDismissRequest = { repairReturnItem = null },
                confirmButton = {
                    Button(
                        onClick = {
                            val costVal = repairCostInput.toDoubleOrNull() ?: 0.0
                            viewModel.resolveRepairItem(item.id, costVal)
                            repairReturnItem = null
                        }
                    ) {
                        Text("Complete Repair")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { repairReturnItem = null }) {
                        Text("Cancel")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, "Resolve", tint = MaterialTheme.colorScheme.primary)
                        Text("Return from Repair")
                    }
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Item: ${item.name} (${item.model})\nIMEI: ${item.serialNumber}\n\nCurrent Purchase Cost: ₹${String.format("%,.2f", item.amount)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        OutlinedTextField(
                            value = repairCostInput,
                            onValueChange = { 
                                if (it.isEmpty() || it.toDoubleOrNull() != null || it.endsWith(".")) {
                                    repairCostInput = it 
                                }
                            },
                            label = { Text("Repair Cost (Optional)") },
                            placeholder = { Text("E.g., 1500") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Note: The repair cost entered will be added to the phone's purchase cost, representing the updated total inventory value of this item.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }

        // Dialogue Modal for Editing item properties and pricing
        editingItem?.let { item ->
            AlertDialog(
                onDismissRequest = { editingItem = null },
                confirmButton = {
                    Button(
                        onClick = {
                            val amountVal = editAmount.toDoubleOrNull() ?: item.amount
                            val salePriceVal = editSalePrice.toDoubleOrNull() ?: item.salePrice
                            val minSalePriceVal = editMinSalePrice.toDoubleOrNull() ?: item.minSalePrice
                            val qtyVal = 1
                            viewModel.editInventoryItem(
                                item.id,
                                item.copy(
                                    serialNumber = editSerialNumber.ifBlank { item.serialNumber },
                                    model = editModel,
                                    name = editName,
                                    amount = amountVal,
                                    salePrice = salePriceVal,
                                    minSalePrice = minSalePriceVal,
                                    description = editDesc,
                                    quantity = qtyVal
                                )
                            )
                            editingItem = null
                        }
                    ) {
                        Text("Save Changes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingItem = null }) {
                        Text("Cancel")
                    }
                },
                title = { Text("Edit Product & Pricing") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = editSerialNumber,
                            onValueChange = { editSerialNumber = it },
                            label = { Text("IMEI / Serial Number") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Name") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editModel,
                            onValueChange = { editModel = it },
                            label = { Text("Model") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (canSeePurchasePrice) {
                            OutlinedTextField(
                                value = editAmount,
                                onValueChange = { editAmount = it },
                                label = { Text("Purchase Cost (₹)") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        OutlinedTextField(
                            value = editSalePrice,
                            onValueChange = { editSalePrice = it },
                            label = { Text("Expected Sale Price (₹)") },
                            placeholder = { Text("E.g., 25000 (Optional)") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editMinSalePrice,
                            onValueChange = { editMinSalePrice = it },
                            label = { Text("Min Sale Price (₹)") },
                            placeholder = { Text("E.g., 22000 (Min price salesman can offer)") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editDesc,
                            onValueChange = { editDesc = it },
                            label = { Text("Description") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )
        }

        // Custom Price Range Dialog
        if (showPriceRangeDialog) {
            var minInput by remember { mutableStateOf(customMinPrice?.toInt()?.toString() ?: "") }
            var maxInput by remember { mutableStateOf(customMaxPrice?.toInt()?.toString() ?: "") }
            AlertDialog(
                onDismissRequest = { showPriceRangeDialog = false },
                confirmButton = {
                    Button(
                        onClick = {
                            customMinPrice = minInput.toDoubleOrNull()
                            customMaxPrice = maxInput.toDoubleOrNull()
                            activePriceFilter = "Custom Price"
                            showPriceRangeDialog = false
                        }
                    ) { Text("Apply Filter") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            customMinPrice = null
                            customMaxPrice = null
                            activePriceFilter = "All Prices"
                            showPriceRangeDialog = false
                        }
                    ) { Text("Reset") }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Sell, contentDescription = null, tint = Color(0xFF15803D))
                        Text("Filter by Price Range")
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Filter products by selling price according to customer demand:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = minInput,
                            onValueChange = { minInput = it },
                            label = { Text("Minimum Sale Price (₹)") },
                            placeholder = { Text("0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = maxInput,
                            onValueChange = { maxInput = it },
                            label = { Text("Maximum Sale Price (₹)") },
                            placeholder = { Text("E.g., 50000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            )
        }



        // FullScreen Photo Viewer
        if (selectedPhotosForViewer != null) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { selectedPhotosForViewer = null },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                val photos = selectedPhotosForViewer!!
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { photos.size })
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        coil.compose.AsyncImage(
                            model = com.example.util.AppUtils.resolveImageModel(photos[page]),
                            contentDescription = "Full Screen Photo",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }

                    // Top Bar with Close & Download Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { selectedPhotosForViewer = null },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }

                        IconButton(
                            onClick = {
                                com.example.util.AppUtils.saveImageToGallery(ctx, photos[pagerState.currentPage])
                            },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                        }
                    }
                    
                    if (photos.size > 1) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${photos.size}",
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(24.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
        
        if (showDatePickerDialog) {
            val dateRangePickerState = rememberDateRangePickerState()
            DatePickerDialog(
                onDismissRequest = { 
                    showDatePickerDialog = false 
                    if (customStartDate == null) activeDateFilter = "All Time" // revert if no date picked
                },
                confirmButton = {
                    TextButton(onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            customStartDate = start
                            // Set end date to end of day to include the entire day
                            customEndDate = end + 86399999L 
                            showDatePickerDialog = false
                        } else if (start != null) {
                            customStartDate = start
                            customEndDate = start + 86399999L
                            showDatePickerDialog = false
                        }
                    }) { Text("Confirm") }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showDatePickerDialog = false 
                        if (customStartDate == null) activeDateFilter = "All Time"
                    }) { Text("Cancel") }
                }
            ) {
                DateRangePicker(
                    state = dateRangePickerState,
                    modifier = Modifier.fillMaxWidth().height(400.dp),
                    title = { Text(text = "Select Date Range", modifier = Modifier.padding(16.dp)) }
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun InventoryCardItem(
    item: InventoryItem,
    isPriceRevealed: Boolean,
    isCardExpanded: Boolean,
    canManageInventory: Boolean,
    isAdmin: Boolean = false,
    isManager: Boolean = false,
    canEditItemDetails: Boolean = false,
    canRepair: Boolean,
    canDelete: Boolean,
    canSeePrice: Boolean,  // Rule 4 (Purchase price visibility for Admin & Manager)
    canSeePurchasePrice: Boolean = false,
    showPurchasePriceOnCards: Boolean = false,
    canEditPricing: Boolean = false,
    canSell: Boolean,      // Rule 6
    onCardTapped: () -> Unit,
    onEyeToggled: () -> Unit,
    onEditClicked: () -> Unit,
    onRepairClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
    onSellClicked: () -> Unit, // Rule 6
    onPhotoClick: (List<String>) -> Unit = {}
) {
    var expandedActionsMenu by remember { mutableStateOf(false) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current

    val formattedDate = remember(item.dateInMillis) {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date(item.dateInMillis))
    }

    val isRepair = item.isUnderRepair
    val containerBg = if (isRepair) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surface // amber-50
    val borderColor = if (isRepair) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant // amber-100 or slate-100

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardTapped() }
            .testTag("inventory_item_${item.serialNumber}")
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Photo
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isRepair) Color.White else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, if (isRepair) Color(0xFFFEF3C7) else Color.Transparent, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val firstPhoto = item.photoUri?.split(",")?.firstOrNull()
                    if (firstPhoto != null && firstPhoto.isNotBlank() && (!firstPhoto.startsWith("ic_") || firstPhoto in listOf("ic_phone_blue", "ic_phone_amber", "ic_watch", "ic_tablet"))) {
                        coil.compose.AsyncImage(
                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(com.example.util.AppUtils.resolveImageModel(firstPhoto, thumbnail = true))
                                .crossfade(true)
                                .size(240)
                                .precision(coil.size.Precision.INEXACT)
                                .build(),
                            contentDescription = "Item Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = if (isRepair) Icons.Default.BuildCircle else Icons.Default.Smartphone,
                            contentDescription = "Simulated product photo",
                            tint = if (isRepair) Color(0xFFFCD34D) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), // amber-300 or slate-300
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Info Column (IMEI & Model ALWAYS on top - Rule 8)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "Model: ${item.model}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        if (canManageInventory || canRepair || canDelete) {
                            Box {
                                IconButton(
                                    onClick = { expandedActionsMenu = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Show item action drawer",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                DropdownMenu(
                                    expanded = expandedActionsMenu,
                                    onDismissRequest = { expandedActionsMenu = false }
                                ) {
                                    if (isAdmin || isManager || canEditItemDetails || canEditPricing) {
                                        DropdownMenuItem(
                                            text = { Text("Edit details & pricing") },
                                            onClick = {
                                                expandedActionsMenu = false
                                                onEditClicked()
                                            },
                                            leadingIcon = { Icon(Icons.Default.Edit, "Modify") }
                                        )
                                    }
                                    if (canRepair) {
                                        DropdownMenuItem(
                                            text = { Text(if (!isRepair) "Mark for Repair" else "Bring Back to Inventory") },
                                            onClick = {
                                                expandedActionsMenu = false
                                                onRepairClicked()
                                            },
                                            leadingIcon = { Icon(if (!isRepair) Icons.Default.Build else Icons.Default.Inventory, "Repair toggle") }
                                        )
                                    }
                                    if (canDelete) {
                                        DropdownMenuItem(
                                            text = { Text("Delete product") },
                                            onClick = {
                                                expandedActionsMenu = false
                                                onDeleteClicked()
                                            },
                                            leadingIcon = { Icon(Icons.Default.Delete, "Remove", tint = Color.Red) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // IMEI row with long-press & copy button (Rule 16)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable {
                                clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(item.serialNumber))
                                android.widget.Toast.makeText(context, "Copied IMEI: ${item.serialNumber}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Text(
                            text = "IMEI: ${item.serialNumber}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy IMEI number",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pricing section: Sale Price is visible to all (salesman, admin, manager)
                        // Purchase Price is ONLY visible to Admin and Manager (canSeePurchasePrice)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            val effSalePrice = com.example.util.AppUtils.getEffectiveSalePrice(item)
                            val effMinSalePrice = com.example.util.AppUtils.getEffectiveMinSalePrice(item)

                            if (effSalePrice > 0.0) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = "Expected: ₹${String.format("%,.0f", effSalePrice)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF15803D) // Green for Sale Price
                                        )
                                        if (effMinSalePrice > 0.0) {
                                            Text(
                                                text = "Min: ₹${String.format("%,.0f", effMinSalePrice)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFFB45309) // Amber for Min Price
                                            )
                                        }
                                    }
                                    if (isAdmin || isManager || canEditPricing || canEditItemDetails) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit sale price",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                            modifier = Modifier.size(13.dp).clickable { onEditClicked() }
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Sale: Price not set",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                    if (isAdmin || isManager || canEditPricing || canEditItemDetails) {
                                        Text(
                                            text = "Set Price",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .clickable { onEditClicked() }
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            // Purchase Price: STRICTLY restricted to Admin and Manager only, and displayed when enabled
                            if (canSeePurchasePrice && showPurchasePriceOnCards) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (isPriceRevealed) "Buy: ₹${String.format("%,.0f", item.amount)}" else "Buy: ₹ •••••",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = if (isPriceRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle purchase pricing lock mask",
                                        modifier = Modifier.size(13.dp).clickable { onEyeToggled() },
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }

                        // Status Badge with build/tool symbol indicating repair quantities gracefully
                        if (isRepair) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .background(Color(0xFFFEF3C7), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Build,
                                    contentDescription = null,
                                    tint = Color(0xFF92400E),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "IN REPAIR [${item.quantity}]",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E) // amber-800
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .background(if (item.quantity > 0) Color(0xFFF0FDF4) else Color(0xFFFEF2F2), RoundedCornerShape(12.dp))
                                    .border(1.dp, if (item.quantity > 0) Color(0xFFDCFCE7) else Color(0xFFFEE2E2), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.quantity > 0) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                    contentDescription = null,
                                    tint = if (item.quantity > 0) Color(0xFF15803D) else Color(0xFFB91C1C),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (item.quantity > 0) "IN STOCK [${item.quantity}]" else "OUT OF STOCK",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.quantity > 0) Color(0xFF15803D) else Color(0xFFB91C1C)
                                )
                            }
                        }
                    }
                }
            }

            // Expanded details animation block (displays detailed parameters)
            AnimatedVisibility(
                visible = isCardExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Full Product Details",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    HorizontalDivider()

                    if (!item.photoUri.isNullOrBlank()) {
                        val photos = item.photoUri.split(",").filter { it.isNotBlank() && (!it.startsWith("ic_") || it in listOf("ic_phone_blue", "ic_phone_amber", "ic_watch", "ic_tablet")) }
                        if (photos.isNotEmpty()) {
                            Text("Photos (${photos.size}):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(photos.size, key = { it }) { index ->
                                    val uri = photos[index]
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                            .clickable { onPhotoClick(photos) }
                                    ) {
                                        coil.compose.AsyncImage(
                                            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                .data(com.example.util.AppUtils.resolveImageModel(uri, thumbnail = true))
                                                .crossfade(true)
                                                .size(240)
                                                .precision(coil.size.Precision.INEXACT)
                                                .build(),
                                            contentDescription = "Additional Photo $index",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Rule 8: Rest details like Product Name here in show more section
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Product Name:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    // Sale Price range in expanded details (always visible to all users)
                    val effSalePrice = com.example.util.AppUtils.getEffectiveSalePrice(item)
                    val effMinSalePrice = com.example.util.AppUtils.getEffectiveMinSalePrice(item)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Expected Sale Price:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (effSalePrice > 0.0) "₹${String.format("%,.2f", effSalePrice)}" else "Not set",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (effSalePrice > 0.0) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Min Sale Price (Floor):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (effMinSalePrice > 0.0) "₹${String.format("%,.2f", effMinSalePrice)}" else "Not set",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = if (effMinSalePrice > 0.0) Color(0xFFB45309) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (effSalePrice > 0.0 && effMinSalePrice > 0.0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Selling Range (Sale - Min):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${String.format("%,.0f", effSalePrice)} - ₹${String.format("%,.0f", effMinSalePrice)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black)
                        }
                    }

                    // Purchase Price (Only visible to Admin and Manager!)
                    if (canSeePurchasePrice) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Purchase Cost (Buy):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("₹${String.format("%,.2f", item.amount)}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Purchased On:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formattedDate, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }

                    if (!item.phoneNumber.isNullOrBlank()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Contact Phone:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.phoneNumber, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!item.aadhaarNumber.isNullOrBlank()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Aadhaar Number:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.aadhaarNumber, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (item.isUnderRepair) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Repair Log Attributes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B)
                        )
                        HorizontalDivider(color = Color(0xFFFFD54F))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Technician Assigned:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.technicianName ?: "N/A", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Reason for Issue:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(item.repairReason ?: "N/A", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (item.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Description Log:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Direct sale checker button (Rule 6)
                    if (canSell && !isRepair && item.quantity > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onSellClicked,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("direct_sale_${item.serialNumber}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.ShoppingCart, "Sell direct checkout", modifier = Modifier.size(18.dp))
                                Text("Direct Sale (Checkout)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
