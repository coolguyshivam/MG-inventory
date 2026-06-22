package com.example.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
fun BarcodeScannerMockDialog(
    onDismissRequest: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    suggestedImeis: List<String> = emptyList()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
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
                    } else {
                        Toast.makeText(context, "No barcode/IMEI detected.", Toast.LENGTH_SHORT).show()
                    }
                    onDismissRequest()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        context, 
                        "Live scanner unavailable: ${e.localizedMessage}", 
                        Toast.LENGTH_LONG
                    ).show()
                    onDismissRequest()
                }
                .addOnCanceledListener {
                    onDismissRequest()
                }
        } catch (t: Throwable) {
            Toast.makeText(
                context, 
                "Failed to launch live camera scanner: ${t.localizedMessage}", 
                Toast.LENGTH_LONG
            ).show()
            onDismissRequest()
        }
    }
}
