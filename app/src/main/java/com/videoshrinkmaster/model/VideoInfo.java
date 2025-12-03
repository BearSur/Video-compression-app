package com.videoshrinkmaster.model;

import android.os.Parcel;
import android.os.Parcelable;

public class VideoInfo implements Parcelable {
    private long id;
    private String path;
    private String name;
    private long size;
    private long duration;
    private int bitrate;
    private String resolution;
    
    public VideoInfo(long id, String path, String name, long size, long duration) {
        this.id = id;
        this.path = path;
        this.name = name;
        this.size = size;
        this.duration = duration;
        this.bitrate = 0;
        this.resolution = "";
    }
    
    // Getter和Setter方法
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
    
    public int getBitrate() { return bitrate; }
    public void setBitrate(int bitrate) { this.bitrate = bitrate; }
    
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    
    // Parcelable实现
    protected VideoInfo(Parcel in) {
        id = in.readLong();
        path = in.readString();
        name = in.readString();
        size = in.readLong();
        duration = in.readLong();
        bitrate = in.readInt();
        resolution = in.readString();
    }
    
    public static final Creator<VideoInfo> CREATOR = new Creator<VideoInfo>() {
        @Override
        public VideoInfo createFromParcel(Parcel in) {
            return new VideoInfo(in);
        }
        
        @Override
        public VideoInfo[] newArray(int size) {
            return new VideoInfo[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(path);
        dest.writeString(name);
        dest.writeLong(size);
        dest.writeLong(duration);
        dest.writeInt(bitrate);
        dest.writeString(resolution);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        VideoInfo videoInfo = (VideoInfo) obj;
        return id == videoInfo.id && path.equals(videoInfo.path);
    }
    
    @Override
    public int hashCode() {
        return Long.hashCode(id) + path.hashCode();
    }
}

