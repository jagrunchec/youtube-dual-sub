package com.dualsub.controller;

import com.dualsub.model.ProcessRequest;
import com.dualsub.model.ProcessResponse;
import com.dualsub.model.SubtitleEntry;
import com.dualsub.service.TranslationService;
import com.dualsub.service.YouTubeTranscriptService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class VideoController {

    private final YouTubeTranscriptService youTubeService;
    private final TranslationService translationService;

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
