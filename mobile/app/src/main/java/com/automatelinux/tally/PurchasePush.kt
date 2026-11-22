package com.automatelinux.tally

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.automatelinux.tally.data.TallyApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/**
 * How a press of "add my last purchase" ended, as a notification — so the app can be
 * closed while the server waits for the purchase to reach the card company.
 *
 * Delivered by UnifiedPush through the suite's own ntfy server
 * (/opt/automateLinux/services/ntfy/README.md), never Firebase. The server sends the
 * message itself as JSON (tally lib/push.ts); the tally screen shows the same outcome
 * from the server the next time it opens, so a missed push costs only the notification.
 */
object PurchasePush {
    private const val TAG = "PurchasePush"
    private const val INSTANCE = "last-purchase"
    // A channel's sound is fixed once created, so a new sound means a new channel id.
    // The first one used the phone's default sound, which is also WhatsApp's.
    const val CHANNEL = "last-purchase-chime"
    private const val OLD_CHANNELS = "last-purchase"
    const val EXTRA_TALLY = "tallyId"

    /** Safe on every start: keeps the registration (and the server's copy of the endpoint) current. */
    fun register(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(OLD_CHANNELS)
        // Its own sound, so a purchase landing is never mistaken for a WhatsApp message.
        val sound = Uri.parse("android.resource://${context.packageName}/${R.raw.purchase_chime}")
        val channel = NotificationChannel(CHANNEL, "Card purchases", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A purchase you asked for was added — or did not arrive"
            setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build())
        }
        manager.createNotificationChannel(channel)
        UnifiedPush.tryUseCurrentOrDefaultDistributor(context) { ok ->
            if (ok) UnifiedPush.register(context, INSTANCE, "Tally card purchases", null)
            else Log.w(TAG, "No UnifiedPush distributor (ntfy) — outcomes show only inside the app")
        }
    }

    fun show(context: Context, raw: ByteArray) {
        val m = runCatching { JSONObject(raw.decodeToString()) }.getOrNull() ?: return
        val tallyId = m.optString("tallyId")
        val title = m.optString("title")
        val body = m.optString("body")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TALLY, tallyId)
        }
        val id = tallyId.hashCode()
        val pending = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}

class PurchasePushService : PushService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        // Tell the server where to push. A failure is retried on the next app start.
        scope.launch {
            val e = TallyApi(BuildConfig.API_BASE_URL, BuildConfig.API_TOKEN).registerPush(endpoint.url)
            if (e == null) Log.i("PurchasePush", "Registered ${endpoint.url}")
            else Log.w("PurchasePush", "Could not register the push endpoint: $e")
        }
    }

    override fun onMessage(message: PushMessage, instance: String) = PurchasePush.show(applicationContext, message.content)

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.w("PurchasePush", "UnifiedPush registration failed: $reason")
    }

    override fun onUnregistered(instance: String) {
        Log.i("PurchasePush", "UnifiedPush unregistered")
    }
}
