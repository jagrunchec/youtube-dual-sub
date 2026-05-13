package com.dualsub.service;

import com.dualsub.model.TranscriptCache;
import com.dualsub.model.TranslationCache;
import com.dualsub.model.TranslationCacheId;
import com.dualsub.model.UserPreferences;
import com.dualsub.model.WatchHistory;
import com.dualsub.repository.TranscriptCacheRepository;
import com.dualsub.repository.TranslationCacheRepository;
import com.dualsub.repository.UserPreferencesRepository;
import com.dualsub.repository.UserRepository;
import com.dualsub.repository.WatchHistoryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dualsub.model.SubtitleEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PersistenceService {

    private static final String OEMBED_URL =
        "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=%s&format=json";

    private static final String THUMBNAIL_URL =
        "https://img.youtube.com/vi/%s/mqdefault.jpg";

    private final UserPreferencesRepository   prefsRepo;
    private final TranscriptCacheRepository   cacheRepo;
    private final TranslationCacheRepository  translationCacheRepo;
    private final WatchHistoryRepository      historyRepo;
    private final UserRepository              userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;

    public PersistenceService(UserPreferencesRepository  prefsRepo,
                              TranscriptCacheRepository  cacheRepo,
                              TranslationCacheRepository translationCacheRepo,
                              WatchHistoryRepository     historyRepo,
                              UserRepository             userRepository,
                              SSLContext appSslContext) {
        this.prefsRepo            = prefsRepo;
        this.cacheRepo            = cacheRepo;
        this.translationCacheRepo = translationCacheRepo;
        this.historyRepo          = historyRepo;
        this.userRepository       = userRepository;
        this.httpClient = HttpClient.newBuilder()
            .sslContext(appSslContext)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    // ─── User preferences ──────────────────────────────────────────────────

    /** Returns the stored preferences, or sensible defaults if none saved yet. */
    public UserPreferences getOrCreatePreferences() {
        return prefsRepo.findById(1L).orElseGet(this::defaultPreferences);
    }

    @Transactional
    public UserPreferences savePreferences(UserPreferences prefs) {
        prefs.setId(1L);  // enforce singleton
        prefs.setUpdatedAt(LocalDateTime.now());
        return prefsRepo.save(prefs);
    }

    private UserPreferences defaultPreferences() {
        UserPreferences p = new UserPreferences();
        p.setId(1L);
        p.setLang1("fr");
        p.setLang2("de");
        p.setImmersionMode(false);
        p.setUiLang("fr");
        return p;
    }

    // ─── Transcript cache ───────────────────────────────────────────────────

    /**
     * Looks up the transcript cache for a given video.
     * Returns empty if not cached, or if deserialization fails (treated as cache miss).
     */
    public Optional<YouTubeTranscriptService.TranscriptResult> getCachedTranscript(String videoId) {
        return cacheRepo.findById(videoId).flatMap(cached -> {
            try {
                List<SubtitleEntry> entries = objectMapper.readValue(
                    cached.getEntriesJson(),
                    new TypeReference<List<SubtitleEntry>>() {}
                );
                return Optional.of(
                    new YouTubeTranscriptService.TranscriptResult(cached.getLanguageCode(), entries)
                );
            } catch (Exception e) {
                System.err.println("[Cache] Deserialization failed for " + videoId
                    + ": " + e.getMessage() + " — treating as cache miss");
                return Optional.empty();
            }
        });
    }

    /** Persists a transcript result for a video. Overwrites any existing entry. */
    @Transactional
    public void cacheTranscript(String videoId, YouTubeTranscriptService.TranscriptResult result) {
        try {
            String json = objectMapper.writeValueAsString(result.entries);
            TranscriptCache entry = new TranscriptCache();
            entry.setVideoId(videoId);
            entry.setLanguageCode(result.languageCode);
            entry.setEntriesJson(json);
            entry.setFetchedAt(LocalDateTime.now());
            entry.setEntryCount(result.entries.size());
            cacheRepo.save(entry);
            System.out.println("[Cache] Saved transcript for " + videoId
                + " (" + result.entries.size() + " entries, lang=" + result.languageCode + ")");
        } catch (Exception e) {
            System.err.println("[Cache] Failed to save transcript for " + videoId
                + ": " + e.getMessage());
        }
    }

    // ─── Translation cache ──────────────────────────────────────────────────

    /**
     * Looks up cached translated subtitles for a (videoId, lang) pair.
     * Returns empty on cache miss or if deserialization fails.
     */
    public Optional<List<SubtitleEntry>> getCachedTranslation(String videoId, String lang) {
        return translationCacheRepo.findById(new TranslationCacheId(videoId, lang))
            .flatMap(cached -> {
                try {
                    List<SubtitleEntry> entries = objectMapper.readValue(
                        cached.getEntriesJson(),
                        new TypeReference<List<SubtitleEntry>>() {}
                    );
                    System.out.println("[TranslationCache] HIT: " + videoId + "/" + lang
                        + " (" + entries.size() + " entries)");
                    return Optional.of(entries);
                } catch (Exception e) {
                    System.err.println("[TranslationCache] Deserialization failed for "
                        + videoId + "/" + lang + ": " + e.getMessage());
                    return Optional.empty();
                }
            });
    }

    /** Persists a translated subtitle list. Overwrites any existing entry for the same key. */
    @Transactional
    public void cacheTranslation(String videoId, String lang, List<SubtitleEntry> entries) {
        try {
            String json = objectMapper.writeValueAsString(entries);
            TranslationCache entry = new TranslationCache();
            entry.setId(new TranslationCacheId(videoId, lang));
            entry.setEntriesJson(json);
            entry.setTranslatedAt(LocalDateTime.now());
            entry.setEntryCount(entries.size());
            translationCacheRepo.save(entry);
            System.out.println("[TranslationCache] Saved " + entries.size()
                + " entries for " + videoId + "/" + lang);
        } catch (Exception e) {
            System.err.println("[TranslationCache] Failed to save for "
                + videoId + "/" + lang + ": " + e.getMessage());
        }
    }

    // ─── Watch history ──────────────────────────────────────────────────────

    /**
     * Records a watch event for a video.
     * Fetches the video title and thumbnail via YouTube oEmbed (best-effort, 5 s timeout).
     */
    @Transactional
    public void recordWatch(String videoId, String lang1, String lang2, Long userId) {
        try {
            String title        = null;
            String thumbnailUrl = String.format(THUMBNAIL_URL, videoId);

            // Try to get the real title via oEmbed (no API key required)
            try {
                String oembedUrl = String.format(OEMBED_URL, videoId);
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(oembedUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
                HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode node = objectMapper.readTree(response.body());
                    title = node.path("title").asText(null);
                    // oEmbed also returns a thumbnail_url but the CDN URL is more reliable
                }
            } catch (Exception e) {
                System.err.println("[History] oEmbed fetch failed for " + videoId
                    + ": " + e.getMessage());
            }

            // Upsert: find existing entry for this user+video pair to avoid duplicates.
            // Authenticated users get their own entry; anonymous requests get a shared one.
            WatchHistory entry = (userId != null)
                ? historyRepo.findTopByVideoIdAndUser_IdOrderByWatchedAtDesc(videoId, userId)
                             .orElse(new WatchHistory())
                : historyRepo.findTopByVideoIdAndUserIsNullOrderByWatchedAtDesc(videoId)
                             .orElse(new WatchHistory());

            entry.setVideoId(videoId);
            entry.setVideoTitle(title);
            entry.setThumbnailUrl(thumbnailUrl);
            entry.setLang1(lang1);
            entry.setLang2(lang2);
            entry.setWatchedAt(LocalDateTime.now());
            // Link to user if authenticated
            if (userId != null) {
                userRepository.findById(userId).ifPresent(entry::setUser);
            }
            historyRepo.save(entry);
            System.out.println("[History] Recorded watch: " + videoId
                + " [" + lang1 + "/" + lang2 + "]"
                + (title != null ? " \"" + title + "\"" : ""));
        } catch (Exception e) {
            System.err.println("[History] Failed to record watch for " + videoId
                + ": " + e.getMessage());
        }
    }

    /**
     * Returns the 20 most recent watch events, newest first, deduplicated by videoId.
     * Deduplication is done in-memory so that any rows created before the upsert fix
     * are also handled correctly.
     */
    public List<WatchHistory> getHistory() {
        List<WatchHistory> all = historyRepo.findTop20ByOrderByWatchedAtDesc();
        // LinkedHashMap preserves insertion order (newest first) and deduplicates by videoId
        Map<String, WatchHistory> seen = new LinkedHashMap<>();
        for (WatchHistory w : all) {
            seen.putIfAbsent(w.getVideoId(), w);
        }
        return new ArrayList<>(seen.values());
    }

    @Transactional
    public void deleteHistoryEntry(Long id) {
        historyRepo.deleteById(id);
    }
}
