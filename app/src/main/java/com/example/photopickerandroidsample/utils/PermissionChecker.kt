package com.example.photopickerandroidsample.utils

import android.Manifest
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.photopickerandroidsample.R
import com.example.photopickerandroidsample.utils.extensions.activity
import com.example.photopickerandroidsample.utils.extensions.dpToPxPrecise
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.permissionx.guolindev.PermissionX

private const val SNACKBAR_ELEVATION_IN_DP = 20

public class PermissionChecker {
    /**
     * Builds an [Array] of the required permissions for accessing visual media, based on the Android version.
     */
    private fun mediaPermission(): String=
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13
            READ_MEDIA_IMAGES
        } else {
            // Android 12 and below
            READ_EXTERNAL_STORAGE
        }

    public fun isGrantedCameraPermissions(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

    public fun isGrantedMediaPermissions(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                ContextCompat.checkSelfPermission(
                    context, mediaPermission()
                ) == PackageManager.PERMISSION_GRANTED


    /**
     * Check if Camera Permission needs to be requested to the user
     *
     * @param context of the App
     *
     * @return True if [Manifest.permission.CAMERA] is present on the App Manifest and user didn't grant it,
     * False in another case
     */
    public fun isNeededToRequestForCameraPermissions(context: Context): Boolean =
        context.isPermissionDeclared(Manifest.permission.CAMERA) && !isGrantedCameraPermissions(
            context
        )

    public fun isNeededToRequestForMediaPermissions(context: Context): Boolean =
        context.isPermissionDeclared(
            mediaPermission()
        ) && !isGrantedMediaPermissions(context)


    public fun checkCameraPermissions(
        view: View,
        onPermissionDenied: () -> Unit = { },
        onPermissionGranted: () -> Unit = {},
    ) {
        checkPermissions(
            view,
            view.context.getString(R.string.permission_camera_title),
            view.context.getString(R.string.permission_camera_message),
            view.context.getString(R.string.permission_camera_message),
            listOf(Manifest.permission.CAMERA),
            onPermissionDenied,
            onPermissionGranted,
        )
    }

    public fun checkMediaPermissions(
        view: View,
        onPermissionDenied: () -> Unit = {},
        onPermissionGranted: () -> Unit = {},
    ) {
        checkPermissions(
            view,
            view.context.getString(R.string.permission_storage_title),
            view.context.getString(R.string.permission_storage_message),
            view.context.getString(R.string.permission_storage_message),
            listOf(mediaPermission()),
            onPermissionDenied,
            onPermissionGranted,
        )
    }

    @Suppress("LongParameterList")
    private fun checkPermissions(
        view: View,
        dialogTitle: String,
        dialogMessage: String,
        snackbarMessage: String,
        permissions: List<String>,
        onPermissionDenied: () -> Unit,
        onPermissionGranted: () -> Unit,
    ) {
        val activity = view.activity ?: return

        PermissionX.init(activity)
            .permissions(permissions)
            .onExplainRequestReason { _, _ ->
                showPermissionRationaleDialog(view.context, dialogTitle, dialogMessage)
            }
            .onForwardToSettings { _, _ ->
                showPermissionDeniedSnackbar(view, snackbarMessage)
            }
            .request { allGranted, _, _ ->
                if (allGranted) onPermissionGranted() else onPermissionDenied()
            }
    }

    /**
     * Shows permission rationale dialog.
     *
     * @param context The context to show alert dialog.
     * @param dialogTitle The title of the dialog.
     * @param dialogMessage The message to display.
     */
    private fun showPermissionRationaleDialog(
        context: Context,
        dialogTitle: String,
        dialogMessage: String,
    ) {
        AlertDialog.Builder(context)
            .setTitle(dialogTitle)
            .setMessage(dialogMessage)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Shows a [Snackbar] whenever a permission has been denied.
     *
     * @param view The anchor view for the Snackbar.
     * @param snackbarMessage The message displayed in the Snackbar.
     */
    private fun showPermissionDeniedSnackbar(
        view: View,
        snackbarMessage: String,
    ) {
        Snackbar.make(view, snackbarMessage, Snackbar.LENGTH_LONG).apply {
            setAction(R.string.permissions_setting_button) {
                context.openSystemSettings()
            }
            addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onShown(sb: Snackbar?) {
                    super.onShown(sb)
                    sb?.view?.elevation = SNACKBAR_ELEVATION_IN_DP.dpToPxPrecise()
                }
            })
            show()
        }
    }
}