package ru.ebica.avatar.camera

import android.Manifest
import android.animation.Animator
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.fragment.findNavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.Gson
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.microsoft.projectoxford.emotion.EmotionServiceClient
import com.microsoft.projectoxford.emotion.EmotionServiceRestClient
import com.microsoft.projectoxford.emotion.contract.RecognizeResult
import com.microsoft.projectoxford.emotion.rest.EmotionServiceException
import ru.ebica.avatar.R
import ru.ebica.avatar.databinding.FragmentCameraBinding
import ru.ebica.avatar.viewExtensions.gone
import ru.ebica.avatar.viewExtensions.visible
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .build()
        FaceDetection.getClient(options)
    }

    private val frameAnalyzer = ImageAnalysis.Analyzer { imageProxy ->
        processImageProxy(imageProxy)
    }

    private val autoDetectionHandler = Handler(Looper.getMainLooper())
    private val autoDetectionInterval = 5000L
    private var isAutoDetectionRunning = false
    private lateinit var emotionClient: EmotionServiceClient

    private val autoDetectionRunnable = object : Runnable {
        override fun run() {
            if (isAutoDetectionRunning) {
                captureCurrentFrame { bitmap ->
                    bitmap?.let {
                        runCatching {
                            val emotionResults = processWithAutoFaceDetection(emotionClient, it)
                            // Здесь можно обработать результат
                            Log.e("MY_FLAG", "Emotion detection results: $emotionResults")
                        }.onFailure { e ->
                            Log.e("MY_FLAG", "Error processing emotions: ${e.message}")
                        }
                    }
                }
                autoDetectionHandler.postDelayed(this, autoDetectionInterval)
            }
        }
    }

    private fun captureCurrentFrame(callback: (Bitmap?) -> Unit) {
        val surface = binding.previewView.bitmap
        callback(surface)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        emotionClient = EmotionServiceRestClient(getString(R.string.subscription_key))

        binding.endRecording.setOnClickListener {
            stopRecording()
        }
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
            startCameraPreview()
            startAutoDetection()
        }
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.CAMERA),
            1
        )
    }

    private fun startCameraPreview() {
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()
                bindPreview(cameraProvider)
            },
            ContextCompat.getMainExecutor(requireContext())
        )
    }

    private fun stopCameraPreview() {
        cameraProviderFuture.get().unbindAll()
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview: Preview = Preview.Builder().build()
        val cameraSelector: CameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()
        preview.setSurfaceProvider(binding.previewView.surfaceProvider)

        val imageFrameAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        imageFrameAnalysis.setAnalyzer(Executors.newSingleThreadExecutor(), frameAnalyzer)

        cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            preview,
            imageFrameAnalysis
        )

        setupVideoCapture(cameraProvider)
    }

    private var isFaceDetected = false
    private var faceDetectionStartTime: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private val detectionDuration = 5000L

    private val faceDetectionRunnable = Runnable {
        if (isFaceDetected) {
            binding.message.text = "Приготовьтесь к записи видео"
            binding.gradientBackground.background = ContextCompat.getDrawable(
                requireContext(),
                R.drawable.background_green_gradient_border
            )

            binding.countdownTimer.visible()
            binding.countdownTimer.playAnimation()

            binding.countdownTimer.addAnimatorListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) { }

                override fun onAnimationEnd(animation: Animator) {
                    binding.gradientBackground.gone()
                    binding.countdownTimer.gone()

                    binding.message.text = "Говорите"
                    startRecording()
                }

                override fun onAnimationCancel(animation: Animator) { }

                override fun onAnimationRepeat(animation: Animator) { }
            })
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        if (!isFaceDetected) {
                            isFaceDetected = true
                            faceDetectionStartTime = System.currentTimeMillis()
                            binding.previewView.setBackgroundColor(Color.TRANSPARENT)
                            handler.postDelayed(faceDetectionRunnable, detectionDuration)
                        }
                    } else {
                        if (isFaceDetected) {
                            isFaceDetected = false
                            binding.previewView.setBackgroundColor(Color.RED)
                            handler.removeCallbacks(faceDetectionRunnable)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("CameraFragment", "Ошибка при обработке изображения: ${e.message}")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private fun startRecording() {
        val videoCapture = videoCapture ?: return

        val videoFile = File(requireContext().externalMediaDirs.first(), "${System.currentTimeMillis()}.mp4")
        val outputOptions = FileOutputOptions.Builder(videoFile).build()

        currentRecording = videoCapture.output
            .prepareRecording(requireContext(), outputOptions)
            .apply {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(requireContext())) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        Log.e("MY_FLAG", "Запись началась")
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            Log.e("MY_FLAG", "Запись завершена. Сохранено: ${videoFile.absolutePath}")

                            val cameraResponse = CameraResponse(
                                videoFilePath = videoFile.absolutePath
                            )
                            setFragmentResult(cameraResponse)
                        } else {
                            Log.e("MY_FLAG", "Ошибка записи")
                            val cameraResponse = CameraResponse(
                                videoFilePath = null
                            )
                            setFragmentResult(cameraResponse)
                        }
                        currentRecording = null
                    }
                }
            }

        binding.endRecording.visible()
    }

    private fun stopRecording() {
        currentRecording?.stop()
        currentRecording = null
        binding.endRecording.gone()
    }

    private fun setupVideoCapture(cameraProvider: ProcessCameraProvider) {
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        cameraProvider.bindToLifecycle(
            this as LifecycleOwner,
            cameraSelector,
            videoCapture
        )
    }

    private fun setFragmentResult(result: CameraResponse) {
        setFragmentResult(CameraResponse.resultKey, Bundle().apply {
            putParcelable(CameraResponse.responseKey, result)
        })

        findNavController().navigateUp()
    }

    override fun onPause() {
        super.onPause()
        stopCameraPreview()
        stopAutoDetection()
    }

    private fun startAutoDetection() {
        if (!isAutoDetectionRunning) {
            isAutoDetectionRunning = true
            autoDetectionHandler.post(autoDetectionRunnable)
        }
    }

    private fun stopAutoDetection() {
        isAutoDetectionRunning = false
        autoDetectionHandler.removeCallbacks(autoDetectionRunnable)
    }

    @Throws(EmotionServiceException::class, IOException::class)
    private fun processWithAutoFaceDetection(client: EmotionServiceClient, mBitmap: Bitmap): List<RecognizeResult>? {
        val gson = Gson()
        val output = ByteArrayOutputStream()
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
        val inputStream = ByteArrayInputStream(output.toByteArray())

        val startTime = System.currentTimeMillis()

        val result = client.recognizeImage(inputStream)
        val json = gson.toJson(result)
        Log.e("MY_FLAG", json)

        Log.e(
            "MY_FLAG",
            String.format("Detection done. Elapsed time: %d ms", (System.currentTimeMillis() - startTime))
        )

        return result
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        faceDetector.close()
        handler.removeCallbacks(faceDetectionRunnable)
    }
}