package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.ui.viewmodel.StockViewModel
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: StockViewModel,
    onTransactionSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isUploading by viewModel.isUploading.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: PURCHASE, 1: SALE, 2: RETURN, 3: REPAIR
    val tabs = listOf("PURCHASE", "SALE", "RETURN", "REPAIR_SENT")

    // Form states
    var model by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var serialNumber by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("1") }
    var aadhaarNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    var localPhotoUris by remember { mutableStateOf<List<String>>(emptyList()) }

    // Launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            localPhotoUris = localPhotoUris + it.toString()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            val uri = saveBitmapToCache(context, it)
            localPhotoUris = localPhotoUris + uri.toString()
        }
    }

    fun generateSimulatedPhoto() {
        val width = 400
        val height = 400
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply {
            color = android.graphics.Color.DKGRAY
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = android.graphics.Color.CYAN
        paint.textSize = 32f
        canvas.drawText("MOCK PHONE PHOTO", 40f, 100f, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawText("Model: $model", 40f, 160f, paint)
        canvas.drawText("S/N: $serialNumber", 40f, 220f, paint)
        val uri = saveBitmapToCache(context, bmp)
        localPhotoUris = localPhotoUris + uri.toString()
        Toast.makeText(context, "Simulated device photo generated!", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Upper Tabs
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title.replace("_", " "), fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    modifier = Modifier.testTag("tab_$title")
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Mode Info
                Text(
                    text = "New ${tabs[selectedTab].replace("_", " ")} Entry",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Brand & Model
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Brand & Model Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_model"),
                    singleLine = true
                )

                // Customer / Dealer Name
                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text(if (selectedTab == 0) "Supplier / Dealer Name" else "Customer Name") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_name"),
                    singleLine = true
                )

                // Row for phone & quantity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = contactPhone,
                        onValueChange = { contactPhone = it },
                        label = { Text("Contact Phone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_phone"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .width(80.dp)
                            .testTag("input_qty"),
                        singleLine = true
                    )
                }

                // IMEI / Serial Key
                OutlinedTextField(
                    value = serialNumber,
                    onValueChange = { serialNumber = it },
                    label = { Text("IMEI / Serial Key") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_serial"),
                    singleLine = true
                )

                // Amount
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (INR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_amount"),
                    singleLine = true,
                    leadingIcon = { Text("₹ ", style = MaterialTheme.typography.bodyLarge) }
                )

                // Aadhaar Number
                OutlinedTextField(
                    value = aadhaarNumber,
                    onValueChange = { aadhaarNumber = it },
                    label = { Text("Aadhaar Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_aadhaar"),
                    singleLine = true
                )

                // Address
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_address"),
                    maxLines = 2
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Remarks") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_description"),
                    maxLines = 3
                )

                // Photo Capture Section
                Text(
                    text = "Attached Photos (${localPhotoUris.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { cameraLauncher.launch() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("button_take_photo")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Camera")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Camera", fontSize = 11.sp)
                    }

                    Button(
                        onClick = { galleryLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("button_gallery_photo")
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Gallery")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gallery", fontSize = 11.sp)
                    }

                    Button(
                        onClick = { generateSimulatedPhoto() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("button_simulate_photo")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Mock Image")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Simulate", fontSize = 11.sp)
                    }
                }

                // Photo preview row
                if (localPhotoUris.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(localPhotoUris) { uriStr ->
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(uriStr),
                                    contentDescription = "Selected Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red.copy(alpha = 0.8f))
                                        .clickable { localPhotoUris = localPhotoUris - uriStr },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Submit Action Button
                Button(
                    onClick = {
                        // Basic Validate
                        if (model.isBlank() || customerName.isBlank() || amount.isBlank() || serialNumber.isBlank()) {
                            Toast.makeText(context, "Please fill Model, Name, Price, and Serial Number!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val amtParsed = amount.toDoubleOrNull()
                        if (amtParsed == null) {
                            Toast.makeText(context, "Encountered invalid Amount value!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val qtyParsed = quantity.toIntOrNull() ?: 1

                        // Execute
                        viewModel.executeTransaction(
                            context = context,
                            actionType = tabs[selectedTab],
                            model = model,
                            name = customerName,
                            phone = contactPhone,
                            serialNumber = serialNumber,
                            amount = amtParsed,
                            aadhaarNumber = aadhaarNumber,
                            quantity = qtyParsed,
                            address = address,
                            description = description,
                            localPhotoUris = localPhotoUris,
                            onComplete = {
                                Toast.makeText(context, "Transaction successfully applied!", Toast.LENGTH_SHORT).show()
                                // Reset form values
                                model = ""
                                customerName = ""
                                contactPhone = ""
                                serialNumber = ""
                                amount = ""
                                quantity = "1"
                                aadhaarNumber = ""
                                address = ""
                                description = ""
                                localPhotoUris = emptyList()
                                onTransactionSuccess()
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("submit_transaction_button"),
                    enabled = !isUploading && model.isNotBlank() && customerName.isNotBlank()
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Uploading & Saving...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("SUBMIT TRANSACTION", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri {
    val imagesDir = File(context.cacheDir, "images")
    if (!imagesDir.exists()) imagesDir.mkdir()
    val file = File(imagesDir, "photo_${System.currentTimeMillis()}.jpg")
    val fos = FileOutputStream(file)
    bitmap.compress(Bitmap.Config.ARGB_8888.let { Bitmap.CompressFormat.JPEG }, 90, fos)
    fos.close()
    return Uri.fromFile(file)
}
