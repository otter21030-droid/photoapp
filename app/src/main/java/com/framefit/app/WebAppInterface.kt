package com.framefit.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.widget.Toast

class WebAppInterface(
    private val context: Context,
    private val onDownloadCompleted: (filename: String) -> Unit,
    private val onDownloadFailed: (error: String) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun downloadBase64(base64Data: String, mimeType: String, filename: String) {
        Thread {
            try {
                val uri = FileUtils.saveBase64ToDownloads(
                    context = context,
                    base64Data = base64Data,
                    mimeType = mimeType.ifBlank { "image/png" },
                    suggestedFileName = filename.ifBlank { "framefit_image" }
                )

                mainHandler.post {
                    if (uri != null) {
                        onDownloadCompleted(filename)
                    } else {
                        onDownloadFailed("파일 저장에 실패했습니다.")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post {
                    onDownloadFailed(e.localizedMessage ?: "다운로드 중 오류가 발생했습니다.")
                }
            }
        }.start()
    }

    @JavascriptInterface
    fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

