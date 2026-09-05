package com.automatelinux.tally

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.russhwolf.settings.SharedPreferencesSettings

/**
 * Thin Android host: all UI lives in the shared commonMain composables. Two things are
 * genuinely platform work and are done here — handing the shared code a key-value store,
 * and wiring the system back gesture to the shared back stack.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val settings = SharedPreferencesSettings(getSharedPreferences("tally", Context.MODE_PRIVATE))
        val nav = Navigator()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Only the home screen lets back leave the app.
                if (!nav.back()) {
                    remove()
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        setContent { App(settings, nav) }
    }
}
