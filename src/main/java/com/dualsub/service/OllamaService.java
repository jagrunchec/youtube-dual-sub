package com.dualsub.service;

import com.dualsub.model.SubtitleEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * Calls the local Ollama API to refine Google-Translate subtitles.
 *
 * Strategy: hybrid approach — both the source text and the GT translation are sent
 * to the model. GT handles rare vocabulary correctly; the LLM improves naturalness,
 * idioms, and register without losing specific terms.
 */
@Service
public class OllamaService {

    @Value("${app.ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    @Value("${app.ollama.model:mistral-nemo}")
    private String model;

    private static final int    BATCH_SIZE = 10;
    private static final Duration TIMEOUT  = Duration.ofSeconds(90);

    private final HttpClient   httpClient  = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Transcript correction ─────────────────────────────────────

    /**
     * Corrects obvious ASR transcription errors (proper nouns, technical terms, numbers).
     * Uses a very low temperature to avoid hallucinations.
     */
    public List<SubtitleEntry> correctTranscript(List<SubtitleEntry> entries, String sourceCode) {
        if (entries == null || entries.isEmpty()) return entries;
        List<SubtitleEntry> result = new ArrayList<>(entries);
        int total = entries.size();

        for (int i = 0; i < total; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, total);
            try {
                List<String> corrected = correctBatch(entries.subList(i, end), sourceCode);
                for (int j = 0; j < corrected.size() && (i + j) < total; j++) {
                    String text = corrected.get(j).trim();
                    if (!text.isEmpty()) result.set(i + j, copyWith(entries.get(i + j), text));
                }
            } catch (Exception e) {
                System.err.println("[Ollama] Transcript correction batch " + i + " failed: " + e.getMessage());
            }
        }
        return result;
    }

    private List<String> correctBatch(List<SubtitleEntry> batch, String sourceCode) throws Exception {
        String lang = sourceCode != null ? sourceCode.toUpperCase() : "?";
        StringBuilder prompt = new StringBuilder();
        prompt.append("Corrige les erreurs de transcription automatique (ASR) dans ces phrases en ").append(lang).append(".\n")
              .append("Corrige UNIQUEMENT les mots clairement mal transcrits : noms propres, termes techniques, chiffres.\n")
              .append("NE CHANGE PAS le contenu ni le sens. Si une phrase est correcte, retourne-la telle quelle.\n")
              .append("Format : N: texte corrigé (une ligne par entrée, aucune explication).\n\n");
        for (int i = 0; i < batch.size(); i++) {
            prompt.append(i + 1).append(": ").append(batch.get(i).getText()).append("\n");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model",   model);
        body.put("prompt",  prompt.toString());
        body.put("stream",  false);
        body.put("options", Map.of("temperature", 0.05, "num_predict", 512));

        String jsonBody = objectMapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(ollamaUrl + "/api/generate"))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        String responseText = objectMapper.readTree(resp.body()).path("response").asText();
        List<String> parsed = parseNumberedLines(responseText, batch.size());
        return parsed.size() >= batch.size() / 2 ? parsed : Collections.emptyList();
    }

    // ── Availability check ────────────────────────────────────────

    public boolean isAvailable() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl + "/api/tags"))
                .timeout(Duration.ofSeconds(4))
                .GET().build();
            int status = httpClient.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            return status == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Main refinement entry-point ───────────────────────────────

    /**
     * Refines {@code translated} entries using Ollama.
     *
     * @param original   source-language subtitle entries (used as context)
     * @param translated Google-Translate output to improve
     * @param sourceCode BCP-47 code of the original language (e.g. "de")
     * @param targetCode BCP-47 code of the target language  (e.g. "fr")
     * @param onProgress callback receiving progress 0.0→1.0 after each batch
     * @return refined subtitle list (same size, same timing, improved text)
     */
    public List<SubtitleEntry> refine(
            List<SubtitleEntry> original,
            List<SubtitleEntry> translated,
            String sourceCode,
            String targetCode,
            Consumer<Float> onProgress) {

        if (original == null || translated == null
                || original.size() != translated.size()
                || translated.isEmpty()) {
            return translated;
        }

        List<SubtitleEntry> result = new ArrayList<>(translated);
        int total = original.size();

        for (int i = 0; i < total; i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, total);
            try {
                List<String> refined = refineBatch(
                    original.subList(i, end),
                    translated.subList(i, end),
                    sourceCode, targetCode);

                for (int j = 0; j < refined.size() && (i + j) < total; j++) {
                    String text = refined.get(j).trim();
                    if (!text.isEmpty()) {
                        result.set(i + j, copyWith(translated.get(i + j), text));
                    }
                }
            } catch (Exception e) {
                System.err.println("[Ollama] Batch " + i + "/" + total + " failed: " + e.getMessage());
                // Keep original GT translations for this batch — graceful degradation
            }

            if (onProgress != null) onProgress.accept((float) end / total);
        }

        return result;
    }

    // ── Batch processing ──────────────────────────────────────────

    private List<String> refineBatch(
            List<SubtitleEntry> src,
            List<SubtitleEntry> gt,
            String sourceCode,
            String targetCode) throws Exception {

        String srcLabel = sourceCode != null ? sourceCode.toUpperCase() : "SRC";
        String tgtLabel = targetCode != null ? targetCode.toUpperCase() : "TGT";

        StringBuilder prompt = new StringBuilder();
        prompt.append("Tu es un traducteur expert ")
              .append(srcLabel).append("→").append(tgtLabel).append(".\n")
              .append("Améliore les traductions automatiques ci-dessous pour qu'elles soient ")
              .append("naturelles et idiomatiques en ").append(tgtLabel).append(".\n")
              .append("RÈGLE ABSOLUE : conserve les noms propres, termes techniques et ")
              .append("vocabulaire spécifique de la colonne [GT]. Améliore uniquement le style.\n")
              .append("Réponds UNIQUEMENT avec les traductions améliorées, une par ligne, ")
              .append("sous la forme « N: traduction ». Aucune explication.\n\n");

        for (int i = 0; i < src.size(); i++) {
            prompt.append(i + 1).append(": [").append(srcLabel).append("] ")
                  .append(src.get(i).getText()).append("\n")
                  .append("   [GT]  ").append(gt.get(i).getText()).append("\n");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model",   model);
        body.put("prompt",  prompt.toString());
        body.put("stream",  false);
        body.put("options", Map.of("temperature", 0.1, "num_predict", 1024));

        String jsonBody = objectMapper.writeValueAsString(body);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(ollamaUrl + "/api/generate"))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        String responseText = objectMapper.readTree(resp.body()).path("response").asText();

        List<String> parsed = parseNumberedLines(responseText, src.size());
        // If parsing is unreliable (too few results), return empty → caller keeps GT
        return parsed.size() >= src.size() / 2 ? parsed : Collections.emptyList();
    }

    // ── Response parsing ──────────────────────────────────────────

    /** Parses "N: text" or "N. text" or "N) text" lines from the model response. */
    private List<String> parseNumberedLines(String text, int expected) {
        List<String> result = new ArrayList<>();
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.matches("^\\d+[:.)]\\s+.+")) {
                result.add(line.replaceFirst("^\\d+[:.)]\\s+", "").trim());
            }
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────

    private SubtitleEntry copyWith(SubtitleEntry original, String newText) {
        SubtitleEntry copy = new SubtitleEntry();
        copy.setStartMs(original.getStartMs());
        copy.setDurationMs(original.getDurationMs());
        copy.setSpeechEndMs(original.getSpeechEndMs());
        copy.setText(newText);
        return copy;
    }
}
