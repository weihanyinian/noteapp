package com.example.mymind.ui.simple

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import com.example.mymind.databinding.FragmentSimplePageBinding
import com.example.mymind.R

/**
 * 简单占位页：
 * - 共享/帮助/关于：暂时显示“开发中”
 * - 设置：提供语言（中/英）切换
 */
class SimplePageFragment : Fragment() {

    private var _binding: FragmentSimplePageBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSimplePageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pageKey = requireArguments().getString(ARG_PAGE_KEY).orEmpty()
        binding.titleText.text = resolveTitle(requireContext(), pageKey)

        if (pageKey == PAGE_SETTINGS) {
            binding.devText.visibility = View.GONE
            binding.settingsContainer.visibility = View.VISIBLE
            setupLanguageSetting()
        } else {
            binding.devText.visibility = View.VISIBLE
            binding.settingsContainer.visibility = View.GONE
        }
    }

    private fun setupLanguageSetting() {
        val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val isEnglish = currentTags.startsWith("en", ignoreCase = true)
        binding.langEn.isChecked = isEnglish
        binding.langZh.isChecked = !isEnglish

        binding.languageGroup.setOnCheckedChangeListener { _, checkedId ->
            val tag = when (checkedId) {
                binding.langEn.id -> "en"
                else -> "zh-CN"
            }
            requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE_TAG, tag)
                .apply()
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            requireActivity().recreate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_PAGE_KEY = "arg_page_key"
        private const val PREFS_NAME = "app_settings"
        private const val KEY_LANGUAGE_TAG = "language_tag"

        const val PAGE_SETTINGS = "settings"
        const val PAGE_HELP = "help"
        const val PAGE_ABOUT = "about"
        const val PAGE_SHARED = "shared"

        fun newInstance(pageKey: String): SimplePageFragment {
            return SimplePageFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PAGE_KEY, pageKey)
                }
            }
        }

        fun resolveTitle(context: Context, pageKey: String): String {
            return when (pageKey) {
                PAGE_SETTINGS -> context.getString(R.string.nav_settings)
                PAGE_HELP -> context.getString(R.string.nav_help)
                PAGE_ABOUT -> context.getString(R.string.nav_about)
                PAGE_SHARED -> context.getString(R.string.nav_shared)
                else -> ""
            }
        }
    }
}

