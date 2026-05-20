package com.dualsub.service;

import com.dualsub.model.SubtitleEntry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages asynchronous Ollama refinement jobs.
 *
 * A job refines both subtitle tracks in the background after the SSE pipeline
 * has already delivered the initial Google-Translate subtitles to the browser.
 * The browser polls /api/refine/status?jobId=… and swaps in the improved
 * subtitles when the job reaches DONE status.
 */
@Service
public class RefinementService {

    public enum Status { PENDING, IN_PROGRESS, DONE, FAILED }

    public static class Job {
        public final String  jobId;
        public volatile Status           status   = Status.PENDING;
        public volatile float            progress = 0f;   // 0.0 → 1.0
        public volatile List<SubtitleEntry> subtitles1;   // null until DONE
        public volatile List<SubtitleEntry> subtitles2;
        public volatile String           error;
        public final    Instant          createdAt = Instant.now();

        Job(String jobId) { this.jobId = jobId; }
    }

    private final OllamaService                 ollamaService;
    private final ConcurrentHashMap<String, Job> jobs     = new ConcurrentHashMap<>();
    private final ExecutorService                executor = Executors.newCachedThreadPool();

    public RefinementService(OllamaService ollamaService) {
        this.ollamaService = ollamaService;
    }

    // ── Helpers for inline use ────────────────────────────────────

    public boolean isOllamaAvailable() { return ollamaService.isAvailable(); }
    public OllamaService getOllamaService() { return ollamaService; }

    // ── Public API ────────────────────────────────────────────────

    /**
     * Creates a background refinement job.
     *
     * @param original         source-language entries (original transcript)
     * @param subtitles1       GT-translated track 1 (or same as original in immersion mode)
     * @param subtitles2       GT-translated track 2
     * @param lang1Auto        true when track 1 is in immersion mode (no translation)
     * @param lang1            target language code for track 1 (ignored when lang1Auto)
     * @param lang2            target language code for track 2
     * @param sourceCode       detected language code of the original transcript
     * @return jobId, or null if Ollama is unavailable
     */
    public String startRefinement(
            List<SubtitleEntry> original,
            List<SubtitleEntry> subtitles1,
            List<SubtitleEntry> subtitles2,
            boolean lang1Auto,
            String  lang1,
            String  lang2,
            String  sourceCode) {

        if (!ollamaService.isAvailable()) {
            System.out.println("[Refinement] Ollama not available — skipping refinement.");
            return null;
        }

        String jobId = UUID.randomUUID().toString();
        Job    job   = new Job(jobId);
        jobs.put(jobId, job);

        executor.submit(() -> runJob(job, original, subtitles1, subtitles2,
                                     lang1Auto, lang1, lang2, sourceCode));
        System.out.println("[Refinement] Job " + jobId + " started ("
            + original.size() + " entries, source=" + sourceCode + ")");
        return jobId;
    }

    public Job getJob(String jobId) {
        return jobs.get(jobId);
    }

    // ── Background job ────────────────────────────────────────────

    private void runJob(
            Job job,
            List<SubtitleEntry> original,
            List<SubtitleEntry> subtitles1,
            List<SubtitleEntry> subtitles2,
            boolean lang1Auto,
            String  lang1,
            String  lang2,
            String  sourceCode) {

        job.status = Status.IN_PROGRESS;
        try {
            List<SubtitleEntry> refined1;
            List<SubtitleEntry> refined2;

            if (lang1Auto) {
                // Immersion mode: track 1 is the original, no refinement needed
                refined1 = subtitles1;
                refined2 = ollamaService.refine(
                    original, subtitles2, sourceCode, lang2,
                    p -> job.progress = p);
            } else {
                // Normal mode: refine both tracks
                // Track 1 gets the first half of the progress bar, track 2 the second half
                refined1 = ollamaService.refine(
                    original, subtitles1, sourceCode, lang1,
                    p -> job.progress = p * 0.5f);
                refined2 = ollamaService.refine(
                    original, subtitles2, sourceCode, lang2,
                    p -> job.progress = 0.5f + p * 0.5f);
            }

            job.subtitles1 = refined1;
            job.subtitles2 = refined2;
            job.progress   = 1.0f;
            job.status     = Status.DONE;
            System.out.println("[Refinement] Job " + job.jobId + " completed.");

        } catch (Exception e) {
            job.status = Status.FAILED;
            job.error  = e.getMessage();
            System.err.println("[Refinement] Job " + job.jobId + " failed: " + e.getMessage());
        }

        cleanupOldJobs();
    }

    // ── Cleanup ───────────────────────────────────────────────────

    private void cleanupOldJobs() {
        Instant cutoff = Instant.now().minusSeconds(3600); // keep for 1 hour
        jobs.entrySet().removeIf(e -> e.getValue().createdAt.isBefore(cutoff));
    }
}
