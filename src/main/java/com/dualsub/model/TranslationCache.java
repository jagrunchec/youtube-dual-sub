package com.dualsub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Caches the translated subtitle list for a (videoId, lang) pair.
 * A cache hit skips the Google Translate API call entirely on subsequent views.
 */
@Entity
@Table(name = "translation_cache")
public class TranslationCache {

    @EmbeddedId
    private TranslationCacheId id;

    /** JSON-serialised List&lt;SubtitleEntry&gt; in the target language. */
    @Lob
    @Column(name = "entries_json", nullable = false)
    private String entriesJson;

    @Column(name = "translated_at", nullable = false)
    private LocalDateTime translatedAt;

    @Column(name = "entry_count", nullable = false)
    private int entryCount;

    public TranslationCacheId getId()                   { return id; }
    public void setId(TranslationCacheId id)            { this.id = id; }

    public String getEntriesJson()                      { return entriesJson; }
    public void setEntriesJson(String entriesJson)      { this.entriesJson = entriesJson; }

    public LocalDateTime getTranslatedAt()                    { return translatedAt; }
    public void setTranslatedAt(LocalDateTime translatedAt)   { this.translatedAt = translatedAt; }

    public int getEntryCount()                          { return entryCount; }
    public void setEntryCount(int entryCount)           { this.entryCount = entryCount; }
}
