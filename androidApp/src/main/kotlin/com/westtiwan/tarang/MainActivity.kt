package com.westtiwan.tarang

import android.Manifest
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import com.westtiwan.tarang.ui.Navigator

class MainActivity : ComponentActivity() {
    private val navigator = Navigator()
    private var pendingPick: CompletableDeferred<Uri?>? = null
    private val imagePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> pendingPick?.complete(uri) }

    private suspend fun pickImage(): ByteArray? {
        val d = CompletableDeferred<Uri?>()
        pendingPick = d
        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        val uri = d.await() ?: return null
        return withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { it.readBytes() } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = (application as TarangApp).app
        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleIntent(intent)
        setContent {
            val platformUi = remember { AndroidPlatformUi(app) { pickImage() } }
            BackHandler(enabled = navigator.canGoBack) { navigator.back() }
            App(app, navigator, platformUi)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("open_player", false) == true) navigator.nowPlayingOpen = true
    }
}
