package com.automatelinux.tally

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.automatelinux.tally.data.TallyApi
import com.russhwolf.settings.SharedPreferencesSettings

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
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private lateinit var nav: Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // "Add my last purchase" tells you how it ended with a notification.
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)

        val api = TallyApi(BuildConfig.API_BASE_URL, BuildConfig.API_TOKEN)
        nav = Navigator(SharedPreferencesSettings(getSharedPreferences("navigation", MODE_PRIVATE)))
        openTallyFrom(intent)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openTallyFrom(intent)
    }

    /** A tapped purchase notification opens the tally it is about. */
    private fun openTallyFrom(intent: Intent?) {
        val id = intent?.getStringExtra(PurchasePush.EXTRA_TALLY)?.takeIf { it.isNotBlank() } ?: return
        intent.removeExtra(PurchasePush.EXTRA_TALLY)
        val top = nav.current
        if (top is Route.Detail && top.tallyId == id) return
        nav.popToHome()
        nav.go(Route.Detail(id))
    }
}
