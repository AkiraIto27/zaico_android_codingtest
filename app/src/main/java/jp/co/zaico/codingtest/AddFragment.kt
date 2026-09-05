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
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import jp.co.zaico.codingtest.databinding.FragmentAddBinding
import kotlinx.coroutines.launch

class AddFragment : Fragment() {

    private var binding: FragmentAddBinding? = null
    private var completionHandled = false

    private val viewModel: AddViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(AddViewModel::class.java))
                val creator = KtorInventoryCreator(
                    client = HttpClient(Android),
                    baseUrl = getString(R.string.api_endpoint),
                    token = getString(R.string.api_token)
                )
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
        val currentBinding = requireNotNull(binding)

        currentBinding.titleEditText.doAfterTextChanged {
            viewModel.updateTitle(it?.toString().orEmpty())
        }
        currentBinding.submitButton.setOnClickListener {
            viewModel.submit()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun render(state: AddUiState) {
        val currentBinding = binding ?: return
        val renderedTitle = currentBinding.titleEditText.text?.toString().orEmpty()
        if (renderedTitle != state.title) {
            currentBinding.titleEditText.setText(state.title)
            currentBinding.titleEditText.setSelection(state.title.length)
        }

        currentBinding.titleInputLayout.error = when (state.titleError) {
            AddTitleError.Required -> getString(R.string.add_title_required)
            AddTitleError.TooLong -> getString(R.string.add_title_too_long)
            null -> null
        }
        val requestError = when (state.requestError) {
            AddRequestError.Configuration -> R.string.add_configuration_error
            AddRequestError.Http -> R.string.add_api_error
            AddRequestError.InvalidResponse -> R.string.add_response_error
            AddRequestError.Network -> R.string.add_network_error
            null -> null
        }
        currentBinding.errorText.isVisible = requestError != null
        requestError?.let(currentBinding.errorText::setText)

        currentBinding.titleEditText.isEnabled = !state.isSubmitting
        currentBinding.submitButton.isEnabled = !state.isSubmitting && state.createdInventoryId == null
        currentBinding.progressIndicator.isVisible = state.isSubmitting

        if (state.createdInventoryId != null && !completionHandled) {
            completionHandled = true
            Toast.makeText(requireContext(), R.string.add_success, Toast.LENGTH_SHORT).show()
            requireActivity().setResult(Activity.RESULT_OK)
            requireActivity().finish()
        }
    }
}
