package ru.ebica.avatar.home

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.w3c.dom.Text
import ru.ebica.avatar.R
import ru.ebica.avatar.camera.CameraRequest
import ru.ebica.avatar.camera.CameraResponse
import ru.ebica.avatar.databinding.FragmentHomeBinding
import ru.ebica.avatar.camera_response.CameraResponseDialog
import ru.ebica.avatar.camera_response.EmotionResponse
import java.util.Locale

class HomeFragment : Fragment(), OnInitListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var textToSpeech: TextToSpeech

    private val speechRecognizerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val recognizedText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            //tvResult.text = recognizedText?.get(0) ?: "Результат пустой"
            Toast.makeText(context, recognizedText?.get(0) ?: "Результат пустой", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Ошибка распознавания речи", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textToSpeech = TextToSpeech(context, this)
    }

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

        binding.recordAudio.setOnClickListener {
            startSpeechToText()
        }

        observeCameraResponse()

        observeEmotionResponse()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.speechFlow.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
                .collect { data ->
                    playVoice(data)
                }
        }
    }

    private fun startSpeechToText() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Ваше устройство не поддерживает распознавание речи", Toast.LENGTH_SHORT).show()
        }
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

    private fun playVoice(voice: String) {
        if (::textToSpeech.isInitialized) {
            textToSpeech.speak(voice, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
        super.onDestroy()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale("ru", "RU"))

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                return
            }
            //textToSpeech.speak("Тестовый текст", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }
}