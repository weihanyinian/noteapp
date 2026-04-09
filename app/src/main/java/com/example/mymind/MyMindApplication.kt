package com.example.mymind

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
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

/**
 * 应用入口：
 * - 初始化数据库与仓库
 * - 首次启动填充示例数据
 * - 定时清理回收站
 * - 统一应用语言（默认中文，设置页可切换）
 */
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
        applyAppLanguage()
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

    private fun applyAppLanguage() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val tag = prefs.getString(KEY_LANGUAGE_TAG, null) ?: DEFAULT_LANGUAGE_TAG.also {
            prefs.edit().putString(KEY_LANGUAGE_TAG, it).apply()
        }
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_LANGUAGE_TAG = "language_tag"
        private const val DEFAULT_LANGUAGE_TAG = "zh-CN"
    }
}
