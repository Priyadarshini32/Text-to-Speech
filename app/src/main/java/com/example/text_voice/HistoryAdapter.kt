package com.example.text_voice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class HistoryAdapter(
    private val historyList: List<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textViewContent: TextView = view.findViewById(R.id.textViewHistoryContent)
        val textViewTimestamp: TextView = view.findViewById(R.id.textViewHistoryTimestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val historyItem = historyList[position]
        holder.textViewContent.text = historyItem.text

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val date = Date(historyItem.timestamp)
        holder.textViewTimestamp.text = sdf.format(date)

        holder.itemView.setOnClickListener {
            onItemClick(historyItem)
        }
    }

    override fun getItemCount() = historyList.size
}