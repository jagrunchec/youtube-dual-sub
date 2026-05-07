package com.dualsub.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Composite primary key for {@link TranslationCache}: (videoId, lang). */
@Embeddable
public class TranslationCacheId implements Serializable {

    @Column(name = "video_id", length = 30, nullable = false)
    private String videoId;

    @Column(name = "lang", length = 10, nullable = false)
    private String lang;

    public TranslationCacheId() {}

    public TranslationCacheId(String videoId, String lang) {
        this.videoId = videoId;
        this.lang    = lang;
    }

    public String getVideoId() { return videoId; }
    public String getLang()    { return lang; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TranslationCacheId that)) return false;
        return Objects.equals(videoId, that.videoId) && Objects.equals(lang, that.lang);
    }

    @Override
    public int hashCode() { return Objects.hash(videoId, lang); }
}
