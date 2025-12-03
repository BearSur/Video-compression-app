package com.videoshrinkmaster;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.videoshrinkmaster.databinding.ActivityCompressionProgressBinding;
import com.videoshrinkmaster.model.CompressionConfig;
import com.videoshrinkmaster.model.VideoInfo;
import com.videoshrinkmaster.service.CompressionService;
import java.util.ArrayList;
import java.util.List;

public class CompressionProgressActivity extends AppCompatActivity {
    
    private ActivityCompressionProgressBinding binding;
    private List<VideoInfo> selectedVideos;
    private CompressionConfig config;
    private int currentIndex = 0;
    private int totalVideos;
    private long startTime = 0;
    
    private BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (CompressionService.ACTION_PROGRESS.equals(action)) {
                int progress = intent.getIntExtra("progress", 0);
                String currentFile = intent.getStringExtra("current_file");
                int index = intent.getIntExtra("index", 0);
                int total = intent.getIntExtra("total", 0);
                
                updateProgress(progress, currentFile, index, total);
            } 
            else if (CompressionService.ACTION_COMPLETE.equals(action)) {
                List<String> outputPaths = intent.getStringArrayListExtra("output_paths");
                // 跳转到结果页面
                Intent resultIntent = new Intent(CompressionProgressActivity.this, 
                    ResultActivity.class);
                resultIntent.putStringArrayListExtra("output_paths", 
                    new ArrayList<>(outputPaths));
                startActivity(resultIntent);
                finish();
            }
            else if (CompressionService.ACTION_ERROR.equals(action)) {
                String error = intent.getStringExtra("error");
                showError(error);
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCompressionProgressBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 获取数据
        selectedVideos = getIntent().getParcelableArrayListExtra("selected_videos");
        config = getIntent().getParcelableExtra("config");
        
        if (selectedVideos == null || selectedVideos.isEmpty()) {
            finish();
            return;
        }
        
        totalVideos = selectedVideos.size();
        startTime = System.currentTimeMillis();
        setupUI();
        startCompression();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(CompressionService.ACTION_PROGRESS);
        filter.addAction(CompressionService.ACTION_COMPLETE);
        filter.addAction(CompressionService.ACTION_ERROR);
        LocalBroadcastManager.getInstance(this).registerReceiver(progressReceiver, filter);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(progressReceiver);
    }
    
    private void setupUI() {
        binding.btnCancel.setOnClickListener(v -> {
            // 停止服务
            Intent serviceIntent = new Intent(this, CompressionService.class);
            stopService(serviceIntent);
            finish();
        });
        
        binding.btnPause.setOnClickListener(v -> {
            // 暂停/继续
            boolean isPaused = binding.btnPause.getText().toString().equals("继续");
            if (isPaused) {
                resumeCompression();
                binding.btnPause.setText("暂停");
            } else {
                pauseCompression();
                binding.btnPause.setText("继续");
            }
        });
    }
    
    private void startCompression() {
        Intent serviceIntent = new Intent(this, CompressionService.class);
        serviceIntent.putParcelableArrayListExtra("videos", 
            new ArrayList<>(selectedVideos));
        serviceIntent.putExtra("config", config);
        startService(serviceIntent);
    }
    
    private void updateProgress(int progress, String currentFile, int index, int total) {
        new Handler(Looper.getMainLooper()).post(() -> {
            binding.progressBar.setProgress(progress);
            binding.tvCurrentFile.setText(currentFile);
            binding.tvProgress.setText(String.format("%d/%d", index, total));
            binding.tvPercentage.setText(progress + "%");
            
            // 更新剩余时间估计
            if (progress > 0 && startTime > 0) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                long estimatedTotalTime = elapsedTime * 100 / progress;
                long remainingTime = estimatedTotalTime - elapsedTime;
                binding.tvRemainingTime.setText(formatTime(remainingTime));
            }
        });
    }
    
    private void pauseCompression() {
        Intent intent = new Intent(this, CompressionService.class);
        intent.setAction(CompressionService.ACTION_PAUSE);
        startService(intent);
    }
    
    private void resumeCompression() {
        Intent intent = new Intent(this, CompressionService.class);
        intent.setAction(CompressionService.ACTION_RESUME);
        startService(intent);
    }
    
    private void showError(String error) {
        new Handler(Looper.getMainLooper()).post(() -> {
            // 显示错误对话框
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("压缩失败")
                .setMessage(error)
                .setPositiveButton("确定", (dialog, which) -> finish())
                .show();
        });
    }
    
    private String formatTime(long millis) {
        if (millis < 0) {
            return "计算中...";
        }
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%d小时%d分", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%d分%d秒", minutes, seconds % 60);
        } else {
            return String.format("%d秒", seconds);
        }
    }
}

