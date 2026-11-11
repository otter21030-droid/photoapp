package com.framefit.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import java.io.File
import java.io.FileOutputStream
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
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    /**
     * Saves Base64-encoded file data (e.g. from blob: URLs or data URIs) to the public Downloads folder.
     */
    fun saveBase64ToDownloads(
        context: Context,
        base64Data: String,
        mimeType: String,
        suggestedFileName: String
    ): Uri? {
        val cleanBase64 = if (base64Data.contains(",")) {
            base64Data.substringAfter(",")
        } else {
            base64Data
        }

        val bytes = try {
            Base64.decode(cleanBase64, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }

        val fileName = sanitizeFileName(suggestedFileName, mimeType)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    uri
                } catch (e: Exception) {
                    e.printStackTrace()
                    resolver.delete(uri, null, null)
                    null
                }
            } else {
                null
            }
        } else {
            // Android 9 (Pie) or below
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val targetFile = File(downloadsDir, fileName)
            try {
                FileOutputStream(targetFile).use { out ->
                    out.write(bytes)
                    out.flush()
                }
                Uri.fromFile(targetFile)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun sanitizeFileName(fileName: String, mimeType: String): String {
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
                "application/zip" -> ".zip"
                "application/pdf" -> ".pdf"
                else -> ".png"
            }
            clean += ext
        }
        return clean
    }
}

