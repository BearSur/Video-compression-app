package com.videoshrinkmaster.service;

import android.os.AsyncTask;
import com.videoshrinkmaster.model.CompressionConfig;
import com.videoshrinkmaster.model.VideoInfo;

public class CompressionTask extends AsyncTask<Void, Integer, Boolean> {
    
    private VideoInfo video;
    private CompressionConfig config;
    private CompressionCallback callback;
    
    public CompressionTask(VideoInfo video, CompressionConfig config, CompressionCallback callback) {
        this.video = video;
        this.config = config;
        this.callback = callback;
    }
    
    @Override
    protected Boolean doInBackground(Void... voids) {
        // 这个任务类可以用于单视频压缩
        // 主要压缩逻辑在CompressionService中
        return true;
    }
    
    @Override
    protected void onProgressUpdate(Integer... values) {
        if (callback != null) {
            callback.onProgress(values[0]);
        }
    }
    
    @Override
    protected void onPostExecute(Boolean success) {
        if (callback != null) {
            if (success) {
                callback.onSuccess();
            } else {
                callback.onError("压缩失败");
            }
        }
    }
    
    public interface CompressionCallback {
        void onProgress(int progress);
        void onSuccess();
        void onError(String error);
    }
}

