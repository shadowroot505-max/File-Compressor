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
    private var selectedFolder = mutableStateOf<String?>(null)
    private var folderFiles = mutableStateOf<List<File>>(emptyList())

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
                    var currentScreen by remember { mutableStateOf("main") }
                    var currentFolders by remember { mutableStateOf(folderManager.getFolders()) }
                    val currentFolderFiles by folderFiles
                    val activeFolder by selectedFolder

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
                                        val restoreDir = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: filesDir
                                        val targetDir = File(restoreDir, "Restored_${folderName}_${System.currentTimeMillis()}")
                                        targetDir.mkdirs()
                                        
                                        val success = ArchiveEngine.extractArchive(file, targetDir)
                                        launch(kotlinx.coroutines.Dispatchers.Main) {
                                            if (success) {
                                                Toast.makeText(this@MainActivity, "Restored to: ${targetDir.absolutePath}", Toast.LENGTH_LONG).show()
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
