package com.dualsub.controller;

import com.dualsub.model.VideoSummary;
import com.dualsub.service.SummaryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Video-summary endpoints: list of engines + per-language SSE generation.
 *
 * The frontend calls {@code /api/summary/engines} once on app load to populate the
 * engine radio buttons, then calls {@code /api/summary/stream} per-language when
 * the user clicks "Generate". Cached summaries return immediately as a "complete"
 * event with {@code cached: true}.
 */
@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    private final SummaryService summaryService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SummaryController(SummaryService summaryService) {
        this.summaryService = summaryService;
    }

    /** Returns the list of available engines (label, model, availability, restrictions). */
    @GetMapping("/engines")
    public ResponseEntity<?> engines() {
        return ResponseEntity.ok(Map.of("engines", summaryService.getAvailableEngines()));
    }

    /**
     * Returns the cached summary for (videoId, lang, engine) without generating.
     * 200 + payload if cached; 204 No Content otherwise.
     * Used by the UI to auto-populate the panel on video load.
     */
    @GetMapping("/cached")
    public ResponseEntity<?> cached(@RequestParam String videoId,
                                    @RequestParam String lang,
                                    @RequestParam String engine) {
        return summaryService.getCached(videoId, lang, engine)
            .<ResponseEntity<?>>map(s -> ResponseEntity.ok(buildPayload(s, true)))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Streams the summary generation for a single (videoId, lang) pair via SSE.
     *
     * Events:
     *   - {@code complete}: full payload (summary + metadata), with cached=true when served from DB
     *   - {@code progress}: "{step: generating}" emitted right before the LLM call
     *   - {@code apierror}: "{error: ...}" on any failure
     *
     * Query params:
     *   - videoId : YouTube video ID
     *   - lang    : target language (must be in supported set)
     *   - engine  : "ollama" or "gemini"
     *   - refresh : "true" to bypass cache and regenerate
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generate(
            @RequestParam String videoId,
            @RequestParam String lang,
            @RequestParam String engine,
            @RequestParam(defaultValue = "false") boolean refresh) {

        SseEmitter emitter = new SseEmitter(240_000L); // 4-minute timeout

        executor.submit(() -> {
            try {
                // ── Cache lookup (unless refresh forced) ──
                if (!refresh) {
                    Optional<VideoSummary> cached = summaryService.getCached(videoId, lang, engine);
                    if (cached.isPresent()) {
                        VideoSummary s = cached.get();
                        Map<String, Object> payload = buildPayload(s, true);
                        emitter.send(SseEmitter.event().name("complete")
                            .data(objectMapper.writeValueAsString(payload)));
                        emitter.complete();
                        return;
                    }
                }

                // ── Generate ──
                emitter.send(SseEmitter.event().name("progress").data("{\"step\":\"generating\"}"));
                VideoSummary saved = summaryService.generate(videoId, lang, engine);
                Map<String, Object> payload = buildPayload(saved, false);
                emitter.send(SseEmitter.event().name("complete")
                    .data(objectMapper.writeValueAsString(payload)));
                emitter.complete();

            } catch (IllegalArgumentException | IllegalStateException e) {
                sendError(emitter, e.getMessage());
            } catch (Exception e) {
                System.err.println("[Summary] Generation failed: " + e.getMessage());
                sendError(emitter, "Erreur serveur : " + e.getMessage());
            }
        });

        return emitter;
    }

    private Map<String, Object> buildPayload(VideoSummary s, boolean cached) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("videoId",     s.getVideoId());
        m.put("lang",        s.getLang());
        m.put("engine",      s.getEngine());
        m.put("model",       s.getModel());
        m.put("summary",     s.getSummary());
        m.put("durationMs",  s.getDurationMs());
        m.put("createdAt",   s.getCreatedAt() != null ? s.getCreatedAt().toString() : null);
        m.put("cached",      cached);
        return m;
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            String safe = (message == null ? "Erreur inconnue" : message).replace("\"", "'");
            emitter.send(SseEmitter.event().name("apierror").data("{\"error\":\"" + safe + "\"}"));
        } catch (Exception ignored) {}
        try { emitter.complete(); } catch (Exception ignored) {}
    }
}
