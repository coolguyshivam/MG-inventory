package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HistoryEvent
import com.example.data.model.InventoryItem
import com.example.ui.theme.AccentBlue
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.SunsetOrange

@Composable
fun AnalyticsScreen(
    events: List<HistoryEvent>,
    inventory: List<InventoryItem>,
    modifier: Modifier = Modifier
) {
    // Calculative values
    val totalPurchases = remember(events) {
        events.filter { it.actionType == "PURCHASE" }.sumOf { it.amount }
    }

    val totalSales = remember(events) {
        events.filter { it.actionType == "SALE" }.sumOf { it.amount }
    }

    val totalReturns = remember(events) {
        events.filter { it.actionType == "RETURN" }.sumOf { it.amount }
    }

    val itemsInStockCount = remember(inventory) {
        inventory.filter { it.status == "STOCK" }.sumOf { it.quantity }
    }

    val itemsSoldCount = remember(inventory) {
        inventory.filter { it.status == "SOLD" }.sumOf { it.quantity }
    }

    val itemsInRepairCount = remember(inventory) {
        inventory.filter { it.status == "REPAIR" }.sumOf { it.quantity }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Business Metrics & Statistics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // General Ledger Overview Info Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .testTag("analytics_sales_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Total Sales",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "₹ ${String.format("%,.0f", totalSales)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen
                    )
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .testTag("analytics_purchase_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Total Purchases",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "₹ ${String.format("%,.0f", totalPurchases)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SunsetOrange
                    )
                }
            }
        }

        // Return vs Repair Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("analytics_losses_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Returns & General Adjustments",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(AccentBlue))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Returned Stock Worth", fontSize = 12.sp)
                    }
                    Text("₹ ${String.format("%,.2f", totalReturns)}", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                }
            }
        }

        // Stock Distribution Bar Progress Visualization
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Inventory Shares",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Stock vs Sold Progress indicators (Simulates chart bar natively)
                val totalQty = (itemsInStockCount + itemsSoldCount + itemsInRepairCount).coerceAtLeast(1)
                val stockShare = itemsInStockCount.toFloat() / totalQty
                val soldShare = itemsSoldCount.toFloat() / totalQty
                val repairShare = itemsInRepairCount.toFloat() / totalQty

                Text("Available Stock Units ($itemsInStockCount)", fontSize = 11.sp)
                LinearProgressIndicator(
                    progress = { stockShare },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = EmeraldGreen,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Units Transacted & Sold ($itemsSoldCount)", fontSize = 11.sp)
                LinearProgressIndicator(
                    progress = { soldShare },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = SunsetOrange,
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text("Repair Log Intake Status ($itemsInRepairCount)", fontSize = 11.sp)
                LinearProgressIndicator(
                    progress = { repairShare },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = Color(0xFFF1C40F),
                    trackColor = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }

        // Help Information panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Alert icon",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "All calculation metrics are synced live with the secure system Room local DB ledger.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
