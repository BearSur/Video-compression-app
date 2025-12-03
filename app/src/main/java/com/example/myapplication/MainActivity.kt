
package com.example.myapplication

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.RecoverableSecurityException
import android.content.ContentValues
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
import androidx.activity.result.IntentSenderRequest
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
    private val compressedVideoPaths = mutableListOf<String>()
    private val STORAGE_PERMISSION_CODE = 101
    private var totalVideosToCompress = 0
    private var currentVideoIndex = 0

    private val deleteRequestLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "原始视频已删除", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "删除原始视频失败或被取消", Toast.LENGTH_SHORT).show()
        }
    }

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

        selectVideoButton.setOnClickListener { selectVideo() }

        compressButton.setOnClickListener {
            if (selectedVideoUris.isNotEmpty()) {
                compressVideos()
            } else {
                videoInfoTextView.text = "请先选择一个或多个视频"
            }
        }

        saveButton.setOnClickListener {
            compressedVideoPaths.firstOrNull()?.let { path ->
                saveVideoToGallery(path)
            }
        }

        shareButton.setOnClickListener { shareVideo() }

        replaceButton.setOnClickListener { showReplaceConfirmationDialog() }
    }

    private fun selectVideo() {
        postCompressionLayout.visibility = View.GONE
        compressButton.visibility = View.VISIBLE
        selectedVideoUris.clear()
        compressedVideoPaths.clear()

        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        selectVideoLauncher.launch(intent)
    }

    private val selectVideoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
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
        compressedVideoPaths.clear()
        compressButton.visibility = View.GONE
        compressNextVideo()
    }

    private fun compressNextVideo() {
        if (currentVideoIndex >= totalVideosToCompress) {
            runOnUiThread {
                progressBar.visibility = View.GONE
                videoInfoTextView.text = "所有视频压缩完成！"
                if (totalVideosToCompress > 1) {
                    showBatchOperationDialog()
                } else {
                    postCompressionLayout.visibility = View.VISIBLE
                }
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
                        val overallProgress = ((currentVideoIndex.toDouble() + progress) / totalVideosToCompress * 100).toInt()
                        progressBar.progress = overallProgress
                        videoInfoTextView.text = "正在压缩视频 ${currentVideoIndex + 1} / $totalVideosToCompress..."
                    }
                }

                override fun onTranscodeCompleted(successCode: Int) {
                    compressedVideoPaths.add(destinationPath.path)
                    currentVideoIndex++
                    compressNextVideo()
                }

                override fun onTranscodeCanceled() {
                    currentVideoIndex++
                    compressNextVideo()
                }

                override fun onTranscodeFailed(exception: Throwable) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "视频 ${currentVideoIndex + 1} 压缩失败: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                    currentVideoIndex++
                    compressNextVideo()
                }
            }).transcode()
    }

    private fun saveVideoToGallery(videoPath: String) {
        val file = File(videoPath)
        if (!file.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show()
            return
        }

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            try {
                resolver.openOutputStream(uri).use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream!!)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                Toast.makeText(this, "视频已保存到相册", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "无法创建媒体文件", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareVideo() {
        compressedVideoPaths.firstOrNull()?.let { path ->
            val videoFile = File(path)
            val videoUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", videoFile)
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
            .setMessage("这将保存压缩后的视频到相册，并删除原始视频。确定吗？")
            .setPositiveButton("是") { _, _ -> replaceVideo() }
            .setNegativeButton("否", null)
            .show()
    }

    private fun replaceVideo() {
        compressedVideoPaths.firstOrNull()?.let { saveVideoToGallery(it) }
        if (selectedVideoUris.isNotEmpty()) {
            deleteOriginalVideos(listOf(selectedVideoUris.first()))
        }
    }

    private fun showBatchOperationDialog() {
        AlertDialog.Builder(this)
            .setTitle("批量操作")
            .setMessage("所有视频已压缩完成。")
            .setPositiveButton("批量保存") { _, _ ->
                compressedVideoPaths.forEach { saveVideoToGallery(it) }
            }
            .setNegativeButton("批量替换") { _, _ ->
                compressedVideoPaths.forEach { saveVideoToGallery(it) }
                deleteOriginalVideos(selectedVideoUris)
            }
            .setNeutralButton("取消", null)
            .show()
    }

    private fun deleteOriginalVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11+
            val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
            val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            deleteRequestLauncher.launch(request)
        } else {
            var failedDeletions = 0
            uris.forEach { uri ->
                try {
                    contentResolver.delete(uri, null, null)
                } catch (e: SecurityException) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                        failedDeletions++
                    } else {
                        failedDeletions++
                    }
                }
            }
            if (failedDeletions > 0) {
                 Toast.makeText(this, "$failedDeletions 个原始视频删除失败", Toast.LENGTH_LONG).show()
            } else {
                 Toast.makeText(this, "原始视频已删除", Toast.LENGTH_SHORT).show()
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
