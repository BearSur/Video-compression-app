package com.videoshrinkmaster;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.videoshrinkmaster.databinding.ActivityVideoPickerBinding;
import com.videoshrinkmaster.model.VideoInfo;
import com.videoshrinkmaster.utils.VideoUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class VideoPickerActivity extends AppCompatActivity {
    
    private ActivityVideoPickerBinding binding;
    private VideoAdapter adapter;
    private List<VideoInfo> videoList = new ArrayList<>();
    private List<VideoInfo> selectedVideos = new ArrayList<>();
    private boolean isMultiSelectMode = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVideoPickerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        setupRecyclerView();
        loadVideos();
        setupButtons();
    }
    
    private void setupRecyclerView() {
        binding.recyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new VideoAdapter();
        binding.recyclerView.setAdapter(adapter);
    }
    
    private void loadVideos() {
        new LoadVideosTask().execute();
    }
    
    private void setupButtons() {
        binding.btnMultiSelect.setOnClickListener(v -> {
            isMultiSelectMode = !isMultiSelectMode;
            adapter.notifyDataSetChanged();
            binding.btnMultiSelect.setText(isMultiSelectMode ? "取消多选" : "多选模式");
        });
        
        binding.btnNext.setOnClickListener(v -> {
            if (selectedVideos.isEmpty()) {
                // 提示选择视频
                return;
            }
            Intent intent = new Intent(this, CompressionSettingsActivity.class);
            intent.putParcelableArrayListExtra("selected_videos", 
                new ArrayList<>(selectedVideos));
            startActivity(intent);
        });
    }
    
    private class LoadVideosTask extends AsyncTask<Void, Void, List<VideoInfo>> {
        @Override
        protected List<VideoInfo> doInBackground(Void... voids) {
            return VideoUtils.getAllVideos(VideoPickerActivity.this);
        }
        
        @Override
        protected void onPostExecute(List<VideoInfo> videos) {
            videoList = videos;
            adapter.setVideos(videos);
        }
    }
    
    private class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {
        
        private List<VideoInfo> videos = new ArrayList<>();
        
        public void setVideos(List<VideoInfo> videos) {
            this.videos = videos;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_video, parent, false);
            return new VideoViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
            VideoInfo video = videos.get(position);
            holder.bind(video);
        }
        
        @Override
        public int getItemCount() {
            return videos.size();
        }
        
        class VideoViewHolder extends RecyclerView.ViewHolder {
            ImageView thumbnail;
            TextView duration;
            TextView size;
            CheckBox checkBox;
            
            VideoViewHolder(@NonNull View itemView) {
                super(itemView);
                thumbnail = itemView.findViewById(R.id.thumbnail);
                duration = itemView.findViewById(R.id.duration);
                size = itemView.findViewById(R.id.size);
                checkBox = itemView.findViewById(R.id.checkbox);
            }
            
            void bind(VideoInfo video) {
                // 加载缩略图
                Glide.with(itemView.getContext())
                    .load(new File(video.getPath()))
                    .thumbnail(0.1f)
                    .into(thumbnail);
                
                // 格式化显示
                duration.setText(VideoUtils.formatDuration(video.getDuration()));
                size.setText(VideoUtils.formatSize(video.getSize()));
                
                // 设置选择状态
                checkBox.setVisibility(isMultiSelectMode ? View.VISIBLE : View.GONE);
                checkBox.setChecked(selectedVideos.contains(video));
                
                itemView.setOnClickListener(v -> {
                    if (isMultiSelectMode) {
                        if (selectedVideos.contains(video)) {
                            selectedVideos.remove(video);
                        } else {
                            selectedVideos.add(video);
                        }
                        notifyItemChanged(getAdapterPosition());
                    } else {
                        // 单选择模式，直接进入压缩设置
                        List<VideoInfo> singleVideo = new ArrayList<>();
                        singleVideo.add(video);
                        Intent intent = new Intent(VideoPickerActivity.this, 
                            CompressionSettingsActivity.class);
                        intent.putParcelableArrayListExtra("selected_videos", 
                            new ArrayList<>(singleVideo));
                        startActivity(intent);
                    }
                });
            }
        }
    }
}

