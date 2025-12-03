package com.videoshrinkmaster.model;

import android.os.Parcel;
import android.os.Parcelable;

public class CompressionConfig implements Parcelable {
    private String resolution;
    private int bitrate; // kbps
    private int frameRate;
    private int crf; // 0-51, 越低质量越好
    
    public CompressionConfig(String resolution, int bitrate, int frameRate, int crf) {
        this.resolution = resolution;
        this.bitrate = bitrate;
        this.frameRate = frameRate;
        this.crf = crf;
    }
    
    // 预设配置
    public static CompressionConfig getRecommendedConfig() {
        return new CompressionConfig("720p", 1500, 30, 28);
    }
    
    public static CompressionConfig getHighQualityConfig() {
        return new CompressionConfig("1080p", 4000, 30, 23);
    }
    
    public static CompressionConfig getSaveSpaceConfig() {
        return new CompressionConfig("480p", 800, 24, 32);
    }
    
    // Getter和Setter方法
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    
    public int getBitrate() { return bitrate; }
    public void setBitrate(int bitrate) { this.bitrate = bitrate; }
    
    public int getFrameRate() { return frameRate; }
    public void setFrameRate(int frameRate) { this.frameRate = frameRate; }
    
    public int getCrf() { return crf; }
    public void setCrf(int crf) { this.crf = crf; }
    
    // Parcelable实现
    protected CompressionConfig(Parcel in) {
        resolution = in.readString();
        bitrate = in.readInt();
        frameRate = in.readInt();
        crf = in.readInt();
    }
    
    public static final Creator<CompressionConfig> CREATOR = new Creator<CompressionConfig>() {
        @Override
        public CompressionConfig createFromParcel(Parcel in) {
            return new CompressionConfig(in);
        }
        
        @Override
        public CompressionConfig[] newArray(int size) {
            return new CompressionConfig[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(resolution);
        dest.writeInt(bitrate);
        dest.writeInt(frameRate);
        dest.writeInt(crf);
    }
}

