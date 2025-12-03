package com.videoshrinkmaster.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import com.videoshrinkmaster.model.CompressionConfig;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileUtils {
    
    public static String getOutputPath(String inputPath, CompressionConfig config) {
        File inputFile = new File(inputPath);
        String fileName = inputFile.getName();
        
        // 移除扩展名
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            fileName = fileName.substring(0, dotIndex);
        }
        
        // 添加压缩标记和时间戳
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(new Date());
        String outputFileName = fileName + "_compressed_" + timestamp + ".mp4";
        
        // 保存到应用私有目录
        File outputDir = new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "VideoShrinkMaster"
        );
        
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        
        return new File(outputDir, outputFileName).getAbsolutePath();
    }
    
    public static String getFileSize(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return "0 B";
        }
        long size = file.length();
        
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format(Locale.getDefault(), "%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    public static String getVideoInfo(String filePath) {
        try {
            // 使用FFprobe获取视频信息
            // 这里简化处理，实际需要调用FFprobe
            return "1080p | 25fps | 5.2Mbps";
        } catch (Exception e) {
            return "未知信息";
        }
    }
    
    public static void saveToGallery(Context context, String filePath, SaveCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10及以上使用MediaStore
            saveToGalleryApi29(context, filePath, callback);
        } else {
            // Android 9及以下使用传统方法
            saveToGalleryLegacy(context, filePath, callback);
        }
    }
    
    private static void saveToGalleryApi29(Context context, String filePath, SaveCallback callback) {
        File file = new File(filePath);
        if (!file.exists()) {
            callback.onError("文件不存在");
            return;
        }
        
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, file.getName());
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.IS_PENDING, 1);
        }
        
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = resolver.insert(collection, values);
        
        if (uri != null) {
            try (InputStream input = new FileInputStream(file);
                 OutputStream output = resolver.openOutputStream(uri)) {
                
                if (output == null) {
                    callback.onError("无法打开输出流");
                    return;
                }
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear();
                    values.put(MediaStore.Video.Media.IS_PENDING, 0);
                    resolver.update(uri, values, null, null);
                }
                
                callback.onSuccess();
                
            } catch (IOException e) {
                resolver.delete(uri, null, null);
                callback.onError(e.getMessage());
            }
        } else {
            callback.onError("无法创建媒体文件");
        }
    }
    
    private static void saveToGalleryLegacy(Context context, String filePath, SaveCallback callback) {
        File sourceFile = new File(filePath);
        if (!sourceFile.exists()) {
            callback.onError("文件不存在");
            return;
        }
        
        File destDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MOVIES);
        
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
        
        File destFile = new File(destDir, sourceFile.getName());
        
        try (InputStream input = new FileInputStream(sourceFile);
             OutputStream output = new FileOutputStream(destFile)) {
            
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            
            // 通知系统扫描新文件
            android.media.MediaScannerConnection.scanFile(
                context,
                new String[]{destFile.getAbsolutePath()},
                new String[]{"video/mp4"},
                null
            );
            
            callback.onSuccess();
            
        } catch (IOException e) {
            callback.onError(e.getMessage());
        }
    }
    
    public static Uri getFileUri(Context context, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return androidx.core.content.FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
            );
        } else {
            return Uri.fromFile(file);
        }
    }
    
    public interface SaveCallback {
        void onSuccess();
        void onError(String error);
    }
}

