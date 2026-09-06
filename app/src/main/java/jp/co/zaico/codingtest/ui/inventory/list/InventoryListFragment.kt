package jp.co.zaico.codingtest.ui.inventory.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import jp.co.zaico.codingtest.R
import jp.co.zaico.codingtest.databinding.FragmentInventoryListBinding
import kotlinx.coroutines.launch

/**
 * 在庫一覧画面を表示するFragment。
 *
 * 旧クラス名: `FirstFragment`
 */
@AndroidEntryPoint
class InventoryListFragment : Fragment() {
    private var binding: FragmentInventoryListBinding? = null
    private val viewModel: InventoryListViewModel by viewModels()
    private var adapter: InventoryListAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentInventoryListBinding.inflate(inflater, container, false)
        return requireNotNull(binding).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentBinding = requireNotNull(binding)
        val layoutManager = LinearLayoutManager(requireContext())
        val listAdapter = InventoryListAdapter(object : InventoryListAdapter.OnItemClickListener {
            override fun itemClick(item: jp.co.zaico.codingtest.domain.inventory.Inventory) {
                findNavController().navigate(
                    R.id.action_inventory_list_to_inventory_detail,
                    bundleOf("inventoryId" to item.id.toString())
                )
            }
        })
        adapter = listAdapter
        currentBinding.recyclerView.apply {
            this.layoutManager = layoutManager
            addItemDecoration(DividerItemDecoration(requireContext(), layoutManager.orientation))
            this.adapter = listAdapter
        }

        findNavController().currentBackStackEntry?.savedStateHandle
            ?.getLiveData<Long>(INVENTORY_CREATE_RESULT_KEY)
            ?.observe(viewLifecycleOwner) { eventId ->
                viewModel.onInventoryCreated(eventId)
                findNavController().currentBackStackEntry?.savedStateHandle
                    ?.remove<Long>(INVENTORY_CREATE_RESULT_KEY)
            }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onScreenResumed()
    }

    override fun onPause() {
        viewModel.onScreenPaused(requireActivity().isChangingConfigurations)
        super.onPause()
    }

    override fun onDestroyView() {
        binding?.recyclerView?.adapter = null
        adapter = null
        binding = null
        super.onDestroyView()
    }

    private fun render(state: InventoryListUiState) {
        adapter?.submitList(state.inventories)
        state.errorNotificationId?.let { notificationId ->
            val message = if (state.error == InventoryListError.CompanyEmpty) {
                R.string.company_list_empty
            } else {
                R.string.inventory_load_error
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            viewModel.consumeErrorNotification(notificationId)
        }
    }

    private companion object {
        const val INVENTORY_CREATE_RESULT_KEY = "inventory_create_result"
    }
}
