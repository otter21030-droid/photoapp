package com.framefit.app

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    /**
     * Creates a temporary image file for camera captures.
     */
    fun createTempImageFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: context.cacheDir
        return File.createTempFile("IMG_${timeStamp}_", ".jpg", storageDir)
    }

    /**
     * Creates a temporary video file for camera video captures.
     */
    fun createTempVideoFile(context: Context): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.cacheDir
        return File.createTempFile("VID_${timeStamp}_", ".mp4", storageDir)
    }

    /**
     * Writes byte array data directly to a user-selected SAF Document Uri.
     */
    fun writeBytesToUri(context: Context, targetUri: Uri, bytes: ByteArray): Boolean {
        return try {
            context.contentResolver.openOutputStream(targetUri, "rwt")?.use { out ->
                out.write(bytes)
                out.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Streams data from an input stream to a user-selected SAF Document Uri.
     */
    fun writeStreamToUri(context: Context, targetUri: Uri, inputStream: InputStream): Boolean {
        return try {
            context.contentResolver.openOutputStream(targetUri, "rwt")?.use { out ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                }
                out.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun sanitizeFileName(fileName: String, mimeType: String): String {
        var clean = fileName.trim().replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        if (clean.isBlank()) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            clean = "framefit_$timestamp"
        }

        val hasExt = clean.contains(".")
        if (!hasExt) {
            val ext = when (mimeType.lowercase(Locale.ROOT)) {
                "image/png" -> ".png"
                "image/jpeg", "image/jpg" -> ".jpg"
                "image/webp" -> ".webp"
                "video/webm" -> ".webm"
                "video/mp4" -> ".mp4"
                "application/zip" -> ".zip"
                "application/pdf" -> ".pdf"
                else -> ".bin"
            }
            clean += ext
        }
        return clean
    }
}
