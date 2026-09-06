package jp.co.zaico.codingtest.ui.inventory.list

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.zaico.codingtest.R
import jp.co.zaico.codingtest.data.model.Inventory

private object InventoryListDiffCallback : DiffUtil.ItemCallback<Inventory>() {
    override fun areItemsTheSame(oldItem: Inventory, newItem: Inventory): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Inventory, newItem: Inventory): Boolean =
        oldItem == newItem
}

/**
 * Renders inventory summaries and forwards item selections.
 *
 * 旧クラス名: `MyAdapter`
 */
class InventoryListAdapter(
    private val itemClickListener: OnItemClickListener
) : ListAdapter<Inventory, InventoryListAdapter.ViewHolder>(InventoryListDiffCallback) {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

    interface OnItemClickListener {
        fun itemClick(item: Inventory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        (holder.itemView.findViewById<View>(R.id.textView_id) as TextView).text = item.id.toString()
        (holder.itemView.findViewById<View>(R.id.textView_title) as TextView).text = item.title
        holder.itemView.setOnClickListener { itemClickListener.itemClick(item) }
    }
}
