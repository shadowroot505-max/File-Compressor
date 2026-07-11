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

class MainActivity : ComponentActivity() {

    // File picker launcher that allows selecting multiple documents/images
    private val pickFilesLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            Toast.makeText(this, "Selected ${uris.size} files for compression!", Toast.LENGTH_SHORT).show()
            // Here we would pass the URIs to the ArchiveEngine
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FileCompressorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("main") }

                    if (currentScreen == "main") {
                        MainScreen(
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
