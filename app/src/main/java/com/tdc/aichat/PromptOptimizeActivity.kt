package com.tdc.aichat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tdc.aichat.databinding.ActivityPromptOptimizeBinding
import kotlinx.coroutines.launch

class PromptOptimizeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPromptOptimizeBinding
    private lateinit var configManager: ConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPromptOptimizeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configManager = ConfigManager(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnOptimize.setOnClickListener { optimize() }
        binding.btnCopy.setOnClickListener {
            val text = binding.tvResult.text.toString()
            if (text.isNotEmpty()) {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("prompt", text))
                Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun optimize() {
        val raw = binding.etRawPrompt.text.toString().trim()
        if (raw.isEmpty()) {
            Toast.makeText(this, "请输入需要优化的提示词", Toast.LENGTH_SHORT).show()
            return
        }
        if (!configManager.hasChatConfig()) {
            Toast.makeText(this, "请先在设置中配置对话 API", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnOptimize.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.tvResult.visibility = View.GONE
        binding.btnCopy.visibility = View.GONE

        lifecycleScope.launch {
            val config = configManager.loadConfig()
            val result = ApiClient.optimizePrompt(config, raw)
            runOnUiThread {
                binding.btnOptimize.isEnabled = true
                binding.progressBar.visibility = View.GONE

                result.fold(
                    onSuccess = { optimized ->
                        binding.tvResult.text = optimized
                        binding.tvResult.visibility = View.VISIBLE
                        binding.btnCopy.visibility = View.VISIBLE
                    },
                    onFailure = { error ->
                        Toast.makeText(this@PromptOptimizeActivity,
                            "优化失败: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }
}
