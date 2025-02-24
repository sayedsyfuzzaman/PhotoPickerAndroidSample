package com.example.photopickerandroidsample.methodselection

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.example.photopickerandroidsample.camera.CameraFragment
import com.example.photopickerandroidsample.camera.CameraFragment.Companion.OPEN_FRONT_CAMERA
import com.example.photopickerandroidsample.databinding.FragmentMethodSelectionDialogBinding
import com.example.photopickerandroidsample.utils.PermissionChecker
import com.example.photopickerandroidsample.utils.createImageFile
import com.example.photopickerandroidsample.utils.extensions.checkIfFragmentAttached
import com.example.photopickerandroidsample.utils.extensions.safeClick
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.yalantis.ucrop.UCrop

class MethodSelectionDialogFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentMethodSelectionDialogBinding? = null
    private val binding get() = _binding!!
    private val permissionChecker by lazy { PermissionChecker() }
    private var isProfileImage: Boolean = false
    private var isPassportImage: Boolean = false

    private val mediaLauncher = registerForActivityResult(PickVisualMedia()) { uri ->
        uri?.let {
            startCrop(it)
        } ?: run {
            Log.d("MethodSelectionDialogFragment","Camera/video picker returned without any data")
        }
    }

    private val galleryResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let {  uri ->
                startCrop(uri, replaceFile = false)
            }
        } else {
            Log.d("MethodSelectionDialogFragment","Gallery launcher returned without any data")
        }
    }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val capturedUri = result.data?.getStringExtra(CAPTURED_IMAGE)
                Log.d("MethodSelectionDialogFragment", "Captured Image URI: $capturedUri")
                capturedUri?.let {
                    startCrop(it.toUri(), replaceFile = true)
                }
            }
        }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMethodSelectionDialogBinding.inflate(layoutInflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        isProfileImage = arguments?.getBoolean(ARG_IS_PROFILE_IMAGE) ?: false
        isPassportImage = arguments?.getBoolean(ARG_IS_PASSPORT_IMAGE) ?: false

        binding.galleryButton.safeClick({
            checkMediaPermission()
        })

        binding.photoButton.safeClick({
            checkCameraPermissions()
        })

    }

    private fun checkCameraPermissions() {
        if (permissionChecker.isNeededToRequestForCameraPermissions(requireContext())) {
            permissionChecker.checkCameraPermissions(
                binding.root,
                onPermissionGranted = ::openCamera,
            )
            return
        }
        openCamera()
    }

    private fun checkMediaPermission() {
        if (PickVisualMedia.isPhotoPickerAvailable(requireContext())) {
            openVisualMediaLauncher()
        } else {
            openGalleryIntent()
        }
    }

    private fun openCamera(){
        val openFrontCam = when {
            isProfileImage -> { true }
            else -> { false }
        }

        val intent = Intent(requireContext(), CameraFragment::class.java)
        intent.putExtra(OPEN_FRONT_CAMERA, openFrontCam)
        cameraLauncher.launch(intent)
    }

    private fun openVisualMediaLauncher(){
        mediaLauncher.launch(PickVisualMediaRequest(ImageOnly))
    }

    private fun openGalleryIntent(){
        galleryResultLauncher.launch(
            Intent(
                Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            )
        )
    }

    private fun startCrop(uri: Uri, replaceFile: Boolean = false) {
        checkIfFragmentAttached {
            try {
                val options = UCrop.Options().apply {
                    setHideBottomControls(true)
                    setToolbarColor(Color.BLACK)
                    setStatusBarColor(Color.BLACK)
                    setToolbarWidgetColor(Color.WHITE)
//                    setActiveControlsWidgetColor(Color.BLUE)
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
                var uCrop = UCrop.of(uri, if (replaceFile) uri else Uri.fromFile(requireContext().createImageFile()))
                uCrop = uCrop.withOptions(options)
                uCrop.start(requireContext(), this@MethodSelectionDialogFragment)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && requestCode == UCrop.REQUEST_CROP && data != null) {
            val uri = UCrop.getOutput(data)
            if (uri != null) {
                // Send the result back using the Fragment Result API.
                setFragmentResult(
                    REQUEST_KEY,
                    bundleOf(RESULT_IMAGE_URI to uri.toString())
                )
                dismiss()
            }
        } else {
            Log.d("MethodSelectionDialogFragment", "Camera/image picker returned without any data")
        }
    }


    companion object {
        const val REQUEST_KEY = "MethodSelectionDialogFragment.REQUEST_KEY"
        const val ARG_IS_PROFILE_IMAGE = "ARG_IS_PROFILE_IMAGE"
        const val ARG_IS_PASSPORT_IMAGE = "ARG_IS_PASSPORT_IMAGE"
        const val RESULT_IMAGE_URI = "RESULT_IMAGE_URI"
        const val CAPTURED_IMAGE = "captured_image_uri"
    }
}