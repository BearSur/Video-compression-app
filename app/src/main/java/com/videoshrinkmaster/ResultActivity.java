package com.videoshrinkmaster;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.videoshrinkmaster.databinding.ActivityResultBinding;
import com.videoshrinkmaster.utils.FileUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ResultActivity extends AppCompatActivity {
    
    private ActivityResultBinding binding;
    private List<String> outputPaths;
    private String originalPath;
    private String compressedPath;
    private ExoPlayer originalPlayer;
    private ExoPlayer compressedPlayer;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityResultBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 获取压缩后的视频路径
        outputPaths = getIntent().getStringArrayListExtra("output_paths");
        if (outputPaths == null || outputPaths.isEmpty()) {
            finish();
            return;
        }
        
        // 这里简化处理，只显示第一个视频
        // 实际应该从压缩服务传递原始路径，这里暂时使用文件名推导
        compressedPath = outputPaths.get(0);
        originalPath = getOriginalPath(compressedPath);
        
        setupUI();
        loadVideoInfo();
        setupVideoPlayers();
        setupButtons();
    }
    
    private void setupUI() {
        // 设置对比显示
        binding.tvOriginalSize.setText(FileUtils.getFileSize(originalPath));
        binding.tvCompressedSize.setText(FileUtils.getFileSize(compressedPath));
        
        // 计算节省空间
        File originalFile = new File(originalPath);
        File compressedFile = new File(compressedPath);
        
        if (originalFile.exists() && compressedFile.exists()) {
            long originalSize = originalFile.length();
            long compressedSize = compressedFile.length();
            double savedPercentage = (1 - (double) compressedSize / originalSize) * 100;
            binding.tvSaved.setText(String.format("节省 %.1f%% 空间", savedPercentage));
        } else {
            binding.tvSaved.setText("无法计算节省空间");
        }
    }
    
    private void loadVideoInfo() {
        // 使用FFprobe获取视频信息
        String originalInfo = FileUtils.getVideoInfo(originalPath);
        String compressedInfo = FileUtils.getVideoInfo(compressedPath);
        
        binding.tvOriginalInfo.setText(originalInfo);
        binding.tvCompressedInfo.setText(compressedInfo);
    }
    
    private void setupVideoPlayers() {
        // 初始化原始视频播放器
        originalPlayer = new ExoPlayer.Builder(this).build();
        binding.playerOriginal.setPlayer(originalPlayer);
        
        // 初始化压缩视频播放器
        compressedPlayer = new ExoPlayer.Builder(this).build();
        binding.playerCompressed.setPlayer(compressedPlayer);
        
        // 设置视频源
        File originalFile = new File(originalPath);
        File compressedFile = new File(compressedPath);
        
        if (originalFile.exists()) {
            MediaItem originalMediaItem = MediaItem.fromUri(Uri.fromFile(originalFile));
            originalPlayer.setMediaItem(originalMediaItem);
            originalPlayer.prepare();
        }
        
        if (compressedFile.exists()) {
            MediaItem compressedMediaItem = MediaItem.fromUri(Uri.fromFile(compressedFile));
            compressedPlayer.setMediaItem(compressedMediaItem);
            compressedPlayer.prepare();
        }
    }
    
    private void setupButtons() {
        binding.btnSaveToGallery.setOnClickListener(v -> saveToGallery());
        
        binding.btnShare.setOnClickListener(v -> shareVideo());
        
        binding.btnReplaceOriginal.setOnClickListener(v -> showReplaceDialog());
        
        binding.btnPlayOriginal.setOnClickListener(v -> {
            if (originalPlayer == null) return;
            if (originalPlayer.isPlaying()) {
                originalPlayer.pause();
                binding.btnPlayOriginal.setText("播放");
            } else {
                originalPlayer.play();
                binding.btnPlayOriginal.setText("暂停");
            }
        });
        
        binding.btnPlayCompressed.setOnClickListener(v -> {
            if (compressedPlayer == null) return;
            if (compressedPlayer.isPlaying()) {
                compressedPlayer.pause();
                binding.btnPlayCompressed.setText("播放");
            } else {
                compressedPlayer.play();
                binding.btnPlayCompressed.setText("暂停");
            }
        });
    }
    
    private void saveToGallery() {
        FileUtils.saveToGallery(this, compressedPath, new FileUtils.SaveCallback() {
            @Override
            public void onSuccess() {
                // 显示保存成功提示
                runOnUiThread(() -> {
                    new AlertDialog.Builder(ResultActivity.this)
                        .setTitle("成功")
                        .setMessage("视频已保存到相册")
                        .setPositiveButton("确定", null)
                        .show();
                });
            }
            
            @Override
            public void onError(String error) {
                // 显示错误提示
                runOnUiThread(() -> {
                    new AlertDialog.Builder(ResultActivity.this)
                        .setTitle("保存失败")
                        .setMessage(error)
                        .setPositiveButton("确定", null)
                        .show();
                });
            }
        });
    }
    
    private void shareVideo() {
        File file = new File(compressedPath);
        if (!file.exists()) {
            new AlertDialog.Builder(this)
                .setTitle("错误")
                .setMessage("文件不存在")
                .setPositiveButton("确定", null)
                .show();
            return;
        }
        
        Uri fileUri = FileUtils.getFileUri(this, file);
        
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("video/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        
        startActivity(Intent.createChooser(shareIntent, "分享视频"));
    }
    
    private void showReplaceDialog() {
        new AlertDialog.Builder(this)
            .setTitle("替换原视频")
            .setMessage("确定要用压缩后的视频替换原视频吗？此操作不可逆。")
            .setPositiveButton("替换", (dialog, which) -> replaceOriginal())
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void replaceOriginal() {
        File originalFile = new File(originalPath);
        File compressedFile = new File(compressedPath);
        
        if (!compressedFile.exists()) {
            new AlertDialog.Builder(this)
                .setTitle("错误")
                .setMessage("压缩文件不存在")
                .setPositiveButton("确定", null)
                .show();
            return;
        }
        
        try {
            if (originalFile.exists()) {
                originalFile.delete();
            }
            if (compressedFile.renameTo(originalFile)) {
                // 替换成功
                new AlertDialog.Builder(this)
                    .setTitle("成功")
                    .setMessage("原视频已替换")
                    .setPositiveButton("确定", (dialog, which) -> finish())
                    .show();
            } else {
                // 替换失败
                new AlertDialog.Builder(this)
                    .setTitle("失败")
                    .setMessage("替换原视频失败")
                    .setPositiveButton("确定", null)
                    .show();
            }
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                .setTitle("错误")
                .setMessage("替换失败: " + e.getMessage())
                .setPositiveButton("确定", null)
                .show();
        }
    }
    
    private String getOriginalPath(String compressedPath) {
        // 从压缩文件路径推导出原始文件路径
        // 实际实现需要更复杂的逻辑，这里简化处理
        // 应该从CompressionService传递原始路径信息
        String path = compressedPath.replace("_compressed_", "_original_");
        // 如果找不到，尝试其他方式
        if (!new File(path).exists()) {
            // 尝试从文件名中提取
            String fileName = new File(compressedPath).getName();
            if (fileName.contains("_compressed_")) {
                String originalFileName = fileName.substring(0, fileName.indexOf("_compressed_")) + 
                    fileName.substring(fileName.lastIndexOf("."));
                File compressedFile = new File(compressedPath);
                File parentDir = compressedFile.getParentFile();
                if (parentDir != null) {
                    return new File(parentDir.getParent(), originalFileName).getAbsolutePath();
                }
            }
        }
        return path;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (originalPlayer != null) {
            originalPlayer.release();
            originalPlayer = null;
        }
        if (compressedPlayer != null) {
            compressedPlayer.release();
            compressedPlayer = null;
        }
    }
}

