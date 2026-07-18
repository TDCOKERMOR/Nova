package com.tdc.aichat

import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Handles image generation, vision analysis, prompt optimization, file picking, and image download.
 * Called from JsBridge facade, not directly exposed to WebView.
 */
class ImageBridgeHandler(
    private val activity: MainActivity,
    private val configManager: ConfigManager,
    private val scope: CoroutineScope
) {
    // ── Image generation ─────────────────────────────

    fun generateImage(prompt: String) {
        scope.launch {
            val config = configManager.loadConfig()
            val result = ApiClient.generateImage(config, prompt)
            result.fold(
                onSuccess = { url ->
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript(
                            "onImageResult('${ChatBridgeHandler.escapeJs(url)}','${ChatBridgeHandler.escapeJs(prompt)}')", null
                        )
                    }
                },
                onFailure = { error ->
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript(
                            "onImageError('${ChatBridgeHandler.escapeJs(error.message ?: "error")}')", null
                        )
                    }
                }
            )
        }
    }

    fun optimizePrompt(prompt: String, style: String, size: String) {
        scope.launch {
            val config = configManager.loadConfig()
            val extra = buildString {
                if (style.isNotEmpty()) append("风格要求：$style。")
                if (size.isNotEmpty()) append("目标尺寸：$size。")
            }
            val fullPrompt = if (extra.isNotEmpty()) "$prompt。$extra" else prompt
            val result = ApiClient.optimizePrompt(config, fullPrompt)
            result.fold(
                onSuccess = { optimized ->
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript(
                            "onOptimizeResult('${ChatBridgeHandler.escapeJs(optimized)}')", null
                        )
                    }
                },
                onFailure = { error ->
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript(
                            "onOptimizeError('${ChatBridgeHandler.escapeJs(error.message ?: "error")}')", null
                        )
                    }
                }
            )
        }
    }

    fun analyzeImage(b64: String, mime: String, request: String) {
        scope.launch {
            val config = configManager.loadConfig()
            val result = ApiClient.analyzeImageAndGeneratePrompt(config, b64, mime, request)
            result.fold(
                onSuccess = { prompt ->
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript(
                            "onImageAnalysisResult('${ChatBridgeHandler.escapeJs(prompt)}')", null
                        )
                    }
                },
                onFailure = { error ->
                    activity.runOnUiThread {
                        activity.webView.evaluateJavascript(
                            "onImageAnalysisError('${ChatBridgeHandler.escapeJs(error.message ?: "error")}')", null
                        )
                    }
                }
            )
        }
    }

    // ── File picker ──────────────────────────────────

    fun onImagePicked(uri: Uri) {
        scope.launch {
            try {
                val (b64, mime, size) = withContext(Dispatchers.IO) {
                    val input: InputStream = activity.contentResolver.openInputStream(uri)
                        ?: return@withContext Triple("", "", 0)
                    val rawBytes = input.readBytes()
                    input.close()

                    // Determine mime type from content resolver or fallback to JPEG
                    val mimeType = activity.contentResolver.getType(uri) ?: "image/jpeg"

                    // Try to decode bitmap bounds without full decode
                    val opts = android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, opts)
                    val imgW = opts.outWidth; val imgH = opts.outHeight

                    // If image is already within limits and is JPEG (already compressed),
                    // use raw bytes directly to avoid quality loss
                    val maxDim = 2048
                    if (imgW > 0 && imgH > 0 && imgW <= maxDim && imgH <= maxDim
                        && mimeType == "image/jpeg" && rawBytes.size <= 2 * 1024 * 1024) {
                        return@withContext Triple(
                            Base64.encodeToString(rawBytes, Base64.NO_WRAP),
                            mimeType,
                            rawBytes.size
                        )
                    }

                    // Otherwise decode, scale, and re-encode
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                        ?: return@withContext Triple("", "", 0)
                    val w = bitmap.width; val h = bitmap.height
                    val scaled = if (w > maxDim || h > maxDim) {
                        val r = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
                        android.graphics.Bitmap.createScaledBitmap(bitmap, (w * r).toInt(), (h * r).toInt(), true)
                    } else bitmap
                    val bos = ByteArrayOutputStream()
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, bos)
                    val data = bos.toByteArray(); bos.close()
                    if (scaled !== bitmap) scaled.recycle()
                    bitmap.recycle()
                    Triple(Base64.encodeToString(data, Base64.NO_WRAP), "image/jpeg", data.size)
                }
                if (b64.isNotEmpty()) {
                    activity.runOnUiThread {
                        val escapedB64 = ChatBridgeHandler.escapeJs(b64)
                        activity.webView.evaluateJavascript(
                            "onImagePicked('$escapedB64','$mime',$size)", null
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // ── Image download ───────────────────────────────

    fun downloadImage(url: String, filename: String) {
        scope.launch {
            try {
                val displayName = filename.ifBlank { "Nova_${System.currentTimeMillis()}.png" }
                val mime = if (displayName.endsWith(".jpg", true) || displayName.endsWith(".jpeg", true))
                    "image/jpeg" else "image/png"

                if (url.startsWith("data:image")) {
                    saveBase64Image(url, displayName, mime)
                } else {
                    downloadUrlImage(url, displayName, mime)
                }
                activity.runOnUiThread {
                    activity.webView.evaluateJavascript(
                        "onDownloadResult('已保存到下载目录: $displayName')", null
                    )
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    activity.webView.evaluateJavascript(
                        "onDownloadResult('保存失败: ${ChatBridgeHandler.escapeJs(e.message ?: "error")}')", null
                    )
                }
            }
        }
    }

    private suspend fun saveBase64Image(dataUrl: String, filename: String, mime: String) =
        withContext(Dispatchers.IO) {
            val comma = dataUrl.indexOf(',')
            val b64 = if (comma > 0) dataUrl.substring(comma + 1) else dataUrl
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            writeImageToDownloads(bytes, filename, mime)
        }

    private suspend fun downloadUrlImage(url: String, filename: String, mime: String) =
        withContext(Dispatchers.IO) {
            val req = okhttp3.Request.Builder().url(url).build()
            val resp = ApiClient.client.newCall(req).execute()
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val bytes = resp.body?.bytes() ?: throw Exception("empty body")
            writeImageToDownloads(bytes, filename, mime)
        }

    private fun writeImageToDownloads(bytes: ByteArray, filename: String, mime: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = activity.contentResolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: throw Exception("无法创建文件")
            activity.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw Exception("无法写入文件")
        } else {
            @Suppress("DEPRECATION")
            val dir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS
            )
            val file = java.io.File(dir, filename)
            file.writeBytes(bytes)
        }
    }
}
