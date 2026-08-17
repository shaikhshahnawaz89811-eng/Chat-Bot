# Brain — Offline AI Engine — Build Progress

Single source-of-truth file for this whole project. Har phase isi file ko
**update** karta hai (naya content ADD/edit hota hai jahan status change hua
ho) — koi naya MD kabhi nahi banega, aur purana likha hua content delete
nahi hoga jab tak explicitly na bola jaaye (Document-Editing Convention,
rules PDF).

Original design reference (poori mockup, sab 14 screens is single image mein
hain):

![Brain app mockup](docs/mockup.png)

---

## Tech stack (confirmed, Rule 9 context — poore project mein yehi rahega)

| Component        | Tech                                              |
|-------------------|----------------------------------------------------|
| Platform          | Android (phone), minSdk 26 / targetSdk 34          |
| App logic         | Kotlin                                             |
| UI                | Jetpack Compose (Material3)                        |
| Build             | Gradle (Kotlin DSL)                                |
| CI / APK build    | GitHub Actions (`.github/workflows/build-apk.yml`) |
| Push workflow     | Termux → `git push` → GitHub Actions builds APK    |
| Local AI engine   | llama.cpp (JNI/NDK) - Phase 2 (DONE)               |
| Model             | Qwen2.5-1.5B-Instruct, GGUF Q4_K_M - user-imported |
| Secure storage    | SQLCipher (encrypted DB) - Phase 3 (DONE)          |
| Local API server  | OpenAI-compatible, loopback-only — Phase 4 (DONE)   |

---

## Phase map (mockup ke 14 screens → phases)

| # | Screen (from mockup)                          | Phase |
|---|------------------------------------------------|-------|
| 1 | Chat Interface (clean)                          | 1 ✅ |
| 2 | Thinking Process (live animation)               | 1 ✅ |
| 3 | Coding Process (live animation)                 | 1 ✅ |
| 4 | Generating Response (waveform animation)        | 1 ✅ |
| 5 | API Keys (list)                                 | 3 ✅ |
| 6 | Create API Key                                  | 3 ✅ |
| 7 | Key Details                                     | 3 ✅ |
| 8 | Key Options (view/copy/revoke/delete)           | 3 ✅ |
| 9 | Copy Key (success animation)                    | 3 ✅ |
| 10| Local API — Connection Status                   | 4 ✅ |
| 11| Model Settings                                  | 5 ✅ |
| 12| General Settings                                | 6 ✅ |
| 13| Storage                                         | 6 ✅ |
| 14| About                                           | 6 ✅ |
| — | Sidebar / drawer shell + AI Engine Status card  | 1 ✅ |
| — | Bottom nav (Chat/History/Models/Settings)       | 1 ✅ |
| — | Local AI Engine (llama.cpp + Qwen2.5-1.5B)      | 2 ✅ |
| — | Analytics screen                                | 5 ✅ |
| — | Footer highlight badges (Offline/Secure/etc.)   | 6 ✅ |

---

## Phase 1 — Foundation + GitHub Actions CI + Chat UI ✅ DONE (this build)

**What's real and working in this phase:**
- Full Gradle project (Kotlin DSL), namespace `com.brain.offlineai`.
- `.github/workflows/build-apk.yml` — pushes from Termux trigger a real
  GitHub Actions build. Uses `gradle/actions/setup-gradle` (not a
  committed `gradlew` binary jar, since generating that binary needed
  network access this environment doesn't have — documented honestly
  instead of faking the binary; see Rule 17).
- App shell: `ModalNavigationDrawer` (matches the left sidebar in the
  mockup — logo, nav items, live "AI Engine Status" card) + bottom nav bar
  (Chat / History / Models / Settings, matches phone-screen bottom bars).
- Color palette in `ui/theme/Color.kt` pulled directly from the mockup's
  hex values (dark navy background, violet primary, cyan/pink accents).
- **Chat screen (screens 1-4)** — one real state machine
  (`ChatViewModel`) drives all four visual states with real Compose
  animations (`animateFloat`, `infiniteRepeatable`, `animateFloatAsState`):
  1. Plain text bubbles (user + bot)
  2. Thinking checklist — items appear one at a time with a checkmark
  3. Coding block — code appears line-by-line, monospace, dark code panel
  4. Generating response — animated multi-bar waveform + real progress bar

**Explicitly NOT faked (documented, not hidden):**
- There is no local AI model in this build yet. Sending a message plays
  the real thinking/coding/generating animation sequence (all on real
  timers), then ends with a plain, clearly-labeled system note saying the
  engine isn't wired yet — instead of inventing a fake AI answer. The
  single call site Phase 2 will replace is marked with a `TODO` in
  `ChatViewModel.kt` (`BrainEngineBridge.generate(prompt)`).
- Every other drawer/bottom-nav destination (API Keys, Local API, Models,
  Analytics, Settings, About, History) routes to a `PlaceholderScreen`
  that plainly states which phase it arrives in. Nothing shows sample/fake
  data.

**Rules applied this phase:** 1 (endpoint exists for every route, even
placeholders), 4 (full chain: drawer/bottom-nav → NavHost → screen), 9
(platform/stack confirmed and recorded above), 10 (correctness — see
"validation status" below), 17 (endpoint must be correct, not just
present — hence the gradlew-jar decision), 18 (multi-component tech
recorded per-component), 20 (no unrelated payload/deps pulled in early),
21 (small single-purpose composables, no dead code).

**Validation status (Rule 10 — honest, not assumed):** This code has been
written and reviewed line-by-line for syntax and import correctness, but
**has not been compiled in a real Android/Gradle environment** by me —
this sandbox has no network access to download the Android SDK/Gradle
distribution. First real validation will happen automatically the moment
you `git push` (GitHub Actions will compile it for real). If the Actions
run fails, paste the error back and it'll be fixed immediately — that's
the actual first "clean validate" per Rule 10/19.

**Files in this phase:**
```
build.gradle.kts, settings.gradle.kts, gradle.properties
gradle/wrapper/gradle-wrapper.properties
app/build.gradle.kts, app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/values/{strings,colors,themes}.xml
app/src/main/res/xml/{data_extraction_rules,network_security_config}.xml
app/src/main/res/drawable/ic_launcher_{background,foreground}.xml
app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
app/src/main/java/com/brain/offlineai/MainActivity.kt
app/src/main/java/com/brain/offlineai/navigation/Screen.kt
app/src/main/java/com/brain/offlineai/ui/theme/{Color,Type,Theme}.kt
app/src/main/java/com/brain/offlineai/ui/components/
    ChatTopBar.kt, ChatBubbles.kt, ChatInputBar.kt, BottomNavBar.kt, AppDrawer.kt
app/src/main/java/com/brain/offlineai/ui/screens/PlaceholderScreen.kt
app/src/main/java/com/brain/offlineai/ui/screens/chat/
    ChatMessage.kt, ChatViewModel.kt, ChatScreen.kt
.github/workflows/build-apk.yml
docs/mockup.png, README.md, .gitignore
```

---

## Phase 2 — Local AI Engine (llama.cpp + Qwen2.5-1.5B) ✅ DONE (this build)

**What's real and working in this phase:**
- `app/src/main/cpp/CMakeLists.txt` pulls the real llama.cpp source from
  `github.com/ggml-org/llama.cpp` via CMake `FetchContent` at a pinned
  release tag (`b10453`, verify/bump before your first push if upstream
  has moved on) and builds it for real on the CI runner - it is not
  vendored/faked in this repo, same honest reasoning as the Phase 1
  gradlew decision (this sandbox has no network access to actually fetch
  or compile it here).
- `app/src/main/cpp/llama_bridge.cpp` - a real JNI bridge calling
  llama.cpp's actual public API: `llama_model_load_from_file`,
  `llama_init_from_model`, `llama_tokenize`, `llama_decode`, a real
  sampler chain (`llama_sampler_init_top_k/top_p/temp/dist`), and
  `llama_token_to_piece` streamed back to Kotlin one token at a time via a
  `TokenCallback` JNI callback.
- `BrainEngine.kt` / `BrainNative.kt` - Kotlin wrapper exposing a real
  `EngineState` (Unloaded/Loading/Loaded/Error) and a `generate()` Flow
  that emits real streamed token text, nothing scripted.
- `ModelFileManager.kt` - real Storage-Access-Framework import of a
  user-supplied `.gguf` file into app-private storage, with **real GGUF
  magic-byte validation** (0x47 0x47 0x55 0x46) before accepting the file
  - not a filename-extension check.
- `DeviceMemoryMonitor.kt` - real RAM numbers from
  `ActivityManager.getMemoryInfo()`, replacing the Phase 1 hardcoded
  "1.24 GB / 4.00 GB" placeholder in the drawer's AI Engine Status card.
- New **Models** screen (import / load / unload a real model, real
  per-byte import progress, delete). This is the load-a-model
  functionality Phase 2 needs to be usable at all - the fuller Model
  *Settings* screen (context length / temperature / top-p / thread-count
  sliders from mockup screen 11) is still Phase 5, unchanged from the
  original plan.
- `ChatViewModel.kt` rewritten: `sendMessage()` now calls
  `BrainEngine.generate(prompt)` and streams the real response into the
  chat bubble token-by-token. The Phase 1 scripted thinking-checklist and
  canned `is_prime` demo code are gone - that was explicitly a temporary
  placeholder in Phase 1's own notes, and this is the replacement.

**Explicitly NOT faked (documented, not hidden):**
- **No model ships bundled with the app.** Qwen2.5-1.5B-Instruct GGUF
  (Q4_K_M) is roughly ~1 GB - too large to vendor in this repo or this
  build environment (no network access here to download it), and also
  not something that should be silently embedded without you choosing
  it. You import your own GGUF file via the new Models screen (download
  it separately, e.g. from Hugging Face, onto your phone first).
- If you send a chat message with no model loaded, the app says exactly
  that and points you to the Models screen - it does **not** fall back to
  a scripted or canned answer.
- Any real failure (bad GGUF header, `llama_model_load_from_file`
  returning null, out-of-memory, a mid-generation decode error) is
  surfaced as a real error message in the UI, not swallowed or faked into
  a success state.

**Rules applied this phase:** 1 (Models route now has real content, not
just a placeholder), 9/18 (native toolchain recorded: NDK 26.3.11579264,
CMake 3.22.1, llama.cpp tag b10453), 17 (CI workflow explicitly installs
NDK+CMake rather than assuming they're present), 20 (arm64-v8a only for
now - x86_64 intentionally left out, documented in build.gradle.kts).

**Validation status (Rule 10 - honest, not assumed):** Same situation as
Phase 1 - this sandbox cannot compile Android/NDK/CMake code (no network,
no Android SDK/NDK installed here), so this has been written and
reviewed against llama.cpp's real, current public C API
(`include/llama.h` as of the pinned tag) but **not compiled**. First real
validation is the GitHub Actions run after your `git push`. The native
build will take noticeably longer than Phase 1's pure-Kotlin build (CMake
has to clone and compile llama.cpp itself) - that's expected, not a
hang. If the Actions run fails, paste the error back.

**Files added/changed this phase:**
```
app/src/main/cpp/CMakeLists.txt          (new)
app/src/main/cpp/llama_bridge.cpp        (new)
app/src/main/java/.../engine/BrainNative.kt        (new)
app/src/main/java/.../engine/BrainEngine.kt        (new)
app/src/main/java/.../engine/ModelFileManager.kt   (new)
app/src/main/java/.../engine/DeviceMemoryMonitor.kt (new)
app/src/main/java/.../ui/screens/models/ModelsScreen.kt    (new)
app/src/main/java/.../ui/screens/models/ModelsViewModel.kt (new)
app/src/main/java/.../ui/screens/chat/ChatViewModel.kt   (rewritten)
app/src/main/java/.../ui/components/ChatBubbles.kt       (BotGeneratingBubble rewritten)
app/src/main/java/.../ui/components/AppDrawer.kt         (AiEngineStatusCard wired to real state)
app/src/main/java/.../MainActivity.kt    (Models route -> real screen)
app/src/main/java/.../navigation/Screen.kt (comment updated)
app/build.gradle.kts                     (NDK/CMake config, versionCode 2)
.github/workflows/build-apk.yml          (installs NDK + CMake)
AndroidManifest.xml                      (largeHeap="true")
```

## Phase 3 — API Keys module ✅ DONE (this build)

**What's real and working in this phase:**
- `data/apikeys/ApiKeyDatabase.kt` — a real Room database opened through
  SQLCipher's `SupportFactory`, so `brain_api_keys.db` on disk is AES-256
  encrypted, not just app-private-storage-sandboxed. `DatabaseKeyProvider.kt`
  generates a real 256-bit SecureRandom passphrase once and holds it in
  `EncryptedSharedPreferences` (Android Keystore-backed AES256-GCM/SIV) —
  the passphrase itself is never hardcoded and never stored in plaintext.
- `KeyGenerator.kt` — real `SecureRandom`-generated key values (`brn_` +
  24 bytes hex), not placeholder strings.
- `ApiKeyRepository.kt` — the full Rule 3 name-ops set applied to key
  names: **create** (with name-uniqueness check), **rename** (same
  uniqueness check, excluding the row being renamed), **delete** (real row
  removal), **read** (`observeAll()` / `getById()`), and **active-pointer**
  (same pattern as `ModelFileManager`'s last-installed-model pointer —
  `ApiKeyRepository` persists which key id was most recently generated).
- **API Keys screen (screen 5)** — real list from `ApiKeysViewModel` ->
  repository -> encrypted DB via a Room `Flow`. Status badges
  (Active/Expired/Revoked) are computed live from `createdAt` /
  `expiresAt` / `revokedAt` (`ApiKeyEntity.statusAt()`) instead of a stored
  flag that could go stale.
- **Create API Key (screen 6)** — name field + expiration dropdown (Never
  / 7 / 30 / 90 days), real validation (empty name, duplicate name),
  generates a real key and navigates to Key Details.
- **Key Details (screen 7)** — full key info, masked-by-default key value
  with a real reveal/hide toggle, Copy Key button wired to a real
  `ClipboardManager` write.
- **Key Options (screen 8)** — View Details / Copy Key / Revoke Key /
  Delete Key, with real confirmation dialogs before Revoke and Delete
  (both are irreversible DB writes, not soft UI-only states).
- **Copy Key (screen 9)** — success animation shown only after a real
  clipboard write already happened in screen 7 or 8 (it confirms, it
  doesn't perform the copy itself).

**Explicitly NOT faked (documented, not hidden):**
- No key is ever auto-created or pre-seeded — an empty list shows a real
  empty state, not sample keys.
- Revoke/Delete are real, permanent DB writes gated behind an explicit
  confirmation dialog — nothing is silently reversible.
- The local API server that will actually *check* these keys against
  incoming requests is still Phase 4, unchanged from the original plan —
  this phase only builds real key issuance/storage/lifecycle management.

**Rules applied this phase:** 1 (all 4 new sub-routes are real, reachable
destinations, not orphans), 3 (full name-ops set on key names, see
above), 9/18 (Room 2.6.1 + net.zetetic sqlcipher-android 4.6.1 +
androidx.security 1.1.0-alpha06 recorded), 10 (status computed live, not
cached), 20 (only the 3 new dependency lines needed for this phase were
added to `app/build.gradle.kts` — nothing else pulled in early).

**Validation status (Rule 10 — honest, not assumed):** Same situation as
Phases 1-2 — written and reviewed against Room's and SQLCipher-for-Android's
real public APIs, but **not compiled** (no network/Android SDK in this
sandbox). First real validation is the GitHub Actions run after your
`git push`. If the Actions run fails, paste the error back.

**Files added/changed this phase:**
```
app/src/main/java/.../data/apikeys/ApiKeyEntity.kt        (new)
app/src/main/java/.../data/apikeys/ApiKeyDao.kt            (new)
app/src/main/java/.../data/apikeys/ApiKeyDatabase.kt       (new)
app/src/main/java/.../data/apikeys/DatabaseKeyProvider.kt  (new)
app/src/main/java/.../data/apikeys/ApiKeyRepository.kt     (new)
app/src/main/java/.../data/apikeys/ExpirationOption.kt     (new)
app/src/main/java/.../data/apikeys/KeyGenerator.kt         (new)
app/src/main/java/.../ui/screens/apikeys/ApiKeysViewModel.kt   (new)
app/src/main/java/.../ui/screens/apikeys/ApiKeyUiCommon.kt     (new)
app/src/main/java/.../ui/screens/apikeys/ApiKeysListScreen.kt  (new)
app/src/main/java/.../ui/screens/apikeys/CreateApiKeyScreen.kt (new)
app/src/main/java/.../ui/screens/apikeys/KeyDetailsScreen.kt   (new)
app/src/main/java/.../ui/screens/apikeys/KeyOptionsScreen.kt   (new)
app/src/main/java/.../ui/screens/apikeys/CopyKeyScreen.kt      (new)
app/src/main/java/.../navigation/Screen.kt   (4 new sub-routes added)
app/src/main/java/.../MainActivity.kt        (ApiKeys placeholder -> real nav graph)
app/build.gradle.kts  (ksp plugin + Room/SQLCipher/security-crypto deps)
```

## Phase 4 — Local API Server ✅ DONE (this build)

**What's real and working in this phase:**
- `server/LocalApiServer.kt` — a real `NanoHTTPD` subclass, constructed as
  `NanoHTTPD("127.0.0.1", PORT)` — bound to the loopback interface only,
  never `0.0.0.0`, so "100% Offline" is enforced at the socket level (on
  top of `network_security_config.xml`'s existing loopback-only cleartext
  rule from Phase 1). Implements two real OpenAI-compatible routes:
  - `GET /v1/models` — lists the model actually loaded in `BrainEngine`
    right now (an honest empty list if none is loaded, not a fake entry).
  - `POST /v1/chat/completions` — builds a real Qwen2.5 ChatML prompt from
    the request's `messages` array and runs it through the real
    `BrainEngine.generate()` decode loop (same engine Chat screen uses).
    Supports both a normal JSON response and `"stream": true` real
    token-by-token Server-Sent-Events streaming (a background thread feeds
    a `PipedOutputStream` as each token actually decodes — nothing is
    pre-generated and replayed).
- **Real API-key auth on every request** — `Authorization: Bearer <key>`
  is checked against the same SQLCipher-encrypted `api_keys` table Phase 3
  built (`ApiKeyRepository.getKeyByValue()`, a new additive DAO query), via
  the same live `statusAt()` check the API Keys UI uses — a key revoked or
  expired is rejected immediately, no caching of stale status. A
  successful call writes a real `lastUsedAt` timestamp, so a key's "Last
  Used" field on Key Details now reflects genuine Local API traffic.
- `server/LocalApiServerManager.kt` — single process-wide owner of the
  server instance and its live state (`Stopped` / `Running(port,
  startedAt)` / `Error(message)`), same one-owner-object shape as
  `BrainEngine`. Also owns the real, in-memory `requestsServed` counter
  (increments once per authenticated, successfully-routed request).
- `server/LocalApiForegroundService.kt` — a real Android foreground
  `Service` (matches the mockup's "Works in Background" highlight badge).
  Starts/stops `LocalApiServerManager`, posts the required ongoing
  notification (real `NotificationChannel` + `NotificationCompat`), and
  honestly stops itself (not a fake "still running" state) if the server
  fails to bind — e.g. port 11434 already in use by something else.
- **Connection Status screen (screen 10)** — `LocalApiScreen.kt` +
  `LocalApiViewModel.kt`. Every field is real: Status badge from
  `ServerState`, Endpoint (`http://127.0.0.1:11434/v1`, tap-to-copy via
  the real `ClipboardManager` write already used by API Keys), API
  Version, a live-ticking Uptime computed from the real `startedAtMillis`
  (not animated), and Requests Served from the real counter. Start
  Server/Stop Server actually start/stop the real foreground service; on
  Android 13+ it requests the `POST_NOTIFICATIONS` runtime permission
  first (and starts the server either way — the service still runs
  without the notification being visible, per platform behavior).

**Explicitly NOT faked (documented, not hidden):**
- If no model is loaded, `POST /v1/chat/completions` returns a real `503
  model_not_loaded` JSON error telling the caller to load one from the
  Models screen — it never fabricates a reply.
- The non-streaming chat-completion response omits the OpenAI `usage`
  (token-count) field on purpose: a real count needs a native tokenizer
  call this JNI bridge doesn't expose to Kotlin yet, and inventing an
  approximate number and presenting it as real would violate this
  project's own honesty rule. The field is optional per the OpenAI spec,
  so compatibility isn't broken by leaving it out.
- The notification's icon is a stock platform drawable
  (`android.R.drawable.stat_sys_download_done`), not a custom Brain vector
  — called out in-code as a small polish item for a later phase, not
  something silently passed off as final art.

**Rules applied this phase:** 1 (both new routes are real, reachable
endpoints — no orphan handlers), 3 (key lookup extends the existing
name-ops set with a real by-value lookup, additive only — nothing in
`ApiKeyDao`/`ApiKeyRepository` from Phase 3 was changed or removed), 9/18
(NanoHTTPD 2.3.1 recorded as the new dependency; org.json used for
request/response bodies since it already ships with Android — no
unrelated JSON library pulled in), 10 (auth status and uptime are both
computed live, never cached/stale), 17 (loopback binding is the actual
enforcement mechanism, not just a claim in the UI), 20 (only the one new
Gradle dependency this phase needs was added).

**Validation status (Rule 10 — honest, not assumed):** Same situation as
Phases 1–3 — written and reviewed line-by-line against NanoHTTPD's real
public API (`fi.iki.elonen.NanoHTTPD`, `Response`, `IHTTPSession`) and
Android's real `Service`/`NotificationCompat` APIs, including a pass to
qualify NanoHTTPD's static response-builder calls (`NanoHTTPD.newFixedLengthResponse`,
`NanoHTTPD.newChunkedResponse`) to avoid any Kotlin/Java static-resolution
ambiguity — but **not compiled** (no network/Android SDK in this sandbox).
First real validation is the GitHub Actions run after your `git push`. If
the Actions run fails, paste the error back and it'll be fixed
immediately.

**Files added/changed this phase:**
```
app/src/main/java/.../server/LocalApiServer.kt           (new)
app/src/main/java/.../server/LocalApiServerManager.kt     (new)
app/src/main/java/.../server/LocalApiForegroundService.kt (new)
app/src/main/java/.../ui/screens/localapi/LocalApiViewModel.kt (new)
app/src/main/java/.../ui/screens/localapi/LocalApiScreen.kt    (new)
app/src/main/java/.../data/apikeys/ApiKeyDao.kt        (getByKeyValue query added)
app/src/main/java/.../data/apikeys/ApiKeyRepository.kt (getKeyByValue added)
app/src/main/java/.../MainActivity.kt      (Local API placeholder -> real screen)
app/src/main/java/.../navigation/Screen.kt (comment updated)
app/build.gradle.kts       (nanohttpd dependency, versionCode 4)
AndroidManifest.xml        (INTERNET, FOREGROUND_SERVICE(+DATA_SYNC), POST_NOTIFICATIONS, service entry)
```

## Phase 5 — Models + Model Settings + Analytics ✅ DONE (this build)

**What's real and working in this phase:**
- `data/settings/ModelSettingsRepository.kt` — real, persisted inference
  settings (context length, temperature, top-p, thread count). Through
  Phase 4 these were hardcoded defaults baked into `BrainEngine.loadModel()`
  (nCtx=2048, nThreads=4) and `BrainEngine.generate()` (temperature=0.7,
  topP=0.9); there was no screen that changed them. This repository is now
  the single source both reads from.
- **Model Settings screen (screen 11)** — `ModelSettingsScreen.kt` +
  `ModelSettingsViewModel.kt`. Four real sliders (context length 512-8192,
  temperature 0.1-2.0, top-p 0.1-1.0, threads 1-`Runtime.availableProcessors()`
  — the real device core count, not a guessed cap), each writing straight
  to `ModelSettingsRepository`. Reached from a new gear icon on the Models
  screen (Rule 1 — real, reachable endpoint, not just a route string).
- **Correctness (Rule 17) for context length/threads**: these are
  `llama_context` construction parameters (see `llama_bridge.cpp`
  `nativeLoadModel`) — they can't change on an already-running context.
  If a model is currently loaded when changed, the screen shows a real
  "Apply & Reload Now" button that unloads + reloads the actual engine
  with the new values instead of silently doing nothing. Temperature/top-p
  need no reload — `ChatViewModel` now reads `ModelSettingsRepository`
  fresh on every send, so they apply to the very next message automatically.
- `ModelsViewModel.loadModel()` now passes the real saved context
  length/threads to `BrainEngine.loadModel()` instead of the old hardcoded
  defaults.
- `data/analytics/AnalyticsStore.kt` — real, persisted usage counters:
  total messages sent, total tokens generated, total Local API requests
  (cumulative across server restarts), first-launch timestamp. Every
  number increments from a genuine real event — `ChatViewModel.sendMessage()`
  / a completed real generation's actual final token count /
  `LocalApiServerManager`'s existing real per-request callback (additive
  write alongside its existing live per-session counter from Phase 4,
  which is unchanged) — never seeded or sample data.
- **Analytics screen** — `AnalyticsScreen.kt` + `AnalyticsViewModel.kt`,
  replacing the Phase 1-4 placeholder route. Shows the real counters above
  plus live real engine status, live real Local API server status, and
  live real RAM usage (reusing `DeviceMemoryMonitor` from Phase 2) — no
  chart library, no seeded dataset, per this phase's own scope note above.

**Explicitly NOT faked (documented, not hidden):**
- A freshly-installed app's Analytics screen shows all real zeros, not
  placeholder sample numbers.
- Context length/thread changes are never silently claimed as "applied" —
  the screen only says a reload is needed, and only actually reloads when
  the real "Apply & Reload Now" button is tapped and a model is genuinely
  loaded.

**Rules applied this phase:** 1 (Model Settings route is real and
reachable, not orphaned), 2/15 (`ModelSettingsViewModel.applySettingsToRunningModel()`
kept as its own small function rather than folded into `ModelsViewModel`),
9/18 (same Kotlin/Compose/Android stack, no new dependency needed —
`Slider` already ships with the existing material3 BOM), 10 (settings are
coerced into their real valid ranges before being persisted), 12 (input/
timing/output workflow thought through before writing the reload button —
reload only runs if a model is genuinely loaded), 14 (reload failures are
surfaced on the Model Settings screen itself, not only back on Models),
17 (context-length/thread "endpoint" is correct, not just present - see
Apply & Reload above), 20 (AnalyticsStore writes once per completed
generation with the real final token count, not once per streamed token).

**Validation status (Rule 10 — honest, not assumed):** Same situation as
Phases 1-4 — written and reviewed line-by-line, and every edited/added
file was checked for balanced braces/parens, but **not compiled** (no
network/Android SDK in this sandbox). First real validation is the
now-restored GitHub Actions run after your `git push`.

**Files added/changed this phase:**
```
app/src/main/java/.../data/settings/ModelSettingsRepository.kt   (new)
app/src/main/java/.../data/analytics/AnalyticsStore.kt           (new)
app/src/main/java/.../ui/screens/modelsettings/ModelSettingsScreen.kt    (new)
app/src/main/java/.../ui/screens/modelsettings/ModelSettingsViewModel.kt (new)
app/src/main/java/.../ui/screens/analytics/AnalyticsScreen.kt    (new)
app/src/main/java/.../ui/screens/analytics/AnalyticsViewModel.kt (new)
app/src/main/java/.../ui/screens/models/ModelsScreen.kt      (settings gear icon + onOpenSettings added)
app/src/main/java/.../ui/screens/models/ModelsViewModel.kt   (loadModel uses real saved settings)
app/src/main/java/.../ui/screens/chat/ChatViewModel.kt       (AndroidViewModel; real temperature/top-p + analytics wiring)
app/src/main/java/.../server/LocalApiServerManager.kt        (additive AnalyticsStore write in start())
app/src/main/java/.../navigation/Screen.kt        (ModelSettings route added)
app/src/main/java/.../MainActivity.kt             (ModelSettings + real Analytics routes wired)
app/build.gradle.kts       (versionCode 5)
```

## Phase 6 — General Settings + Storage + About ✅ DONE (this build)

**What's real and working in this phase:**
- `data/settings/AppSettingsRepository.kt` — real, persisted General
  Settings (dark theme on/off, chat animations on/off, Local API
  auto-start on/off). Same SharedPreferences tier as
  `ModelSettingsRepository`/`AnalyticsStore` (Rule 20 — these are UI/behavior
  preferences, not secrets).
- **Real theme toggle, not a decorative switch (Rule 17).**
  `ui/theme/Color.kt`'s surface/text tokens (`BrainBgPrimary`,
  `BrainBgCard`, `BrainBgCardAlt`, `BrainBorder`, `BrainTextPrimary`,
  `BrainTextSecondary`, `BrainTextMuted`) are now real Compose `State`
  (`by mutableStateOf(...)`) instead of plain `val`s, with a genuine
  second (light) palette alongside the original dark one. `applyBrainTheme(dark:
  Boolean)` flips all seven at once. Every screen from every earlier phase
  already reads these by name (`import com.brain.offlineai.ui.theme.*`)
  with no call-site changes needed — because the reads are now tracked by
  Compose, toggling the switch on the new General Settings screen
  repaints every already-open screen immediately, app-wide. `Theme.kt`
  now builds its `ColorScheme` inside the `@Composable` function (was a
  top-level `val` before) so it recomposes along with the tokens.
- `data/settings/AppSettingsState.kt` — single process-wide holder (same
  one-owner-object shape as `BrainEngine`/`LocalApiServerManager`) that
  seeds from `AppSettingsRepository` once at launch (`MainActivity.onCreate`,
  before `setContent`) and mirrors the live `animationsEnabled` value into
  reactive state.
- **Real animation toggle.** `ui/components/ChatBubbles.kt`'s
  `WaveformAnimation`, `PulsingDot`, and `TypingDots` each now check
  `AppSettingsState.animationsEnabled` and render a genuinely different,
  static (non-animated) version when it's off — not the same animation
  left silently running under a switch that does nothing.
- **General Settings screen (screen 12)** —
  `ui/screens/settings/GeneralSettingsScreen.kt` +
  `GeneralSettingsViewModel.kt`. Dark Theme / Chat Animations / Local API
  Auto-Start switches, each wired straight to the mechanisms above, plus
  real navigation rows into Storage (screen 13) and About (already an
  existing drawer destination — not duplicated here). Replaces the
  Phase 1-5 Placeholder route (Rule 1 — the route already existed and was
  reachable; it now has real content).
- **Storage screen (screen 13)** —
  `ui/screens/storage/StorageScreen.kt` + `StorageViewModel.kt`. Every
  number is computed fresh when the screen opens: real recursive
  `File.length()` totals for the imported-models directory and the
  SharedPreferences directory, the real on-disk size of the Phase 3
  SQLCipher-encrypted `brain_api_keys.db` file
  (`Context.getDatabasePath(...)`), and real device free/total internal
  storage via `StatFs` — the same API Android's own Settings > Storage
  screen is built on. "Clear Cache" is a real, safe deletion of
  `Context.cacheDir`'s contents (documented Android-reclaimable storage,
  not app data) and immediately re-measures afterward; it deliberately
  does **not** duplicate ModelFileManager's model-delete logic — that
  stays the Models screen's one real endpoint for it (Rule 3 — one real
  action, one owner), this screen just points there in text.
- **About screen (screen 14)** — `ui/screens/about/AboutScreen.kt`. App
  version/build number read from the real `PackageManager.getPackageInfo()`
  at render time (not a hardcoded string that would go stale next
  release), plus real device info (`Build.VERSION.RELEASE`,
  `Build.MODEL`, `Runtime.getRuntime().availableProcessors()`). Replaces
  the Phase 1-5 Placeholder route.
- **Footer highlight badges** — `ui/components/FooterBadges.kt`, a small
  reusable row shown at the bottom of the General Settings and About
  screens. Every badge restates a capability that's already real and
  load-bearing elsewhere in this codebase (loopback-only network config
  from Phase 1/4, SQLCipher encryption from Phase 3, per-request API-key
  auth from Phase 4, the real foreground service from Phase 4) — nothing
  new is claimed here that isn't already true.
- `MainActivity.kt` — seeds `AppSettingsState` before `setContent`;
  starts the real `LocalApiForegroundService` once at launch if the new
  Auto-Start switch is on (same start path `LocalApiViewModel.startServer()`
  already uses); wires the new Settings/Storage/About routes.

**Explicitly NOT faked (documented, not hidden):**
- A freshly-installed app's Storage screen shows real, small numbers (an
  empty/near-empty models directory, a just-created encrypted DB), not
  placeholder sample sizes.
- The theme switch changes actual rendered colors app-wide, verifiable by
  toggling it and watching every open screen repaint — not just a switch
  whose state is stored and ignored.
- "Auto-Start on Launch" only starts the real service; it doesn't fake a
  "Running" status if the real bind fails for some other reason (e.g.
  port already in use) — `LocalApiForegroundService` already handles that
  honestly (stops itself, see Phase 4 notes) and that behavior is
  unchanged here.

**Rules applied this phase:** 1 (Settings/Storage/About routes are real
and reachable, not orphaned — Storage is a new sub-destination reached
only from Settings, same pattern as ModelSettings from Models), 3
(Storage's Clear Cache is additive and doesn't duplicate or shadow
Models' existing Delete-model action), 9/18 (no new dependency needed —
`Switch`, `StatFs`, and `PackageManager` all ship with the existing
Compose Material3 BOM / Android SDK), 10 (every Storage/About number is
computed live, never cached/stale), 17 (theme and animation toggles have
a verifiable, traceable code path to a real visual change — see above,
not just a persisted boolean nobody reads), 20 (no unrelated dependency
or API surface pulled in — e.g. `FooterBadges` avoids an experimental
FlowRow API for a fixed 4-badge layout).

**Validation status (Rule 10 — honest, not assumed):** Same situation as
Phases 1-5 — written and reviewed line-by-line, and every added/edited
file was checked for balanced braces/parens, but **not compiled** (no
network/Android SDK in this sandbox). First real validation is the
GitHub Actions run after your `git push`. If the Actions run fails,
paste the error back and it'll be fixed immediately.

**Files added/changed this phase:**
```
app/src/main/java/.../data/settings/AppSettingsRepository.kt   (new)
app/src/main/java/.../data/settings/AppSettingsState.kt        (new)
app/src/main/java/.../ui/screens/settings/GeneralSettingsScreen.kt    (new)
app/src/main/java/.../ui/screens/settings/GeneralSettingsViewModel.kt (new)
app/src/main/java/.../ui/screens/storage/StorageScreen.kt      (new)
app/src/main/java/.../ui/screens/storage/StorageViewModel.kt   (new)
app/src/main/java/.../ui/screens/about/AboutScreen.kt          (new)
app/src/main/java/.../ui/components/FooterBadges.kt            (new)
app/src/main/java/.../ui/theme/Color.kt      (surface/text tokens made reactive; real light palette added)
app/src/main/java/.../ui/theme/Theme.kt      (color scheme now built inside the composable, dark/light aware)
app/src/main/java/.../ui/components/ChatBubbles.kt  (WaveformAnimation/PulsingDot/TypingDots gated on AppSettingsState.animationsEnabled)
app/src/main/java/.../MainActivity.kt        (AppSettingsState.init + auto-start wiring; Settings/Storage/About routed to real screens)
app/src/main/java/.../navigation/Screen.kt   (Storage sub-route added; Settings/About comments updated)
app/build.gradle.kts                         (versionCode 6)
```

## Phase 7 — Final integration + polish ✅ DONE (this build)

**What this phase actually did (Rule 16 - full function-by-function audit,
not just the two items originally scoped):**

1. **Real gap found and fixed (Rule 1/17, the main finding of this pass).**
   The "History" bottom-nav destination (wired since Phase 1) was still
   `PlaceholderScreen("History", arrivingInPhase = 2)` - but History was
   never actually one of the 14 mockup screens claimed in any phase's own
   scope, so that text had gone stale/false across five completed phases
   (it named a phase that shipped without ever touching History). Rather
   than just editing the placeholder's wording, this phase gives it real
   content:
   - `data/history/` (new) - `ChatSessionEntity`/`ChatHistoryMessageEntity`
     (Room, deliberately plain/unencrypted - Rule 20, a chat transcript
     isn't the kind of live credential the SQLCipher-wrapped API-keys DB
     protects), `ChatHistoryDao`, `ChatHistoryDatabase`,
     `ChatHistoryRepository` - same singleton/repository shape as
     `data/apikeys/`.
   - `ChatViewModel.kt` - every real user message and every real
     completed/streamed bot message is now persisted as it happens (not
     batched at the end), via a real Room upsert keyed on the message id -
     a kill mid-stream loses at most the in-flight partial token buffer,
     not the whole exchange. A session is created lazily on the first real
     message sent (a fresh Chat tab that's never typed in still writes
     zero rows - same "no data until something real happens" rule
     Analytics already followed). Added a `ViewModelProvider.Factory` so
     the same class can either start a fresh conversation (default,
     unchanged bottom-nav behavior) or reopen a persisted one by id.
   - **History screen** (new, real) - `ui/screens/history/` lists every
     real session from `ChatHistoryRepository.observeSessions()` (Room
     `Flow`, same live-list pattern `ApiKeysListScreen` already uses),
     newest first, with a real permanent-delete action gated behind the
     same confirm-dialog pattern used for Delete Key / Delete Model.
     Tapping a row reopens the real transcript in `ChatScreen` via a new
     `chat_session/{sessionId}` sub-route (`Screen.kt`), same
     sub-destination pattern as `ModelSettings`/`Storage`.
   - `MainActivity.kt` - History's placeholder `composable` replaced with
     the real screen + the new `chat_session/{sessionId}` route; the now-
     unused `PlaceholderScreen` import removed (the component itself is
     untouched/undeleted - Document-Editing Convention - it's still a
     legitimate reusable piece if a genuinely new route is ever added
     ahead of its content).

2. **Real background-service-stability fix (the other originally-scoped
   item).** `LocalApiForegroundService` had no handling for Android 14's
   (API 34) real ~6-hour cumulative daily cap on `dataSync` foreground
   services - previously the OS would eventually force-kill the process
   with a `ForegroundServiceDidNotStopInTimeException` instead of a clean
   shutdown. Added `onTimeout(startId, fgsType)` - a real, platform-
   provided `Service` callback added in API 34, not a fake/invented
   method - which stops the real server and the service the same clean
   way `ACTION_STOP` already does, so `LocalApiServerManager.state`
   genuinely reflects `Stopped` instead of looking `Running` while the
   process is torn down underneath it. No-op (never invoked) on the
   minSdk 26 - API 33 range, same as any other newer-OS-only override.

3. **Rule 16 audit of the rest of the codebase (Phases 1-6 files not
   already covered by the Phase 1-4 audit pass below).** Checked every
   `.kt` file for balanced braces/parens, orphaned/unreachable functions,
   stale comments, and every nav route's real reachability. Everything in
   `engine/`, `server/` (aside from item 2), `data/apikeys/`,
   `data/settings/`, `data/analytics/`, and every `ui/screens/*` folder
   from Phases 2-6 checked out clean - no further gaps found.

4. **Version bump + final README pass.** `app/build.gradle.kts`
   `versionCode` 6 → 7, `versionName` dropped its `-phase6` suffix to
   `1.0.0` (this is the final integration phase, not an interim one).
   `README.md`'s project-structure tree and Status section updated to
   reflect the real, now-complete feature set (additive edit, nothing
   removed).

**Explicitly NOT faked (documented, not hidden):**
- History's empty state is real (a fresh install/fresh chat shows zero
  sessions, not sample conversations) - same honesty rule Analytics
  already followed in Phase 5.
- `onTimeout` genuinely stops the real server/service; it doesn't just log
  or swallow the platform callback.
- No file was deleted and no existing function's behavior was changed
  outside what's listed above (Document-Editing Convention) - the History
  route's *content* changed (placeholder → real screen), its *route
  string* (`"history"`) did not, so nothing that already linked to it
  broke.

**Rules applied this phase:** 1 (History + the new ChatSession sub-route
are real, reachable, non-orphaned endpoints), 3 (History's delete is a
real permanent removal behind a confirm dialog, doesn't duplicate any
existing delete action), 9/18 (no new Gradle dependency needed - Room/ksp
were already pulled in by Phase 3, reused here for a second, separate,
unencrypted database rather than overloading the SQLCipher one), 10
(session list and message content are read live from Room via `Flow`,
never cached/stale), 16 (full function-by-function audit as scoped),
17 (the "History" endpoint is now actually correct, not just present -
same standard already applied to the theme/animation toggles in Phase 6),
20 (History DB deliberately left unencrypted rather than reusing
SQLCipher - Rule 20 minimal-necessary, a transcript isn't the same class
of secret as an API key), 21 (small, single-purpose new files - entities/
dao/database/repository split the same way `data/apikeys/` already is).

**Validation status (Rule 10 - honest, not assumed):** Same situation as
every phase before it - written and reviewed line-by-line, every new/
edited file rechecked for balanced braces/parens across the whole
project (not just this phase's files), but **not compiled** (no network/
Android SDK/NDK in this sandbox, same constraint documented in every
phase above). First real validation is the GitHub Actions run after your
`git push`. If the Actions run fails, paste the error back and it'll be
fixed immediately - same offer as every phase before this one.

**Files added/changed this phase:**
```
app/src/main/java/.../data/history/ChatHistoryEntities.kt    (new)
app/src/main/java/.../data/history/ChatHistoryDao.kt          (new)
app/src/main/java/.../data/history/ChatHistoryDatabase.kt     (new)
app/src/main/java/.../data/history/ChatHistoryRepository.kt   (new)
app/src/main/java/.../ui/screens/history/HistoryScreen.kt     (new)
app/src/main/java/.../ui/screens/history/HistoryViewModel.kt  (new)
app/src/main/java/.../ui/screens/chat/ChatViewModel.kt   (real persistence + session-reopen Factory added)
app/src/main/java/.../ui/screens/chat/ChatScreen.kt      (optional openSessionId param)
app/src/main/java/.../navigation/Screen.kt               (ChatSession sub-route added)
app/src/main/java/.../MainActivity.kt        (History placeholder -> real screen + ChatSession route)
app/src/main/java/.../server/LocalApiForegroundService.kt   (onTimeout override added)
app/build.gradle.kts       (versionCode 7, versionName 1.0.0)
README.md                  (project structure + status updated)
```

---

## How to continue from here
Just say "next phase" / "phase 2 karo" any time. Each phase's zip is
cumulative — it always contains everything from every previous phase plus
the new work, and this file gets updated (not replaced) each time.

---

## Phase 1-4 Rule-Audit Pass (append-only, per Document-Editing Convention — nothing above changed)

A full Rule 1-21 pass was run over every file shipped through Phase 4
(engine/, data/apikeys/, server/, ui/, cpp/, gradle/manifest). Most of the
codebase checked out clean (endpoints wired, name-ops complete, no orphan
functions, honest validation-status notes already in place). Three real
gaps were found and fixed — all additive/safe (Rule 7 risk-check clean, no
existing behavior changed):

1. **Missing CI endpoint (Rule 1/4, high priority).** `.github/workflows/build-apk.yml`
   was documented in this file (every phase's "Files added/changed" list)
   as the real Termux → git push → GitHub Actions → APK entry point, but
   was not actually present in the Phase 4 zip — the whole push-to-build
   chain was broken end to end. Recreated from this document's own spec
   (JDK 17, Android SDK, NDK 26.3.11579264 + CMake 3.22.1,
   `gradle/actions/setup-gradle`, `assembleDebug`). Verify this builds on
   your first real push — this is still this project's actual first clean
   validation per Rule 10.
2. **Model delete had no confirmation (Rule 3).** `ModelsScreen.kt`'s
   Delete button called `deleteModel()` directly — a real, irreversible
   on-disk file removal with no confirm step, unlike API Keys' Delete Key
   (which already has one). Added the same confirm-dialog pattern.
3. **`ChatTopBar` showed a hardcoded "Online" status (Rule 10/17).** The
   green dot + "Online" text never changed regardless of real engine
   state — the same kind of fake status text Phase 2 already fixed once
   for the drawer's `AiEngineStatusCard`. Now reads real `BrainEngine.state`
   (Online / Loading… / Error / No model), same source of truth the drawer
   card and Models screen use.

No functions were deleted, renamed, or had their existing behavior
changed. No files outside the three above were modified.
