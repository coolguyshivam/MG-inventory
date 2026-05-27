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
import com.example.data.repository.InventoryRepository
import com.example.ui.components.PullToRefreshContainer
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.StockViewModel
import com.example.ui.viewmodel.ViewModelFactory
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : FragmentActivity() {
    private lateinit var repository: InventoryRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Firebase dynamically if keys are configured
        com.example.data.repository.FirebaseSyncManager.initialize(applicationContext)

        repository = InventoryRepository()

        setContent {
            // Instantiate StockViewModel
            val stockViewModel: StockViewModel = viewModel(
                factory = ViewModelFactory(repository)
            )

            val isDarkTheme by stockViewModel.isDarkTheme.collectAsState()
            val isLoggedIn by stockViewModel.isLoggedIn.collectAsState()
            val appIconStyle by stockViewModel.appIconStyle.collectAsState()

            LaunchedEffect(Unit) {
                stockViewModel.loadAppIconStyle(applicationContext)
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(16.dp))
                    NavigationDrawerItem(
                        label = { Text("App Attendance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                        selected = false,
                        onClick = { /* Handle click */ },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(16.dp))
                    
                    // Attendance section
                    Text(
                        text = "Selfie/Location based check-in required.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            android.widget.Toast.makeText(context, "Attendance Marked Successfully (Simulated)", android.widget.Toast.LENGTH_SHORT).show()
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Take Selfie", modifier = Modifier.size(18.dp).padding(end = 4.dp))
                        Text("Check In")
                    }

                    // Theme Branded Icons header
                    HorizontalDivider(modifier = Modifier.padding(16.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Customize App Style",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Customize the app aesthetic and preview unique modern icon collections below.",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    // List of interactive styles
                    val appIconStyle by viewModel.appIconStyle.collectAsState()
                    val styles = listOf(
                        IconStyleItem("Classic Slate", Color(0xFF2563EB), Color(0xFF3B82F6)),
                        IconStyleItem("Sunset Glow", Color(0xFFE11D48), Color(0xFFFB7185)),
                        IconStyleItem("Emerald Mint", Color(0xFF0D9488), Color(0xFF10B981)),
                        IconStyleItem("Golden Luxury", Color(0xFFD97706), Color(0xFFF59E0B)),
                        IconStyleItem("Vibrant Indigo", Color(0xFF4F46E5), Color(0xFF6366F1))
                    )

                    styles.forEach { item ->
                        AppIconPreviewCard(
                            styleName = item.name,
                            primaryColor = item.primary,
                            secondaryColor = item.secondary,
                            isSelected = appIconStyle == item.name,
                            onClick = {
                                viewModel.setAppIconStyle(context, item.name)
                                android.widget.Toast.makeText(context, "${item.name} Style Applied!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    
                    Spacer(Modifier.height(32.dp))
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
                                        imageVector = Icons.Default.Inventory,
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
                
                if (canManageUsers) {
                    tabsItems.add(Triple(5, "Users", Icons.Default.Group))
                }
                if (canViewLedger) {
                    tabsItems.add(Triple(6, "Ledger", Icons.Default.CompareArrows))
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
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                
                // Camera Lens Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.8f))
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
                        "Classic Slate" -> "Professional clean blue theme."
                        "Sunset Glow" -> "Warm artistic coral pink & gold dusk."
                        "Emerald Mint" -> "Fresh mint & natural emerald."
                        "Golden Luxury" -> "Premium executive polished gold."
                        "Vibrant Indigo" -> "Bold cosmic star violet & indigo."
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
