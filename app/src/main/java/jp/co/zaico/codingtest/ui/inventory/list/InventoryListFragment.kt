package jp.co.zaico.codingtest.ui.inventory.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import jp.co.zaico.codingtest.R
import jp.co.zaico.codingtest.ZaicoApplication
import jp.co.zaico.codingtest.data.model.Inventory
import jp.co.zaico.codingtest.data.repository.CompanyIdResult
import jp.co.zaico.codingtest.data.repository.CompanyRepositoryException
import jp.co.zaico.codingtest.databinding.FragmentInventoryListBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Inventory list screen.
 *
 * 旧クラス名: `FirstFragment`
 */
class InventoryListFragment : Fragment() {
    private var _binding: FragmentInventoryListBinding? = null
    private lateinit var viewModel: InventoryListViewModel
    private lateinit var adapter: InventoryListAdapter
    private var loadJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventoryListBinding.inflate(inflater, container, false)
        return requireNotNull(_binding).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = InventoryListViewModel(
            (requireActivity().application as ZaicoApplication).createInventoryRepository()
        )
        val layoutManager = LinearLayoutManager(requireContext())
        val dividerItemDecoration = DividerItemDecoration(requireContext(), layoutManager.orientation)
        adapter = InventoryListAdapter(object : InventoryListAdapter.OnItemClickListener {
            override fun itemClick(item: Inventory) {
                val bundle = bundleOf("inventoryId" to item.id.toString())
                findNavController().navigate(
                    R.id.action_inventory_list_to_inventory_detail,
                    bundle
                )
            }
        })
        requireNotNull(_binding).recyclerView.also {
            it.layoutManager = layoutManager
            it.addItemDecoration(dividerItemDecoration)
            it.adapter = adapter
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding == null || !::viewModel.isInitialized) return
        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val inventories = withContext(Dispatchers.IO) { viewModel.getInventories() }
                if (_binding != null) adapter.submitList(inventories)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (empty: CompanyRepositoryException) {
                val message = if (empty.result is CompanyIdResult.Empty) {
                    R.string.company_list_empty
                } else {
                    R.string.inventory_load_error
                }
                context?.let { Toast.makeText(it, message, Toast.LENGTH_LONG).show() }
            } catch (_: Exception) {
                context?.let { Toast.makeText(it, R.string.inventory_load_error, Toast.LENGTH_SHORT).show() }
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
