package com.powersync.androidexample.ui

import android.net.Uri
import android.os.Environment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.powersync.androidexample.MainActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A very basic camera service. This should not be used in production.
 */
class CameraService(val activity: MainActivity) {
    private var currentPhotoUri: Uri? = null
    private var pictureResult: CompletableDeferred<ByteArray>? = null
    private var file: File? = null
    private val mutex = Mutex()

    private val takePictureLauncher = activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && currentPhotoUri != null) {
            activity.contentResolver.openInputStream(currentPhotoUri!!)?.use {
                pictureResult!!.complete(it.readBytes())
            }
            file!!.delete()

        } else {
            pictureResult!!.completeExceptionally(Exception("Could not capture photo"))
        }

        file = null
        currentPhotoUri = null
        pictureResult = null
    }

    suspend fun takePicture(): ByteArray = mutex.withLock {
        pictureResult = CompletableDeferred<ByteArray>()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        file = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        currentPhotoUri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file!!)

        takePictureLauncher.launch(currentPhotoUri!!)

        return pictureResult!!.await()
    }
}