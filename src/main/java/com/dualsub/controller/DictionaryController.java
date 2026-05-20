package com.dualsub.controller;

import com.dualsub.model.User;
import com.dualsub.service.DictionaryService;
import com.dualsub.service.DictionaryService.DictionaryItemDto;
import com.dualsub.service.DictionaryService.LookupRequest;
import com.dualsub.service.DictionaryService.LookupResponse;
import com.dualsub.service.DictionaryService.TranslateResponse;
import com.dualsub.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST API for the personal dictionary feature.
 *
 * POST   /api/dictionary/lookup              Translate a word and save it
 * GET    /api/dictionary                     List user's dictionary (with filters)
 * PATCH  /api/dictionary/entries/{id}/notes  Update personal notes on an entry
 * DELETE /api/dictionary/entries/{id}        Delete a single entry
 * DELETE /api/dictionary/words/{wordId}      Delete a word and all its entries
 * DELETE /api/dictionary/by-video/{videoId}  Delete all entries for a video
 * DELETE /api/dictionary/by-date?before=     Delete entries created before a date
 * DELETE /api/dictionary/all                 Delete the entire dictionary
 */
@RestController
@RequestMapping("/api/dictionary")
public class DictionaryController {

    private final DictionaryService dictionaryService;
    private final UserService       userService;

    public DictionaryController(DictionaryService dictionaryService, UserService userService) {
        this.dictionaryService = dictionaryService;
        this.userService       = userService;
    }

    // ── Translate only (hover bar — no save) ─────────────────────────────────

    @GetMapping("/translate")
    public ResponseEntity<?> translate(@RequestParam String word,
                                       @RequestParam String from,
                                       @RequestParam String to,
                                       Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        try {
            TranslateResponse resp = dictionaryService.translateOnly(word, from, to);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ── Lookup / save ─────────────────────────────────────────────────────────

    @PostMapping("/lookup")
    public ResponseEntity<?> lookup(@RequestBody LookupRequest req, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        try {
            User user = userService.getByEmail(principal.getName());
            LookupResponse resp = dictionaryService.lookup(user, req);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ── List ──────────────────────────────────────────────────────────────────

    /**
     * @param sort    "alpha" or "date" (default: date)
     * @param videoId filter to a specific video
     * @param from    filter entries created on or after this ISO date (yyyy-MM-dd)
     * @param lang    filter to a source language code
     * @param tag     filter to entries whose word has this tag
     */
    @GetMapping
    public ResponseEntity<List<DictionaryItemDto>> list(
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String videoId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String lang,
            @RequestParam(required = false) String tag,
            Principal principal) {

        if (principal == null) return ResponseEntity.ok(List.of());
        User user = userService.getByEmail(principal.getName());

        LocalDate fromDate = null;
        if (from != null && !from.isBlank()) {
            try { fromDate = LocalDate.parse(from); } catch (Exception ignored) {}
        }

        List<DictionaryItemDto> items =
            dictionaryService.list(user.getId(), sort, videoId, fromDate, lang, tag);
        return ResponseEntity.ok(items);
    }

    // ── Tags ──────────────────────────────────────────────────────────────────

    @PatchMapping("/words/{wordId}/tags")
    public ResponseEntity<?> updateTags(@PathVariable Long wordId,
                                        @RequestBody Map<String, Object> body,
                                        Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        try {
            User user = userService.getByEmail(principal.getName());
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) body.get("tags");
            dictionaryService.updateTags(user.getId(), wordId, tags);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Notes ─────────────────────────────────────────────────────────────────

    @PatchMapping("/entries/{id}/notes")
    public ResponseEntity<?> updateNotes(@PathVariable Long id,
                                         @RequestBody Map<String, String> body,
                                         Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        try {
            User user = userService.getByEmail(principal.getName());
            dictionaryService.updateNotes(user.getId(), id, body.get("notes"));
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Delete operations ─────────────────────────────────────────────────────

    @DeleteMapping("/entries/{id}")
    public ResponseEntity<?> deleteEntry(@PathVariable Long id, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        try {
            User user = userService.getByEmail(principal.getName());
            dictionaryService.deleteEntry(user.getId(), id);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/words/{wordId}")
    public ResponseEntity<?> deleteWord(@PathVariable Long wordId, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        try {
            User user = userService.getByEmail(principal.getName());
            dictionaryService.deleteWord(user.getId(), wordId);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/by-video/{videoId}")
    public ResponseEntity<?> deleteByVideo(@PathVariable String videoId, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        User user = userService.getByEmail(principal.getName());
        dictionaryService.deleteByVideo(user.getId(), videoId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete entries created before (strictly) a given date.
     * @param before ISO date string, e.g. "2025-06-01"
     */
    @DeleteMapping("/by-date")
    public ResponseEntity<?> deleteByDate(@RequestParam String before, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        try {
            User user = userService.getByEmail(principal.getName());
            dictionaryService.deleteByDate(user.getId(), LocalDate.parse(before));
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/all")
    public ResponseEntity<?> deleteAll(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        User user = userService.getByEmail(principal.getName());
        dictionaryService.deleteAll(user.getId());
        return ResponseEntity.noContent().build();
    }
}
