package com.tdc.aichat

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.tdc.aichat.databinding.ItemMessageBinding

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            val context = binding.root.context
            val isUser = message.role == "user"

            // Alignment: user right, AI left
            val bubbleParams = binding.bubble.layoutParams as? LinearLayout.LayoutParams
                ?: LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            if (isUser) {
                bubbleParams.gravity = Gravity.END
                (binding.rootLayout as LinearLayout).gravity = Gravity.END
            } else {
                bubbleParams.gravity = Gravity.START
                (binding.rootLayout as LinearLayout).gravity = Gravity.START
            }
            binding.bubble.layoutParams = bubbleParams

            // Bubble background
            val bg = binding.bubble.background.mutate() as? GradientDrawable ?: GradientDrawable()
            bg.cornerRadius = 18f
            if (isUser) {
                bg.setColor(ContextCompat.getColor(context, R.color.user_bubble))
                binding.tvContent.setTextColor(ContextCompat.getColor(context, R.color.user_text))
            } else {
                bg.setColor(ContextCompat.getColor(context, R.color.ai_bubble))
                binding.tvContent.setTextColor(ContextCompat.getColor(context, R.color.ai_text))
            }

            // Content
            binding.tvContent.text = message.content
            binding.tvContent.visibility =
                if (message.content.isNotEmpty()) android.view.View.VISIBLE
                else android.view.View.GONE

            // Image
            if (message.isImage) {
                binding.ivImage.load(message.imageUrl)
                binding.ivImage.visibility = android.view.View.VISIBLE
                binding.tvPrompt.text = "提示词: ${message.imagePrompt}"
                binding.tvPrompt.visibility =
                    if (message.imagePrompt.isNotEmpty()) android.view.View.VISIBLE
                    else android.view.View.GONE
            } else {
                binding.ivImage.setImageDrawable(null)
                binding.ivImage.visibility = android.view.View.GONE
                binding.tvPrompt.visibility = android.view.View.GONE
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(old: ChatMessage, new: ChatMessage): Boolean = old === new
        override fun areContentsTheSame(old: ChatMessage, new: ChatMessage): Boolean =
            old.content == new.content && old.imageUrl == new.imageUrl
    }
}
