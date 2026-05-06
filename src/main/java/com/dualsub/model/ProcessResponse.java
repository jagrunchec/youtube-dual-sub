package com.dualsub.model;

import java.util.List;

public class ProcessResponse {
    private String videoId;
    private List<SubtitleEntry> subtitles1;
    private List<SubtitleEntry> subtitles2;
    private String lang1Label;
    private String lang2Label;

    public String getVideoId() { return videoId; }
    public void setVideoId(String videoId) { this.videoId = videoId; }

    public List<SubtitleEntry> getSubtitles1() { return subtitles1; }
    public void setSubtitles1(List<SubtitleEntry> subtitles1) { this.subtitles1 = subtitles1; }

    public List<SubtitleEntry> getSubtitles2() { return subtitles2; }
    public void setSubtitles2(List<SubtitleEntry> subtitles2) { this.subtitles2 = subtitles2; }

    public String getLang1Label() { return lang1Label; }
    public void setLang1Label(String lang1Label) { this.lang1Label = lang1Label; }

    public String getLang2Label() { return lang2Label; }
    public void setLang2Label(String lang2Label) { this.lang2Label = lang2Label; }
}
