package com.dualsub.service;

import com.dualsub.model.SubtitleEntry;
import com.dualsub.model.VideoSummary;
import com.dualsub.repository.VideoSummaryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Generates concise video summaries from cached transcripts.
 *
 * Two engines are supported:
 *  - "ollama"  → local LLM (mistral-nemo by default), free, slow, mediocre on ar/hi
 *  - "gemini"  → Google Gemini 2.0 Flash, requires API key, fast, excellent multilingual
 *
 * Results are cached in {@code video_summaries} keyed by (videoId, lang, engine).
 */
@Service
public class SummaryService {

    // ── Configuration ─────────────────────────────────────────────

    @Value("${app.ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${app.ollama.model:mistral-nemo}")
    private String ollamaModel;

    @Value("${app.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.gemini.model:gemini-2.0-flash}")
    private String geminiModel;

    /** Languages where Ollama's mistral-nemo produces poor summaries. */
    private static final Set<String> OLLAMA_UNSUPPORTED = Set.of("ar", "hi");

    /** BCP-47 → French language name (used in the prompt). */
    private static final Map<String, String> LANG_NAMES_FR = Map.ofEntries(
        Map.entry("fr", "français"),
        Map.entry("en", "anglais"),
        Map.entry("es", "espagnol"),
        Map.entry("it", "italien"),
        Map.entry("de", "allemand"),
        Map.entry("pl", "polonais"),
        Map.entry("pt", "portugais"),
        Map.entry("nl", "néerlandais"),
        Map.entry("ru", "russe"),
        Map.entry("hi", "hindi"),
        Map.entry("ar", "arabe")
    );

    // ── Dependencies ──────────────────────────────────────────────

    private final VideoSummaryRepository summaryRepo;
    private final PersistenceService     persistenceService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient   httpClient   = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10)).build();

    public SummaryService(VideoSummaryRepository summaryRepo,
                          PersistenceService persistenceService) {
        this.summaryRepo        = summaryRepo;
        this.persistenceService = persistenceService;
    }

    // ── Public API ────────────────────────────────────────────────

    /** Returns the list of available engines based on current configuration. */
    public List<Map<String, Object>> getAvailableEngines() {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> ollama = new LinkedHashMap<>();
        ollama.put("id",            "ollama");
        ollama.put("label",         "Ollama (local)");
        ollama.put("model",         ollamaModel);
        ollama.put("available",     isOllamaAvailable());
        ollama.put("unsupportedLangs", OLLAMA_UNSUPPORTED);
        result.add(ollama);

        Map<String, Object> gemini = new LinkedHashMap<>();
        gemini.put("id",            "gemini");
        gemini.put("label",         "Gemini (cloud)");
        gemini.put("model",         geminiModel);
        gemini.put("available",     geminiApiKey != null && !geminiApiKey.isBlank());
        gemini.put("unsupportedLangs", Collections.emptySet());
        result.add(gemini);

        return result;
    }

    /**
     * Returns a cached summary if present, otherwise null.
     * Used by the controller for instant lookups before deciding to (re)generate.
     */
    public Optional<VideoSummary> getCached(String videoId, String lang, String engine) {
        return summaryRepo.findByVideoIdAndLangAndEngine(videoId, lang, engine);
    }

    /**
     * Generates and caches a new summary, replacing any previous entry.
     *
     * @param videoId YouTube video ID
     * @param lang    target language code
     * @param engine  "ollama" or "gemini"
     * @return the persisted VideoSummary
     */
    @Transactional
    public VideoSummary generate(String videoId, String lang, String engine, int lengthPct) throws Exception {
        // ── Validate inputs ──
        if (!LANG_NAMES_FR.containsKey(lang)) {
            throw new IllegalArgumentException("Langue non supportée pour le résumé : " + lang);
        }
        if ("ollama".equals(engine) && OLLAMA_UNSUPPORTED.contains(lang)) {
            throw new IllegalArgumentException(
                "Ollama ne supporte pas le résumé en " + LANG_NAMES_FR.get(lang)
                + ". Utilisez Gemini.");
        }
        if ("gemini".equals(engine) && (geminiApiKey == null || geminiApiKey.isBlank())) {
            throw new IllegalArgumentException(
                "Clé Gemini non configurée. Ajoutez app.gemini.api-key dans application.properties.");
        }

        // ── Load transcript from cache ──
        var transcriptOpt = persistenceService.getCachedTranscript(videoId);
        if (transcriptOpt.isEmpty()) {
            throw new IllegalStateException("Transcript introuvable. Analysez d'abord la vidéo.");
        }
        List<SubtitleEntry> entries = transcriptOpt.get().entries;
        String transcript = entriesToPlainText(entries);

        // ── Compute target word count ──
        int transcriptWords = countWords(transcript);
        int targetWords     = Math.max(40, transcriptWords * lengthPct / 100);
        System.out.println("[Summary] lengthPct=" + lengthPct + "% → transcript=" + transcriptWords
            + " words → target=" + targetWords + " words");

        // ── Call the engine ──
        long t0 = System.currentTimeMillis();
        String summary;
        String modelUsed;
        if ("ollama".equals(engine)) {
            summary  = summarizeWithOllama(transcript, lang, targetWords);
            modelUsed = ollamaModel;
        } else if ("gemini".equals(engine)) {
            summary  = summarizeWithGemini(transcript, lang, targetWords);
            modelUsed = geminiModel;
        } else {
            throw new IllegalArgumentException("Moteur inconnu : " + engine);
        }
        long elapsedMs = System.currentTimeMillis() - t0;

        if (summary == null || summary.isBlank()) {
            throw new RuntimeException("Le moteur " + engine + " a renvoyé un résumé vide.");
        }

        // ── Persist (replace existing entry if any) ──
        VideoSummary saved = summaryRepo
            .findByVideoIdAndLangAndEngine(videoId, lang, engine)
            .orElseGet(VideoSummary::new);
        saved.setVideoId(videoId);
        saved.setLang(lang);
        saved.setEngine(engine);
        saved.setModel(modelUsed);
        saved.setSummary(summary.trim());
        saved.setDurationMs((int) elapsedMs);
        saved.setCreatedAt(LocalDateTime.now());
        return summaryRepo.save(saved);
    }

    // ── Engine implementations ────────────────────────────────────

    private String summarizeWithOllama(String transcript, String lang, int targetWords) throws Exception {
        String langName = LANG_NAMES_FR.getOrDefault(lang, lang);

        // mistral-nemo is a chat-tuned model — use /api/chat with system + user messages
        // rather than /api/generate. This gives the model a clear role boundary and
        // dramatically improves instruction-following on long inputs.
        String systemMsg =
            "Tu es un outil de résumé automatique de vidéos YouTube. "
          + "Tu lis le transcript fourni par l'utilisateur et tu produis un résumé "
          + "factuel, neutre et fidèle au contenu. "
          + "Tu n'inventes JAMAIS d'informations qui ne sont pas dans le transcript. "
          + "Tu ne fais aucun préambule : tu commences directement par le résumé.";

        String userMsg =
            "Résume la vidéo ci-dessous en " + langName + ".\n"
          + "Longueur cible : environ " + targetWords + " mots (± 20 %).\n"
          + "Contenu : sujet principal, points clés, conclusion.\n\n"
          + "TRANSCRIPT :\n"
          + transcript;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", ollamaModel);
        body.put("messages", List.of(
            Map.of("role", "system", "content", systemMsg),
            Map.of("role", "user",   "content", userMsg)
        ));
        body.put("stream", false);
        // num_ctx = 16384 lets the full transcript fit for videos up to ~50 min.
        // Default Ollama context is 2048-4096 which silently truncates long transcripts.
        // num_predict: allow ~2.5 tokens per target word, minimum 256, capped at 4096.
        int numPredict = Math.min(4096, Math.max(256, (int)(targetWords * 2.5)));
        body.put("options", Map.of(
            "temperature", 0.3,
            "num_predict", numPredict,
            "num_ctx",     16384
        ));

        String jsonBody = objectMapper.writeValueAsString(body);
        System.out.println("[Summary] Ollama call: transcript=" + transcript.length()
            + " chars, lang=" + lang + ", model=" + ollamaModel);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(ollamaUrl + "/api/chat"))
            .timeout(Duration.ofSeconds(240))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            throw new RuntimeException("Ollama HTTP " + resp.statusCode() + ": "
                + resp.body().substring(0, Math.min(300, resp.body().length())));
        }
        // /api/chat response shape: { "message": { "role": "assistant", "content": "..." }, ... }
        var root = objectMapper.readTree(resp.body());
        String content = root.path("message").path("content").asText();
        System.out.println("[Summary] Ollama returned " + content.length() + " chars");
        return content;
    }

    private String summarizeWithGemini(String transcript, String lang, int targetWords) throws Exception {
        String prompt = buildPrompt(transcript, lang, targetWords);

        // maxOutputTokens: allow ~1.5 tokens per target word, minimum 256, capped at 8192.
        int maxTokens = Math.min(8192, Math.max(256, (int)(targetWords * 1.5)));

        // Build {"contents":[{"parts":[{"text":"..."}]}]} payload
        Map<String, Object> payload = Map.of(
            "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
            "generationConfig", Map.of(
                "temperature",     0.3,
                "maxOutputTokens", maxTokens
            )
        );

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                   + geminiModel + ":generateContent?key=" + geminiApiKey;

        System.out.println("[Summary] Gemini call: transcript=" + transcript.length()
            + " chars, lang=" + lang + ", model=" + geminiModel);

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
            .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) {
            // Log the full body so we can distinguish quota types
            String rawBody = resp.body().substring(0, Math.min(600, resp.body().length()));
            System.err.println("[Summary] Gemini error " + resp.statusCode() + " body: " + rawBody);
            // Produce friendly messages for common quota / auth errors
            if (resp.statusCode() == 429) {
                throw new RuntimeException(
                    "Quota Gemini dépassé (429). Vérifiez votre plan sur " +
                    "https://aistudio.google.com — quota gratuit peut-être épuisé ou limité à 0.");
            }
            if (resp.statusCode() == 403 || resp.statusCode() == 401) {
                throw new RuntimeException(
                    "Clé Gemini invalide ou accès refusé (" + resp.statusCode() + "). " +
                    "Vérifiez app.gemini.api-key dans application-local.properties.");
            }
            // Generic — don't leak the API key in error messages
            throw new RuntimeException("Gemini HTTP " + resp.statusCode() + ": "
                + resp.body().substring(0, Math.min(300, resp.body().length())));
        }

        // Response: { "candidates": [ { "content": { "parts": [ {"text": "..."} ] } } ] }
        var root = objectMapper.readTree(resp.body());
        var candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new RuntimeException("Gemini : aucune réponse candidate. Body : "
                + resp.body().substring(0, Math.min(300, resp.body().length())));
        }
        var parts = candidates.get(0).path("content").path("parts");
        StringBuilder out = new StringBuilder();
        for (var p : parts) out.append(p.path("text").asText(""));
        return out.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────

    /** Soft cap on transcript size sent to the LLM (~12k tokens for European languages). */
    private static final int TRANSCRIPT_MAX_CHARS = 50_000;

    /**
     * Joins all subtitle entries into one plain-text block.
     * If the result exceeds TRANSCRIPT_MAX_CHARS, keeps the beginning (80%) and end (20%)
     * — intro + conclusion are usually the most informative for a summary.
     */
    private String entriesToPlainText(List<SubtitleEntry> entries) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (SubtitleEntry e : entries) {
            sb.append(e.getText());
            sb.append(' ');
            if (++i % 10 == 0) sb.append('\n');
        }
        String full = sb.toString().trim();
        if (full.length() <= TRANSCRIPT_MAX_CHARS) return full;

        int headLen = (int)(TRANSCRIPT_MAX_CHARS * 0.8);
        int tailLen = TRANSCRIPT_MAX_CHARS - headLen;
        String head = full.substring(0, headLen);
        String tail = full.substring(full.length() - tailLen);
        System.out.println("[Summary] Transcript truncated: " + full.length()
            + " chars → " + (headLen + tailLen) + " (head+tail)");
        return head + "\n\n[…transcript abrégé pour le résumé…]\n\n" + tail;
    }

    /** Builds the summarization prompt for Gemini. */
    private String buildPrompt(String transcript, String lang, int targetWords) {
        String langName = LANG_NAMES_FR.getOrDefault(lang, lang);
        return  "Tu es un assistant qui résume des vidéos YouTube à partir de leur transcript.\n\n"
              + "Langue de réponse : " + langName + "\n"
              + "Longueur cible : environ " + targetWords + " mots (± 20 %).\n"
              + "Contenu : sujet principal, points clés, conclusion.\n"
              + "Ton : neutre et factuel.\n\n"
              + "Réponds UNIQUEMENT avec le résumé, sans préambule, sans titre, sans formule de politesse.\n\n"
              + "Transcript :\n"
              + transcript;
    }

    /** Counts the approximate number of words in a text (split on whitespace). */
    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    /** Quick availability check for Ollama (HEAD /api/tags). */
    private boolean isOllamaAvailable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl + "/api/tags"))
                .timeout(Duration.ofSeconds(3))
                .GET().build();
            return httpClient.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
