package com.dualsub.service;

import com.dualsub.model.SubtitleEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TranslationService {

    // Maximum characters per Google Translate API call
    private static final int CHUNK_SIZE = 2000;

    // Delay between API calls to avoid rate-limiting (ms)
    private static final long API_DELAY_MS = 400;

    // Line separator: plain newline — subtitle text never contains newlines after cleaning
    private static final String SEP = "\n";

    private final HttpClient httpClient;

    public TranslationService(SSLContext appSslContext) {
        this.httpClient = HttpClient.newBuilder()
            .sslContext(appSslContext)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static final Map<String, String> LANGUAGES = new LinkedHashMap<>();

    static {
        LANGUAGES.put("fr", "Français");
        LANGUAGES.put("en", "English");
        LANGUAGES.put("es", "Español");
        LANGUAGES.put("it", "Italiano");
        LANGUAGES.put("de", "Deutsch");
        LANGUAGES.put("pl", "Polski");
        // Secondary-only languages: punctuation model doesn't cover them, so they
        // can't be used as a source, but Google Translate targets them reliably.
        LANGUAGES.put("pt", "Português");
        LANGUAGES.put("nl", "Nederlands");
        LANGUAGES.put("ru", "Русский");
        LANGUAGES.put("hi", "हिन्दी");
        LANGUAGES.put("ar", "العربية");
    }

    public List<SubtitleEntry> translate(List<SubtitleEntry> entries, String targetLang)
            throws InterruptedException {
        if (entries.isEmpty()) return entries;

        List<List<SubtitleEntry>> chunks = splitIntoChunks(entries);
        System.out.println("[Translation] " + entries.size() + " subtitles → "
            + chunks.size() + " chunk(s) → " + targetLang);

        List<SubtitleEntry> result = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            List<SubtitleEntry> chunk = chunks.get(i);

            // Delay between calls to avoid rate-limiting (skip delay on first chunk)
            if (i > 0) Thread.sleep(API_DELAY_MS);

            result.addAll(translateChunk(chunk, targetLang, i + 1, chunks.size()));
        }
        System.out.println("[Translation] Done: " + result.size() + " entries translated to " + targetLang);
        return result;
    }

    private List<List<SubtitleEntry>> splitIntoChunks(List<SubtitleEntry> entries) {
        List<List<SubtitleEntry>> chunks = new ArrayList<>();
        List<SubtitleEntry> current = new ArrayList<>();
        int currentLen = 0;

        for (SubtitleEntry e : entries) {
            int len = e.getText().length() + SEP.length();
            if (currentLen + len > CHUNK_SIZE && !current.isEmpty()) {
                chunks.add(current);
                current = new ArrayList<>();
                currentLen = 0;
            }
            current.add(e);
            currentLen += len;
        }
        if (!current.isEmpty()) chunks.add(current);
        return chunks;
    }

    private List<SubtitleEntry> translateChunk(List<SubtitleEntry> chunk,
                                                String targetLang,
                                                int chunkNum, int total) {
        try {
            String combined = chunk.stream()
                .map(SubtitleEntry::getText)
                .collect(Collectors.joining(SEP));

            String translated = callGoogleTranslate(combined, targetLang);

            String[] parts = translated.split(java.util.regex.Pattern.quote(SEP), -1);

            System.out.println("[Translation] Chunk " + chunkNum + "/" + total
                + " — expected: " + chunk.size() + ", received: " + parts.length);

            // Line counts match — map translations back to original timing
            if (parts.length == chunk.size()) {
                List<SubtitleEntry> result = new ArrayList<>();
                for (int i = 0; i < chunk.size(); i++) {
                    SubtitleEntry orig = chunk.get(i);
                    String text = parts[i].trim();
                    // Fall back to original text if the translated line is empty
                    if (text.isEmpty()) text = orig.getText();
                    result.add(new SubtitleEntry(orig.getStartMs(), orig.getDurationMs(), text));
                }
                return result;
            }

            // Line count mismatch — keep the original text for this chunk
            System.err.println("[Translation] Line count mismatch in chunk " + chunkNum
                + " (expected " + chunk.size() + ", got " + parts.length
                + ") — keeping original text");
            return chunk;

        } catch (Exception e) {
            System.err.println("[Translation] Chunk " + chunkNum + " error: "
                + e.getMessage() + " — keeping original text");
            return chunk;
        }
    }

    private String callGoogleTranslate(String text, String targetLang)
            throws IOException, InterruptedException {

        String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8);
        String url = "https://translate.googleapis.com/translate_a/single"
            + "?client=gtx&sl=auto&tl=" + targetLang + "&dt=t&q=" + encoded;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .header("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "application/json, text/plain, */*")
            .GET()
            .build();

        // Read as bytes and decode as UTF-8 to avoid Java's ISO-8859-1 default
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 429) {
            System.err.println("[Translation] Rate-limited (429) — waiting 2s and retrying...");
            Thread.sleep(2000);
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }

        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " from Google Translate");
        }

        String body = new String(response.body(), StandardCharsets.UTF_8);
        return parseGoogleTranslateResponse(body);
    }

    private String parseGoogleTranslateResponse(String json) throws IOException {
        // Response format: [[[translated, original, ...], ...], null, "sourceLang"]
        JsonNode root = objectMapper.readTree(json);
        if (!root.isArray() || root.isEmpty()) return "";

        JsonNode sentences = root.get(0);
        if (!sentences.isArray()) return "";

        StringBuilder sb = new StringBuilder();
        for (JsonNode sentence : sentences) {
            if (sentence.isArray() && !sentence.isEmpty()) {
                sb.append(sentence.get(0).asText(""));
            }
        }
        return sb.toString();
    }
}
