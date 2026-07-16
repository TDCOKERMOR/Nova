package com.tdc.aichat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.tdc.aichat.databinding.ItemConversationBinding

class ConversationAdapter(
    private val onSelect: (Conversation) -> Unit,
    private val onDelete: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationAdapter.VH>(DiffCb()) {

    private var selectedId: String = ""
    fun setSelected(id: String) { selectedId = id; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, pos: Int) {
        val conv = getItem(pos)
        holder.binding.tvTitle.text = conv.title
        holder.binding.tvTitle.setTextColor(
            if (conv.id == selectedId) 0xFF4D49FC.toInt() else 0xFF1A1A1A.toInt()
        )
        holder.binding.btnDelete.visibility =
            if (conv.id == selectedId) android.view.View.GONE
            else android.view.View.VISIBLE

        holder.binding.root.setOnClickListener { onSelect(conv) }
        holder.binding.btnDelete.setOnClickListener { onDelete(conv) }
    }

    class VH(val binding: ItemConversationBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCb : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(o: Conversation, n: Conversation) = o.id == n.id
        override fun areContentsTheSame(o: Conversation, n: Conversation) =
            o.title == n.title && o.messages.size == n.messages.size
    }
}
