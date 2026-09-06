package jp.co.zaico.codingtest

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import jp.co.zaico.codingtest.databinding.FragmentFirstBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private lateinit var viewModel: FirstViewModel
    private lateinit var adapter: MyAdapter
    private var loadJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFirstBinding.inflate(layoutInflater)
        return _binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = FirstViewModel(context!!)

        val _layoutManager = LinearLayoutManager(context!!)
        val _dividerItemDecoration = DividerItemDecoration(
            context!!,
            _layoutManager.orientation
        )
        adapter = MyAdapter(object : MyAdapter.OnItemClickListener {
            override fun itemClick(item: Inventory) {
                val bundle = bundleOf("inventoryId" to item.id.toString())
                findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment, bundle)
            }
        })

        _binding!!.recyclerView.also {
            it.layoutManager = _layoutManager
            it.addItemDecoration(_dividerItemDecoration)
            it.adapter = adapter
        }

    }

    override fun onResume() {
        super.onResume()
        if (_binding == null || !::viewModel.isInitialized) return

        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val inventories = withContext(Dispatchers.IO) {
                    viewModel.getInventories()
                }
                if (_binding != null) {
                    adapter.submitList(inventories)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                context?.let {
                    Toast.makeText(it, R.string.inventory_load_error, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        loadJob = null
        _binding?.recyclerView?.adapter = null
        _binding = null
        super.onDestroyView()
    }

}

val diff_util= object: DiffUtil.ItemCallback<Inventory>(){
    override fun areItemsTheSame(oldItem: Inventory, newItem: Inventory): Boolean
    {
        return oldItem.title== newItem.title
    }

    override fun areContentsTheSame(oldItem: Inventory, newItem: Inventory): Boolean
    {
        return oldItem== newItem
    }

}

class MyAdapter(
    private val itemClickListener: OnItemClickListener,
) : ListAdapter<Inventory, MyAdapter.ViewHolder>(diff_util) {

    class ViewHolder(view: View): RecyclerView.ViewHolder(view)

    interface OnItemClickListener{
        fun itemClick(item: Inventory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder
    {
        val _view= LayoutInflater.from(parent.context)
            .inflate(R.layout.first_item, parent, false)
        return ViewHolder(_view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int)
    {
        val _item= getItem(position)
        (holder.itemView.findViewById<View>(R.id.textView_id) as TextView).text = _item.id.toString()
        (holder.itemView.findViewById<View>(R.id.textView_title) as TextView).text = _item.title

        holder.itemView.setOnClickListener{
            itemClickListener.itemClick(_item)
        }
    }

}
