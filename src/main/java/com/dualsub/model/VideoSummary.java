package com.dualsub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Caches a generated video summary keyed by (videoId, lang, engine).
 * Allows instant retrieval on subsequent clicks without re-calling the LLM.
 */
@Entity
@Table(name = "video_summaries",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_video_summary",
           columnNames = {"video_id", "lang", "engine"}))
public class VideoSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", nullable = false, length = 30)
    private String videoId;

    /** Target language BCP-47 code (e.g. "fr", "ru", "ar"). */
    @Column(name = "lang", nullable = false, length = 10)
    private String lang;

    /** Engine that produced this summary: "ollama" or "gemini". */
    @Column(name = "engine", nullable = false, length = 20)
    private String engine;

    /** Specific model name (e.g. "mistral-nemo", "gemini-2.0-flash"). */
    @Column(name = "model", length = 50)
    private String model;

    @Lob
    @Column(name = "summary", nullable = false)
    private String summary;

    /** Wall-clock generation time in milliseconds. */
    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getId()                              { return id; }
    public void setId(Long id)                       { this.id = id; }

    public String getVideoId()                       { return videoId; }
    public void setVideoId(String v)                 { this.videoId = v; }

    public String getLang()                          { return lang; }
    public void setLang(String v)                    { this.lang = v; }

    public String getEngine()                        { return engine; }
    public void setEngine(String v)                  { this.engine = v; }

    public String getModel()                         { return model; }
    public void setModel(String v)                   { this.model = v; }

    public String getSummary()                       { return summary; }
    public void setSummary(String v)                 { this.summary = v; }

    public Integer getDurationMs()                   { return durationMs; }
    public void setDurationMs(Integer v)             { this.durationMs = v; }

    public LocalDateTime getCreatedAt()              { return createdAt; }
    public void setCreatedAt(LocalDateTime v)        { this.createdAt = v; }
}
