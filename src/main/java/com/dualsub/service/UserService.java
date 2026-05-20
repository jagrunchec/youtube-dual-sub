package com.dualsub.service;

import com.dualsub.model.AccountToken;
import com.dualsub.model.AccountToken.TokenType;
import com.dualsub.model.Role;
import com.dualsub.model.SecurityQuestion;
import com.dualsub.model.User;
import com.dualsub.repository.AccountTokenRepository;
import com.dualsub.repository.SecurityQuestionRepository;
import com.dualsub.repository.UserRepository;
import com.dualsub.repository.WatchHistoryRepository;
import com.dualsub.repository.SupportMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    /** Default weekly video cap for newly registered LIMITED accounts. */
    public static final int DEFAULT_LIMITED_QUOTA = 10;

    /**
     * Predefined security questions (key → French label).
     * Keys are stored in the DB; labels are shown to the user.
     */
    public static final Map<String, String> QUESTIONS;
    static {
        Map<String, String> m = new LinkedHashMap<>();
        // Famille
        m.put("MOTHER_MAIDEN",      "Quel est le nom de jeune fille de votre mère ?");
        m.put("GRANDMA_MATERNAL",   "Quel est le prénom de votre grand-mère maternelle ?");
        m.put("GRANDMA_PATERNAL",   "Quel est le prénom de votre grand-mère paternelle ?");
        m.put("PARENTS_MET_CITY",   "Dans quelle ville vos parents se sont-ils rencontrés ?");
        m.put("SIBLING_FIRST_NAME", "Quel est le prénom de votre frère ou sœur aîné(e) ?");
        // Enfance
        m.put("BIRTH_CITY",         "Dans quelle ville êtes-vous né(e) ?");
        m.put("CHILDHOOD_NICK",     "Quel était votre surnom d'enfance ?");
        m.put("CHILDHOOD_HERO",     "Quel était votre héros ou personnage préféré d'enfance ?");
        m.put("CHILDHOOD_FRIEND",   "Quel est le prénom de votre meilleur(e) ami(e) d'enfance ?");
        m.put("CHILDHOOD_STREET",   "Dans quelle rue avez-vous grandi ?");
        // École
        m.put("PRIMARY_SCHOOL",     "Quel est le nom de votre école primaire ?");
        m.put("FIRST_TEACHER",      "Quel est le nom de votre premier(e) instituteur/institutrice ?");
        m.put("HIGH_SCHOOL",        "Quel est le nom de votre lycée ?");
        // Animaux & possessions
        m.put("FIRST_PET",          "Quel était le nom de votre premier animal de compagnie ?");
        m.put("FIRST_CAR",          "Quelle était la marque de votre première voiture ?");
        m.put("FIRST_PHONE",        "Quelle était la marque de votre premier téléphone portable ?");
        // Loisirs & culture
        m.put("FAVORITE_BOOK",      "Quel est le titre de votre livre préféré d'enfance ?");
        m.put("FAVORITE_FILM",      "Quel est votre film préféré de tous les temps ?");
        m.put("FIRST_CONCERT",      "Quel est le premier concert auquel vous avez assisté ?");
        m.put("FAVORITE_SPORT",     "Quel était votre sport préféré dans votre jeunesse ?");
        // Vie professionnelle & voyages
        m.put("FIRST_JOB",          "Quel était votre premier emploi ?");
        m.put("DREAM_DESTINATION",  "Quelle est la destination de voyage dont vous avez toujours rêvé ?");
        m.put("FIRST_TRIP_ABROAD",  "Quel est le premier pays étranger que vous avez visité ?");
        QUESTIONS = m;
    }

    @Value("${app.mail.enabled:true}")
    private boolean mailEnabled;

    private final UserRepository             userRepository;
    private final SecurityQuestionRepository sqRepository;
    private final AccountTokenRepository     tokenRepository;
    private final WatchHistoryRepository      watchHistoryRepository;
    private final SupportMessageRepository   supportMessageRepository;
    private final PasswordEncoder            passwordEncoder;
    private final EmailService               emailService;

    public UserService(UserRepository userRepository,
                       SecurityQuestionRepository sqRepository,
                       AccountTokenRepository tokenRepository,
                       WatchHistoryRepository watchHistoryRepository,
                       SupportMessageRepository supportMessageRepository,
                       PasswordEncoder passwordEncoder,
                       EmailService emailService) {
        this.userRepository  = userRepository;
        this.sqRepository    = sqRepository;
        this.tokenRepository = tokenRepository;
        this.watchHistoryRepository    = watchHistoryRepository;
        this.supportMessageRepository = supportMessageRepository;
        this.passwordEncoder           = passwordEncoder;
        this.emailService    = emailService;
    }

    // ── Lookup ──────────────────────────────────────────────────────────────

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + email));
    }

    public User getById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + id));
    }

    public List<User> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    // ── Registration ─────────────────────────────────────────────────────────

    @Transactional
    public User register(String email, String rawPassword, String firstName, String lastName,
                         String nativeLanguage, String languagesToLearn,
                         Integer birthYear, String country,
                         List<String> questionKeys, List<String> rawAnswers) {

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email.");
        }
        if (questionKeys.size() != rawAnswers.size() || questionKeys.size() < 2) {
            throw new IllegalArgumentException("Deux questions de sécurité sont requises.");
        }

        User user = new User();
        user.setEmail(email.trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setRole(Role.NORMAL);
        // Account is inactive until email is verified (unless mail is disabled)
        user.setActive(!mailEnabled);
        user.setCreatedAt(LocalDateTime.now());
        // Optional profile fields
        if (nativeLanguage   != null && !nativeLanguage.isBlank())   user.setNativeLanguage(nativeLanguage.trim());
        if (languagesToLearn != null && !languagesToLearn.isBlank())  user.setLanguagesToLearn(languagesToLearn);
        if (birthYear        != null)                                 user.setBirthYear(birthYear);
        if (country          != null && !country.isBlank())           user.setCountry(country.trim());
        user = userRepository.save(user);

        for (int i = 0; i < questionKeys.size(); i++) {
            SecurityQuestion sq = new SecurityQuestion();
            sq.setUser(user);
            sq.setQuestionKey(questionKeys.get(i));
            sq.setAnswerHash(passwordEncoder.encode(normalise(rawAnswers.get(i))));
            sq.setQuestionOrder(i + 1);
            sqRepository.save(sq);
        }

        // Send verification email (only when mail is enabled)
        if (mailEnabled) {
            String token = createToken(user, TokenType.EMAIL_VERIFICATION,
                                       LocalDateTime.now().plusHours(24));
            emailService.sendVerificationEmail(user, token);
        }

        return user;
    }

    // ── Email verification ────────────────────────────────────────────────────

    @Transactional
    public void verifyEmail(String token) {
        AccountToken t = tokenRepository
            .findByTokenAndTokenType(token, TokenType.EMAIL_VERIFICATION)
            .orElseThrow(() -> new IllegalArgumentException("Lien de vérification invalide ou expiré."));

        if (t.isUsed())    throw new IllegalArgumentException("Ce lien a déjà été utilisé.");
        if (t.isExpired()) throw new IllegalArgumentException("Ce lien a expiré. Créez un nouveau compte.");

        t.setUsedAt(LocalDateTime.now());
        tokenRepository.save(t);

        User user = t.getUser();
        user.setActive(true);
        userRepository.save(user);
        System.out.println("[Auth] Email verified for " + user.getEmail());
    }

    // ── Password reset via email token ────────────────────────────────────────

    @Transactional
    public void sendPasswordResetEmail(String email) {
        User user = getByEmail(email);
        if (!user.isActive()) {
            throw new IllegalArgumentException("Ce compte n'est pas encore activé.");
        }
        String token = createToken(user, TokenType.PASSWORD_RESET,
                                   LocalDateTime.now().plusHours(1));
        emailService.sendPasswordResetEmail(user, token);
    }

    @Transactional
    public void resetPasswordByToken(String token, String newRaw) {
        AccountToken t = tokenRepository
            .findByTokenAndTokenType(token, TokenType.PASSWORD_RESET)
            .orElseThrow(() -> new IllegalArgumentException("Lien de réinitialisation invalide ou expiré."));

        if (t.isUsed())    throw new IllegalArgumentException("Ce lien a déjà été utilisé.");
        if (t.isExpired()) throw new IllegalArgumentException("Ce lien a expiré (validité : 1 heure). Recommencez la procédure.");

        t.setUsedAt(LocalDateTime.now());
        tokenRepository.save(t);

        User user = t.getUser();
        user.setPasswordHash(passwordEncoder.encode(newRaw));
        userRepository.save(user);
        System.out.println("[Auth] Password reset for " + user.getEmail());
    }

    // ── Token helper ──────────────────────────────────────────────────────────

    private String createToken(User user, TokenType type, LocalDateTime expiresAt) {
        AccountToken t = new AccountToken();
        t.setToken(UUID.randomUUID().toString());
        t.setUser(user);
        t.setTokenType(type);
        t.setExpiresAt(expiresAt);
        return tokenRepository.save(t).getToken();
    }

    // ── Profile update ───────────────────────────────────────────────────────

    @Transactional
    public User updateProfile(Long userId, String firstName, String lastName,
                               String nativeLanguage, String languagesToLearn,
                               Integer birthYear, String country,
                               String learningGoals, String learningLevel,
                               Integer studyGoalMinutesWeek,
                               Integer ollamaAutoMinutes,
                               Integer whisperAutoMinutes) {
        User user = getById(userId);
        if (firstName != null)            user.setFirstName(firstName.trim());
        if (lastName != null)             user.setLastName(lastName.trim());
        if (nativeLanguage != null)       user.setNativeLanguage(nativeLanguage);
        if (languagesToLearn != null)     user.setLanguagesToLearn(languagesToLearn);
        if (birthYear != null)            user.setBirthYear(birthYear);
        if (country != null)             user.setCountry(country);
        if (learningGoals != null)        user.setLearningGoals(learningGoals);
        if (learningLevel != null)        user.setLearningLevel(learningLevel);
        if (studyGoalMinutesWeek != null) user.setStudyGoalMinutesWeek(studyGoalMinutesWeek);
        // ollamaAutoMinutes: -1=always off, 0=always on, N=auto. Always saved when present.
        if (ollamaAutoMinutes != null)    user.setOllamaAutoMinutes(ollamaAutoMinutes);
        // whisperAutoMinutes: threshold in minutes for large-v3 vs medium selection.
        if (whisperAutoMinutes != null)   user.setWhisperAutoMinutes(whisperAutoMinutes);
        return userRepository.save(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentRaw, String newRaw) {
        User user = getById(userId);
        if (!passwordEncoder.matches(currentRaw, user.getPasswordHash())) {
            throw new IllegalArgumentException("Mot de passe actuel incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(newRaw));
        userRepository.save(user);
    }

    // ── Password recovery ────────────────────────────────────────────────────

    public List<SecurityQuestion> getSecurityQuestions(User user) {
        return sqRepository.findByUserOrderByQuestionOrder(user);
    }

    @Transactional
    public void resetPasswordIfAnswersMatch(String email, List<String> answers, String newRaw) {
        User user = getByEmail(email);
        List<SecurityQuestion> questions = sqRepository.findByUserOrderByQuestionOrder(user);

        if (questions.size() < 2 || answers.size() < 2) {
            throw new IllegalArgumentException("Réponses insuffisantes.");
        }
        for (int i = 0; i < Math.min(questions.size(), answers.size()); i++) {
            if (!passwordEncoder.matches(normalise(answers.get(i)), questions.get(i).getAnswerHash())) {
                throw new IllegalArgumentException("Réponse incorrecte à la question " + (i + 1) + ".");
            }
        }
        user.setPasswordHash(passwordEncoder.encode(newRaw));
        userRepository.save(user);
    }

    // ── Weekly limit (LIMITED users) ─────────────────────────────────────────

    /**
     * Atomically checks the weekly quota and increments the counter.
     * Must be called inside a transaction so the read-modify-write is consistent.
     *
     * @throws WeeklyLimitExceededException if the user has reached their limit.
     */
    @Transactional
    public void checkAndIncrementWeeklyView(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();

        // Only LIMITED users have a quota
        if (user.getRole() != Role.LIMITED || user.getWeeklyVideoLimit() == null) return;

        // Reset counter at the start of each ISO week (Monday)
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        if (user.getWeeklyResetDate() == null || user.getWeeklyResetDate().isBefore(monday)) {
            user.setWeeklyViewCount(0);
            user.setWeeklyResetDate(monday);
        }

        int count = user.getWeeklyViewCount();
        int limit = user.getWeeklyVideoLimit();

        if (count >= limit) {
            LocalDate nextReset = monday.plusWeeks(1);
            throw new WeeklyLimitExceededException(limit, nextReset);
        }

        user.setWeeklyViewCount(count + 1);
        userRepository.save(user);
    }

    // ── Admin operations ─────────────────────────────────────────────────────

    @Transactional
    public User setRole(Long userId, Role role) {
        User user = getById(userId);
        user.setRole(role);
        // Set/clear default quota when switching to/from LIMITED
        if (role == Role.LIMITED && user.getWeeklyVideoLimit() == null) {
            user.setWeeklyVideoLimit(DEFAULT_LIMITED_QUOTA);
        } else if (role != Role.LIMITED) {
            user.setWeeklyVideoLimit(null);
        }
        return userRepository.save(user);
    }

    @Transactional
    public User setWeeklyLimit(Long userId, Integer limit) {
        User user = getById(userId);
        user.setWeeklyVideoLimit(limit);
        return userRepository.save(user);
    }

    @Transactional
    public User setActive(Long userId, boolean active) {
        User user = getById(userId);
        user.setActive(active);
        return userRepository.save(user);
    }

    @Transactional
    public void recordLogin(String email) {
        userRepository.findByEmail(email).ifPresent(u -> {
            u.setLastLoginAt(LocalDateTime.now());
            // Clear any leftover lockout state on successful login
            u.setFailedLoginAttempts(0);
            u.setAccountLockedUntil(null);
            userRepository.save(u);
        });
    }

    // ── Login lockout ─────────────────────────────────────────────────────────

    public static final int MAX_FAILED_ATTEMPTS = 10;
    public static final int LOCKOUT_MINUTES     = 30;

    /**
     * Increments the failed-attempt counter for the given email.
     * Locks the account for LOCKOUT_MINUTES when the counter reaches MAX_FAILED_ATTEMPTS.
     * No-op if the email does not correspond to any user (prevents user enumeration).
     */
    @Transactional
    public void recordFailedLogin(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            int attempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                user.setAccountLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
                System.out.printf("[Security] Account LOCKED for %s after %d failed attempts (until %s)%n",
                    email, attempts, user.getAccountLockedUntil());
            }
            userRepository.save(user);
        });
    }

    /**
     * Manually unlocks an account and resets the failed-attempt counter.
     * Used by admins.
     */
    @Transactional
    public User unlockAccount(Long userId) {
        User user = getById(userId);
        user.setFailedLoginAttempts(0);
        user.setAccountLockedUntil(null);
        System.out.println("[Security] Account manually unlocked for " + user.getEmail());
        return userRepository.save(user);
    }


    // __ Delete user (admin) __________________________________________________

    /**
     * Permanently deletes a user and all their associated data:
     * watch history, account tokens, security questions, support messages.
     * Cannot delete an ADMIN account.
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = getById(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Impossible de supprimer un compte ADMIN.");
        }
        watchHistoryRepository.deleteByUser_Id(userId);
        tokenRepository.deleteByUser_Id(userId);
        sqRepository.deleteByUser_Id(userId);
        supportMessageRepository.deleteByUser_Id(userId);
        userRepository.delete(user);
        System.out.println("[Admin] User deleted: " + user.getEmail() + " (id=" + userId + ")");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Normalises a security-question answer for case- and whitespace-insensitive hashing. */
    private String normalise(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    // ── Inner exception ──────────────────────────────────────────────────────

    public static class WeeklyLimitExceededException extends RuntimeException {
        private final int       limit;
        private final LocalDate nextReset;

        public WeeklyLimitExceededException(int limit, LocalDate nextReset) {
            super("Limite hebdomadaire atteinte");
            this.limit     = limit;
            this.nextReset = nextReset;
        }

        public int       getLimit()     { return limit; }
        public LocalDate getNextReset() { return nextReset; }
    }
}
