package com.example

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.InventoryRepository
import com.example.ui.components.PullToRefreshContainer
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StockViewModel
import com.example.util.AppUtils
import com.example.ui.viewmodel.ViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : FragmentActivity() {
    private lateinit var repository: InventoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        
        try {
            val req = androidx.work.PeriodicWorkRequestBuilder<com.example.util.AttendanceAlarmWorker>(15, java.util.concurrent.TimeUnit.MINUTES).build()
            androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork("attendance_alarm", androidx.work.ExistingPeriodicWorkPolicy.KEEP, req)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to schedule work", e)
        }

        enableEdgeToEdge()

        // Initialize Firebase dynamically if keys are configured
        com.example.data.repository.FirebaseSyncManager.initialize(applicationContext)

        repository = InventoryRepository()

        setContent {
            // Instantiate StockViewModel
            val stockViewModel: StockViewModel = viewModel(
                factory = ViewModelFactory(repository)
            )

            val isDarkTheme by stockViewModel.isDarkTheme.collectAsStateWithLifecycle()
            val isLoggedIn by stockViewModel.isLoggedIn.collectAsStateWithLifecycle()
            val appIconStyle by stockViewModel.appIconStyle.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) {
                stockViewModel.loadAppIconStyle(applicationContext)
                stockViewModel.checkAutoLogin(applicationContext)
            }

            MyApplicationTheme(darkTheme = isDarkTheme, appIconStyle = appIconStyle) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isLoggedIn) {
                        LoginScreen(viewModel = stockViewModel)
                    } else {
                        MainAppContent(viewModel = stockViewModel)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainAppContent(viewModel: StockViewModel) {
    val activeTab by viewModel.activeTab.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val lastSynced by viewModel.lastSyncedTime.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    var lastBackPress by remember { mutableStateOf(0L) }
    val activity = context as? androidx.activity.ComponentActivity
    androidx.activity.compose.BackHandler(enabled = true) {
        val now = System.currentTimeMillis()
        if (now - lastBackPress < 2000) {
            activity?.finishAffinity()
        } else {
            lastBackPress = now
            android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Sync pulse rotate animation
    val infiniteTransition = rememberInfiniteTransition(label = "Sync spin")
    val spinningAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing)
        ),
        label = "Sync spin"
    )

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    val allNotifications by viewModel.allNotifications.collectAsState()
    val loggedInUser by viewModel.loggedInUser.collectAsState()
    var lastAlertTime by remember { mutableStateOf(System.currentTimeMillis()) }
    
    LaunchedEffect(allNotifications) {
        val userRole = loggedInUser?.role
        if (userRole == "Admin" || userRole == "Manager") {
            val latest = allNotifications.maxByOrNull { it.timestamp }
            if (latest != null && latest.timestamp > lastAlertTime) {
                lastAlertTime = latest.timestamp
                AppUtils.postSystemNotification(context, latest.title, latest.message)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                ) {
                    Spacer(Modifier.height(32.dp))

                    Text(
                        text = "Navigation Menu",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    val activeTab by viewModel.activeTab.collectAsState()
                    val canManageUsers by viewModel.canManageUsers.collectAsState()
                    val canViewLedger by viewModel.canViewLedger.collectAsState()

                    // Gorgeous standard NavigationDrawerItem for Attendance
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(22.dp)) },
                        label = { 
                            Column {
                                Text("Check-In & Attendance", fontWeight = FontWeight.Bold)
                                Text("Selfie & location base logs", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                            }
                        },
                        selected = activeTab == 4,
                        onClick = {
                            viewModel.setTab(4)
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    if (canManageUsers) {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(22.dp)) },
                            label = { 
                                Column {
                                    Text("Users & Staff Management", fontWeight = FontWeight.Bold)
                                    Text("Manage logins and authorization roles", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                                }
                            },
                            selected = activeTab == 5,
                            onClick = {
                                viewModel.setTab(5)
                                coroutineScope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    if (canViewLedger) {
                        NavigationDrawerItem(
                            icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(22.dp)) },
                            label = { 
                                Column {
                                    Text("Unified Ledger Logs", fontWeight = FontWeight.Bold)
                                    Text("Track employee salaries & payments", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                                }
                            },
                            selected = activeTab == 6,
                            onClick = {
                                viewModel.setTab(6)
                                coroutineScope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    
                    if (loggedInUser != null) {
                        val user = loggedInUser!!
                        Card(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = "Active Operator", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(text = user.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(text = "${user.role} Authorization", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                OutlinedButton(
                                    onClick = { 
                                        coroutineScope.launch { drawerState.close() }
                                        showChangePasswordDialog = true 
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Change Password")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Change Password")
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                
                                OutlinedButton(
                                    onClick = { 
                                        coroutineScope.launch { drawerState.close() }
                                        showLogoutDialog = true 
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Log Out")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Logout Session")
                                }
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Rounded-xl icon badge
                                IconButton(
                                    onClick = { coroutineScope.launch { drawerState.open() } },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Camera,
                                        contentDescription = "App Icon - Open Menu",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                Text(
                                    text = "Mobile Gallery",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.5).sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val isFirebaseConnected = com.example.data.repository.FirebaseSyncManager.isConfigured()
                                    Text(
                                        text = if (isFirebaseConnected) "FIREBASE SYNC ACTIVE" else "LOCAL OFFLINE-FIRST",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp,
                                        color = if (isFirebaseConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isSyncing) {
                                        Icon(
                                            imageVector = Icons.Default.Sync,
                                            contentDescription = "Syncing",
                                            tint = if (isFirebaseConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .size(12.dp)
                                                .rotate(spinningAngle)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (isFirebaseConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                            contentDescription = if (isFirebaseConnected) "Cloud Connected" else "Cloud Disconnected",
                                            tint = if (isFirebaseConnected) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Right side icons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
                            IconButton(
                                onClick = { viewModel.toggleTheme() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = "Toggle Theme",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { viewModel.triggerCloudSync() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Notifications",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { showLogoutDialog = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Account",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            val canManageInventory by viewModel.canManageInventory.collectAsState()
            val canSell by viewModel.canSell.collectAsState()
            
            if (activeTab == 0 && (canManageInventory || canSell)) { // Only show on Inventory screen
                FloatingActionButton(
                    onClick = { viewModel.setTab(1) }, // Navigate to transactions/add
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                    modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Item", modifier = Modifier.size(28.dp))
                }
            }
        },
        bottomBar = {
            val canManageUsers by viewModel.canManageUsers.collectAsState()
            val canViewAnalytics by viewModel.canViewAnalytics.collectAsState()
            val canManageInventory by viewModel.canManageInventory.collectAsState()
            val canSell by viewModel.canSell.collectAsState()
            val canViewLedger by viewModel.canViewLedger.collectAsState()
            
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Bottom Page Indices
                val tabsItems = mutableListOf<Triple<Int, String, androidx.compose.ui.graphics.vector.ImageVector>>()
                
                // Everybody gets Inventory (read-only for MIS)
                tabsItems.add(Triple(0, "Inventory", Icons.Default.Inventory))
                
                if (canManageInventory || canSell) {
                    tabsItems.add(Triple(1, "Transactions", Icons.AutoMirrored.Filled.Send))
                }
                if (canViewAnalytics) {
                    tabsItems.add(Triple(2, "Analytics", Icons.Default.Assessment))
                }
                
                // Everyone can see history
                tabsItems.add(Triple(3, "History", Icons.Default.History))

                tabsItems.forEach { (index, title, icon) ->
                    NavigationBarItem(
                        selected = activeTab == index,
                        onClick = { viewModel.setTab(index) },
                        icon = { Icon(imageVector = icon, contentDescription = "$title Page Selection") },
                        label = { Text(text = title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("nav_item_$title")
                    )
                }
            }
        }
    ) { innerPadding ->
        // Pull down gesture container wrapper around ALL screens
        PullToRefreshContainer(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshAllPages() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, easing = EaseInOutCubic)) togetherWith
                    fadeOut(animationSpec = tween(220, easing = EaseInOutCubic))
                },
                label = "Screen transitions anim"
            ) { targetScreen ->
                when (targetScreen) {
                    0 -> InventoryScreen(viewModel = viewModel)
                    1 -> TransactionsScreen(viewModel = viewModel)
                    2 -> AnalyticsScreen(viewModel = viewModel)
                    3 -> HistoryScreen(viewModel = viewModel)
                    4 -> AttendanceScreen(viewModel = viewModel)
                    5 -> UserManagementScreen(viewModel = viewModel)
                    6 -> com.example.ui.screens.LedgerScreen(viewModel = viewModel)
                    else -> InventoryScreen(viewModel = viewModel)
                }
            }
        }
    }

    // Confirm dialog for logging out
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Secure Exit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text("Confirm Logout Request") },
            text = {
                Text("Are you sure you want to log out from the administrative control panel? Real-time database synchronizations will sleep safely.")
            }
        )
    }

    if (showChangePasswordDialog) {
        var newSecretPassword by remember { mutableStateOf("") }
        var confirmSecretPassword by remember { mutableStateOf("") }
        var passwordError by remember { mutableStateOf<String?>(null) }
        
        AlertDialog(
            onDismissRequest = { 
                showChangePasswordDialog = false 
                newSecretPassword = ""
                confirmSecretPassword = ""
                passwordError = null
            },
            title = { Text("Change Password", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Set a secure password for your operator account (${loggedInUser?.username ?: ""}).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newSecretPassword,
                        onValueChange = { 
                            newSecretPassword = it
                            passwordError = null
                        },
                        label = { Text("New Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmSecretPassword,
                        onValueChange = { 
                            confirmSecretPassword = it
                            passwordError = null
                        },
                        label = { Text("Confirm New Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (passwordError != null) {
                        Text(
                            text = passwordError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newSecretPassword.isBlank()) {
                            passwordError = "Password cannot be blank"
                        } else if (newSecretPassword != confirmSecretPassword) {
                            passwordError = "Passwords do not match"
                        } else {
                            val activeUsername = loggedInUser?.username ?: "admin"
                            viewModel.changeUserPassword(activeUsername, newSecretPassword)
                            android.widget.Toast.makeText(context, "Password updated successfully!", android.widget.Toast.LENGTH_SHORT).show()
                            showChangePasswordDialog = false
                            newSecretPassword = ""
                            confirmSecretPassword = ""
                            passwordError = null
                        }
                    }
                ) {
                    Text("Change Password")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showChangePasswordDialog = false 
                        newSecretPassword = ""
                        confirmSecretPassword = ""
                        passwordError = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    } // closes ModalNavigationDrawer
}

data class IconStyleItem(val name: String, val primary: Color, val secondary: Color)

@Composable
fun AppIconPreviewCard(
    styleName: String,
    primaryColor: Color,
    secondaryColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) primaryColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, primaryColor) else null,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Miniature Mock Icon Canvas
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(primaryColor, secondaryColor)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (styleName) {
                        "Celestial Dusk" -> Icons.Default.Landscape
                        "Digital Stack" -> Icons.Default.Collections
                        else -> Icons.Default.Camera
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = styleName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when (styleName) {
                        "Mobile Gallery Core" -> "Premium high-contrast camera aperture iris (Default)."
                        "Celestial Dusk" -> "Vibrant mountain backdrop under linear warm sun."
                        "Digital Stack" -> "Overlapping photo layouts with geometric neon grids."
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 14.sp
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
