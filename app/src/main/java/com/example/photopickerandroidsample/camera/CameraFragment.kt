package com.example.photopickerandroidsample.camera

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.example.photopickerandroidsample.R
import com.example.photopickerandroidsample.databinding.FragmentCameraBinding
import com.example.photopickerandroidsample.methodselection.MethodSelectionDialogFragment.Companion.CAPTURED_IMAGE
import com.example.photopickerandroidsample.utils.extensions.safeClick
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.example.photopickerandroidsample.utils.createImageFile

class CameraFragment : AppCompatActivity() {
    private lateinit var binding: FragmentCameraBinding

    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private lateinit var preview: Preview
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeFocusCircle: View? = null
    private lateinit var orientationEventListener: OrientationEventListener
    private var deviceRotation: Int = Surface.ROTATION_0
    private lateinit var cameraExecutor: ExecutorService
    private var openFrontCamArgs: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = FragmentCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        openFrontCamArgs = intent.extras?.getBoolean(OPEN_FRONT_CAMERA)

        cameraExecutor = Executors.newSingleThreadExecutor()

        binding.viewFinder.post {
            updateCameraUiControls()
            lifecycleScope.launch { setUpCamera() }
        }
    }

    /**
     * Sets up the camera UI, including buttons and listeners.
     */
    private fun updateCameraUiControls(){
        binding.cameraSwitchButton.let {
            it.isEnabled = false
            it.safeClick( {
                lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
                // Re-bind use cases to update selected camera
                bindCameraUseCases()
            })
        }

        binding.cameraCaptureButton.safeClick ({
            captureAndSavePhoto()
        })

        binding.viewFinder.setOnTouchListener { view, event ->
            view?.let {
                if (event.action == MotionEvent.ACTION_UP) {
                    view.performClick()
                    val factory = binding.viewFinder.meteringPointFactory
                    val point = factory.createPoint(event.x, event.y)

                    val action = FocusMeteringAction.Builder(point)
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build()

                    camera?.cameraControl?.startFocusAndMetering(action)

                    showFocusCircle(event.x, event.y)
                }
            }
            true
        }

        binding.cameraCloseButton.safeClick( {
            this.finish()
        })
        orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                val newRotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270 // Left landscape
                    in 135..224 -> Surface.ROTATION_180 // Upside down
                    in 225..314 -> Surface.ROTATION_90 // Right landscape
                    else -> Surface.ROTATION_0 // Portrait
                }

                if (newRotation != deviceRotation) {
                    updateCameraRotation(newRotation)
                    rotateButtons(newRotation)
                }
            }
        }

        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }
    }

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private suspend fun setUpCamera() {
        cameraProvider = ProcessCameraProvider.getInstance(this).await()

        // Select lensFacing depending on the available cameras
        lensFacing = when {
            hasFrontCamera() && openFrontCamArgs == true -> CameraSelector.LENS_FACING_FRONT
            hasBackCamera() && openFrontCamArgs == false -> CameraSelector.LENS_FACING_BACK
            else -> throw IllegalStateException("Back and front camera are unavailable")
        }

        // Enable or disable switching between cameras
        updateCameraSwitchButton()

        // Build and bind the camera use cases
        bindCameraUseCases()
    }

    /**
     * Binds the camera use cases for preview and image capture.
     */
    private fun bindCameraUseCases() {
        val screenAspectRatio = aspectRatio()
        val rotation = binding.viewFinder.display.rotation
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        preview = Preview.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .build()

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(rotation)
            .build()

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        camera = cameraProvider.bindToLifecycle(
            this, cameraSelector, preview, imageCapture
        ).apply {
            removeCameraStateObservers(this.cameraInfo)
            try {
                preview.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                observeCameraState(this.cameraInfo)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }
    }

    /**
     * Captures a photo and saves it to external storage.
     */
    private fun captureAndSavePhoto() {
        imageCapture?.let { imageCapture ->
            try {
                val photoFile = this.createImageFile()
                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                imageCapture.takePicture(
                    outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exc: ImageCaptureException) {
                            Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                            handleCriticalError()
                        }

                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            Log.d(TAG, "Photo capture succeeded: ${output.savedUri}")

                            // Send the result back
                            val resultIntent = Intent().apply {
                                putExtra(CAPTURED_IMAGE, output.savedUri.toString())
                            }
                            setResult(Activity.RESULT_OK, resultIntent)
                            finish() // Close the camera activity
                        }
                    })
            } catch (e: IOException) {
                Log.e(TAG, "Failed to create image file", e)
                handleCriticalError()
            }

            // Display flash animation to indicate that photo was captured
            binding.root.postDelayed({
                binding.root.foreground = ColorDrawable(Color.BLACK)
                binding.root.postDelayed(
                    { binding.root.foreground = null }, ANIMATION_FAST_MILLIS
                )
            }, ANIMATION_SLOW_MILLIS)
        }
    }

    /**
     * Observes and handles camera state changes.
     */
    private fun observeCameraState(cameraInfo: CameraInfo) {
        cameraInfo.cameraState.observe(this) { cameraState ->
            run {
                when (cameraState.type) {
                    CameraState.Type.PENDING_OPEN -> {
                        // Ask the user to close other camera apps
                        Log.d(TAG, "CameraState: Pending Open")
                    }
                    CameraState.Type.OPENING -> {
                        // Show the Camera UI
                        Log.d(TAG, "CameraState: Opening")
                    }
                    CameraState.Type.OPEN -> {
                        // Setup Camera resources and begin processing
                        Log.d(TAG, "CameraState: Open")
                    }
                    CameraState.Type.CLOSING -> {
                        // Close camera UI
                        Log.d(TAG, "CameraState: Closing")
                    }
                    CameraState.Type.CLOSED -> {
                        // Free camera resources
                        Log.d(TAG, "CameraState: Closed")
                    }
                }
            }

            cameraState.error?.let { error ->
                when (error.code) {
                    // Open errors
                    CameraState.ERROR_STREAM_CONFIG -> {
                        // Make sure to setup the use cases properly
                        Log.d(TAG, "CameraState: Error Stream Config")
                    }
                    // Opening errors
                    CameraState.ERROR_CAMERA_IN_USE -> {
                        // Close the camera or ask user to close another camera app that's using the
                        // camera
                        Log.d(TAG, "CameraState: Error Camera In Use")
                    }
                    CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                        // Close another open camera in the app, or ask the user to close another
                        // camera app that's using the camera
                        Log.d(TAG, "CameraState: Error Max Cameras In Use")
                    }
                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {
                        Log.d(TAG, "CameraState: Error Other Recoverable Error")
                    }
                    // Closing errors
                    CameraState.ERROR_CAMERA_DISABLED -> {
                        // Ask the user to enable the device's cameras
                        Log.d(TAG, "CameraState: Error Camera Disabled")
                    }
                    CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                        // Ask the user to reboot the device to restore camera function
                        Log.d(TAG, "CameraState: Error Camera Fatal Error")
                    }
                    // Closed errors
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
                        // Ask the user to disable the "Do Not Disturb" mode, then reopen the camera
                        Log.d(TAG, "CameraState: Error Do Not Disturb Mode Enabled")
                    }
                }
            }
        }
    }

    /**
     * Rotates UI buttons based on device orientation.
     */
    private fun rotateButtons(rotation: Int) {
        val rotationAngle = when (rotation) {
            Surface.ROTATION_90 -> 90f
            Surface.ROTATION_180 -> 180f
            Surface.ROTATION_270 -> 90f
            else -> 0f
        }

        binding.cameraSwitchButton.animate()
            .rotation(rotationAngle)
            .setDuration(300)
            .setInterpolator(LinearInterpolator())
            .start()

        binding.cameraCloseButton.animate()
            .rotation(rotationAngle)
            .setDuration(300)
            .setInterpolator(LinearInterpolator())
            .start()
    }

    /**
     * Updates the camera rotation when the device orientation changes.
     *
     * @param rotation The new device rotation angle
     */
    private fun updateCameraRotation(rotation: Int) {
        deviceRotation = rotation
        imageCapture?.targetRotation = deviceRotation
    }

    /**
     * Displays a focus animation when the user taps to focus on the viewfinder.
     */
    private fun showFocusCircle(x: Float, y: Float) {
        // Remove existing focus circle if present
        activeFocusCircle?.let {
            binding.viewFinder.removeView(it)
        }

        val focusCircle = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(100, 100)
            background = ResourcesCompat.getDrawable(resources, R.drawable.focus_circle, theme)
            alpha = 0f // Start invisible to prevent (0,0) flickering issue
        }

        activeFocusCircle = focusCircle

        // Post ensures layout measurement is done BEFORE adding the view
        binding.viewFinder.post {
            binding.viewFinder.addView(focusCircle) // Now it's added only after measuring

            // Ensure correct position after it's measured
            focusCircle.x = x - focusCircle.width / 2f // Center horizontally
            focusCircle.y = y - focusCircle.height / 2f // Center vertically

            // Scale and fade animation
            val scaleX = ObjectAnimator.ofFloat(focusCircle, View.SCALE_X, 1f, 2f)
            val scaleY = ObjectAnimator.ofFloat(focusCircle, View.SCALE_Y, 1f, 2f)
            val alpha = ObjectAnimator.ofFloat(focusCircle, View.ALPHA, 1f, 0f)

            AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha)
                duration = 500
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        binding.viewFinder.removeView(focusCircle) // Remove after animation
                        if (activeFocusCircle == focusCircle) {
                            activeFocusCircle = null // Clear reference after removal
                        }
                    }
                })
                start()
            }
        }
    }

    /**
     * Updates the camera switch button state based on available cameras.
     */
    private fun updateCameraSwitchButton() {
        binding.cameraSwitchButton.isEnabled = hasBackCamera() && hasFrontCamera()
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    /**
     *  Currently it has value of 4:3 customize this if needed.
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(): Int {
        return AspectRatio.RATIO_4_3
    }

    /**
     * Removes all camera state observers to prevent memory leaks.
     *
     * @param cameraInfo The CameraInfo object whose observers should be removed.
     */
    private fun removeCameraStateObservers(cameraInfo: CameraInfo) {
        cameraInfo.cameraState.removeObservers(this)
    }

    /**
     * Handles critical errors by displaying a toast message and navigating back to the previous screen.
     * This ensures UI-related actions run on the main thread.
     *
     * @param errorMsg Optional error message to be displayed; defaults to a generic error message.
     */
    private fun handleCriticalError(errorMsg: String?= null) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, errorMsg ?: "Something went wrong!", Toast.LENGTH_SHORT).show()
            this.finish()
        }
    }

    /**
     * Cleans up resources when fragment is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        orientationEventListener.disable()
        this.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    companion object{
        private const val TAG = "CameraFragment"

        /** Milliseconds used for UI animations */
        private const val ANIMATION_FAST_MILLIS = 50L
        private const val ANIMATION_SLOW_MILLIS = 100L

        const val OPEN_FRONT_CAMERA = "OpenFrontCamera"
    }
}