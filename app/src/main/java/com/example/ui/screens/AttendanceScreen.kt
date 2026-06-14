package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import coil.compose.AsyncImage
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.model.AttendanceRecord
import com.example.data.model.LeaveApplication
import com.example.data.model.User
import com.example.ui.viewmodel.StockViewModel
import com.example.util.AppUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val loggedInUser by viewModel.loggedInUser.collectAsState()
    val allAttendance by viewModel.allAttendanceRecords.collectAsState()
    val allLeaves by viewModel.allLeaveApplications.collectAsState()
    val allUsers by viewModel.allUsers.collectAsState()

    // Screen Sub-tab: 0 = My Attendance, 1 = Leaves List / Admin Panel
    var subTabSelection by remember { mutableStateOf(0) }
    val monthsList = remember { listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December") }

    // Admin/Manager selected employee details
    val isAdminOrManager = loggedInUser?.role in listOf("Admin", "Manager")
    var selectedEmployeeScope by remember { mutableStateOf<User?>(null) }
    var employeeFilterExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(allUsers, loggedInUser) {
        if (loggedInUser?.role in listOf("Admin", "Manager")) {
            val nonAdminManagerUser = allUsers.find { it.role != "Admin" && it.role != "Manager" }
            if (nonAdminManagerUser != null && (selectedEmployeeScope == null || selectedEmployeeScope?.role in listOf("Admin", "Manager"))) {
                selectedEmployeeScope = nonAdminManagerUser
            }
        } else {
            selectedEmployeeScope = loggedInUser
        }
    }

    // Synchronize current date
    val now = remember { System.currentTimeMillis() }
    val sdfDay = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayStr = sdfDay.format(Date(now))

    // Handle Active Target Employee Attendance
    val targetUser = selectedEmployeeScope ?: loggedInUser ?: User("admin", "", "Admin")
    val targetRecords = allAttendance.filter { it.userId == targetUser.username }
    val targetTodayRecord = targetRecords.find { it.dateString == todayStr }

    val currentMonthAbsences = remember(allAttendance, targetUser) {
        try {
            val calendar = java.util.Calendar.getInstance()
            val year = calendar.get(java.util.Calendar.YEAR)
            val month = calendar.get(java.util.Calendar.MONTH) // 0-indexed
            val dayOfMonth = calendar.get(java.util.Calendar.DAY_OF_MONTH)
            
            var count = 0
            if (targetUser.role != "Admin" && targetUser.role != "Manager") {
                for (day in 1 until dayOfMonth) {
                    val dateStr = String.format("%04d-%02d-%02d", year, month + 1, day)
                    val record = allAttendance.find { it.userId == targetUser.username && it.dateString == dateStr }
                    if (record == null || record.status == "Absent") {
                        count++
                    }
                }
            }
            count
        } catch(e: Exception) {
            0
        }
    }

    // Calendar UI variables
    val calendarInstance = remember { Calendar.getInstance() }
    var currentYear by remember { mutableStateOf(calendarInstance.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableStateOf(calendarInstance.get(Calendar.MONTH)) } // 0-indexed

    val monthlyStats = remember(allAttendance, targetUser, currentYear, currentMonth) {
        var presentCount = 0
        var leaveCount = 0
        var absentCount = 0
        
        if (targetUser.role != "Admin" && targetUser.role != "Manager") {
            val dCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, currentYear)
                set(Calendar.MONTH, currentMonth)
                set(Calendar.DAY_OF_MONTH, 1)
            }
            val maxDays = dCal.getActualMaximum(Calendar.DAY_OF_MONTH)
            
            for (day in 1..maxDays) {
                val dateStr = String.format("%04d-%02d-%02d", currentYear, currentMonth + 1, day)
                val record = allAttendance.find { it.userId == targetUser.username && it.dateString == dateStr }
                if (record != null) {
                    if (record.status == "Present") {
                        presentCount++
                    } else if (record.status == "On Leave") {
                        leaveCount++
                    } else if (record.status == "Absent") {
                        absentCount++
                    }
                } else {
                    val todayC = Calendar.getInstance()
                    val queryC = Calendar.getInstance().apply {
                        set(Calendar.YEAR, currentYear)
                        set(Calendar.MONTH, currentMonth)
                        set(Calendar.DAY_OF_MONTH, day)
                    }
                    if (queryC.before(todayC)) {
                        absentCount++
                    }
                }
            }
        }
        Triple(presentCount, leaveCount, absentCount)
    }

    // Dialog flags
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showModifyAttendanceDialog by remember { mutableStateOf(false) }
    var clickedRecordDetailDialog by remember { mutableStateOf<AttendanceRecord?>(null) }
    var selectedTeamDateStr by remember { mutableStateOf(todayStr) }
    var showWhatsAppSettingsDialog by remember { mutableStateOf(false) }

    val whatsAppWebhookUrl by viewModel.whatsAppWebhookUrl.collectAsState()
    val whatsAppEnable by viewModel.whatsAppEnable.collectAsState()
    val whatsappSparePhoneEnable by viewModel.whatsappSparePhoneEnable.collectAsState()
    val whatsappTargetPhone by viewModel.whatsappTargetPhone.collectAsState()

    var tempWebhookUrl by remember(whatsAppWebhookUrl) { mutableStateOf(whatsAppWebhookUrl) }
    var tempWhatsAppEnable by remember(whatsAppEnable) { mutableStateOf(whatsAppEnable) }
    var tempSparePhoneEnable by remember(whatsappSparePhoneEnable) { mutableStateOf(whatsappSparePhoneEnable) }
    var tempTargetPhone by remember(whatsappTargetPhone) { mutableStateOf(whatsappTargetPhone) }

    LaunchedEffect(Unit) {
        viewModel.loadWhatsAppSettings(context)
    }

    // Camera attachments
    val tempCameraUriState = remember { mutableStateOf<Uri?>(null) }
    var isCheckingInAction by remember { mutableStateOf(true) } // true = check-in, false = check-out

    // Camera capture handler
    val selfieLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraUriState.value?.let { uri ->
                val base64 = AppUtils.uriToBase64(context, uri)
                if (base64 != null) {
                    AppUtils.getCurrentLocation(context) { locSpec ->
                        if (isCheckingInAction) {
                            viewModel.markCheckIn(context, base64, locSpec, targetUser.username)
                            Toast.makeText(context, "Checked In Successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.markCheckOut(context, base64, locSpec, targetUser.username)
                            Toast.makeText(context, "Checked Out Successfully!", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Failed to capture high fidelity selfie.", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Verification Scan Cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // Delegate state to break compile-time circular dependency loops in local Compose definitions without triggering infinite recomposition
    val triggerSelfieCaptureRef = remember { arrayOfNulls<(Boolean) -> Unit>(1) }

    // Permission launcher for Camera & Location
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[android.Manifest.permission.CAMERA] ?: false
        val locationCoarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val locationFineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false

        if (cameraGranted && (locationCoarseGranted || locationFineGranted)) {
            triggerSelfieCaptureRef[0]?.invoke(isCheckingInAction)
        } else {
            Toast.makeText(context, "Camera & Location permissions are required for secure check-in/out stamps.", Toast.LENGTH_LONG).show()
        }
    }

    // Capture Trigger
    val triggerSelfieCapture = remember {
        { isCheckIn: Boolean ->
            isCheckingInAction = isCheckIn
            val cameraPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
            val locationCoarsePermission = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
            val locationFinePermission = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)

            val hasCamera = cameraPermission == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarseLoc = locationCoarsePermission == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasFineLoc = locationFinePermission == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasCamera || (!hasCoarseLoc && !hasFineLoc)) {
                permissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.CAMERA,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION,
                        android.Manifest.permission.ACCESS_FINE_LOCATION
                    )
                )
                Toast.makeText(context, "Requesting Camera & location permissions...", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    val directory = File(context.cacheDir, "selfie_scans")
                    if (!directory.exists()) directory.mkdirs()
                    val tempFile = File.createTempFile("selfie_${System.currentTimeMillis()}", ".jpg", directory)
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
                    tempCameraUriState.value = uri
                    selfieLauncher.launch(uri)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Security Camera Integration Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    triggerSelfieCaptureRef[0] = triggerSelfieCapture

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Aesthetic Section Tabs Row with Settings Option next to it
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TabRow(
                    selectedTabIndex = subTabSelection,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = subTabSelection == 0,
                        onClick = { subTabSelection = 0 },
                        text = { Text("Attendance Board", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = subTabSelection == 1,
                        onClick = { subTabSelection = 1 },
                        text = { Text("Leave Panel", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.CardTravel, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = subTabSelection == 2,
                        onClick = { subTabSelection = 2 },
                        text = { Text("Team Calendar", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
            }
            IconButton(
                onClick = { showWhatsAppSettingsDialog = true },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SettingsPhone,
                    contentDescription = "WhatsApp Notification Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Excessive Absence warning card
            if (currentMonthAbsences > 4 && subTabSelection == 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Absence Warning",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = "Excessive Absences Detected!",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "${targetUser.username} has been absent for $currentMonthAbsences days this month. This has been reported to Manager and Admin.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // Admin/Manager Selected Employee Banner
            if (isAdminOrManager && subTabSelection == 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SupervisorAccount,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Employee Dashboard View",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Currently viewing: ${targetUser.username} (${targetUser.role})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                    )
                                }
                            }
                            
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { employeeFilterExpanded = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    contentPadding = PaddingValues(vertical = 10.dp)
                                ) {
                                    Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Switch Active Employee / Team", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                DropdownMenu(
                                    expanded = employeeFilterExpanded,
                                    onDismissRequest = { employeeFilterExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    allUsers.filter { it.role != "Admin" && it.role != "Manager" }.forEach { user ->
                                        DropdownMenuItem(
                                            text = { Text(user.username + " (" + user.role + ")", fontWeight = FontWeight.Medium) },
                                            onClick = {
                                                selectedEmployeeScope = user
                                                employeeFilterExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val isTargetAdminOrManager = targetUser.role == "Admin" || targetUser.role == "Manager"

            if (isTargetAdminOrManager && subTabSelection == 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SupervisorAccount,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Administrative Control View",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Admins & Managers do not register attendance logs. Please select an Employee from the 'Switch Team' menu above to monitor their attendance, check leave approvals, and run payroll smoothly.",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            if (subTabSelection == 0) {
                // TODAY CHECK-IN/OUT INTERACTIVE DECK
                if (!isTargetAdminOrManager) {
                    item {
                        val isSelfViewing = targetUser.username == loggedInUser?.username
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (targetTodayRecord?.checkOutTime != 0L && targetTodayRecord?.checkInTime != 0L) {
                                Color(0xFFE8F5E9).copy(alpha = 0.8f) // Soft green for complete
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Today's Attendance for ${targetUser.username}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Text(
                                text = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date(now)),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Check-In", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = if (targetTodayRecord?.checkInTime != null && targetTodayRecord.checkInTime != 0L) {
                                            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(targetTodayRecord.checkInTime))
                                        } else "--:--",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (targetTodayRecord?.checkInTime != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Check-Out", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = if (targetTodayRecord?.checkOutTime != null && targetTodayRecord.checkOutTime != 0L) {
                                            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(targetTodayRecord.checkOutTime))
                                        } else "--:--",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (targetTodayRecord?.checkOutTime != null && targetTodayRecord.checkOutTime != 0L) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }

                            // Interactive GPS & Thumbnails previews
                            if (targetTodayRecord != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (!targetTodayRecord.checkInSelfieBase64.isNullOrBlank()) {
                                        Card(
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                AsyncImage(
                                                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                        .data(AppUtils.resolveImageModel(targetTodayRecord.checkInSelfieBase64, thumbnail = true))
                                                        .crossfade(true)
                                                        .size(180)
                                                        .precision(coil.size.Precision.INEXACT)
                                                        .build(),
                                                    contentDescription = "Check-In Selfie",
                                                    modifier = Modifier
                                                        .size(60.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.surface),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text("Check-In Selfie", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                targetTodayRecord.checkInLocationSpec?.let { loc ->
                                                    Text(loc, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                    if (!targetTodayRecord.checkOutSelfieBase64.isNullOrBlank()) {
                                        Card(
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                AsyncImage(
                                                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                        .data(AppUtils.resolveImageModel(targetTodayRecord.checkOutSelfieBase64, thumbnail = true))
                                                        .crossfade(true)
                                                        .size(180)
                                                        .precision(coil.size.Precision.INEXACT)
                                                        .build(),
                                                    contentDescription = "Check-Out Selfie",
                                                    modifier = Modifier
                                                        .size(60.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.surface),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text("Check-Out Selfie", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                targetTodayRecord.checkOutLocationSpec?.let { loc ->
                                                    Text(loc, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Dynamic Buttons for Attendance checking (Only enabled for self or Admin/Manager for others)
                            if (isSelfViewing) {
                                if (targetTodayRecord == null) {
                                    Button(
                                        onClick = { triggerSelfieCapture(true) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                    ) {
                                        Icon(Icons.Default.CameraFront, contentDescription = null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Scan Selfie to Check-In", fontWeight = FontWeight.Bold)
                                    }
                                } else if (targetTodayRecord.checkOutTime == 0L) {
                                    Button(
                                        onClick = { triggerSelfieCapture(false) },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                                    ) {
                                        Icon(Icons.Default.CameraFront, contentDescription = null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Scan Selfie to Check-Out", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                        Text("Today's Shift Logged Perfectly", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    }
                                }
                            } else if (isAdminOrManager) {
                                if (targetTodayRecord == null) {
                                    Button(
                                        onClick = {
                                            viewModel.markCheckIn(context, "", "Authorized by ${loggedInUser?.role}: ${loggedInUser?.username}", targetUser.username)
                                            Toast.makeText(context, "Checked In Employee ${targetUser.username}", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Mark Check-In for ${targetUser.username}", fontWeight = FontWeight.Bold)
                                    }
                                } else if (targetTodayRecord.checkOutTime == 0L) {
                                    Button(
                                        onClick = {
                                            viewModel.markCheckOut(context, "", "Authorized by ${loggedInUser?.role}: ${loggedInUser?.username}", targetUser.username)
                                            Toast.makeText(context, "Checked Out Employee ${targetUser.username}", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Mark Check-Out for ${targetUser.username}", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32))
                                        Text("Attendance Marked Perfect for Today", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                    }
                                }
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Viewing employee records in admin mode", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                }

                // INTEGRATED MONTHLY CALENDAR COMPOSABLE
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header: Month & Year Picker
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    if (currentMonth == 0) {
                                        currentMonth = 11
                                        currentYear -= 1
                                    } else {
                                        currentMonth -= 1
                                    }
                                }) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Month")
                                }
                                
                                Text(
                                    text = "${monthsList[currentMonth]} $currentYear",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                
                                IconButton(onClick = {
                                    if (currentMonth == 11) {
                                        currentMonth = 0
                                        currentYear += 1
                                    } else {
                                        currentMonth += 1
                                    }
                                }) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                                }
                            }

                            // Weekday Titles
                            Row(modifier = Modifier.fillMaxWidth()) {
                                listOf("S", "M", "T", "W", "T", "F", "S").forEach { day ->
                                    Text(
                                        text = day,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            // Generate Days Array for selected month and year
                            val dCal = Calendar.getInstance().apply {
                                set(Calendar.YEAR, currentYear)
                                set(Calendar.MONTH, currentMonth)
                                set(Calendar.DAY_OF_MONTH, 1)
                            }
                            val firstDayOfWeek = dCal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday...
                            val maxDays = dCal.getActualMaximum(Calendar.DAY_OF_MONTH)

                            val daysList = mutableListOf<String?>()
                            // Empty pads for week offset
                            for (i in 1 until firstDayOfWeek) {
                                daysList.add(null)
                            }
                            // Real days of month
                            for (day in 1..maxDays) {
                                daysList.add(String.format("%04d-%02d-%02d", currentYear, currentMonth + 1, day))
                            }

                            // Calendar Grid
                            val rows = daysList.chunked(7)
                            rows.forEach { rowDays ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    rowDays.forEach { dateStr ->
                                        if (dateStr == null) {
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {}
                                        } else {
                                            val dayNum = dateStr.split("-").last().toInt().toString()
                                            
                                            // Identify state for date string
                                            val queryRecord = targetRecords.find { it.dateString == dateStr }
                                            val isToday = dateStr == todayStr
                                            
                                            val otherUsersOnLeave = allLeaves.filter { leave ->
                                                leave.userId != targetUser.username &&
                                                leave.status == "Approved" &&
                                                dateStr >= leave.startDateString &&
                                                dateStr <= leave.endDateString
                                            }.map { it.userName }

                                            val otherUsersOnLeaveFromAtt = allAttendance.filter { r ->
                                                r.userId != targetUser.username &&
                                                r.status == "On Leave" &&
                                                r.dateString == dateStr
                                            }.map { it.userName }

                                            val othersLeaveList = (otherUsersOnLeave + otherUsersOnLeaveFromAtt).distinct()

                                            val (bgColor, textColor, borderStroke) = when {
                                                queryRecord?.status == "On Leave" -> Triple(Color(0xFFFFF9C4), Color(0xFFF57F17), BorderStroke(1.dp, Color(0xFFFBC02D))) // leave soft yellow/gold
                                                queryRecord?.status == "Present" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), BorderStroke(1.dp, Color(0xFF81C784))) // present soft green
                                                isToday -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, BorderStroke(1.4.dp, MaterialTheme.colorScheme.primary)) // primary default accent for unmarked today
                                                else -> Triple(Color.Transparent, MaterialTheme.colorScheme.onSurface, BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) // idle past or future dates
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .padding(2.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(bgColor)
                                                    .border(borderStroke.width, borderStroke.brush, RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        if (queryRecord != null) {
                                                            clickedRecordDetailDialog = queryRecord
                                                        } else {
                                                            // Provide prompt details to register an unmarked presence easily
                                                            clickedRecordDetailDialog = AttendanceRecord(
                                                                userId = targetUser.username,
                                                                userName = targetUser.username,
                                                                dateString = dateStr,
                                                                status = "Absent",
                                                                notes = "Unmarked Day"
                                                            )
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = dayNum,
                                                        fontWeight = if (isToday) FontWeight.Black else FontWeight.Medium,
                                                        fontSize = 11.sp,
                                                        color = textColor
                                                    )
                                                    // Dot indicators
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (queryRecord?.status == "Present") {
                                                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFF2E7D32)))
                                                        } else if (queryRecord?.status == "On Leave") {
                                                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFFE65100)))
                                                        }
                                                        
                                                        if (othersLeaveList.isNotEmpty()) {
                                                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(Color(0xFF2563EB)))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    // Pad remaining items if row size is less than 7 elements
                                    if (rowDays.size < 7) {
                                        for (j in 0 until (7 - rowDays.size)) {
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {}
                                        }
                                    }
                                }
                            }

                            // Calendar Legend indicators
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFE8F5E9)))
                                    Text("Present", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFFFF9C4)))
                                    Text("On Leave", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF57F17))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF2563EB)))
                                    Text("Others On Leave", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2563EB))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)))
                                    Text("Unmarked", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            val currentMonthLeaves = remember(allLeaves, currentYear, currentMonth) {
                                allLeaves.filter { leave ->
                                    leave.status == "Approved" && (
                                        leave.startDateString.startsWith(String.format("%04d-%02d", currentYear, currentMonth + 1)) ||
                                        leave.endDateString.startsWith(String.format("%04d-%02d", currentYear, currentMonth + 1))
                                    )
                                }
                            }

                            if (currentMonthLeaves.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Text(
                                    text = "Approved Leaves in ${monthsList[currentMonth]} Status Desk",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    currentMonthLeaves.forEach { leave ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "• ${leave.userName} (${leave.leaveType})",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${leave.startDateString} to ${leave.endDateString}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // BOTTOM BUTTON DECK: APPLY FOR LEAVE / MODIFY ATTENDANCE
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (loggedInUser?.role !in listOf("Admin", "Manager")) {
                            Button(
                                onClick = {
                                    showLeaveDialog = true
                                },
                                modifier = Modifier.weight(1.0f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.CardTravel, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Apply For Leave", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (isAdminOrManager) {
                            Button(
                                onClick = { showModifyAttendanceDialog = true },
                                modifier = Modifier.weight(1.0f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit Attendance", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (subTabSelection == 1) {
                // LEAVE APPLICATIONS TRACKING PANEL

                // section 0: Submit Leave banner
                if (loggedInUser?.role !in listOf("Admin", "Manager")) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(0.7f)) {
                                    Text("Apply For Leave", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text("Request casual, medical or vacation leave.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                                }
                                Button(
                                    onClick = { showLeaveDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("Apply Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Leave approvals list header
                item {
                    Text(
                        text = if (isAdminOrManager) "Admin Leave Requests Queue" else "My Leaves & Team Approved Leaves Desk",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                val leavesToDisplay = if (isAdminOrManager) {
                    allLeaves
                } else {
                    allLeaves.filter { it.userId == loggedInUser?.username || it.status == "Approved" }
                }.sortedByDescending { it.appliedOn }

                if (leavesToDisplay.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassEmpty,
                                    contentDescription = null,
                                    modifier = Modifier.size(38.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("No leave applications registered yet.", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            }
                        }
                    }
                } else {
                    items(leavesToDisplay) { leave ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${leave.leaveType} (${leave.userName})",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Requested: ${leave.startDateString} to ${leave.endDateString}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    val badgeColors = when (leave.status) {
                                        "Approved" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
                                        "Rejected" -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
                                        else -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(badgeColors.first)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = leave.status,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeColors.second
                                        )
                                    }
                                }

                                Text(
                                    text = "Reason: ${leave.reason}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                )

                                if (leave.status == "Approved" && !leave.approvedBy.isNullOrBlank()) {
                                    Text(
                                        text = "✔ Approved by: ${leave.approvedBy}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2E7D32)
                                    )
                                } else if (leave.status == "Rejected" && !leave.approvedBy.isNullOrBlank()) {
                                    Text(
                                        text = "✘ Rejected by: ${leave.approvedBy}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFC62828)
                                    )
                                }

                                // ACTIONS PANEL FOR SCHEDULERS (ADMINS/MANAGERS)
                                if (isAdminOrManager && leave.status == "Pending") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.approveOrRejectLeave(leave, "Approved", loggedInUser?.username ?: "Manager")
                                                Toast.makeText(context, "Leave Application Approved!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Approve", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Button(
                                            onClick = {
                                                viewModel.approveOrRejectLeave(leave, "Rejected", loggedInUser?.username ?: "Manager")
                                                Toast.makeText(context, "Leave Application Rejected!", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Reject", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (subTabSelection == 2) {
                // TEAM ATTENDANCE CALENDAR (EVERYONE'S STATUS IN A SINGLE CALENDAR VIEW)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Month Navigator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = {
                                    if (currentMonth == 0) {
                                        currentMonth = 11
                                        currentYear -= 1
                                    } else {
                                        currentMonth -= 1
                                    }
                                }) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Previous Month")
                                }
                                
                                Text(
                                    text = "${monthsList[currentMonth]} $currentYear",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                
                                IconButton(onClick = {
                                    if (currentMonth == 11) {
                                        currentMonth = 0
                                        currentYear += 1
                                    } else {
                                        currentMonth += 1
                                    }
                                }) {
                                    Icon(Icons.Default.ArrowForward, contentDescription = "Next Month")
                                }
                            }
                            
                            // Weekly columns Header
                            Row(modifier = Modifier.fillMaxWidth()) {
                                listOf("S", "M", "T", "W", "T", "F", "S").forEach { dName ->
                                    Text(
                                        text = dName,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            
                            // Calendar grid calculations
                            val dCal = Calendar.getInstance().apply {
                                set(Calendar.YEAR, currentYear)
                                set(Calendar.MONTH, currentMonth)
                                set(Calendar.DAY_OF_MONTH, 1)
                            }
                            val firstDayOfWeek = dCal.get(Calendar.DAY_OF_WEEK) // 1 = Sunday, 2 = Monday...
                            val maxDays = dCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                            
                            val daysList = mutableListOf<String?>()
                            for (i in 1 until firstDayOfWeek) {
                                daysList.add(null)
                            }
                            for (day in 1..maxDays) {
                                daysList.add(String.format("%04d-%02d-%02d", currentYear, currentMonth + 1, day))
                            }
                            
                            val rows = daysList.chunked(7)
                            rows.forEach { rowDays ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    rowDays.forEach { dateStr ->
                                        if (dateStr == null) {
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {}
                                        } else {
                                            val dayNum = dateStr.split("-").last().toInt().toString()
                                            val isToday = dateStr == todayStr
                                            val isSelected = dateStr == selectedTeamDateStr
                                            
                                            // Compute aggregated statistics for this specific date across ALL users
                                            val staffListForStats = allUsers.filter { it.role != "Admin" }
                                            
                                            var presentCount = 0
                                            var leaveCount = 0
                                            var absentCount = 0
                                            
                                            staffListForStats.forEach { u ->
                                                val rec = allAttendance.find { it.userId == u.username && it.dateString == dateStr }
                                                val hasApprovedLeave = allLeaves.any { leave ->
                                                    leave.userId == u.username &&
                                                    leave.status == "Approved" &&
                                                    dateStr >= leave.startDateString &&
                                                    dateStr <= leave.endDateString
                                                }
                                                
                                                when {
                                                    rec?.status == "Present" -> presentCount++
                                                    rec?.status == "On Leave" || hasApprovedLeave -> leaveCount++
                                                    rec?.status == "Absent" -> absentCount++
                                                    else -> {
                                                        val checkCal = Calendar.getInstance()
                                                        val queryCal = Calendar.getInstance().apply {
                                                            val parts = dateStr.split("-")
                                                            set(Calendar.YEAR, parts[0].toInt())
                                                            set(Calendar.MONTH, parts[1].toInt() - 1)
                                                            set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                                                        }
                                                        if (queryCal.before(checkCal) && !isToday) {
                                                            absentCount++
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            val cellBg = when {
                                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                                isToday -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                                else -> Color.Transparent
                                            }
                                            val cellBorder = when {
                                                isSelected -> BorderStroke(1.8.dp, MaterialTheme.colorScheme.primary)
                                                isToday -> BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
                                                else -> BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            }
                                            
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .padding(2.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(cellBg)
                                                    .border(cellBorder, RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        selectedTeamDateStr = dateStr
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = dayNum,
                                                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                                                        fontSize = 11.sp,
                                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(top = 1.dp)
                                                    ) {
                                                        if (presentCount > 0) {
                                                            Box(modifier = Modifier.size(3.5.dp).clip(CircleShape).background(Color(0xFF2E7D32)))
                                                        }
                                                        if (leaveCount > 0) {
                                                            Box(modifier = Modifier.size(3.5.dp).clip(CircleShape).background(Color(0xFFFBC02D)))
                                                        }
                                                        if (absentCount > 0) {
                                                            Box(modifier = Modifier.size(3.5.dp).clip(CircleShape).background(Color(0xFFC62828)))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (rowDays.size < 7) {
                                        for (j in 0 until (7 - rowDays.size)) {
                                            Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {}
                                        }
                                    }
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2E7D32)))
                                    Text("Present", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFBC02D)))
                                    Text("On Leave", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFC62828)))
                                    Text("Absent", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                
                item {
                    val formattedSelectedDate = try {
                        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedTeamDateStr)
                        java.text.SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(parsed)
                    } catch(e: Exception) {
                        selectedTeamDateStr
                    }
                    
                    Text(
                        text = "Team Status: $formattedSelectedDate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                val staffList = allUsers.filter { it.role != "Admin" }
                if (staffList.isEmpty()) {
                    item {
                        Text(
                            text = "No staff members registered in the database.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    items(staffList) { staff ->
                        val attRecord = allAttendance.find { it.userId == staff.username && it.dateString == selectedTeamDateStr }
                        val leaveRecord = allLeaves.find { leave ->
                            leave.userId == staff.username &&
                            leave.status == "Approved" &&
                            selectedTeamDateStr >= leave.startDateString &&
                            selectedTeamDateStr <= leave.endDateString
                        }
                        
                        val statusStr = when {
                            attRecord?.status == "On Leave" || leaveRecord != null -> "On Leave"
                            attRecord?.status == "Present" -> "Present"
                            attRecord?.status == "Absent" -> "Absent"
                            else -> {
                                val checkCal = Calendar.getInstance()
                                val queryCal = Calendar.getInstance().apply {
                                    val parts = selectedTeamDateStr.split("-")
                                    set(Calendar.YEAR, parts[0].toInt())
                                    set(Calendar.MONTH, parts[1].toInt() - 1)
                                    set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                                }
                                if (queryCal.before(checkCal) && selectedTeamDateStr != todayStr) "Absent" else "Unmarked"
                            }
                        }
                        
                        val (pColor, pTextColor, pIcon) = when (statusStr) {
                            "Present" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), Icons.Default.CheckCircle)
                            "On Leave" -> Triple(Color(0xFFFFF9C4), Color(0xFFE65100), Icons.Default.CardTravel)
                            "Absent" -> Triple(Color(0xFFFFEBEE), Color(0xFFC62828), Icons.Default.Cancel)
                            else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.RadioButtonUnchecked)
                        }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (attRecord != null) {
                                    clickedRecordDetailDialog = attRecord
                                } else if (leaveRecord != null) {
                                    clickedRecordDetailDialog = AttendanceRecord(
                                        userId = staff.username,
                                        userName = staff.username,
                                        dateString = selectedTeamDateStr,
                                        status = "On Leave",
                                        notes = "Approved leave application: ${leaveRecord.reason}"
                                    )
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = staff.username.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    Spacer(modifier = Modifier.height(2.dp))
                                    when {
                                        statusStr == "Present" && attRecord != null -> {
                                            val inTime = if (attRecord.checkInTime > 0) java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(attRecord.checkInTime)) else "--:--"
                                            val outTime = if (attRecord.checkOutTime > 0) java.text.SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(attRecord.checkOutTime)) else "--:--"
                                            Text(
                                                text = "⏱ In: $inTime  |  Out: $outTime",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (!attRecord.checkInLocationSpec.isNullOrBlank()) {
                                                Text(
                                                    text = "📍 Log: ${attRecord.checkInLocationSpec}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                        statusStr == "On Leave" -> {
                                            val leaveType = leaveRecord?.leaveType ?: attRecord?.notes ?: "Approved"
                                            val reason = leaveRecord?.reason ?: ""
                                            Text(
                                                text = "Type: $leaveType",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (reason.isNotBlank()) {
                                                Text(
                                                    text = "Reason: $reason",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                )
                                            }
                                        }
                                        else -> {
                                            Text(
                                                text = "No action logged",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(pColor)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = pIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = pTextColor
                                        )
                                        Text(
                                            text = statusStr,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = pTextColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // DIALOGS SECTION

    // 0. DIALOG: WHATSAPP CONFIGURATION
    if (showWhatsAppSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showWhatsAppSettingsDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsPhone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "WhatsApp Dispatcher",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            },
            text = {
                val scrollState = rememberScrollState()
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Configure how your attendance logs (Check-Ins/Outs) and Daily Leaves/Week-offs summary are posted to WhatsApp.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Section 1: Spare Phone Automation (Recommended)
                    Text(
                        text = "📱 Spare Phone Automation Mode",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "Pushes specialized local Android notifications with formatted texts containing check-ins (instant) and leaves summary (daily at 9:00 AM). Automation utilities like MacroDroid or Tasker can capture these notifications to auto-post messages to WhatsApp.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Spare Automation",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Post automation-ready local alerts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = tempSparePhoneEnable,
                            onCheckedChange = { tempSparePhoneEnable = it }
                        )
                    }

                    OutlinedTextField(
                        value = tempTargetPhone,
                        onValueChange = { tempTargetPhone = it },
                        label = { Text("Target WhatsApp Number") },
                        placeholder = { Text("e.g. +919876543210") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            Text("Optional target direct number (with country code e.g. +91...)")
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Section 2: Webhook Dispatcher
                    Text(
                        text = "🌐 Webhook Gateway Integration (Alternative)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    OutlinedTextField(
                        value = tempWebhookUrl,
                        onValueChange = { tempWebhookUrl = it },
                        label = { Text("WhatsApp Webhook URL") },
                        placeholder = { Text("https://api.gateway.com/endpoint") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            Text("E.g., custom trigger URL (or Twilio/Zapier webhook link)")
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Webhook Push",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Send automated checks instantly via webhook",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Switch(
                            checked = tempWhatsAppEnable,
                            onCheckedChange = { tempWhatsAppEnable = it }
                        )
                    }

                    Text(
                        text = "* Manual Share: You can also manually share any entry to WhatsApp anytime by tapping on any card below and choosing manual WhatsApp send.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveWhatsAppSettings(
                            context, 
                            tempWebhookUrl, 
                            tempWhatsAppEnable, 
                            tempSparePhoneEnable, 
                            tempTargetPhone
                        )
                        Toast.makeText(context, "WhatsApp Dispatch Config Saved!", Toast.LENGTH_SHORT).show()
                        showWhatsAppSettingsDialog = false
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWhatsAppSettingsDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // 1. DIALOG: FILE LEAVE APPLICATION
    if (showLeaveDialog) {
        var startLeaveSpec by remember { mutableStateOf("") }
        var endLeaveSpec by remember { mutableStateOf("") }
        var leaveTypeSpec by remember { mutableStateOf("Week-off") }
        var reasonSpec by remember { mutableStateOf("") }
        
        val showDatePicker = { onDateSelected: (String) -> Unit ->
            val calendar = Calendar.getInstance()
            android.app.DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val formattedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                    onDateSelected(formattedDate)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
        
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Apply For Leave", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker { startLeaveSpec = it } }
                    ) {
                        OutlinedTextField(
                            value = startLeaveSpec,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Start Date") },
                            placeholder = { Text("Tap to select start date...") },
                            trailingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = "Choose Start Date")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker { endLeaveSpec = it } }
                    ) {
                        OutlinedTextField(
                            value = endLeaveSpec,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("End Date") },
                            placeholder = { Text("Tap to select end date...") },
                            trailingIcon = {
                                Icon(Icons.Default.DateRange, contentDescription = "Choose End Date")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = false,
                            colors = OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.outline,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    // Leave type Description
                    Column {
                        Text("Category: Week-off (Fixed)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("This is the standard and only leave type supported.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    OutlinedTextField(
                        value = reasonSpec,
                        onValueChange = { reasonSpec = it },
                        label = { Text("Reason for Leave") },
                        placeholder = { Text("Provide details...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (startLeaveSpec.isNotBlank() && endLeaveSpec.isNotBlank() && reasonSpec.isNotBlank()) {
                            val currentEmpName = loggedInUser?.username ?: ""
                            val hasOverlap = allLeaves.any { leave ->
                                leave.userId == currentEmpName &&
                                (leave.status == "Approved" || leave.status == "Pending") &&
                                startLeaveSpec.trim() <= leave.endDateString &&
                                leave.startDateString <= endLeaveSpec.trim()
                            }
                            if (hasOverlap) {
                                Toast.makeText(context, "An overlapping Pending or Approved leave exists for this period.", Toast.LENGTH_LONG).show()
                            } else {
                                viewModel.applyForLeave(startLeaveSpec.trim(), endLeaveSpec.trim(), leaveTypeSpec, reasonSpec.trim())
                                Toast.makeText(context, "Leave Request Submitted!", Toast.LENGTH_SHORT).show()
                                showLeaveDialog = false
                            }
                        } else {
                            Toast.makeText(context, "Fill in all parameters.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Submit Application")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 2. DIALOG: MODIFY / EDIT EMPLOYEE ATTENDANCE (ADMIN & MANAGER ONLY)
    if (showModifyAttendanceDialog) {
        var empNameSpec by remember { mutableStateOf(targetUser.username) }
        var dSpec by remember { mutableStateOf(todayStr) }
        var statusSpec by remember { mutableStateOf("Present") }
        var remarkSpec by remember { mutableStateOf("") }

        val matchingEmployee = allUsers.find { it.username == empNameSpec }

        AlertDialog(
            onDismissRequest = { showModifyAttendanceDialog = false },
            title = { Text("Modify Staff Attendance", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = empNameSpec,
                        onValueChange = { empNameSpec = it },
                        label = { Text("Employee Username") },
                        placeholder = { Text(" E.g., operator1") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(modifier = Modifier.fillMaxWidth().clickable {
                        val calendar = java.util.Calendar.getInstance()
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val cal = java.util.Calendar.getInstance()
                                cal.set(year, month, dayOfMonth)
                                dSpec = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(cal.time)
                            },
                            calendar.get(java.util.Calendar.YEAR),
                            calendar.get(java.util.Calendar.MONTH),
                            calendar.get(java.util.Calendar.DAY_OF_MONTH)
                        ).show()
                    }) {
                        OutlinedTextField(
                            value = dSpec,
                            onValueChange = { },
                            label = { Text("Target Date") },
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                disabledBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    // Status Choices
                    Column {
                        Text("Determine Status:", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Present", "On Leave", "Absent").forEach { stat ->
                                FilterChip(
                                    selected = statusSpec == stat,
                                    onClick = { statusSpec = stat },
                                    label = { Text(stat) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = remarkSpec,
                        onValueChange = { remarkSpec = it },
                        label = { Text("Admin Remarks / Reason") },
                        placeholder = { Text("E.g., Regularised by admin") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (empNameSpec.isNotBlank() && dSpec.isNotBlank()) {
                            val targetUserNameTrimmed = empNameSpec.trim()
                            val targetUserObj = allUsers.find { it.username.trim().lowercase() == targetUserNameTrimmed.lowercase() }
                            val isTargetActuallyAdminOrManager = targetUserObj?.role in listOf("Admin", "Manager") || 
                                    targetUserNameTrimmed.equals("admin", ignoreCase = true) || 
                                    targetUserNameTrimmed.equals("manager", ignoreCase = true) ||
                                    targetUserNameTrimmed.equals(loggedInUser?.username, ignoreCase = true)
                            
                            if (isTargetActuallyAdminOrManager) {
                                Toast.makeText(context, "Cannot mark/modify attendance for Admins or Managers.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.modifyAttendance(
                                    userId = targetUserNameTrimmed,
                                    userName = targetUserNameTrimmed,
                                    dateString = dSpec.trim(),
                                    status = statusSpec,
                                    notes = remarkSpec.trim()
                                )
                                Toast.makeText(context, "Attendance database updated!", Toast.LENGTH_SHORT).show()
                                showModifyAttendanceDialog = false
                            }
                        } else {
                            Toast.makeText(context, "Scope values required.", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Apply Mod")
                }
            },
            dismissButton = {
                TextButton(onClick = { showModifyAttendanceDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 3. DIALOG: DETAILED DATE ATTENDANCE STAMP DIALOG VIEW
    if (clickedRecordDetailDialog != null) {
        val stamp = clickedRecordDetailDialog!!
        var activeDialogTab by remember { mutableStateOf(0) } // 0 = Individual record, 1 = Team Board, 2 = Hourly Status
        
        // Lifted State Calculations
        val teamStaff = remember(allUsers) { allUsers.filter { it.role != "Admin" } }
        val totalTeamCount = teamStaff.size
        
        val presentRecords = remember(allAttendance, stamp.dateString) {
            allAttendance.filter { it.dateString == stamp.dateString && it.status == "Present" }
        }
        val presentUsernames = remember(presentRecords) { presentRecords.map { it.userId }.toSet() }
        val presentTeam = remember(teamStaff, presentUsernames) { teamStaff.filter { it.username in presentUsernames } }
        
        val onLeaveRecords = remember(allLeaves, allAttendance, stamp.dateString) {
            allLeaves.filter { leave ->
                leave.status == "Approved" && 
                stamp.dateString >= leave.startDateString && 
                stamp.dateString <= leave.endDateString
            }.map { it.userId }.toSet() + allAttendance.filter {
                it.dateString == stamp.dateString && it.status == "On Leave"
            }.map { it.userId }.toSet()
        }
        val leaveTeam = remember(teamStaff, onLeaveRecords) { teamStaff.filter { it.username in onLeaveRecords } }
        
        val absentTeam = remember(teamStaff, presentUsernames, onLeaveRecords) {
            teamStaff.filter { it.username !in presentUsernames && it.username !in onLeaveRecords }
        }

        AlertDialog(
            onDismissRequest = { clickedRecordDetailDialog = null },
            title = {
                Column {
                    Text("Attendance Hub: ${stamp.dateString}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    TabRow(
                        selectedTabIndex = activeDialogTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = activeDialogTab == 0,
                            onClick = { activeDialogTab = 0 },
                            text = { Text("Individual", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = activeDialogTab == 1,
                            onClick = { activeDialogTab = 1 },
                            text = { Text("Team List", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = activeDialogTab == 2,
                            onClick = { activeDialogTab = 2 },
                            text = { Text("Hourly", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            },
            text = {
                Box(modifier = Modifier.heightIn(max = 420.dp)) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        if (activeDialogTab == 0) {
                            // TAB 0: INDIVIDUAL RECORD DETAILS (STAMP LOGS)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Staged Status:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    val badgeClr = if (stamp.status == "Present") Color(0xFF2E7D32) else if (stamp.status == "On Leave") Color(0xFFF57F17) else Color(0xFFC62828)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(badgeClr.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(stamp.status, color = badgeClr, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                Text("Employee: ${stamp.userName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)

                                if (stamp.checkInTime != 0L) {
                                    ListItem(
                                        headlineContent = { Text("Check-In Registered", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                                        supportingContent = { 
                                            Column {
                                                Text(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(stamp.checkInTime)) + " (Official)", fontSize = 12.sp)
                                                stamp.checkInLocationSpec?.let { loc ->
                                                    Text("📍 GPS: $loc", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        },
                                        leadingContent = { Icon(Icons.Default.Login, contentDescription = null, tint = Color(0xFF2E7D32)) }
                                    )
                                }

                                if (stamp.checkOutTime != 0L) {
                                    ListItem(
                                        headlineContent = { Text("Check-Out Registered", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                                        supportingContent = { 
                                            Column {
                                                Text(SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(stamp.checkOutTime)) + " (Official)", fontSize = 12.sp)
                                                stamp.checkOutLocationSpec?.let { loc ->
                                                    Text("📍 GPS: $loc", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        },
                                        leadingContent = { Icon(Icons.Default.Logout, contentDescription = null, tint = Color(0xFFC62828)) }
                                    )
                                }

                                if (!stamp.notes.isNullOrBlank()) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.padding(8.dp)) {
                                            Text("Remarks / Notes:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text(stamp.notes, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }

                                // Display selfies verification if present
                                if (!stamp.checkInSelfieBase64.isNullOrBlank() || !stamp.checkOutSelfieBase64.isNullOrBlank()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        if (!stamp.checkInSelfieBase64.isNullOrBlank()) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                                Text("Check-In Selfie", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                Spacer(Modifier.height(4.dp))
                                                AsyncImage(
                                                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                        .data(AppUtils.resolveImageModel(stamp.checkInSelfieBase64, thumbnail = true))
                                                        .crossfade(true)
                                                        .size(300)
                                                        .precision(coil.size.Precision.INEXACT)
                                                        .build(),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(110.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }

                                        if (!stamp.checkOutSelfieBase64.isNullOrBlank()) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                                Text("Check-Out Selfie", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                Spacer(Modifier.height(4.dp))
                                                AsyncImage(
                                                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                                        .data(AppUtils.resolveImageModel(stamp.checkOutSelfieBase64, thumbnail = true))
                                                        .crossfade(true)
                                                        .size(300)
                                                        .precision(coil.size.Precision.INEXACT)
                                                        .build(),
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(110.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    }
                                }

                                // 📢 WhatsApp Dispatcher Card Integration
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "WhatsApp Dispatcher",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Text(
                                            text = "Manage manual and webhook dispatch options for this attendance entry.",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                         ) {
                                                              // Action 1: Generic WhatsApp Share via Intent (Manual Share)
                                            Button(
                                                onClick = {
                                                    val simpleTimeSdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
                                                    val shareText = java.lang.StringBuilder().apply {
                                                        append("*Employee:* ${stamp.userName}\n")
                                                        append("📅 *Date:* ${stamp.dateString}\n")
                                                        if (stamp.checkInTime > 0L) {
                                                            append("⏱ *Check-In:* ${simpleTimeSdf.format(Date(stamp.checkInTime))}\n")
                                                        }
                                                        if (stamp.checkOutTime > 0L) {
                                                            append("⏱ *Check-Out:* ${simpleTimeSdf.format(Date(stamp.checkOutTime))}\n")
                                                        }
                                                        if (!stamp.notes.isNullOrBlank()) {
                                                            append("📝 *Notes:* ${stamp.notes}\n")
                                                        }
                                                    }.toString()

                                                    val targetNum = whatsappTargetPhone.trim()
                                                    if (targetNum.isNotBlank()) {
                                                        val cleanNum = targetNum.replace("[^0-9+]".toRegex(), "")
                                                        val escapedText = android.net.Uri.encode(shareText)
                                                        val directUrl = "https://api.whatsapp.com/send?phone=$cleanNum&text=$escapedText"
                                                        val directIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                                            data = android.net.Uri.parse(directUrl)
                                                            setPackage("com.whatsapp")
                                                        }
                                                        try {
                                                            context.startActivity(directIntent)
                                                        } catch (e: Exception) {
                                                            try {
                                                                val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(directUrl))
                                                                context.startActivity(fallbackIntent)
                                                            } catch (e2: Exception) {
                                                                Toast.makeText(context, "Could not open direct WhatsApp conversation", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    } else {
                                                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                                            setPackage("com.whatsapp")
                                                        }
                                                        try {
                                                            context.startActivity(sendIntent)
                                                        } catch (e: Exception) {
                                                            try {
                                                                val generalIntent = android.content.Intent.createChooser(
                                                                    android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                                        type = "text/plain"
                                                                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                                                    },
                                                                    "Share Attendance Log"
                                                                )
                                                                context.startActivity(generalIntent)
                                                            } catch (e2: Exception) {
                                                                Toast.makeText(context, "No sharing apps found", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("WhatsApp Msg", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            // Action 2: Trigger Webhook Manually inside background API call!
                                            Button(
                                                onClick = {
                                                    val timeString = if (stamp.checkInTime > 0L) {
                                                        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(stamp.checkInTime))
                                                    } else {
                                                        "N/A"
                                                    }
                                                    viewModel.triggerWhatsAppMessage(
                                                        context = context,
                                                        employeeName = stamp.userName,
                                                        timeStr = timeString,
                                                        status = stamp.status,
                                                        activity = "Manual Push (${stamp.status})"
                                                    )
                                                    Toast.makeText(context, "Webhook background dispatch triggered!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Push Webhook", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (activeDialogTab == 1) {
                            // TAB 1: TEAM OVERVIEW (WHO IS PRESENT, ON LEAVE, OR ABSENT)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Statistics Summary Cards Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                                    ) {
                                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("${presentTeam.size}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFF2E7D32))
                                            Text("Present", fontSize = 10.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4))
                                    ) {
                                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("${leaveTeam.size}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color(0xFFE65100))
                                            Text("On Leave", fontSize = 10.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    ) {
                                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("${absentTeam.size}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text("Unmarked", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Text("Present Team List:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Color(0xFF2E7D32))
                                if (presentTeam.isEmpty()) {
                                    Text("No employees present yet on this day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    presentTeam.forEach { emp ->
                                        val rec = presentRecords.find { it.userId == emp.username }
                                        val timingInfo = if (rec != null && rec.checkInTime != 0L) {
                                            val startT = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(rec.checkInTime))
                                            val endT = if (rec.checkOutTime != 0L) SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(rec.checkOutTime)) else "Active"
                                            " ($startT - $endT)"
                                        } else ""
                                        Text("✓ ${emp.username}$timingInfo", fontSize = 13.sp, color = Color(0xFF1B5E20), fontWeight = FontWeight.Medium)
                                    }
                                }

                                Text("On Leave List:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Color(0xFFE65100))
                                if (leaveTeam.isEmpty()) {
                                    Text("No approved leave requests today.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    leaveTeam.forEach { emp ->
                                        Text("🌴 ${emp.username} (Approved Leave)", fontSize = 13.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Medium)
                                    }
                                }

                                Text("Unmarked / Off-Duty List:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (absentTeam.isEmpty()) {
                                    Text("All rostered staff accounted.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                } else {
                                    absentTeam.forEach { emp ->
                                        Text("• ${emp.username} (Roster Pending)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        } else if (activeDialogTab == 2) {
                            // TAB 2: HOURLY COVERAGE / DENSITY MATRIX
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "Coverage estimates across standard business shifts based on real check-in/out timestamps.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Check each hour from 08:00 to 18:00
                                for (hour in 8..18) {
                                    val checkedInAtHour = presentRecords.filter { r ->
                                        if (r.checkInTime == 0L) false
                                        else {
                                            val cIn = Calendar.getInstance().apply { timeInMillis = r.checkInTime }
                                            val checkInHour = cIn.get(Calendar.HOUR_OF_DAY)
                                            if (r.checkOutTime == 0L) {
                                                checkInHour <= hour
                                            } else {
                                                val cOut = Calendar.getInstance().apply { timeInMillis = r.checkOutTime }
                                                val checkOutHour = cOut.get(Calendar.HOUR_OF_DAY)
                                                checkInHour <= hour && hour <= checkOutHour
                                            }
                                        }
                                    }

                                    val amPm = if (hour >= 12) "PM" else "AM"
                                    val formattedHourNum = when {
                                        hour == 0 -> 12
                                        hour > 12 -> hour - 12
                                        else -> hour
                                    }
                                    val hourLabel = String.format("%02d:00 %s", formattedHourNum, amPm)

                                    val ratio = if (totalTeamCount > 0) checkedInAtHour.size.toFloat() / totalTeamCount else 0.0f
                                    val densityBarColor = when {
                                        checkedInAtHour.isEmpty() -> Color.LightGray.copy(alpha = 0.5f)
                                        checkedInAtHour.size <= 1 -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.primary
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(hourLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                                Text(
                                                    text = "${checkedInAtHour.size} / $totalTeamCount available",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (checkedInAtHour.isNotEmpty()) Color(0xFF2E7D32) else Color.Gray
                                                )
                                            }
                                            LinearProgressIndicator(
                                                progress = { ratio },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp)),
                                                color = densityBarColor,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            if (checkedInAtHour.isNotEmpty()) {
                                                Text(
                                                    text = "Present: " + checkedInAtHour.joinToString(", ") { it.userName },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { clickedRecordDetailDialog = null }) { Text("Dismiss") }
            }
        )
    }
}
