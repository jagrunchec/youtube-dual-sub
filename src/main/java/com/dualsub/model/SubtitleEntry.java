package com.dualsub.model;

public class SubtitleEntry {
    private long startMs;
    private long durationMs;
    private String text;

    public SubtitleEntry() {}

    public SubtitleEntry(long startMs, long durationMs, String text) {
        this.startMs = startMs;
        this.durationMs = durationMs;
        this.text = text;
    }

    public long getStartMs() { return startMs; }
    public void setStartMs(long startMs) { this.startMs = startMs; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
