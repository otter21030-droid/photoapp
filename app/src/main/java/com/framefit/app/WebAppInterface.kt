package com.framefit.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class WebAppInterface(
    private val context: Context,
    private val onPromptSaveFile: (fileName: String, mimeType: String, tempFile: File) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionFiles = ConcurrentHashMap<String, File>()

    /**
     * Called by injected JavaScript when the user triggers a single or small file download.
     */
    @JavascriptInterface
    fun requestSaveFile(base64Data: String, mimeType: String, filename: String) {
        Thread {
            try {
                val cleanBase64 = if (base64Data.contains(",")) {
                    base64Data.substringAfter(",")
                } else {
                    base64Data
                }

                val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
                val sanitizedName = FileUtils.sanitizeFileName(filename, mimeType)
                val tempFile = File(context.cacheDir, "save_${System.currentTimeMillis()}_$sanitizedName")
                FileOutputStream(tempFile).use { it.write(bytes) }

                mainHandler.post {
                    onPromptSaveFile(sanitizedName, mimeType, tempFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post {
                    Toast.makeText(context, "파일 처리 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /**
     * Chunk-based save: Start session for large files (videos, zip archives).
     */
    @JavascriptInterface
    fun startFileSaveSession(sessionId: String, filename: String, mimeType: String) {
        try {
            val sanitizedName = FileUtils.sanitizeFileName(filename, mimeType)
            val tempFile = File(context.cacheDir, "session_${sessionId}_$sanitizedName")
            if (tempFile.exists()) tempFile.delete()
            tempFile.createNewFile()
            sessionFiles[sessionId] = tempFile
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Chunk-based save: Append base64 chunk.
     */
    @JavascriptInterface
    fun appendFileChunk(sessionId: String, chunkBase64: String) {
        val tempFile = sessionFiles[sessionId] ?: return
        try {
            val bytes = Base64.decode(chunkBase64, Base64.DEFAULT)
            FileOutputStream(tempFile, true).use { it.write(bytes) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Chunk-based save: Complete session and prompt user to choose destination folder.
     */
    @JavascriptInterface
    fun completeFileSaveSession(sessionId: String, filename: String, mimeType: String) {
        val tempFile = sessionFiles.remove(sessionId) ?: return
        val sanitizedName = FileUtils.sanitizeFileName(filename, mimeType)
        mainHandler.post {
            onPromptSaveFile(sanitizedName, mimeType, tempFile)
        }
    }

    @JavascriptInterface
    fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
