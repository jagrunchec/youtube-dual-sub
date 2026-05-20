package com.dualsub.controller;

import com.dualsub.service.RefinementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API for the Ollama subtitle-refinement pipeline.
 *
 * GET /api/refine/status?jobId=…
 *   Polls the status of a refinement job.
 *   Returns { jobId, status, progress } while in progress,
 *   plus { subtitles1, subtitles2 } once DONE.
 *
 * GET /api/refine/available
 *   Quick health-check: is Ollama reachable?
 */
@RestController
@RequestMapping("/api/refine")
public class RefinementController {

    private final RefinementService refinementService;
    private final com.dualsub.service.OllamaService ollamaService;

    public RefinementController(RefinementService refinementService,
                                com.dualsub.service.OllamaService ollamaService) {
        this.refinementService = refinementService;
        this.ollamaService     = ollamaService;
    }

    @GetMapping("/available")
    public ResponseEntity<Map<String, Object>> available() {
        boolean ok = ollamaService.isAvailable();
        return ResponseEntity.ok(Map.of("available", ok));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam String jobId) {
        RefinementService.Job job = refinementService.getJob(jobId);
        if (job == null) return ResponseEntity.notFound().build();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("jobId",    job.jobId);
        resp.put("status",   job.status.name());
        resp.put("progress", job.progress);

        if (job.status == RefinementService.Status.DONE) {
            resp.put("subtitles1", job.subtitles1);
            resp.put("subtitles2", job.subtitles2);
        }
        if (job.status == RefinementService.Status.FAILED) {
            resp.put("error", job.error);
        }
        return ResponseEntity.ok(resp);
    }
}
