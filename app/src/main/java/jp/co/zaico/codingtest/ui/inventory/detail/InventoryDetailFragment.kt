package jp.co.zaico.codingtest.ui.inventory.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import jp.co.zaico.codingtest.ZaicoApplication
import jp.co.zaico.codingtest.domain.inventory.Inventory
import jp.co.zaico.codingtest.databinding.FragmentInventoryDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 在庫詳細画面を表示するFragment。
 *
 * 旧クラス名: `SecondFragment`
 */
class InventoryDetailFragment : Fragment() {
    private var _binding: FragmentInventoryDetailBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInventoryDetailBinding.inflate(inflater, container, false)
        return requireNotNull(_binding).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val inventoryId = requireNotNull(requireArguments().getString("inventoryId")).toInt()
        val viewModel = InventoryDetailViewModel(
            (requireActivity().application as ZaicoApplication).createInventoryRepository()
        )
        viewLifecycleOwner.lifecycleScope.launch {
            val inventory = withContext(Dispatchers.IO) {
                viewModel.getInventory(inventoryId)
            }
            if (_binding != null) initView(inventory)
        }
    }

    private fun initView(inventory: Inventory) {
        requireNotNull(_binding).apply {
            textViewId.text = inventory.id.toString()
            textViewTitle.text = inventory.title
            textViewQuantity.text = inventory.quantity
        }
    }
}
