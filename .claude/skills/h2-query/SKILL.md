---
name: h2-query
description: >
  Query the DualSub H2 embedded database directly from Windows via PowerShell + WSL2.
  Use this skill whenever the user asks about database contents, wants to inspect data,
  verify watch history, check user records, look at cached transcripts or translations,
  or asks any question that could be answered by querying the app's H2 database.
  Trigger on phrases like "regarde dans la base", "accède à la DB", "vérifie en base",
  "what's in the database", "check the database", "query the DB", "montre moi les données",
  or any question about the state of stored data (users, history, caches, preferences).
---

# H2 Query Skill

This skill lets you query the DualSub app's H2 embedded database directly from PowerShell via WSL2, without needing a browser or the H2 console UI.

## Connection approach

The app uses H2 in file mode with `AUTO_SERVER=TRUE`. When Spring Boot is running, H2 automatically starts a TCP server and writes its port to a lock file (`data/dualsub.lock.db`). Connecting with `AUTO_SERVER=TRUE` from H2 Shell causes it to read the lock file and connect via TCP — no file locking conflict.

- **DB file (WSL2):** `/home/dev/youtube-dual-sub/data/dualsub`
- **H2 JAR:** `/home/dev/.m2/repository/com/h2database/h2/2.2.224/h2-2.2.224.jar`
- **User:** `sa` / **Password:** *(empty)*

## Standard query command

Always redirect to `/tmp/h2result.txt` and cat it back — direct stdout capture in PowerShell loses H2 Shell's tabular output:

```powershell
wsl -e bash -c "java -cp /home/dev/.m2/repository/com/h2database/h2/2.2.224/h2-2.2.224.jar org.h2.tools.Shell -url 'jdbc:h2:file:/home/dev/youtube-dual-sub/data/dualsub;IFEXISTS=TRUE;AUTO_SERVER=TRUE' -user sa -password '' -sql 'YOUR SQL HERE' > /tmp/h2result.txt 2>&1; cat /tmp/h2result.txt"
```

No trailing semicolon in the SQL. Single quotes around the whole `bash -c` argument.

## Common queries

### List all users
```sql
SELECT ID, EMAIL, ROLE, CREATED_AT FROM USERS ORDER BY ID
```

### Watch history for a user (replace USER_ID)
```sql
SELECT ID, VIDEO_ID, VIDEO_TITLE, LANG1, LANG2, WATCHED_AT FROM WATCH_HISTORY WHERE USER_ID = 33 ORDER BY WATCHED_AT DESC
```

### Full watch history with user emails
```sql
SELECT H.ID, H.VIDEO_ID, H.VIDEO_TITLE, U.EMAIL, H.LANG1, H.LANG2, H.WATCHED_AT FROM WATCH_HISTORY H LEFT JOIN USERS U ON U.ID = H.USER_ID ORDER BY H.WATCHED_AT DESC
```

### Cached transcripts
```sql
SELECT VIDEO_ID, LANGUAGE_CODE, ENTRY_COUNT, FETCHED_AT FROM TRANSCRIPT_CACHE ORDER BY FETCHED_AT DESC
```

### Cached translations
```sql
SELECT VIDEO_ID, LANG, ENTRY_COUNT, TRANSLATED_AT FROM TRANSLATION_CACHE ORDER BY TRANSLATED_AT DESC
```

### User preferences
```sql
SELECT * FROM USER_PREFERENCES
```

### List all app tables
```sql
SELECT TABLE_SCHEMA, TABLE_NAME FROM INFORMATION_SCHEMA.TABLES ORDER BY TABLE_NAME
```

## Table reference

| Table | Primary key | Notes |
|---|---|---|
| `USERS` | `ID` | Email, BCrypt password, ROLE (ADMIN/NORMAL), CREATED_AT |
| `USER_PREFERENCES` | `ID=1` | Singleton: lang1, lang2, immersion_mode, ui_lang |
| `WATCH_HISTORY` | `ID` | VIDEO_ID, VIDEO_TITLE, LANG1, LANG2, WATCHED_AT, USER_ID (nullable for anon) |
| `TRANSCRIPT_CACHE` | `VIDEO_ID` | LANGUAGE_CODE, ENTRIES_JSON (large), ENTRY_COUNT, FETCHED_AT |
| `TRANSLATION_CACHE` | `(VIDEO_ID, LANG)` | ENTRIES_JSON (large), ENTRY_COUNT, TRANSLATED_AT |
| `ACCOUNT_TOKENS` | `ID` | Email verification / password reset tokens |
| `SECURITY_QUESTIONS` | `ID` | User security Q&A |
| `SUPPORT_MESSAGES` | `ID` | Support contact messages |

## Error handling

| Error | Cause | Fix |
|---|---|---|
| `The file is locked` | Spring Boot not using AUTO_SERVER=TRUE (old JAR) | Rebuild: `mvn clean package -DskipTests` then restart |
| `Table "X" not found` | Wrong table name — use `USERS`, not `APP_USER` | Run the table list query above |
| `IFEXISTS=TRUE` error | DB file missing | `wsl -e bash -c "ls /home/dev/youtube-dual-sub/data/"` |
| `Wrong user name/password` | Don't use `jdbc:h2:tcp://` directly | Always use `jdbc:h2:file://...;AUTO_SERVER=TRUE` |

## Finding the H2 JAR (if version changes)

```powershell
wsl -e bash -c "find ~/.m2/repository/com/h2database -name 'h2-*.jar' | sort -V | tail -1"
```

## Presenting results

Format the output as a readable table. If a column contains JSON (like `ENTRIES_JSON`), show only the first 80 characters unless the user explicitly asked for the full content.
