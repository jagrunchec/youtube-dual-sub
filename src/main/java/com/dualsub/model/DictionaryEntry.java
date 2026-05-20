package com.dualsub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Context in which the user encountered a word: one row per (user, word, video).
 * If the same word appears in multiple videos, there will be multiple entries.
 */
@Entity
@Table(name = "dictionary_entries",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_de_user_word_video",
           columnNames = {"user_id", "word_id", "video_id"}))
public class DictionaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "word_id", nullable = false)
    private DictionaryWord word;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "video_id", length = 30, nullable = false)
    private String videoId;

    @Column(name = "video_title", length = 500)
    private String videoTitle;

    /** Translation of the word in the target language. */
    @Column(columnDefinition = "TEXT")
    private String translation;

    @Column(name = "target_language", length = 10, nullable = false)
    private String targetLanguage;

    /** Full subtitle sentence where the word appeared (source track). */
    @Column(name = "source_sentence", columnDefinition = "TEXT")
    private String sourceSentence;

    /** Translated subtitle sentence (target track). */
    @Column(name = "translated_sentence", columnDefinition = "TEXT")
    private String translatedSentence;

    /** Position in the video when the word was saved (ms). */
    @Column(name = "video_timing_ms")
    private Long videoTimingMs;

    /** User's personal note about this word in this video context. */
    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ── getters / setters ─────────────────────────────────────────────────────

    public Long getId()                           { return id; }
    public void setId(Long id)                    { this.id = id; }

    public DictionaryWord getWord()               { return word; }
    public void setWord(DictionaryWord word)      { this.word = word; }

    public User getUser()                         { return user; }
    public void setUser(User user)                { this.user = user; }

    public String getVideoId()                    { return videoId; }
    public void setVideoId(String v)              { this.videoId = v; }

    public String getVideoTitle()                 { return videoTitle; }
    public void setVideoTitle(String v)           { this.videoTitle = v; }

    public String getTranslation()                { return translation; }
    public void setTranslation(String v)          { this.translation = v; }

    public String getTargetLanguage()             { return targetLanguage; }
    public void setTargetLanguage(String v)       { this.targetLanguage = v; }

    public String getSourceSentence()             { return sourceSentence; }
    public void setSourceSentence(String v)       { this.sourceSentence = v; }

    public String getTranslatedSentence()         { return translatedSentence; }
    public void setTranslatedSentence(String v)   { this.translatedSentence = v; }

    public Long getVideoTimingMs()                { return videoTimingMs; }
    public void setVideoTimingMs(Long v)          { this.videoTimingMs = v; }

    public String getNotes()                      { return notes; }
    public void setNotes(String notes)            { this.notes = notes; }

    public LocalDateTime getCreatedAt()           { return createdAt; }
    public void setCreatedAt(LocalDateTime v)     { this.createdAt = v; }
}
