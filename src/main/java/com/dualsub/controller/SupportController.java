package com.dualsub.controller;

import com.dualsub.model.User;
import com.dualsub.service.SupportService;
import com.dualsub.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/support")
public class SupportController {

    private final SupportService supportService;
    private final UserService    userService;

    public SupportController(SupportService supportService, UserService userService) {
        this.supportService = supportService;
        this.userService    = userService;
    }

    /** Sends a new support ticket. */
    @PostMapping("/messages")
    public ResponseEntity<?> send(Principal principal, @RequestBody Map<String, String> body) {
        try {
            String subject = body.getOrDefault("subject", "").trim();
            String msgBody = body.getOrDefault("body",    "").trim();
            if (subject.isEmpty() || msgBody.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Le sujet et le message sont obligatoires."));
            }
            User user = userService.getByEmail(principal.getName());
            return ResponseEntity.ok(
                AdminController.toSupportDto(supportService.send(user, subject, msgBody)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Returns the current user's own tickets. */
    @GetMapping("/messages")
    public ResponseEntity<?> myMessages(Principal principal) {
        User user = userService.getByEmail(principal.getName());
        return ResponseEntity.ok(
            supportService.getForUser(user).stream()
                .map(AdminController::toSupportDto)
                .toList());
    }
}
