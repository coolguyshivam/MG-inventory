package com.example.ui.components

import androidx.compose.runtime.Composable

/**
 * Replaced legacy mock with real working CameraX & ML Kit Smart IMEI scanner.
 * Retained for backwards compatibility across existing screen calls.
 */
@Composable
fun BarcodeScannerMockDialog(
    onDismissRequest: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    onMultipleBarcodesScanned: ((List<String>) -> Unit)? = null,
    suggestedImeis: List<String> = emptyList()
) {
    SmartImeiScannerDialog(
        onDismissRequest = onDismissRequest,
        onBarcodeScanned = onBarcodeScanned,
        onMultipleBarcodesScanned = onMultipleBarcodesScanned,
        suggestedImeis = suggestedImeis
    )
}
