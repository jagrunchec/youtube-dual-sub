package com.dualsub.controller;

import com.dualsub.model.WatchHistory;
import com.dualsub.service.PersistenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for the watch history.
 *
 * GET    /api/history        → returns last 20 watch events (newest first)
 * DELETE /api/history/{id}   → removes a single history entry
 */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final PersistenceService persistenceService;

    public HistoryController(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @GetMapping
    public ResponseEntity<List<WatchHistory>> getHistory() {
        return ResponseEntity.ok(persistenceService.getHistory());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntry(@PathVariable Long id) {
        persistenceService.deleteHistoryEntry(id);
        return ResponseEntity.noContent().build();
    }
}
