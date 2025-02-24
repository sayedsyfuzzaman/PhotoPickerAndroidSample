package com.example.photopickerandroidsample

import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import coil.load
import com.example.photopickerandroidsample.databinding.FragmentHomeBinding
import com.example.photopickerandroidsample.methodselection.MethodSelectionDialogFragment

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
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


        observeMedia()

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
            openMediaPicker()
        }
        binding.addProfileButton.setOnClickListener {
            isProfileImage = true
            isPassportImage = false
            openMediaPicker()
        }
    }

    private fun openMediaPicker() {
        val dialog = MethodSelectionDialogFragment().apply {
            arguments = Bundle().apply {
                putBoolean(MethodSelectionDialogFragment.ARG_IS_PROFILE_IMAGE, isProfileImage)
                putBoolean(MethodSelectionDialogFragment.ARG_IS_PASSPORT_IMAGE, isPassportImage)
            }
        }
        dialog.show(childFragmentManager, "MethodSelectionDialog")
    }

    private fun observeMedia(){
        childFragmentManager.setFragmentResultListener(
            MethodSelectionDialogFragment.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            val uriString = bundle.getString(MethodSelectionDialogFragment.RESULT_IMAGE_URI)
            uriString?.let { uri ->
                if (isProfileImage) {
                    profileImageUri = uri.toUri()
                    binding.profileImage.load(uri) {
                        crossfade(true)
                        transformations(coil.transform.CircleCropTransformation())
                    }
                } else if (isPassportImage) {
                    passportImageUri = uri.toUri()
                    binding.passportImage.load(uri) {
                        crossfade(true)
                    }
                }
            }
        }
    }
}