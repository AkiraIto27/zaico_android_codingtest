package jp.co.zaico.codingtest

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import jp.co.zaico.codingtest.databinding.FragmentSecondBinding

class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return _binding!!.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val inventoryId = arguments!!.getString("inventoryId")!!.toInt()

        val _viewModel = SecondViewModel(context!!)

        val inventory = _viewModel.getInventory(inventoryId)
        initView(inventory)

    }

    private fun initView(inventory: Inventory) {
        _binding!!.textViewId.text = inventory.id.toString()
        _binding!!.textViewTitle.text = inventory.title
        _binding!!.textViewQuantity.text = inventory.quantity
    }

}