package com.tdc.aichat

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.tdc.aichat.databinding.DialogPromptOptimizeBinding
import kotlinx.coroutines.launch

class PromptOptimizeDialog(
    private val configManager: ConfigManager,
    private val onConfirm: (String) -> Unit
) : DialogFragment() {

    private var _binding: DialogPromptOptimizeBinding? = null
    private val binding get() = requireNotNull(_binding) { "Binding not initialized" }
    private var lastOptimized = ""

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogPromptOptimizeBinding.inflate(layoutInflater)
        setupButtons()
        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create().apply {
                window?.setBackgroundDrawableResource(android.R.color.transparent)
            }
    }

    private fun setupButtons() {
        binding.btnOptimize.setOnClickListener { doOptimize() }
        binding.btnConfirm.setOnClickListener { onConfirm(lastOptimized); dismiss() }
        binding.btnRegenerate.setOnClickListener { doOptimize() }
        binding.btnCancel.setOnClickListener { dismiss() }
    }

    private fun doOptimize() {
        val raw = binding.etRawPrompt.text.toString().trim()
        if (raw.isEmpty()) {
            Toast.makeText(requireContext(), "请输入描述", Toast.LENGTH_SHORT).show(); return
        }
        if (!configManager.hasChatConfig()) {
            Toast.makeText(requireContext(), "请先配置对话 API", Toast.LENGTH_SHORT).show(); return
        }

        val style = binding.etStyle.text.toString().trim()
        val size = binding.etSize.text.toString().trim()
        val extra = buildString {
            if (style.isNotEmpty()) append("风格要求：$style。")
            if (size.isNotEmpty()) append("目标尺寸：$size。")
        }
        val fullPrompt = if (extra.isNotEmpty()) "$raw。$extra" else raw

        binding.btnOptimize.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.cardResult.visibility = View.GONE
        binding.actionButtons.visibility = View.GONE

        lifecycleScope.launch {
            val config = configManager.loadConfig()
            val result = ApiClient.optimizePrompt(config, fullPrompt)
            if (!isAdded) return@launch
            binding.btnOptimize.isEnabled = true
            binding.progressBar.visibility = View.GONE
            result.fold(
                onSuccess = { optimized ->
                    lastOptimized = optimized
                    binding.tvResult.text = optimized
                    binding.cardResult.visibility = View.VISIBLE
                    binding.actionButtons.visibility = View.VISIBLE
                },
                onFailure = { error ->
                    Toast.makeText(requireContext(), "优化失败: ${error.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
