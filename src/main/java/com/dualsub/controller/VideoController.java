package com.dualsub.controller;

import com.dualsub.model.ProcessRequest;
import com.dualsub.model.ProcessResponse;
import com.dualsub.model.SubtitleEntry;
import com.dualsub.service.TranslationService;
import com.dualsub.service.YouTubeTranscriptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@RestController
@RequestMapping("/api")
public class VideoController {

    private final YouTubeTranscriptService youTubeService;
    private final TranslationService translationService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VideoController(YouTubeTranscriptService youTubeService,
                           TranslationService translationService) {
        this.youTubeService = youTubeService;
        this.translationService = translationService;
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
     * Usage: GET /api/poc/tlang?videoId=VIDEO_ID
     */
    @GetMapping("/poc/tlang")
    public ResponseEntity<?> pocTlang(
            @RequestParam(defaultValue = "2KAmqiTpa8Y") String videoId) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("videoId", videoId);

            // Resolve the base caption URL once
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

            // Test tlang for each language (delay between calls to avoid 429)
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
     * Stages emitted: transcript → punctuation → sentences → translation1 → translation2 → complete.
     *
     * Using GET (not POST) because EventSource only supports GET requests.
     * Parameters are passed as query strings; Spring URL-decodes them automatically.
     */
    @GetMapping(value = "/process/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter processStream(
            @RequestParam String videoUrl,
            @RequestParam String lang1,
            @RequestParam String lang2) {

        SseEmitter emitter = new SseEmitter(300_000L); // 5-minute timeout

        executor.submit(() -> {
            try {
                Consumer<String> progress = json -> {
                    try { emitter.send(SseEmitter.event().name("progress").data(json)); }
                    catch (Exception ignored) { /* client disconnected */ }
                };

                String videoId = youTubeService.extractVideoId(videoUrl);
                System.out.println("[Stream] Processing: " + videoId
                    + " | lang1=" + lang1 + " lang2=" + lang2);

                // Stage 1: transcript (Python script + merge + punctuation + sentences)
                progress.accept("{\"step\":\"transcript\"}");
                YouTubeTranscriptService.TranscriptResult transcript =
                    youTubeService.fetchTranscriptFull(videoId, progress);
                List<SubtitleEntry> original = transcript.entries;

                if (original.isEmpty()) {
                    emitter.send(SseEmitter.event().name("apierror").data(
                        "{\"error\":\"Transcript vide. La vidéo doit avoir des sous-titres activés.\"}"));
                    emitter.complete();
                    return;
                }

                // Stages 4 & 5: translate both tracks in parallel.
                // When lang1="auto" (immersion mode), track 1 is served as-is (no network call).
                final boolean lang1Auto = "auto".equals(lang1);
                final String detectedCode = transcript.languageCode;

                final String lang1Label = lang1Auto
                    ? (detectedCode != null
                        ? TranslationService.LANGUAGES.getOrDefault(detectedCode, detectedCode.toUpperCase())
                        : "Source")
                    : TranslationService.LANGUAGES.getOrDefault(lang1, lang1);
                final String lang2Label = TranslationService.LANGUAGES.getOrDefault(lang2, lang2);

                // Submit both translation tasks concurrently before waiting on either result.
                Future<List<SubtitleEntry>> future1 = executor.submit(() ->
                    lang1Auto ? original : translationService.translate(original, lang1));
                Future<List<SubtitleEntry>> future2 = executor.submit(() ->
                    translationService.translate(original, lang2));

                // Notify the browser that both translation steps have started.
                progress.accept("{\"step\":\"translation1\"}");
                progress.accept("{\"step\":\"translation2\"}");

                List<SubtitleEntry> subtitles1 = future1.get();
                List<SubtitleEntry> subtitles2 = future2.get();

                System.out.println("[Stream] Done: " + subtitles1.size()
                    + " [" + (lang1Auto ? "auto→" + detectedCode : lang1) + "] / "
                    + subtitles2.size() + " [" + lang2 + "]");

                ProcessResponse resp = new ProcessResponse();
                resp.setVideoId(videoId);
                resp.setSubtitles1(subtitles1);
                resp.setSubtitles2(subtitles2);
                resp.setLang1Label(lang1Label);
                resp.setLang2Label(lang2Label);

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

    @PostMapping("/process")
    public ResponseEntity<?> processVideo(@RequestBody ProcessRequest request) {
        try {
            String videoId = youTubeService.extractVideoId(request.getVideoUrl());
            System.out.println("[Controller] Processing video: " + videoId
                + " | lang1=" + request.getLang1() + " lang2=" + request.getLang2());

            // Fetch source transcript via Python (youtube-transcript-api)
            List<SubtitleEntry> original = youTubeService.fetchTranscript(videoId);
            System.out.println("[Controller] Source transcript: " + original.size() + " entries");

            if (original.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Empty transcript for this video. "
                           + "The video must have captions enabled (auto-generated or manual)."
                ));
            }

            // Translate to both selected languages via Google Translate
            List<SubtitleEntry> subtitles1 = translationService.translate(original, request.getLang1());
            List<SubtitleEntry> subtitles2 = translationService.translate(original, request.getLang2());
            System.out.println("[Controller] Translation done: "
                + subtitles1.size() + " [" + request.getLang1() + "] / "
                + subtitles2.size() + " [" + request.getLang2() + "]");

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
