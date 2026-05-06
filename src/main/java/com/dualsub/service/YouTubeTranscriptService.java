package com.dualsub.service;

import com.dualsub.model.SubtitleEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class YouTubeTranscriptService {

    /** Path to the Python transcript script (configurable via application.properties). */
    @Value("${app.transcript.script:scripts/get_transcript.py}")
    private String transcriptScriptPath;

    /** Python executable to use. */
    @Value("${app.transcript.python:python}")
    private String pythonExe;

    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // InnerTube API request body template (WEB client)
    private static final String INNERTUBE_BODY_TEMPLATE =
        "{\"videoId\":\"%s\",\"context\":{\"client\":{" +
        "\"hl\":\"en\",\"gl\":\"US\"," +
        "\"clientName\":\"WEB\",\"clientVersion\":\"2.20240101.00.00\"}}}";

    // Cookie manager: accepts all cookies from youtube.com (CONSENT, SOCS, etc.)
    private final CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

    // Force HTTP/1.1 — Java's HTTP/2 client can silently drop the body on some YouTube CDN nodes
    private final HttpClient httpClient = HttpClient.newBuilder()
        .version(java.net.http.HttpClient.Version.HTTP_1_1)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20))
        .cookieHandler(cookieManager)
        .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─── Public API ──────────────────────────────────────────────

    public String extractVideoId(String input) {
        Pattern pattern = Pattern.compile(
            "(?:youtu\\.be/|youtube\\.com/(?:watch\\?v=|embed/|shorts/))([a-zA-Z0-9_-]{11})"
        );
        Matcher m = pattern.matcher(input);
        if (m.find()) return m.group(1);
        if (input.matches("[a-zA-Z0-9_-]{11}")) return input;
        throw new IllegalArgumentException("Invalid YouTube URL: " + input);
    }

    /**
     * Resolves and returns the base timedtext URL (without &fmt) for the given video.
     * Tries the InnerTube API first, then falls back to HTML page scraping.
     * The HTML page fetch also seeds the cookie jar (CONSENT, SOCS) for subsequent requests.
     */
    public String fetchCaptionBaseUrl(String videoId) throws IOException, InterruptedException {
        System.out.println("[Transcript] Resolving base caption URL for videoId=" + videoId);
        String url = fetchCaptionUrlViaInnerTube(videoId);
        if (url == null) {
            System.out.println("[Transcript] InnerTube returned no tracks — falling back to HTML scraping");
            url = fetchCaptionUrlViaHtmlScraping(videoId);
        }
        if (url != null) {
            System.out.println("[Transcript] Base URL found: " + url.substring(0, Math.min(120, url.length())) + "...");
        } else {
            System.out.println("[Transcript] No base caption URL found");
        }
        return url;
    }

    /**
     * Fetches subtitles translated via YouTube's tlang parameter.
     * YouTube performs the translation server-side — no external translation service needed.
     * Resolves the base URL from the videoId (makes a network request).
     *
     * @param videoId    YouTube video ID
     * @param targetLang target language code (fr, en, es, it, de, …)
     */
    public List<SubtitleEntry> fetchTranscriptWithLang(String videoId, String targetLang)
            throws IOException, InterruptedException {
        System.out.println("[Transcript] fetchTranscriptWithLang videoId=" + videoId + " lang=" + targetLang);

        String captionBaseUrl = fetchCaptionBaseUrl(videoId);
        if (captionBaseUrl == null) {
            throw new RuntimeException(
                "No subtitles available for video ID: " + videoId +
                ". The video must have captions enabled (auto-generated or manual)."
            );
        }

        String base = captionBaseUrl.replaceAll("&fmt=[^&]*", "");
        return fetchTranscriptFromBaseUrl(base, targetLang);
    }

    /**
     * Fetches subtitles from an already-resolved base URL, appending the tlang parameter.
     * Avoids re-scraping the YouTube page for each language (performance + anti-429).
     *
     * @param cleanBaseUrl base URL without &fmt parameter
     * @param targetLang   target language code (fr, en, es, it, de, …)
     */
    public List<SubtitleEntry> fetchTranscriptFromBaseUrl(String cleanBaseUrl, String targetLang)
            throws IOException, InterruptedException {

        // Extract videoId from the base URL to build a valid Referer header
        String videoId = "unknown";
        java.util.regex.Matcher vm = Pattern.compile("[?&]v=([^&]+)").matcher(cleanBaseUrl);
        if (vm.find()) videoId = vm.group(1);

        String jsonUrl = cleanBaseUrl + "&tlang=" + targetLang + "&fmt=json3";
        System.out.println("[Transcript] tlang request [" + targetLang + "]: "
            + jsonUrl.substring(0, Math.min(150, jsonUrl.length())) + "...");

        String captionJson = fetchTimedtext(jsonUrl, videoId);
        System.out.println("[Transcript] Response: " + captionJson.length() + " bytes");
        System.out.println("[Transcript] Preview:  " + captionJson.substring(0, Math.min(300, captionJson.length())));

        List<SubtitleEntry> result = parseCaptionJson3(captionJson);
        System.out.println("[Transcript] Done: " + result.size() + " subtitles [" + targetLang + "]");
        return result;
    }

    /**
     * Primary transcript fetcher: invokes the Python youtube-transcript-api script.
     * This reliably bypasses YouTube's bot-detection that blocks direct HTTP requests.
     * Returns subtitles in the video's source language (before any translation).
     */
    public List<SubtitleEntry> fetchViaYtdlp(String videoId) throws IOException, InterruptedException {
        System.out.println("[Transcript] fetchViaYtdlp videoId=" + videoId);

        // Locate the Python script
        java.io.File script = new java.io.File(transcriptScriptPath);
        if (!script.isAbsolute()) {
            script = new java.io.File(System.getProperty("user.dir"), transcriptScriptPath);
        }
        if (!script.exists()) {
            throw new IOException("Script not found: " + script.getAbsolutePath()
                + " — check app.transcript.script in application.properties");
        }

        System.out.println("[Transcript] Script path: " + script.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(pythonExe, script.getAbsolutePath(), videoId);
        pb.redirectErrorStream(false);
        // Force UTF-8 on Python stdout (Windows defaults to CP850 / CP1252)
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        pb.environment().put("PYTHONUTF8", "1");
        Process proc = pb.start();

        // Read stdout (JSON array) with explicit UTF-8 decoding
        String jsonOutput;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            jsonOutput = sb.toString();
        }

        // Read stderr for diagnostics
        String stderrOutput;
        try (BufferedReader errReader = new BufferedReader(
                new InputStreamReader(proc.getErrorStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = errReader.readLine()) != null) sb.append(line).append("\n");
            stderrOutput = sb.toString().trim();
        }

        proc.waitFor();
        System.out.println("[Transcript] Script stdout: " + jsonOutput.length() + " chars");
        if (!stderrOutput.isBlank()) {
            System.err.println("[Transcript] Script stderr: "
                + stderrOutput.substring(0, Math.min(500, stderrOutput.length())));
        }

        if (jsonOutput.isBlank()) {
            throw new IOException("Python script produced no output.");
        }

        // Parse the JSON output
        JsonNode root = objectMapper.readTree(jsonOutput);

        // Error case: {"error": "..."}
        if (root.isObject() && root.has("error")) {
            throw new RuntimeException("Python script error: " + root.path("error").asText());
        }

        if (!root.isArray()) {
            throw new IOException("Unexpected JSON from Python script: "
                + jsonOutput.substring(0, Math.min(200, jsonOutput.length())));
        }

        List<SubtitleEntry> raw = new ArrayList<>();
        for (JsonNode node : root) {
            long startMs    = (long)(node.path("start").asDouble(0) * 1000);
            long durationMs = (long)(node.path("duration").asDouble(3) * 1000);
            String text     = cleanSubtitleText(node.path("text").asText("").trim());
            if (!text.isEmpty() && durationMs > 0) {
                raw.add(new SubtitleEntry(startMs, durationMs, text));
            }
        }

        // Merge short ASR fragments into readable phrases
        List<SubtitleEntry> entries = mergeSubtitles(raw);
        System.out.println("[Transcript] Done: " + raw.size() + " fragments → "
            + entries.size() + " merged phrases");
        return entries;
    }

    /**
     * Cleans raw ASR subtitle text:
     * - removes sound/music annotations like [Musik], [Music], [Applause], etc.
     * - strips residual HTML tags
     * - collapses multiple spaces
     */
    private static String cleanSubtitleText(String text) {
        if (text == null || text.isBlank()) return "";
        return text
            .replaceAll("\\[\\s*[Mm]usik\\s*\\]", "")
            .replaceAll("\\[\\s*[Mm]usic\\s*\\]", "")
            .replaceAll("\\[\\s*[Aa]pplaus.*?\\]", "")
            .replaceAll("\\[\\s*[Ll]achen.*?\\]", "")
            .replaceAll("\\[\\s*[Gg]el.cht.*?\\]", "")
            .replaceAll("\\[.*?\\]", "")     // any other bracketed annotation
            .replaceAll("<[^>]+>", "")       // HTML tags
            .replaceAll("\\s{2,}", " ")      // collapse whitespace
            .trim();
    }

    /**
     * Merges short ASR transcript fragments into longer, more readable phrases.
     *
     * Merge rules:
     *  - Keep merging while the gap to the next fragment is below GAP_MAX ms.
     *  - Stop when the combined text would exceed TARGET_CHARS characters.
     *  - Stop at strong punctuation (sentence boundary).
     *  - Always stop after a silence longer than GAP_BREAK ms.
     */
    private static List<SubtitleEntry> mergeSubtitles(List<SubtitleEntry> entries) {
        if (entries.isEmpty()) return entries;

        final int  TARGET_CHARS = 80;   // target phrase length in characters
        final long GAP_MAX      = 1200; // ms: gaps below this are merged
        final long GAP_BREAK    = 2500; // ms: gaps above this always start a new phrase

        List<SubtitleEntry> merged = new ArrayList<>();
        int i = 0;

        while (i < entries.size()) {
            SubtitleEntry first = entries.get(i);
            StringBuilder text  = new StringBuilder(first.getText());
            long startMs        = first.getStartMs();
            long endMs          = first.getStartMs() + first.getDurationMs();
            i++;

            while (i < entries.size()) {
                SubtitleEntry next = entries.get(i);
                long gap           = next.getStartMs() - endMs;

                // Long silence — hard sentence boundary
                if (gap > GAP_BREAK) break;

                String combined = text + " " + next.getText();

                // Would exceed target length
                if (combined.length() > TARGET_CHARS) break;

                // Strong punctuation at end of current fragment — natural sentence end
                String trimmed = text.toString().trim();
                if (trimmed.endsWith(".") || trimmed.endsWith("!")
                        || trimmed.endsWith("?") || trimmed.endsWith("…")
                        || trimmed.endsWith(":")) break;

                // Moderate gap — only merge if current text is still short
                if (gap > GAP_MAX && text.length() >= 35) break;

                text.append(" ").append(next.getText());
                endMs = next.getStartMs() + next.getDurationMs();
                i++;
            }

            merged.add(new SubtitleEntry(startMs, endMs - startMs, text.toString().trim()));
        }

        return merged;
    }

    /** Fetches the source transcript (Python script first, HTTP fallback). */
    public List<SubtitleEntry> fetchTranscript(String videoId) throws IOException, InterruptedException {
        System.out.println("[Transcript] Starting transcript fetch for videoId=" + videoId);

        // Attempt 1: Python youtube-transcript-api script (reliable)
        try {
            List<SubtitleEntry> result = fetchViaYtdlp(videoId);
            if (!result.isEmpty()) return result;
        } catch (Exception e) {
            System.err.println("[Transcript] Python script failed: " + e.getMessage()
                + " — falling back to direct HTTP");
        }

        // Attempt 2: direct HTTP fallback (less reliable, may return empty)
        System.out.println("[Transcript] HTTP fallback...");

        String captionBaseUrl = fetchCaptionBaseUrl(videoId);

        if (captionBaseUrl == null) {
            throw new RuntimeException(
                "No subtitles available for video ID: " + videoId +
                ". The video must have captions enabled (auto-generated or manual)."
            );
        }

        String base = captionBaseUrl.replaceAll("&fmt=[^&]*", "");

        // Try JSON3 format
        String jsonUrl = base + "&fmt=json3";
        System.out.println("[Transcript] Trying JSON3...");
        String captionJson = fetchTimedtext(jsonUrl, videoId);

        // JSON3 empty — try default XML format
        if (captionJson.isBlank()) {
            System.out.println("[Transcript] JSON3 empty — trying XML (no fmt)...");
            captionJson = fetchTimedtext(base, videoId);
        }

        // Still empty — try srv3 format
        if (captionJson.isBlank()) {
            System.out.println("[Transcript] XML empty — trying srv3...");
            captionJson = fetchTimedtext(base + "&fmt=srv3", videoId);
        }

        System.out.println("[Transcript] Response: " + captionJson.length() + " bytes");
        System.out.println("[Transcript] Preview:  "
            + captionJson.substring(0, Math.min(300, captionJson.length())));

        List<SubtitleEntry> result = parseCaptionJson3(captionJson);
        System.out.println("[Transcript] Done: " + result.size() + " subtitles parsed");
        return result;
    }

    /** Returns raw diagnostic data for the /api/debug/transcript endpoint. */
    public DiagInfo diagnose(String videoId) throws IOException, InterruptedException {
        DiagInfo info = new DiagInfo();
        info.videoId = videoId;

        // Method 1: InnerTube
        try {
            String body = String.format(INNERTUBE_BODY_TEMPLATE, videoId);
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://www.youtube.com/youtubei/v1/player"))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .header("X-YouTube-Client-Name", "1")
                .header("X-YouTube-Client-Version", "2.20240101.00.00")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            info.innerTubeStatus = resp.statusCode();
            info.innerTubeBodyPreview = resp.body().substring(0, Math.min(500, resp.body().length()));

            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode tracks = root.path("captions")
                .path("playerCaptionsTracklistRenderer")
                .path("captionTracks");
            info.innerTubeTracksFound = tracks.isArray() ? tracks.size() : 0;

            if (tracks.isArray() && !tracks.isEmpty()) {
                info.firstTrackUrl  = tracks.get(0).path("baseUrl").asText("?");
                info.firstTrackLang = tracks.get(0).path("languageCode").asText("?");
            }
        } catch (Exception e) {
            info.innerTubeError = e.getMessage();
        }

        // Method 2: HTML scraping
        try {
            String html = fetchPage(videoId);
            info.htmlPageLength       = html.length();
            info.htmlHasCaptionTracks = html.contains("captionTracks");
            info.htmlHasTimedtext     = html.contains("timedtext");
            info.scrapedUrl           = fetchCaptionUrlViaHtmlScraping(videoId);
        } catch (Exception e) {
            info.scrapingError = e.getMessage();
        }

        return info;
    }

    // ─── Private methods ─────────────────────────────────────────

    private String fetchCaptionUrlViaInnerTube(String videoId) throws IOException, InterruptedException {
        String body = String.format(INNERTUBE_BODY_TEMPLATE, videoId);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://www.youtube.com/youtubei/v1/player"))
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("X-YouTube-Client-Name", "1")
            .header("X-YouTube-Client-Version", "2.20240101.00.00")
            .header("Accept-Language", "en-US,en;q=0.9")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("[Transcript] InnerTube HTTP " + response.statusCode());

        if (response.statusCode() >= 400) return null;

        try {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode tracks = root.path("captions")
                .path("playerCaptionsTracklistRenderer")
                .path("captionTracks");

            System.out.println("[Transcript] InnerTube: "
                + (tracks.isArray() ? tracks.size() : 0) + " track(s) found");

            if (!tracks.isArray() || tracks.isEmpty()) return null;

            // Preference order: English ASR → any English → first available
            String first = null;
            for (JsonNode track : tracks) {
                String lang = track.path("languageCode").asText("");
                String kind = track.path("kind").asText("");
                String url  = track.path("baseUrl").asText(null);
                if (url == null) continue;
                System.out.println("[Transcript]   track: lang=" + lang + " kind=" + kind);
                if (first == null) first = url;
                if (lang.startsWith("en") && "asr".equals(kind)) return url;
            }
            for (JsonNode track : tracks) {
                String lang = track.path("languageCode").asText("");
                String url  = track.path("baseUrl").asText(null);
                if (url != null && lang.startsWith("en")) return url;
            }
            return first;

        } catch (Exception e) {
            System.err.println("[Transcript] InnerTube parse error: " + e.getMessage());
            return null;
        }
    }

    private String fetchCaptionUrlViaHtmlScraping(String videoId) throws IOException, InterruptedException {
        String html = fetchPage(videoId);
        System.out.println("[Transcript] HTML page: " + html.length() + " bytes");
        System.out.println("[Transcript] Contains 'captionTracks': " + html.contains("captionTracks"));
        System.out.println("[Transcript] Contains 'timedtext': "    + html.contains("timedtext"));

        // Look for timedtext baseUrl in the page source
        Pattern p = Pattern.compile("\"baseUrl\":\"(https://www\\.youtube\\.com/api/timedtext[^\"]*)\"");
        Matcher m = p.matcher(html);

        String first = null;
        while (m.find()) {
            String raw = m.group(1)
                .replace("\\u003d", "=").replace("\\u0026", "&")
                .replace("\\u003c", "<").replace("\\u003e", ">").replace("\\/", "/");
            System.out.println("[Transcript] HTML URL found: " + raw.substring(0, Math.min(100, raw.length())));
            if (first == null) first = raw;
            if (raw.contains("lang=en") || raw.contains("asr")) return raw;
        }
        return first;
    }

    private String fetchPage(String videoId) throws IOException, InterruptedException {
        return fetchRaw("https://www.youtube.com/watch?v=" + videoId);
    }

    private List<SubtitleEntry> parseCaptionJson3(String json) throws IOException {
        // Detect XML responses (some YouTube caption endpoints return XML instead of JSON3)
        String trimmed = json.stripLeading();
        if (trimmed.startsWith("<?xml") || trimmed.startsWith("<transcript")) {
            System.out.println("[Transcript] XML format detected — switching to XML parser");
            return parseCaptionXml(json);
        }

        JsonNode root = objectMapper.readTree(json);

        // YouTube occasionally returns an empty JSON object {}
        if (root.isEmpty()) {
            System.err.println("[Transcript] Empty JSON received!");
            return new ArrayList<>();
        }

        JsonNode events = root.path("events");
        if (events.isMissingNode() || !events.isArray()) {
            System.err.println("[Transcript] No 'events' field in JSON3!");
            System.err.println("[Transcript] Top-level keys: " + root.fieldNames());
            return new ArrayList<>();
        }

        System.out.println("[Transcript] JSON3 events: " + events.size());

        List<SubtitleEntry> entries = new ArrayList<>();
        int skipped = 0;
        long prevEnd = 0;

        for (JsonNode event : events) {
            long startMs = event.path("tStartMs").asLong(-1);
            if (startMs < 0) { skipped++; continue; }

            long durationMs = event.path("dDurationMs").asLong(0);
            // If duration is missing or zero, default to 3 seconds
            if (durationMs <= 0) durationMs = 3000;

            JsonNode segs = event.path("segs");
            if (!segs.isArray() || segs.isEmpty()) {
                skipped++;
                prevEnd = startMs + durationMs;
                continue;
            }

            StringBuilder text = new StringBuilder();
            for (JsonNode seg : segs) {
                String t = seg.path("utf8").asText("");
                text.append(t.replace("\n", " "));
            }

            String textStr = text.toString().trim();
            if (!textStr.isEmpty()) {
                entries.add(new SubtitleEntry(startMs, durationMs, textStr));
            } else {
                skipped++;
            }
            prevEnd = startMs + durationMs;
        }

        System.out.println("[Transcript] JSON3 parsed: " + entries.size() + " valid, " + skipped + " skipped");
        if (!entries.isEmpty()) {
            System.out.println("[Transcript] First: t=" + entries.get(0).getStartMs()
                + "ms \"" + entries.get(0).getText() + "\"");
            System.out.println("[Transcript] Last:  t=" + entries.get(entries.size() - 1).getStartMs() + "ms");
        }
        return entries;
    }

    /** Fallback XML caption parser. Format: &lt;text start="1.23" dur="2.45"&gt;text&lt;/text&gt; */
    private List<SubtitleEntry> parseCaptionXml(String xml) {
        List<SubtitleEntry> entries = new ArrayList<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "<text[^>]+start=\"([^\"]+)\"[^>]*dur=\"([^\"]+)\"[^>]*>([^<]*)</text>",
            java.util.regex.Pattern.DOTALL
        );
        java.util.regex.Matcher m = p.matcher(xml);
        while (m.find()) {
            try {
                long startMs    = (long)(Double.parseDouble(m.group(1)) * 1000);
                long durationMs = (long)(Double.parseDouble(m.group(2)) * 1000);
                String text = m.group(3)
                    .replace("&amp;", "&").replace("&lt;", "<")
                    .replace("&gt;", ">").replace("&quot;", "\"")
                    .replace("&#39;", "'").replace("\n", " ").trim();
                if (!text.isEmpty()) {
                    entries.add(new SubtitleEntry(startMs, durationMs, text));
                }
            } catch (NumberFormatException ignored) {}
        }
        System.out.println("[Transcript] XML parsed: " + entries.size() + " entries");
        return entries;
    }

    /**
     * Fetches a timedtext URL with browser-like headers.
     * Reads the response body as raw bytes to avoid charset misdetection.
     */
    private String fetchTimedtext(String url, String videoId) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept", "text/xml,application/json,text/plain,*/*;q=0.8")
            .header("Accept-Encoding", "identity")  // disable compression — read body as-is
            .header("Referer", "https://www.youtube.com/watch?v=" + videoId)
            .GET()
            .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] bytes = response.body();
        System.out.println("[Transcript] timedtext HTTP " + response.statusCode()
            + " — bytes: " + bytes.length
            + " — Content-Type: " + response.headers().firstValue("content-type").orElse("?")
            + " — cookies: " + cookieManager.getCookieStore().getCookies().size());

        if (response.statusCode() == 429) {
            System.err.println("[Transcript] 429 rate-limited — waiting 3s and retrying...");
            Thread.sleep(3000);
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            bytes = response.body();
            System.out.println("[Transcript] Retry HTTP " + response.statusCode()
                + " — bytes: " + bytes.length);
        }
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " for: " + url);
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String fetchRaw(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept", "text/html,application/json,*/*;q=0.8")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + " for: " + url);
        }
        return response.body();
    }

    // ─── Diagnostic data class ───────────────────────────────────

    public static class DiagInfo {
        public String  videoId;
        public int     innerTubeStatus;
        public String  innerTubeBodyPreview;
        public int     innerTubeTracksFound;
        public String  firstTrackUrl;
        public String  firstTrackLang;
        public String  innerTubeError;
        public int     htmlPageLength;
        public boolean htmlHasCaptionTracks;
        public boolean htmlHasTimedtext;
        public String  scrapedUrl;
        public String  scrapingError;
    }
}
