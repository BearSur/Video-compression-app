package com.videoshrinkmaster;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.SeekBar;
import androidx.appcompat.app.AppCompatActivity;
import com.videoshrinkmaster.databinding.ActivityCompressionSettingsBinding;
import com.videoshrinkmaster.model.CompressionConfig;
import com.videoshrinkmaster.model.VideoInfo;
import com.videoshrinkmaster.utils.SizeEstimator;
import java.util.ArrayList;
import java.util.List;

public class CompressionSettingsActivity extends AppCompatActivity {
    
    private ActivityCompressionSettingsBinding binding;
    private List<VideoInfo> selectedVideos;
    private CompressionConfig currentConfig;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCompressionSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 获取选择的视频
        selectedVideos = getIntent().getParcelableArrayListExtra("selected_videos");
        if (selectedVideos == null || selectedVideos.isEmpty()) {
            finish();
            return;
        }
        
        // 计算总大小
        long totalSize = 0;
        for (VideoInfo video : selectedVideos) {
            totalSize += video.getSize();
        }
        binding.tvTotalSize.setText(formatSize(totalSize));
        
        // 初始化配置
        currentConfig = CompressionConfig.getRecommendedConfig();
        setupUI();
        updateEstimation();
    }
    
    private void setupUI() {
        // 预设模式选择
        binding.rgPresetMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_recommended) {
                currentConfig = CompressionConfig.getRecommendedConfig();
            } else if (checkedId == R.id.rb_high_quality) {
                currentConfig = CompressionConfig.getHighQualityConfig();
            } else if (checkedId == R.id.rb_save_space) {
                currentConfig = CompressionConfig.getSaveSpaceConfig();
            }
            updateUIFromConfig();
            updateEstimation();
        });
        
        // 分辨率选择
        binding.spResolution.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String resolution = parent.getItemAtPosition(position).toString();
                currentConfig.setResolution(resolution);
                updateEstimation();
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        // 码率调节
        binding.sbBitrate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int bitrate = (progress + 1) * 500; // 500kbps - 5000kbps
                currentConfig.setBitrate(bitrate);
                binding.tvBitrateValue.setText(bitrate + " kbps");
                updateEstimation();
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 帧率选择
        binding.spFrameRate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int frameRate = Integer.parseInt(parent.getItemAtPosition(position).toString());
                currentConfig.setFrameRate(frameRate);
                updateEstimation();
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        
        // 开始压缩按钮
        binding.btnStartCompression.setOnClickListener(v -> {
            Intent intent = new Intent(this, CompressionProgressActivity.class);
            intent.putParcelableArrayListExtra("selected_videos", 
                new ArrayList<>(selectedVideos));
            intent.putExtra("config", currentConfig);
            startActivity(intent);
        });
    }
    
    private void updateUIFromConfig() {
        // 更新UI显示当前配置
        binding.sbBitrate.setProgress((currentConfig.getBitrate() / 500) - 1);
        binding.tvBitrateValue.setText(currentConfig.getBitrate() + " kbps");
        
        // 设置分辨率选择
        String[] resolutions = getResources().getStringArray(R.array.resolutions);
        for (int i = 0; i < resolutions.length; i++) {
            if (resolutions[i].equals(currentConfig.getResolution())) {
                binding.spResolution.setSelection(i);
                break;
            }
        }
        
        // 设置帧率选择
        String[] frameRates = getResources().getStringArray(R.array.frame_rates);
        for (int i = 0; i < frameRates.length; i++) {
            if (frameRates[i].equals(String.valueOf(currentConfig.getFrameRate()))) {
                binding.spFrameRate.setSelection(i);
                break;
            }
        }
    }
    
    private void updateEstimation() {
        if (selectedVideos.isEmpty()) return;
        
        VideoInfo sampleVideo = selectedVideos.get(0);
        // 获取原始码率和分辨率，如果不存在则使用默认值
        int originalBitrate = sampleVideo.getBitrate() > 0 ? sampleVideo.getBitrate() : 5000;
        String originalResolution = sampleVideo.getResolution() != null && !sampleVideo.getResolution().isEmpty() 
            ? sampleVideo.getResolution() : "1080p";
        
        double estimatedSize = SizeEstimator.estimateCompressedSize(
            sampleVideo.getSize(),
            originalBitrate,
            currentConfig.getBitrate(),
            originalResolution,
            currentConfig.getResolution()
        );
        
        long totalOriginalSize = 0;
        for (VideoInfo video : selectedVideos) {
            totalOriginalSize += video.getSize();
        }
        
        double totalEstimatedSize = estimatedSize * selectedVideos.size();
        double savedPercentage = (1 - totalEstimatedSize / totalOriginalSize) * 100;
        
        binding.tvEstimatedSize.setText(formatSize((long) totalEstimatedSize));
        binding.tvSavedPercentage.setText(String.format("节省 %.1f%%", savedPercentage));
    }
    
    private String formatSize(long sizeBytes) {
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        } else if (sizeBytes < 1024 * 1024) {
            return String.format("%.1f KB", sizeBytes / 1024.0);
        } else if (sizeBytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}



