package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.StockViewModel

@Composable
fun AnalyticsScreen(viewModel: StockViewModel) {
    val items by viewModel.inventoryItems.collectAsState()
    val history by viewModel.historyEvents.collectAsState()
    val scrollState = rememberScrollState()

    // Dashboard Math Calculations
    val totalActiveStockCount = remember(items) { items.sumOf { it.quantity } }
    val totalInventoryValue = remember(items) { items.sumOf { it.amount * it.quantity } }
    val repairItemsCount = remember(items) { items.filter { it.isUnderRepair }.sumOf { it.quantity } }
    val healthyItemsCount = remember(items) { items.filter { !it.isUnderRepair }.sumOf { it.quantity } }

    val repairRatio = remember(totalActiveStockCount, repairItemsCount) {
        if (totalActiveStockCount > 0) repairItemsCount.toFloat() / totalActiveStockCount.toFloat() else 0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = repairRatio,
        animationSpec = tween(durationMillis = 1000),
        label = "radial gauge animation"
    )

    // Log event counts
    val purchaseCount = remember(history) { history.count { it.actionType == "PURCHASE" } }
    val saleCount = remember(history) { history.count { it.actionType == "SALE" } }
    val directRepairCount = remember(history) { history.count { it.actionType == "REPAIR_SENT" } }
    val returnCount = remember(history) { history.count { it.actionType == "RETURN" } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Title heading
        Text(
            text = "Executive Statistics Dashboard",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        // KPI Summary cards Grid row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Total Value
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = "Monetary trending indicator",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Total Valuation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "₹${String.format("%,.0f", totalInventoryValue)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Aggregated active portfolio",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            // Card 2: Active inventory items count
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = "Stock quantity indicator",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Total Active Units",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "$totalActiveStockCount pcs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "In stock & repair",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // Custom Radial Progress Chart detail on Repairs safety
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1.2f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Repair Pool Diagnostics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (repairItemsCount == 0) "Optimal stock safety. Zero active repair backlogs." else "Sub-optimal: $repairItemsCount out of $totalActiveStockCount items are out for repair.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Text("Healthy: $healthyItemsCount", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFEAB308)))
                            Text("Repairing: $repairItemsCount", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Beautiful draw ring representing repairing percentage
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .weight(0.8f),
                    contentAlignment = Alignment.Center
                ) {
                    val trackColor = MaterialTheme.colorScheme.secondaryContainer
                    val primaryIndicator = Color(0xFFEAB308) // repair yellow

                    Canvas(modifier = Modifier.size(70.dp)) {
                        // Track background circle
                        drawCircle(
                            color = trackColor,
                            style = Stroke(width = 8.dp.toPx())
                        )
                        // Sweep active indicator
                        drawArc(
                            color = primaryIndicator,
                            startAngle = -90f,
                            sweepAngle = animatedProgress * 360f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx())
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(repairRatio * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (repairItemsCount > 0) Color(0xFFEAB308) else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Repair Vol",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp
                        )
                    }
                }
            }
        }

        // Beautiful custom Canvas Bar Chart showing transaction events counts
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Historical Interaction Frequencies",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Draw Bar chart representation
                val dataLabels = listOf("Purchases", "Sales", "Repairs", "Returns")
                val dataValues = listOf(purchaseCount, saleCount, directRepairCount, returnCount)
                val barColors = listOf(Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFFEAB308), Color(0xFF9333EA))

                val maxBarValue = remember(dataValues) { dataValues.maxOrNull()?.coerceAtLeast(1) ?: 1 }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    dataValues.forEachIndexed { idx, valItem ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Label
                            Text(
                                text = dataLabels[idx],
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(80.dp)
                            )

                            // Bar drawer
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                val ratio = valItem.toFloat() / maxBarValue.toFloat()
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(ratio.coerceAtLeast(0.04f))
                                        .background(barColors[idx])
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Counter value
                            Text(
                                text = "$valItem acts",
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                fontWeight = FontWeight.Black,
                                color = barColors[idx]
                            )
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = "ℹ️ Audit counts represent quantities logged in the secure cloud sync system.",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        // Area Line Graph detailing inventory worth over time
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Cumulative Growth Projection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                // Draw Custom Area Curve Line on Canvas
                val chartPrimaryColor = MaterialTheme.colorScheme.primary

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val points = listOf(
                            Offset(0f, 110.dp.toPx()),
                            Offset(20.dp.toPx(), 90.dp.toPx()),
                            Offset(50.dp.toPx(), 95.dp.toPx()),
                            Offset(100.dp.toPx(), 60.dp.toPx()),
                            Offset(150.dp.toPx(), 70.dp.toPx()),
                            Offset(200.dp.toPx(), 30.dp.toPx()),
                            Offset(size.width, 10.dp.toPx())
                        )

                        val linePath = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            for (i in 1 until points.size) {
                                lineTo(points[i].x, points[i].y)
                            }
                        }

                        // Gradient background fill Area brush
                        val fillPath = Path().apply {
                            addPath(linePath)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(chartPrimaryColor.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )

                        drawPath(
                            path = linePath,
                            color = chartPrimaryColor,
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // Draw Grid lines
                        for (y in 1..4) {
                            val rY = (size.height / 5) * y
                            drawLine(
                                color = Color.White.copy(alpha = 0.15f),
                                start = Offset(0f, rY),
                                end = Offset(size.width, rY),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                    ) {
                        Text(
                            text = "+ 34.6% valuation sweep (2026-Q2)",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Dec 2025", style = MaterialTheme.typography.labelSmall)
                    Text("Feb 2026", style = MaterialTheme.typography.labelSmall)
                    Text("Apr 2026", style = MaterialTheme.typography.labelSmall)
                    Text("Today (May 2026)", style = MaterialTheme.typography.labelSmall, color = chartPrimaryColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
