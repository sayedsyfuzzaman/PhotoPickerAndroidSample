package com.example.photopickerandroidsample.utils

import android.content.Context
import android.content.pm.PackageManager

/**
 * Returns if we need to check for the given permission or not.
 *
 * @param permission The permission to check.
 * @return If the given permission is declared in the manifest or not.
 */
public fun Context.isPermissionDeclared(permission: String): Boolean {
    return packageManager
        .getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        .requestedPermissions
        ?.contains(permission) == true
}


