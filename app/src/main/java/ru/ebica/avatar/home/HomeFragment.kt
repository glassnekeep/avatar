package ru.ebica.avatar.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import ru.ebica.avatar.R
import ru.ebica.avatar.camera.CameraRequest
import ru.ebica.avatar.camera.CameraResponse
import ru.ebica.avatar.databinding.FragmentHomeBinding
import ru.ebica.avatar.camera_response.CameraResponseDialog
import ru.ebica.avatar.camera_response.EmotionResponse

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recordVideo.setOnClickListener {
            val request = CameraRequest("some parament")
            val bundle = Bundle().apply {
                putParcelable(CameraRequest.requestKey, request)
            }
            findNavController().navigate(R.id.action_homeFragment_to_cameraFragment, bundle)
        }

        observeCameraResponse()

        observeEmotionResponse()
    }

    private fun observeCameraResponse() {
        setFragmentResultListener(CameraResponse.resultKey) { _, bundle ->
            val response = bundle.getParcelable<CameraResponse>(CameraResponse.responseKey)

            processCameraResponse(response ?: CameraResponse(null))
        }
    }

    private fun processCameraResponse(response: CameraResponse) {
        if (response.videoFilePath == null) {
            Toast.makeText(requireContext(), "Произошла ошибка при записи видео", Toast.LENGTH_LONG).show()
            return
        }

        binding.message.text = ""
        binding.recordVideo.text = "Записать новое видео"

        val dialog = CameraResponseDialog.newInstance(response)
        dialog.show(parentFragmentManager, "CameraResultDialog")
    }

    private fun observeEmotionResponse() {
        setFragmentResultListener(EmotionResponse.resultKey) { _, bundle ->
            val response = bundle.getParcelable<EmotionResponse>(EmotionResponse.responseKey)

            processEmotionResponse(response)
        }
    }

    private fun processEmotionResponse(response: EmotionResponse?) {
        if (response == null) {
            Toast.makeText(requireContext(), "Произошла ошибка при распознавании эмоции", Toast.LENGTH_LONG).show()
            return
        }

        val animationResId = when (response.emotion) {
            EmotionResponse.Emotion.Angry -> R.raw.emotion_angry
            EmotionResponse.Emotion.Apathy -> R.raw.emotion_apathy
            EmotionResponse.Emotion.Crying -> R.raw.emotion_crying
            EmotionResponse.Emotion.Happy -> R.raw.emotion_happy
            EmotionResponse.Emotion.Loved -> R.raw.emotion_loved
            EmotionResponse.Emotion.Sad -> R.raw.emotion_sad
        }

        binding.emotion.setAnimation(animationResId)
        binding.emotion.playAnimation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}