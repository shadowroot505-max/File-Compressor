package com.example.filecompressor

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

    /**
     * Extracts a ZIP archive to a destination directory.
     * @param zipFile The ZIP file to extract.
     * @param destDir The directory where files will be extracted.
     * @return true if successful, false otherwise.
     */
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
}
