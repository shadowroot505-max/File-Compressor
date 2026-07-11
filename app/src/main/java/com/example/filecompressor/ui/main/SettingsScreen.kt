package com.example.filecompressor.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var cloudConnected by remember { mutableStateOf(false) }
    var driveEmail by remember { mutableStateOf("") }
    var driveFolder by remember { mutableStateOf("My Vault Backup") }
    var showDriveDialog by remember { mutableStateOf(false) }
    var compressionLevel by remember { mutableFloatStateOf(5f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Vault Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Text("⬅️", style = MaterialTheme.typography.titleMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Security Section
            SettingsSection(title = "Security") {
                ListItem(
                    headlineContent = { Text("Master Password") },
                    supportingContent = { Text(if (password.isEmpty()) "Not Set" else "Set (Hidden)") },
                    trailingContent = {
                        Button(onClick = { showPasswordDialog = true }) {
                            Text(if (password.isEmpty()) "Setup" else "Change")
                        }
                    }
                )
            }

            // Cloud Sync Section
            SettingsSection(title = "Cloud Backup") {
                ListItem(
                    headlineContent = { Text("Google Drive Sync") },
                    supportingContent = { 
                        Text(if (cloudConnected && driveEmail.isNotEmpty()) "Linked to: $driveEmail\nFolder: $driveFolder" else "Not Connected") 
                    },
                    trailingContent = {
                        Switch(
                            checked = cloudConnected,
                            onCheckedChange = { 
                                if (it) {
                                    showDriveDialog = true
                                } else {
                                    cloudConnected = false
                                }
                            }
                        )
                    }
                )
            }

            // Compression Section
            SettingsSection(title = "Compression Profile") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Level: ${compressionLevel.toInt()} (0 = Fast, 9 = Max Compression)", 
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = compressionLevel,
                        onValueChange = { compressionLevel = it },
                        valueRange = 0f..9f,
                        steps = 8
                    )
                }
            }
        }
    }

    if (showPasswordDialog) {
        var tempPassword by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Set Master Password") },
            text = {
                OutlinedTextField(
                    value = tempPassword,
                    onValueChange = { tempPassword = it },
                    label = { Text("AES-256 Encryption Password") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    password = tempPassword
                    showPasswordDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDriveDialog) {
        var emailInput by remember { mutableStateOf(driveEmail) }
        var folderInput by remember { mutableStateOf(driveFolder) }

        AlertDialog(
            onDismissRequest = { 
                showDriveDialog = false 
            },
            title = { Text("Google Drive Credentials") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter your cloud account credentials below to securely sync folder archives.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Google Account Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = folderInput,
                        onValueChange = { folderInput = it },
                        label = { Text("Target Drive Folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (emailInput.isNotBlank() && folderInput.isNotBlank()) {
                        driveEmail = emailInput
                        driveFolder = folderInput
                        cloudConnected = true
                    } else {
                        cloudConnected = false
                    }
                    showDriveDialog = false
                }) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDriveDialog = false 
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}
