package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.User
import com.example.ui.viewmodel.StockViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(viewModel: StockViewModel) {
    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    var showAddUserDialog by remember { mutableStateOf(false) }
    var changePasswordForUser by remember { mutableStateOf<User?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "User Management Console",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            FloatingActionButton(
                onClick = { showAddUserDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Default.Add, "Add User", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(users) { user ->
                UserCard(
                    user = user, 
                    onDelete = { viewModel.deleteUser(user) },
                    onChangePassword = { changePasswordForUser = user }
                )
            }
        }
    }

    if (changePasswordForUser != null) {
        var newSecretPassword by remember { mutableStateOf("") }
        val targetUser = changePasswordForUser!!
        AlertDialog(
            onDismissRequest = { changePasswordForUser = null },
            title = { Text("Change Password for ${targetUser.username}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "As administrator, you can override and set a new password for this user account.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newSecretPassword,
                        onValueChange = { newSecretPassword = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newSecretPassword.isNotBlank()) {
                        viewModel.changeUserPassword(targetUser.username, newSecretPassword)
                        changePasswordForUser = null
                    }
                }) {
                    Text("Change Password")
                }
            },
            dismissButton = {
                TextButton(onClick = { changePasswordForUser = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddUserDialog) {
        var newUsername by remember { mutableStateOf("") }
        var newPassword by remember { mutableStateOf("") }
        var selectedRole by remember { mutableStateOf("Operator") }
        val roles = listOf("Admin", "Manager", "Operator", "MIS", "Sales")
        var expanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddUserDialog = false },
            title = { Text("Add New User") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text("Username") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Password") },
                        singleLine = true
                    )
                    
                    Box {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Role: $selectedRole")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            roles.forEach { role ->
                                DropdownMenuItem(
                                    text = { Text(role) },
                                    onClick = {
                                        selectedRole = role
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newUsername.isNotBlank() && newPassword.isNotBlank()) {
                        viewModel.addUser(newUsername, newPassword, selectedRole)
                        showAddUserDialog = false
                    }
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddUserDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun UserCard(user: User, onDelete: () -> Unit, onChangePassword: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.Person, contentDescription = "User", tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text(text = user.username, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(text = "Role: ${user.role}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onChangePassword) {
                    Icon(Icons.Default.Edit, contentDescription = "Change User Password", tint = MaterialTheme.colorScheme.primary)
                }
                if (user.username != "admin") { // Prevent deleting default admin
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete User", tint = Color.Red)
                    }
                }
            }
        }
    }
}
