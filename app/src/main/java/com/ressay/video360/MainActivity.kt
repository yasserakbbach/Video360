package com.ressay.video360

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.coursion.freakycoder.mediapicker.galleries.Gallery
import kotlinx.android.synthetic.main.activity_main.*
import java.io.FileNotFoundException


class MainActivity : AppCompatActivity() {

    private val OPEN_MEDIA_PICKER = 1  // Request code
    private val TAG = "Video360"
    private val REQ_VID_TRIM = 3325

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initPermissions()

        video_btn_trim.setOnClickListener {

            if(isNecessaryPermissionGranted()) {

                pickVideo()
            }
        }

        video_btn_record.setOnClickListener {
            
            if(isNecessaryPermissionGranted()) {

                recordVideo()
            }
        }
    }

    private fun recordVideo() {

        startActivity(
            Intent(this@MainActivity, VideoRecordActivity::class.java)
        )
    }

    private fun initPermissions() {

        askPermission(Manifest.permission.CAMERA)
        askPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        askPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun pickVideo() {

        val intent = Intent(this, Gallery::class.java)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        intent.putExtra("title", "Select media") // Set the title for toolbar
        // Mode 1 for both images and videos selection, 2 for images only and 3 for videos!
        intent.putExtra("mode", 3)
        intent.putExtra("maxSelection", 1)
        startActivityForResult(intent, OPEN_MEDIA_PICKER)

    }

    private fun trimVideo(pathVideo : String) {

        val intent = Intent(this, VideoTrimActivity::class.java)
        intent.putExtra("video", pathVideo)
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Check which request we're responding to
        if (requestCode == OPEN_MEDIA_PICKER) {
            // Make sure the request was successful
            if (resultCode == Activity.RESULT_OK && data != null) {
                val selectionResult = data.getStringArrayListExtra("result")
                selectionResult?.forEach {
                    try {
                        /*Log.d("MyApp", "Image Path : " + it)
                        val uriFromPath = Uri.fromFile(File(it))
                        Log.d("MyApp", "Image URI : " + uriFromPath)
                        // Convert URI to Bitmap
                        val bm = BitmapFactory.decodeStream(contentResolver.openInputStream(uriFromPath))
                        image.setImageBitmap(bm)
                        //Log.d(TAG, it)*/
                        trimVideo(it)
                    } catch (e: FileNotFoundException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {

        when (requestCode) {
            REQ_VID_TRIM -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    if (isNecessaryPermissionGranted()) {

                        pickVideo()
                    }
                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
    }

    private fun askPermission(permission : String) {

        if(!isPermissionGranted(permission)) {
            ActivityCompat.requestPermissions(
                this@MainActivity,
                arrayOf(permission),
                REQ_VID_TRIM
            )
        }

    }

    private fun isPermissionGranted(permission : String) =
        ContextCompat.checkSelfPermission(this@MainActivity, permission) == PackageManager.PERMISSION_GRANTED

    private fun isNecessaryPermissionGranted() =
        isPermissionGranted(Manifest.permission.CAMERA) && isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                && isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)
}