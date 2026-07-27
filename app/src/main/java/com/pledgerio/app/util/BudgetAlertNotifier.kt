package com.pledgerio.app.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pledgerio.app.MainActivity
import com.pledgerio.app.R
import com.pledgerio.app.domain.model.Budget
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun notifyOverBudget(
        budgets: List<Budget>,
        yearMonth: YearMonth,
        thresholdPercent: Int,
    ) {
        if (budgets.isEmpty()) return

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BudgetAlertLogic.CHANNEL_ID,
                context.getString(R.string.budget_alert_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.budget_alert_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val threshold = BudgetAlertLogic.normalizeThresholdPercent(thresholdPercent)
        val budgetNames = budgets.joinToString(", ") { it.name }
        val contentText = context.resources.getQuantityString(
            R.plurals.budget_alert_notification_body,
            budgets.size,
            budgets.size,
            threshold,
            budgetNames,
        )

        val deepLink = Uri.parse(BudgetAlertLogic.budgetsDeepLinkUri(yearMonth))
        val contentIntent = Intent(Intent.ACTION_VIEW, deepLink, context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, BudgetAlertLogic.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.budget_alert_notification_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(BudgetAlertLogic.NOTIFICATION_ID, notification)
    }
}
