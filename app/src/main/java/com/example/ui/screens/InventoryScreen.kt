package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InventoryItem
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.SunsetOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    items: List<InventoryItem>,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("ALL") } // ALL, STOCK, SOLD, REPAIR, RETURNED

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val filteredItems = remember(items, searchQuery, selectedStatusFilter) {
        items.filter { item ->
            val matchQuery = item.model.contains(searchQuery, ignoreCase = true) ||
                    item.serialNumber.contains(searchQuery, ignoreCase = true) ||
                    item.supplierOrCustomerName.contains(searchQuery, ignoreCase = true)

            val matchStatus = selectedStatusFilter == "ALL" || item.status == selectedStatusFilter

            matchQuery && matchStatus
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("inventory_search_input"),
            placeholder = { Text("Search Model, IMEI, or Name") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
            )
        )

        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("ALL", "STOCK", "SOLD", "REPAIR", "RETURNED")
            filters.forEach { filter ->
                FilterChip(
                    selected = selectedStatusFilter == filter,
                    onClick = { selectedStatusFilter = filter },
                    label = { 
                        Text(
                            text = filter,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ) 
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("filter_chip_$filter")
                )
            }
        }

        if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "No Items Found",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No stock items matches search filter.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inventory_item_${item.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.model,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                val statusColor = when (item.status) {
                                    "STOCK" -> EmeraldGreen
                                    "SOLD" -> SunsetOrange
                                    "REPAIR" -> Color(0xFFF1C40F)
                                    "RETURNED" -> AccentBlue
                                    else -> Color.Gray
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(statusColor.copy(alpha = 0.15f))
                                        .border(1.dp, statusColor, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = item.status,
                                        color = statusColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "IMEI / SERIAL",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable {
                                            clipboardManager.setText(AnnotatedString(item.serialNumber))
                                            Toast.makeText(context, "IMEI Copied", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text(
                                            text = item.serialNumber,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Copy Serial",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "AMOUNT / PRICE",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "INR ${String.format("%,.2f", item.price)}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (item.status == "STOCK") "Supplier: ${item.supplierOrCustomerName}" else "Customer: ${item.supplierOrCustomerName}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "Qty: ${item.quantity} unit(s)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
