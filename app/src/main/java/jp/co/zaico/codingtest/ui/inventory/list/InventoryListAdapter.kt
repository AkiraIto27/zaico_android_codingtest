package jp.co.zaico.codingtest.ui.inventory.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.zaico.codingtest.databinding.ItemInventoryBinding
import jp.co.zaico.codingtest.domain.inventory.Inventory

private object InventoryListDiffCallback : DiffUtil.ItemCallback<Inventory>() {
    override fun areItemsTheSame(oldItem: Inventory, newItem: Inventory): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Inventory, newItem: Inventory): Boolean =
        oldItem == newItem
}

/**
 * 在庫一覧を表示し、項目の選択を通知するAdapter。
 *
 * 旧クラス名: `MyAdapter`
 */
class InventoryListAdapter(
    private val itemClickListener: OnItemClickListener
) : ListAdapter<Inventory, InventoryListAdapter.ViewHolder>(InventoryListDiffCallback) {
    class ViewHolder(val binding: ItemInventoryBinding) : RecyclerView.ViewHolder(binding.root)

    interface OnItemClickListener {
        fun itemClick(item: Inventory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInventoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.binding.apply {
            textViewId.text = item.id.toString()
            textViewTitle.text = item.title
            root.setOnClickListener { itemClickListener.itemClick(item) }
        }
    }
}
