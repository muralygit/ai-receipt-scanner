package com.muraly.receiptscanner.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.muraly.receiptscanner.data.local.entity.ReceiptWithItems
import com.muraly.receiptscanner.databinding.ItemReceiptBinding
import java.text.NumberFormat
import java.util.Locale

class ReceiptAdapter(
    private val onClick: (ReceiptWithItems) -> Unit,
    private val onLongClick: (ReceiptWithItems) -> Unit
) : ListAdapter<ReceiptWithItems, ReceiptAdapter.ReceiptViewHolder>(DIFF_CALLBACK) {

    private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReceiptViewHolder {
        val binding = ItemReceiptBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReceiptViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReceiptViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ReceiptViewHolder(private val binding: ItemReceiptBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ReceiptWithItems) {
            binding.tvShopName.text = item.receipt.shopName.ifBlank { "Unknown Shop" }
            binding.tvTotal.text = inrFormat.format(item.receipt.total)
            binding.tvDate.text = item.receipt.date.ifBlank { "-" }

            binding.root.setOnClickListener { onClick(item) }
            binding.root.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ReceiptWithItems>() {
            override fun areItemsTheSame(oldItem: ReceiptWithItems, newItem: ReceiptWithItems) =
                oldItem.receipt.id == newItem.receipt.id

            override fun areContentsTheSame(oldItem: ReceiptWithItems, newItem: ReceiptWithItems) =
                oldItem == newItem
        }
    }
}
