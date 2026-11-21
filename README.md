# CivicLens

**CivicLens** is a voice-first, AI-assisted civic grievance reporting and public transparency Android application built with **Kotlin** and **Jetpack Compose (Material 3)**. Citizens can report local civic issues using regional-language voice notes, photos, videos, or text; review AI-structured CPGRAMS complaints with explicit consent; and view anonymized, deduplicated hotspot analytics on a live public dashboard.

---

## Key Features

- **Multimodal & Regional-Language Intake:** Report issues via recorded audio, attached photos/videos, camera capture, or text in English, Hindi (`हिन्दी`), Tamil (`தமிழ்`), Telugu (`తెలుగు`), Kannada (`ಕನ್ನಡ`), or Bengali (`বাংলা`), with built-in Text-to-Speech readback.
- **Multi-Stage AI Triage Pipeline:**
  1. **Speech-to-Text & Vision Analysis** — Transcribes audio and inspects attached visual evidence via **Firebase AI Logic**.
  2. **LLM Planner & CPGRAMS Retrieval** — Maps grievances to official CPGRAMS ministries, department codes, target SLAs, and verifiable government portal links.
  3. **Risk Scorer & Confidence Check** — Computes a 0–100 public safety risk score and automatically flags drafts with `<75%` confidence for citizen/human review.
  4. **Duplicate Hotspot Clustering** — Matches nearby open reports in the same ward and geo-grid cell so citizens can join an existing cluster instead of creating duplicates.
  5. **Explicit Consent Gate** — Never submits government forms automatically; citizens review, edit, and check explicit consent before saving or exporting a complaint package.
- **Anonymized Public Transparency Dashboard:** Real-time KPI summary, anonymized ward hotspot bubble map, department breakdown bars, and cluster status filters with zero personal citizen PII exposed.
- **Responsive Across Devices:** Adapts automatically across phones (bottom navigation, single-column flow), foldables/tablets (side navigation rail, 2-column grids), and laptops/desktops (two-pane intake & review workstation, 6-column KPI row, optional hardware feature declarations).

---

## Privacy & Safety Guardrails

- **No Auto-Submission:** External portal packages are only exported or opened after explicit user review and consent.
- **PII Stripping on Public Data:** Public hotspot documents store only ward, coarse geo-cell, category, severity, and counts; personal details stay isolated in user-owned private records enforced by `firestore.rules`.
- **Probabilistic Media Observations:** Authenticity observations describe visual signals probabilistically rather than making absolute deepfake claims.
- **Source Citations Visible:** Department routing links directly to official government grievance portals.

---

## Tech Stack

- **UI:** Jetpack Compose, Material Design 3, adaptive `BoxWithConstraints` layouts, Coil
- **Architecture:** MVVM (`ViewModel`, `StateFlow`, `UiState`), Kotlin Coroutines & Flow
- **Backend & Security:** Firebase Authentication (Google Sign-In via Jetpack Credential Manager), Cloud Firestore (with strict security rules), Firebase App Check (Debug & reCAPTCHA), Firebase AI Logic
- **Media & Location:** `MediaRecorder`, `MediaMetadataRetriever`, `TextToSpeech`, Google Play Services Coarse Location (`FusedLocationProviderClient`), Android Photo Picker
- **Testing:** JUnit, Robolectric, and local Firestore Emulator security rules tests (`firestore.test.js`)

---

## Getting Started

### Prerequisites
- Android Studio (with JDK 11+ and Android SDK 36)
- A Firebase project configured with:
  - **Authentication** (Google provider enabled)
  - **Cloud Firestore**
  - **Firebase AI Logic** & **App Check**

### Build & Run
1. Clone the repository:
   ```bash
   git clone https://github.com/<your-username>/<your-repo>.git
   cd <your-repo>
   ```
2. Add your own `app/google-services.json` from Firebase Console (if cloning to a separate Firebase project) and deploy `firestore.rules`.
3. Build and run unit tests:
   ```bash
   ./gradlew :app:testDebugUnitTest
   ./gradlew :app:assembleDebug
   ```
4. Test Firestore security rules locally (requires Firebase CLI & Node.js):
   ```bash
   firebase emulators:exec --only firestore "node --test firestore.test.js"
   ```
