package jp.co.zaico.codingtest

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
import jp.co.zaico.codingtest.databinding.FragmentAddBinding
import kotlinx.coroutines.launch

class AddFragment : Fragment() {

    private var binding: FragmentAddBinding? = null
    private var completionHandled = false
    private var emptyCompanyToastShown = false

    private val viewModel: AddViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(AddViewModel::class.java))
                val creator = (requireActivity().application as ZaicoApplication)
                    .createInventoryCreator()
                @Suppress("UNCHECKED_CAST")
                return AddViewModel(creator) as T
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return FragmentAddBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentBinding = binding ?: return

        currentBinding.titleEditText.doAfterTextChanged {
            viewModel.updateTitle(it?.toString().orEmpty())
        }
        currentBinding.submitButton.setOnClickListener {
            viewModel.submit()
        }

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

    private fun updateView(state: AddUiState) {
        val currentBinding = binding ?: return

        currentBinding.apply {
            val renderedTitle = titleEditText.text?.toString().orEmpty()
            if (renderedTitle != state.title) {
                titleEditText.setText(state.title)
                titleEditText.setSelection(state.title.length)
            }

            titleInputLayout.error = when (state.titleError) {
                AddTitleError.Required -> getString(R.string.add_title_required)
                AddTitleError.TooLong -> getString(R.string.add_title_too_long)
                null -> null
            }
            titleEditText.isEnabled = !state.isSubmitting
            submitButton.isEnabled = !state.isSubmitting && state.createdInventoryId == null
            progressIndicator.isVisible = state.isSubmitting
        }
    }

    private fun handleRequestError(error: AddRequestError?) {
        val currentBinding = binding ?: return
        val errorMessage = when (error) {
            AddRequestError.Configuration -> R.string.add_configuration_error
            AddRequestError.EmptyCompany -> R.string.company_list_empty
            AddRequestError.Http -> R.string.add_api_error
            AddRequestError.InvalidResponse -> R.string.add_response_error
            AddRequestError.Network -> R.string.add_network_error
            null -> null
        }

        currentBinding.apply {
            errorText.isVisible = errorMessage != null
            if (errorMessage == null) {
                errorText.text = ""
            } else {
                errorText.setText(errorMessage)
            }
        }

        if (error == AddRequestError.EmptyCompany && !emptyCompanyToastShown) {
            emptyCompanyToastShown = true
            Toast.makeText(requireContext(), R.string.company_list_empty, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleSubmissionSuccess(inventoryId: Long?) {
        if (inventoryId == null || completionHandled || binding == null) return

        completionHandled = true
        Toast.makeText(requireContext(), R.string.add_success, Toast.LENGTH_SHORT).show()
        requireActivity().setResult(Activity.RESULT_OK)
        requireActivity().finish()
    }
}
