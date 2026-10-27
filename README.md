# NexusWMS

Autonomous warehouse management system. **100% Java**: JavaFX 21 desktop UI + Spring Boot 3.3 + MySQL 8, in one process.
AI agents *propose*; humans *approve*. Everything works with no AI key configured.

> **Verification status.** In the generation sandbox (no Maven Central) all 131 Java files were compiled against the real JavaFX API plus minimal stand-ins for
> Spring/JPA/ControlsFX/Spring AI/Vosk, and 120 unit tests were executed (everything except `EntityMappingTest`, which needs real H2 + Hibernate). `styles.css` was parsed with
> JavaFX's own CSS parser (0 errors). **Not verified:** the real Spring AI / Vosk / ControlsFX / Ikonli / AtlantaFX method signatures (they follow the 1.0.0 / 0.3.45 / 11.2.1 / 12.3.1 / 2.0.1 APIs
> from memory), visual rendering, MySQL behaviour, and a real microphone. Run `mvn -q clean test` first and report anything that fails.

## Prerequisites
| | Windows | macOS | Linux (Debian/Ubuntu) |
|---|---|---|---|
| JDK 21+ | `winget install EclipseAdoptium.Temurin.21.JDK` | `brew install --cask temurin@21` | `sudo apt install openjdk-21-jdk` |
| Maven 3.9+ | `winget install Apache.Maven` | `brew install maven` | `sudo apt install maven` |
| MySQL 8 | `winget install Oracle.MySQL` | `brew install mysql && brew services start mysql` | `sudo apt install mysql-server` |

## Database setup
```sql
CREATE DATABASE nexuswms CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'nexus'@'localhost' IDENTIFIED BY 'change-me';
GRANT ALL PRIVILEGES ON nexuswms.* TO 'nexus'@'localhost';
```
`schema.sql` runs (idempotently) on every start; `seed.sql` is loaded once, only when `products` is empty.

## Environment variables
| Variable | Default / meaning |
|---|---|
| `NEXUS_DB_URL` | `jdbc:mysql://localhost:3306/nexuswms?useSSL=false&serverTimezone=UTC&characterEncoding=utf8` |
| `NEXUS_DB_USER` / `NEXUS_DB_PASSWORD` | `nexus` / *(empty)* |
| `NEXUS_AI_ENABLED` | `false`. Set `true` to use an LLM for the assistant and the Replenishment agent |
| `NEXUS_AI_PROVIDER` | `anthropic` (default) or `openai` |
| `NEXUS_AI_API_KEY` | provider API key (never commit it) |
| `NEXUS_AI_MODEL` | blank = `claude-3-5-sonnet-latest` (anthropic) / `gpt-4o-mini` (openai) |
| `NEXUS_AI_TIMEOUT` / `NEXUS_AI_MAX_TOKENS` | `45` seconds / `1500` |
| `NEXUS_VOSK_MODEL_PATH` | folder of an unpacked Vosk model (enables the microphone) |
| `NEXUS_VOICE_MAX_SECONDS` | `8` – longest single utterance |
| `NEXUS_UI_THEME` | `PrimerLight` or `PrimerDark` (the top-bar toggle persists the choice) |
| `NEXUS_LOG_DIR` | `logs` |

## AI (assistant + Replenishment agent)
```bash
export NEXUS_AI_ENABLED=true NEXUS_AI_PROVIDER=anthropic NEXUS_AI_API_KEY=sk-ant-...   # or openai
```
* **Assistant** – recognised commands ("show low stock", "create order for 5 of SKU-1001") run deterministically; anything else goes to the LLM with
  read-only tools (`queryInventory`, `queryOrders`, `runForecast`, `summarizeTrends`) and a 12-message memory.
* **ReplenishmentAgent** – reads the forecast/low-stock state, sends a JSON snapshot to the LLM, requires a strict JSON answer, validates every item
  (known SKU, quantity 1…3× baseline, cost recomputed locally) and stores the result as a PENDING approval request. A human must approve.
  *RiskAgent* (probability maths) and *OrderAgent* (status rules) stay deterministic on purpose.
* **Failure handling** – invalid key → latched `AUTH` state, one clear message, no retries until restart; rate limit → 60 s pause; 3 timeouts/outages → 2 min pause;
  unparseable JSON → one automatic repair attempt, then deterministic rules. The UI always says which mode produced a result (Settings, Agents, Audit).
  Agents run **outside** any DB transaction, so a slow LLM never holds a connection.

## Voice commands (offline, Vosk)
1. Download a model, e.g. `vosk-model-small-en-us-0.15` from https://alphacephei.com/vosk/models and unzip it.
2. `export NEXUS_VOSK_MODEL_PATH=/path/to/vosk-model-small-en-us-0.15`
3. Click the microphone in the top bar (or **Ctrl+Shift+M**). Speak one command; the transcript and how it was understood are shown, then press **Run**.
   Examples: "show low stock for category packaging", "create order for five of SKU one zero zero one from Acme", "open approvals", "what is inventory value".
   Order creation always asks for confirmation and only creates a DRAFT.
* Linux needs a working default ALSA/PulseAudio input; macOS/Windows will prompt for microphone permission. The Vosk native library is bundled in the `vosk` JAR.
* Without a model the mic button explains how to set it up; typed commands work identically.

## Run
```bash
export NEXUS_DB_PASSWORD=change-me
mvn -q clean test          # acceptance gate: needs no MySQL (uses H2)
mvn javafx:run             # launches com.nexuswms.NexusLauncher
mvn package && java -jar target/nexuswms-1.0.0.jar
```
**IntelliJ:** run `com.nexuswms.NexusLauncher` — **not** `NexusWmsApplication`.

**Default logins (change them):** `admin/admin123` (ADMIN), `manager/manager123` (MANAGER), `operator/operator123` (OPERATOR).
There is no VIEWER seed user; insert one with a BCrypt hash if you need it.

## Architecture
```
 JavaFX UI (ui/*)  ── FxTaskHelper: Task on worker pool, Platform.runLater for UI ──┐
      │ app.bean(...)                                                               │
 Application services (service/*, forecasting/ForecastService, voice/*)            │
      │                              ▲ read-only tools                              │
 Agent layer (agents/*): Agent.propose() ─► AgentOrchestrator ─► approval_requests  │
      │                                        ApprovalService.approve() ─► services (same transactions)
 Persistence: Spring Data JPA / Hibernate (ddl-auto=validate) ─► MySQL 8 (schema.sql is authoritative)
 AssistantService: Spring AI ChatClient + WarehouseTools  ──or──  rule-based fallback (no key needed)
```

## Phase checklist
- [x] 1 Shell & dashboard (header, 30 s MySQL pill, sidebar, KPI cards, 12-week value chart, attention list, movement badges, theme toggle)
- [x] 2 Products & inventory CRUD (single-transaction `adjustStock`, soft delete, debounced search, pagination, Bean Validation decorations)
- [x] 3 Orders, suppliers, ledger drawer (partial receiving, reliability score)
- [x] 4 Warehouse map, analytics charts, notifications bell
- [x] 5 Forecasting & replenishment (moving average, exponential smoothing, safety stock; nightly `@Scheduled` + "Run forecast now")
- [x] 6 Assistant (Spring AI + fallback), command parser, text voice box (STT adapter stub)
- [x] 7 Agents, orchestrator, approvals, settings
- [x] 8 Roles, audit view, tests, JaCoCo, packaging

## Design decisions (documented per the brief)
- **Tables:** your spec said eight but listed nine; all nine exist, plus `approval_requests` (needed to persist agent proposals).
- **Supplier reliability:** `(onTime + 4) / (completed + 5)`; "on time" = fully received on/before `expected_date`. New suppliers start at 0.80.
- **Safety stock:** `ceil(z · σ_daily · √leadTime)`, service level 95 % (z≈1.645); the product's configured `safety_stock` acts as a floor.
- **Forecast:** daily SHIP demand over 90 days, exponential smoothing α=0.3; `reorder_qty = max(0, level·leadTime + safety − onHand − onOrder)`.
- **Bin capacity** (for the map colours) is modelled as 4 × reorder point, since the schema has no bin-capacity column.
- **Stock-out risk:** `1 − Φ((available − μ_L)/σ_L)` with μ_L, σ_L over the supplier lead time; threshold in Settings (default 30 %).
- **"Expedited transfer"** is executed as an *expedited purchase order* (shorter expected date): the single-warehouse schema has no source location to transfer from.
- **Approval policy:** stored and editable, but v1 always requires approval for financial/destructive actions (agents never auto-execute).
- **Seed:** executed by `SeedDataRunner`, not `spring.sql.init.data-locations` (which would re-run on every start).
- **Entities use public fields** to keep the codebase compact.
- **Error dialogs** use JavaFX `Alert` + ControlsFX `Notifications` (ControlsFX 11 no longer ships a `Dialogs` class).
- **Voice:** `VoskSpeechToTextAdapter` (offline) + `SpokenNormalizer`; see *Voice commands*.
- **User management UI** is not included (ADMIN permission exists in `Roles`); add users via SQL.

## Known risks (things to check first)
1. **Spring AI 1.0.0 on Spring Boot 3.3.5** (your build passed, so this works). Spring AI 1.0 targets Boot 3.4+. If the context fails to start, bump the parent to `3.4.x`. The `AnthropicChatModel.builder()` / `ChatClient` calls in `AssistantService` follow the 1.0 GA API; adjust if your version differs. Failures there degrade to rule-based mode at runtime but would break compilation.
2. **`ddl-auto=validate` on MySQL.** `ENUM` and `TEXT` columns are mapped with `columnDefinition = "enum"` / `"text"` so Hibernate's type-name check passes. If validation still complains about a column, fix the **entity**, not the schema.
3. **H2 compatibility of `schema.sql`.** `EntityMappingTest` runs the real script on H2 (MySQL mode). If H2 rejects a MySQL-only construct, the test fails with a clear message; that test round-trips every entity instead of using Hibernate's validator (H2 reports ENUM/TEXT metadata differently).
4. **Ikonli literals** (e.g. `mdal-dashboard`) are wrapped in a fallback; a wrong literal shows a dot instead of crashing.
5. **Coverage:** JaCoCo is configured (`target/site/jacoco/index.html`), but the ≥70 % target is unverified.

## Troubleshooting
- **MySQL connection:** the app still opens, shows a red *MySQL: offline* pill and an *Offline mode* banner with **Retry connection**. Check host/port/db/user/password (`NEXUS_DB_*`); the dialog shows the root cause.
- **"JavaFX runtime components are missing":** you launched `NexusWmsApplication` directly. Launch `NexusLauncher` (or `mvn javafx:run`).
- **`Schema-validation: wrong column type` / `missing column`:** an entity differs from `schema.sql`. Fix the entity.
- **`Access denied for user 'nexus'`:** create the user/grant (see *Database setup*) and export `NEXUS_DB_PASSWORD`.
- **Packaging:** the JAR contains the JavaFX natives of the *build* OS only; cross-platform packaging is a known limitation (Phase 8+).

## What changed in the production-hardening pass
**UI** – new design system (`css/styles.css`, light/dark tokens), Font Awesome icons via Ikonli, grouped sidebar with live approval badge, top bar (breadcrumb, global search `Ctrl+K`,
mic, notifications, theme, DB status), card layouts, striped/hoverable data tables with empty states, borderless draggable dialogs centred on the main window, inline field validation.
**Services** – every service validates its own input (not only the UI); stock movements must match their reason (RECEIVE/RETURN add, SHIP/SHRINK remove), never go below zero or below the
reserved quantity, and inactive products are frozen; orders reject negative/zero/oversized quantities, negative costs, past dates, inactive suppliers/products and merge duplicate lines;
a supplier with open orders cannot be deleted/deactivated, a product on an open order cannot be removed; orders and approvals are row-locked so double clicks or two users cannot
double-receive or double-approve; notifications fire only **after commit**; sign-in locks a username for 60 s after 5 failures; forecasts are pruned after 90 days; agent runs cannot overlap.
**UI null-safety** – table actions go through `withSelected`, cell factories cannot throw, stale search responses are ignored, spinners commit typed text, offline mode disables data calls.

## Accounts, passwords and exports
* **Users** (sidebar, ADMIN only): add users, change roles, reset passwords, activate/deactivate. The last active administrator can never be demoted or deactivated, and you cannot deactivate yourself.
* **Password policy** (enforced in `PasswordPolicy`, for every create/reset/change): ≥ 10 characters, at least one letter and one digit, must not contain the username, must not be a known default, max 72 bytes (BCrypt).
* **Seeded demo passwords are rejected at sign-in**: if you log in with `admin123`/`manager123`/`operator123`, a non-skippable "Choose a new password" dialog appears before the app opens.
  Everyone can change their own password under Settings → Account.
* **CSV export** on Inventory (all rows matching the current search, up to 50,000) and Audit (rows currently shown). UTF-8 with BOM for Excel; cells that could execute as spreadsheet formulas are neutralised.

## Operations
* **Backups:** `NEXUS_DB_PASSWORD=… ./scripts/backup-mysql.sh /var/backups/nexus` (consistent InnoDB snapshot, gzip-verified, 14-day retention). Schedule it with cron / Task Scheduler
  and copy the files off the machine. **Test a restore** with `scripts/restore-mysql.sh` on a scratch database before you rely on it.
* **Before go-live checklist:** seeded passwords replaced (the app forces this), a dedicated least-privilege MySQL user (not root), `NEXUS_AI_API_KEY` supplied via environment only, logs
  (`logs/`) monitored, backup + restore rehearsed, user acceptance test with real data.
* **Known gaps (not done):** schema versioning is `schema.sql` (idempotent create-if-missing) – adopt Flyway/Liquibase before your first schema change on live data; installers/code signing
  (use `jpackage` on each target OS); the VIEWER role has no seeded account (create one under Users); the Inter font is not bundled, so the UI falls back to Segoe UI / SF / system fonts.
