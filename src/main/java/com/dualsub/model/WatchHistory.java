package com.dualsub.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.dualsub.model.User;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * One row per video-watch event.
 * Duplicate watches are allowed (same video with different languages or at different times).
 */
@Entity
@Table(name = "watch_history")
public class WatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_id", length = 30, nullable = false)
    private String videoId;

    /** Title fetched from YouTube oEmbed — may be null if the call failed. */
    @Column(name = "video_title", length = 500)
    private String videoTitle;

    /** Standard YouTube thumbnail URL (mqdefault.jpg, 320×180). */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /** Primary language code chosen by the user (or "auto" for immersion mode). */
    @Column(name = "lang1", length = 10, nullable = false)
    private String lang1;

    /** Secondary language code chosen by the user. */
    @Column(name = "lang2", length = 10, nullable = false)
    private String lang2;

    @Column(name = "watched_at", nullable = false)
    private LocalDateTime watchedAt;

    /** The authenticated user who watched — null for anonymous watches (legacy rows). */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    public Long getId()                             { return id; }
    public void setId(Long id)                      { this.id = id; }

    public String getVideoId()                      { return videoId; }
    public void setVideoId(String videoId)          { this.videoId = videoId; }

    public String getVideoTitle()                         { return videoTitle; }
    public void setVideoTitle(String videoTitle)          { this.videoTitle = videoTitle; }

    public String getThumbnailUrl()                       { return thumbnailUrl; }
    public void setThumbnailUrl(String thumbnailUrl)      { this.thumbnailUrl = thumbnailUrl; }

    public String getLang1()                        { return lang1; }
    public void setLang1(String lang1)              { this.lang1 = lang1; }

    public String getLang2()                        { return lang2; }
    public void setLang2(String lang2)              { this.lang2 = lang2; }

    public LocalDateTime getWatchedAt()                   { return watchedAt; }
    public void setWatchedAt(LocalDateTime watchedAt)     { this.watchedAt = watchedAt; }

    public User getUser()                                 { return user; }
    public void setUser(User user)                        { this.user = user; }
}
