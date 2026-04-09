package com.example.mymind

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mymind.data.cloud.NoOpNoteCloudDataSource
import com.example.mymind.data.local.AppDatabase
import com.example.mymind.data.repository.MyMindRepository
import com.example.mymind.data.repository.NoteRepository
import com.example.mymind.worker.TrashCleanupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MyMindApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val repository: MyMindRepository by lazy {
        MyMindRepository(
            noteDao = database.noteDao(),
            mindMapDao = database.mindMapDao(),
            mindNodeDao = database.mindNodeDao()
        )
    }

    val noteRepository: NoteRepository by lazy {
        NoteRepository(
            noteDao = database.noteDao(),
            cloudDataSource = NoOpNoteCloudDataSource()
        )
    }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            repository.seedIfEmpty()
        }

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val cleanupWorkRequest = PeriodicWorkRequestBuilder<TrashCleanupWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "trash_cleanup",
            ExistingPeriodicWorkPolicy.UPDATE,
            cleanupWorkRequest
        )
    }
}
