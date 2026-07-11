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
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var folderManager: FolderManager

    // File picker launcher that allows selecting multiple documents/images
    private val pickFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            Toast.makeText(this, "Compressing ${uris.size} files to vault...", Toast.LENGTH_SHORT).show()
            
            // Run compression in a background thread
            androidx.lifecycle.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                // Ensure at least one folder exists to save to
                var folders = folderManager.getFolders()
                if (folders.isEmpty()) {
                    folderManager.createFolder("My Vault")
                    folders = folderManager.getFolders()
                }
                
                val targetFolder = java.io.File(File(filesDir, "VaultData"), folders.first())
                val zipFile = java.io.File(targetFolder, "Archive_${System.currentTimeMillis()}.zip")
                
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
                        Toast.makeText(this@MainActivity, "Saved to ${folders.first()}! Originals deleted: $deletedCount", Toast.LENGTH_LONG).show()
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
                    var currentScreen by remember { mutableStateOf("main") }
                    var currentFolders by remember { mutableStateOf(folderManager.getFolders()) }

                    if (currentScreen == "main") {
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
                            onCompressClick = {
                                pickFilesLauncher.launch(arrayOf("image/*", "application/*", "text/*"))
                            },
                            onSettingsClick = {
                                currentScreen = "settings"
                            }
                        )
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
