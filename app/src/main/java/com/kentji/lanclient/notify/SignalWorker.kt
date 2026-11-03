package com.kentji.lanclient.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** 15-minute fallback check (WorkManager minimum interval). */
class SignalWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            SignalChecker.check(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
