package com.example.mymind.network

import android.content.Context
import android.content.SharedPreferences

/**
 * 存储 AI 配置（API 密钥、BaseURL、模型）
 * 使用 SharedPreferences 持久化
 */
class AiConfig(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ai_config", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var modelName: String
        get() = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    fun isConfigured(): Boolean = apiKey.isNotBlank()

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL = "model"
        const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1/"
        const val DEFAULT_MODEL = "deepseek-chat"
    }
}
