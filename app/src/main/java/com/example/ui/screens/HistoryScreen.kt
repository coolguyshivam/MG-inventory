package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
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

    // Filtering & Sorting processes
    val filteredEvents = remember(rawEvents, searchWord, typeFilter, sortOption, activeDateFilter) {
        var list = rawEvents

        // Date Filter
        val now = System.currentTimeMillis()
        val threshold = when (activeDateFilter) {
            "Today" -> now - 86400000L
            "This Week" -> now - 86400000L * 7L
            "This Month" -> now - 86400000L * 30L
            else -> 0L
        }
        if (threshold > 0) {
            list = list.filter { it.timestamp >= threshold }
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
                placeholder = { Text("Search IMEI history, Action info...", fontSize = 13.sp) },
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
                    .size(44.dp)
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
                        .size(44.dp)
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
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("All Time", "Today", "This Week", "This Month")
            items(filters.size) { index ->
                val filter = filters[index]
                FilterChip(
                    selected = activeDateFilter == filter,
                    onClick = { activeDateFilter = filter },
                    label = { Text(filter) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
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
                        onPhotoClick = { selectedPhotosForViewer = it }
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
                        model = photos[page],
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

@Composable
fun HistoryRowItem(
    event: HistoryEvent,
    isExpanded: Boolean,
    onExpandTapped: () -> Unit,
    onPhotoClick: ((List<String>) -> Unit)? = null
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

                    if (!event.extraDetails.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Log Metadata:", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = themeColor)
                        Text(event.extraDetails, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                    }

                    if (event.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(event.description, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (!event.photoUri.isNullOrBlank()) {
                        val photos = event.photoUri.split(",").filter { it.isNotBlank() && !it.startsWith("ic_") }
                        if (photos.isNotEmpty()) {
                            Text("Photos (${photos.size}):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(photos.size, key = { it }) { index ->
                                    val uri = photos[index]
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                                            .clickable { onPhotoClick?.invoke(photos) }
                                    ) {
                                        val ctx = androidx.compose.ui.platform.LocalContext.current
                                        val request = coil.request.ImageRequest.Builder(ctx)
                                            .data(uri)
                                            .size(200) // limit size to fix latency and memory limits
                                            .crossfade(true)
                                            .build()
                                        coil.compose.AsyncImage(
                                            model = request,
                                            contentDescription = "Photo $index",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val context = androidx.compose.ui.platform.LocalContext.current
                    Button(
                        onClick = { printHistoryEvent(context, event) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = themeColor)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = "Print", modifier = Modifier.size(18.dp).padding(end = 6.dp))
                        Text("Print / Generate PDF")
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
        
        val imgTags = event.photoUri?.split(",")?.filter { it.isNotBlank() && !it.startsWith("ic_") }?.joinToString("") {
            "<img src='$it' style='max-width: 100%; height: auto; margin-top: 10px; border: 1px solid #ddd; padding: 4px;'/>"
        } ?: ""

        val htmlDocument = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body { font-family: sans-serif; padding: 20px; color: #333; line-height: 1.5; }
                    h1 { border-bottom: 2px solid #ccc; padding-bottom: 10px; }
                    .label { font-weight: bold; width: 150px; display: inline-block; }
                    .row { border-bottom: 1px solid #eee; padding: 8px 0; }
                    .photos { margin-top: 20px; }
                </style>
            </head>
            <body>
                <h1>Transaction Receipt</h1>
                <div class="row"><span class="label">Date:</span> $date</div>
                <div class="row"><span class="label">Action Type:</span> ${event.actionType}</div>
                <div class="row"><span class="label">IMEI/Serial:</span> ${event.serialNumber}</div>
                <div class="row"><span class="label">Model:</span> ${event.model}</div>
                <div class="row"><span class="label">Party Name:</span> ${event.name}</div>
                <div class="row"><span class="label">Phone:</span> ${event.phoneNumber ?: "N/A"}</div>
                <div class="row"><span class="label">Quantity:</span> ${event.quantity}</div>
                <div class="row"><span class="label">Amount:</span> INR ${event.amount}</div>
                <div class="row"><span class="label">Audited By:</span> ${event.userId}</div>
                <div class="row"><span class="label">Description:</span> ${event.description}</div>
                <div class="photos">$imgTags</div>
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
