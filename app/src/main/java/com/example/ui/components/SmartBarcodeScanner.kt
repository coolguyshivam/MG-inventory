package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.util.AppUtils
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Intelligent IMEI parser that detects valid 15-digit IMEIs from:
 * 1. Physical 1D and 2D barcodes (Code 128, Code 39, QR code, EAN)
 * 2. Printed box labels (e.g. "IMEI: 358912345678901", "IMEI 1: ...", "IMEI 2: ...")
 * 3. Phone screen displays (Settings > About phone or *#06# dialer screen)
 * 4. Formatted sequences with spaces/hyphens (e.g. "35 891234 567890 1")
 */
object SmartImeiExtractor {
    // Regex for exactly 15 numeric digits surrounded by non-digits or word boundaries
    private val DIGITS_15_REGEX = Regex("""(?<!\d)\d{15}(?!\d)""")

    // Formatted IMEI with spaces, slashes, or dashes: e.g. 35 123456 789012 3 or 35-123456-789012-3
    private val FORMATTED_IMEI_REGEX = Regex("""(?<!\d)(\d{2})[\s\-\/]?(\d{6})[\s\-\/]?(\d{6})[\s\-\/]?(\d{1})(?!\d)""")

    // Labeled IMEI in box text or screen: e.g. "IMEI: 358912345678901", "IMEI 1: 35...", "IMEI2 86..."
    private val LABELED_IMEI_REGEX = Regex("""IMEI\s*(?:[12]|One|Two)?\s*[:#\/\-]?\s*([0-9\s\-\/]{15,22})""", RegexOption.IGNORE_CASE)

    fun extractImeisFromText(text: String): List<String> {
        val results = linkedSetOf<String>()

        // 1. Check labeled patterns first (highest confidence for box labels and phone screens)
        LABELED_IMEI_REGEX.findAll(text).forEach { match ->
            val rawMatched = match.groupValues[1]
            val digitsOnly = rawMatched.filter { it.isDigit() }
            if (digitsOnly.length >= 15) {
                val candidate = digitsOnly.take(15)
                if (isValidImeiCandidate(candidate)) {
                    results.add(candidate)
                }
            }
        }

        // 2. Formatted sequences
        FORMATTED_IMEI_REGEX.findAll(text).forEach { match ->
            val digits = match.value.filter { it.isDigit() }
            if (digits.length == 15 && isValidImeiCandidate(digits)) {
                results.add(digits)
            }
        }

        // 3. Raw 15 digits
        DIGITS_15_REGEX.findAll(text).forEach { match ->
            val candidate = match.value
            if (isValidImeiCandidate(candidate)) {
                results.add(candidate)
            }
        }

        return results.toList()
    }

    fun extractImeiFromBarcode(barcodeRaw: String): String? {
        val clean = barcodeRaw.trim()
        val digitsOnly = clean.filter { it.isDigit() }

        // Standard 15-digit IMEI
        if (digitsOnly.length == 15 && isValidImeiCandidate(digitsOnly)) {
            return digitsOnly
        }

        // 16-digit IMEISV (Software Version): first 15 digits is the standard IMEI
        if (digitsOnly.length == 16 && isValidImeiCandidate(digitsOnly.take(15))) {
            return digitsOnly.take(15)
        }

        // Check labeled patterns in raw barcode string
        val candidates = extractImeisFromText(clean)
        if (candidates.isNotEmpty()) {
            return candidates.first()
        }

        // Alphanumeric Serial Number fallback if 8..20 chars
        if (clean.length in 8..22 && clean.all { it.isLetterOrDigit() }) {
            return clean
        }

        return null
    }

    fun isValidImeiCandidate(candidate: String): Boolean {
        if (candidate.length != 15 || !candidate.all { it.isDigit() }) return false
        // Exclude dummy numbers like 000000000000000 or 111111111111111
        if (candidate.all { it == candidate[0] }) return false
        return true
    }
}

/**
 * Real working CameraX + ML Kit Dual-Engine Scanner Dialog.
 * Simultaneously scans barcodes AND extracts printed IMEI numbers from box labels or screens.
 */
@Composable
fun SmartImeiScannerDialog(
    onDismissRequest: () -> Unit,
    onBarcodeScanned: (String) -> Unit,
    onMultipleBarcodesScanned: ((List<String>) -> Unit)? = null,
    suggestedImeis: List<String> = emptyList()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permissions state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission needed to scan barcodes & boxes", Toast.LENGTH_LONG).show()
        }
    }

    // Scanner state
    var isContinuousMode by remember { mutableStateOf(false) }
    var isTorchOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }
    val scannedImeis = remember { mutableStateListOf<String>() }
    var manualInput by remember { mutableStateOf("") }
    var lastDetectedSource by remember { mutableStateOf<String?>(null) }
    var lastDetectedValue by remember { mutableStateOf<String?>(null) }
    var detectedOptionsOnBox by remember { mutableStateOf<List<String>>(emptyList()) }
    var cameraInitError by remember { mutableStateOf<String?>(null) }

    // Last detection timestamp for debounce
    var lastScanTimestamp by remember { mutableLongStateOf(0L) }
    var lastScannedImei by remember { mutableStateOf("") }

    // Sound & Haptic notification
    fun playChime() {
        AppUtils.performHapticFeedback(context)
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 130)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    toneGen.release()
                } catch (_: Exception) {}
            }, 250)
        } catch (_: Exception) {}
    }

    // Handle incoming confirmed IMEI
    fun handleConfirmedImei(imei: String, source: String) {
        val now = System.currentTimeMillis()
        if (imei == lastScannedImei && now - lastScanTimestamp < 1500L) {
            return // Debounce repeated frames of same code
        }
        lastScanTimestamp = now
        lastScannedImei = imei
        lastDetectedValue = imei
        lastDetectedSource = source

        playChime()

        if (!isContinuousMode) {
            Handler(Looper.getMainLooper()).postDelayed({
                onBarcodeScanned(imei)
                onDismissRequest()
            }, 400)
        } else {
            if (scannedImeis.contains(imei)) {
                Toast.makeText(context, "Duplicate! Already in queue: $imei", Toast.LENGTH_SHORT).show()
            } else {
                scannedImeis.add(imei)
                Toast.makeText(context, "Added ($source): $imei", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Google Play Services Code Scanner fallback
    fun launchGmsCodeScanner() {
        try {
            val options = GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            val scanner = GmsBarcodeScanning.getClient(context, options)
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val raw = barcode.rawValue?.trim() ?: ""
                    val imei = SmartImeiExtractor.extractImeiFromBarcode(raw) ?: raw
                    if (imei.isNotBlank()) {
                        handleConfirmedImei(imei, "Google Code Scanner")
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Scanner closed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
        } catch (t: Throwable) {
            Toast.makeText(context, "Error: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // Photo Gallery OCR fallback (for difficult box labels)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                val inputImage = InputImage.fromFilePath(context, selectedUri)
                val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val barcodeScanner = BarcodeScanning.getClient()

                barcodeScanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        var foundBarcodeImei: String? = null
                        for (b in barcodes) {
                            val raw = b.rawValue ?: continue
                            val extracted = SmartImeiExtractor.extractImeiFromBarcode(raw)
                            if (extracted != null) {
                                foundBarcodeImei = extracted
                                break
                            }
                        }
                        if (foundBarcodeImei != null) {
                            handleConfirmedImei(foundBarcodeImei, "Photo Barcode")
                        } else {
                            textRecognizer.process(inputImage)
                                .addOnSuccessListener { visionText ->
                                    val imeis = SmartImeiExtractor.extractImeisFromText(visionText.text)
                                    if (imeis.isNotEmpty()) {
                                        if (imeis.size > 1) {
                                            detectedOptionsOnBox = imeis
                                        }
                                        handleConfirmedImei(imeis.first(), "Photo Box Text")
                                    } else {
                                        Toast.makeText(context, "No IMEI or Barcode found in selected image", Toast.LENGTH_LONG).show()
                                    }
                                }
                                .addOnFailureListener {
                                    Toast.makeText(context, "OCR failed on image", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to inspect image: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Request camera permission on mount if missing
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Laser scanning animation line
    val infiniteTransition = rememberInfiniteTransition(label = "LaserTransition")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LaserPosition"
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Smart IMEI & Barcode Scanner",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Dual-Engine: Barcodes + Box/Screen OCR",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Continuous Multi-Scan Toggle Pill
                Surface(
                    color = if (isContinuousMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                if (isContinuousMode) Icons.Default.Layers else Icons.Default.Filter1,
                                contentDescription = null,
                                tint = if (isContinuousMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isContinuousMode) "Continuous Batch Scan Mode" else "Single Item Scan Mode",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = if (isContinuousMode) "Keep camera active to scan carton in bulk" else "Auto-confirms and closes on first scan",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isContinuousMode,
                            onCheckedChange = { isContinuousMode = it },
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Camera Viewfinder Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission && cameraInitError == null) {
                        // Live CameraX Preview & Image Analysis
                        val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
                        val barcodeScanner = remember {
                            val options = BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                                .build()
                            BarcodeScanning.getClient(options)
                        }
                        val textRecognizer = remember {
                            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        }

                        DisposableEffect(Unit) {
                            onDispose {
                                cameraExecutor.shutdown()
                                barcodeScanner.close()
                                textRecognizer.close()
                            }
                        }

                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }

                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .setTargetResolution(Size(1280, 720))
                                            .build()

                                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                            processLiveFrame(
                                                imageProxy = imageProxy,
                                                barcodeScanner = barcodeScanner,
                                                textRecognizer = textRecognizer,
                                                onImeiDetected = { imei, source ->
                                                    Handler(Looper.getMainLooper()).post {
                                                        handleConfirmedImei(imei, source)
                                                    }
                                                },
                                                onMultipleImeisDetected = { imeis ->
                                                    Handler(Looper.getMainLooper()).post {
                                                        if (imeis.size > 1) {
                                                            detectedOptionsOnBox = imeis
                                                        }
                                                    }
                                                }
                                            )
                                        }

                                        cameraProvider.unbindAll()
                                        val cam = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                        cameraControl = cam.cameraControl
                                    } catch (e: Exception) {
                                        Log.e("SmartScanner", "Camera init failed", e)
                                        cameraInitError = e.localizedMessage
                                    }
                                }, ContextCompat.getMainExecutor(ctx))

                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Reticle Overlay Canvas with animated laser
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val boxWidth = size.width * 0.85f
                            val boxHeight = size.height * 0.55f
                            val left = (size.width - boxWidth) / 2f
                            val top = (size.height - boxHeight) / 2f
                            val right = left + boxWidth
                            val bottom = top + boxHeight

                            // Dark Scrim around scanner target
                            drawRect(
                                color = Color.Black.copy(alpha = 0.45f)
                            )

                            // Clear cutout
                            drawRoundRect(
                                color = Color.Transparent,
                                topLeft = Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(boxWidth, boxHeight),
                                cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                                blendMode = androidx.compose.ui.graphics.BlendMode.Clear
                            )

                            // Corner Target Guides
                            val cornerLen = 32.dp.toPx()
                            val strokeW = 4.dp.toPx()
                            val targetColor = if (lastDetectedValue != null) Color(0xFF00E676) else Color(0xFF29B6F6)

                            // Top-Left
                            drawLine(targetColor, Offset(left, top), Offset(left + cornerLen, top), strokeW)
                            drawLine(targetColor, Offset(left, top), Offset(left, top + cornerLen), strokeW)

                            // Top-Right
                            drawLine(targetColor, Offset(right, top), Offset(right - cornerLen, top), strokeW)
                            drawLine(targetColor, Offset(right, top), Offset(right, top + cornerLen), strokeW)

                            // Bottom-Left
                            drawLine(targetColor, Offset(left, bottom), Offset(left + cornerLen, bottom), strokeW)
                            drawLine(targetColor, Offset(left, bottom), Offset(left, bottom - cornerLen), strokeW)

                            // Bottom-Right
                            drawLine(targetColor, Offset(right, bottom), Offset(right - cornerLen, bottom), strokeW)
                            drawLine(targetColor, Offset(right, bottom), Offset(right, bottom - cornerLen), strokeW)

                            // Animated Scanning Laser Line
                            val laserY = top + (boxHeight * laserPosition)
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        targetColor.copy(alpha = 0.85f),
                                        Color.White,
                                        targetColor.copy(alpha = 0.85f),
                                        Color.Transparent
                                    )
                                ),
                                start = Offset(left + 8.dp.toPx(), laserY),
                                end = Offset(right - 8.dp.toPx(), laserY),
                                strokeWidth = 3.dp.toPx()
                            )
                        }

                        // Scanning HUD Instruction & Torch Controls
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            // Top Badges
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(Color(0xFF00E676), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "AI Live Recognition Active",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                // Torch Button
                                Surface(
                                    color = if (isTorchOn) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable {
                                            val nextState = !isTorchOn
                                            isTorchOn = nextState
                                            cameraControl?.enableTorch(nextState)
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                            contentDescription = "Flashlight",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            // Bottom Instruction Label
                            Surface(
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp)
                            ) {
                                Text(
                                    text = "Aim at barcode or printed IMEI on box / screen",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    } else if (!hasCameraPermission) {
                        // Permission request fallback card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Camera Permission Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Grant camera permission to enable live barcode and box text scanning.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Allow Camera Access")
                            }
                        }
                    } else {
                        // Camera hardware error / Virtual camera fallback
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Camera Stream Unavailable",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Use Google Code Scanner or inspect photo from gallery.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(onClick = { launchGmsCodeScanner() }) {
                                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Launch Google Scanner")
                            }
                        }
                    }
                }

                // Detection Confirmation Banner
                if (lastDetectedValue != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFE8F5E9),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E7D32)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2E7D32),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = lastDetectedValue ?: "",
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF1B5E20),
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Detected via ${lastDetectedSource ?: "Scanner"}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF2E7D32)
                                    )
                                }
                            }

                            if (!isContinuousMode) {
                                Text(
                                    text = "Confirming...",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }

                // Dual-SIM or Multiple IMEIs detected on Box Chips
                if (detectedOptionsOnBox.size > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Select from box IMEIs:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(detectedOptionsOnBox) { candidate ->
                            SuggestionChip(
                                onClick = { handleConfirmedImei(candidate, "Box Multi-IMEI") },
                                label = {
                                    Text(candidate, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                }
                            )
                        }
                    }
                }

                // Continuous Queue items
                if (isContinuousMode && scannedImeis.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scanned Queue (${scannedImeis.size} items):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { scannedImeis.clear() },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 100.dp)
                    ) {
                        items(scannedImeis) { imei ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = imei,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    IconButton(
                                        onClick = { scannedImeis.remove(imei) },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Alternative Options & Manual Entry
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { launchGmsCodeScanner() },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Google Scanner", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan Box Photo", fontSize = 11.sp)
                    }

                    if (isContinuousMode && scannedImeis.isNotEmpty()) {
                        Button(
                            onClick = {
                                if (onMultipleBarcodesScanned != null) {
                                    onMultipleBarcodesScanned(scannedImeis.toList())
                                } else {
                                    scannedImeis.firstOrNull()?.let { onBarcodeScanned(it) }
                                }
                                onDismissRequest()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Done (${scannedImeis.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Manual Input Bar
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        label = { Text("Manual IMEI input", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilledTonalButton(
                        onClick = {
                            val clean = manualInput.trim()
                            if (clean.isNotBlank()) {
                                handleConfirmedImei(clean, "Manual Input")
                                manualInput = ""
                            }
                        },
                        enabled = manualInput.isNotBlank()
                    ) {
                        Text("Add", fontSize = 12.sp)
                    }
                }

                // Quick Picks for suggested IMEIs (if provided)
                if (suggestedImeis.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(suggestedImeis.take(6)) { imei ->
                            SuggestionChip(
                                onClick = { handleConfirmedImei(imei, "Quick Pick") },
                                label = { Text(imei, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Image analysis loop combining Barcode scanning and OCR Text Recognition.
 */
@OptIn(ExperimentalGetImage::class)
private fun processLiveFrame(
    imageProxy: ImageProxy,
    barcodeScanner: BarcodeScanner,
    textRecognizer: TextRecognizer,
    onImeiDetected: (String, String) -> Unit,
    onMultipleImeisDetected: (List<String>) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val rotation = imageProxy.imageInfo.rotationDegrees
    val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

    // Run Barcode scanning
    barcodeScanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            var detectedImei: String? = null
            for (barcode in barcodes) {
                val raw = barcode.rawValue ?: continue
                val extracted = SmartImeiExtractor.extractImeiFromBarcode(raw)
                if (extracted != null) {
                    detectedImei = extracted
                    break
                }
            }

            if (detectedImei != null) {
                onImeiDetected(detectedImei, "Barcode")
                imageProxy.close()
            } else {
                // If no barcode contained an IMEI, run Text Recognition for printed box label or screen
                try {
                    textRecognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            val text = visionText.text
                            val imeis = SmartImeiExtractor.extractImeisFromText(text)
                            if (imeis.isNotEmpty()) {
                                onImeiDetected(imeis.first(), "Box / Screen Text")
                                if (imeis.size > 1) {
                                    onMultipleImeisDetected(imeis)
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                } catch (e: Exception) {
                    imageProxy.close()
                }
            }
        }
        .addOnFailureListener {
            imageProxy.close()
        }
}
