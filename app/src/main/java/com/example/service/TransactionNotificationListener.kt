package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.DetectedNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class TransactionNotificationListener : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        private const val TAG = "TxNotificationListener"
        private const val CHANNEL_ID = "transaction_alerts"
        private const val CHANNEL_NAME = "Transaction Quick Alerts"

        // Regular expression to find transaction amounts in multiple formats: Rs. 100, Rs. 100.50, INR 500, Rs1500, ₹ 250, etc.
        private val AMOUNT_PATTERN = Pattern.compile("(?i)(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{1,2})?)")
        
        // Key phrase match to verify if it is indeed a transaction expense notification
        private val ACCORD_PATTERN = Pattern.compile("(?i)(debited|paid|spent|sent|transfer|withdrawn|remitted|payment|spent of)")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName ?: ""
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

        val fullText = "$title $text $subText"

        Log.d(TAG, "Notification received from package: $packageName")
        Log.d(TAG, "Content: $fullText")

        // Filter: check if this is a transaction
        if (ACCORD_PATTERN.matcher(fullText).find()) {
            val amountMatcher = AMOUNT_PATTERN.matcher(fullText)
            if (amountMatcher.find()) {
                val amountStr = amountMatcher.group(1)?.replace(",", "") ?: return
                val amount = amountStr.toDoubleOrNull() ?: return

                Log.d(TAG, "SUCCESS! Detected transaction amount: $amount from notification!")

                // Clean package name to display nicely
                val simpleAppName = when {
                    packageName.contains("paytm", true) -> "Paytm"
                    packageName.contains("phonepe", true) -> "PhonePe"
                    packageName.contains("gpay", true) || packageName.contains("apps.mapper", true) -> "Google Pay"
                    packageName.contains("bhim", true) -> "BHIM UPI"
                    packageName.contains("android.apps.messaging", true) -> "SMS Messaging"
                    else -> packageName.substringAfterLast(".").replaceFirstChar { it.uppercase() }
                }

                // Insert into Room DB
                val detectedNotification = DetectedNotification(
                    appName = simpleAppName,
                    title = title,
                    text = text,
                    amount = amount,
                    sender = if (title.isNotEmpty()) title else simpleAppName,
                    timestamp = System.currentTimeMillis(),
                    isLogged = false
                )

                serviceScope.launch {
                    try {
                        val database = AppDatabase.getDatabase(this@TransactionNotificationListener)
                        database.expenseDao().insertDetectedNotification(detectedNotification)
                    } catch (e: Exception) {
                        Log.e(TAG, "Database insert failed", e)
                    }
                }

                // Push custom System Notification alerting the user with an Action to Log the transaction!
                triggerQuickAlert(simpleAppName, amount, text)
            }
        }
    }

    private fun triggerQuickAlert(appName: String, amount: Double, text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts to instantly categorize and log payments received via UPI or bank SMS notifications."
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Tap opens MainActivity and sends arguments so it triggers the Dialog instantly
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("LAUNCH_ACTION", "QUICK_LOG")
            putExtra("DETECTED_AMOUNT", amount)
            putExtra("DETECTED_APP", appName)
            putExtra("DETECTED_MERCHANT", text.take(30))
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat) // standard alert drawable
            .setContentTitle("Spent ₹$amount via $appName?")
            .setContentText("Tap to quickly log this expense and select its category!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        notificationManager.notify(1001, builder.build())
    }
}
