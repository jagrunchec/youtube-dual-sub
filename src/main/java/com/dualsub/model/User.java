package com.dualsub.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.NORMAL;

    /** BCP-47 code of the user's native language (e.g. "fr"). */
    @Column(name = "native_language", length = 10)
    private String nativeLanguage;

    /** JSON array of language codes the user wants to learn, e.g. ["en","de"]. */
    @Column(name = "languages_to_learn", columnDefinition = "TEXT")
    private String languagesToLearn;

    /** Weekly video cap — null means unlimited. Meaningful only for ROLE_LIMITED. */
    @Column(name = "weekly_video_limit")
    private Integer weeklyVideoLimit;

    /** Number of videos watched in the current ISO week. */
    @Column(name = "weekly_view_count")
    private Integer weeklyViewCount = 0;

    /** Monday of the week when weeklyViewCount was last reset. */
    @Column(name = "weekly_reset_date")
    private LocalDate weeklyResetDate;

    @Column(name = "birth_year")
    private Integer birthYear;

    /** ISO 3166-1 alpha-2 country code. */
    @Column(name = "country", length = 10)
    private String country;

    /** JSON array of learning goal keys, e.g. ["casual","travel"]. */
    @Column(name = "learning_goals", columnDefinition = "TEXT")
    private String learningGoals;

    /** JSON map of language → CECRL level, e.g. {"en":"B2","de":"A1"}. */
    @Column(name = "learning_level", columnDefinition = "TEXT")
    private String learningLevel;

    /** User's self-declared weekly study time goal in minutes. */
    @Column(name = "study_goal_minutes_week")
    private Integer studyGoalMinutesWeek;

    /**
     * Ollama auto-enable threshold (minutes).
     *  -1 = always off (never auto-check)
     *   0 = always on  (always auto-check)
     *   N = auto: check the box only for videos longer than N minutes
     * Default 2 (set by Liquibase).
     */
    @Column(name = "ollama_auto_minutes")
    private Integer ollamaAutoMinutes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /** Number of consecutive failed login attempts since last success. */
    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    /** If non-null and in the future, the account is temporarily locked. */
    @Column(name = "account_locked_until")
    private LocalDateTime accountLockedUntil;

    // ── getters / setters ──────────────────────────────────────────────────────

    public Long getId()                                  { return id; }
    public void setId(Long id)                           { this.id = id; }

    public String getEmail()                             { return email; }
    public void setEmail(String email)                   { this.email = email; }

    public String getPasswordHash()                      { return passwordHash; }
    public void setPasswordHash(String passwordHash)     { this.passwordHash = passwordHash; }

    public String getFirstName()                         { return firstName; }
    public void setFirstName(String firstName)           { this.firstName = firstName; }

    public String getLastName()                          { return lastName; }
    public void setLastName(String lastName)             { this.lastName = lastName; }

    public Role getRole()                                { return role; }
    public void setRole(Role role)                       { this.role = role; }

    public String getNativeLanguage()                    { return nativeLanguage; }
    public void setNativeLanguage(String nativeLanguage) { this.nativeLanguage = nativeLanguage; }

    public String getLanguagesToLearn()                  { return languagesToLearn; }
    public void setLanguagesToLearn(String v)            { this.languagesToLearn = v; }

    public Integer getWeeklyVideoLimit()                 { return weeklyVideoLimit; }
    public void setWeeklyVideoLimit(Integer v)           { this.weeklyVideoLimit = v; }

    public Integer getWeeklyViewCount()                  { return weeklyViewCount == null ? 0 : weeklyViewCount; }
    public void setWeeklyViewCount(Integer v)            { this.weeklyViewCount = v; }

    public LocalDate getWeeklyResetDate()                { return weeklyResetDate; }
    public void setWeeklyResetDate(LocalDate v)          { this.weeklyResetDate = v; }

    public Integer getBirthYear()                        { return birthYear; }
    public void setBirthYear(Integer birthYear)          { this.birthYear = birthYear; }

    public String getCountry()                           { return country; }
    public void setCountry(String country)               { this.country = country; }

    public String getLearningGoals()                     { return learningGoals; }
    public void setLearningGoals(String learningGoals)   { this.learningGoals = learningGoals; }

    public String getLearningLevel()                     { return learningLevel; }
    public void setLearningLevel(String learningLevel)   { this.learningLevel = learningLevel; }

    public Integer getStudyGoalMinutesWeek()             { return studyGoalMinutesWeek; }
    public void setStudyGoalMinutesWeek(Integer v)       { this.studyGoalMinutesWeek = v; }

    public Integer getOllamaAutoMinutes()                { return ollamaAutoMinutes; }
    public void setOllamaAutoMinutes(Integer v)          { this.ollamaAutoMinutes = v; }

    public boolean isActive()                            { return active; }
    public void setActive(boolean active)                { this.active = active; }

    public LocalDateTime getCreatedAt()                  { return createdAt; }
    public void setCreatedAt(LocalDateTime v)            { this.createdAt = v; }

    public LocalDateTime getLastLoginAt()                { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime v)          { this.lastLoginAt = v; }

    public int getFailedLoginAttempts()                  { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int v)            { this.failedLoginAttempts = v; }

    public LocalDateTime getAccountLockedUntil()         { return accountLockedUntil; }
    public void setAccountLockedUntil(LocalDateTime v)   { this.accountLockedUntil = v; }

    /** Returns true if the account is currently locked out. */
    public boolean isLocked() {
        return accountLockedUntil != null && accountLockedUntil.isAfter(LocalDateTime.now());
    }
}
