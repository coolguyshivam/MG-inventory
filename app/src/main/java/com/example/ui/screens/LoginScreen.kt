package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val defaultPin = "1234"

    fun handlePinInput(digit: String) {
        if (pin.length < 4) {
            pin += digit
            errorMessage = ""
        }
        if (pin.length == 4) {
            if (pin == defaultPin) {
                onLoginSuccess()
            } else {
                errorMessage = "Incorrect PIN. Try '1234'"
                pin = ""
            }
        }
    }

    Scaffold { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(72.dp)
                        .padding(bottom = 16.dp)
                )

                Text(
                    text = "Welcome to Mobile Gallery",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = "Please enter security PIN or use biometric sign-in",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 32.dp)
                )

                // PIN dots indicators
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    repeat(4) { idx ->
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .padding(horizontal = 4.dp)
                                .clip(CircleShape)
                                .background(
                                    if (idx < pin.length) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                                )
                        )
                    }
                }

                AnimatedVisibility(
                    visible = errorMessage.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Numeric Keypad Grid
                Column(
                    modifier = Modifier.widthIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("fingerprint", "0", "backspace")
                    )

                    keys.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { item ->
                                when (item) {
                                    "fingerprint" -> {
                                        IconButton(
                                            onClick = { onLoginSuccess() }, // Bypass biometric mock
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                .testTag("biometric_login_button"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Biometric Login",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                    "backspace" -> {
                                        IconButton(
                                            onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack,
                                                contentDescription = "Backspace",
                                                tint = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    }
                                    else -> {
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surface)
                                                .clickable { handlePinInput(item) }
                                                .testTag("keypad_$item"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item,
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Hint to help user
                TextButton(
                    onClick = { pin = defaultPin; handlePinInput("") },
                    modifier = Modifier.padding(top = 32.dp)
                ) {
                    Text(
                        text = "Demo PIN Hint: 1234 (Or press key lock icon)",
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
