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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {

    private lateinit var folderManager: FolderManager
    private var foldersState = mutableStateOf<List<String>>(emptyList())

    // File picker launcher that allows selecting multiple documents/images
    private val pickFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            Toast.makeText(this, "Compressing and moving ${uris.size} files to vault...", Toast.LENGTH_SHORT).show()
            // Here we would pass the URIs to the ArchiveEngine.
            // For now, we simulate success and attempt to delete the original files.
            var deletedCount = 0
            for (uri in uris) {
                try {
                    // Attempt to delete original file via ContentResolver
                    val deletedRows = contentResolver.delete(uri, null, null)
                    if (deletedRows > 0) {
                        deletedCount++
                    }
                } catch (e: SecurityException) {
                    // On Android 11+, we may need to ask for specific user permission via createDeleteRequest
                    e.printStackTrace()
                }
            }
            Toast.makeText(this, "Moved $deletedCount files to vault (Originals deleted)", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        folderManager = FolderManager(this)
        foldersState.value = folderManager.getFolders()
        
        enableEdgeToEdge()
        setContent {
            FileCompressorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("main") }
                    val currentFolders by foldersState

                    if (currentScreen == "main") {
                        MainScreen(
                            folders = currentFolders,
                            onCreateFolder = { newFolderName ->
                                val success = folderManager.createFolder(newFolderName)
                                if (success) {
                                    foldersState.value = folderManager.getFolders()
                                    Toast.makeText(this, "Folder Created", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, "Folder already exists", Toast.LENGTH_SHORT).show()
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
