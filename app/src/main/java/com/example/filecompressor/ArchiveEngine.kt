package com.example.filecompressor

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ArchiveEngine {

    /**
     * Compresses a list of files into a single ZIP archive.
     * @param files The list of files to compress.
     * @param zipFile The destination ZIP file.
     * @return true if successful, false otherwise.
     */
    fun compressFiles(files: List<File>, zipFile: File): Boolean {
        return try {
            FileOutputStream(zipFile).use { fos ->
                ZipOutputStream(BufferedOutputStream(fos)).use { zos ->
                    for (file in files) {
                        if (!file.exists()) continue
                        
                        FileInputStream(file).use { fis ->
                            BufferedInputStream(fis).use { bis ->
                                val entry = ZipEntry(file.name)
                                zos.putNextEntry(entry)
                                
                                val buffer = ByteArray(1024)
                                var count: Int
                                while (bis.read(buffer).also { count = it } != -1) {
                                    zos.write(buffer, 0, count)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun compressUris(context: Context, uris: List<Uri>, zipFile: File): Boolean {
        return try {
            val contentResolver = context.contentResolver
            FileOutputStream(zipFile).use { fos ->
                ZipOutputStream(BufferedOutputStream(fos)).use { zos ->
                    for (uri in uris) {
                        // Get original file name
                        var fileName = "file_${System.currentTimeMillis()}"
                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (nameIndex >= 0) {
                                    fileName = cursor.getString(nameIndex)
                                }
                            }
                        }

                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            BufferedInputStream(inputStream).use { bis ->
                                val entry = ZipEntry(fileName)
                                zos.putNextEntry(entry)

                                val buffer = ByteArray(1024)
                                var count: Int
                                while (bis.read(buffer).also { count = it } != -1) {
                                    zos.write(buffer, 0, count)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun extractArchive(zipFile: File, destDir: File): Boolean {
        return try {
            if (!destDir.exists()) destDir.mkdirs()

            FileInputStream(zipFile).use { fis ->
                ZipInputStream(BufferedInputStream(fis)).use { zis ->
                    var entry: ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        val newFile = File(destDir, entry!!.name)
                        
                        // Prevent Zip Slip vulnerability
                        val destDirPath = destDir.canonicalPath
                        val destFilePath = newFile.canonicalPath
                        if (!destFilePath.startsWith(destDirPath + File.separator)) {
                            throw SecurityException("Entry is outside of the target dir: ${entry!!.name}")
                        }

                        if (entry!!.isDirectory) {
                            newFile.mkdirs()
                        } else {
                            File(newFile.parent).mkdirs()
                            FileOutputStream(newFile).use { fos ->
                                BufferedOutputStream(fos).use { bos ->
                                    val buffer = ByteArray(1024)
                                    var count: Int
                                    while (zis.read(buffer).also { count = it } != -1) {
                                        bos.write(buffer, 0, count)
                                    }
                                }
                            }
                        }
                        zis.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    fun extractZipToDownloads(context: Context, zipFile: File): Boolean {
        return try {
            val contentResolver = context.contentResolver
            FileInputStream(zipFile).use { fis ->
                ZipInputStream(BufferedInputStream(fis)).use { zis ->
                    var entry: ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        if (entry!!.isDirectory) continue
                        
                        val entryName = entry!!.name
                        val mimeType = getMimeType(entryName)
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val values = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, entryName)
                                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/VaultRestored")
                            }
                            val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            if (uri != null) {
                                contentResolver.openOutputStream(uri)?.use { fos ->
                                    BufferedOutputStream(fos).use { bos ->
                                        val buffer = ByteArray(1024)
                                        var count: Int
                                        while (zis.read(buffer).also { count = it } != -1) {
                                            bos.write(buffer, 0, count)
                                        }
                                    }
                                }
                            }
                        } else {
                            val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                            val restoredDir = File(downloadDir, "VaultRestored")
                            if (!restoredDir.exists()) restoredDir.mkdirs()
                            val targetFile = File(restoredDir, entryName)
                            FileOutputStream(targetFile).use { fos ->
                                BufferedOutputStream(fos).use { bos ->
                                    val buffer = ByteArray(1024)
                                    var count: Int
                                    while (zis.read(buffer).also { count = it } != -1) {
                                        bos.write(buffer, 0, count)
                                    }
                                }
                            }
                        }
                        zis.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun extractZipToSaf(context: Context, zipFile: File, treeUri: Uri): Boolean {
        return try {
            val documentTree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, treeUri) ?: return false
            FileInputStream(zipFile).use { fis ->
                ZipInputStream(BufferedInputStream(fis)).use { zis ->
                    var entry: ZipEntry?
                    while (zis.nextEntry.also { entry = it } != null) {
                        if (entry!!.isDirectory) {
                            continue
                        }
                        
                        val mimeType = getMimeType(entry!!.name)
                        val newFile = documentTree.createFile(mimeType, entry!!.name) ?: continue
                        
                        context.contentResolver.openOutputStream(newFile.uri)?.use { fos ->
                            BufferedOutputStream(fos).use { bos ->
                                val buffer = ByteArray(1024)
                                var count: Int
                                while (zis.read(buffer).also { count = it } != -1) {
                                    bos.write(buffer, 0, count)
                                }
                            }
                        }
                        zis.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "txt" -> "text/plain"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}
