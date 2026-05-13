package com.dualsub.controller;

import com.dualsub.model.MessageStatus;
import com.dualsub.model.Role;
import com.dualsub.model.SupportMessage;
import com.dualsub.model.User;
import com.dualsub.repository.UserRepository;
import com.dualsub.service.SupportService;
import com.dualsub.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService    userService;
    private final SupportService supportService;
    private final UserRepository userRepository;

    public AdminController(UserService userService,
                           SupportService supportService,
                           UserRepository userRepository) {
        this.userService    = userService;
        this.supportService = supportService;
        this.userRepository = userRepository;
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public List<Map<String, Object>> listUsers() {
        return userService.getAllUsers().stream()
            .map(AdminController::toAdminDto)
            .toList();
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> setRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            Role role = Role.valueOf(body.get("role").toUpperCase());
            User user = userService.setRole(id, role);
            return ResponseEntity.ok(toAdminDto(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/users/{id}/limit")
    public ResponseEntity<?> setLimit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            Integer limit = body.get("limit") != null
                ? Integer.parseInt(body.get("limit").toString()) : null;
            User user = userService.setWeeklyLimit(id, limit);
            return ResponseEntity.ok(toAdminDto(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/users/{id}/active")
    public ResponseEntity<?> setActive(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            boolean active = Boolean.parseBoolean(body.get("active").toString());
            User user = userService.setActive(id, active);
            return ResponseEntity.ok(toAdminDto(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/users/{id}/unlock")
    public ResponseEntity<?> unlock(@PathVariable Long id) {
        try {
            User user = userService.unlockAccount(id);
            return ResponseEntity.ok(toAdminDto(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id, java.security.Principal principal) {
        try {
            // Prevent self-deletion
            User self = userService.getByEmail(principal.getName());
            if (self.getId().equals(id)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Vous ne pouvez pas supprimer votre propre compte."));
            }
            userService.deleteUser(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Support messages ──────────────────────────────────────────────────────

    @GetMapping("/support")
    public List<Map<String, Object>> listMessages() {
        return supportService.getAll().stream()
            .map(AdminController::toSupportDto)
            .toList();
    }

    @PostMapping("/support/{id}/respond")
    public ResponseEntity<?> respond(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            MessageStatus status = body.containsKey("status")
                ? MessageStatus.valueOf(body.get("status").toUpperCase())
                : MessageStatus.CLOSED;
            SupportMessage msg = supportService.respond(id, body.getOrDefault("response", ""), status);
            return ResponseEntity.ok(toSupportDto(msg));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
            "totalUsers",    userRepository.count(),
            "activeUsers",   userRepository.countByActive(true),
            "limitedUsers",  userRepository.countByRole(Role.LIMITED),
            "normalUsers",   userRepository.countByRole(Role.NORMAL),
            "superUsers",    userRepository.countByRole(Role.SUPER),
            "adminUsers",    userRepository.countByRole(Role.ADMIN),
            "openTickets",   supportService.getAll().stream()
                                 .filter(m -> m.getStatus() == MessageStatus.OPEN).count()
        );
    }

    // ── DTO helpers ────────────────────────────────────────────────────────────

    public static Map<String, Object> toAdminDto(User u) {
        return Map.ofEntries(
            Map.entry("id",                  u.getId()),
            Map.entry("email",               u.getEmail()),
            Map.entry("firstName",           u.getFirstName()  != null ? u.getFirstName()  : ""),
            Map.entry("lastName",            u.getLastName()   != null ? u.getLastName()   : ""),
            Map.entry("role",                u.getRole().name()),
            Map.entry("active",              u.isActive()),
            Map.entry("weeklyVideoLimit",    u.getWeeklyVideoLimit() != null ? u.getWeeklyVideoLimit() : -1),
            Map.entry("weeklyViewCount",     u.getWeeklyViewCount()),
            Map.entry("nativeLanguage",      u.getNativeLanguage() != null ? u.getNativeLanguage() : ""),
            Map.entry("createdAt",           u.getCreatedAt() != null ? u.getCreatedAt().toString() : ""),
            Map.entry("lastLoginAt",         u.getLastLoginAt() != null ? u.getLastLoginAt().toString() : ""),
            Map.entry("failedAttempts",      u.getFailedLoginAttempts()),
            Map.entry("locked",              u.isLocked()),
            Map.entry("lockedUntil",         u.getAccountLockedUntil() != null ? u.getAccountLockedUntil().toString() : "")
        );
    }

    public static Map<String, Object> toSupportDto(SupportMessage m) {
        return Map.ofEntries(
            Map.entry("id",            m.getId()),
            Map.entry("userId",        m.getUser().getId()),
            Map.entry("userEmail",     m.getUser().getEmail()),
            Map.entry("userName",      (m.getUser().getFirstName() != null ? m.getUser().getFirstName() : "")
                                     + " " + (m.getUser().getLastName() != null ? m.getUser().getLastName() : "")),
            Map.entry("subject",       m.getSubject()),
            Map.entry("body",          m.getBody()),
            Map.entry("status",        m.getStatus().name()),
            Map.entry("adminResponse", m.getAdminResponse() != null ? m.getAdminResponse() : ""),
            Map.entry("createdAt",     m.getCreatedAt().toString()),
            Map.entry("respondedAt",   m.getRespondedAt() != null ? m.getRespondedAt().toString() : "")
        );
    }
}
