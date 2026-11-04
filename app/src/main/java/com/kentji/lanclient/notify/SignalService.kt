package com.kentji.lanclient.notify

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kentji.lanclient.data.Store
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Foreground polling service, trading window only. Outside the window it stops. */
class SignalService : Service() {
    companion object {
        const val ACTION_STOP = "com.kentji.lanclient.STOP"
        const val NOTIF_ID = 8001

        fun scheduleFallback(context: android.content.Context) {
            val req = PeriodicWorkRequestBuilder<SignalWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "signal_fallback", ExistingPeriodicWorkPolicy.KEEP, req
            )
        }
    }

    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Store(this).setServiceEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, Notifier.serviceNotification(this, "正在监控本地服务器"))
        job?.cancel()
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                if (!inTradingWindow()) {
                    stopSelf()
                    break
                }
                try {
                    SignalChecker.check(applicationContext)
                } catch (_: Exception) {
                }
                delay(60_000)
            }
        }
        return START_STICKY
    }

    private fun inTradingWindow(): Boolean {
        val cal = Calendar.getInstance()
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        // 09:15-15:30 local time; the server's /api/data_status trading flag is
        // checked by the worker path, this window is a cheap local guard.
        return minutes in (9 * 60 + 15)..(15 * 60 + 30)
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
