package com.dualsub.controller;

import com.dualsub.model.User;
import com.dualsub.model.WatchHistory;
import com.dualsub.service.PersistenceService;
import com.dualsub.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * REST endpoints for the watch history.
 *
 * GET    /api/history        → returns the current user's last 20 watch events (newest first)
 * DELETE /api/history/{id}   → removes a single history entry
 */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final PersistenceService persistenceService;
    private final UserService        userService;

    public HistoryController(PersistenceService persistenceService, UserService userService) {
        this.persistenceService = persistenceService;
        this.userService        = userService;
    }

    @GetMapping
    public ResponseEntity<List<WatchHistory>> getHistory(Principal principal) {
        if (principal == null) {
            return ResponseEntity.ok(List.of());
        }
        User user = userService.getByEmail(principal.getName());
        return ResponseEntity.ok(persistenceService.getHistoryForUser(user.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntry(@PathVariable Long id) {
        persistenceService.deleteHistoryEntry(id);
        return ResponseEntity.noContent().build();
    }
}
