package com.openscad.viewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.openscad.viewer.ui.App
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hosts the Compose UI and acts as the entry point for content shared from other
 * apps (for example an assistant sharing a `.scad` file or its text).
 *
 * `launchMode="singleTop"` means a share received while the app is already open
 * arrives through [onNewIntent] instead of spawning a second instance.
 */
class MainActivity : ComponentActivity() {

    private val sharedDocument = MutableStateFlow<SharedDocument?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App(sharedDocument = sharedDocument)
        }
        handleIncoming(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val fallbackName = intent.getStringExtra(Intent.EXTRA_TITLE)
                    ?: uri?.lastPathSegment
                    ?: "shared.scad"

                // Prefer the attached file; fall back to plain shared text, which
                // is what a chat/assistant app sends when it shares source.
                val text = uri?.let(::readText) ?: intent.getStringExtra(Intent.EXTRA_TEXT)

                if (!text.isNullOrBlank()) {
                    sharedDocument.value = SharedDocument(text, cleanName(fallbackName))
                }
            }

            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                val text = readText(uri) ?: return
                sharedDocument.value =
                    SharedDocument(text, cleanName(uri.lastPathSegment ?: "shared.scad"))
            }
        }
    }

    private fun readText(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
    }.getOrNull()

    private fun cleanName(name: String): String =
        name.substringAfterLast('/').substringBefore('?').ifBlank { "shared.scad" }
}
