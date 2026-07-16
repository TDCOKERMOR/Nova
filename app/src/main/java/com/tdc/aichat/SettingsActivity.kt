package com.tdc.aichat

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.tdc.aichat.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var configManager: ConfigManager
    private var hasChanges = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configManager = ConfigManager(this)
        loadCurrentConfig()
        setupListeners()
    }

    private fun loadCurrentConfig() {
        val c = configManager.loadConfig()
        binding.etChatApiUrl.setText(c.chatApiUrl)
        binding.etChatApiKey.setText(c.chatApiKey)
        binding.etChatModel.setText(c.chatModel)
        binding.etVisionApiUrl.setText(c.visionApiUrl)
        binding.etVisionApiKey.setText(c.visionApiKey)
        binding.etVisionModel.setText(c.visionModel)
        binding.etImageApiUrl.setText(c.imageApiUrl)
        binding.etImageApiKey.setText(c.imageApiKey)
        binding.etImageModel.setText(c.imageModel)
        hasChanges = false
    }

    private fun setupListeners() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { hasChanges = true }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        }

        binding.etChatApiUrl.addTextChangedListener(watcher)
        binding.etChatApiKey.addTextChangedListener(watcher)
        binding.etChatModel.addTextChangedListener(watcher)
        binding.etVisionApiUrl.addTextChangedListener(watcher)
        binding.etVisionApiKey.addTextChangedListener(watcher)
        binding.etVisionModel.addTextChangedListener(watcher)
        binding.etImageApiUrl.addTextChangedListener(watcher)
        binding.etImageApiKey.addTextChangedListener(watcher)
        binding.etImageModel.addTextChangedListener(watcher)

        binding.btnSave.setOnClickListener { doSave() }
        binding.btnBack.setOnClickListener { onBackPressed() }
    }

    private fun doSave() {
        val chatUrl = binding.etChatApiUrl.text.toString().trim()
        val chatKey = binding.etChatApiKey.text.toString().trim()
        val chatModel = binding.etChatModel.text.toString().trim()

        if (chatUrl.isEmpty() || chatKey.isEmpty() || chatModel.isEmpty()) {
            Toast.makeText(this, "对话 API 三字段为必填", Toast.LENGTH_SHORT).show()
            return
        }

        val config = AppConfig(
            chatApiUrl = chatUrl,
            chatApiKey = chatKey,
            chatModel = chatModel,
            visionApiUrl = binding.etVisionApiUrl.text.toString().trim(),
            visionApiKey = binding.etVisionApiKey.text.toString().trim(),
            visionModel = binding.etVisionModel.text.toString().trim(),
            imageApiUrl = binding.etImageApiUrl.text.toString().trim(),
            imageApiKey = binding.etImageApiKey.text.toString().trim(),
            imageModel = binding.etImageModel.text.toString().trim()
        )
        configManager.saveConfig(config)
        hasChanges = false
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onBackPressed() {
        if (hasChanges) {
            AlertDialog.Builder(this)
                .setTitle("未保存的更改")
                .setMessage("你有未保存的配置修改，是否保存？")
                .setPositiveButton("保存") { _, _ -> doSave() }
                .setNegativeButton("放弃") { _, _ -> finish() }
                .setNeutralButton("取消", null)
                .show()
        } else {
            finish()
        }
    }
}
