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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import jp.co.zaico.codingtest.R
import jp.co.zaico.codingtest.databinding.FragmentInventoryCreateBinding
import kotlinx.coroutines.launch

/**
 * 在庫作成フォームと登録結果を表示するFragment。
 *
 * 旧クラス名: `AddFragment`
 */
@AndroidEntryPoint
class InventoryCreateFragment : Fragment() {
    private var binding: FragmentInventoryCreateBinding? = null
    private val viewModel: InventoryCreateViewModel by viewModels()

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
                viewModel.uiState.collect { state -> render(state) }
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun render(state: InventoryCreateUiState) {
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

        renderRequestError(state)
        state.emptyCompanyToastNotificationId?.let { notificationId ->
            Toast.makeText(requireContext(), R.string.company_list_empty, Toast.LENGTH_LONG).show()
            viewModel.consumeEmptyCompanyToast(notificationId)
        }
        state.successNotificationId?.let { notificationId ->
            Toast.makeText(requireContext(), R.string.inventory_create_success, Toast.LENGTH_SHORT).show()
            requireActivity().setResult(Activity.RESULT_OK)
            requireActivity().finish()
            viewModel.consumeSuccessNotification(notificationId)
        }
    }

    private fun renderRequestError(state: InventoryCreateUiState) {
        val currentBinding = binding ?: return
        val errorMessage = when (state.requestError) {
            InventoryCreateRequestError.Configuration -> R.string.inventory_create_configuration_error
            InventoryCreateRequestError.EmptyCompany -> R.string.company_list_empty
            InventoryCreateRequestError.Http -> R.string.inventory_create_api_error
            InventoryCreateRequestError.InvalidResponse -> R.string.inventory_create_response_error
            InventoryCreateRequestError.Network -> R.string.inventory_create_network_error
            null -> null
        }
        currentBinding.errorText.isVisible = errorMessage != null
        if (errorMessage == null) {
            currentBinding.errorText.text = ""
        } else {
            currentBinding.errorText.setText(errorMessage)
        }
        state.requestErrorNotificationId?.let(viewModel::consumeRequestErrorNotification)
    }
}
