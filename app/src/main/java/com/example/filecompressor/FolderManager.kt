package com.example.filecompressor

import android.content.Context
import java.io.File

/**
 * Manages the creation, editing, and deletion of virtual archive folders on the device's internal storage.
 */
class FolderManager(private val context: Context) {

    private val baseVaultDir: File
        get() = File(context.filesDir, "VaultData").apply {
            if (!exists()) {
                mkdirs()
            }
        }

    /**
     * Creates a new folder within the vault.
     */
    fun createFolder(folderName: String): Boolean {
        val newFolder = File(baseVaultDir, folderName)
        if (newFolder.exists()) {
            return false // Folder already exists
        }
        return newFolder.mkdirs()
    }

    /**
     * Lists all folders within the vault.
     */
    fun getFolders(): List<String> {
        return baseVaultDir.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList()
    }

    /**
     * Renames an existing folder.
     */
    fun renameFolder(oldName: String, newName: String): Boolean {
        val oldFolder = File(baseVaultDir, oldName)
        val newFolder = File(baseVaultDir, newName)
        if (!oldFolder.exists() || newFolder.exists()) {
            return false
        }
        return oldFolder.renameTo(newFolder)
    }

    /**
     * Deletes a folder and all of its contents (archives).
     */
    fun deleteFolder(folderName: String): Boolean {
        val folder = File(baseVaultDir, folderName)
        return if (folder.exists()) {
            folder.deleteRecursively()
        } else {
            false
        }
    }
}
