# jowiAIandroid — Jowi (Workis) Android app

Native Android port of the SwiftUI app at `~/Documents/swiftUIProjects/jowiAIs`,
talking to the same Django API (`~/Documents/pythonProjects/jowiAI`).
Read those repos' docs before changing API or brand code:
`jowiAIs/docs/API_CONTRACT.md`, `jowiAI/docs/WORKIS_BRAND_TOKENS.md`, `WORKIS_ARCHITECTURE.md`.

**Parity ledger: `jowiAIs/docs/ANDROID_PARITY.md`.** Every iOS change Android must
mirror lands there as a dated entry (iOS commit · what · OPEN/DONE/SKIP). Closing an
entry = write the Android commit hash next to it. Section 1 carries the standing rules
(markdown keys are rendered, navigation/selection grammar, cell-exit autosave + green
tick, status never color-only). API shapes are never repeated there — API_CONTRACT.md.

## Stack

- Kotlin (built-in via AGP 9.3.x — no `org.jetbrains.kotlin.android` plugin), Jetpack Compose (BOM), Material 3
- Gradle 9.7.1 wrapper; build from CLI with:
  `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
- Package / applicationId: `ai.workis.jowi`. minSdk 26, compileSdk 37, targetSdk 36.
- In place: Retrofit + OkHttp + kotlinx.serialization, EncryptedSharedPreferences (token),
  BiometricPrompt gate on launch (ui/BiometricGate.kt — skips when no lock/biometric enrolled),
  per-app locale via AppCompat, prefs in SharedPreferences (`workis_lang`, `workis_appearance`).
- Chrome Custom Tabs via `openInApp()` (ui/components/Markdown.kt) — every `workis.ai` link
  in consent texts opens in-app; catalog markdown (`**bold**`, `[label](url)`) renders
  through `MarkdownText`. Still planned: push seam (FCM not implemented server-side).
- Agreement paper (`ui/components/AgreementPaper.kt`) is shared by wizard step 04 and the
  expert screen; its text comes from `GET /workis/agreement/?role=|key=` and the PDF renders
  natively (android.graphics.pdf.PdfRenderer, `PdfPages`). Never hard-code a PDF path.
- Jowi tab (`ui/screens/JowiScreen.kt`) is the SERVER's thread (`GET ask/history/`, shared with the
  web drawer; no device storage): `ask/preview/` before every send (10 s, fallback = typed text),
  policy-B stamp + 👍/👎 via `ask/like/`, match card, "Aç" links in-app. OkHttp read timeout is 90 s
  because answers are model calls.
- Server Markdown (expert guide) renders through `MarkdownDocument` (ui/components/) — the dialect in
  API_CONTRACT (##/###, > quote box, GFM tables, lists, `code` chips). Never re-author guide text.
- Knowledge (Bilgi) is ONE screen behind a seat: `ExpertKnowledgeScreen(seat = Expert | Console)` with
  `KnowledgeViewModel` (expert/* vs console/* doors; console adds semantic search, author tags, pairs).
- Expert slice 2: `ExpertEarnings.kt` (door = the Panel's "Bu ay" card) and `ExpertAccountSections.kt`
  (Hesap sections for role 5 + departure/rejoin). Money stays decimal-as-string end to end.
- Every "it's done" state is `OutcomeView` (glyph + mono title + one paragraph + one kiremit action).
- Cell-exit autosave rows: `FormRow` (ui/components/FormRows.kt) — `onCommit` fires when
  focus leaves; expert form saves a prefs draft, application detail POSTs one field per call.

## API rules (from API_CONTRACT.md — the mobile side never edits Django serializers)

- Base: `https://workis.ai/api/v1/` · header `Authorization: Token <key>` (NOT Bearer), `Accept-Language: tr|en` on every request.
- Responses are camelCase; money is decimal-as-string → `BigDecimal`, never Double.
- Decode tolerantly: nullable fields + `ignoreUnknownKeys = true` (backend lights fields up after client ships).
- 401 → wipe token, back to login. 403 → routing signal (missing seat profile → onboarding), not an error screen.
- No pagination on `/workis/` lists, no WebSockets (poll/pull-to-refresh), push not implemented yet.
- Login is e-mail → OTP (`/auth/otp/request|verify/` — contract proposed, backend gap). `POST /auth/validate-token/` on launch.
- Roles are exact-match ints: 1 Partner, 2 Guest, 3 Coordinator, 4 Region lead, 5 Expert. Coordinator console
  only for role 3 or 4; role 5 gets its own Panel (`ui/screens/expert/`, `/workis/expert/*`) and no Cases tab.
- §10.2 re-acceptance: `GET /workis/agreement/pending/` after sign-in (AuthRepository.agreementPending) → the
  paper opens once per launch (`AgreementReacceptScreen`); Hesap keeps a reminder row until accepted.
- Live console endpoints emit ids/vkn as NUMBERS ("id":121) although the contract reads string — decode
  id-like fields with `FlexString` (data/ConsoleModels.kt), never plain String.

## Strings / i18n — single source: the iOS String Catalog

`jowiAIs/Shared/Localizable.xcstrings` is the ONE translation source for both apps.
`python3 tools/generate_strings.py` regenerates `res/values*/strings_catalog.xml`
(camelCase keys → snake_case: signInTitle → R.string.sign_in_title) and
`res/xml/locales_config.xml`. Run it after any catalog change; never edit the
generated files. New language = new locale in the catalog + rerun + add it to the
language pickers (LoginScreen tabs, AccountScreen) + Django `settings.LANGUAGES`.
Hand-kept `strings.xml` holds Android-only keys (app_name, bio_*, lang names…) —
never duplicate a catalog key there.

## Brand (canonical source: iOS `WorkisTheme.swift`; mirrored in `ui/theme/`)

- Kiremit-400 `#E8703F` = primary button fill only; kiremit-500 `#D95F2E` = slash/icons.
  One tinted primary action per screen; success green is status, never action; no pure black/white.
- IBM Plex bundled in `res/font` (Sans variable + Mono statics). Mono only for logo/digits/eyebrows; Sans for reading text.
- Loading idioms: inline button spinner / breathing slash (`WorkisMark(breathing = true)`) / typing dots — never mix.
- iOS "Liquid Glass" is NOT faked — use Material 3 tonal surfaces. Cards: `surface` color, radius 20dp, 1dp `border` hairline.

## Scope

Port only Workis-era screens: Splash → OTP Login (TR/EN tabs + public Jowi ask) → Apply wizard →
Main tabs (Panel [role 3/4 console · role 5 expert] · Talepler · Hesap · Jowi) + console/expert screens.
Do NOT port legacy jowi/Decora screens (ContentView, ChatView, Dashboard…) or `/api/ask-question/`.
`/workis/console/sistem/` stays web-only. Localization via `strings.xml` + `values-tr/` (not a hand-rolled struct).
