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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
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
import com.example.ui.components.BarcodeScannerMockDialog
import com.example.ui.viewmodel.StockViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(viewModel: StockViewModel) {
    val rawItems by viewModel.inventoryItems.collectAsState()
    val suggestedImeis = remember(rawItems) { rawItems.map { it.serialNumber } }
    var showScanner by remember { mutableStateOf(false) }
    val searchWord by viewModel.inventorySearchTerm.collectAsState()
    val activeSubTab by viewModel.inventorySubTab.collectAsState() // 0 = Inventory, 1 = Repair
    val sortOption by viewModel.inventorySortOption.collectAsState()
    val sortAscending by viewModel.inventorySortAscending.collectAsState()
    val revealedSet by viewModel.revealedPrices.collectAsState()

    val canManageInventory by viewModel.canManageInventory.collectAsState()
    val canRepair by viewModel.canRepair.collectAsState()
    val canDelete by viewModel.canDelete.collectAsState()
    val canSeePrice by viewModel.canSeePrice.collectAsState()
    val canSell by viewModel.canSell.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }

    // Dialog state for "Dispatch to Repair"
    var repairDispatchItem by remember { mutableStateOf<InventoryItem?>(null) }
    var technicianName by remember { mutableStateOf("") }
    var repairReason by remember { mutableStateOf("") }

    // Dialog state for "Edit Item"
    var editingItem by remember { mutableStateOf<InventoryItem?>(null) }
    var editModel by remember { mutableStateOf("") }
    var editName by remember { mutableStateOf("") }
    var editAmount by remember { mutableStateOf("") }
    var editDesc by remember { mutableStateOf("") }
    var editQty by remember { mutableStateOf("1") }

    var selectedPhotosForViewer by remember { mutableStateOf<List<String>?>(null) }

    // Filtering & Sorting math
    val filteredItems = remember(rawItems, searchWord, activeSubTab, sortOption, sortAscending) {
        var resultList = rawItems.filter { item ->
            // Filter by Sub Tab first:
            // Sub tab 0 is standard active stock (isUnderRepair = false)
            // Sub tab 1 is repair pool (isUnderRepair = true)
            item.isUnderRepair == (activeSubTab == 1)
        }

        // Apply Search Term (IMEI check or Model check or description check)
        if (searchWord.isNotBlank()) {
            val key = searchWord.trim().lowercase()
            resultList = resultList.filter { item ->
                item.serialNumber.lowercase().contains(key) ||
                item.model.lowercase().contains(key) ||
                item.name.lowercase().contains(key)
            }
        }

        // Apply Sorting List
        resultList = when (sortOption) {
            "Name" -> if (sortAscending) resultList.sortedBy { it.name } else resultList.sortedByDescending { it.name }
            "Quantity" -> if (sortAscending) resultList.sortedBy { it.quantity } else resultList.sortedByDescending { it.quantity }
            "Price" -> if (sortAscending) resultList.sortedBy { it.amount } else resultList.sortedByDescending { it.amount }
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
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .testTag("inventory_sort_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filter and Sort categories",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
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
                    DropdownMenuItem(
                        text = { Text("Sort by Purchase Price") },
                        onClick = {
                            viewModel.setInventorySortOption("Price")
                            showSortMenu = false
                        },
                        leadingIcon = {
                            if (sortOption == "Price") Icon(Icons.Default.Check, "Active")
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
                        canRepair = canRepair,
                        canDelete = canDelete,
                        canSeePrice = canSeePrice,
                        canSell = canSell,
                        onCardTapped = { isCardExpanded = !isCardExpanded },
                        onEyeToggled = { viewModel.togglePriceReveal(item.id) },
                        onEditClicked = {
                            editingItem = item
                            editModel = item.model
                            editName = item.name
                            editAmount = item.amount.toString()
                            editDesc = item.description
                            editQty = item.quantity.toString()
                        },
                        onRepairClicked = {
                            // If standard stock, triggers send-to-repair popup
                            // If already repair tab, triggers return-from-repair operation
                            if (!item.isUnderRepair) {
                                repairDispatchItem = item
                                technicianName = ""
                                repairReason = ""
                            } else {
                                viewModel.resolveRepairItem(item.id)
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
            BarcodeScannerMockDialog(
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

        // Dialogue Modal for Editing item properties
        editingItem?.let { item ->
            AlertDialog(
                onDismissRequest = { editingItem = null },
                confirmButton = {
                    Button(
                        onClick = {
                            val amountVal = editAmount.toDoubleOrNull() ?: item.amount
                            val qtyVal = editQty.toIntOrNull() ?: item.quantity
                            viewModel.editInventoryItem(
                                item.id,
                                item.copy(
                                    model = editModel,
                                    name = editName,
                                    amount = amountVal,
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
                title = { Text("Edit Product Attributes") },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
                        OutlinedTextField(
                            value = editAmount,
                            onValueChange = { editAmount = it },
                            label = { Text("Purchase Price") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = editQty,
                            onValueChange = { editQty = it },
                            label = { Text("Quantity") },
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
                            model = photos[page],
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
                                android.widget.Toast.makeText(ctx, "Downloading photo ${pagerState.currentPage + 1}...", android.widget.Toast.LENGTH_SHORT).show()
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
    }
}

@Composable
fun InventoryCardItem(
    item: InventoryItem,
    isPriceRevealed: Boolean,
    isCardExpanded: Boolean,
    canManageInventory: Boolean,
    canRepair: Boolean,
    canDelete: Boolean,
    canSeePrice: Boolean,  // Rule 4
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
                    if (firstPhoto != null && firstPhoto.isNotBlank() && !firstPhoto.startsWith("ic_")) {
                        coil.compose.AsyncImage(
                            model = firstPhoto,
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
                                    if (canManageInventory) {
                                        DropdownMenuItem(
                                            text = { Text("Edit details") },
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
                        // Display price only if role allows (Rule 4)
                        if (canSeePrice) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isPriceRevealed) "₹${String.format("%,.0f", item.amount)}" else "₹ •••••",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Icon(
                                    imageVector = if (isPriceRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle pricing lock mask",
                                    modifier = Modifier.size(16.dp).clickable { onEyeToggled() },
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            // Blank spacers if price is hidden for Operators/MIS/Sales
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Status Badge
                        if (isRepair) {
                            Text(
                                text = "IN REPAIR",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E), // amber-800
                                modifier = Modifier
                                    .background(Color(0xFFFEF3C7), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        } else {
                            Text(
                                text = if (item.quantity > 0) "IN STOCK (${item.quantity})" else "OUT OF STOCK",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D), // green-700
                                modifier = Modifier
                                    .background(Color(0xFFF0FDF4), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFDCFCE7), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
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
                        val photos = item.photoUri.split(",").filter { it.isNotBlank() && !it.startsWith("ic_") }
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
                                            model = uri,
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
