package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.AppDatabase
import com.example.data.repository.InventoryRepository
import com.example.ui.components.PullToRefreshContainer
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StockViewModel
import com.example.ui.viewmodel.ViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var repository: InventoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize database & repository singletons safely at activity scope
        database = AppDatabase.getDatabase(applicationContext)
        repository = InventoryRepository(
            database.inventoryDao(),
            database.historyDao(),
            database.userDao()
        )

        // Initialize Firebase dynamically if keys are configured
        com.example.data.repository.FirebaseSyncManager.initialize(applicationContext)

        setContent {
            // Instantiate StockViewModel
            val stockViewModel: StockViewModel = viewModel(
                factory = ViewModelFactory(repository)
            )

            val isDarkTheme by stockViewModel.isDarkTheme.collectAsState()
            val isLoggedIn by stockViewModel.isLoggedIn.collectAsState()

            MyApplicationTheme(darkTheme = isDarkTheme) {
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
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inventory,
                                    contentDescription = "App Icon",
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
                
                if (canManageUsers) {
                    tabsItems.add(Triple(5, "Users", Icons.Default.Group))
                }

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
                    5 -> UserManagementScreen(viewModel = viewModel)
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
                        viewModel.logout()
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
}
