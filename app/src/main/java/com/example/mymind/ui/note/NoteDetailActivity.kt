package com.example.mymind.ui.note

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mymind.R
import com.example.mymind.databinding.ActivityNoteDetailBinding
import kotlinx.coroutines.launch

class NoteDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNoteDetailBinding
    private val viewModel: NoteEditorViewModel by viewModels()

    private var noteId: Long = INVALID_NOTE_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        noteId = intent.getLongExtra(EXTRA_NOTE_ID, INVALID_NOTE_ID)
        if (noteId == INVALID_NOTE_ID) {
            finish()
            return
        }

        setupToolbar()
        setupWebView(binding.webView)
        observeNote()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener { finish() }
        binding.topAppBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_edit -> {
                    startActivity(NoteEditorActivity.createIntent(this, noteId))
                    true
                }
                R.id.action_share -> {
                    share()
                    true
                }
                R.id.action_delete -> {
                    lifecycleScope.launch {
                        viewModel.trash(noteId)
                        finish()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupWebView(webView: WebView) {
        webView.settings.javaScriptEnabled = false
        webView.settings.domStorageEnabled = false
    }

    private fun observeNote() {
        viewModel.observeNote(noteId).observe(this) { note ->
            if (note == null) return@observe
            binding.topAppBar.title = note.title
            val html = """
                <html>
                <head>
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <style>
                    body { font-family: sans-serif; padding: 16px; line-height: 1.6; }
                    img { max-width: 100%; height: auto; }
                  </style>
                </head>
                <body>${note.content}</body>
                </html>
            """.trimIndent()
            binding.webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        }
    }

    private fun share() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "MyMind 笔记：$noteId")
        }
        startActivity(Intent.createChooser(intent, "分享笔记"))
    }

    companion object {
        private const val EXTRA_NOTE_ID = "extra_note_id"
        private const val INVALID_NOTE_ID = -1L

        fun createIntent(context: Context, noteId: Long): Intent {
            return Intent(context, NoteDetailActivity::class.java).apply {
                putExtra(EXTRA_NOTE_ID, noteId)
            }
        }
    }
}

