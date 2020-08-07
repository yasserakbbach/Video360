package com.ressay.video360

import android.app.Dialog
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.video.trimmer.interfaces.OnTrimVideoListener
import kotlinx.android.synthetic.main.activity_video_trim.*
import java.io.File
import java.util.concurrent.TimeUnit

class VideoTrimActivity : AppCompatActivity(), OnTrimVideoListener {

    private val TAG = "VideoTrimActivity"
    private val MAX_VIDEO_SEC = 30
    private lateinit var trimProgressDialog : Dialog
    private lateinit var TRIM_VIDEO_CACHE_DIR : String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_trim)

        val pathVideo = intent?.extras?.get("video").toString()
        TRIM_VIDEO_CACHE_DIR = externalCacheDir!!.absolutePath + File.separator + "temp" + File.separator + "Videos" + File.separator

        clearOldCache()
        setUpVideoTrimmer(pathVideo)
    }

    private fun clearOldCache() {

        val dir = File(TRIM_VIDEO_CACHE_DIR)
        if (dir.isDirectory) {
            val children: Array<String> = dir.list()!!
            for (i in children.indices) {
                File(dir, children[i]).delete()
            }
        }
    }

    override fun cancelAction() {}

    override fun getResult(uri: Uri) {
        chunkVideo(uri.path!!)
    }

    override fun onError(message: String) {
        Log.d(TAG, "onError: $message")
    }

    override fun onTrimStarted() {

        trimProgressDialog = Dialog(this@VideoTrimActivity)
        trimProgressDialog.setContentView(R.layout.dialog_trim_video_progress)
        trimProgressDialog.setCancelable(false)
        trimProgressDialog.show()
    }

    private fun setUpVideoTrimmer(pathVideo : String) {

        videoTrimmer.setOnTrimVideoListener(this)
                    .setVideoURI(Uri.parse(pathVideo))
                    .setVideoInformationVisibility(true)
                    .setMaxDuration(MAX_VIDEO_SEC)
                    .setMinDuration(MAX_VIDEO_SEC)
                    .setDestinationPath(TRIM_VIDEO_CACHE_DIR)

        save.setOnClickListener {
            videoTrimmer.onSaveClicked()
        }
    }

    private fun chunkVideo(pathVideo: String) {

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(pathVideo)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        //Log.d(TAG, "chunkVideo: $duration")
        for(i in 0..duration step (duration / MAX_VIDEO_SEC)) {
            Log.d(TAG, "chunkVideo: $i")
        }
        //retriever.getFrameAtTime()
    }

}