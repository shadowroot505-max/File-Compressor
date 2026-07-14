package com.example.filecompressor

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.filecompressor.theme.FileCompressorTheme
import com.example.filecompressor.ui.main.MainScreen
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var folderManager: FolderManager
    private var selectedFolder = mutableStateOf<String?>(null)
    private var folderFiles = mutableStateOf<List<File>>(emptyList())
    private var pendingRestoreFile: File? = null

    // Register SAF Document Tree launcher for restoring files
    private val restoreFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null && pendingRestoreFile != null) {
            Toast.makeText(this, "Restoring files to selected folder...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val success = ArchiveEngine.extractZipToSaf(this@MainActivity, pendingRestoreFile!!, treeUri)
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    if (success) {
                        pendingRestoreFile!!.delete()
                        refreshFolderFiles()
                        Toast.makeText(this@MainActivity, "Restoration complete (Removed from Vault)", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Restoration failed", Toast.LENGTH_SHORT).show()
                    }
                    pendingRestoreFile = null
                }
            }
        }
    }

    private fun refreshFolderFiles() {
        val folder = selectedFolder.value
        if (folder != null) {
            val targetFolder = File(File(filesDir, "VaultData"), folder)
            folderFiles.value = targetFolder.listFiles()?.filter { it.isFile && it.name.endsWith(".zip") } ?: emptyList()
        }
    }

    // File picker launcher that allows selecting multiple documents/images
    private val pickFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val folder = selectedFolder.value ?: "My Vault"
            Toast.makeText(this, "Compressing ${uris.size} files to $folder...", Toast.LENGTH_SHORT).show()
            
            // Run compression in a background thread
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                // Ensure the folder exists
                if (!folderManager.getFolders().contains(folder)) {
                    folderManager.createFolder(folder)
                }
                
                val targetFolder = File(File(filesDir, "VaultData"), folder)
                val zipFile = File(targetFolder, "Archive_${System.currentTimeMillis()}.zip")
                
                val success = ArchiveEngine.compressUris(this@MainActivity, uris, zipFile)
                
                if (success) {
                    var deletedCount = 0
                    for (uri in uris) {
                        try {
                            val deleted = android.provider.DocumentsContract.deleteDocument(contentResolver, uri)
                            if (deleted) deletedCount++
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    launch(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Saved to $folder! Originals deleted: $deletedCount", Toast.LENGTH_LONG).show()
                        refreshFolderFiles()
                    }
                } else {
                    launch(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Compression failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folderManager = FolderManager(this)
        
        enableEdgeToEdge()
        setContent {
            FileCompressorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("splash") }
                    var currentFolders by remember { mutableStateOf(folderManager.getFolders()) }
                    val currentFolderFiles by folderFiles
                    val activeFolder by selectedFolder

                    if (currentScreen == "splash") {
                        SplashScreen(onTimeout = { currentScreen = "main" })
                    } else if (currentScreen == "main") {
                        MainScreen(
                            folders = currentFolders,
                            onCreateFolder = { newFolderName ->
                                val success = folderManager.createFolder(newFolderName)
                                if (success) {
                                    currentFolders = folderManager.getFolders()
                                    Toast.makeText(this@MainActivity, "Folder Created", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@MainActivity, "Folder already exists", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onFolderClick = { folderName ->
                                selectedFolder.value = folderName
                                refreshFolderFiles()
                                currentScreen = "folder_contents"
                            },
                            onCompressClick = {
                                // If global compress clicked, default to first folder or "My Vault"
                                val folders = folderManager.getFolders()
                                selectedFolder.value = folders.firstOrNull() ?: "My Vault"
                                pickFilesLauncher.launch(arrayOf("image/*", "application/*", "text/*"))
                            },
                            onSettingsClick = {
                                currentScreen = "settings"
                            }
                        )
                    } else if (currentScreen == "folder_contents") {
                        activeFolder?.let { folderName ->
                            com.example.filecompressor.ui.main.FolderContentsScreen(
                                folderName = folderName,
                                files = currentFolderFiles,
                                onNavigateBack = {
                                    currentScreen = "main"
                                    selectedFolder.value = null
                                },
                                onRestoreFile = { file ->
                                    Toast.makeText(this, "Restoring file...", Toast.LENGTH_SHORT).show()
                                    lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        val success = ArchiveEngine.extractZipToDownloads(this@MainActivity, file)
                                        launch(kotlinx.coroutines.Dispatchers.Main) {
                                            if (success) {
                                                file.delete()
                                                refreshFolderFiles()
                                                Toast.makeText(this@MainActivity, "Restored to Downloads/VaultRestored! (Removed from Vault)", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(this@MainActivity, "Restoration failed", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                onDeleteFile = { file ->
                                    val deleted = file.delete()
                                    if (deleted) {
                                        Toast.makeText(this, "File deleted from Vault", Toast.LENGTH_SHORT).show()
                                        refreshFolderFiles()
                                    } else {
                                        Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onCompressClick = {
                                    pickFilesLauncher.launch(arrayOf("image/*", "application/*", "text/*"))
                                }
                            )
                        }
                    } else if (currentScreen == "settings") {
                        com.example.filecompressor.ui.main.SettingsScreen(
                            onNavigateBack = {
                                currentScreen = "main"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var startAnimation by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (startAnimation) 1.2f else 0.5f,
        animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
        label = "scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "alpha"
    )

    LaunchedEffect(key1 = true) {
        startAnimation = true
        kotlinx.coroutines.delay(2000)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🗄️",
                style = androidx.compose.ui.text.TextStyle(fontSize = 100.sp),
                modifier = Modifier.graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    alpha = alpha
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Secure File Vault",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.graphicsLayer(alpha = alpha)
            )
        }
    }
}
