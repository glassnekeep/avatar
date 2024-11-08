package ru.ebica.avatar.camera_response

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.ebica.avatar.R
import ru.ebica.avatar.camera.CameraResponse
import ru.ebica.avatar.databinding.DialogCameraResponseBinding

class CameraResponseDialog : DialogFragment() {
    private var _binding: DialogCameraResponseBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance(response: CameraResponse): CameraResponseDialog {
            val dialog = CameraResponseDialog()
            val bundle = Bundle().apply {
                putParcelable(CameraResponse.responseKey, response)
            }
            dialog.arguments = bundle
            return dialog
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogCameraResponseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.window?.setBackgroundDrawableResource(R.drawable.background_rounded_corners)
        isCancelable = false

        viewLifecycleOwner.lifecycleScope.launch {
            repeatTextEveryTwoSeconds()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            delay(8000) // Имитация запроса

            val result = EmotionResponse.generateRandomEmotionResult()

            parentFragmentManager.setFragmentResult(EmotionResponse.resultKey, Bundle().apply {
                putParcelable(EmotionResponse.responseKey, result)
            })

            dismiss()
        }
    }

    private suspend fun repeatTextEveryTwoSeconds() {
        while (true) {
            delay(2000)
            if (binding.message.text != "Идет обработка...") {
                binding.message.text = "Идет обработка..."
            } else {
                binding.message.text = "Подождите"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}