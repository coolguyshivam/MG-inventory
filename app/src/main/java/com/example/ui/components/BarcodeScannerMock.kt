package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerMockDialog(
    onDismissRequest: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    suggestedImeis: List<String> = emptyList()
) {
    val context = LocalContext.current
    var typedBarcode by remember { mutableStateOf("") }
    
    // Function to start Google Play Services scanner
    val startScanner = {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            val scanner = GmsBarcodeScanning.getClient(context, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val rawValue = barcode.rawValue
                    if (!rawValue.isNullOrBlank()) {
                        onBarcodeScanned(rawValue.trim())
                        onDismissRequest()
                    } else {
                        Toast.makeText(context, "No barcode detected.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        context, 
                        "Scanner unavailable on this device/emulator: ${e.localizedMessage}. Please enter manually.", 
                        Toast.LENGTH_LONG
                    ).show()
                }
                .addOnCanceledListener {
                    // Do nothing on user cancel
                }
        } catch (t: Throwable) {
            Toast.makeText(
                context, 
                "Live scanning failed to start: ${t.localizedMessage}. Please type manually.", 
                Toast.LENGTH_LONG
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scanner icon",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Scan/Enter IMEI Or Barcode", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // 1. LIVE CAMERA SCAN TRIGGER
                Button(
                    onClick = { startScanner() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scanner",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Launch Live Camera Scanner", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 15.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        text = "OR ENTER MANUALLY", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }

                Text(
                    text = "If testing on emulator, you can type the barcode/IMEI value or select an auto-completed stock record below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = typedBarcode,
                    onValueChange = { typedBarcode = it },
                    label = { Text("IMEI or Barcode") },
                    placeholder = { Text("E.g., 356829103847291...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (suggestedImeis.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Auto-suggested warehouse records:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(suggestedImeis) { imei ->
                                SuggestionChip(
                                    onClick = { typedBarcode = imei },
                                    label = { Text(imei, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (typedBarcode.isNotBlank()) {
                        onBarcodeScanned(typedBarcode.trim())
                        onDismissRequest()
                    } else {
                        Toast.makeText(context, "Please enter or select a valid code.", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}
