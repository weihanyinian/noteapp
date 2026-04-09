package com.example.mymind.ui

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import com.example.mymind.R
import com.example.mymind.network.AiConfig
import com.example.mymind.network.AiRetrofitClient
import com.example.mymind.network.ChatMessage
import com.example.mymind.network.ChatRequest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * AI 生成内容对话框
 * 使用场景：
 *  - mode = NOTE     → 根据关键词生成笔记正文
 *  - mode = MINDMAP  → 根据主题生成思维导图节点列表
 */
object AiGenerateDialog {

    enum class Mode { NOTE, MINDMAP }

    fun show(
        context: Context,
        scope: CoroutineScope,
        mode: Mode,
        onResult: (String) -> Unit
    ) {
        val aiConfig = AiConfig(context)

        if (!aiConfig.isConfigured()) {
            showConfigDialog(context, aiConfig) {
                show(context, scope, mode, onResult)
            }
            return
        }

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ai_generate, null)
        val promptInput = view.findViewById<TextInputEditText>(R.id.promptInput)
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        val resultHint = view.findViewById<TextView>(R.id.resultHint)

        val hintText = if (mode == Mode.NOTE) "输入关键词，AI 生成笔记内容" else "输入主题，AI 生成思维导图节点"
        promptInput.hint = hintText

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (mode == Mode.NOTE) "AI 生成笔记" else "AI 生成思维导图")
            .setView(view)
            .setPositiveButton("生成", null)
            .setNegativeButton("取消", null)
            .setCancelable(false)
            .create()

        dialog.show()

        // 覆盖 Positive 按钮，避免点击后自动关闭
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val prompt = promptInput.text?.toString()?.trim()
            if (prompt.isNullOrBlank()) {
                promptInput.error = "请输入内容"
                return@setOnClickListener
            }
            progressBar.visibility = View.VISIBLE
            resultHint.text = "AI 生成中，请稍候..."
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false

            scope.launch(Dispatchers.IO) {
                val systemPrompt = if (mode == Mode.NOTE) {
                    "你是一个知识助手。用户提供关键词，你需要生成结构化的中文笔记内容，使用HTML格式（h2/p/ul/li标签），内容丰富、条理清晰，不超过400字。"
                } else {
                    "你是一个思维导图助手。用户提供主题，你需要生成一个树状节点列表，格式为：每行一个节点，用'-'开头表示子节点，最多3层，每层不超过5个节点。只输出节点内容，不要其他说明。"
                }

                try {
                    val service = AiRetrofitClient.build(aiConfig.baseUrl, aiConfig.apiKey)
                    val response = service.chat(
                        ChatRequest(
                            model = aiConfig.modelName,
                            messages = listOf(
                                ChatMessage("system", systemPrompt),
                                ChatMessage("user", prompt)
                            )
                        )
                    )
                    val content = response.choices?.firstOrNull()?.message?.content
                        ?: response.error?.message
                        ?: "AI 没有返回内容"

                    withContext(Dispatchers.Main) {
                        onResult(content)
                        dialog.dismiss()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        resultHint.text = "生成失败：${e.message}"
                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    }
                }
            }
        }
    }

    /** 配置 API 的对话框 */
    private fun showConfigDialog(context: Context, aiConfig: AiConfig, onDone: () -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_ai_config, null)
        val apiKeyInput = view.findViewById<TextInputEditText>(R.id.apiKeyInput)
        val baseUrlInput = view.findViewById<TextInputEditText>(R.id.baseUrlInput)
        val modelInput = view.findViewById<TextInputEditText>(R.id.modelInput)

        apiKeyInput.setText(aiConfig.apiKey)
        baseUrlInput.setText(aiConfig.baseUrl)
        modelInput.setText(aiConfig.modelName)

        MaterialAlertDialogBuilder(context)
            .setTitle("配置 AI 接口")
            .setView(view)
            .setPositiveButton("保存并继续") { _, _ ->
                aiConfig.apiKey = apiKeyInput.text?.toString()?.trim() ?: ""
                val url = baseUrlInput.text?.toString()?.trim()
                if (!url.isNullOrBlank()) aiConfig.baseUrl = url
                val model = modelInput.text?.toString()?.trim()
                if (!model.isNullOrBlank()) aiConfig.modelName = model
                onDone()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
