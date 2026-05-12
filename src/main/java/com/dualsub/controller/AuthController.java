package com.dualsub.controller;

import com.dualsub.model.SecurityQuestion;
import com.dualsub.model.User;
import com.dualsub.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.security.Principal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    private final AuthenticationManager authManager;
    private final UserService           userService;

    public AuthController(AuthenticationManager authManager, UserService userService) {
        this.authManager = authManager;
        this.userService = userService;
    }

    // ── Current session user ──────────────────────────────────────────────────

    @GetMapping("/me")
    public ResponseEntity<?> me(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).body(Map.of("error", "Non authentifié"));
        User user = userService.getByEmail(principal.getName());
        return ResponseEntity.ok(toDto(user));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String email    = body.getOrDefault("email", "").trim().toLowerCase();
        String password = body.getOrDefault("password", "");

        // ── Pre-auth lockout check ──────────────────────────────────────────────
        var userOpt = userService.findByEmail(email);
        if (userOpt.isPresent()) {
            User candidate = userOpt.get();
            if (candidate.isLocked()) {
                long mins = java.time.temporal.ChronoUnit.MINUTES.between(
                    java.time.LocalDateTime.now(), candidate.getAccountLockedUntil()) + 1;
                return ResponseEntity.status(403).body(Map.of("error",
                    "Compte temporairement bloqué après " + UserService.MAX_FAILED_ATTEMPTS
                    + " tentatives incorrectes. Réessayez dans " + mins + " minute(s)."));
            }
            // Auto-clear expired lockout so attempts counter resets
            if (candidate.getAccountLockedUntil() != null && !candidate.isLocked()) {
                userService.unlockAccount(candidate.getId());
            }
        }

        try {
            Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password));

            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            SecurityContextHolder.setContext(ctx);

            HttpSession session = request.getSession(true);
            session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);

            userService.recordLogin(email);   // also resets failed-attempt counter
            User user = userService.getByEmail(email);
            return ResponseEntity.ok(toDto(user));

        } catch (BadCredentialsException e) {
            userService.recordFailedLogin(email);
            // Re-check lock status after increment to give accurate message
            var u = userService.findByEmail(email);
            if (u.isPresent() && u.get().isLocked()) {
                return ResponseEntity.status(403).body(Map.of("error",
                    "Compte bloqué après " + UserService.MAX_FAILED_ATTEMPTS
                    + " tentatives incorrectes. Réessayez dans "
                    + UserService.LOCKOUT_MINUTES + " minutes."));
            }
            int remaining = u.map(usr -> UserService.MAX_FAILED_ATTEMPTS - usr.getFailedLoginAttempts())
                             .orElse(-1);
            String hint = remaining > 0 && remaining <= 3
                ? " (" + remaining + " tentative(s) restante(s) avant blocage)"
                : "";
            return ResponseEntity.status(401).body(Map.of("error",
                "Email ou mot de passe incorrect." + hint));

        } catch (DisabledException e) {
            return ResponseEntity.status(403).body(Map.of("error",
                "Compte désactivé. Contactez l'administrateur."));
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Register ──────────────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> body) {
        try {
            String email     = str(body, "email").trim().toLowerCase();
            String password  = str(body, "password");
            String firstName = str(body, "firstName");
            String lastName  = str(body, "lastName");

            @SuppressWarnings("unchecked")
            List<String> questionKeys = (List<String>) body.getOrDefault("questionKeys", List.of());
            @SuppressWarnings("unchecked")
            List<String> answers      = (List<String>) body.getOrDefault("answers", List.of());

            if (password.length() < 8) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Le mot de passe doit contenir au moins 8 caractères."));
            }

            String nativeLanguage   = str(body, "nativeLanguage");
            String languagesToLearn = str(body, "languagesToLearn");
            String country          = str(body, "country");
            Integer birthYear       = body.get("birthYear") instanceof Number n ? n.intValue() : null;

            User user = userService.register(email, password, firstName, lastName,
                                             nativeLanguage, languagesToLearn, birthYear, country,
                                             questionKeys, answers);
            if (mailEnabled) {
                // Account pending e-mail verification — do not auto-login
                return ResponseEntity.ok(Map.of(
                    "pending", true,
                    "email",   user.getEmail(),
                    "message", "Un email de confirmation a été envoyé à " + user.getEmail()
                               + ". Cliquez sur le lien pour activer votre compte."
                ));
            }
            return ResponseEntity.ok(toDto(user));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Password recovery ─────────────────────────────────────────────────────

    /** Step 1: returns the user's security question labels so the browser can display them. */
    @GetMapping("/recover/questions")
    public ResponseEntity<?> recoverQuestions(@RequestParam String email) {
        try {
            User user = userService.getByEmail(email.trim().toLowerCase());
            List<SecurityQuestion> questions = userService.getSecurityQuestions(user);
            if (questions.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Aucune question de sécurité configurée pour ce compte."));
            }
            List<Map<String, String>> result = questions.stream()
                .map(q -> Map.of(
                    "order",   String.valueOf(q.getQuestionOrder()),
                    "label",   UserService.QUESTIONS.getOrDefault(q.getQuestionKey(), q.getQuestionKey())
                )).toList();
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Step 2: verify answers and reset the password. */
    @PostMapping("/recover/reset")
    public ResponseEntity<?> recoverReset(@RequestBody Map<String, Object> body) {
        try {
            String email      = str(body, "email").trim().toLowerCase();
            @SuppressWarnings("unchecked")
            List<String> answers = (List<String>) body.getOrDefault("answers", List.of());
            String newPassword   = str(body, "newPassword");

            if (newPassword.length() < 8) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Le nouveau mot de passe doit contenir au moins 8 caractères."));
            }

            userService.resetPasswordIfAnswersMatch(email, answers, newPassword);
            return ResponseEntity.ok(Map.of("ok", true));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Returns the predefined list of security questions (for registration form). */
    @GetMapping("/questions")
    public ResponseEntity<?> questions() {
        return ResponseEntity.ok(UserService.QUESTIONS);
    }

    // ── Email verification ────────────────────────────────────────────────────

    @GetMapping("/verify")
    public org.springframework.http.ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        try {
            userService.verifyEmail(token);
            // Redirect to SPA with success flag
            return org.springframework.http.ResponseEntity
                .status(302)
                .location(URI.create(baseUrl + "/?verified=true"))
                .build();
        } catch (IllegalArgumentException e) {
            return org.springframework.http.ResponseEntity
                .status(302)
                .location(URI.create(baseUrl + "/?verifyError=" +
                    java.net.URLEncoder.encode(e.getMessage(), java.nio.charset.StandardCharsets.UTF_8)))
                .build();
        }
    }

    // ── Password reset via email ──────────────────────────────────────────────

    /** Step 1: user submits their email → we send a reset link */
    @PostMapping("/recover/send")
    public ResponseEntity<?> recoverSend(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        try {
            userService.sendPasswordResetEmail(email);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Step 2: user clicked the link in the email → reset password with token */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String token       = body.getOrDefault("token", "");
        String newPassword = body.getOrDefault("newPassword", "");
        if (newPassword.length() < 8) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Le mot de passe doit contenir au moins 8 caractères."));
        }
        try {
            userService.resetPasswordByToken(token, newPassword);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── DTO builder ────────────────────────────────────────────────────────────

    public static Map<String, Object> toDto(User u) {
        Integer limit  = u.getWeeklyVideoLimit();
        Integer count  = u.getWeeklyViewCount();
        Integer left   = (limit != null) ? Math.max(0, limit - count) : null;

        // Days until weekly reset
        LocalDate today  = LocalDate.now();
        LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);
        LocalDate nextReset = monday.plusWeeks(1);
        long daysToReset = ChronoUnit.DAYS.between(today, nextReset);

        return Map.ofEntries(
            Map.entry("id",                  u.getId()),
            Map.entry("email",               u.getEmail()),
            Map.entry("firstName",           u.getFirstName() != null  ? u.getFirstName()  : ""),
            Map.entry("lastName",            u.getLastName()  != null  ? u.getLastName()   : ""),
            Map.entry("role",                u.getRole().name()),
            Map.entry("nativeLanguage",      u.getNativeLanguage()      != null ? u.getNativeLanguage()      : ""),
            Map.entry("languagesToLearn",    u.getLanguagesToLearn()    != null ? u.getLanguagesToLearn()    : "[]"),
            Map.entry("weeklyVideoLimit",    limit  != null ? limit  : -1),
            Map.entry("weeklyViewCount",     count  != null ? count  : 0),
            Map.entry("weeklyViewsLeft",     left   != null ? left   : -1),
            Map.entry("daysToReset",         daysToReset),
            Map.entry("birthYear",           u.getBirthYear()           != null ? u.getBirthYear()           : 0),
            Map.entry("country",             u.getCountry()             != null ? u.getCountry()             : ""),
            Map.entry("learningGoals",       u.getLearningGoals()       != null ? u.getLearningGoals()       : "[]"),
            Map.entry("learningLevel",       u.getLearningLevel()       != null ? u.getLearningLevel()       : "{}"),
            Map.entry("studyGoalMinutesWeek",u.getStudyGoalMinutesWeek()!= null ? u.getStudyGoalMinutesWeek(): 0),
            Map.entry("active",              u.isActive()),
            Map.entry("createdAt",           u.getCreatedAt() != null ? u.getCreatedAt().toString() : "")
        );
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "";
    }
}
