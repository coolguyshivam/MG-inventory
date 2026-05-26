package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.StockViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(viewModel: StockViewModel) {
    val appContext = androidx.compose.ui.platform.LocalContext.current
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val loginError by viewModel.loginError.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Gradient brush background
    val bgGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradient)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
        ) {
            // App Branding Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "App Logo",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Text(
                    text = "MOBILE GALLERY",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "Smartphones & Gadgets Inventory Suite",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }

            // Login Panel Card
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(24.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Sign In To Account",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Error Message Display
                    AnimatedVisibility(
                        visible = loginError != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        loginError?.let { err ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = err,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Username Input
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            viewModel.clearFormErrorAndSuccess()
                        },
                        label = { Text("Username") },
                        placeholder = { Text("admin") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Username icon"
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("username_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )

                    // Password Input
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            viewModel.clearFormErrorAndSuccess()
                        },
                        label = { Text("Password") },
                        placeholder = { Text("admin") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password icon"
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("password_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )

                    // Standard Login Button
                    Button(
                        onClick = { viewModel.login(appContext, username, password) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("login_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "Login",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Visual OR separator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = "OR",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    // Biometric Authentication Trigger button
                    OutlinedButton(
                        onClick = {
                            val activity = appContext as? androidx.fragment.app.FragmentActivity
                            if (activity != null) {
                                val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                                val biometricPrompt = androidx.biometric.BiometricPrompt(activity, executor,
                                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                            super.onAuthenticationError(errorCode, errString)
                                            android.widget.Toast.makeText(appContext, "Authentication error: $errString", android.widget.Toast.LENGTH_SHORT).show()
                                        }

                                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                            super.onAuthenticationSucceeded(result)
                                            viewModel.biometricLogin(appContext)
                                        }

                                        override fun onAuthenticationFailed() {
                                            super.onAuthenticationFailed()
                                            android.widget.Toast.makeText(appContext, "Authentication failed", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    })

                                val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Biometric Login")
                                    .setSubtitle("Log in using your biometric credential")
                                    .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                                    .build()

                                biometricPrompt.authenticate(promptInfo)
                            } else {
                                android.widget.Toast.makeText(appContext, "Biometrics not supported here", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("biometric_login_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors()
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = "Biometric thumb scan"
                            )
                            Text(
                                text = "Biometric Real-Time Sign-In",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Tip/Helper footnote
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "💡 Default Administrator credentials: admin / admin",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Use the 'User Management' panel as Administrator to create customized accounts for Manager or Employee roles to test visual restriction flows.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }



        // Biometric registration / linking dialog flow
        val showBiometricLinkingDialog by viewModel.showBiometricLinkingDialog.collectAsState()
        val tempPendingUser by viewModel.tempPendingUser.collectAsState()

        if (showBiometricLinkingDialog && tempPendingUser != null) {
            AlertDialog(
                onDismissRequest = {
                    viewModel.showBiometricLinkingDialog.value = false
                    viewModel.completeLogin(tempPendingUser!!)
                },
                title = {
                    Text(
                        text = "Unlock with Biometrics?",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Would you like to enable quick fingerprint login for ${tempPendingUser!!.username}? Next time you sign in, simply click the biometric button to log in instantly.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )

                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Prompt Fingerprint",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val activity = appContext as? androidx.fragment.app.FragmentActivity
                            if (activity != null) {
                                val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
                                val biometricPrompt = androidx.biometric.BiometricPrompt(activity, executor,
                                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                            super.onAuthenticationSucceeded(result)
                                            viewModel.registerBiometrics(appContext, tempPendingUser!!.username)
                                            viewModel.completeLogin(tempPendingUser!!)
                                            viewModel.showBiometricLinkingDialog.value = false
                                        }

                                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                            super.onAuthenticationError(errorCode, errString)
                                            android.widget.Toast.makeText(appContext, "Authentication error: $errString", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        
                                        override fun onAuthenticationFailed() {
                                            super.onAuthenticationFailed()
                                            android.widget.Toast.makeText(appContext, "Authentication failed", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    })
                                    
                                val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Link Biometric")
                                    .setSubtitle("Scan to register biometric sign-in")
                                    .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                                    .build()
                                    
                                biometricPrompt.authenticate(promptInfo)
                            }
                        }
                    ) {
                        Text("Enable & Link")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.completeLogin(tempPendingUser!!)
                            viewModel.showBiometricLinkingDialog.value = false
                        }
                    ) {
                        Text("Skip for Now")
                    }
                }
            )
        }
    }
}
