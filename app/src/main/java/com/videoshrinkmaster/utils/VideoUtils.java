package com.videoshrinkmaster.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import com.videoshrinkmaster.model.VideoInfo;
import java.util.ArrayList;
import java.util.List;

public class VideoUtils {
    
    public static List<VideoInfo> getAllVideos(Context context) {
        List<VideoInfo> videos = new ArrayList<>();
        
        String[] projection = {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.RESOLUTION
        };
        
        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";
        
        Uri collection;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }
        
        try (Cursor cursor = context.getContentResolver().query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int resolutionColumn = cursor.getColumnIndex(MediaStore.Video.Media.RESOLUTION);
                
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String path = null;
                    
                    // Android 10+ 使用Uri, Android 9- 使用DATA字段
                    if (dataColumn >= 0) {
                        path = cursor.getString(dataColumn);
                    }
                    
                    // 如果DATA字段不可用,使用Uri构建路径
                    if (path == null || path.isEmpty()) {
                        Uri videoUri = Uri.withAppendedPath(collection, String.valueOf(id));
                        path = videoUri.toString();
                    }
                    
                    String name = cursor.getString(nameColumn);
                    long size = cursor.getLong(sizeColumn);
                    long duration = cursor.getLong(durationColumn);
                    
                    VideoInfo video = new VideoInfo(id, path, name, size, duration);
                    
                    if (resolutionColumn >= 0) {
                        String resolution = cursor.getString(resolutionColumn);
                        video.setResolution(resolution != null ? resolution : "");
                    }
                    
                    videos.add(video);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return videos;
    }
    
    public static String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60);
        } else {
            return String.format("%02d:%02d", minutes, seconds % 60);
        }
    }
    
    public static String formatSize(long sizeBytes) {
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

