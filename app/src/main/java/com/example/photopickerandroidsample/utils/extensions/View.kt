package com.example.photopickerandroidsample.utils.extensions

import android.content.ContextWrapper
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity

/**
 * Ensures the context being accessed in a View can be cast to Activity.
 */
public val View.activity: FragmentActivity?
    get() {
        var context = context
        while (context is ContextWrapper) {
            if (context is FragmentActivity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

public fun View.safeClick(action: View.OnClickListener, debounceTime: Long = 1000L) {
    this.setOnClickListener(object : View.OnClickListener {
        private var lastClickTime: Long = 0

        override fun onClick(v: View) {
            if (SystemClock.elapsedRealtime() - lastClickTime < debounceTime) return else action.onClick(v)
            lastClickTime = SystemClock.elapsedRealtime()
        }
    })
}