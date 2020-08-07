package com.ressay.video360

import android.app.Dialog
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.androidnetworking.AndroidNetworking
import com.androidnetworking.common.Priority
import com.androidnetworking.error.ANError
import com.androidnetworking.interfaces.JSONObjectRequestListener
import com.iammert.library.cameravideobuttonlib.CameraVideoButton
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.default
import id.zelory.compressor.constraint.destination
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import kotlinx.android.synthetic.main.activity_video_record.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.lang.Runnable
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VideoRecordActivity : AppCompatActivity(), CameraVideoButton.ActionListener {

    private val MAX_VIDEO_MILLISEC = 30_000L
    private val MAX_VIDEO_SNAPSHOTS = 30
    private val QUALITY_PICTURE = 75
    private val DELAY_SNAPSHOT = 700L // 0.7s
    private val DELAY_GATHERING_PICS = 5000L // 5s
    private val COMPRESS_TYPE = Bitmap.CompressFormat.JPEG
    private val URL_VIDEO_360 = "https://www.beta.cdma-solution.ma/video360"
    private var takenPicsBas64 = ArrayList<String>()
    private lateinit var PATH_CACHE_PICS : String

    private lateinit var compressPicsJob : Job
    private lateinit var dialogUploadImages : Dialog

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private lateinit var cameraSelector : CameraSelector
    private lateinit var cameraProvider : ProcessCameraProvider
    private var isRecording = false

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
//        private const val REQUEST_CODE_PERMISSIONS = 10
//        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_record)

        init()
        startCamera()
    }

    override fun onStartRecord() {

        isRecording = true
        GlobalScope.launch(Dispatchers.IO) {
            do {
                Log.d(TAG, "[$isRecording] Taking picture...")
                if(isRecording) {
                    takePhoto()
                }
                delay(DELAY_SNAPSHOT)
            }while(isRecording)
        }
    }

    override fun onEndRecord() {

        isRecording = false
        GlobalScope.launch(Dispatchers.IO) {
            promptUploadToServer()
        }
    }

    override fun onDurationTooShortError() {}

    override fun onSingleTap() {}

    private fun init() {

        video_record_btn.enablePhotoTaking(false)
        video_record_btn.setVideoDuration(MAX_VIDEO_MILLISEC)
        video_record_btn.enableVideoRecording(true)
        video_record_btn.actionListener = this

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
        cleanOldPics()

        dialogUploadImages = Dialog(this@VideoRecordActivity)
    }

    private fun getOutputDirectory() : File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply {
                mkdirs()
            }
        }

        return if(mediaDir != null && mediaDir.exists()){
            PATH_CACHE_PICS = mediaDir.absolutePath
            mediaDir
        } else {
            PATH_CACHE_PICS = filesDir.absolutePath
            filesDir
        }
    }

    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            // Preview
            preview = Preview.Builder()
                             .build()

            imageCapture = ImageCapture.Builder()
                                       .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                       .build()

            // Select back camera
            cameraSelector = CameraSelector.Builder()
                                           .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                                           .build()

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture, preview)
                preview?.setSurfaceProvider(viewFinder.createSurfaceProvider())
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create timestamped output file to hold the image
        val photoFile = File(outputDirectory, "${SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis())}.jpg")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions
                                        .Builder(photoFile)
                                        .build()

        // Setup image capture listener which is triggered after photo has been taken
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.d(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    compressPicsJob = GlobalScope.launch(Dispatchers.IO) {
                        compressPics(savedUri.path!!)
                    }

                    Log.d(TAG, msg)
                }
            })
    }

    private suspend fun compressPics(pathImg : String) {

        /*val compressedPic = */
        Compressor.compress(applicationContext, File(pathImg)) {
            default()
            destination(File(pathImg))
            format(COMPRESS_TYPE)
            quality(QUALITY_PICTURE)
        }

        /*takenPicsBas64.add( TODO: encode it this way
            Base64.encodeToString(compressedPic.readBytes(), Base64.DEFAULT)
        )*/
    }

    private fun promptUploadToServer() {

        showProgressUpload()

        compressPicsJob.invokeOnCompletion {

            SystemClock.sleep(DELAY_GATHERING_PICS) // Hope this fucking delay works -_-

//            dialogUploadImages.dismiss()
            GlobalScope.launch(Dispatchers.Main) {
                val dialog = Dialog(this@VideoRecordActivity)
                dialog.setCancelable(false)
                dialog.setContentView(R.layout.dialog_confirm_video_snapshots)
                dialog.findViewById<TextView>(R.id.confirm_video_snapshots)
                    .text = getString(R.string.confirm_video_snapshots, countTakenPics())
                dialog.findViewById<Button>(R.id.yes_confirm_video_snapshots).setOnClickListener {
                    dialog.dismiss()
                    showProgressUpload()
                    encodeTakenPics()
                    uploadToServer()
                }
                dialog.findViewById<Button>(R.id.no_confirm_video_snapshots).setOnClickListener {
                    cleanOldPics()
                    dialog.dismiss()
                }
                dialog.show()
            }
        }
    }

    private fun showProgressUpload() {

        GlobalScope.launch(Dispatchers.Main) {
            dialogUploadImages.setContentView(R.layout.dialog_trim_video_progress)
            dialogUploadImages.setCancelable(false)
            dialogUploadImages.show()
        }
    }

    private fun uploadToServer() {

        AndroidNetworking.post(URL_VIDEO_360)
            .addBodyParameter("images", JSONArray(takenPicsBas64).toString())
            .setPriority(Priority.HIGH)
            .build()
            .getAsJSONObject(object : JSONObjectRequestListener{
                override fun onResponse(response: JSONObject?) { // TODO: remember the server sends invalid json
                    Log.d(TAG, "Check your server!")
                    GlobalScope.launch(Dispatchers.Main) {
                        dialogUploadImages.dismiss()
                        finish()
                    }
                }

                override fun onError(anError: ANError?) {
                    Log.d(TAG, "onError: ${anError?.message}")
                    GlobalScope.launch(Dispatchers.Main) {
                        dialogUploadImages.dismiss()
                    }
                }

            })
    }

    private fun cleanOldPics() {

        val dir = File(outputDirectory.path)
        if (dir.isDirectory) {
            val children: Array<String> = dir.list()!!
            for (i in children.indices) {
                File(dir, children[i]).delete()
            }
        }
        takenPicsBas64.clear()
    }

    private fun countTakenPics() = File(outputDirectory.path).list()?.size.toString()
    
    private fun encodeTakenPics() {

        val dir = File(outputDirectory.path)
        if (dir.isDirectory) {
            val children: Array<String> = dir.list()!!
            for (i in children.indices) {
                takenPicsBas64.add(
                    Base64.encodeToString(File(dir, children[i]).readBytes(), Base64.DEFAULT)
                )
            }
        }

        Log.d(TAG, "encodeTakenPics: ${takenPicsBas64.size}")
    }
}