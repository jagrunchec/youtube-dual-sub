package com.dualsub.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Looks up a word's frequency rank within its language by invoking the
 * {@code scripts/word_freq.py} helper, which wraps the {@code wordfreq} Python
 * library. The script prints the 1-based rank (1 = most common word in that
 * language, drawn from a 100k-deep frequency list) or an empty line when the
 * word is rarer than the top 100k.
 *
 * <p>This service is best-effort: any failure (missing Python, missing library,
 * script timeout, anything) returns {@code null} so the calling save path
 * still succeeds — the dictionary entry is just stored without a rank.
 *
 * <p>Results are cached in memory per ({@code word}, {@code lang}) so a user
 * who saves the same word from two different videos pays the Python startup
 * cost only once per server lifetime.
 */
@Service
public class WordFrequencyService {

    @Value("${app.transcript.python:python}")
    private String pythonExe;

    @Value("${app.transcript.script.wordfreq:scripts/word_freq.py}")
    private String scriptPath;

    private final ConcurrentHashMap<String, Integer> cache = new ConcurrentHashMap<>();
    /** Sentinel cached for "unknown" results so we don't retry forever. */
    private static final Integer UNKNOWN = -1;

    /**
     * Returns the 1-based frequency rank of {@code word} in {@code lang}, or
     * {@code null} when the word is too rare or the lookup failed for any
     * reason. Never throws.
     *
     * @param word  the word (case is normalised to lowercase internally)
     * @param lang  BCP-47-ish language code accepted by wordfreq (fr, en, de, es, it, pl, …)
     */
    public Integer lookupRank(String word, String lang) {
        if (word == null || lang == null || word.isBlank() || lang.isBlank()) {
            return null;
        }
        String normWord = word.trim().toLowerCase(Locale.ROOT);
        String normLang = lang.trim().toLowerCase(Locale.ROOT);
        String key = normLang + ":" + normWord;

        Integer cached = cache.get(key);
        if (cached != null) {
            return cached.equals(UNKNOWN) ? null : cached;
        }

        Integer rank = invokeScript(normWord, normLang);
        cache.put(key, rank == null ? UNKNOWN : rank);
        return rank;
    }

    /**
     * Spawn {@code python word_freq.py <word> <lang>} and parse the single
     * integer the script prints on stdout. Empty stdout (or any error path)
     * yields {@code null}.
     */
    private Integer invokeScript(String word, String lang) {
        File script = new File(scriptPath);
        if (!script.exists()) {
            System.err.println("[WordFreq] Script not found: " + script.getAbsolutePath());
            return null;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonExe, script.getAbsolutePath(), word, lang);
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            pb.environment().put("PYTHONUTF8", "1");
            pb.redirectErrorStream(false);

            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    out.append(line);
                }
            }
            if (!p.waitFor(15, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                System.err.println("[WordFreq] Timeout for word=" + word + " lang=" + lang);
                return null;
            }
            String s = out.toString().trim();
            if (s.isEmpty()) return null;
            return Integer.parseInt(s);
        } catch (NumberFormatException nfe) {
            // Script printed something we couldn't parse — treat as unknown
            return null;
        } catch (Exception e) {
            System.err.println("[WordFreq] Lookup failed for '" + word + "' (" + lang + "): " + e.getMessage());
            return null;
        }
    }
}
