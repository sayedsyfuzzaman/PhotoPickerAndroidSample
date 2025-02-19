package com.example.photopickerandroidsample

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.photopickerandroidsample.camera.CameraFragment
import com.example.photopickerandroidsample.databinding.FragmentHomeBinding
import com.example.photopickerandroidsample.utils.PermissionChecker
import com.example.photopickerandroidsample.utils.extensions.checkIfFragmentAttached
import com.yalantis.ucrop.UCrop

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val permissionChecker by lazy { PermissionChecker() }
    private var isProfileImage: Boolean = false
    private var isPassportImage: Boolean = false
    private var passportImageUri: Uri? = null
    private var profileImageUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.passportImage.load(passportImageUri ?: R.drawable.placeholder) {
            crossfade(true)
        }

        binding.profileImage.load(profileImageUri ?: R.drawable.placeholder) {
            crossfade(true)
            transformations(coil.transform.CircleCropTransformation())
        }

        binding.addPassportButton.setOnClickListener {
            isPassportImage = true
            isProfileImage = false
            checkPermissions()
        }
        binding.addProfileButton.setOnClickListener {
            isProfileImage = true
            isPassportImage = false
            checkPermissions()
        }
        observeImageFromCamera()
    }

    private fun checkPermissions() {
        if (permissionChecker.isNeededToRequestForCameraPermissions(requireContext())) {
            permissionChecker.checkCameraPermissions(
                binding.root,
                onPermissionGranted = ::onPermissionGranted,
            )
            return
        }
        onPermissionGranted()
    }

    private fun onPermissionGranted() {
        val openFrontCam = when {
            isProfileImage -> { true }
            else -> { false }
        }
        findNavController().navigate(R.id.cameraFragment, bundleOf(
            CameraFragment.OPEN_FRONT_CAMERA to openFrontCam
        ))
    }


    private fun observeImageFromCamera(){
        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<Uri>(CameraFragment.CAPTURED_IMAGE_URI)
            ?.observe(viewLifecycleOwner) { uri ->
                if (uri != null) {
                    startCrop(uri)
                    findNavController().currentBackStackEntry?.savedStateHandle?.set(CameraFragment.CAPTURED_IMAGE_URI, null) // Reset LiveData
                }
            }
    }

    private fun startCrop(uri: Uri) {
        checkIfFragmentAttached {
            try {
                val options = UCrop.Options().apply {
                    setHideBottomControls(false)
                    setToolbarColor(Color.BLACK)
                    setStatusBarColor(Color.BLACK)
                    setToolbarWidgetColor(Color.WHITE)
                    setActiveControlsWidgetColor(Color.BLUE)
                    setFreeStyleCropEnabled(false)
                    when {
                        isProfileImage -> {
                            withAspectRatio(4f, 4f)
                            setCircleDimmedLayer(true)
                        }
                        isPassportImage -> {
                            withAspectRatio(10f, 7f)
                        }
                        else -> {
                            setFreeStyleCropEnabled(true)
                        }
                    }
                    withMaxResultSize(1280, 720)
                }
                var uCrop = UCrop.of(uri, uri)
                uCrop = uCrop.withOptions(options)
                uCrop.start(requireContext(), this@HomeFragment)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == UCrop.REQUEST_CROP && data != null) {
                val uri = UCrop.getOutput(data)
                if (uri != null) {
                    if (isProfileImage) {
                        profileImageUri = uri
                        binding.profileImage.load(uri){
                            crossfade(true)
                            transformations(coil.transform.CircleCropTransformation())
                        }
                    } else if (isPassportImage) {
                        passportImageUri = uri
                        binding.passportImage.load(uri) {
                            crossfade(true)
                        }
                    }
                }
            }
        } else {
            Log.d("HomeFragment", "Camera/image picker returned without any data")
        }
    }
}