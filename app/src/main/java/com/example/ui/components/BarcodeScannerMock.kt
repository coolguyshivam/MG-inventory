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
    var showManualInput by remember { mutableStateOf(false) }
    var typedBarcode by remember { mutableStateOf("") }
    
    // Check permission status
    val cameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(
        context, 
        android.Manifest.permission.CAMERA
    )
    var hasCameraPermission by remember { 
        mutableStateOf(cameraPermission == android.content.pm.PackageManager.PERMISSION_GRANTED) 
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission needed. Falling back to input.", Toast.LENGTH_SHORT).show()
            showManualInput = true
        }
    }

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
                        onBarcodeScanned(rawValue)
                        onDismissRequest()
                    } else {
                        Toast.makeText(context, "No barcode detected.", Toast.LENGTH_SHORT).show()
                        onDismissRequest()
                    }
                }
                .addOnFailureListener { e ->
                    showManualInput = true
                }
                .addOnCanceledListener {
                    onDismissRequest()
                }
        } catch (t: Throwable) {
            showManualInput = true
        }
    }

    // Directly trigger scanning or fallback depending on GMS availability & permissions
    LaunchedEffect(hasCameraPermission) {
        val isGmsAvailable = try {
            com.google.android.gms.common.GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS
        } catch (t: Throwable) {
            false
        }

        if (!isGmsAvailable) {
            // Emulator or non-GMS device: Fall back instantly to avoid crashing
            showManualInput = true
        } else if (hasCameraPermission) {
            startScanner()
        } else {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Elegant and extremely lightweight manual fallback dialog for emulators/previews
    if (showManualInput) {
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
                    Text("Barcode & IMEI Entry", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Real-time scanner is loading or camera is simulated on emulator. Type or select a code below.",
                        style = MaterialTheme.typography.bodyMedium,
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
                                text = "Auto-suggested / Stock items in stock:",
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
}
