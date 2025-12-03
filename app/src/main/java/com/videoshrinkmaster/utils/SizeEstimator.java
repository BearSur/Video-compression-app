package com.videoshrinkmaster.utils;

import android.util.Log;

public class SizeEstimator {
    private static final String TAG = "SizeEstimator";
    
    public static double estimateCompressedSize(
        long originalSize,
        int originalBitrate,
        int targetBitrate,
        String originalResolution,
        String targetResolution
    ) {
        try {
            // 基于码率变化的估算
            double bitrateRatio = originalBitrate > 0 
                ? (double) targetBitrate / originalBitrate 
                : 1.0;
            
            // 基于分辨率变化的估算
            double resolutionRatio = getResolutionRatio(originalResolution, targetResolution);
            
            // 总估算 = 原大小 × 码率比 × 分辨率比 × 编码效率系数
            double estimatedSize = originalSize * bitrateRatio * resolutionRatio * 0.9;
            
            Log.d(TAG, String.format(
                "估算: %.2f = %d × %.2f × %.2f × 0.9",
                estimatedSize, originalSize, bitrateRatio, resolutionRatio
            ));
            
            return estimatedSize;
            
        } catch (Exception e) {
            Log.e(TAG, "估算失败: " + e.getMessage());
            return originalSize * 0.5; // 默认估算为原大小的一半
        }
    }
    
    private static double getResolutionRatio(String originalRes, String targetRes) {
        // 解析分辨率字符串，如 "1920x1080"
        int originalPixels = parseResolutionToPixels(originalRes);
        int targetPixels = parseResolutionToPixels(targetRes);
        
        if (originalPixels <= 0 || targetPixels <= 0) {
            return 1.0; // 无法解析时返回1
        }
        
        return (double) targetPixels / originalPixels;
    }
    
    private static int parseResolutionToPixels(String resolution) {
        try {
            if (resolution == null || resolution.isEmpty()) {
                return -1;
            }
            
            if (resolution.contains("x")) {
                String[] parts = resolution.split("x");
                int width = Integer.parseInt(parts[0]);
                int height = Integer.parseInt(parts[1]);
                return width * height;
            }
            
            // 处理预设分辨率
            switch (resolution) {
                case "original":
                    return -1; // 保持原分辨率
                case "480p":
                    return 854 * 480;
                case "720p":
                    return 1280 * 720;
                case "1080p":
                    return 1920 * 1080;
                case "2K":
                    return 2560 * 1440;
                case "4K":
                    return 3840 * 2160;
                default:
                    return -1;
            }
        } catch (Exception e) {
            return -1;
        }
    }
}

