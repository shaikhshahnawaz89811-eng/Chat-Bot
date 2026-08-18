# Brain — Offline AI Engine (Android)

100% offline, on-device AI chat app built around a local Qwen2.5-1.5B-Instruct
(GGUF Q4_K_M) model via llama.cpp, with a built-in OpenAI-compatible local API
server and an encrypted API-key system for the companion Rani app.

Full build plan, phase-by-phase status, and screen-by-screen mapping to the
original design are in **[PROGRESS.md](PROGRESS.md)** — always check that file
first.

## Build workflow (Termux → GitHub Actions → APK)

You never build the APK on your phone directly. Termux is only used to push
code; GitHub's servers do the actual compiling.

```bash
# one-time setup in Termux
pkg install git -y
git clone <your-repo-url>
cd Brain-Offline-AI-Engine

# every time you want a new APK
git add .
git commit -m "your message"
git push origin main
```

That push triggers `.github/workflows/build-apk.yml`. Go to your repo's
**Actions** tab → the latest run → **Artifacts** → download `brain-debug-apk`.

To get a proper GitHub **Release** (permanent download link) instead of a
build artifact:

```bash
git tag v1.0.0
git push --tags
```

## Project structure

```
app/src/main/java/com/brain/offlineai/
  ├─ MainActivity.kt              # nav drawer + bottom bar + NavHost shell
  ├─ navigation/Screen.kt         # every route in the app
  ├─ engine/                      # llama.cpp JNI bridge, model import, RAM monitor
  ├─ server/                      # loopback-only OpenAI-compatible local API server
  ├─ data/apikeys/                # SQLCipher-encrypted API key storage
  ├─ data/history/                # real chat-session persistence (Phase 7)
  ├─ data/attachments/            # real file/ZIP/image/video upload storage (Phase 10)
  ├─ data/artifacts/              # real generated-file/ZIP output + download (Phase 11)
  ├─ data/settings/, data/analytics/  # persisted app/model settings, usage counters
  ├─ ui/theme/                    # colors/type/theme pulled from the mockup
  ├─ ui/process/, ui/tasks/, ui/normalize/, ui/multimodal/, ui/recovery/
  │                               # marking system, multi-task splitter, input
  │                               # normalizer, attachment-role router, error
  │                               # recovery (Phases 8, 12, 13, 14, 15)
  ├─ ui/components/               # reusable pieces (drawer, bubbles, bars, cards)
  └─ ui/screens/                  # one folder per feature
```

## Status

All 15 phases in PROGRESS.md are complete - the full original mockup (14
screens), the local llama.cpp engine, the encrypted API-key system, the
loopback-only local API server, real chat history, and the full
Claude-style AI agent chat UI spec (markings/live process card, streaming
states, file/ZIP/image/video upload, artifact output + download, multi-task
handling, input normalization, multimodal attachment routing, and error &
recovery) are all wired end-to-end. See PROGRESS.md for the authoritative,
phase-by-phase detail, including every deliberate scope reduction and every
not-yet-wired marking, documented rather than hidden.

## Getting a model onto the phone (Phase 2)

No model ships inside the app (a usable GGUF is roughly ~1 GB - too big to
bundle, and you should choose what you run). To chat with Brain:

1. Download a `.gguf` file onto your phone - e.g. search Hugging Face for
   `Qwen2.5-1.5B-Instruct-GGUF` and grab the `Q4_K_M` version.
2. Open Brain → drawer/bottom-nav → **Models** → **Import .gguf model**.
3. Once imported, tap **Load model**. The drawer's AI Engine Status card
   will show real RAM usage and "Online & Ready" once it's loaded.
4. Go back to Chat and send a message - responses stream in token-by-token
   from the real local model.
