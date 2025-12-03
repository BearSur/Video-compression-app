package com.videoshrinkmaster;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.guolindev.permissionx.PermissionX;
import com.videoshrinkmaster.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    
    private ActivityMainBinding binding;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        requestPermissions();
        setupUI();
    }
    
    private void requestPermissions() {
        PermissionX.init(this)
            .permissions(
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            .request((allGranted, grantedList, deniedList) -> {
                if (allGranted) {
                    // 所有权限已授予
                } else {
                    // 处理权限被拒绝的情况
                }
            });
    }
    
    private void setupUI() {
        binding.btnSelectVideo.setOnClickListener(v -> {
            Intent intent = new Intent(this, VideoPickerActivity.class);
            startActivity(intent);
        });
        
        binding.btnHistory.setOnClickListener(v -> {
            // 打开历史记录
        });
        
        binding.btnSettings.setOnClickListener(v -> {
            // 打开设置
        });
    }
}

