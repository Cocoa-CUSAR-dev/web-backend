# Cocoa Web Backend (Kotlin)

Researcher-facing backend API for the **Cocoa Supply Chain Databank** (Is Thai Cacao). Serves the Next.js researcher web app: auth, researcher/form/task management, analytics (incl. spatial), and Excel data exports.

---

## Role in the 2026–2027 plan

Part of a system being modernized over a 10-month thesis (Jul 2026 – Apr 2027), in two phases:

- **Phase I (mandatory, by Dec 2026)** — LINE OA AI chatbot channel, farmer-app modernization, **SSO between LINE OA and the web platform**, reminders, and **web submission history**. The existing form system stays unchanged (no data migration). Refactoring runs alongside every sprint.
- **Phase II (gated, Dec 2026 – Apr 2027)** — Knowledge Base + Computer Vision.

**Where this service fits:** it is the **researcher/web-side backend**. New Phase I work landing here includes the **SSO token exchange** (so a LINE-authenticated farmer can open web links without a second login), the **submission-history** endpoints, and **reminder-cadence configuration** APIs. In Phase II it will host **Knowledge Base authoring** endpoints. Several security fixes (`BE-1/2/3`) are scheduled early in Phase I — see the weak-point register.

---

## Tech stack

- **Language:** Kotlin · **Framework:** Spring Boot
- **DB access:** jOOQ (explicit SQL, no ORM) against PostgreSQL (NeonDB)
- **Auth:** Spring Security + JWT (cookie-based)
- **Prereq:** JDK 21

## Project structure

```
src/main/kotlin/com/cocoa/web/
├── base/           # base classes/interfaces
├── config/         # Spring configuration (security, CORS, ...)
├── controller/     # REST controllers
├── exception/      # exception handling
├── jooq/           # custom jOOQ bindings/converters
├── model/          # plain Kotlin data classes (no jOOQ types leak out)
├── repository/     # DB access via jOOQ
├── security/       # JWT auth filter
├── service/        # business logic
└── util/
src/generated/      # jOOQ-generated classes — do NOT edit by hand
```

Architecture rules to preserve: jOOQ over JPA (explicit SQL); repositories don't inject other repositories (cross-domain assembly happens in services); model classes never expose jOOQ record types.

## First-time setup

There are **two run paths, each with its own config file** (both gitignored — never commit them):

| Run path | Config file | Create it from |
|---|---|---|
| `./gradlew bootRun` / `java -jar` | root **`.env`** (via `spring-dotenv`; also read by jOOQ codegen) | `.env.sample` |
| IntelliJ **"Run Server"** run config | **`src/main/resources/local.properties`** (loaded via `-Dspring.config.location`, replacing `application.properties`) | `local.properties.sample` |

**Gradle / command line:**
```bash
cp .env.sample .env            # project root, next to build.gradle.kts
./gradlew generateJooq         # needs .env; re-run after every DB schema change
./gradlew bootRun
```

**IntelliJ:** copy `src/main/resources/local.properties.sample` → `local.properties`, fill in values, then use the committed **"Re-Generate JOOQ"** and **"Run Server"** run configs (in `.run/`).

Server: `http://localhost:3001`, under context path `/api/v1`.

> The old README told you to create `./src/.env` — that path is wrong; `build.gradle.kts` reads the `.env` at the **project root**. Note `local.properties` is *not* dead config: it's the config source for the IntelliJ "Run Server" path, which is why it (and `.env`) hold real secrets and are gitignored.

## API docs

With the server running (paths are under `/api/v1`):

| Path | Description |
|---|---|
| `/api/v1/swagger-ui.html` | Swagger UI |
| `/api/v1/api-docs` | OpenAPI JSON |

## Build & deploy

```bash
./gradlew clean build          # output: ./src/build/libs/cocoa.jar
java -jar cocoa.jar            # needs JDK 21 + the .env next to it
```

## Known issues tracked for Phase I

- `BE-1` — auth cookie not marked `Secure` (`CookieService.kt`); leaks over plain HTTP.
- `BE-2` / `BE-3` — task-response and bulk-export endpoints have **no authorization check** — any authenticated user can read/export others' data.
- `BE-6` — no automated tests.

Full list and fix order: the project docs site.

---

**Security note:** never commit `.env` or `local.properties`. Rotate the NeonDB password and `JWT_KEY` if there's any chance they were exposed in the old transfer folder.
