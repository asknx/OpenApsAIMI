package app.aaps.plugins.aps.openAPSAIMI.context.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSAIMI.context.ContextIntent

/**
 * Adapter for active intents RecyclerView.
 * 
 * Displays each intent with:
 * - Icon + Type + Intensity
 * - Time remaining
 * - Remove and Extend buttons
 */
class ContextIntentAdapter(
    private val onRemove: (String) -> Unit,
    private val onExtend: (String) -> Unit,
    private val getTimeRemaining: (ContextIntent) -> String,
    private val getDisplayString: (ContextIntent) -> String
) : ListAdapter<Pair<String, ContextIntent>, ContextIntentAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_active_intent,
            parent,
            false
        )
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {
        private val textIntentType: TextView = itemView.findViewById(R.id.textIntentType)
        private val textTimeRemaining: TextView = itemView.findViewById(R.id.textTimeRemaining)
        private val textConfidence: TextView = itemView.findViewById(R.id.textConfidence)
        private val btnRemove: Button = itemView.findViewById(R.id.btnRemove)
        private val btnExtend: Button = itemView.findViewById(R.id.btnExtend)
        
        fun bind(item: Pair<String, ContextIntent>) {
            val (id, intent) = item
            
            // Display string (e.g., "🏃 Activity: CARDIO HIGH")
            textIntentType.text = getDisplayString(intent)
            
            // Time remaining
            textTimeRemaining.text = getTimeRemaining(intent)
            
            // Confidence (if available)
            val confidenceText = when {
                intent.confidence >= 0.90 -> "✓ Haute confiance"
                intent.confidence >= 0.70 -> "~ Moyenne confiance"
                intent.confidence >= 0.50 -> "? Faible confiance"
                else -> ""
            }
            textConfidence.text = confidenceText
            
            // Remove button
            btnRemove.setOnClickListener {
                onRemove(id)
            }
            
            // Extend button
            btnExtend.setOnClickListener {
                onExtend(id)
            }
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<Pair<String, ContextIntent>>() {
        override fun areItemsTheSame(
            oldItem: Pair<String, ContextIntent>,
            newItem: Pair<String, ContextIntent>
        ): Boolean {
            return oldItem.first == newItem.first
        }
        
        override fun areContentsTheSame(
            oldItem: Pair<String, ContextIntent>,
            newItem: Pair<String, ContextIntent>
        ): Boolean {
            return oldItem == newItem
        }
    }
}
