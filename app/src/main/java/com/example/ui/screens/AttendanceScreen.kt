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
    var dailyWageInput by remember { mutableStateOf("1500") }

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
                            viewModel.markCheckIn(context, base64, locSpec)
                            Toast.makeText(context, "Checked In Successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.markCheckOut(context, base64, locSpec)
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

    // Capture Trigger
    fun triggerSelfieCapture(isCheckIn: Boolean) {
        isCheckingInAction = isCheckIn
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Aesthetic Section Tabs
        TabRow(
            selectedTabIndex = subTabSelection,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = subTabSelection == 0,
                onClick = { subTabSelection = 0 },
                text = { Text("Attendance Board", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                icon = { Icon(Icons.Default.Fingerprint, contentDescription = null) }
            )
            Tab(
                selected = subTabSelection == 1,
                onClick = { subTabSelection = 1 },
                text = { Text("Leave Panel", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                icon = { Icon(Icons.Default.CardTravel, contentDescription = null) }
            )
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

            if (subTabSelection == 0 && !isTargetAdminOrManager) {
                // TODAY CHECK-IN/OUT INTERACTIVE DECK
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
                                text = "Today's Ledger Stamp",
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
                                                    model = AppUtils.resolveImageModel(targetTodayRecord.checkInSelfieBase64),
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
                                                    model = AppUtils.resolveImageModel(targetTodayRecord.checkOutSelfieBase64),
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

                            // Dynamic Buttons for Attendance checking (Only enabled for self!)
                            if (isSelfViewing) {
                                if (targetTodayRecord == null) {
                                    Button(
                                        onClick = { triggerSelfieCapture(isCheckIn = true) },
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
                                        onClick = { triggerSelfieCapture(isCheckIn = false) },
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
                        }
                    }
                }

                // STAFF SALARY & PAYROLL CARD
                item {
                    val salary = (monthlyStats.first * (dailyWageInput.toDoubleOrNull() ?: 1500.0))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                    Text(
                                        text = "Staff Salary & Payroll",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Base Rate",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            
                            Text(
                                text = "Daily wages are dynamically combined with active presence logs for ${monthsList[currentMonth]} $currentYear.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f)
                            )
                            
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = dailyWageInput,
                                    onValueChange = { dailyWageInput = it },
                                    label = { Text("Daily Wage Rate (₹)") },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        focusedLabelColor = MaterialTheme.colorScheme.tertiary,
                                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = "Net Payout",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = String.format("₹%,.2f", salary),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Formula: Present Days (${monthlyStats.first}) × Wage (₹${dailyWageInput})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = "Leaves: ${monthlyStats.second} | Absences: ${monthlyStats.third}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            
                            if (isAdminOrManager) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = {
                                            val wage = dailyWageInput.toDoubleOrNull() ?: 1500.0
                                            val finalAmount = monthlyStats.first * wage
                                            if (finalAmount > 0) {
                                                viewModel.recordSalaryPayment(
                                                    employeeName = targetUser.username,
                                                    amount = finalAmount,
                                                    description = "Paid attendance salary for ${monthsList[currentMonth]} $currentYear (${monthlyStats.first} present days @ ₹${wage}/day)"
                                                )
                                                Toast.makeText(context, "Salary Disbursed & Outflow recorded in Ledger under ${targetUser.username}!", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Cannot disburse ₹0.00 salary. Verify present days count.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Disburse", fontWeight = FontWeight.Bold, maxLines = 1)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val wage = dailyWageInput.toDoubleOrNull() ?: 1500.0
                                            val finalAmount = monthlyStats.first * wage
                                            val shareMsg = """
                                            💸 *STUDIO LENS SALARY DISBURSEMENT* 💸
                                            ━━━━━━━━━━━━━━━━━━━━━━━━━━
                                            • *Employee Username:* ${targetUser.username}
                                            • *Month/Period:* ${monthsList[currentMonth]} $currentYear
                                            • *Active Present Days:* ${monthlyStats.first}
                                            • *Configured Daily Wage:* ₹$wage / day
                                            • *Approved Leaves:* ${monthlyStats.second}
                                            • *Total Payout Disbursed:* *₹${String.format("%,.2f", finalAmount)}*
                                            ━━━━━━━━━━━━━━━━━━━━━━━━━━
                                            _Voucher digitally processed & logged in Studio Lens ledger._
                                            """.trimIndent()
                                            
                                            try {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(android.content.Intent.EXTRA_TEXT, shareMsg)
                                                    setPackage("com.whatsapp")
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                try {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(android.content.Intent.EXTRA_TEXT, shareMsg)
                                                    }
                                                    context.startActivity(android.content.Intent.createChooser(intent, "Share Payslip"))
                                                } catch (ex: Exception) {
                                                    Toast.makeText(context, "No sharing app found", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Share Payslip", fontWeight = FontWeight.Bold, maxLines = 1)
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
                        Button(
                            onClick = {
                                if (loggedInUser?.role in listOf("Admin", "Manager")) {
                                    Toast.makeText(context, "Manager and Admin do not require to apply for leaves, all others do.", Toast.LENGTH_LONG).show()
                                } else {
                                    showLeaveDialog = true
                                }
                            },
                            modifier = Modifier.weight(1.0f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.CardTravel, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Apply For Leave", fontWeight = FontWeight.Bold)
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
                                Text("Apply For Outrage & Leave", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text("Request vacation, casual or medical leave directly to managers in past, present or future dates.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
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

                // Leave approvals list header
                item {
                    Text(
                        text = if (isAdminOrManager) "Admin Leave Requests Queue" else "My Leave Status Desk",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                val leavesToDisplay = if (isAdminOrManager) {
                    allLeaves
                } else {
                    allLeaves.filter { it.userId == loggedInUser?.username }
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
            }
        }
    }

    // DIALOGS SECTION

    // 1. DIALOG: FILE LEAVE APPLICATION
    if (showLeaveDialog) {
        var startLeaveSpec by remember { mutableStateOf("") }
        var endLeaveSpec by remember { mutableStateOf("") }
        var leaveTypeSpec by remember { mutableStateOf("Week-off") }
        var reasonSpec by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Apply For Leave", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = startLeaveSpec,
                        onValueChange = { startLeaveSpec = it },
                        label = { Text("Start Date (yyyy-MM-dd)") },
                        placeholder = { Text("E.g., $todayStr") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = endLeaveSpec,
                        onValueChange = { endLeaveSpec = it },
                        label = { Text("End Date (yyyy-MM-dd)") },
                        placeholder = { Text("E.g., $todayStr") },
                        modifier = Modifier.fillMaxWidth()
                    )

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
                            viewModel.applyForLeave(startLeaveSpec.trim(), endLeaveSpec.trim(), leaveTypeSpec, reasonSpec.trim())
                            Toast.makeText(context, "Leave Request Submitted!", Toast.LENGTH_SHORT).show()
                            showLeaveDialog = false
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

                    OutlinedTextField(
                        value = dSpec,
                        onValueChange = { dSpec = it },
                        label = { Text("Target Date (yyyy-MM-dd)") },
                        placeholder = { Text("E.g., $todayStr") },
                        modifier = Modifier.fillMaxWidth()
                    )

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
                            viewModel.modifyAttendance(
                                userId = empNameSpec.trim(),
                                userName = empNameSpec.trim(),
                                dateString = dSpec.trim(),
                                status = statusSpec,
                                notes = remarkSpec.trim()
                            )
                            Toast.makeText(context, "Attendance database updated!", Toast.LENGTH_SHORT).show()
                            showModifyAttendanceDialog = false
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
        AlertDialog(
            onDismissRequest = { clickedRecordDetailDialog = null },
            title = { Text("Attendance Frame: ${stamp.dateString}", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // Overall Status indicator badge
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Staged Status:")
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

                    Text("Employee: ${stamp.userName}")

                    val otherUsersOnLeave = allLeaves.filter { leave ->
                        leave.userId != stamp.userId &&
                        leave.status == "Approved" &&
                        stamp.dateString >= leave.startDateString &&
                        stamp.dateString <= leave.endDateString
                    }.map { it.userName }

                    val otherUsersOnLeaveFromAtt = allAttendance.filter { r ->
                        r.userId != stamp.userId &&
                        r.status == "On Leave" &&
                        r.dateString == stamp.dateString
                    }.map { it.userName }

                    val dialogOthersLeaveList = (otherUsersOnLeave + otherUsersOnLeaveFromAtt).distinct()

                    if (dialogOthersLeaveList.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2563EB)))
                                    Text(
                                        text = "Others on Leave today (View-Only):",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                dialogOthersLeaveList.forEach { name ->
                                    Text("• $name [Week-off]", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    if (stamp.checkInTime != 0L) {
                        Text("✓ Checked-In: " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(stamp.checkInTime)))
                        stamp.checkInLocationSpec?.let { loc ->
                            Text("📍 Check-In GPS: $loc")
                        }
                    }

                    if (stamp.checkOutTime != 0L) {
                        Text("✓ Checked-Out: " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(stamp.checkOutTime)))
                        stamp.checkOutLocationSpec?.let { loc ->
                            Text("📍 Check-Out GPS: $loc")
                        }
                    }

                    if (!stamp.notes.isNullOrBlank()) {
                        Text("Remarks: " + stamp.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Display selfies verification if present!
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (!stamp.checkInSelfieBase64.isNullOrBlank()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("Check-In Selfie", style = MaterialTheme.typography.labelSmall)
                                AsyncImage(
                                    model = AppUtils.resolveImageModel(stamp.checkInSelfieBase64),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        if (!stamp.checkOutSelfieBase64.isNullOrBlank()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text("Check-Out Selfie", style = MaterialTheme.typography.labelSmall)
                                AsyncImage(
                                    model = AppUtils.resolveImageModel(stamp.checkOutSelfieBase64),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
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
