package com.example.photopickerandroidsample.utils.extensions

import android.content.Context
import androidx.fragment.app.Fragment

public inline fun <T> Fragment.checkIfFragmentAttached(operation: Context.() -> T?): T? {
    if (isAdded && context != null) {
        return operation(requireContext())
    }
    return null
}
