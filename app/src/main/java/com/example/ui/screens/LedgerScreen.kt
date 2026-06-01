package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.StockViewModel
import com.example.data.model.Party
import com.example.data.model.LedgerEntry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val allParties by viewModel.allParties.collectAsStateWithLifecycle()
    val allLedgerEntries by viewModel.allLedgerEntries.collectAsStateWithLifecycle()
    val historyEvents by viewModel.historyEvents.collectAsStateWithLifecycle()
    val loggedInUser by viewModel.loggedInUser.collectAsStateWithLifecycle()
    val isAdmin = loggedInUser?.role == "Admin"
    var selectedEventForDialog by remember { mutableStateOf<com.example.data.model.HistoryEvent?>(null) }
    var eventToPrintCustomly by remember { mutableStateOf<com.example.data.model.HistoryEvent?>(null) }
    var selectedPhotosForViewer by remember { mutableStateOf<List<String>?>(null) }

    var showAddPartyDialog by remember { mutableStateOf(false) }
    var showEditPartyDialog by remember { mutableStateOf(false) }
    var showDeletePartyDialog by remember { mutableStateOf(false) }
    var selectedParty by remember { mutableStateOf<Party?>(null) }
    var showPaymentDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedParty == null) {
            // List of Parties
            TopAppBar(
                title = { Text("Vendor & Customer Ledger", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showAddPartyDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Party")
                    }
                }
            )
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allParties) { party ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedParty = party },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(party.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Phone: ${party.phoneNumber}", style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val balanceColor = if (party.balance > 0) Color(0xFF4CAF50) else if (party.balance < 0) Color.Red else MaterialTheme.colorScheme.onSurface 
                                val balanceText = if (party.balance > 0) "They owe ₹${party.balance}" else if (party.balance < 0) "We owe ₹${-party.balance}" else "Settled"
                                Text(balanceText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = balanceColor)
                            }
                        }
                    }
                }
            }
        } else {
            // Party details and ledger
            val party = allParties.find { it.id == selectedParty!!.id } ?: selectedParty!!
            val partyLedger = allLedgerEntries.filter { it.partyId == party.id }.sortedByDescending { it.timestamp }
            
            TopAppBar(
                title = { Text(party.name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = { selectedParty = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditPartyDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Party")
                    }
                    IconButton(onClick = { showDeletePartyDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Party")
                    }
                    if (isAdmin) {
                        Button(onClick = { showPaymentDialog = true }) {
                            Text("Add Payment")
                        }
                    }
                }
            )
            
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val balanceColor = if (party.balance > 0) Color(0xFF4CAF50) else if (party.balance < 0) Color.Red else MaterialTheme.colorScheme.onSurface 
                val balanceText = if (party.balance > 0) "They owe us: ₹${party.balance}" else if (party.balance < 0) "We owe them: ₹${-party.balance}" else "Balance: Settled"
                Text(balanceText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = balanceColor)
            }
            HorizontalDivider()
            val groupedLedger = remember(partyLedger) {
                partyLedger.groupBy { 
                    java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(it.timestamp)) 
                }
            }

            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groupedLedger.forEach { (dateStr, entries) ->
                    item {
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(entries) { entry ->
                        val color = when (entry.type) {
                            "SALE" -> Color(0xFF10B981)
                            "PURCHASE" -> Color(0xFF3B82F6)
                            "PAYMENT_IN" -> Color(0xFF10B981)
                            "PAYMENT_OUT" -> Color(0xFFEAB308)
                            "RETURN" -> Color(0xFF9333EA)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val sign = if (entry.type == "SALE" || entry.type == "PAYMENT_OUT" || entry.type == "REPAIR_SENT") "+" else "-"
                        val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.timestamp))

                        var showCustomPrintDialogForEntry by remember { mutableStateOf(false) }
                        val linkedEvent = remember(entry.historyEventId, historyEvents) {
                            entry.historyEventId?.let { evId -> historyEvents.find { it.id == evId } }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (entry.historyEventId != null) {
                                    selectedEventForDialog = historyEvents.find { it.id == entry.historyEventId }
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.type.replace("_", " "), fontWeight = FontWeight.Bold, color = color)
                                    Text(timeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (entry.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(entry.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text("$sign₹${entry.amount}", fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
                                    IconButton(
                                        onClick = {
                                            if (linkedEvent != null) {
                                                showCustomPrintDialogForEntry = true
                                            } else {
                                                printLedgerEntry(context, entry, party)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Print,
                                            contentDescription = "Print ledger entry",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        if (showCustomPrintDialogForEntry && linkedEvent != null) {
                            CustomPrintDialog(
                                event = linkedEvent,
                                onDismiss = { showCustomPrintDialogForEntry = false }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddPartyDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var aadhaar by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPartyDialog = false },
            title = { Text("Add Party") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
                    OutlinedTextField(value = aadhaar, onValueChange = { aadhaar = it }, label = { Text("Aadhaar") }, singleLine = true)
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, singleLine = false)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.addParty(name, phone, aadhaar, address)
                        showAddPartyDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddPartyDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showPaymentDialog && selectedParty != null) {
        var amountStr by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("PAYMENT_OUT") } // or PAYMENT_IN
        var desc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            title = { Text("Record Lumpsum Payment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = type == "PAYMENT_IN", onClick = { type = "PAYMENT_IN" })
                            Text("Received (In)")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = type == "PAYMENT_OUT", onClick = { type = "PAYMENT_OUT" })
                            Text("Paid (Out)")
                        }
                    }
                    OutlinedTextField(value = amountStr, onValueChange = { amountStr = it }, label = { Text("Amount") })
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amount = amountStr.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        viewModel.addLedgerPayment(selectedParty!!.id, amount, type, desc)
                        showPaymentDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }) { Text("Cancel") }
            }
        )
    }

    selectedEventForDialog?.let { event ->
        AlertDialog(
            onDismissRequest = { selectedEventForDialog = null },
            title = { Text("Transaction Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                // Keep it single list context style for HistoryRowItem
                // that expects a column
                HistoryRowItem(
                    event = event,
                    isExpanded = true,
                    onExpandTapped = {},
                    onPhotoClick = { selectedPhotosForViewer = it },
                    onPrintClick = { ev ->
                        eventToPrintCustomly = ev
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { selectedEventForDialog = null }) { Text("Close") }
            }
        )
    }

    if (eventToPrintCustomly != null) {
        CustomPrintDialog(
            event = eventToPrintCustomly!!,
            onDismiss = { eventToPrintCustomly = null }
        )
    }

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

    if (showEditPartyDialog && selectedParty != null) {
        var editName by remember { mutableStateOf(selectedParty!!.name) }
        var editPhone by remember { mutableStateOf(selectedParty!!.phoneNumber) }
        var editAadhaar by remember { mutableStateOf(selectedParty!!.aadhaarNumber ?: "") }
        var editAddress by remember { mutableStateOf(selectedParty!!.address) }
        AlertDialog(
            onDismissRequest = { showEditPartyDialog = false },
            title = { Text("Edit Party") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(value = editPhone, onValueChange = { editPhone = it }, label = { Text("Phone") }, singleLine = true)
                    OutlinedTextField(value = editAadhaar, onValueChange = { editAadhaar = it }, label = { Text("Aadhaar") }, singleLine = true)
                    OutlinedTextField(value = editAddress, onValueChange = { editAddress = it }, label = { Text("Address") }, singleLine = false)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (editName.isNotBlank()) {
                        viewModel.editParty(selectedParty!!.id, editName, editPhone, editAadhaar, editAddress)
                        showEditPartyDialog = false
                        selectedParty = selectedParty?.copy(name = editName, phoneNumber = editPhone, aadhaarNumber = editAadhaar, address = editAddress)
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditPartyDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeletePartyDialog && selectedParty != null) {
        AlertDialog(
            onDismissRequest = { showDeletePartyDialog = false },
            title = { Text("Delete Party") },
            text = { Text("Are you sure you want to delete ${selectedParty!!.name}? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteParty(selectedParty!!.id)
                        showDeletePartyDialog = false
                        selectedParty = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePartyDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private var activePrintWebView: android.webkit.WebView? = null

private fun printLedgerEntry(context: android.content.Context, entry: LedgerEntry, party: Party) {
    val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager
    if (printManager == null) {
        android.widget.Toast.makeText(context, "Print service not available", android.widget.Toast.LENGTH_SHORT).show()
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
            val date = sdf.format(java.util.Date(entry.timestamp))
            
            val voucherTitle = when (entry.type) {
                "PAYMENT_IN" -> "Receipt Voucher (Payment In)"
                "PAYMENT_OUT" -> "Payment Voucher (Payment Out)"
                "SALE" -> "Sales Receipt"
                "PURCHASE" -> "Purchase Invoice Copy"
                else -> "${entry.type.replace("_", " ")} Voucher"
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
                            max-width: 800px;
                            margin: 0 auto;
                            box-sizing: border-box;
                        }
                        .invoice-card {
                            border: 2px solid #222;
                            border-radius: 4px;
                            padding: 16px;
                            background: #fff;
                            box-sizing: border-box;
                            min-height: 95vh;
                            display: flex;
                            flex-direction: column;
                        }
                        .header {
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            border-bottom: 2px solid #222;
                            padding-bottom: 4px;
                            margin-bottom: 8px;
                        }
                        .header-title {
                            font-size: 16px;
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
                        .grid {
                            display: table;
                            width: 100%;
                            margin-bottom: 8px;
                        }
                        .grid-row {
                            display: table-row;
                        }
                        .grid-cell {
                            display: table-cell;
                            padding: 6px 8px;
                            font-size: 12px;
                            border-bottom: 1px dotted #ccc;
                        }
                        .label {
                            font-weight: bold;
                            color: #111;
                            width: 140px;
                        }
                        .amount-block {
                            font-size: 16px;
                            font-weight: bold;
                            background: #fdfdfd;
                            border: 2px solid #111;
                            padding: 10px;
                            border-radius: 4px;
                            margin-top: 15px;
                            text-align: center;
                        }
                        .remarks-block {
                            font-size: 12px;
                            margin-top: 15px;
                            color: #111;
                            border-left: 2px solid #111;
                            padding-left: 10px;
                            font-style: italic;
                        }
                        .sign-row {
                            margin-top: 50px; /* Spacing added natively using div below */
                            display: flex;
                            justify-content: space-between;
                            padding: 0 20px;
                            font-size: 11px;
                        }
                        .sign-line {
                            width: 220px;
                            border-top: 1.5px solid #111;
                            text-align: center;
                            padding-top: 10px;
                            font-weight: bold;
                        }
                        .footer-note {
                            font-size: 8px;
                            text-align: center;
                            margin-top: auto;
                            padding-top: 20px;
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
                                <div class="header-title">$voucherTitle</div>
                                <div style="font-size: 10px; color: #555;">Mobile Gallery Suite</div>
                            </div>
                            <div class="header-meta">
                                <div>Date: $date</div>
                                <div>Voucher ID: ${entry.id.take(8).uppercase()}</div>
                            </div>
                        </div>
                        
                        <div class="grid">
                            <div class="grid-row">
                                <div class="grid-cell label">Party Name:</div>
                                <div class="grid-cell" style="font-weight: bold;">${party.name}</div>
                            </div>
                            <div class="grid-row">
                                <div class="grid-cell label">Phone / Contact:</div>
                                <div class="grid-cell">${party.phoneNumber.ifBlank { "N/A" }}</div>
                            </div>
                            <div class="grid-row">
                                <div class="grid-cell label">Address:</div>
                                <div class="grid-cell">${party.address.ifBlank { "N/A" }}</div>
                            </div>
                            <div class="grid-row">
                                <div class="grid-cell label">Audited Party Id:</div>
                                <div class="grid-cell" style="font-family: monospace;">${party.id}</div>
                            </div>
                        </div>
    
                        <div class="amount-block">
                            Amount: INR ${String.format("%,.2f", entry.amount)}
                        </div>
    
                        <div class="remarks-block">
                            <strong>Transaction Description:</strong><br/>
                            ${entry.description.ifBlank { "No description details provided." }}
                        </div>
    
                        <div style="font-size: 11px; margin-top: 25px; color: #333; line-height: 1.4;">
                            <strong>Declaration:</strong> This voucher records an official Ledger adjustment entry in Mobile Gallery Suite databases. All transactions are subjected to verification against bank or cash balance transitions of Mobile Gallery Store.
                        </div>
    
                        <!-- Generous empty spacing around signing block for physics/pen comfort -->
                        <div style="height: 120px;"></div>
    
                        <div class="sign-row">
                            <div class="sign-line">Authorized Signatory</div>
                            <div class="sign-line">Party / Receiver Signature</div>
                        </div>
    
                        <div class="footer-note">
                            Thank you for your business! | System generated via Mobile Gallery Suite.
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent()

            withContext(Dispatchers.Main) {
                val webView = android.webkit.WebView(context).apply {
                    settings.allowContentAccess = true
                    settings.allowFileAccess = true
                }
                activePrintWebView = webView

                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        try {
                            view?.let {
                                val printAdapter = it.createPrintDocumentAdapter("Ledger Voucher")
                                val jobName = "Ledger_Voucher_${entry.id}"
                                printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
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
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Cannot generate receipt print block", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}

