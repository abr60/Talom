# Talom

Local-first Android app. Reads whitelisted WhatsApp conversations and Google Classroom data, extracts structured items (classes, assignments, deadlines, announcements, reminders) via a configurable AI provider — Google Gemini, any OpenAI-compatible API (OpenRouter, Groq, Mistral, etc.), or a local Ollama server.

On-device only. No analytics or telemetry. API keys are stored in the Android Keystore. Message text is sent to a cloud provider only if you enable it and grant consent.

## Features

- **Academic tab** — classes, assignments, deadlines, exams grouped by time window.
- **Personal tab** — reminders and notes from the same stream.
- **Settings** — choose Cloud / Local AI provider, manage WhatsApp whitelist, connect Classroom.

## Requirements

- Android 13 (API 33)+
- For WhatsApp live pulls: rooted device, or import a WhatsApp `.txt` export
- For local AI: an Ollama server (default `http://localhost:11434`, configurable)

## Build

Requires JDK 17 and Android SDK (build-tools 37.0.0, platform 35).

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk

# Signed release (needs keystore in app/talom-release.keystore + ~/.gradle/gradle.properties)
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleRelease
```

Cleartext is allowed only for `localhost` / `127.0.0.1` / `10.0.2.2` (see `app/src/main/res/xml/network_security_config.xml`). Add your LAN host there if Ollama runs on another machine.

## Project layout

```
app/src/main/java/com/talom/
  MainActivity.kt        Compose entry + navigation
  core/ai/               AI provider abstraction (Gemini / OpenAI-compatible / Ollama)
  core/auth/             Google OAuth
  core/classroom/        Classroom mapping
  data/                  Room database + DAOs
  source/whatsapp/       WhatsApp snapshot / pull / export parsers
  ui/                    Academic / Personal / Settings / Components / Theme
```

## License

Apache-2.0. See `LICENSE`.
