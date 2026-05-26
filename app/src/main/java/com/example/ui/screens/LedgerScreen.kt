package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(viewModel: StockViewModel) {
    val allParties by viewModel.allParties.collectAsState()
    val allLedgerEntries by viewModel.allLedgerEntries.collectAsState()

    var showAddPartyDialog by remember { mutableStateOf(false) }
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(onClick = { showPaymentDialog = true }) {
                        Text("Add Payment")
                    }
                }
            )
            
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                val balanceColor = if (party.balance > 0) Color(0xFF4CAF50) else if (party.balance < 0) Color.Red else MaterialTheme.colorScheme.onSurface 
                val balanceText = if (party.balance > 0) "They owe us: ₹${party.balance}" else if (party.balance < 0) "We owe them: ₹${-party.balance}" else "Balance: Settled"
                Text(balanceText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = balanceColor)
            }
            HorizontalDivider()
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(partyLedger) { entry ->
                    val color = when (entry.type) {
                        "SALE" -> Color(0xFF10B981)
                        "PURCHASE" -> Color(0xFF3B82F6)
                        "PAYMENT_IN" -> Color(0xFF10B981)
                        "PAYMENT_OUT" -> Color(0xFFEAB308)
                        "RETURN" -> Color(0xFF9333EA)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val sign = if (entry.type == "SALE" || entry.type == "PAYMENT_OUT" || entry.type == "REPAIR_SENT") "+" else "-"
                    val formattedDate = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.timestamp))

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.type.replace("_", " "), fontWeight = FontWeight.Bold, color = color)
                                Text(formattedDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (entry.description.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(entry.description, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Text("$sign₹${entry.amount}", fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
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
        AlertDialog(
            onDismissRequest = { showAddPartyDialog = false },
            title = { Text("Add Party") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, singleLine = true)
                    OutlinedTextField(value = aadhaar, onValueChange = { aadhaar = it }, label = { Text("Aadhaar") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.addParty(name, phone, aadhaar)
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
        var type by remember { mutableStateOf("PAYMENT_IN") } // or PAYMENT_OUT
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
}
