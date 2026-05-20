package com.dualsub.service;

import com.dualsub.model.DictionaryEntry;
import com.dualsub.model.DictionaryWord;
import com.dualsub.model.User;
import com.dualsub.repository.DictionaryEntryRepository;
import com.dualsub.repository.DictionaryWordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DictionaryService {

    private final DictionaryWordRepository  wordRepo;
    private final DictionaryEntryRepository entryRepo;
    private final WordFrequencyService      wordFreqService;
    private final HttpClient                httpClient;
    private final ObjectMapper              objectMapper = new ObjectMapper();

    public DictionaryService(DictionaryWordRepository wordRepo,
                             DictionaryEntryRepository entryRepo,
                             WordFrequencyService wordFreqService,
                             SSLContext appSslContext) {
        this.wordRepo        = wordRepo;
        this.entryRepo       = entryRepo;
        this.wordFreqService = wordFreqService;
        this.httpClient = HttpClient.newBuilder()
            .sslContext(appSslContext)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    /** Flat view returned to the frontend for the DICO panel. */
    public static class DictionaryItemDto {
        public Long         entryId;
        public Long         wordId;
        public String       word;
        public String       sourceLanguage;
        public Integer      frequencyRank;
        public java.util.List<String> tags;
        public String       translation;
        public String       targetLanguage;
        public String       videoId;
        public String       videoTitle;
        public String       sourceSentence;
        public String       translatedSentence;
        public Long         videoTimingMs;
        public String       notes;
        public String       createdAt;   // ISO-8601
    }

    /** Request body for the lookup endpoint. */
    public static class LookupRequest {
        public String word;
        public String sourceLang;
        public String targetLang;
        public String videoId;
        public String videoTitle;
        public String sourceSentence;
        public String translatedSentence;
        public Long   timingMs;
    }

    /** Response from the lookup endpoint. */
    public static class LookupResponse {
        public String  translation;
        public boolean alreadySaved;
        public Long    entryId;
        public Long    wordId;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Translate a word and save it to the user's dictionary.
     * If (user, word, sourceLang, videoId) already exists the existing entry
     * is returned without creating a duplicate.
     */
    @Transactional
    public LookupResponse lookup(User user, LookupRequest req) throws IOException, InterruptedException {
        String normalizedWord = req.word.trim().toLowerCase(Locale.ROOT);

        // 1. Find or create the DictionaryWord
        DictionaryWord dw = wordRepo
            .findByUser_IdAndWordAndSourceLanguage(user.getId(), normalizedWord, req.sourceLang)
            .orElseGet(() -> {
                DictionaryWord w = new DictionaryWord();
                w.setUser(user);
                w.setWord(normalizedWord);
                w.setSourceLanguage(req.sourceLang);
                // Best-effort frequency rank from wordfreq (null if Python/wordfreq
                // unavailable or the word is rarer than the top 100k).
                w.setFrequencyRank(wordFreqService.lookupRank(normalizedWord, req.sourceLang));
                w.setCreatedAt(LocalDateTime.now());
                return wordRepo.save(w);
            });

        // 1b. If the word already existed but its frequency rank was never computed
        //     (saved before this feature was deployed), fill it in now.
        if (dw.getFrequencyRank() == null) {
            Integer rank = wordFreqService.lookupRank(normalizedWord, req.sourceLang);
            if (rank != null) {
                dw.setFrequencyRank(rank);
                wordRepo.save(dw);
            }
        }

        // 2. Find or create the DictionaryEntry for this video
        Optional<DictionaryEntry> existing =
            entryRepo.findByUser_IdAndWord_IdAndVideoId(user.getId(), dw.getId(), req.videoId);

        LookupResponse resp = new LookupResponse();

        if (existing.isPresent()) {
            DictionaryEntry entry = existing.get();
            resp.translation  = entry.getTranslation();
            resp.alreadySaved = true;
            resp.entryId      = entry.getId();
            resp.wordId       = dw.getId();
            return resp;
        }

        // 3. Translate the word
        String translation = translateWord(normalizedWord, req.sourceLang, req.targetLang);

        // 4. Persist entry
        DictionaryEntry entry = new DictionaryEntry();
        entry.setWord(dw);
        entry.setUser(user);
        entry.setVideoId(req.videoId);
        entry.setVideoTitle(req.videoTitle);
        entry.setTranslation(translation);
        entry.setTargetLanguage(req.targetLang);
        entry.setSourceSentence(req.sourceSentence);
        entry.setTranslatedSentence(req.translatedSentence);
        entry.setVideoTimingMs(req.timingMs);
        entry.setCreatedAt(LocalDateTime.now());
        entryRepo.save(entry);

        resp.translation  = translation;
        resp.alreadySaved = false;
        resp.entryId      = entry.getId();
        resp.wordId       = dw.getId();
        return resp;
    }

    /**
     * Return the user's dictionary, flattened to one row per entry.
     *
     * @param sort     "alpha" | "date" (default: date desc)
     * @param videoId  filter to a specific video (null = all)
     * @param from     filter to entries created on or after this date (null = all)
     * @param lang     filter to a specific source language (null = all)
     * @param tag      filter to entries whose word has this tag (null = all)
     */
    @Transactional(readOnly = true)
    public List<DictionaryItemDto> list(Long userId, String sort,
                                        String videoId, LocalDate from,
                                        String lang, String tag) {
        List<DictionaryEntry> entries;

        if (tag != null && !tag.isBlank()) {
            entries = entryRepo.findByUserIdAndTag(userId, tag);
        } else if (videoId != null && !videoId.isBlank()) {
            entries = entryRepo.findByUser_IdAndVideoIdOrderByCreatedAtDesc(userId, videoId);
        } else if (from != null) {
            entries = entryRepo.findByUser_IdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                userId, from.atStartOfDay());
        } else {
            entries = entryRepo.findByUser_IdOrderByCreatedAtDesc(userId);
        }

        // Language filter (applied in memory — simple enough)
        if (lang != null && !lang.isBlank()) {
            String langFilter = lang;
            entries = entries.stream()
                .filter(e -> langFilter.equals(e.getWord().getSourceLanguage()))
                .collect(Collectors.toList());
        }

        List<DictionaryItemDto> dtos = entries.stream().map(this::toDto).collect(Collectors.toList());

        if ("alpha".equalsIgnoreCase(sort)) {
            dtos.sort((a, b) -> {
                String wa = a.word == null ? "" : a.word;
                String wb = b.word == null ? "" : b.word;
                return wa.compareToIgnoreCase(wb);
            });
        }
        return dtos;
    }

    /** Update personal notes on an entry. */
    @Transactional
    public void updateNotes(Long userId, Long entryId, String notes) {
        DictionaryEntry entry = entryRepo.findById(entryId)
            .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + entryId));
        if (!entry.getUser().getId().equals(userId)) {
            throw new SecurityException("Not your entry");
        }
        entry.setNotes(notes);
        entryRepo.save(entry);
    }

    /**
     * Replace the tag list on a word.
     * Tags are normalised (trimmed, lowercased) and capped at 5.
     */
    @Transactional
    public void updateTags(Long userId, Long wordId, List<String> rawTags) {
        DictionaryWord word = wordRepo.findById(wordId)
            .orElseThrow(() -> new IllegalArgumentException("Word not found: " + wordId));
        if (!word.getUser().getId().equals(userId)) {
            throw new SecurityException("Not your word");
        }
        List<String> source = rawTags != null ? rawTags : new ArrayList<>();
        List<String> normalised = source.stream()
            .map(t -> t.trim().toLowerCase(Locale.ROOT))
            .filter(t -> !t.isBlank())
            .distinct()
            .limit(5)
            .collect(Collectors.toList());
        word.getTags().clear();
        word.getTags().addAll(normalised);
        wordRepo.save(word);
    }

    /** Delete a single entry (keeps the word if it has entries in other videos). */
    @Transactional
    public void deleteEntry(Long userId, Long entryId) {
        DictionaryEntry entry = entryRepo.findById(entryId)
            .orElseThrow(() -> new IllegalArgumentException("Entry not found: " + entryId));
        if (!entry.getUser().getId().equals(userId)) {
            throw new SecurityException("Not your entry");
        }
        DictionaryWord word = entry.getWord();
        entryRepo.delete(entry);

        // Remove the word if it no longer has any entries
        if (entryRepo.findByWord_IdAndUser_Id(word.getId(), userId).isEmpty()) {
            wordRepo.delete(word);
        }
    }

    /** Delete a word and all its entries across all videos. */
    @Transactional
    public void deleteWord(Long userId, Long wordId) {
        DictionaryWord word = wordRepo.findById(wordId)
            .orElseThrow(() -> new IllegalArgumentException("Word not found: " + wordId));
        if (!word.getUser().getId().equals(userId)) {
            throw new SecurityException("Not your word");
        }
        wordRepo.delete(word);  // cascade deletes entries
    }

    /** Delete all entries (and orphaned words) for a given video. */
    @Transactional
    public void deleteByVideo(Long userId, String videoId) {
        entryRepo.deleteByUserIdAndVideoId(userId, videoId);
        pruneOrphanedWords(userId);
    }

    /**
     * Delete all entries created strictly before a given date.
     * For UI: "delete words added before 2025-06-01" = delete entries with createdAt < that date.
     */
    @Transactional
    public void deleteByDate(Long userId, LocalDate before) {
        entryRepo.deleteByUserIdAndCreatedAtBefore(userId, before.atStartOfDay());
        pruneOrphanedWords(userId);
    }

    /** Delete the entire dictionary for this user. */
    @Transactional
    public void deleteAll(Long userId) {
        entryRepo.deleteAllByUserId(userId);
        wordRepo.deleteAllByUserId(userId);
    }

    /**
     * Translate a word without saving it to the dictionary.
     * Used by the hover bar to show a translation before the user decides to save.
     */
    public String translateOnly(String word, String sourceLang, String targetLang)
            throws IOException, InterruptedException {
        return translateWord(word.trim().toLowerCase(Locale.ROOT), sourceLang, targetLang);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private DictionaryItemDto toDto(DictionaryEntry e) {
        DictionaryItemDto d = new DictionaryItemDto();
        d.entryId            = e.getId();
        d.wordId             = e.getWord().getId();
        d.word               = e.getWord().getWord();
        d.sourceLanguage     = e.getWord().getSourceLanguage();
        d.frequencyRank      = e.getWord().getFrequencyRank();
        d.translation        = e.getTranslation();
        d.targetLanguage     = e.getTargetLanguage();
        d.videoId            = e.getVideoId();
        d.videoTitle         = e.getVideoTitle();
        d.sourceSentence     = e.getSourceSentence();
        d.translatedSentence = e.getTranslatedSentence();
        d.videoTimingMs      = e.getVideoTimingMs();
        d.notes              = e.getNotes();
        d.tags               = new ArrayList<>(e.getWord().getTags());
        d.createdAt          = e.getCreatedAt().toString();
        return d;
    }

    /** Remove DictionaryWord rows that have no entries left. */
    private void pruneOrphanedWords(Long userId) {
        List<DictionaryWord> words = wordRepo.findByUser_IdOrderByWordAsc(userId);
        for (DictionaryWord w : words) {
            if (entryRepo.findByWord_IdAndUser_Id(w.getId(), userId).isEmpty()) {
                wordRepo.delete(w);
            }
        }
    }

    /**
     * Translate a single word using Google Translate's unofficial `gtx` endpoint.
     * Returns the translated word, or the original if the call fails.
     */
    private String translateWord(String word, String sourceLang, String targetLang)
            throws IOException, InterruptedException {
        try {
            String encoded = URLEncoder.encode(word, StandardCharsets.UTF_8);
            String url = "https://translate.googleapis.com/translate_a/single"
                + "?client=gtx&sl=" + sourceLang + "&tl=" + targetLang
                + "&dt=t&dt=bd&q=" + encoded;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .GET()
                .build();

            HttpResponse<byte[]> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() >= 400) {
                System.err.println("[Dictionary] Translate error HTTP " + response.statusCode()
                    + " for word: " + word);
                return word;
            }

            String body = new String(response.body(), StandardCharsets.UTF_8);
            return parseTranslations(body, word);

        } catch (Exception e) {
            System.err.println("[Dictionary] Translation failed for '" + word + "': " + e.getMessage());
            return word;
        }
    }

    /**
     * Extract all alternative translations from the `bd` section of the Google Translate
     * response. Returns a comma-separated string of translations (most relevant first).
     * Falls back to the plain `t` (sentence) translation if `bd` is not available.
     *
     * Response shape (simplified):
     *   [ [["translated","original",...], ...], null, "sl",
     *     null, null, null, null,
     *     [ ["pos", [ ["alt1","alt2",...], ... ], ...], ... ]   ← index 1 of bd entry
     *   ]
     */
    private String parseTranslations(String json, String fallback) {
        try {
            JsonNode root = objectMapper.readTree(json);

            // Try `bd` (detailed bilingual dictionary) section at root[5]
            if (root.isArray() && root.size() > 5 && !root.get(5).isNull()
                    && root.get(5).isArray() && !root.get(5).isEmpty()) {
                Set<String> seen = new LinkedHashSet<>();
                for (JsonNode posGroup : root.get(5)) {
                    // posGroup: ["noun", [ ["translation", "..."], ... ], ...]
                    if (posGroup.isArray() && posGroup.size() > 1 && posGroup.get(1).isArray()) {
                        for (JsonNode altGroup : posGroup.get(1)) {
                            if (altGroup.isArray() && !altGroup.isEmpty()) {
                                seen.add(altGroup.get(0).asText("").trim());
                            }
                        }
                    }
                }
                if (!seen.isEmpty()) {
                    return String.join(", ", seen);
                }
            }

            // Fallback: plain sentence translation at root[0]
            if (root.isArray() && !root.isEmpty() && root.get(0).isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode sentence : root.get(0)) {
                    if (sentence.isArray() && !sentence.isEmpty()) {
                        sb.append(sentence.get(0).asText(""));
                    }
                }
                String t = sb.toString().trim();
                return t.isEmpty() ? fallback : t;
            }

        } catch (Exception e) {
            System.err.println("[Dictionary] Parse error: " + e.getMessage());
        }
        return fallback;
    }
}
