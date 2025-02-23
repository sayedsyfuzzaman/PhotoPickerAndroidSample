package com.example.photopickerandroidsample.utils

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Creates an image file in temporary storage that is not accessible from the gallery.
 *
 * @return A temporary image file.
 * @throws IOException If an error occurs while creating the file.
 */
@Throws(IOException::class)
fun Context.createImageFile(): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
    val imageFileName = "IMAGE_" + timeStamp + "_"
    val storageDir = this.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    if (storageDir?.exists() == false) {
        storageDir.mkdirs()
    }
    return File.createTempFile(imageFileName, ".jpg", storageDir)
}