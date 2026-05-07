package com.dualsub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Caches the fully-processed transcript for a video (after merge + punctuation + sentence split).
 * Keyed by videoId — allows skipping the Python pipeline on repeated views of the same video.
 */
@Entity
@Table(name = "transcript_cache")
public class TranscriptCache {

    /** YouTube video ID (11-character alphanumeric string). */
    @Id
    @Column(name = "video_id", length = 30, nullable = false)
    private String videoId;

    /** BCP-47 language code detected by youtube-transcript-api. May be null (HTTP fallback). */
    @Column(name = "language_code", length = 10)
    private String languageCode;

    /**
     * JSON-serialised List&lt;SubtitleEntry&gt; — can be up to ~200 KB for long videos.
     * Stored as a CLOB so H2 handles arbitrarily large text.
     */
    @Lob
    @Column(name = "entries_json", nullable = false)
    private String entriesJson;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "entry_count", nullable = false)
    private int entryCount;

    public String getVideoId()                      { return videoId; }
    public void setVideoId(String videoId)          { this.videoId = videoId; }

    public String getLanguageCode()                       { return languageCode; }
    public void setLanguageCode(String languageCode)      { this.languageCode = languageCode; }

    public String getEntriesJson()                        { return entriesJson; }
    public void setEntriesJson(String entriesJson)        { this.entriesJson = entriesJson; }

    public LocalDateTime getFetchedAt()                   { return fetchedAt; }
    public void setFetchedAt(LocalDateTime fetchedAt)     { this.fetchedAt = fetchedAt; }

    public int getEntryCount()                      { return entryCount; }
    public void setEntryCount(int entryCount)       { this.entryCount = entryCount; }
}
