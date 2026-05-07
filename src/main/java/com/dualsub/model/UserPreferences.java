package com.dualsub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Singleton entity (always id=1) storing the user's language preferences.
 * Persisted to H2 so selections survive server restarts.
 */
@Entity
@Table(name = "user_preferences")
public class UserPreferences {

    /** Always 1 — this table has exactly one row. */
    @Id
    private Long id = 1L;

    @Column(name = "lang1", length = 10, nullable = false)
    private String lang1 = "fr";

    @Column(name = "lang2", length = 10, nullable = false)
    private String lang2 = "de";

    @Column(name = "immersion_mode", nullable = false)
    private boolean immersionMode = false;

    @Column(name = "ui_lang", length = 10, nullable = false)
    private String uiLang = "fr";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId()                   { return id; }
    public void setId(Long id)            { this.id = id; }

    public String getLang1()              { return lang1; }
    public void setLang1(String lang1)    { this.lang1 = lang1; }

    public String getLang2()              { return lang2; }
    public void setLang2(String lang2)    { this.lang2 = lang2; }

    public boolean isImmersionMode()                    { return immersionMode; }
    public void setImmersionMode(boolean immersionMode) { this.immersionMode = immersionMode; }

    public String getUiLang()             { return uiLang; }
    public void setUiLang(String uiLang)  { this.uiLang = uiLang; }

    public LocalDateTime getUpdatedAt()                   { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt)     { this.updatedAt = updatedAt; }
}
