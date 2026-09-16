package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.viewmodel.StockViewModel
import com.example.util.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseBackupScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val historyEvents by viewModel.historyEvents.collectAsState()
    val brandStockItems by viewModel.brandStockItems.collectAsState()
    val brandStockTransactions by viewModel.brandStockTransactions.collectAsState()
    val brandVariants by viewModel.brandVariants.collectAsState()
    val allParties by viewModel.allParties.collectAsState()
    val allLedgerEntries by viewModel.allLedgerEntries.collectAsState()
    val allAttendanceRecords by viewModel.allAttendanceRecords.collectAsState()
    val allLeaveApplications by viewModel.allLeaveApplications.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()
    val loggedInUser by viewModel.loggedInUser.collectAsState()

    var isExporting by remember { mutableStateOf(false) }
    var exportStatusMessage by remember { mutableStateOf<String?>(null) }

    val totalRecords = inventoryItems.size + historyEvents.size + brandStockItems.size +
            brandStockTransactions.size + allParties.size + allLedgerEntries.size +
            allAttendanceRecords.size + allLeaveApplications.size

    fun shareExportedFile(file: File, mimeType: String, chooserTitle: String) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                clipData = android.content.ClipData.newRawUri(file.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
            AppUtils.performHapticFeedback(context)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    fun exportFullJson() {
        isExporting = true
        exportStatusMessage = "Compiling full JSON database dump..."
        coroutineScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val root = JSONObject()
                    val meta = JSONObject().apply {
                        put("app", "Mobile Gallery Inventory Management")
                        put("version", "2.0")
                        put("timestamp", System.currentTimeMillis())
                        put("exported_at_iso", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                        put("exported_by", loggedInUser?.username ?: "Admin")
                        put("total_entities", totalRecords)
                    }
                    root.put("metadata", meta)

                    // Inventory Items
                    val invArray = JSONArray()
                    inventoryItems.forEach { item ->
                        val obj = JSONObject().apply {
                            put("id", item.id)
                            put("serialNumber", item.serialNumber)
                            put("model", item.model)
                            put("name", item.name)
                            put("phoneNumber", item.phoneNumber ?: "")
                            put("aadhaarNumber", item.aadhaarNumber ?: "")
                            put("amount", item.amount)
                            put("description", item.description)
                            put("quantity", item.quantity)
                            put("dateInMillis", item.dateInMillis)
                            put("underRepair", item.underRepair)
                            put("photoUri", item.photoUri ?: "")
                        }
                        invArray.put(obj)
                    }
                    root.put("inventory_items", invArray)

                    // History Events
                    val histArray = JSONArray()
                    historyEvents.forEach { event ->
                        val obj = JSONObject().apply {
                            put("id", event.id)
                            put("actionType", event.actionType)
                            put("serialNumber", event.serialNumber)
                            put("model", event.model)
                            put("name", event.name)
                            put("phoneNumber", event.phoneNumber ?: "")
                            put("aadhaarNumber", event.aadhaarNumber ?: "")
                            put("amount", event.amount)
                            put("description", event.description)
                            put("quantity", event.quantity)
                            put("dateInMillis", event.dateInMillis)
                            put("userId", event.userId)
                            put("photoUri", event.photoUri ?: "")
                        }
                        histArray.put(obj)
                    }
                    root.put("history_events", histArray)

                    // Brand Stock Items
                    val brandArray = JSONArray()
                    brandStockItems.forEach { item ->
                        val obj = JSONObject().apply {
                            put("id", item.id)
                            put("imei", item.imei)
                            put("brand", item.brand)
                            put("variant", item.variant)
                            put("color", item.color)
                            put("warehouse", item.warehouse)
                            put("addedByUser", item.addedByUser)
                            put("addedDate", item.addedDate)
                        }
                        brandArray.put(obj)
                    }
                    root.put("brand_stock_items", brandArray)

                    // Brand Stock Transactions
                    val brandTxArray = JSONArray()
                    brandStockTransactions.forEach { tx ->
                        val obj = JSONObject().apply {
                            put("id", tx.id)
                            put("imei", tx.imei)
                            put("brand", tx.brand)
                            put("variant", tx.variant)
                            put("color", tx.color)
                            put("warehouse", tx.warehouse)
                            put("type", tx.type)
                            put("operator", tx.operator)
                            put("dateInMillis", tx.dateInMillis)
                            put("notes", tx.notes ?: "")
                        }
                        brandTxArray.put(obj)
                    }
                    root.put("brand_stock_transactions", brandTxArray)

                    // Brand Variants
                    val variantsArray = JSONArray()
                    brandVariants.forEach { v ->
                        val obj = JSONObject().apply {
                            put("id", v.id)
                            put("brand", v.brand)
                            put("modelName", v.modelName)
                            put("specs", v.specs)
                            put("color", v.color)
                        }
                        variantsArray.put(obj)
                    }
                    root.put("brand_variants", variantsArray)

                    // Parties
                    val partiesArray = JSONArray()
                    allParties.forEach { p ->
                        val obj = JSONObject().apply {
                            put("id", p.id)
                            put("name", p.name)
                            put("phoneNumber", p.phoneNumber)
                            put("aadhaarNumber", p.aadhaarNumber)
                            put("address", p.address)
                        }
                        partiesArray.put(obj)
                    }
                    root.put("parties", partiesArray)

                    // Ledger Entries
                    val ledgerArray = JSONArray()
                    allLedgerEntries.forEach { l ->
                        val obj = JSONObject().apply {
                            put("id", l.id)
                            put("partyId", l.partyId)
                            put("amount", l.amount)
                            put("type", l.type)
                            put("description", l.description)
                            put("timestamp", l.timestamp)
                        }
                        ledgerArray.put(obj)
                    }
                    root.put("ledger_entries", ledgerArray)

                    // Attendance Records
                    val attArray = JSONArray()
                    allAttendanceRecords.forEach { att ->
                        val obj = JSONObject().apply {
                            put("id", att.id)
                            put("userId", att.userId)
                            put("userName", att.userName)
                            put("dateString", att.dateString)
                            put("status", att.status)
                            put("checkInTime", att.checkInTime)
                            put("checkOutTime", att.checkOutTime)
                            put("checkInLocation", att.checkInLocationSpec)
                            put("notes", att.notes)
                        }
                        attArray.put(obj)
                    }
                    root.put("attendance_records", attArray)

                    // Leave Applications
                    val leaveArray = JSONArray()
                    allLeaveApplications.forEach { l ->
                        val obj = JSONObject().apply {
                            put("id", l.id)
                            put("userId", l.userId)
                            put("userName", l.userName)
                            put("startDate", l.startDateString)
                            put("endDate", l.endDateString)
                            put("leaveType", l.leaveType)
                            put("reason", l.reason)
                            put("status", l.status)
                            put("approvedBy", l.approvedBy ?: "")
                            put("appliedOn", l.appliedOn)
                        }
                        leaveArray.put(obj)
                    }
                    root.put("leave_applications", leaveArray)

                    // Users (Sanitized metadata)
                    val usersArray = JSONArray()
                    allUsers.forEach { u ->
                        val obj = JSONObject().apply {
                            put("username", u.username)
                            put("role", u.role)
                        }
                        usersArray.put(obj)
                    }
                    root.put("users", usersArray)

                    val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val outFile = File(context.cacheDir, "MobileGallery_Full_Backup_$timestampStr.json")
                    FileOutputStream(outFile).use { fos ->
                        fos.write(root.toString(2).toByteArray(Charsets.UTF_8))
                    }
                    outFile
                }
                isExporting = false
                exportStatusMessage = "Full JSON backup ready (${file.length() / 1024} KB)"
                shareExportedFile(file, "application/json", "Export Full Database JSON Backup")
            } catch (e: Exception) {
                isExporting = false
                exportStatusMessage = "JSON export failed: ${e.localizedMessage}"
                Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportCsv(
        filenamePrefix: String,
        headers: List<String>,
        rows: List<List<String>>,
        displayName: String
    ) {
        isExporting = true
        exportStatusMessage = "Generating $displayName CSV..."
        coroutineScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val sb = StringBuilder()
                    // Escape and join headers
                    sb.append(headers.joinToString(",") { escapeCsv(it) }).append("\n")
                    // Rows
                    rows.forEach { row ->
                        sb.append(row.joinToString(",") { escapeCsv(it) }).append("\n")
                    }

                    val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val outFile = File(context.cacheDir, "${filenamePrefix}_$timestampStr.csv")
                    FileOutputStream(outFile).use { fos ->
                        fos.write(sb.toString().toByteArray(Charsets.UTF_8))
                    }
                    outFile
                }
                isExporting = false
                exportStatusMessage = "$displayName CSV exported successfully"
                shareExportedFile(file, "text/csv", "Export $displayName")
            } catch (e: Exception) {
                isExporting = false
                exportStatusMessage = "CSV export failed: ${e.localizedMessage}"
                Toast.makeText(context, "Export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Database Backup & Export", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Instant JSON/CSV Cloud Snapshots",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.setTab(0) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to Inventory")
                    }
                },
                actions = {
                    IconButton(onClick = { exportFullJson() }, enabled = !isExporting) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Quick Share All",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Admin Status Banner
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Cloud Multi-Store Sync",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = Color(0xFF2E7D32),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "200 MB Cache Active",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Total Data Entities: $totalRecords synchronized records across ~20 active staff terminals.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Export in progress indicator
            if (isExporting || exportStatusMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isExporting) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(
                                text = exportStatusMessage ?: "Ready",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // HERO CARD: Full JSON Backup
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DataArray,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Full Database Snapshot (JSON)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Generates a complete, single-file schema-preserving JSON archive containing all inventory items, sales history, brand stocks, partner ledgers, and attendance records.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { exportFullJson() },
                            enabled = !isExporting,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_export_full_json")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Full JSON Archive ($totalRecords items)")
                        }
                    }
                }
            }

            // SECTION HEADER: Target CSV Exports
            item {
                Text(
                    text = "Modular CSV Exports",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 1. Active Stock Inventory CSV
            item {
                BackupExportRow(
                    title = "Active Stock Inventory",
                    description = "Current unsold devices, IMEI, cost, quantities, and repair statuses",
                    count = inventoryItems.size,
                    icon = Icons.Default.Inventory2,
                    isExporting = isExporting,
                    onExport = {
                        val headers = listOf("Serial/IMEI", "Model", "Party/Customer", "Phone", "Aadhaar", "Amount", "Quantity", "Date", "Under Repair", "Description")
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val rows = inventoryItems.map {
                            listOf(
                                it.serialNumber,
                                it.model,
                                it.name,
                                it.phoneNumber ?: "",
                                it.aadhaarNumber ?: "",
                                it.amount.toString(),
                                it.quantity.toString(),
                                sdf.format(Date(it.dateInMillis)),
                                if (it.underRepair) "YES" else "NO",
                                it.description
                            )
                        }
                        exportCsv("Inventory_Active_Stock", headers, rows, "Active Inventory")
                    }
                )
            }

            // 2. Sales & Audit History Events CSV
            item {
                BackupExportRow(
                    title = "Sales & Audit History Events",
                    description = "Chronological purchases, sales, returns, and repair logs",
                    count = historyEvents.size,
                    icon = Icons.Default.History,
                    isExporting = isExporting,
                    onExport = {
                        val headers = listOf("Event ID", "Action", "Serial/IMEI", "Model", "Name", "Phone", "Aadhaar", "Amount", "Quantity", "Date", "Staff/User", "Description")
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val rows = historyEvents.map {
                            listOf(
                                it.id,
                                it.actionType,
                                it.serialNumber,
                                it.model,
                                it.name,
                                it.phoneNumber ?: "",
                                it.aadhaarNumber ?: "",
                                it.amount.toString(),
                                it.quantity.toString(),
                                sdf.format(Date(it.dateInMillis)),
                                it.userId,
                                it.description
                            )
                        }
                        exportCsv("Audit_History_Events", headers, rows, "History Events")
                    }
                )
            }

            // 3. Brand Stock Active CSV
            item {
                BackupExportRow(
                    title = "Brand Stock Active Inventory",
                    description = "IMEI-indexed devices by Brand, Variant, Color, and Warehouse (G/O)",
                    count = brandStockItems.size,
                    icon = Icons.Default.Smartphone,
                    isExporting = isExporting,
                    onExport = {
                        val headers = listOf("IMEI", "Brand", "Variant", "Color", "Warehouse", "Added By", "Added Date")
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val rows = brandStockItems.map {
                            listOf(
                                it.imei,
                                it.brand,
                                it.variant,
                                it.color,
                                it.warehouse,
                                it.addedByUser,
                                sdf.format(Date(it.addedDate))
                            )
                        }
                        exportCsv("Brand_Stock_Active", headers, rows, "Brand Stock Active")
                    }
                )
            }

            // 4. Brand Stock Transactions CSV
            item {
                BackupExportRow(
                    title = "Brand Stock Transactions Log",
                    description = "IN, OUT, and TRANSFER movements across warehouses G and O",
                    count = brandStockTransactions.size,
                    icon = Icons.Default.SyncAlt,
                    isExporting = isExporting,
                    onExport = {
                        val headers = listOf("Transaction ID", "IMEI", "Brand", "Variant", "Color", "Warehouse", "Type", "Operator", "Date", "Notes")
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val rows = brandStockTransactions.map {
                            listOf(
                                it.id,
                                it.imei,
                                it.brand,
                                it.variant,
                                it.color,
                                it.warehouse,
                                it.type,
                                it.operator,
                                sdf.format(Date(it.dateInMillis)),
                                it.notes ?: ""
                            )
                        }
                        exportCsv("Brand_Stock_Transactions", headers, rows, "Brand Stock Transactions")
                    }
                )
            }

            // 5. Unified Ledger & Partner Accounts CSV
            item {
                BackupExportRow(
                    title = "Unified Ledger & Accounts",
                    description = "Partner ledgers, payment out/in entries, and staff salary records",
                    count = allLedgerEntries.size,
                    icon = Icons.AutoMirrored.Filled.CompareArrows,
                    isExporting = isExporting,
                    onExport = {
                        val headers = listOf("Ledger ID", "Party ID", "Party Name", "Amount", "Entry Type", "Description", "Date")
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val partyMap = allParties.associateBy { it.id }
                        val rows = allLedgerEntries.map { l ->
                            val partyName = partyMap[l.partyId]?.name ?: "Unknown"
                            listOf(
                                l.id,
                                l.partyId,
                                partyName,
                                l.amount.toString(),
                                l.type,
                                l.description,
                                sdf.format(Date(l.timestamp))
                            )
                        }
                        exportCsv("Unified_Ledger_Entries", headers, rows, "Ledger Entries")
                    }
                )
            }

            // 6. Attendance & Staff Logs CSV
            item {
                BackupExportRow(
                    title = "Staff Attendance Logs",
                    description = "Daily check-in and check-out logs with location specs and status",
                    count = allAttendanceRecords.size,
                    icon = Icons.Default.Fingerprint,
                    isExporting = isExporting,
                    onExport = {
                        val headers = listOf("Record ID", "Staff Username", "Date", "Status", "Check-In", "Check-Out", "Location", "Notes")
                        val timeSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                        val rows = allAttendanceRecords.map { att ->
                            listOf(
                                att.id,
                                att.userId,
                                att.dateString,
                                att.status,
                                if (att.checkInTime > 0) timeSdf.format(Date(att.checkInTime)) else "-",
                                if (att.checkOutTime > 0) timeSdf.format(Date(att.checkOutTime)) else "-",
                                att.checkInLocationSpec ?: "-",
                                att.notes ?: ""
                            )
                        }
                        exportCsv("Staff_Attendance_Records", headers, rows, "Staff Attendance")
                    }
                )
            }

            // Backup Policy Guidance
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFF1976D2),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Multi-User Backup Recommendations",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "• Weekly Offsite Export: Admin should trigger a Full JSON backup every Saturday to Google Drive or an encrypted drive.\n" +
                            "• Real-Time Resilience: Offline persistent cache (200MB) keeps local stock synchronized during connectivity drops across all 20 terminals.\n" +
                            "• Photos Safety: Compressed WebP photos are automatically uploaded to Firebase Storage in background WorkManager jobs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun BackupExportRow(
    title: String,
    description: String,
    count: Int,
    icon: ImageVector,
    isExporting: Boolean,
    onExport: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "$count",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        maxLines = 2
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalButton(
                onClick = onExport,
                enabled = !isExporting && count > 0,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun escapeCsv(value: String): String {
    val containsSpecial = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")
    return if (containsSpecial) {
        "\"" + value.replace("\"", "\"\"") + "\""
    } else {
        value
    }
}
