package com.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    setContent {
      MyApplicationTheme {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020617)) // Deep slate-950
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
        ) {
          AudioTagEditorWebView()
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AudioTagEditorWebView() {
  val context = LocalContext.current
  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val callback = filePathCallback
    filePathCallback = null
    if (callback != null) {
      if (result.resultCode == Activity.RESULT_OK && result.data != null) {
        val data = result.data
        val clipData = data?.clipData
        val uris = mutableListOf<Uri>()

        if (clipData != null) {
          for (i in 0 until clipData.itemCount) {
            uris.add(clipData.getItemAt(i).uri)
          }
        } else if (data?.data != null) {
          uris.add(data.data!!)
        }

        callback.onReceiveValue(if (uris.isNotEmpty()) uris.toTypedArray() else null)
      } else {
        callback.onReceiveValue(null)
      }
    }
  }

  BackHandler(enabled = webViewInstance?.canGoBack() == true) {
    webViewInstance?.goBack()
  }

  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      WebView(ctx).apply {
        webViewInstance = this
        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          allowFileAccess = true
          databaseEnabled = true
          cacheMode = WebSettings.LOAD_DEFAULT
          useWideViewPort = true
          loadWithOverviewMode = true
        }

        setBackgroundColor(android.graphics.Color.parseColor("#020617"))

        webViewClient = object : WebViewClient() {}

        webChromeClient = object : WebChromeClient() {
          override fun onShowFileChooser(
            webView: WebView?,
            filePathCallbackParam: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
          ): Boolean {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = filePathCallbackParam

            val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
              type = "*/*"
              putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }

            try {
              filePickerLauncher.launch(intent)
            } catch (e: Exception) {
              filePathCallbackParam?.onReceiveValue(null)
              filePathCallback = null
              return false
            }
            return true
          }
        }

        // Add a download listener for audio files if triggered directly
        setDownloadListener { url, _, contentDisposition, mimetype, _ ->
          try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
              setDataAndType(Uri.parse(url), mimetype)
              flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            ctx.startActivity(intent)
          } catch (e: Exception) {
            Toast.makeText(ctx, "Download initiated: $contentDisposition", Toast.LENGTH_SHORT).show()
          }
        }

        loadUrl("file:///android_asset/index.html")
      }
    },
    update = {
      webViewInstance = it
    }
  )

  DisposableEffect(Unit) {
    onDispose {
      webViewInstance?.destroy()
    }
  }
}
