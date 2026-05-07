package com.dualsub.controller;

import com.dualsub.model.UserPreferences;
import com.dualsub.service.PersistenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for reading and updating user language preferences.
 *
 * GET  /api/preferences        → returns current preferences (or defaults)
 * PUT  /api/preferences        → saves new preferences, returns saved entity
 */
@RestController
@RequestMapping("/api/preferences")
public class PreferencesController {

    private final PersistenceService persistenceService;

    public PreferencesController(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @GetMapping
    public ResponseEntity<UserPreferences> getPreferences() {
        return ResponseEntity.ok(persistenceService.getOrCreatePreferences());
    }

    @PutMapping
    public ResponseEntity<UserPreferences> savePreferences(
            @RequestBody UserPreferences prefs) {
        return ResponseEntity.ok(persistenceService.savePreferences(prefs));
    }
}
