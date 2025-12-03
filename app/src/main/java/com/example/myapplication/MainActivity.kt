package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderListener
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import com.otaliastudios.transcoder.strategy.RemoveTrackStrategy
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var selectVideoButton: Button
    private lateinit var videoInfoTextView: TextView
    private lateinit var videoView: VideoView
    private lateinit var compressButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var postCompressionLayout: LinearLayout
    private lateinit var saveButton: Button
    private lateinit var shareButton: Button
    private lateinit var replaceButton: Button
    private lateinit var compressionOptionsRadioGroup: RadioGroup

    private val selectedVideoUris = mutableListOf<Uri>()
    private var compressedVideoPath: String? = null
    private val STORAGE_PERMISSION_CODE = 101
    private var totalVideosToCompress = 0
    private var currentVideoIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        selectVideoButton = findViewById(R.id.select_video_button)
        videoInfoTextView = findViewById(R.id.video_info_textview)
        videoView = findViewById(R.id.video_view)
        compressButton = findViewById(R.id.compress_button)
        progressBar = findViewById(R.id.progress_bar)
        postCompressionLayout = findViewById(R.id.post_compression_layout)
        saveButton = findViewById(R.id.save_button)
        shareButton = findViewById(R.id.share_button)
        replaceButton = findViewById(R.id.replace_button)
        compressionOptionsRadioGroup = findViewById(R.id.compression_options_radiogroup)

        requestStoragePermission()

        selectVideoButton.setOnClickListener {
            selectVideo()
        }

        compressButton.setOnClickListener {
            if (selectedVideoUris.isNotEmpty()) {
                compressVideos()
            } else {
                videoInfoTextView.text = "请先选择一个或多个视频"
            }
        }

        saveButton.setOnClickListener {
            Toast.makeText(this, "视频已保存至 $compressedVideoPath", Toast.LENGTH_SHORT).show()
        }

        shareButton.setOnClickListener {
            shareVideo()
        }

        replaceButton.setOnClickListener {
            showReplaceConfirmationDialog()
        }
    }

    private fun selectVideo() {
        postCompressionLayout.visibility = View.GONE
        compressButton.visibility = View.VISIBLE
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        selectVideoLauncher.launch(intent)
    }

    private val selectVideoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedVideoUris.clear()
            result.data?.let { data ->
                if (data.clipData != null) {
                    val clipData = data.clipData!!
                    for (i in 0 until clipData.itemCount) {
                        selectedVideoUris.add(clipData.getItemAt(i).uri)
                    }
                } else if (data.data != null) {
                    selectedVideoUris.add(data.data!!)
                }
            }

            if (selectedVideoUris.isNotEmpty()) {
                videoInfoTextView.text = "已选择 ${selectedVideoUris.size} 个视频"
                if (selectedVideoUris.size == 1) {
                    videoView.setVideoURI(selectedVideoUris.first())
                    videoView.start()
                    videoView.visibility = View.VISIBLE
                } else {
                    videoView.visibility = View.GONE
                }
            }
        }
    }

    private fun compressVideos() {
        if (selectedVideoUris.isEmpty()) {
            videoInfoTextView.text = "请先选择视频"
            return
        }
        totalVideosToCompress = selectedVideoUris.size
        currentVideoIndex = 0
        compressNextVideo()
    }

    private fun compressNextVideo() {
        if (currentVideoIndex >= totalVideosToCompress) {
            runOnUiThread {
                progressBar.visibility = View.GONE
                videoInfoTextView.text = "所有视频压缩完成！"
                postCompressionLayout.visibility = if (totalVideosToCompress == 1) View.VISIBLE else View.GONE
                compressButton.visibility = View.VISIBLE
            }
            return
        }

        val videoUri = selectedVideoUris[currentVideoIndex]
        val destinationDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "CompressedVideos")
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        val destinationPath = File(destinationDir, "compressed_${System.currentTimeMillis()}.mp4")

        val videoStrategy = when (compressionOptionsRadioGroup.checkedRadioButtonId) {
            R.id.high_quality_radiobutton -> DefaultVideoStrategy.atMost(720).build()
            R.id.medium_quality_radiobutton -> DefaultVideoStrategy.atMost(480).build()
            R.id.low_quality_radiobutton -> DefaultVideoStrategy.atMost(360).build()
            else -> DefaultVideoStrategy.atMost(480).build()
        }

        Transcoder.into(destinationPath.path)
            .addDataSource(this, videoUri)
            .setVideoTrackStrategy(videoStrategy)
            .setAudioTrackStrategy(RemoveTrackStrategy())
            .setListener(object : TranscoderListener {
                override fun onTranscodeProgress(progress: Double) {
                    runOnUiThread {
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = (progress * 100).toInt()
                        videoInfoTextView.text = "正在压缩视频 ${currentVideoIndex + 1} / $totalVideosToCompress..."
                    }
                }

                override fun onTranscodeCompleted(successCode: Int) {
                    if (totalVideosToCompress == 1) {
                        compressedVideoPath = destinationPath.path
                    }
                    currentVideoIndex++
                    compressNextVideo()
                }

                override fun onTranscodeCanceled() {
                    currentVideoIndex++
                    compressNextVideo()
                }

                override fun onTranscodeFailed(exception: Throwable) {
                    currentVideoIndex++
                    compressNextVideo()
                }
            }).transcode()
    }


    private fun shareVideo() {
        compressedVideoPath?.let {
            val videoFile = File(it)
            val videoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                videoFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, videoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享视频"))
        }
    }

    private fun showReplaceConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("替换视频")
            .setMessage("您确定要删除原始视频并保留压缩版本吗？")
            .setPositiveButton("是") { _, _ ->
                replaceVideo()
            }
            .setNegativeButton("否", null)
            .show()
    }

    private fun replaceVideo() {
        if (selectedVideoUris.size == 1) {
            selectedVideoUris.first()?.let {
                try {
                    contentResolver.delete(it, null, null)
                    Toast.makeText(this, "原始视频已替换", Toast.LENGTH_SHORT).show()
                    videoInfoTextView.text = "原始视频已替换。压缩视频位于: $compressedVideoPath"
                } catch (e: SecurityException) {
                    Toast.makeText(this, "删除原始视频失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        checkPermission(permission, STORAGE_PERMISSION_CODE)
    }

    private fun checkPermission(permission: String, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (!(grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                videoInfoTextView.text = "存储权限被拒绝"
            }
        }
    }
}
