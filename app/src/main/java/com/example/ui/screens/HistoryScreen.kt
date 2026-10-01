package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HistoryEvent
import com.example.ui.components.SmartImeiScannerDialog
import com.example.ui.theme.TransactionColors
import com.example.ui.viewmodel.StockViewModel
import java.text.SimpleDateFormat
import java.util.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: StockViewModel) {
    val rawEvents by viewModel.historyEvents.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val suggestedImeis = remember(rawEvents) { rawEvents.map { it.serialNumber } }
    var showScanner by remember { mutableStateOf(false) }
    val searchWord by viewModel.historySearchTerm.collectAsStateWithLifecycle()
    val typeFilter by viewModel.historyTypeFilter.collectAsStateWithLifecycle() // "All", "PURCHASE", "SALE", "REPAIR_SENT", "REPAIR_RETURNED", "RETURN", "EDIT", "DELETE"
    val sortOption by viewModel.historySortOption.collectAsStateWithLifecycle()

    var showScannerDialog by remember { mutableStateOf(false) }
    var expandedFilterMenu by remember { mutableStateOf(false) }
    var selectedPhotosForViewer by remember { mutableStateOf<List<String>?>(null) }
    var activeDateFilter by remember { mutableStateOf("All Time") }
    var customStartDate by remember { mutableStateOf<Long?>(null) }
    var customEndDate by remember { mutableStateOf<Long?>(null) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var eventToPrintCustomly by remember { mutableStateOf<HistoryEvent?>(null) }

    // Filtering & Sorting processes
    val filteredEvents = remember(rawEvents, searchWord, typeFilter, sortOption, activeDateFilter, customStartDate, customEndDate) {
        var list = rawEvents

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
                    list = list.filter { it.timestamp in startThreshold..endThreshold }
                }
            } else if (startThreshold > 0) {
                list = list.filter { it.timestamp >= startThreshold }
            }
        }

        // Apply Search (IMEI matching)
        if (searchWord.isNotBlank()) {
            val key = searchWord.trim().lowercase()
            list = list.filter { event ->
                event.serialNumber.lowercase().contains(key) ||
                event.model.lowercase().contains(key) ||
                event.name.lowercase().contains(key) ||
                event.userId.lowercase().contains(key) ||
                event.phoneNumber?.lowercase()?.contains(key) == true ||
                event.description.lowercase().contains(key)
            }
        }

        // Apply Action Type filter selection
        if (typeFilter != "All") {
            list = list.filter { event ->
                when (typeFilter) {
                    "Purchase" -> event.actionType == "PURCHASE"
                    "Sale" -> event.actionType == "SALE"
                    "Repair" -> event.actionType == "REPAIR_SENT" || event.actionType == "REPAIR_RETURNED"
                    "Return" -> event.actionType == "RETURN"
                    "Edit" -> event.actionType == "EDIT"
                    "Delete" -> event.actionType == "DELETE"
                    else -> true
                }
            }
        }

        // Apply sorting (default is latest timestamp first)
        list = when (sortOption) {
            "Oldest First" -> list.sortedBy { it.timestamp }
            "Value Out" -> list.sortedByDescending { it.amount }
            else -> list.sortedByDescending { it.timestamp } // "Newest First"
        }

        list
    }

    LaunchedEffect(filteredEvents.firstOrNull()?.id) {
        if (filteredEvents.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Safe Search input field for chronological works
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchWord,
                onValueChange = { viewModel.setHistorySearchTerm(it) },
                placeholder = { Text("Search IMEI, Action...", fontSize = 13.sp) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search history stream",
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchWord.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setHistorySearchTerm("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear entries",
                                modifier = Modifier.size(16.dp)
                            )
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
                    .height(50.dp)
                    .testTag("history_search_word")
            )

            // Tactile scanner button
            IconButton(
                onClick = { showScanner = true },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("history_scanner_button")
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Start scanning",
                    modifier = Modifier.size(18.dp)
                )
            }

            // Dropdown filter and sorting trigger
            Box {
                IconButton(
                    onClick = { expandedFilterMenu = true },
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Expand filters dropdown",
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = expandedFilterMenu,
                    onDismissRequest = { expandedFilterMenu = false }
                ) {
                    // Category Selection Filter Header
                    DropdownMenuItem(
                        text = { Text("FILTER BY ACTION", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                        onClick = {},
                        enabled = false
                    )
                    listOf("All", "Purchase", "Sale", "Repair", "Return", "Edit", "Delete").forEach { actionLabel ->
                        DropdownMenuItem(
                            text = { Text(actionLabel) },
                            onClick = {
                                viewModel.setHistoryTypeFilter(actionLabel)
                                expandedFilterMenu = false
                            },
                            leadingIcon = {
                                if (typeFilter == actionLabel) Icon(Icons.Default.Check, "Selected")
                            }
                        )
                    }

                    HorizontalDivider()

                    // Sorting Category items
                    DropdownMenuItem(
                        text = { Text("SORT SEQUENCE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                        onClick = {},
                        enabled = false
                    )
                    listOf("Newest First", "Oldest First", "Value Out").forEach { sortLabel ->
                        DropdownMenuItem(
                            text = { Text(sortLabel) },
                            onClick = {
                                viewModel.setHistorySortOption(sortLabel)
                                expandedFilterMenu = false
                            },
                            leadingIcon = {
                                if (sortOption == sortLabel) Icon(Icons.Default.Check, "Selected")
                            }
                        )
                    }
                }
            }
        }

        // Date Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
        }

        // Active filters notification pill
        if (typeFilter != "All" || sortOption != "Newest First") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (typeFilter != "All") {
                    InputChip(
                        selected = true,
                        onClick = { viewModel.setHistoryTypeFilter("All") },
                        label = { Text("Action: $typeFilter") },
                        trailingIcon = { Icon(Icons.Default.Close, "Clear Filter", modifier = Modifier.size(12.dp)) }
                    )
                }
                if (sortOption != "Newest First") {
                    InputChip(
                        selected = true,
                        onClick = { viewModel.setHistorySortOption("Newest First") },
                        label = { Text("Order: $sortOption") },
                        trailingIcon = { Icon(Icons.Default.Close, "Clear Sort", modifier = Modifier.size(12.dp)) }
                    )
                }
            }
        }

        // Continuous Audit Logs stream column listings
        if (filteredEvents.isEmpty()) {
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
                        imageVector = Icons.Default.HistoryToggleOff,
                        contentDescription = "Empty file log",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "No recorded transactions match the criteria.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("history_events_stream"),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredEvents, key = { it.id }) { event ->
                    var isExpanded by remember { mutableStateOf(false) }
                    HistoryRowItem(
                        event = event,
                        isExpanded = isExpanded,
                        onExpandTapped = { isExpanded = !isExpanded },
                        onPhotoClick = { selectedPhotosForViewer = it },
                        onPrintClick = { eventToPrintCustomly = it }
                    )
                }
            }
        }
    }

    if (showScanner) {
        SmartImeiScannerDialog(
            onDismissRequest = { showScanner = false },
            onBarcodeScanned = { viewModel.setHistorySearchTerm(it) },
            suggestedImeis = suggestedImeis
        )
    }

    // FullScreen Photo Viewer
    if (selectedPhotosForViewer != null && selectedPhotosForViewer!!.isNotEmpty()) {
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

    if (eventToPrintCustomly != null) {
        val isAdminUser = viewModel.canManageUsers.collectAsStateWithLifecycle().value
        CustomPrintDialog(
            event = eventToPrintCustomly!!,
            onDismiss = { eventToPrintCustomly = null },
            isAdmin = isAdminUser,
            viewModel = viewModel
        )
    }
}

@Composable
fun HistoryRowItem(
    event: HistoryEvent,
    isExpanded: Boolean,
    onExpandTapped: () -> Unit,
    onPhotoClick: ((List<String>) -> Unit)? = null,
    onPrintClick: (HistoryEvent) -> Unit = {}
) {
    val formattedTimestamp = remember(event.timestamp) {
        val sdf = SimpleDateFormat("dd MMM yyyy \n hh:mm a", Locale.getDefault())
        sdf.format(Date(event.timestamp))
    }

    // Determine target color based on ACTION type:
    // Purchase - blue, Sale - green, Repair - yellow, Return - light purple, Delete - red, Edit - pink
    val actionPalette = remember(event.actionType) {
        when (event.actionType) {
            "PURCHASE" -> Triple(TransactionColors.PurchaseBlue, "PURCHASED INBOUND", Icons.Default.AddShoppingCart)
            "SALE" -> Triple(TransactionColors.SaleGreen, "SOLD OUTBOUND", Icons.AutoMirrored.Filled.OfflineShare)
            "REPAIR_SENT" -> Triple(TransactionColors.RepairYellow, "SENT OUT TO REPAIR", Icons.Default.Build)
            "REPAIR_RETURNED" -> Triple(TransactionColors.RepairYellow, "REPAIRED BACK", Icons.Default.BuildCircle)
            "RETURN" -> Triple(TransactionColors.ReturnPurple, "PRODUCT RETURNED", Icons.AutoMirrored.Filled.KeyboardReturn)
            "EDIT" -> Triple(TransactionColors.EditPink, "PRODUCT EDITED", Icons.Default.Edit)
            "DELETE" -> Triple(TransactionColors.DeleteRed, "PRODUCT DELETED", Icons.Default.DeleteForever)
            else -> Triple(Color.Gray, "SYSTEM LOG", Icons.Default.History)
        }
    }

    val (themeColor, subLabel, iconVector) = actionPalette

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickable { onExpandTapped() }
            .testTag("history_event_item_${event.id}"),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = themeColor.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(10.dp)
        ) {
            // Header: Icon badge + Action type + Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Left indicator Badge
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(themeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = "Event theme icon indicator",
                        tint = themeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Title + user tag column
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = subLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = themeColor,
                        letterSpacing = 1.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, "User tag key", modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Audited: ${event.userId}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Chronological Timestamp right indicator
                Text(
                    text = formattedTimestamp,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    lineHeight = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Short detail description of log item - displays model name and IMEI; Qty removed as requested
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = event.model.ifBlank { "Unspecified Model" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "IMEI: ${event.serialNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val clipboardContext = androidx.compose.ui.platform.LocalContext.current
                        IconButton(
                            onClick = {
                                val clip = android.content.ClipData.newPlainText("IMEI", event.serialNumber)
                                val clipboardManager = clipboardContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboardManager.setPrimaryClip(clip)
                                android.widget.Toast.makeText(clipboardContext, "IMEI Copied", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(24.dp)
                                .padding(start = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy IMEI",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Pricing label
                Text(
                    text = "₹${String.format("%,.2f", event.amount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Black,
                    color = themeColor
                )
            }

            // Expanded extra parameter block details
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(themeColor.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Transaction details in expanded card view
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Model Name:", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                        Text(event.model.ifBlank { "Unspecified Model" }, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("IMEI / Serial:", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                        Text(event.serialNumber.ifBlank { "N/A" }, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Quantity:", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                        Text("${event.quantity} unit(s)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Amount / Price:", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                        Text("₹${String.format("%,.2f", event.amount)}", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Customer Name:", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                        Text(event.name.ifBlank { "N/A" }, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    if (!event.phoneNumber.isNullOrBlank()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Registered Contact:", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                            Text(event.phoneNumber, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!event.aadhaarNumber.isNullOrBlank()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Aadhaar Verification ID:", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                            Text(event.aadhaarNumber, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (event.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(event.description, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val context = androidx.compose.ui.platform.LocalContext.current

                        // WhatsApp Broadcast Button
                        IconButton(
                            onClick = {
                                val shareMessage = """
📦 *MOBILE GALLERY TRANSACTION* 📱
━━━━━━━━━━━━━━━━━━━━━━
• *Category:* ${event.actionType}
• *Model:* ${event.model}
• *IMEI/Serial:* ${event.serialNumber}
• *Customer:* ${event.name}
${if (!event.phoneNumber.isNullOrBlank()) "• *Contact:* ${event.phoneNumber}\n" else ""}• *Amount:* INR ${String.format("%,.2f", event.amount)}
• *Operator:* ${event.userId}
━━━━━━━━━━━━━━━━━━━━━━
_Logged under Mobile Gallery System_
""".trimIndent()
                                shareToWhatsApp(context, shareMessage)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share to WhatsApp",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (!event.photoUri.isNullOrBlank()) {
                            val photos = event.photoUri.split(",").filter { it.isNotBlank() && (!it.startsWith("ic_") || it in listOf("ic_phone_blue", "ic_phone_amber", "ic_watch", "ic_tablet")) }
                            if (photos.isNotEmpty()) {
                                IconButton(onClick = { onPhotoClick?.invoke(photos) }) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = "View Photos", tint = MaterialTheme.colorScheme.secondary)
                                }
                            }
                        }

                        IconButton(onClick = { onPrintClick(event) }) {
                            Icon(Icons.Default.Print, contentDescription = "Customize & Print PDF", tint = themeColor)
                        }
                    }
                }
            }
        }
    }
}

private var activePrintWebView: android.webkit.WebView? = null // Retain webview to avoid GC crash during print

fun printHistoryEvent(context: android.content.Context, event: HistoryEvent) {
    val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager
    if (printManager == null) {
        android.widget.Toast.makeText(context, "Print service not available", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val sdf = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.getDefault())
            val date = sdf.format(Date(event.timestamp))
            
            val terms = com.example.util.AppUtils.getFixedTermsForEvent(context, event.actionType)
            
            val rawPhotosList = event.photoUri?.split(",")?.filter { it.isNotBlank() && (!it.startsWith("ic_") || it in listOf("ic_phone_blue", "ic_phone_amber", "ic_watch", "ic_tablet")) } ?: emptyList()
            
            // Map each photo to an async deferred task for parallel download & decompression with dimensions
            val photoDeferreds = rawPhotosList.map { photo ->
                async {
                    com.example.util.AppUtils.loadPhotoWithDimensions(context, photo)
                }
            }
            val loadedPhotosList = photoDeferreds.map { it.await() }.filter { it.base64.isNotBlank() }
            
            val (addressVal, descVal) = extractAddressAndDescription(event.description)
            
            val samePagePhotos = loadedPhotosList.take(2)
            val nextPagePhotos = loadedPhotosList.drop(2)

            val samePageHtml = when (samePagePhotos.size) {
                0 -> ""
                1 -> {
                    val p = samePagePhotos[0]
                    val imgStyle = if (p.isLandscape) {
                        "width: 100%; max-height: 520px; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                    } else {
                        "max-width: 100%; max-height: 540px; width: auto; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                    }
                    """
                    <div style="text-align: center; margin: 6px 0; page-break-inside: avoid; width: 100%; box-sizing: border-box;">
                        <img src="${p.base64}" style="$imgStyle" />
                    </div>
                    """.trimIndent()
                }
                else -> {
                    val p1 = samePagePhotos[0]
                    val p2 = samePagePhotos[1]
                    val style1 = if (p1.isLandscape) {
                        "width: 100%; max-height: 340px; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                    } else {
                        "width: 100%; max-height: 480px; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                    }
                    val style2 = if (p2.isLandscape) {
                        "width: 100%; max-height: 340px; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                    } else {
                        "width: 100%; max-height: 480px; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                    }
                    """
                    <div style="margin: 6px 0; page-break-inside: avoid; display: flex; justify-content: space-between; align-items: center; gap: 8px; width: 100%; box-sizing: border-box;">
                        <div style="flex: 1; min-width: 0; text-align: center; box-sizing: border-box;">
                            <img src="${p1.base64}" style="$style1" />
                        </div>
                        <div style="flex: 1; min-width: 0; text-align: center; box-sizing: border-box;">
                            <img src="${p2.base64}" style="$style2" />
                        </div>
                    </div>
                    """.trimIndent()
                }
            }

            val nextPageHtml = if (nextPagePhotos.isNotEmpty()) {
                val items = nextPagePhotos.joinToString("") { photo ->
                    val itemStyle = if (photo.isLandscape) {
                        "max-width: 48%; min-width: 280px; max-height: 360px; width: 100%; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box;"
                    } else {
                        "max-width: 48%; min-width: 240px; max-height: 420px; width: auto; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box;"
                    }
                    """
                    <div style="display: inline-block; margin: 6px; text-align: center; vertical-align: middle;">
                        <img src="${photo.base64}" style="$itemStyle" />
                    </div>
                    """.trimIndent()
                }
                """
                <div class="page-break" style="page-break-before: always; break-before: page; margin-top: 16px; padding-top: 8px;">
                    <div class="header" style="border-bottom: 2px solid #222; padding-bottom: 6px; margin-bottom: 10px;">
                        <div style="font-size: 16px; font-weight: 800; text-transform: uppercase;">Photo Annexure (Page 2 - Back Page)</div>
                        <div style="font-size: 11px; color: #555;">Tx ID: ${event.id.take(8).uppercase()} | IMEI: ${event.serialNumber}</div>
                    </div>
                    <div style="text-align: center; display: flex; flex-wrap: wrap; justify-content: center; align-items: center; gap: 8px;">
                        $items
                    </div>
                </div>
                """.trimIndent()
            } else ""

            val prefs = context.getSharedPreferences("mobile_gallery_prefs", android.content.Context.MODE_PRIVATE)
            val printPrice = prefs.getBoolean("print_price_in_pdf", false)
            val pricePlaceholder = if (printPrice) {
                "₹${String.format("%,.2f", event.amount)}"
            } else {
                "________________"
            }

            val htmlDocument = """
                <!DOCTYPE html>
                <html>
                <head>
                	<meta charset="utf-8">
                    <style>
                        @page {
                            size: auto;
                            margin: 6mm 8mm;
                        }
                        body {
                            font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
                            padding: 0;
                            margin: 0 auto;
                            color: #111;
                            line-height: 1.32;
                            max-width: 100%;
                            width: 100%;
                            box-sizing: border-box;
                        }
                        .invoice-card {
                            border: 0.75pt solid #555;
                            border-radius: 4px;
                            padding: 8px 10px;
                            background: #fff;
                            box-sizing: border-box;
                            width: 100%;
                        }
                        .header {
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            border-bottom: 2pt solid #111;
                            padding-bottom: 6px;
                            margin-bottom: 10px;
                        }
                        .header-title {
                            font-size: 19px;
                            font-weight: 800;
                            text-transform: uppercase;
                            letter-spacing: 0.6px;
                            color: #111;
                        }
                        .header-meta {
                            font-size: 11px;
                            text-align: right;
                            color: #333;
                        }
                        table.details-table {
                            width: 100%;
                            border-collapse: collapse;
                            table-layout: fixed;
                            margin-bottom: 6px;
                        }
                        table.details-table td {
                            padding: 3px 5px;
                            font-size: 10px;
                            border-bottom: 0.75pt dotted #ccc;
                            word-wrap: break-word;
                            vertical-align: middle;
                        }
                        table.details-table td.label {
                            font-weight: bold;
                            color: #111;
                            background: #fdfdfd;
                            width: 20%;
                        }
                        table.details-table td.value {
                            width: 30%;
                        }
                        .terms-block {
                            font-size: 8.5px;
                            background: #fafafa;
                            border: 0.75pt solid #ddd;
                            padding: 5px 7px;
                            border-radius: 3px;
                            margin-top: 5px;
                            color: #333;
                            white-space: pre-wrap;
                            page-break-inside: avoid;
                            line-height: 1.32;
                        }
                        .footer-note {
                            font-size: 8px;
                            text-align: center;
                            margin-top: 6px;
                            color: #888;
                            font-style: italic;
                        }
                        @media print {
                            @page {
                                margin: 6mm 8mm;
                            }
                            body { padding: 0; margin: 0; max-width: 100%; width: 100%; }
                            .invoice-card {
                                border: 0.75pt solid #555 !important;
                                border-radius: 4px;
                                padding: 8px 10px !important;
                                width: 100%;
                            }
                            .page-break {
                                page-break-before: always !important;
                                break-before: page !important;
                            }
                        }
                    </style>
                </head>
                <body>
                    <div class="invoice-card">
                        <div class="header">
                            <div>
                                <div class="header-title">Mobile Gallery</div>
                                <div style="font-size: 11px; margin-top: 2px; color: #555;">Operator ID: ${event.userId}</div>
                            </div>
                            <div class="header-meta">
                                <div>Date: $date</div>
                                <div>Tx ID: ${event.id.take(8).uppercase()}</div>
                            </div>
                        </div>
    
                        <table class="details-table">
                            <colgroup>
                                <col style="width: 20%;">
                                <col style="width: 30%;">
                                <col style="width: 20%;">
                                <col style="width: 30%;">
                            </colgroup>
                            <tr>
                                <td class="label">Transaction Mode:</td>
                                <td class="value" style="font-weight: bold; color: #111;">${event.actionType}</td>
                                <td class="label">Brand & Model:</td>
                                <td class="value">${event.model.ifBlank { "________________" }}</td>
                            </tr>
                            <tr>
                                <td class="label">Customer Name:</td>
                                <td class="value">${event.name.ifBlank { "_____________________________" }}</td>
                                <td class="label">Contact Phone:</td>
                                <td class="value">${event.phoneNumber ?: "_____________________________"}</td>
                            </tr>
                            <tr>
                                <td class="label">IMEI/Serial Key:</td>
                                <td class="value" style="font-family: monospace;">${event.serialNumber.ifBlank { "________________" }}</td>
                                <td class="label">Amount / Price:</td>
                                <td class="value" style="font-weight: bold; color: #111;">$pricePlaceholder</td>
                            </tr>
                            <tr>
                                <td class="label">Audited By:</td>
                                <td class="value">${event.userId}</td>
                                <td class="label">Quantity Unit:</td>
                                <td class="value">${event.quantity} Unit(s)</td>
                            </tr>
                            <tr>
                                <td class="label">Address:</td>
                                <td class="value" colspan="3" style="white-space: pre-wrap;">${addressVal.ifBlank { "_____________________________" }}</td>
                            </tr>
                            <tr>
                                <td class="label">Remarks / Notes:</td>
                                <td class="value" colspan="3" style="white-space: pre-wrap;">${descVal.ifBlank { "No additional remarks logged." }}</td>
                            </tr>
                        </table>
    
                        <div class="terms-block">
                            $terms
                        </div>

                        $samePageHtml
    
                        <div class="footer-note">
                            Thank you for your business! | System generated via Mobile Gallery Suite.
                        </div>
                    </div>

                    $nextPageHtml
                </body>
                </html>
            """.trimIndent()

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val webView = com.example.util.AppUtils.createPrintWebView(context)
                activePrintWebView = webView

                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        try {
                            view?.let {
                                val printAdapter = it.createPrintDocumentAdapter("Transaction Receipt")
                                val jobName = "Receipt_${event.serialNumber}"
                                val printAttributes = android.print.PrintAttributes.Builder()
                                    .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                                    .setMinMargins(android.print.PrintAttributes.Margins(200, 200, 200, 200))
                                    .build()
                                printManager.print(jobName, printAdapter, printAttributes)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                webView.loadDataWithBaseURL(null, htmlDocument, "text/HTML", "UTF-8", null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Cannot generate PDF", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

fun shareToWhatsApp(context: android.content.Context, message: String) {
    try {
        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, message)
            setPackage("com.whatsapp")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(sendIntent)
    } catch (e: Exception) {
        try {
            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, message)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooserIntent = android.content.Intent.createChooser(sendIntent, "Share Updates").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooserIntent)
        } catch (ex: Exception) {
            android.widget.Toast.makeText(context, "No sharing app installed", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

fun extractAddressAndDescription(desc: String?): Pair<String, String> {
    if (desc == null) return Pair("", "")
    val trimmed = desc.trim()
    if (trimmed.startsWith("Address: ")) {
        val newlineIdx = trimmed.indexOf("\n")
        if (newlineIdx != -1) {
            val addressVal = trimmed.substring(0, newlineIdx).replace("Address: ", "").trim()
            val descVal = trimmed.substring(newlineIdx + 1).trim()
            return Pair(addressVal, descVal)
        } else {
            val addressVal = trimmed.replace("Address: ", "").trim()
            return Pair(addressVal, "")
        }
    }
    return Pair("", trimmed)
}

fun printHistoryEventCustom(
    context: android.content.Context,
    event: HistoryEvent,
    customText: String,
    samePagePhotos: List<String> = emptyList(),
    nextPagePhotos: List<String> = emptyList(),
    includeBlanks: Boolean = false,
    selectedPhotos: List<String> = emptyList(),
    placeholderCount: Int = 0,
    samePageLayout: String = "AUTO"
) {
    val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager
    if (printManager == null) {
        android.widget.Toast.makeText(context, "Print service not available", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        try {
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            val date = sdf.format(Date(event.timestamp))

            val effectiveSamePage = if (samePagePhotos.isNotEmpty() || nextPagePhotos.isNotEmpty()) {
                samePagePhotos.take(2)
            } else {
                selectedPhotos.take(2)
            }
            val effectiveNextPage = if (samePagePhotos.isNotEmpty() || nextPagePhotos.isNotEmpty()) {
                nextPagePhotos
            } else {
                selectedPhotos.drop(2)
            }

            // Map selected photos to async deferred tasks for parallel download & decompression with dimensions
            val samePageDeferreds = effectiveSamePage.map { photo ->
                async {
                    com.example.util.AppUtils.loadPhotoWithDimensions(context, photo)
                }
            }
            val nextPageDeferreds = effectiveNextPage.map { photo ->
                async {
                    com.example.util.AppUtils.loadPhotoWithDimensions(context, photo)
                }
            }
            val loadedSamePage = samePageDeferreds.map { it.await() }.filter { it.base64.isNotBlank() }
            val loadedNextPage = nextPageDeferreds.map { it.await() }.filter { it.base64.isNotBlank() }

            val declarationTitle = when (event.actionType) {
                "PURCHASE" -> "Purchase Declaration"
                "SALE" -> "Sale Declaration"
                "REPAIR_SENT", "REPAIR_RETURNED" -> "Repair Declaration"
                "RETURN" -> "Return Declaration"
                else -> "${event.actionType.replace("_", " ")} Declaration"
            }

            val (addressVal, descVal) = extractAddressAndDescription(event.description)

            val samePageHtml = when (loadedSamePage.size) {
                0 -> ""
                1 -> {
                    val p = loadedSamePage[0]
                    val imgStyle = if (p.isLandscape) {
                        "width: 100%; max-height: 520px; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                    } else {
                        "max-width: 100%; max-height: 540px; width: auto; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                    }
                    """
                    <div style="text-align: center; margin: 6px 0; page-break-inside: avoid; width: 100%; box-sizing: border-box;">
                        <img src="${p.base64}" style="$imgStyle" />
                    </div>
                    """.trimIndent()
                }
                else -> {
                    val p1 = loadedSamePage[0]
                    val p2 = loadedSamePage[1]
                    val isStacked = samePageLayout == "STACKED"
                    if (isStacked) {
                        val rowStyle1 = if (p1.isLandscape) {
                            "width: 100%; max-height: 250px; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                        } else {
                            "max-width: 100%; max-height: 250px; width: auto; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                        }
                        val rowStyle2 = if (p2.isLandscape) {
                            "width: 100%; max-height: 250px; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                        } else {
                            "max-width: 100%; max-height: 250px; width: auto; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                        }
                        """
                        <div style="margin: 6px 0; page-break-inside: avoid; display: flex; flex-direction: column; gap: 6px; width: 100%; box-sizing: border-box;">
                            <div style="width: 100%; text-align: center; box-sizing: border-box;">
                                <img src="${p1.base64}" style="$rowStyle1" />
                            </div>
                            <div style="width: 100%; text-align: center; box-sizing: border-box;">
                                <img src="${p2.base64}" style="$rowStyle2" />
                            </div>
                        </div>
                        """.trimIndent()
                    } else {
                        // SIDE_BY_SIDE or AUTO
                        val style1 = if (p1.isLandscape) {
                            "width: 100%; max-height: 340px; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                        } else {
                            "width: 100%; max-height: 480px; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                        }
                        val style2 = if (p2.isLandscape) {
                            "width: 100%; max-height: 340px; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                        } else {
                            "width: 100%; max-height: 480px; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box; display: block; margin: 0 auto;"
                        }
                        """
                        <div style="margin: 6px 0; page-break-inside: avoid; display: flex; justify-content: space-between; align-items: center; gap: 8px; width: 100%; box-sizing: border-box;">
                            <div style="flex: 1; min-width: 0; text-align: center; box-sizing: border-box;">
                                <img src="${p1.base64}" style="$style1" />
                            </div>
                            <div style="flex: 1; min-width: 0; text-align: center; box-sizing: border-box;">
                                <img src="${p2.base64}" style="$style2" />
                            </div>
                        </div>
                        """.trimIndent()
                    }
                }
            }

            val nextPageHtml = if (loadedNextPage.isNotEmpty()) {
                val items = loadedNextPage.joinToString("") { photo ->
                    val itemStyle = if (photo.isLandscape) {
                        "max-width: 48%; min-width: 280px; max-height: 360px; width: 100%; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box;"
                    } else {
                        "max-width: 48%; min-width: 240px; max-height: 420px; width: auto; height: auto; object-fit: contain; border: 0.75pt solid #666; border-radius: 3px; box-sizing: border-box;"
                    }
                    """
                    <div style="display: inline-block; margin: 6px; text-align: center; vertical-align: middle;">
                        <img src="${photo.base64}" style="$itemStyle" />
                    </div>
                    """.trimIndent()
                }
                """
                <div class="page-break" style="page-break-before: always; break-before: page; margin-top: 16px; padding-top: 8px;">
                    <div class="header" style="border-bottom: 2px solid #222; padding-bottom: 6px; margin-bottom: 10px;">
                        <div style="font-size: 16px; font-weight: 800; text-transform: uppercase;">Photo Annexure (Page 2 - Back Page)</div>
                        <div style="font-size: 11px; color: #555;">Tx ID: ${event.id.take(8).uppercase()} | IMEI: ${event.serialNumber}</div>
                    </div>
                    <div style="text-align: center; display: flex; flex-wrap: wrap; justify-content: center; align-items: center; gap: 8px;">
                        $items
                    </div>
                </div>
                """.trimIndent()
            } else ""

            val prefs = context.getSharedPreferences("mobile_gallery_prefs", android.content.Context.MODE_PRIVATE)
            val printPrice = prefs.getBoolean("print_price_in_pdf", false)
            val pricePlaceholder = if (printPrice) {
                "₹${String.format("%,.2f", event.amount)}"
            } else {
                "________________"
            }

            val htmlDocument = """
                <!DOCTYPE html>
                <html>
                <head>
                	<meta charset="utf-8">
                    <style>
                        @page {
                            size: auto;
                            margin: 6mm 8mm;
                        }
                        body {
                            font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
                            padding: 0;
                            color: #111;
                            line-height: 1.32;
                            max-width: 100%;
                            width: 100%;
                            margin: 0 auto;
                            box-sizing: border-box;
                        }
                        .invoice-card {
                            border: 0.75pt solid #555;
                            border-radius: 4px;
                            padding: 8px 10px;
                            background: #fff;
                            box-sizing: border-box;
                            width: 100%;
                        }
                        .header {
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            border-bottom: 2pt solid #222;
                            padding-bottom: 6px;
                            margin-bottom: 10px;
                        }
                        .header-title {
                            font-size: 19px;
                            font-weight: 800;
                            text-transform: uppercase;
                            letter-spacing: 0.5px;
                            color: #111;
                        }
                        .header-meta {
                            font-size: 11px;
                            text-align: right;
                            color: #444;
                        }
                        table.details-table {
                            width: 100%;
                            border-collapse: collapse;
                            table-layout: fixed;
                            margin-bottom: 6px;
                        }
                        table.details-table td {
                            padding: 3px 5px;
                            font-size: 10px;
                            border-bottom: 0.75pt dotted #ccc;
                            word-wrap: break-word;
                            vertical-align: middle;
                        }
                        table.details-table td.label {
                            font-weight: bold;
                            color: #111;
                            background: #fdfdfd;
                            width: 20%;
                        }
                        table.details-table td.value {
                            width: 30%;
                        }
                        .terms-block {
                            font-size: 8.5px;
                            margin-top: 5px;
                            color: #111;
                            white-space: pre-wrap;
                            border: 0.75pt solid #ddd;
                            border-radius: 3px;
                            padding: 5px 7px;
                            background: #fafafa;
                            page-break-inside: avoid;
                            line-height: 1.32;
                        }
                        .footer-note {
                            font-size: 8px;
                            text-align: center;
                            margin-top: 6px;
                            color: #888;
                            font-style: italic;
                        }
                        @media print {
                            @page {
                                margin: 6mm 8mm;
                            }
                            body { padding: 0; margin: 0; max-width: 100%; width: 100%; }
                            .invoice-card {
                                border: 0.75pt solid #555 !important;
                                border-radius: 4px;
                                padding: 8px 10px !important;
                                width: 100%;
                            }
                            .page-break {
                                page-break-before: always !important;
                                break-before: page !important;
                            }
                        }
                    </style>
                </head>
                <body>
                    <div class="invoice-card">
                        <div class="header">
                            <div>
                                <div class="header-title">$declarationTitle</div>
                                <div style="font-size: 11px; margin-top: 2px; color: #555;">Operator ID: ${event.userId}</div>
                            </div>
                            <div class="header-meta">
                                <div>Date: $date</div>
                                <div>Tx ID: ${event.id.take(8).uppercase()}</div>
                            </div>
                        </div>
                        
                        <table class="details-table">
                            <colgroup>
                                <col style="width: 20%;">
                                <col style="width: 30%;">
                                <col style="width: 20%;">
                                <col style="width: 30%;">
                            </colgroup>
                            <tr>
                                <td class="label">Action Mode:</td>
                                <td class="value" style="font-weight: bold; color: #111;">${event.actionType}</td>
                                <td class="label">Brand & Model:</td>
                                <td class="value">${event.model.ifBlank { "________________" }}</td>
                            </tr>
                            <tr>
                                <td class="label">Customer Name:</td>
                                <td class="value">${event.name.ifBlank { "_____________________________" }}</td>
                                <td class="label">Contact Phone:</td>
                                <td class="value">${event.phoneNumber ?: "_____________________________"}</td>
                            </tr>
                            <tr>
                                <td class="label">IMEI / Serial key:</td>
                                <td class="value" style="font-family: monospace;">${event.serialNumber.ifBlank { "________________" }}</td>
                                <td class="label">Amount / Price:</td>
                                <td class="value" style="font-weight: bold; color: #111;">$pricePlaceholder</td>
                            </tr>
                            <tr>
                                <td class="label">Aadhaar Number:</td>
                                <td class="value" style="font-family: monospace;">${event.aadhaarNumber?.ifBlank { "_____________________________" } ?: "_____________________________"}</td>
                                <td class="label">Quantity / Qty:</td>
                                <td class="value">${event.quantity} unit(s)</td>
                            </tr>
                            <tr>
                                <td class="label">Address:</td>
                                <td class="value" colspan="3" style="white-space: pre-wrap;">${addressVal.ifBlank { "_____________________________" }}</td>
                            </tr>
                            <tr>
                                <td class="label">Remarks / Notes:</td>
                                <td class="value" colspan="3" style="white-space: pre-wrap;">${descVal.ifBlank { "No additional remarks logged." }}</td>
                            </tr>
                        </table>
    
                        <div class="terms-block">
                            $customText
                        </div>
    
                        $samePageHtml

                        <div class="footer-note">
                            Thank you for your business! | System generated via Mobile Gallery Suite.
                        </div>
                    </div>

                    $nextPageHtml
                </body>
                </html>
            """.trimIndent()

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val webView = com.example.util.AppUtils.createPrintWebView(context)
                activePrintWebView = webView

                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        try {
                            view?.let {
                                val printAdapter = it.createPrintDocumentAdapter("Custom Receipt")
                                val jobName = "Custom_Receipt_${event.serialNumber}"
                                val printAttributes = android.print.PrintAttributes.Builder()
                                    .setMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4)
                                    .setMinMargins(android.print.PrintAttributes.Margins(200, 200, 200, 200))
                                    .build()
                                printManager.print(jobName, printAdapter, printAttributes)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                webView.loadDataWithBaseURL(null, htmlDocument, "text/HTML", "UTF-8", null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Cannot generate custom print doc", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPrintDialog(
    event: HistoryEvent,
    onDismiss: () -> Unit,
    isAdmin: Boolean = false,
    viewModel: StockViewModel? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fixedTerms = remember(event.actionType) {
        com.example.util.AppUtils.getFixedTermsForEvent(context, event.actionType)
    }

    var customTerms by remember(fixedTerms) { mutableStateOf(fixedTerms) }
    var samePageLayoutMode by remember { mutableStateOf("AUTO") }
    
    val photos = remember(event.photoUri) {
        event.photoUri?.split(",")?.filter { it.isNotBlank() && (!it.startsWith("ic_") || it in listOf("ic_phone_blue", "ic_phone_amber", "ic_watch", "ic_tablet")) } ?: emptyList()
    }
    
    val samePagePhotos = remember {
        mutableStateListOf<String>().apply {
            addAll(photos.take(2))
        }
    }
    val nextPagePhotos = remember {
        mutableStateListOf<String>().apply {
            if (photos.size > 2) {
                addAll(photos.drop(2))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Customize & Print Voucher", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Margin indicator card confirming standard / normal margins
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Standard Page Margins (Normal / 15mm)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Standard A4 layout margins configured for clear receipt printing.",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Terms & Conditions section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isAdmin) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Admin: Editable Terms",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            TextButton(
                                onClick = {
                                    com.example.util.AppUtils.saveFixedTermsForEvent(context, event.actionType, customTerms)
                                    viewModel?.loadTermsAndConditions(context)
                                    android.widget.Toast.makeText(context, "Saved & fixed for all users and vouchers!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save as Fixed for All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Terms fixed by Administrator (Read-Only)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customTerms,
                        onValueChange = { if (isAdmin) customTerms = it },
                        readOnly = !isAdmin,
                        label = { Text("Common Text / Terms & Conditions") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }

                if (photos.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Photo Layout (1 or 2 on Same Page, rest on Next Page):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Choose 1 or 2 images of choice to print directly on the receipt page. Additional selected images will be printed on the back/next page.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Same Page: ${samePagePhotos.size}/2") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (samePagePhotos.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                )
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text("Next Page: ${nextPagePhotos.size}") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (nextPagePhotos.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                                )
                            )
                        }

                        if (samePagePhotos.size == 2) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "2 Photos Layout on Same Page:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Auto optimizes portrait & landscape photos to fully utilize voucher space. Choose Side-by-Side or Stacked Rows.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        FilterChip(
                                            selected = samePageLayoutMode == "AUTO",
                                            onClick = { samePageLayoutMode = "AUTO" },
                                            label = { Text("Auto", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = samePageLayoutMode == "SIDE_BY_SIDE",
                                            onClick = { samePageLayoutMode = "SIDE_BY_SIDE" },
                                            label = { Text("Side-by-Side", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = samePageLayoutMode == "STACKED",
                                            onClick = { samePageLayoutMode = "STACKED" },
                                            label = { Text("Stacked Rows", fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        photos.forEach { photo ->
                            val isSamePage = samePagePhotos.contains(photo)
                            val isNextPage = nextPagePhotos.contains(photo)
                            val isSelected = isSamePage || isNextPage

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                border = if (isSamePage) {
                                    androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else if (isNextPage) {
                                    androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
                                } else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                if (checked) {
                                                    if (samePagePhotos.size < 2) {
                                                        samePagePhotos.add(photo)
                                                    } else {
                                                        nextPagePhotos.add(photo)
                                                    }
                                                } else {
                                                    samePagePhotos.remove(photo)
                                                    nextPagePhotos.remove(photo)
                                                }
                                            }
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.Gray)
                                        ) {
                                            coil.compose.AsyncImage(
                                                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                    .data(com.example.util.AppUtils.resolveImageModel(photo, thumbnail = true))
                                                    .crossfade(true)
                                                    .size(160)
                                                    .precision(coil.size.Precision.INEXACT)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (isSamePage) "Same Page (Front)" else if (isNextPage) "Next Page (Back)" else "Excluded from Print",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSamePage) MaterialTheme.colorScheme.primary else if (isNextPage) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = photo.takeLast(30),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isSelected) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            FilterChip(
                                                selected = isSamePage,
                                                onClick = {
                                                    if (!isSamePage) {
                                                        if (samePagePhotos.size >= 2) {
                                                            val moved = samePagePhotos.removeAt(samePagePhotos.size - 1)
                                                            nextPagePhotos.add(moved)
                                                            android.widget.Toast.makeText(context, "Max 2 images on same page. Moved previous image to next page.", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                        nextPagePhotos.remove(photo)
                                                        samePagePhotos.add(photo)
                                                    }
                                                },
                                                label = { Text("Same Page (Front)", fontSize = 11.sp) },
                                                leadingIcon = if (isSamePage) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                } else null,
                                                modifier = Modifier.weight(1f)
                                            )

                                            FilterChip(
                                                selected = isNextPage,
                                                onClick = {
                                                    if (!isNextPage) {
                                                        samePagePhotos.remove(photo)
                                                        nextPagePhotos.add(photo)
                                                    }
                                                },
                                                label = { Text("Next Page (Back)", fontSize = 11.sp) },
                                                leadingIcon = if (isNextPage) {
                                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                                } else null,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No snapshots attached to this transaction log.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    printHistoryEventCustom(
                        context = context,
                        event = event,
                        customText = customTerms,
                        samePagePhotos = samePagePhotos.toList(),
                        nextPagePhotos = nextPagePhotos.toList(),
                        includeBlanks = false,
                        selectedPhotos = samePagePhotos.toList() + nextPagePhotos.toList(),
                        placeholderCount = 0,
                        samePageLayout = samePageLayoutMode
                    )
                    onDismiss()
                }
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Print Receipt")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
