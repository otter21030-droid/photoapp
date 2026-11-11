package com.framefit.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.DownloadListener
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import java.io.IOException

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TARGET_URL = "https://otter21030-droid.github.io/newapp/"
        private const val BACK_PRESS_INTERVAL = 2000L
    }

    private lateinit var binding: ActivityMainBinding
    private var backPressedTime = 0L

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    private lateinit var fileChooserLauncher: ActivityResultLauncher<Intent>
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

        // Load target website
        binding.webView.loadUrl(TARGET_URL)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupFullScreen()
        }
    }

    /**
     * Configures full-screen immersive mode (hides status & navigation bars).
     */
    private fun setupFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupLaunchers() {
        // File Chooser Activity Result Launcher
        fileChooserLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val callback = fileUploadCallback ?: return@registerForActivityResult
            var results: Array<Uri>? = null

            if (result.resultCode == RESULT_OK) {
                val data = result.data
                if (data != null && (data.data != null || data.clipData != null)) {
                    // Result from Gallery / File picker
                    val clipData = data.clipData
                    results = if (clipData != null && clipData.itemCount > 0) {
                        Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                    } else if (data.data != null) {
                        arrayOf(data.data!!)
                    } else {
                        null
                    }
                } else if (cameraImageUri != null) {
                    // Result from Camera
                    results = arrayOf(cameraImageUri!!)
                }
            }

            callback.onReceiveValue(results)
            fileUploadCallback = null
            cameraImageUri = null
        }

        // Runtime Permission Launcher
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            // Permissions handled if needed
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val webView = binding.webView
        val settings = webView.settings

        // Enable essential web capabilities
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        // Viewport & Scaling
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // Cache & Performance
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        // Custom User-Agent to ensure modern mobile compatibility
        val defaultUserAgent = settings.userAgentString
        settings.userAgentString = "$defaultUserAgent FrameFitApp/1.0"

        // JavaScript Interface for Blob & Base64 downloads
        val bridge = WebAppInterface(
            context = this,
            onDownloadCompleted = { filename ->
                Toast.makeText(
                    this,
                    getString(R.string.download_completed),
                    Toast.LENGTH_LONG
                ).show()
            },
            onDownloadFailed = { error ->
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            }
        )
        webView.addJavascriptInterface(bridge, "AndroidBridge")

        // Standard Download Listener
        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            handleUrlDownload(url, mimetype)
        })

        // WebChromeClient (File Chooser, Progress, Fullscreen view)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress < 100) {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = newProgress
                } else {
                    binding.progressBar.visibility = View.GONE
                    // Hide splash on initial load finish
                    if (binding.layoutSplash.visibility == View.VISIBLE) {
                        binding.layoutSplash.animate()
                            .alpha(0f)
                            .setDuration(300)
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
                    val requestedResources = it.resources
                    it.grant(requestedResources)
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                return super.onConsoleMessage(consoleMessage)
            }
        }

        // WebViewClient (URL navigation, offline handling, blob download injection)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: ""

                // Handle non-web schemes (intent, tel, mailto, etc.)
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
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.swipeRefreshLayout.isRefreshing = false
                injectBlobDownloadHook(view)
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
     * Opens an Intent Chooser allowing the user to pick an existing image or capture a new one with the camera.
     */
    private fun openCustomFileChooser(fileChooserParams: WebChromeClient.FileChooserParams?) {
        checkAndRequestPermissions()

        val intents = mutableListOf<Intent>()

        // 1. Camera Intent
        try {
            val photoFile = FileUtils.createTempImageFile(this)
            val photoURI = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                photoFile
            )
            cameraImageUri = photoURI

            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            intents.add(takePictureIntent)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // 2. Gallery / File Intent
        val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }

        // 3. Chooser Intent combining Camera + Gallery
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    /**
     * Injects JavaScript to capture blob: and data: download triggers from <a> tags.
     */
    private fun injectBlobDownloadHook(view: WebView?) {
        val script = """
            (function() {
                if (window.__framefitDownloadHooked) return;
                window.__framefitDownloadHooked = true;

                document.addEventListener('click', function(e) {
                    var el = e.target.closest('a[download]');
                    if (!el) return;
                    var href = el.href;
                    var filename = el.getAttribute('download') || 'framefit_image.png';

                    if (href && href.startsWith('blob:')) {
                        e.preventDefault();
                        fetch(href)
                            .then(function(res) { return res.blob(); })
                            .then(function(blob) {
                                var reader = new FileReader();
                                reader.onloadend = function() {
                                    if (window.AndroidBridge) {
                                        window.AndroidBridge.downloadBase64(reader.result, blob.type, filename);
                                    }
                                };
                                reader.readAsDataURL(blob);
                            })
                            .catch(function(err) {
                                console.error('Blob download hook error:', err);
                            });
                    } else if (href && href.startsWith('data:')) {
                        e.preventDefault();
                        var mime = href.substring(5, href.indexOf(';'));
                        if (window.AndroidBridge) {
                            window.AndroidBridge.downloadBase64(href, mime, filename);
                        }
                    }
                }, true);
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    private fun handleUrlDownload(url: String, mimeType: String?) {
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            // Blob/Data URLs are captured via JavascriptInterface
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

        // Only allow swipe refresh when the web view is scrolled to top
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

