package com.videoshrinkmaster.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.videoshrinkmaster.R;
import com.videoshrinkmaster.model.CompressionConfig;
import com.videoshrinkmaster.model.VideoInfo;
import com.videoshrinkmaster.utils.FileUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CompressionService extends Service {
    
    public static final String ACTION_PROGRESS = "com.videoshrinkmaster.PROGRESS";
    public static final String ACTION_COMPLETE = "com.videoshrinkmaster.COMPLETE";
    public static final String ACTION_ERROR = "com.videoshrinkmaster.ERROR";
    public static final String ACTION_PAUSE = "com.videoshrinkmaster.PAUSE";
    public static final String ACTION_RESUME = "com.videoshrinkmaster.RESUME";
    
    private static final String CHANNEL_ID = "compression_channel";
    private static final int NOTIFICATION_ID = 1;
    
    private ExecutorService executorService;
    private List<VideoInfo> videos;
    private CompressionConfig config;
    private List<String> outputPaths = new ArrayList<>();
    private boolean isPaused = false;
    private int currentIndex = 0;
    private boolean isRunning = false;
    
    private final IBinder binder = new LocalBinder();
    
    public class LocalBinder extends Binder {
        CompressionService getService() {
            return CompressionService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        executorService = Executors.newSingleThreadExecutor();
        createNotificationChannel();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                if (ACTION_PAUSE.equals(action)) {
                    pauseCompression();
                } else if (ACTION_RESUME.equals(action)) {
                    resumeCompression();
                }
            } else if (!isRunning) {
                videos = intent.getParcelableArrayListExtra("videos");
                config = intent.getParcelableExtra("config");
                
                if (videos != null && config != null) {
                    startForeground(NOTIFICATION_ID, createNotification());
                    isRunning = true;
                    startCompression();
                }
            }
        }
        return START_STICKY;
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    private void startCompression() {
        executorService.execute(() -> {
            try {
                for (int i = 0; i < videos.size(); i++) {
                    currentIndex = i;
                    
                    // 检查是否暂停
                    while (isPaused) {
                        Thread.sleep(100);
                    }
                    
                    VideoInfo video = videos.get(i);
                    String outputPath = compressVideo(video, i);
                    
                    if (outputPath != null) {
                        outputPaths.add(outputPath);
                    }
                    
                    // 发送进度更新
                    sendProgressUpdate(i + 1, video.getName(), (i + 1) * 100 / videos.size());
                }
                
                // 发送完成通知
                sendCompletion();
                
            } catch (Exception e) {
                sendError(e.getMessage());
            } finally {
                isRunning = false;
                stopSelf();
            }
        });
    }
    
    private String compressVideo(VideoInfo video, int index) {
        String inputPath = video.getPath();
        String outputPath = FileUtils.getOutputPath(inputPath, config);
        
        // 构建FFmpeg命令
        String command = buildFFmpegCommand(inputPath, outputPath, config);
        
        // 执行压缩
        try {
            int returnCode = FFmpeg.execute(command);
            
            if (returnCode == 0) {
                // 压缩成功
                File outputFile = new File(outputPath);
                if (outputFile.exists()) {
                    return outputPath;
                } else {
                    sendError("压缩失败: 输出文件不存在");
                    return null;
                }
            } else {
                // 压缩失败
                String error = FFmpeg.getLastCommandOutput();
                sendError("压缩失败: " + (error != null ? error : "未知错误"));
                return null;
            }
        } catch (Exception e) {
            sendError("压缩异常: " + e.getMessage());
            return null;
        }
    }
    
    private String buildFFmpegCommand(String inputPath, String outputPath, CompressionConfig config) {
        StringBuilder command = new StringBuilder();
        
        command.append("-i \"").append(inputPath).append("\" ");
        command.append("-c:v libx264 ");
        command.append("-preset medium ");
        command.append("-crf ").append(config.getCrf()).append(" ");
        command.append("-b:v ").append(config.getBitrate()).append("k ");
        
        // 设置分辨率
        if (!config.getResolution().equals("original")) {
            String resolution = parseResolution(config.getResolution());
            if (!resolution.equals("-1:-1")) {
                command.append("-vf scale=").append(resolution).append(" ");
            }
        }
        
        // 设置帧率
        if (config.getFrameRate() > 0) {
            command.append("-r ").append(config.getFrameRate()).append(" ");
        }
        
        command.append("-c:a aac ");
        command.append("-b:a 128k ");
        command.append("-y "); // 覆盖输出文件
        command.append("\"").append(outputPath).append("\"");
        
        return command.toString();
    }
    
    private String parseResolution(String resolution) {
        switch (resolution) {
            case "480p":
                return "854:480";
            case "720p":
                return "1280:720";
            case "1080p":
                return "1920:1080";
            case "2K":
                return "2560:1440";
            case "4K":
                return "3840:2160";
            default:
                return "-1:-1"; // 保持原分辨率
        }
    }
    
    private void sendProgressUpdate(int index, String fileName, int progress) {
        Intent intent = new Intent(ACTION_PROGRESS);
        intent.putExtra("index", index);
        intent.putExtra("total", videos.size());
        intent.putExtra("current_file", fileName);
        intent.putExtra("progress", progress);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        
        // 更新通知
        updateNotification(fileName, progress);
    }
    
    private void sendCompletion() {
        Intent intent = new Intent(ACTION_COMPLETE);
        intent.putStringArrayListExtra("output_paths", new ArrayList<>(outputPaths));
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    private void sendError(String error) {
        Intent intent = new Intent(ACTION_ERROR);
        intent.putExtra("error", error);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "视频压缩",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("显示视频压缩进度");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("视频压缩中")
            .setContentText("正在压缩视频...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true);
        
        return builder.build();
    }
    
    private void updateNotification(String fileName, int progress) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("视频压缩中")
            .setContentText(fileName + " - " + progress + "%")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true);
        
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, builder.build());
        }
    }
    
    public void pauseCompression() {
        isPaused = true;
    }
    
    public void resumeCompression() {
        isPaused = false;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}

