package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.ui.components.BarcodeScannerMockDialog
import com.example.ui.theme.TransactionColors
import com.example.ui.viewmodel.StockViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: StockViewModel) {
    val rawEvents by viewModel.historyEvents.collectAsState()
    val suggestedImeis = remember(rawEvents) { rawEvents.map { it.serialNumber } }
    var showScanner by remember { mutableStateOf(false) }
    val searchWord by viewModel.historySearchTerm.collectAsState()
    val typeFilter by viewModel.historyTypeFilter.collectAsState() // "All", "PURCHASE", "SALE", "REPAIR_SENT", "REPAIR_RETURNED", "RETURN", "EDIT", "DELETE"
    val sortOption by viewModel.historySortOption.collectAsState()

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
                event.userId.lowercase().contains(key)
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
        BarcodeScannerMockDialog(
            onDismissRequest = { showScanner = false },
            onBarcodeScanned = { viewModel.setHistorySearchTerm(it) },
            suggestedImeis = suggestedImeis
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
        CustomPrintDialog(
            event = eventToPrintCustomly!!,
            onDismiss = { eventToPrintCustomly = null }
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

            // Short detail description of log item
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 2.dp)
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
                    Text(
                        text = "Qty: ${event.quantity} units",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    // removed Complete Transaction Footprint header
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Model Name:", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                        Text(event.model, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Customer Name:", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                        Text(event.name, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                🚀 *STUDIO LENS LEDGER TRANSACTION* 📱
                                ━━━━━━━━━━━━━━━━━━━━━━
                                • *Category:* ${event.actionType}
                                • *Device brand & model:* ${event.model}
                                • *IMEI / Serial Key:* ${event.serialNumber}
                                • *Customer Name:* ${event.name}
                                ${if (!event.phoneNumber.isNullOrBlank()) "• *Contact Number:* ${event.phoneNumber}\n" else ""}• *Cost Value:* INR ${String.format("%,.2f", event.amount)}
                                • *Audited By:* ${event.userId}
                                ━━━━━━━━━━━━━━━━━━━━━━
                                _Logged instantly under Studio Lens systems._
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
    try {
        val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager
        if (printManager == null) {
            android.widget.Toast.makeText(context, "Print service not available", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        val webView = android.webkit.WebView(context).apply {
            settings.allowContentAccess = true
            settings.allowFileAccess = true
        }
        activePrintWebView = webView
        
        val sdf = SimpleDateFormat("dd MMM yyyy hh:mm a", Locale.getDefault())
        val date = sdf.format(Date(event.timestamp))
        
        val imgTags = event.photoUri?.split(",")?.filter { it.isNotBlank() && (!it.startsWith("ic_") || it in listOf("ic_phone_blue", "ic_phone_amber", "ic_watch", "ic_tablet")) }?.map {
            when (it) {
                "ic_phone_blue" -> "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=400&q=80"
                "ic_phone_amber" -> "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?auto=format&fit=crop&w=400&q=80"
                "ic_watch" -> "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=400&q=80"
                "ic_tablet" -> "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=400&q=80"
                else -> it
            }
        }?.joinToString("") {
            "<img src='$it' style='max-width: 100%; height: auto; margin-top: 10px; border: 1px solid #ddd; padding: 4px;'/>"
        } ?: ""

        val htmlDocument = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body {
                        font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
                        padding: 20px;
                        color: #111;
                        line-height: 1.4;
                        max-width: 750px;
                        margin: 0 auto;
                        box-sizing: border-box;
                    }
                    .invoice-card {
                        border: 3px double #333;
                        border-radius: 8px;
                        padding: 24px;
                        background: #fff;
                        box-sizing: border-box;
                    }
                    .header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        border-bottom: 3px solid #111;
                        padding-bottom: 8px;
                        margin-bottom: 16px;
                    }
                    .header-title {
                        font-size: 20px;
                        font-weight: 800;
                        text-transform: uppercase;
                        letter-spacing: 0.8px;
                        color: #111;
                    }
                    .header-meta {
                        font-size: 11px;
                        text-align: right;
                        color: #333;
                    }
                    .grid {
                        display: table;
                        width: 100%;
                        margin-bottom: 16px;
                        border-collapse: collapse;
                    }
                    .grid-row {
                        display: table-row;
                    }
                    .grid-cell {
                        display: table-cell;
                        padding: 8px 10px;
                        font-size: 11px;
                        border-bottom: 1px dotted #ccc;
                    }
                    .label {
                        font-weight: bold;
                        color: #111;
                        background: #fdfdfd;
                        width: 130px;
                    }
                    .photos {
                        margin-top: 16px;
                        display: flex;
                        gap: 12px;
                        flex-wrap: wrap;
                    }
                    .photos img {
                        max-height: 120px;
                        border: 1px solid #ddd;
                        padding: 4px;
                        border-radius: 4px;
                    }
                    .terms-block {
                        font-size: 9px;
                        background: #f9f9f9;
                        border: 1px solid #e0e0e0;
                        padding: 10px;
                        border-radius: 4px;
                        margin-top: 16px;
                        color: #333;
                        white-space: pre-wrap;
                    }
                    .sign-row {
                        margin-top: 30px;
                        display: flex;
                        justify-content: space-between;
                        padding: 0 10px;
                        font-size: 10px;
                    }
                    .sign-line {
                        width: 180px;
                        border-top: 1px solid #111;
                        text-align: center;
                        padding-top: 4px;
                        margin-top: 25px;
                        font-weight: bold;
                    }
                    .footer-note {
                        font-size: 8px;
                        text-align: center;
                        margin-top: 16px;
                        color: #888;
                        font-style: italic;
                    }
                    @media print {
                        body { padding: 0; margin: 0; }
                    }
                </style>
            </head>
            <body>
                <div class="invoice-card">
                    <div class="header">
                        <div>
                            <div class="header-title">Mobile Gallery</div>
                            <div style="font-size: 10px; color: #555; font-style: italic;">Official Transaction & Safe-Custody Bill</div>
                        </div>
                        <div class="header-meta">
                            <div>Date: $date</div>
                            <div>Tx ID: ${event.id.take(8).uppercase()}</div>
                        </div>
                    </div>

                    <div class="grid">
                        <div class="grid-row">
                            <div class="grid-cell label">Transaction Mode:</div>
                            <div class="grid-cell" style="font-weight: bold; color: #111;">${event.actionType}</div>
                            <div class="grid-cell label">Brand & Model:</div>
                            <div class="grid-cell">${event.model.ifBlank { "________________" }}</div>
                        </div>
                        <div class="grid-row">
                            <div class="grid-cell label">Customer Name:</div>
                            <div class="grid-cell">${event.name.ifBlank { "_____________________________" }}</div>
                            <div class="grid-cell label">Contact Phone:</div>
                            <div class="grid-cell">${event.phoneNumber ?: "_____________________________"}</div>
                        </div>
                        <div class="grid-row">
                            <div class="grid-cell label">IMEI/Serial Key:</div>
                            <div class="grid-cell" style="font-family: monospace;">${event.serialNumber.ifBlank { "________________" }}</div>
                            <div class="grid-cell label">Disbursed Amount:</div>
                            <div class="grid-cell" style="font-weight: bold; color: #111;">INR ${String.format("%,.2f", event.amount)}</div>
                        </div>
                        <div class="grid-row">
                            <div class="grid-cell label">Audited By:</div>
                            <div class="grid-cell">${event.userId}</div>
                            <div class="grid-cell label">Quantity Unit:</div>
                            <div class="grid-cell">${event.quantity} Unit(s)</div>
                        </div>
                    </div>

                    <div style="font-size: 11px; margin-top: 8px; padding-bottom: 8px; border-bottom: 1px dotted #ccc;">
                        <strong>Log Remarks:</strong> ${event.description.ifBlank { "No additional remarks logged." }}
                    </div>

                    <div class="photos">$imgTags</div>

                    <div class="terms-block">
                        <strong>COMMON TRANSACTION DISCLOSURES & POLICY TERMS:</strong><br/>
1. WARRANTY ASSISTANCE: All brand items are covered solely by manufacturer service centers. Retailer holds no liability for mechanical failure, screen damage, liquid ingress, or physical wear/breakage.
2. DOCUMENTATION REQUIREMENT: Please retain original packaging box, complete inside accessories, and this physical printed voucher/bill to initiate claims, verification, or service assistance.
3. REFUND POLICY: All processed sales are final. Absolutely no cash refunds. Unopened, untampered items may be considered for exchange or store ledger credit notes within 24 hours of receipt.
4. OUT-FOR-REPAIR DEVICES: Repair hand-overs are registered entirely at client's risk. Please backup/clone personal user files. Retailer is not liable for data loss or software degradation during repair.
                    </div>

                    <div class="sign-row">
                        <div class="sign-line">Operator / Auditor Signature</div>
                        <div class="sign-line">Customer Accept Signature</div>
                    </div>

                    <div class="footer-note">
                        Thank you for your business! | System generated via Mobile Gallery Suite.
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                try {
                    view?.let {
                        val printAdapter = it.createPrintDocumentAdapter("Transaction Receipt")
                        val jobName = "Receipt_${event.serialNumber}"
                        printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        webView.loadDataWithBaseURL(null, htmlDocument, "text/HTML", "UTF-8", null)
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Cannot generate PDF", android.widget.Toast.LENGTH_SHORT).show()
    }
}

fun shareToWhatsApp(context: android.content.Context, message: String) {
    try {
        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, message)
            setPackage("com.whatsapp")
        }
        context.startActivity(sendIntent)
    } catch (e: Exception) {
        try {
            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, message)
            }
            context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Updates"))
        } catch (ex: Exception) {
            android.widget.Toast.makeText(context, "No sharing app installed", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

fun printHistoryEventCustom(
    context: android.content.Context,
    event: HistoryEvent,
    customText: String,
    includeBlanks: Boolean,
    selectedPhotos: List<String>,
    placeholderCount: Int
) {
    try {
        val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager
        if (printManager == null) {
            android.widget.Toast.makeText(context, "Print service not available", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        val webView = android.webkit.WebView(context).apply {
            settings.allowContentAccess = true
            settings.allowFileAccess = true
        }
        activePrintWebView = webView
        
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val date = sdf.format(Date(event.timestamp))
        
        val loadedPhotos = selectedPhotos.map {
            when (it) {
                "ic_phone_blue" -> "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=400&q=80"
                "ic_phone_amber" -> "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?auto=format&fit=crop&w=400&q=80"
                "ic_watch" -> "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=400&q=80"
                "ic_tablet" -> "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=400&q=80"
                else -> it
            }
        }

        var photoBoxesHtml = ""
        for (photo in loadedPhotos) {
            photoBoxesHtml += """
                <div class="photo-box">
                    <img src="$photo" />
                </div>
            """.trimIndent()
        }
        val neededPlaceholders = if (loadedPhotos.size < 2) {
            placeholderCount.coerceAtMost(2 - loadedPhotos.size)
        } else {
            0
        }
        for (i in 1..neededPlaceholders) {
            photoBoxesHtml += """
                <div class="photo-box" style="display: flex; flex-direction: column; justify-content: center; align-items: center; border: 1px dashed #777; background: #fafafa; font-size: 8px; color: #777; height: 100%;">
                    <div style="font-weight: bold; margin-bottom: 2px;">Image / Stamp Frame ${if (neededPlaceholders > 1) i.toString() else ""}</div>
                    <div>(Dashed box space)</div>
                </div>
            """.trimIndent()
        }

        val htmlDocument = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body {
                        font-family: sans-serif;
                        padding: 10px;
                        color: #111;
                        line-height: 1.3;
                        max-width: 750px;
                        margin: 0 auto;
                        box-sizing: border-box;
                    }
                    .invoice-card {
                        border: 2.5px dashed #333;
                        border-radius: 8px;
                        padding: 16px;
                        background: #fff;
                        box-sizing: border-box;
                    }
                    .header {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        border-bottom: 2px solid #222;
                        padding-bottom: 6px;
                        margin-bottom: 12px;
                    }
                    .header-title {
                        font-size: 16px;
                        font-weight: 800;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                        color: #111;
                    }
                    .header-meta {
                        font-size: 10px;
                        text-align: right;
                        color: #444;
                    }
                    .grid {
                        display: table;
                        width: 100%;
                        margin-bottom: 12px;
                    }
                    .grid-row {
                        display: table-row;
                    }
                    .grid-cell {
                        display: table-cell;
                        padding: 4px 6px;
                        font-size: 10px;
                        border-bottom: 1px dotted #ccc;
                    }
                    .label {
                        font-weight: bold;
                        color: #111;
                        width: 120px;
                    }
                    .photos-container {
                        display: flex;
                        gap: 12px;
                        margin: 10px 0;
                        height: 80px;
                    }
                    .photo-box {
                        flex: 1;
                        height: 80px;
                        border: 1px dashed #555;
                        border-radius: 4px;
                        overflow: hidden;
                        text-align: center;
                    }
                    .photo-box img {
                        width: 100%;
                        height: 100%;
                        object-fit: cover;
                    }
                    .terms-block {
                        font-size: 8.5px;
                        background: #f7f7f7;
                        border: 1px solid #ddd;
                        padding: 6px;
                        border-radius: 4px;
                        margin-top: 8px;
                        color: #333;
                        white-space: pre-wrap;
                    }
                    .footer-note {
                        font-size: 8px;
                        text-align: center;
                        margin-top: 10px;
                        color: #777;
                        font-style: italic;
                    }
                    @media print {
                        body { padding: 0; margin: 0; }
                    }
                </style>
            </head>
            <body>
                <div class="invoice-card">
                    <div class="header">
                        <div>
                            <div class="header-title">Studio Lens Transaction slip</div>
                            <div style="font-size: 9px; color: #555; font-style: italic;">Ledger verification sheet</div>
                        </div>
                        <div class="header-meta">
                            <div>Date: $date</div>
                            <div>Tx ID: ${event.id.take(8).uppercase()}</div>
                        </div>
                    </div>
                    
                    <div class="grid">
                        <div class="grid-row">
                            <div class="grid-cell label">Action Mode:</div>
                            <div class="grid-cell" style="font-weight: bold; color: #111;">${event.actionType}</div>
                            <div class="grid-cell label">Brand & Model:</div>
                            <div class="grid-cell">${event.model.ifBlank { "________________" }}</div>
                        </div>
                        <div class="grid-row">
                            <div class="grid-cell label">Customer Name:</div>
                            <div class="grid-cell">${event.name.ifBlank { "_____________________________" }}</div>
                            <div class="grid-cell label">Contact Phone:</div>
                            <div class="grid-cell">${event.phoneNumber ?: "_____________________________"}</div>
                        </div>
                        <div class="grid-row">
                            <div class="grid-cell label">IMEI / Serial key:</div>
                            <div class="grid-cell" style="font-family: monospace;">${event.serialNumber.ifBlank { "________________" }}</div>
                            <div class="grid-cell label">Disbursed Amount:</div>
                            <div class="grid-cell" style="font-weight: bold; color: #111;">INR ${String.format("%,.2f", event.amount)}</div>
                        </div>
                    </div>

                    <div class="photos-container">
                        $photoBoxesHtml
                    </div>

                    <div class="terms-block">
                        <strong>COMMON TRANSACTION DISCLOSURES & TERMS:</strong><br/>
                        $customText
                    </div>
                    
                    <div class="footer-note">
                        Voucher uses exactly ~1/3 space of page | Certified by Auditer (${event.userId})
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        webView.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                try {
                    view?.let {
                        val printAdapter = it.createPrintDocumentAdapter("Custom Receipt")
                        val jobName = "Custom_Receipt_${event.serialNumber}"
                        printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        webView.loadDataWithBaseURL(null, htmlDocument, "text/HTML", "UTF-8", null)
    } catch (e: Exception) {
        e.printStackTrace()
        android.widget.Toast.makeText(context, "Cannot generate custom print doc", android.widget.Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPrintDialog(
    event: HistoryEvent,
    onDismiss: () -> Unit
) {
    var customTerms by remember { mutableStateOf(
        "1. WARRANTY ASSISTANCE: All brand items are covered solely by manufacturer service centers. Retailer holds no liability for mechanical failure, screen damage, liquid ingress, or physical wear/breakage.\n" +
        "2. DOCUMENTATION REQUIREMENT: Please retain original packaging box, complete inside accessories, and this physical printed voucher/bill to initiate claims, verification, or service assistance.\n" +
        "3. REFUND POLICY: All processed sales are final. Absolutely no cash refunds. Unopened, untampered items may be considered for exchange or store ledger credit notes within 24 hours of receipt.\n" +
        "4. OUT-FOR-REPAIR DEVICES: Repair hand-overs are registered entirely at client's risk. Please backup/clone personal user files. Retailer is not liable for data loss or software degradation during repair."
    ) }
    
    val photos = remember(event.photoUri) {
        event.photoUri?.split(",")?.filter { it.isNotBlank() && (!it.startsWith("ic_") || it in listOf("ic_phone_blue", "ic_phone_amber", "ic_watch", "ic_tablet")) } ?: emptyList()
    }
    
    val selectedPhotos = remember { mutableStateListOf<String>().apply { 
        addAll(photos.take(2))
    } }
    
    var placeholderCount by remember { mutableStateOf(if (photos.isEmpty()) 2 else (2 - photos.size).coerceAtLeast(0)) }

    val context = androidx.compose.ui.platform.LocalContext.current

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
                Text(
                    text = "Configure receipt styling for A4 / thermal roll paper layout. This compact format utilizes approx. 1/3 page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = customTerms,
                    onValueChange = { customTerms = it },
                    label = { Text("Common Text / Terms & Conditions") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                if (photos.isNotEmpty()) {
                    Text(
                        text = "Select up to 2 images to print on receipt:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        photos.forEach { photo ->
                            val isSelected = selectedPhotos.contains(photo)
                            Surface(
                                onClick = {
                                        if (isSelected) {
                                            selectedPhotos.remove(photo)
                                        } else {
                                            if (selectedPhotos.size < 2) {
                                                selectedPhotos.add(photo)
                                            } else {
                                                android.widget.Toast.makeText(context, "Only up to 2 photos can be printed", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.Gray)
                                    ) {
                                        coil.compose.AsyncImage(
                                            model = com.example.util.AppUtils.resolveImageModel(photo),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (photo.startsWith("http")) "Cloud Captured Photo" else "Sample Photo Asset",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = photo.takeLast(30),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = null
                                    )
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

                if (selectedPhotos.size < 2) {
                    val remainingLimit = 2 - selectedPhotos.size
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Include empty placeholder boxes (for physical signs/thumbs):",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Row {
                                (0..remainingLimit).forEach { i ->
                                    FilterChip(
                                        selected = placeholderCount == i,
                                        onClick = { placeholderCount = i },
                                        label = { Text("$i Box") },
                                        modifier = Modifier.padding(horizontal = 2.dp)
                                    )
                                }
                            }
                        }
                    }
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
                        includeBlanks = true,
                        selectedPhotos = selectedPhotos.toList(),
                        placeholderCount = placeholderCount
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
