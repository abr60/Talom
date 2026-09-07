# Talom

Local-first Android app for one Economics student at the University of Chittagong.

Talom reads whitelisted WhatsApp conversations, combines them with Google Classroom
data, and produces structured classes / assignments / deadlines / announcements
via a configurable AI provider (Google Gemini, OpenAI-compatible APIs like
OpenRouter / Groq / Mistral, or a local Ollama server).

Everything stays on-device. No analytics, no telemetry, no remote storage.
API keys are stored in the Android Keystore (AES/GCM). WhatsApp message text
is only sent to the AI provider if the user explicitly enables cloud processing
and grants consent.

## What it does

- **WhatsApp pull** — reads whitelisted messages from the WhatsApp SQLite
  database on rooted devices (or via a shared export), sends batches to the
  selected AI provider, and persists the extracted items.
- **Academic tab** — surfaces classes, assignments, deadlines, exams,
  announcements, and cancellations grouped by time window ("Next 36 hours",
  "This week") and by type. Stale data is pruned automatically after every
  pull.
- **Personal tab** — surfaces reminders, family/friend mentions, and general
  notes from the same conversation stream.
- **Settings** — three tabs:
  - **Cloud** — pick Google Gemini, or any OpenAI-compatible endpoint
    (OpenRouter, Groq, Mistral, Together, vLLM, llama.cpp, LM Studio, …).
    The app fetches the model list from the provider and auto-recommends a
    sensible default.
  - **Local** — point at a local Ollama server.
  - **WhatsApp** — manage whitelisted conversations, add/remove, categorize.

## Requirements

- Android 13 (API 33) or later
- For WhatsApp live pulls: a rooted device, or paste a WhatsApp `.txt` export
- For Google Classroom: an Android OAuth client + an installed
  `play-services-auth` build (included by default)

## Build

Requires JDK 17 and the Android SDK (build-tools 37.0.0, platform 35).

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
  ./gradlew :app:assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

### Release build (signed)

This repo ships with the signing config in `app/build.gradle.kts`. The
keystore and its password are **not** in the repo (see `.gitignore`). To
produce a signed release APK:

1. Generate a keystore:

   ```bash
   keytool -genkeypair -keystore app/talom-release.keystore \
     -alias talom -keyalg RSA -keysize 2048 -validity 10000 \
     -dname "CN=Talom, OU=Talom, O=Talom, L=, ST=, C=US"
   ```

2. Put the store + key password in `~/.gradle/gradle.properties`:

   ```properties
   TALOM_RELEASE_STORE_PASSWORD=...
   TALOM_RELEASE_KEY_PASSWORD=...
   TALOM_RELEASE_KEY_ALIAS=talom
   ```

3. Build:

   ```bash
   JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
     ./gradlew :app:assembleRelease
   ```

   Output: `app/build/outputs/apk/release/app-release.apk`

## Project layout

```
app/
  src/main/java/com/talom/
    MainActivity.kt              entry point + Compose navigation
    core/ai/                     AI provider abstraction + Gemini / OpenAI / Ollama
    core/auth/                   Google OAuth token provider
    core/classroom/              Google Classroom read-only provider
    data/                        Room database + DAOs
    source/whatsapp/             WhatsApp snapshot / pull / export parsers
    ui/academic/                 Academic tab
    ui/personal/                 Personal tab
    ui/settings/                 Settings hub + AI / WhatsApp / Classroom sub-pages
    ui/components/               Shared TalomCard, StatBar, StatusPanel, etc.
    ui/theme/                    Monochrome theme tokens
docs/                            Design notes + build plan
```

## License

Apache-2.0. See `LICENSE`.

## Disclaimer

Talom is a personal research project. It is not affiliated with WhatsApp,
Google, or any AI provider. The user is responsible for complying with
applicable terms of service when configuring an AI provider and granting
consent for cloud processing.
