package ru.ebica.avatar.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.navigation.fragment.findNavController
import ru.ebica.avatar.R
import ru.ebica.avatar.camera.CameraRequest
import ru.ebica.avatar.camera.CameraResponse
import ru.ebica.avatar.databinding.FragmentHomeBinding

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

        setFragmentResultListener(CameraResponse.resultKey) { _, bundle ->
            val result = bundle.getParcelable<CameraResponse>(CameraResponse.responseKey)
            // Обработка результата
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}