package com.dualsub.controller;

import com.dualsub.model.User;
import com.dualsub.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
