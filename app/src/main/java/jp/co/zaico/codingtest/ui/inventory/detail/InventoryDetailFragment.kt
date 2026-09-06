package jp.co.zaico.codingtest.ui.inventory.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import jp.co.zaico.codingtest.R
import jp.co.zaico.codingtest.databinding.FragmentInventoryDetailBinding
import kotlinx.coroutines.launch

/**
 * 在庫詳細画面を表示するFragment。
 *
 * 旧クラス名: `SecondFragment`
 */
@AndroidEntryPoint
class InventoryDetailFragment : Fragment() {
    private var binding: FragmentInventoryDetailBinding? = null
    private val viewModel: InventoryDetailViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentInventoryDetailBinding.inflate(inflater, container, false)
        return requireNotNull(binding).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun render(state: InventoryDetailUiState) {
        state.inventory?.let { inventory ->
            binding?.apply {
                textViewId.text = inventory.id.toString()
                textViewTitle.text = inventory.title
                textViewQuantity.text = inventory.quantity
            }
        }
        state.errorNotificationId?.let { notificationId ->
            val message = if (state.error == InventoryDetailError.CompanyEmpty) {
                R.string.company_list_empty
            } else {
                R.string.inventory_load_error
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            viewModel.consumeErrorNotification(notificationId)
        }
    }
}
