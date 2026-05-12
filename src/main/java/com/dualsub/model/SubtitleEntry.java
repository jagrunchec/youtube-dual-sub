package com.dualsub.model;

public class SubtitleEntry {
    private long startMs;
    private long durationMs;
    private String text;
    /**
     * Estimated end of actual speech within this entry (ms from video start).
     * Derived from the ASR fragment end times before the gapless pin-pass.
     * The interval [speechEndMs, startMs+durationMs) is the real silence after
     * this sentence. Set to 0 when unknown (e.g. HTTP fallback entries).
     */
    private long speechEndMs;

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

    public long getSpeechEndMs() { return speechEndMs; }
    public void setSpeechEndMs(long speechEndMs) { this.speechEndMs = speechEndMs; }
}
