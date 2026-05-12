package com.dualsub.controller;

import com.dualsub.model.User;
import com.dualsub.model.WatchHistory;
import com.dualsub.repository.WatchHistoryRepository;
import com.dualsub.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService            userService;
    private final WatchHistoryRepository historyRepo;

    public UserController(UserService userService, WatchHistoryRepository historyRepo) {
        this.userService = userService;
        this.historyRepo = historyRepo;
    }

    /** Returns the current user's full profile. */
    @GetMapping("/me")
    public ResponseEntity<?> getProfile(Principal principal) {
        User user = userService.getByEmail(principal.getName());
        return ResponseEntity.ok(AuthController.toDto(user));
    }

    /** Updates editable profile fields. */
    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(Principal principal, @RequestBody Map<String, Object> body) {
        try {
            User user = userService.getByEmail(principal.getName());
            userService.updateProfile(
                user.getId(),
                str(body, "firstName"),
                str(body, "lastName"),
                str(body, "nativeLanguage"),
                str(body, "languagesToLearn"),
                intVal(body, "birthYear"),
                str(body, "country"),
                str(body, "learningGoals"),
                str(body, "learningLevel"),
                intVal(body, "studyGoalMinutesWeek")
            );
            return ResponseEntity.ok(AuthController.toDto(userService.getByEmail(principal.getName())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Changes the current user's password. */
    @PostMapping("/me/password")
    public ResponseEntity<?> changePassword(Principal principal, @RequestBody Map<String, String> body) {
        try {
            User user = userService.getByEmail(principal.getName());
            userService.changePassword(user.getId(), body.get("currentPassword"), body.get("newPassword"));
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Personal watch statistics for the current user. */
    @GetMapping("/me/stats")
    public ResponseEntity<?> getStats(Principal principal) {
        User user = userService.getByEmail(principal.getName());
        List<WatchHistory> history = historyRepo.findByUser_IdOrderByWatchedAtDesc(user.getId());

        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);

        long videosThisWeek = history.stream()
            .filter(h -> !h.getWatchedAt().toLocalDate().isBefore(monday))
            .count();

        // Top language pairs (max 5)
        List<Map<String, Object>> topPairs = history.stream()
            .collect(Collectors.groupingBy(
                h -> h.getLang1().toUpperCase() + " / " + h.getLang2().toUpperCase(),
                Collectors.counting()))
            .entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(5)
            .map(e -> Map.<String, Object>of("pair", e.getKey(), "count", e.getValue()))
            .toList();

        // Weekly counts — last 8 ISO weeks
        List<Map<String, Object>> weeklyHistory = new ArrayList<>();
        for (int w = 7; w >= 0; w--) {
            LocalDate ws = monday.minusWeeks(w);
            LocalDate we = ws.plusDays(7);
            int wNum = ws.get(WeekFields.ISO.weekOfWeekBasedYear());
            long cnt = history.stream()
                .filter(h -> {
                    LocalDate d = h.getWatchedAt().toLocalDate();
                    return !d.isBefore(ws) && d.isBefore(we);
                }).count();
            weeklyHistory.add(Map.of("label", "S" + wNum, "count", cnt));
        }

        return ResponseEntity.ok(Map.of(
            "totalVideos",    history.size(),
            "videosThisWeek", videosThisWeek,
            "topPairs",       topPairs,
            "weeklyHistory",  weeklyHistory
        ));
    }

    private String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return (v != null && !v.toString().isBlank()) ? v.toString() : null;
    }

    private Integer intVal(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) return null;
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
