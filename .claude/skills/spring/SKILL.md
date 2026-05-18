---
name: spring
description: >
  Guide for adding or modifying Spring Boot backend code in the DualSub project.
  Use this skill whenever the user wants to add a new feature, endpoint, entity,
  service, or repository — or when modifying existing backend logic. Triggers on:
  "ajoute un endpoint", "crée une entité", "ajoute une colonne", "nouveau service",
  "ajoute un champ", "migration Liquibase", "nouvelle API", "ajoute une fonctionnalité"
  or any request involving Java backend changes to this Spring Boot project.
---

# Spring Boot — DualSub Development Patterns

## Project structure

```
src/main/java/com/dualsub/
  controller/   REST endpoints (@RestController)
  service/      Business logic (@Service)
  model/        JPA entities (@Entity)
  repository/   Spring Data JPA interfaces
  security/     Spring Security config
  config/       App config beans

src/main/resources/
  db/changelog/ Liquibase migration files
  static/       Frontend (index.html, app.js, style.css)
  application.properties
  application-linux.properties
  application-local.properties  ← gitignored, contains secrets
```

## Key conventions

- **No `ddl-auto`** — schema is 100% managed by Liquibase. Never use `create`, `update`, or `create-drop`.
- **`open-in-view=false`** — lazy relations must use `@JsonIgnore` or be eagerly fetched in the service layer to avoid `LazyInitializationException`.
- **`@Transactional`** on service methods that write to multiple tables.
- **H2 file database** with `AUTO_SERVER=TRUE` — the app holds a TCP lock, external tools connect via the H2 TCP server.
- **Security** — all `/api/admin/**` endpoints require `ROLE_ADMIN`. Use `@PreAuthorize` or the `SecurityConfig` `requestMatchers`.

## Adding a new feature — checklist

### 1. Liquibase migration (if schema changes)

Create a new file in `src/main/resources/db/changelog/changes/`:
```xml
<!-- YYYYMMDD-description.xml -->
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog" ...>
    <changeSet id="YYYYMMDD-1" author="dev">
        <addColumn tableName="USERS">
            <column name="NEW_FIELD" type="VARCHAR(255)"/>
        </addColumn>
    </changeSet>
</databaseChangeLog>
```

Then reference it in `db.changelog-master.yaml`:
```yaml
- include:
    file: db/changelog/changes/YYYYMMDD-description.xml
```

### 2. Entity field

Add the field to the `@Entity` class with appropriate JPA annotations:
```java
@Column(name = "NEW_FIELD")
private String newField;
// + getter/setter
```

### 3. Repository method

Spring Data JPA derived queries:
```java
// Find
Optional<User> findByEmail(String email);
List<User> findByActiveTrue();

// Delete (use in @Transactional service methods)
void deleteByUser_Id(Long userId);

// Count
long countByRole(Role role);
```

### 4. Service method

```java
@Transactional
public ReturnType doSomething(Long id, ...) {
    Entity entity = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Not found: " + id));
    // business logic
    return repository.save(entity);
}
```

### 5. REST endpoint

```java
@RestController
@RequestMapping("/api/...")
public class MyController {

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.get(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body,
                                    java.security.Principal principal) {
        // principal.getName() → logged-in user's email
    }
}
```

### 6. Security — protect an endpoint

In `SecurityConfig.java`, add inside `requestMatchers`:
```java
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.requestMatchers("/api/user/**").authenticated()
```

Or use method-level security (already enabled):
```java
@PreAuthorize("hasRole('ADMIN')")
public void adminOnlyMethod() { ... }
```

## Existing entities (table names)

| Entity | Table | Key fields |
|---|---|---|
| `User` | `USERS` | id, email, role (LIMITED/NORMAL/SUPER/ADMIN), active, locked |
| `WatchHistory` | `WATCH_HISTORY` | id, user (@ManyToOne @JsonIgnore), videoId, title |
| `SupportMessage` | `SUPPORT_MESSAGES` | id, user, subject, body, status (OPEN/IN_PROGRESS/CLOSED) |
| `AccountToken` | `ACCOUNT_TOKENS` | id, user, token, type, expiresAt |
| `SecurityQuestion` | `SECURITY_QUESTIONS` | id, user, question, answerHash |
| `TranscriptCache` | `TRANSCRIPT_CACHE` | id, videoId, data |
| `TranslationCache` | `TRANSLATION_CACHE` | id, cacheKey, data |
| `UserPreferences` | `USER_PREFERENCES` | id, user, lang1, lang2 |

## Common gotchas

- **`@JsonIgnore` on `@ManyToOne`** — always add it when the entity is serialized directly to JSON and `open-in-view=false` is set. Forgetting this causes `LazyInitializationException` at serialization time.
- **Delete cascade order** — delete child records before the parent: watch_history → account_tokens → security_questions → support_messages → users.
- **`AUTO_SERVER=TRUE` incompatible with `DB_CLOSE_ON_EXIT=FALSE`** — don't add the latter.
- **Static assets need a rebuild** — they are embedded in the fat JAR. After any HTML/CSS/JS change, bump `?v=N` in `index.html` and rebuild. Use the `/deploy` skill.
- **`application-local.properties`** — contains the Resend API key. It exists on the WSL2 machine but is gitignored. It is embedded in the JAR at build time from `src/main/resources/`.
