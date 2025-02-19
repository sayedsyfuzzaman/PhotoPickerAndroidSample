package com.example.photopickerandroidsample.utils.extensions

import android.content.res.Resources
import android.util.DisplayMetrics

/**
 * Uses the display metrics to transform the value of DP to pixels.
 */
public fun Int.dpToPxPrecise(): Float = (this * displayMetrics().density)

/**
 * Fetches the current system display metrics based on [Resources].
 */
internal fun displayMetrics(): DisplayMetrics = Resources.getSystem().displayMetrics