package ru.ebica.avatar.home

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import com.google.common.util.concurrent.ListenableFuture
import ru.ebica.avatar.R
import ru.ebica.avatar.camera.CameraResponse
import ru.ebica.avatar.camera_response.CameraResponseDialog
import ru.ebica.avatar.camera_response.EmotionResponse
import ru.ebica.avatar.databinding.FragmentHomeBinding
import java.util.Locale

class HomeFragment : Fragment(), OnInitListener {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var textToSpeech: TextToSpeech

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    private val speechRecognizerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val recognizedText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            processEmotionResponse(response = EmotionResponse.generateRandomEmotionResult())
            Handler(Looper.getMainLooper()).postDelayed(700L) {
                playVoice(recognizedText?.get(0) ?: "Я вас не понимаю")
            }
            //Toast.makeText(context, recognizedText?.get(0) ?: "Результат пустой", Toast.LENGTH_SHORT).show()
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

        //binding.recordVideo.setOnClickListener {
        //    val request = CameraRequest("some parament")
        //    val bundle = Bundle().apply {
        //        putParcelable(CameraRequest.requestKey, request)
        //    }
        //    findNavController().navigate(R.id.action_homeFragment_to_cameraFragment, bundle)
        //}

        binding.recordAudio.setOnClickListener {
            startSpeechToText()
        }

        observeCameraResponse()

        observeEmotionResponse()

//        viewLifecycleOwner.lifecycleScope.launch {
//            viewModel.speechFlow.flowWithLifecycle(viewLifecycleOwner.lifecycle, Lifecycle.State.STARTED)
//                .collect { data ->
//                    playVoice(data)
//                }
//        }
    }

    override fun onResume() {
        super.onResume()

        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        if (
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission()
        } else {
            startCamera()
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

        //binding.message.text = ""
        //binding.recordVideo.text = "Записать новое видео"

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
            EmotionResponse.Emotion.Normal -> R.raw.emotion_regular_winking
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

    private fun startCamera() {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            val imageCapture = ImageCapture.Builder()
                .setTargetRotation(requireActivity().window.decorView.display.rotation)
                .build()

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageCapture)
                //startPeriodicCapture(imageCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.CAMERA),
            1
        )
    }

    companion object {
        private const val TAG = "HOME"
    }
}