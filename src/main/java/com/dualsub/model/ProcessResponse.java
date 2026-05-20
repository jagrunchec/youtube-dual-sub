package com.dualsub.model;

import java.util.List;

public class ProcessResponse {
    private String videoId;
    private List<SubtitleEntry> subtitles1;
    private List<SubtitleEntry> subtitles2;
    private String lang1Label;
    private String lang2Label;
    /** BCP-47 code actually used for track 1 (e.g. "fr", "en"). For immersion mode: the video's detected language code. */
    private String lang1Code;
    /** BCP-47 code used for track 2 (e.g. "de"). */
    private String lang2Code;
    /** Video title fetched at processing time (may be null). */
    private String videoTitle;
    /** ID of the background Ollama refinement job (null if Ollama unavailable). */
    private String refinementJobId;

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

    public String getLang1Code() { return lang1Code; }
    public void setLang1Code(String lang1Code) { this.lang1Code = lang1Code; }

    public String getLang2Code() { return lang2Code; }
    public void setLang2Code(String lang2Code) { this.lang2Code = lang2Code; }

    public String getVideoTitle() { return videoTitle; }
    public void setVideoTitle(String videoTitle) { this.videoTitle = videoTitle; }

    public String getRefinementJobId() { return refinementJobId; }
    public void setRefinementJobId(String refinementJobId) { this.refinementJobId = refinementJobId; }
}
