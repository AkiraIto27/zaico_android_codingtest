package jp.co.zaico.codingtest.ui.inventory.create

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import jp.co.zaico.codingtest.R
import jp.co.zaico.codingtest.ZaicoApplication
import jp.co.zaico.codingtest.databinding.FragmentInventoryCreateBinding
import kotlinx.coroutines.launch

/**
 * 在庫作成フォームと登録結果を表示するFragment。
 *
 * 旧クラス名: `AddFragment`
 */
class InventoryCreateFragment : Fragment() {
    private var binding: FragmentInventoryCreateBinding? = null
    private var completionHandled = false
    private var emptyCompanyToastShown = false

    private val viewModel: InventoryCreateViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(InventoryCreateViewModel::class.java))
                val creator = (requireActivity().application as ZaicoApplication)
                    .createInventoryCreator()
                @Suppress("UNCHECKED_CAST")
                return InventoryCreateViewModel(creator) as T
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentInventoryCreateBinding.inflate(inflater, container, false)
        .also { binding = it }
        .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentBinding = binding ?: return
        currentBinding.titleEditText.doAfterTextChanged {
            viewModel.updateTitle(it?.toString().orEmpty())
        }
        currentBinding.submitButton.setOnClickListener { viewModel.submit() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (binding == null) return@collect
                    updateView(state)
                    handleRequestError(state.requestError)
                    handleSubmissionSuccess(state.createdInventoryId)
                }
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun updateView(state: InventoryCreateUiState) {
        val currentBinding = binding ?: return
        currentBinding.apply {
            val renderedTitle = titleEditText.text?.toString().orEmpty()
            if (renderedTitle != state.title) {
                titleEditText.setText(state.title)
                titleEditText.setSelection(state.title.length)
            }
            titleInputLayout.error = when (state.titleError) {
                InventoryCreateTitleError.Required -> getString(R.string.inventory_create_title_required)
                InventoryCreateTitleError.TooLong -> getString(R.string.inventory_create_title_too_long)
                null -> null
            }
            titleEditText.isEnabled = !state.isSubmitting
            submitButton.isEnabled = !state.isSubmitting && state.createdInventoryId == null
            progressIndicator.isVisible = state.isSubmitting
        }
    }

    private fun handleRequestError(error: InventoryCreateRequestError?) {
        val currentBinding = binding ?: return
        val errorMessage = when (error) {
            InventoryCreateRequestError.Configuration -> R.string.inventory_create_configuration_error
            InventoryCreateRequestError.EmptyCompany -> R.string.company_list_empty
            InventoryCreateRequestError.Http -> R.string.inventory_create_api_error
            InventoryCreateRequestError.InvalidResponse -> R.string.inventory_create_response_error
            InventoryCreateRequestError.Network -> R.string.inventory_create_network_error
            null -> null
        }
        currentBinding.apply {
            errorText.isVisible = errorMessage != null
            if (errorMessage == null) errorText.text = "" else errorText.setText(errorMessage)
        }
        if (error == InventoryCreateRequestError.EmptyCompany && !emptyCompanyToastShown) {
            emptyCompanyToastShown = true
            Toast.makeText(requireContext(), R.string.company_list_empty, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleSubmissionSuccess(inventoryId: Long?) {
        if (inventoryId == null || completionHandled || binding == null) return
        completionHandled = true
        Toast.makeText(requireContext(), R.string.inventory_create_success, Toast.LENGTH_SHORT).show()
        requireActivity().setResult(Activity.RESULT_OK)
        requireActivity().finish()
    }
}
