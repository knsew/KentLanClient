package com.kentji.lanclient.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kentji.lanclient.MainActivity

object Notifier {
    const val CH_SIGNAL = "signal"
    const val CH_PAPER = "paper"
    const val CH_AI = "ai"
    const val CH_WATCH = "watch"
    const val CH_SYSTEM = "system"
    const val CH_SERVICE = "service"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        fun channel(id: String, name: String, importance: Int, desc: String) {
            val ch = NotificationChannel(id, name, importance)
            ch.description = desc
            mgr.createNotificationChannel(ch)
        }
        channel(CH_SIGNAL, "S1 信号", NotificationManager.IMPORTANCE_HIGH, "已触发的信号")
        channel(CH_PAPER, "虚拟成交", NotificationManager.IMPORTANCE_DEFAULT, "虚拟单成交")
        channel(CH_AI, "AI 偏离", NotificationManager.IMPORTANCE_HIGH, "与计划偏离的 AI 建议")
        channel(CH_WATCH, "观察池", NotificationManager.IMPORTANCE_DEFAULT, "观察池信号")
        channel(CH_SYSTEM, "系统", NotificationManager.IMPORTANCE_LOW, "离线与服务状态")
        channel(CH_SERVICE, "监控中", NotificationManager.IMPORTANCE_LOW, "本地持续监控")
    }

    private fun contentIntent(context: Context, route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("route", route)
        }
        return PendingIntent.getActivity(
            context, route.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun push(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        text: String,
        route: String,
        silentUpdate: Boolean = false,
    ) {
        createChannels(context)
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent(context, route))
            .setAutoCancel(true)
            .setPriority(
                when (channel) {
                    CH_SIGNAL, CH_AI -> NotificationCompat.PRIORITY_HIGH
                    CH_SYSTEM, CH_SERVICE -> NotificationCompat.PRIORITY_LOW
                    else -> NotificationCompat.PRIORITY_DEFAULT
                }
            )
        if (silentUpdate) {
            builder.setOnlyAlertOnce(true).setSilent(true)
        }
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(id, builder.build())
    }

    /** The foreground service's ongoing notification (with a Stop action). */
    fun serviceNotification(context: Context, text: String): android.app.Notification {
        createChannels(context)
        val stop = Intent(context, SignalService::class.java).apply { action = SignalService.ACTION_STOP }
        val stopPi = PendingIntent.getService(
            context, 1, stop,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CH_SERVICE)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("LanClient 监控中")
            .setContentText(text)
            .setOngoing(true)
            .addAction(0, "停止", stopPi)
            .setContentIntent(contentIntent(context, "signals"))
            .build()
    }
}
