package com.automatelinux.tally

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.automatelinux.tally.data.TallyApi

/**
 * Thin Android host: all UI lives in the shared commonMain composables. Two things
 * are genuinely platform work and are done here — pointing the shared code at the
 * backend, and wiring the system back gesture to the shared back stack.
 *
 * The base URL and token are baked in at build time from the gitignored
 * mobile/.env, so the APK carries no checked-in secret and a build made without
 * one says so on screen rather than failing quietly.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val api = TallyApi(BuildConfig.API_BASE_URL, BuildConfig.API_TOKEN)
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

        setContent { App(api, nav) }
    }
}
