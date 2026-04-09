package com.example.mymind.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mymind.data.local.AppDatabase
import com.example.mymind.data.repository.MyMindRepository
import java.util.concurrent.TimeUnit

class TrashCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val database = AppDatabase.getInstance(applicationContext)
            val repository = MyMindRepository(
                noteDao = database.noteDao(),
                mindMapDao = database.mindMapDao(),
                mindNodeDao = database.mindNodeDao()
            )
            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(15)
            repository.purgeTrashedDataBefore(cutoffTime)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

