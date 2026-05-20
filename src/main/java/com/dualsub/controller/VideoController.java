package com.dualsub.controller;

import com.dualsub.model.ProcessRequest;
import com.dualsub.model.ProcessResponse;
import com.dualsub.model.SubtitleEntry;
import com.dualsub.model.User;
import com.dualsub.service.PersistenceService;
import com.dualsub.service.RefinementService;
import com.dualsub.service.TranslationService;
import com.dualsub.service.UserService;
import com.dualsub.service.YouTubeTranscriptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@RestController
@RequestMapping("/api")
public class VideoController {

    private final YouTubeTranscriptService youTubeService;
    private final TranslationService translationService;
    private final PersistenceService persistenceService;
    private final UserService        userService;
    private final RefinementService  refinementService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VideoController(YouTubeTranscriptService youTubeService,
                           TranslationService translationService,
                           PersistenceService persistenceService,
                           UserService userService,
                           RefinementService refinementService) {
        this.youTubeService     = youTubeService;
        this.translationService = translationService;
        this.persistenceService = persistenceService;
        this.userService        = userService;
        this.refinementService  = refinementService;
    }

    @GetMapping("/languages")
    public Map<String, String> getLanguages() {
        return TranslationService.LANGUAGES;
    }

    /** Full diagnostic: tests each step of the transcript extraction pipeline. */
    @GetMapping("/debug/transcript")
    public ResponseEntity<?> debugTranscript(@RequestParam String url) {
        try {
            String videoId = youTubeService.extractVideoId(url);
            YouTubeTranscriptService.DiagInfo info = youTubeService.diagnose(videoId);
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /** Returns the first N raw subtitles (source language, no translation). */
    @GetMapping("/debug/subtitles")
    public ResponseEntity<?> debugSubtitles(@RequestParam String url,
                                            @RequestParam(defaultValue = "10") int n) {
        try {
            String videoId = youTubeService.extractVideoId(url);
            List<SubtitleEntry> subs = youTubeService.fetchTranscript(videoId);
            System.out.println("[Debug] " + subs.size() + " subtitles fetched for " + videoId);
            List<SubtitleEntry> preview = subs.subList(0, Math.min(n, subs.size()));
            return ResponseEntity.ok(Map.of(
                "videoId", videoId,
                "total",   subs.size(),
                "preview", preview
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Tests YouTube's tlang parameter for a given video.
     * Returns up to 5 sample subtitles per language to verify translation quality.
     */
    @GetMapping("/poc/tlang")
    public ResponseEntity<?> pocTlang(
            @RequestParam(defaultValue = "2KAmqiTpa8Y") String videoId) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("videoId", videoId);

            String baseUrl = youTubeService.fetchCaptionBaseUrl(videoId);
            if (baseUrl == null) {
                return ResponseEntity.ok(Map.of(
                    "videoId", videoId,
                    "error",   "No caption tracks found for this video."
                ));
            }
            String cleanBase = baseUrl.replaceAll("&fmt=[^&]*", "");
            result.put("captionBaseUrl", cleanBase.length() > 150
                ? cleanBase.substring(0, 150) + "…" : cleanBase);

            Map<String, Object> langs = new LinkedHashMap<>();
            for (String lang : List.of("fr", "en", "es", "it", "de")) {
                try {
                    if (!langs.isEmpty()) Thread.sleep(1500);
                    List<SubtitleEntry> subs = youTubeService.fetchTranscriptFromBaseUrl(cleanBase, lang);
                    langs.put(lang, Map.of(
                        "total",  subs.size(),
                        "sample", subs.subList(0, Math.min(5, subs.size()))
                    ));
                } catch (Exception e) {
                    langs.put(lang, Map.of("error", e.getMessage()));
                }
            }
            result.put("languages", langs);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * SSE endpoint: streams pipeline progress then the final result.
     *
     * When the transcript is already cached, the punctuation and sentences stages are
     * skipped and a single "transcript" event with cached=true is emitted instead,
     * allowing the browser to jump directly to the translation steps.
     *
     * Using GET (not POST) because EventSource only supports GET requests.
     */
    @GetMapping(value = "/process/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter processStream(
            @RequestParam String videoUrl,
            @RequestParam String lang1,
            @RequestParam String lang2,
            @RequestParam(defaultValue = "false") boolean ollama,
            @RequestParam(defaultValue = "none") String whisperModel,
            Principal principal) {

        SseEmitter emitter = new SseEmitter(300_000L); // 5-minute timeout

        // Cancellation flag: set to true when the client closes the SSE connection.
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onTimeout(()    -> cancelled.set(true));
        emitter.onError(e       -> cancelled.set(true));

        // Resolve current user before entering the async thread (SecurityContext is thread-local)
        final User currentUser = (principal != null)
            ? userService.findByEmail(principal.getName()).orElse(null)
            : null;

        executor.submit(() -> {
            try {
                // ── Weekly quota check (LIMITED users only) ───────────────────
                if (currentUser != null) {
                    try {
                        userService.checkAndIncrementWeeklyView(currentUser.getId());
                    } catch (UserService.WeeklyLimitExceededException e) {
                        emitter.send(SseEmitter.event().name("apierror").data(
                            "{\"error\":\"Limite hebdomadaire atteinte ("
                            + e.getLimit() + " vidéos). Quota réinitialisé le "
                            + e.getNextReset() + ".\",\"limitReached\":true}"));
                        emitter.complete();
                        return;
                    }
                }

                Consumer<String> progress = json -> {
                    try { emitter.send(SseEmitter.event().name("progress").data(json)); }
                    catch (Exception ignored) { /* client disconnected */ }
                };

                String videoId = youTubeService.extractVideoId(videoUrl);
                System.out.println("[Stream] Processing: " + videoId
                    + " | lang1=" + lang1 + " lang2=" + lang2);

                // ── Stage 1: transcript ───────────────────────────────────────
                // Check the transcript cache first to skip the slow Python pipeline.
                List<SubtitleEntry> original;
                String detectedCode;

                Optional<YouTubeTranscriptService.TranscriptResult> cachedResult =
                    persistenceService.getCachedTranscript(videoId);

                if (cachedResult.isPresent()) {
                    // Cache HIT — skip Python, punctuation, and sentence-split stages
                    progress.accept("{\"step\":\"transcript\",\"cached\":true}");
                    original     = cachedResult.get().entries;
                    detectedCode = cachedResult.get().languageCode;
                    System.out.println("[Stream] Transcript cache HIT: " + videoId
                        + " (" + original.size() + " entries)");
                } else {
                    // Cache MISS — run the full pipeline (emits punctuation + sentences internally)
                    progress.accept("{\"step\":\"transcript\"}");
                    YouTubeTranscriptService.TranscriptResult transcript =
                        youTubeService.fetchTranscriptFull(videoId, progress);
                    original     = transcript.entries;
                    detectedCode = transcript.languageCode;

                    if (!original.isEmpty()) {
                        persistenceService.cacheTranscript(videoId, transcript);
                    }
                }

                // ── Whisper fallback: if YouTube returned nothing AND Whisper was requested ──
                if (original.isEmpty() && !"none".equals(whisperModel)) {
                    progress.accept("{\"step\":\"whisper\"}");
                    try {
                        YouTubeTranscriptService.TranscriptResult whisperResult =
                            youTubeService.fetchWithWhisper(videoId, whisperModel, progress);
                        original     = whisperResult.entries;
                        detectedCode = whisperResult.languageCode;
                        if (!original.isEmpty()) {
                            persistenceService.cacheTranscript(videoId, whisperResult);
                        }
                    } catch (Exception e) {
                        System.err.println("[Whisper] Fallback failed: " + e.getMessage());
                    }
                }

                if (original.isEmpty()) {
                    emitter.send(SseEmitter.event().name("apierror").data(
                        "{\"error\":\"Transcript vide. La vidéo doit avoir des sous-titres activés.\"}"));
                    emitter.complete();
                    return;
                }

                // ── Cancellation check after transcript ───────────────────────────
                if (cancelled.get()) { emitter.complete(); return; }

                final boolean lang1Auto    = "auto".equals(lang1);
                final String  finalDetCode = detectedCode;
                final String  sourceCode   = finalDetCode != null ? finalDetCode : "auto";

                final String lang1Label = lang1Auto
                    ? (detectedCode != null
                        ? TranslationService.LANGUAGES.getOrDefault(detectedCode, detectedCode.toUpperCase())
                        : "Source")
                    : TranslationService.LANGUAGES.getOrDefault(lang1, lang1);
                final String lang2Label = TranslationService.LANGUAGES.getOrDefault(lang2, lang2);

                // ── Ollama Step 1: correct transcript BEFORE Google Translate ─
                // Only for fresh fetches (cache hits are already processed).
                final boolean ollamaActive = ollama && refinementService.isOllamaAvailable();
                final List<SubtitleEntry> originalEntries;
                if (ollamaActive && cachedResult.isEmpty()) {
                    progress.accept("{\"step\":\"ollama_transcript\"}");
                    List<SubtitleEntry> corrected = original;
                    try {
                        corrected = refinementService.getOllamaService()
                            .correctTranscript(original, sourceCode);
                        System.out.println("[Ollama] Transcript corrected: "
                            + corrected.size() + " entries");
                    } catch (Exception e) {
                        System.err.println("[Ollama] Transcript correction failed: " + e.getMessage());
                    }
                    originalEntries = corrected;
                } else {
                    originalEntries = original;
                }

                // ── Cancellation check before translations ────────────────────
                if (cancelled.get()) { emitter.complete(); return; }

                // ── Stages 4 & 5: translate both tracks in parallel ───────────
                // Check translation caches before submitting tasks
                final Optional<List<SubtitleEntry>> translCached1 = lang1Auto
                    ? Optional.empty()
                    : persistenceService.getCachedTranslation(videoId, lang1);
                final Optional<List<SubtitleEntry>> translCached2 =
                    persistenceService.getCachedTranslation(videoId, lang2);

                // Submit both tasks: cache hit = immediate return, miss = Google Translate
                Future<List<SubtitleEntry>> future1 = executor.submit(() -> {
                    if (lang1Auto) return originalEntries;
                    return translCached1.orElseGet(() -> {
                        try { return translationService.translate(originalEntries, lang1); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); return originalEntries; }
                    });
                });
                Future<List<SubtitleEntry>> future2 = executor.submit(() ->
                    translCached2.orElseGet(() -> {
                        try { return translationService.translate(originalEntries, lang2); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); return originalEntries; }
                    })
                );

                // Emit progress with cache status per track
                progress.accept("{\"step\":\"translation1\",\"cached\":"
                    + translCached1.isPresent() + "}");
                progress.accept("{\"step\":\"translation2\",\"cached\":"
                    + translCached2.isPresent() + "}");

                List<SubtitleEntry> subtitles1 = future1.get();
                List<SubtitleEntry> subtitles2 = future2.get();

                System.out.println("[Stream] Done: " + subtitles1.size()
                    + " [" + (lang1Auto ? "auto→" + finalDetCode : lang1)
                    + (translCached1.isPresent() ? " (cached)" : "") + "] / "
                    + subtitles2.size() + " [" + lang2 + "]"
                    + (translCached2.isPresent() ? " (cached)" : ""));

                // Persist GT translations to cache (async, non-blocking)
                if (!lang1Auto && translCached1.isEmpty()) {
                    final List<SubtitleEntry> s1 = subtitles1;
                    executor.submit(() -> persistenceService.cacheTranslation(videoId, lang1, s1));
                }
                if (translCached2.isEmpty()) {
                    final List<SubtitleEntry> s2 = subtitles2;
                    executor.submit(() -> persistenceService.cacheTranslation(videoId, lang2, s2));
                }

                // ── Record watch history (best-effort, non-blocking for SSE) ──
                final String effectiveLang1 = lang1Auto ? "auto" : lang1;
                final Long userId = (currentUser != null) ? currentUser.getId() : null;
                executor.submit(() ->
                    persistenceService.recordWatch(videoId, effectiveLang1, lang2, userId));

                // ── Cancellation check before Ollama refinement ──────────────
                if (cancelled.get()) { emitter.complete(); return; }

                // ── Ollama Step 2: refine GT translations ─────────────────────
                List<SubtitleEntry> outSubs1 = subtitles1;
                List<SubtitleEntry> outSubs2 = subtitles2;

                if (ollamaActive) {
                    progress.accept("{\"step\":\"ollama_translation\"}");
                    try {
                        if (!lang1Auto) {
                            outSubs1 = refinementService.getOllamaService()
                                .refine(originalEntries, subtitles1, sourceCode, lang1, null);
                        }
                        outSubs2 = refinementService.getOllamaService()
                            .refine(originalEntries, subtitles2, sourceCode, lang2, null);
                        System.out.println("[Ollama] Translations refined.");
                    } catch (Exception e) {
                        System.err.println("[Ollama] Translation refinement failed: " + e.getMessage());
                    }
                }

                // ── Send complete event ───────────────────────────────────────
                ProcessResponse resp = new ProcessResponse();
                resp.setVideoId(videoId);
                resp.setSubtitles1(outSubs1);
                resp.setSubtitles2(outSubs2);
                resp.setLang1Label(lang1Label);
                resp.setLang2Label(lang2Label);
                resp.setLang1Code(lang1Auto ? (finalDetCode != null ? finalDetCode : "auto") : lang1);
                resp.setLang2Code(lang2);

                emitter.send(SseEmitter.event().name("complete").data(
                    objectMapper.writeValueAsString(resp)));
                emitter.complete();

            } catch (IllegalArgumentException e) {
                streamError(emitter, e.getMessage());
            } catch (Exception e) {
                streamError(emitter, "Erreur serveur : " + e.getMessage());
            }
        });

        return emitter;
    }

    private void streamError(SseEmitter emitter, String message) {
        try {
            String safe = (message == null ? "Erreur inconnue" : message).replace("\"", "'");
            emitter.send(SseEmitter.event().name("apierror").data("{\"error\":\"" + safe + "\"}"));
        } catch (Exception ignored) {}
        try { emitter.complete(); } catch (Exception ignored) {}
    }

    /** Legacy synchronous endpoint — not used by the current frontend. */
    @PostMapping("/process")
    public ResponseEntity<?> processVideo(@RequestBody ProcessRequest request) {
        try {
            String videoId = youTubeService.extractVideoId(request.getVideoUrl());
            System.out.println("[Controller] Processing video: " + videoId
                + " | lang1=" + request.getLang1() + " lang2=" + request.getLang2());

            List<SubtitleEntry> original = youTubeService.fetchTranscript(videoId);
            System.out.println("[Controller] Source transcript: " + original.size() + " entries");

            if (original.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Empty transcript for this video. "
                           + "The video must have captions enabled (auto-generated or manual)."
                ));
            }

            List<SubtitleEntry> subtitles1 = translationService.translate(original, request.getLang1());
            List<SubtitleEntry> subtitles2 = translationService.translate(original, request.getLang2());

            ProcessResponse resp = new ProcessResponse();
            resp.setVideoId(videoId);
            resp.setSubtitles1(subtitles1);
            resp.setSubtitles2(subtitles2);
            resp.setLang1Label(TranslationService.LANGUAGES.getOrDefault(
                request.getLang1(), request.getLang1()));
            resp.setLang2Label(TranslationService.LANGUAGES.getOrDefault(
                request.getLang2(), request.getLang2()));

            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Server error: " + e.getMessage()));
        }
    }
}
