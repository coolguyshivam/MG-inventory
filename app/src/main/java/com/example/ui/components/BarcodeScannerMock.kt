package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.code.scanner.GmsBarcodeScanning
import com.google.android.gms.code.scanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun BarcodeScannerMockDialog(
    onDismissRequest: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    suggestedImeis: List<String> = emptyList()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var typedBarcode by remember { mutableStateOf("") }
    var scanStatusMessage by remember { mutableStateOf("Ready for scanner input...") }
    var isBeeping by remember { mutableStateOf(false) }

    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
    }
    val gmsScanner = remember(context) {
        GmsBarcodeScanning.getClient(context, scannerOptions)
    }

    // Laser Animation Sweep
    val infiniteTransition = rememberInfiniteTransition(label = "Laser sweep")
    val laserOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Laser sweep"
    )

    // Quick mock data for IMEIs combined with dynamic suggestions!
    val sampleImeis = remember(suggestedImeis) {
        (suggestedImeis.take(10) + listOf(
            "354920056123456",
            "880439821876543",
            "998247716900124",
            "123456789012345",
            "774029921455667"
        )).distinct().filter { it.isNotBlank() }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            Button(
                onClick = {
                    if (typedBarcode.isNotBlank()) {
                        coroutineScope.launch {
                            isBeeping = true
                            scanStatusMessage = "IMEI Scanned successfully!"
                            delay(400)
                            onBarcodeScanned(typedBarcode.trim().uppercase())
                            onDismissRequest()
                        }
                    }
                },
                enabled = typedBarcode.isNotBlank()
            ) {
                Text("Confirm Manual")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scanner",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "High-Precision Barcode Scanner",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Button to trigger the physical device camera scanner!
                Button(
                    onClick = {
                        gmsScanner.startScan()
                            .addOnSuccessListener { barcode ->
                                val scannedValue = barcode.rawValue ?: barcode.displayValue ?: ""
                                if (scannedValue.isNotBlank()) {
                                    Toast.makeText(context, "Scanned successfully: $scannedValue", Toast.LENGTH_SHORT).show()
                                    onBarcodeScanned(scannedValue)
                                    onDismissRequest()
                                }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Scanning cancelled or not supported.", Toast.LENGTH_SHORT).show()
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = "Camera Scanner icon")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan with Device Camera", fontWeight = FontWeight.Bold)
                }

                HorizontalDivider()

                // Mock Camera Frame
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    // Scanning Sweep Laser
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val verticalPosition = maxHeight * laserOffset
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .offset(y = verticalPosition)
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            if (isBeeping) Color.Green else Color.Red,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }

                    // Framing Corners overlay
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .border(2.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    )

                    // Text status overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .fillMaxWidth()
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = scanStatusMessage,
                            color = if (isBeeping) Color.Green else Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Quick selector for testing
                Text(
                    text = "Or simulate scanning below:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(sampleImeis) { imei ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    coroutineScope.launch {
                                        isBeeping = true
                                        scanStatusMessage = "Scan Success!"
                                        typedBarcode = imei
                                        delay(500)
                                        onBarcodeScanned(imei)
                                        onDismissRequest()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = imei,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Scan",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Manual typed backup input
                OutlinedTextField(
                    value = typedBarcode,
                    onValueChange = { typedBarcode = it },
                    label = { Text("Manual IMEI input") },
                    placeholder = { Text("Type IMEI-XXXXX...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    )
}
