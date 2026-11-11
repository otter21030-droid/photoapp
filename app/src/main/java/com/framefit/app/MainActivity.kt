package com.framefit.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.DownloadListener
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.framefit.app.databinding.ActivityMainBinding
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TARGET_URL = "https://otter21030-droid.github.io/newapp/"
        private const val BACK_PRESS_INTERVAL = 2000L
    }

    private lateinit var binding: ActivityMainBinding
    private var backPressedTime = 0L

    // File Chooser state
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null
    private var cameraVideoUri: Uri? = null

    // SAF Save File state
    private var pendingSaveFile: File? = null
    private var pendingSaveFileName: String? = null

    // Fullscreen Video State
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
    private lateinit var saveFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFullScreen()
        setupLaunchers()
        setupWebView()
        setupSwipeRefresh()
        setupErrorHandling()
        setupBackPressHandler()

        binding.webView.loadUrl(TARGET_URL)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && customView == null) {
            setupFullScreen()
        }
    }

    private fun setupFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupLaunchers() {
        // 1. File Chooser Launcher (Images, Videos, PDFs)
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val callback = fileUploadCallback ?: return@registerForActivityResult
            var results: Array<Uri>? = null

            if (result.resultCode == RESULT_OK) {
                val data = result.data
                if (data != null && (data.data != null || data.clipData != null)) {
                    val clipData = data.clipData
                    results = if (clipData != null && clipData.itemCount > 0) {
                        Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                    } else if (data.data != null) {
                        arrayOf(data.data!!)
                    } else {
                        null
                    }
                } else if (cameraImageUri != null) {
                    results = arrayOf(cameraImageUri!!)
                } else if (cameraVideoUri != null) {
                    results = arrayOf(cameraVideoUri!!)
                }
            }

            callback.onReceiveValue(results)
            fileUploadCallback = null
            cameraImageUri = null
            cameraVideoUri = null
        }

        // 2. SAF Save File Launcher (User selects destination in device storage)
        saveFileLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val tempFile = pendingSaveFile
            val fileName = pendingSaveFileName ?: "file"

            if (result.resultCode == RESULT_OK && result.data?.data != null && tempFile != null && tempFile.exists()) {
                val targetUri = result.data!!.data!!
                val success = try {
                    FileInputStream(tempFile).use { input ->
                        FileUtils.writeStreamToUri(this, targetUri, input)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }

                if (success) {
                    val msg = getString(R.string.save_file_success, fileName)
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.save_file_canceled), Toast.LENGTH_SHORT).show()
            }

            // Cleanup temp file
            try {
                tempFile?.delete()
            } catch (e: Exception) {
                /* ignore */
            }
            pendingSaveFile = null
            pendingSaveFileName = null
        }

        // 3. Permission Launcher
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* Handled */ }
    }

    /**
     * Prompts the user with the system Storage Access Framework to choose where to save the file.
     */
    fun promptSaveFile(fileName: String, mimeType: String, tempFile: File) {
        pendingSaveFile = tempFile
        pendingSaveFileName = fileName

        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (mimeType.isNotBlank()) mimeType else "*/*"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        saveFileLauncher.launch(saveIntent)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        // Enable modern web app capabilities
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        // Viewport & Scale optimization for mobile
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = false
        settings.textZoom = 100
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // Media & Video playback support
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // User-Agent: Mobile Browser
        val defaultUserAgent = settings.userAgentString
        settings.userAgentString = "$defaultUserAgent Mobile FrameFitApp/1.1"

        // JavaScript Interface for SAF File Save Bridge
        val bridge = WebAppInterface(
            context = this,
            onPromptSaveFile = { fileName, mimeType, tempFile ->
                promptSaveFile(fileName, mimeType, tempFile)
            }
        )
        webView.addJavascriptInterface(bridge, "AndroidBridge")

        // Standard HTTP/HTTPS download fallback
        webView.setDownloadListener(DownloadListener { url, _, _, mimetype, _ ->
            handleUrlDownload(url, mimetype)
        })

        // WebChromeClient (File Chooser for Photos/Videos/PDFs, Progress, Fullscreen Video)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress > 40) {
                    injectMobileEnhancements(view)
                }

                if (newProgress < 100) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = newProgress
                } else {
                    binding.progressBar.visibility = View.GONE
                    if (binding.layoutSplash.visibility == View.VISIBLE) {
                        binding.layoutSplash.animate()
                            .alpha(0f)
                            .setDuration(250)
                            .withEndAction { binding.layoutSplash.visibility = View.GONE }
                            .start()
                    }
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                openCustomFileChooser(fileChooserParams)
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let {
                    it.grant(it.resources)
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback

                binding.swipeRefreshLayout.visibility = View.GONE
                binding.customViewContainer.visibility = View.VISIBLE
                binding.customViewContainer.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            override fun onHideCustomView() {
                if (customView == null) return

                binding.customViewContainer.removeView(customView)
                binding.customViewContainer.visibility = View.GONE
                binding.swipeRefreshLayout.visibility = View.VISIBLE

                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null

                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                setupFullScreen()
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                return super.onConsoleMessage(consoleMessage)
            }
        }

        // WebViewClient
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: ""

                if (scheme != "http" && scheme != "https") {
                    return try {
                        val intent = Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                        startActivity(intent)
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        true
                    }
                }
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.layoutError.visibility = View.GONE
                injectMobileEnhancements(view)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.swipeRefreshLayout.isRefreshing = false
                injectMobileEnhancements(view)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    binding.layoutError.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * Injects mobile responsive CSS & Download intercept scripts into the webview.
     */
    private fun injectMobileEnhancements(view: WebView?) {
        if (view == null) return

        val mobileCss = """
            /* FrameFit Mobile Responsive Injected CSS */
            html, body {
                min-width: 0 !important;
                width: 100% !important;
                max-width: 100vw !important;
                overflow-x: hidden !important;
                -webkit-text-size-adjust: 100% !important;
            }
            #app {
                width: 100% !important;
                max-width: 100vw !important;
                overflow-x: hidden !important;
            }
            .shell {
                min-width: 0 !important;
                width: 100% !important;
                max-width: 100% !important;
                padding: 12px 14px 48px !important;
                box-sizing: border-box !important;
            }
            .topbar {
                flex-direction: column !important;
                align-items: stretch !important;
                gap: 12px !important;
                margin-bottom: 16px !important;
            }
            .brand {
                text-align: left !important;
                order: -1 !important;
            }
            .brand p {
                font-size: 11px !important;
                margin-bottom: 2px !important;
            }
            .brand h1 {
                font-size: 30px !important;
                line-height: 1.05 !important;
            }
            .formats {
                display: flex !important;
                flex-wrap: nowrap !important;
                overflow-x: auto !important;
                -webkit-overflow-scrolling: touch !important;
                gap: 8px !important;
                padding-bottom: 6px !important;
                max-width: 100% !important;
            }
            .format-chip {
                flex: 0 0 auto !important;
                min-width: 94px !important;
                padding: 8px 10px !important;
                border-radius: 12px !important;
            }
            .format-chip svg {
                width: 22px !important;
                height: 22px !important;
            }
            .format-chip strong {
                font-size: 12px !important;
            }
            .format-chip span {
                font-size: 10px !important;
            }
            .dropzone {
                min-height: 180px !important;
                padding: 24px 16px !important;
                border-radius: 20px !important;
            }
            .dropzone.is-compact {
                grid-template-columns: 1fr !important;
                text-align: center !important;
                padding: 16px !important;
                gap: 12px !important;
            }
            .drop-copy h2 {
                font-size: 24px !important;
            }
            .drop-copy p {
                font-size: 13px !important;
            }
            .file-btn {
                margin-top: 14px !important;
                padding: 12px 24px !important;
                font-size: 14px !important;
                width: 100% !important;
                max-width: 260px !important;
            }
            .controls {
                grid-template-columns: 1fr !important;
                gap: 16px !important;
                padding: 16px !important;
                border-radius: 16px !important;
            }
            .batch {
                justify-items: stretch !important;
                min-width: 0 !important;
                width: 100% !important;
            }
            .batch button {
                width: 100% !important;
                height: 46px !important;
                font-size: 15px !important;
                border-radius: 12px !important;
            }
            .ratio-row, .mode-row {
                display: flex !important;
                flex-wrap: wrap !important;
                gap: 6px !important;
            }
            .choice span {
                min-height: 36px !important;
                padding: 0 12px !important;
                font-size: 13px !important;
            }
            .file-card {
                padding: 14px !important;
                border-radius: 16px !important;
            }
            .file-card-top {
                grid-template-columns: 68px 1fr !important;
                gap: 10px !important;
            }
            .file-card-top .row-actions {
                grid-column: 1 / -1 !important;
                display: flex !important;
                flex-wrap: wrap !important;
                gap: 8px !important;
                width: 100% !important;
            }
            .row-actions button, .file-toolbar button {
                flex: 1 1 calc(50% - 6px) !important;
                min-width: 0 !important;
                height: 42px !important;
                font-size: 13px !important;
                text-align: center !important;
            }
            .edit-grid {
                grid-template-columns: 1fr !important;
                gap: 16px !important;
            }
            .filter-grid {
                grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
                gap: 6px !important;
            }
            .preview-pair {
                grid-template-columns: 1fr !important;
                gap: 10px !important;
            }
            .crop-canvas, .live-preview, .pdf-preview, .preview-pair img, .preview-pair video {
                max-width: 100% !important;
                height: auto !important;
                max-height: 280px !important;
            }
            .pdf-scroll {
                max-height: 300px !important;
            }
            .lightbox-frame {
                width: 100% !important;
                padding: 10px !important;
                margin: 10px !important;
            }
        """.trimIndent()

        val script = """
            (function() {
                // 1. Ensure viewport meta tag is optimized for mobile
                var vp = document.querySelector('meta[name="viewport"]');
                if (!vp) {
                    vp = document.createElement('meta');
                    vp.name = 'viewport';
                    document.head.appendChild(vp);
                }
                vp.content = 'width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes';

                // 2. Inject responsive CSS
                var styleId = 'framefit-mobile-injected-style';
                var styleEl = document.getElementById(styleId);
                if (!styleEl) {
                    styleEl = document.createElement('style');
                    styleEl.id = styleId;
                    styleEl.textContent = `${mobileCss.replace("`", "\\`")}`;
                    document.head.appendChild(styleEl);
                }

                // 3. Setup SAF download interception
                if (window.__framefitBridgeInstalled) return;
                window.__framefitBridgeInstalled = true;

                async function sendBlobToNative(blob, filename) {
                    var safeName = filename || 'downloaded_file';
                    var mime = blob.type || 'application/octet-stream';
                    var size = blob.size;

                    // If smaller than 1.5MB, send directly
                    if (size < 1500000) {
                        var reader = new FileReader();
                        reader.onloadend = function() {
                            if (window.AndroidBridge) {
                                window.AndroidBridge.requestSaveFile(reader.result, mime, safeName);
                            }
                        };
                        reader.readAsDataURL(blob);
                        return;
                    }

                    // Otherwise stream in 256KB chunks
                    var sessionId = 'sess_' + Date.now() + '_' + Math.random().toString(16).slice(2);
                    if (window.AndroidBridge) {
                        window.AndroidBridge.startFileSaveSession(sessionId, safeName, mime);
                    }

                    var buffer = await blob.arrayBuffer();
                    var bytes = new Uint8Array(buffer);
                    var chunkSize = 256 * 1024;
                    for (var i = 0; i < bytes.length; i += chunkSize) {
                        var chunk = bytes.subarray(i, Math.min(i + chunkSize, bytes.length));
                        var binary = '';
                        for (var j = 0; j < chunk.length; j++) {
                            binary += String.fromCharCode(chunk[j]);
                        }
                        var base64Chunk = btoa(binary);
                        if (window.AndroidBridge) {
                            window.AndroidBridge.appendFileChunk(sessionId, base64Chunk);
                        }
                    }
                    if (window.AndroidBridge) {
                        window.AndroidBridge.completeFileSaveSession(sessionId, safeName, mime);
                    }
                }

                // Intercept global writeBlobToDestination if present
                if (typeof window.writeBlobToDestination === 'function') {
                    var origWrite = window.writeBlobToDestination;
                    window.writeBlobToDestination = async function(handle, blob, filename, onProgress) {
                        if (window.AndroidBridge) {
                            try {
                                onProgress && onProgress(20);
                                await sendBlobToNative(blob, filename);
                                onProgress && onProgress(100);
                                return 'saved';
                            } catch (e) {
                                console.error('SAF Native save error:', e);
                            }
                        }
                        return origWrite(handle, blob, filename, onProgress);
                    };
                }

                // Intercept <a download> clicks
                document.addEventListener('click', function(e) {
                    var el = e.target.closest('a[download]');
                    if (!el) return;
                    var href = el.href;
                    var filename = el.getAttribute('download') || 'framefit_file';

                    if (href && (href.startsWith('blob:') || href.startsWith('data:'))) {
                        e.preventDefault();
                        e.stopPropagation();
                        fetch(href)
                            .then(function(r) { return r.blob(); })
                            .then(function(blob) {
                                sendBlobToNative(blob, filename);
                            })
                            .catch(function(err) {
                                console.error('Anchor click intercept error:', err);
                            });
                    }
                }, true);
            })();
        """.trimIndent()

        view.evaluateJavascript(script, null)
    }

    /**
     * Opens an Intent Chooser that supports Images, Videos, and PDFs,
     * along with Camera (Photo) and Camcorder (Video) capture options.
     */
    private fun openCustomFileChooser(fileChooserParams: WebChromeClient.FileChooserParams?) {
        checkAndRequestPermissions()

        val intents = mutableListOf<Intent>()

        // 1. Camera Photo capture option
        try {
            val photoFile = FileUtils.createTempImageFile(this)
            val photoURI = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                photoFile
            )
            cameraImageUri = photoURI

            val takePhotoIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            intents.add(takePhotoIntent)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // 2. Camera Video capture option
        try {
            val videoFile = FileUtils.createTempVideoFile(this)
            val videoURI = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                videoFile
            )
            cameraVideoUri = videoURI

            val takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, videoURI)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            intents.add(takeVideoIntent)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // 3. File System Picker: Accepts Images, Videos, and PDFs
        val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "image/*",
                    "video/*",
                    "application/pdf",
                    "application/x-pdf",
                    "application/zip"
                )
            )
        }

        // 4. Unified Chooser
        val chooserIntent = Intent(Intent.ACTION_CHOOSER).apply {
            putExtra(Intent.EXTRA_INTENT, pickIntent)
            putExtra(Intent.EXTRA_TITLE, getString(R.string.file_chooser_title))
            if (intents.isNotEmpty()) {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toTypedArray<Parcelable>())
            }
        }

        fileChooserLauncher.launch(chooserIntent)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun handleUrlDownload(url: String, mimeType: String?) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            return
        }

        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    Uri.parse(url).lastPathSegment ?: "downloaded_file"
                )
            }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, getString(R.string.download_started), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.download_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.primary,
            R.color.accent_coral
        )

        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.webView.reload()
        }

        binding.webView.viewTreeObserver.addOnScrollChangedListener {
            binding.swipeRefreshLayout.isEnabled = (binding.webView.scrollY == 0)
        }
    }

    private fun setupErrorHandling() {
        binding.btnRetry.setOnClickListener {
            binding.layoutError.visibility = View.GONE
            binding.webView.reload()
        }
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // If fullscreen video is showing, exit it first
                if (customView != null) {
                    binding.webView.webChromeClient?.onHideCustomView()
                    return
                }

                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - backPressedTime < BACK_PRESS_INTERVAL) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    } else {
                        backPressedTime = currentTime
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.press_again_to_exit),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        binding.webView.apply {
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}
