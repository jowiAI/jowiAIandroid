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
- Tester builds go through **Firebase App Distribution** (project `workis-app`, group `ekip`):
  `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleRelease appDistributionUploadRelease`
  signs with `keystore/jowi-release.jks` (keystore.properties, both gitignored), uploads the APK and mails the group.
  Before every upload: bump `versionCode` (the same code is refused as a duplicate) and rewrite `app/release-notes.txt`.
  Auth = the Firebase CLI login on this Mac (`firebase login`, interactive — the owner runs it). Testers / groups:
  `firebase appdistribution:testers:add --group-alias ekip a@b.c --project workis-app`, groups `appdistribution:group:create`.
  The Play Console (internal testing, AAB) is the store road later; nothing here depends on it.
- In place: Retrofit + OkHttp + kotlinx.serialization, EncryptedSharedPreferences (token),
  BiometricPrompt gate on launch (ui/BiometricGate.kt — skips when no lock/biometric enrolled),
  per-app locale via AppCompat, prefs in SharedPreferences (`workis_lang`, `workis_appearance`).
- Chrome Custom Tabs via `openInApp()` (ui/components/Markdown.kt) — every `workis.ai` link
  in consent texts opens in-app; catalog markdown (`**bold**`, `[label](url)`) renders
  through `MarkdownText`.
- Push (ledger 45, `data/PushService.kt` + `WorkisMessagingService`): the FCM carrier — Django's `push/register|unregister/`
  with `platform: "fcm"` (explicit; token as it comes, never hex), `{success, ready}` (`ready: false` = stored, carrier not
  switched on → `notificationsServerOff`). The build is DORMANT without the owner's `app/google-services.json` (gitignored):
  the google-services plugin is applied only when the file exists, FirebaseApp never initialises, `tokenSource` stays null and
  the Hesap footer reads `notificationsBuildOff`. Messages carry `data.scopes` as a JSON-array STRING (comma list also read;
  `thread-id` the fallback) — foreground = no banner, the `ChangesWatch` screens re-read; a tap on the system banner (channel
  "workis", the slash glyph, kiremit) lands in `MainActivity` (singleTop) → `PushService.tapped` → `openScope` picks the
  Pazar tab and `MarketRoot` opens the first scope's page. The push never carries the record. Hesap › Bildirimler is the
  person's choice, off by default; POST_NOTIFICATIONS asked only there.
- Agreement paper (`ui/components/AgreementPaper.kt`) is shared by wizard step 04 and the
  expert screen; its text comes from `GET /workis/agreement/?role=|key=` and the PDF renders
  natively (android.graphics.pdf.PdfRenderer, `PdfPages`). Never hard-code a PDF path.
- Jowi tab (`ui/screens/JowiScreen.kt`) is the SERVER's thread (`GET ask/history/`, shared with the
  web drawer; no device storage): `ask/preview/` before every send (10 s, fallback = typed text),
  policy-B stamp + 👍/👎 via `ask/like/`, match card, "Aç" links in-app. OkHttp read timeout is 60 s
  (deliberate: raised to 90 s on 2026-09-09, back to 60 s on 2026-09-11 — the same as iOS's URLSession default on `/workis/ask/`). `handedOff` (ledger 70) on the live reply and on history rows (`source == "handoff"` reads
  the same) draws `WorkisIcons.People` + "Ekibe iletildi" in the stamp's slot — icon + words, never colour alone. Answers are
  md_lite blocks through `AnswerText`, never plain text.
- Server Markdown (expert guide) renders through `MarkdownDocument` (ui/components/) — the dialect in
  API_CONTRACT (##/###, > quote box, GFM tables, lists, `code` chips). Never re-author guide text.
- Partner seat (role 1) lives in `ui/screens/partner/` (Django `docs/WORKIS_PARTNER_API.md`): `PartnerStore`
  (Graph.partner) holds the `company/` verdict — 2xx = Pazar tab (Listeler / Teklifler by seat), 403
  `seat:"none"` = Başvuru tab (SeatlessScreen), `"operator"` = no partner tab. Hesap shows `CompanySections`
  whenever a firm answered. MONEY ON THIS SURFACE IS A JSON NUMBER → `WorkisMoney.text()` (2 fraction digits,
  app locale), never a decimal string. `text/polish/` only suggests — applied on the user's tap, never automatically.
- Login's Jowi tab (ledger 60): `GET workis/public/home/?lang=` (data/PublicHome.kt, cached per language in prefs, drawn
  offline) gives the intro, five cards and the 12-page story; `how` opens `StorySheet` (ui/screens/), the other four POST
  `apply/ask/ {card, lang, vk}`; a free question there carries `surface: "home"`; a story-page question `story: <id>`.
  `vk` = `Graph.visitorKey` (one UUID per install, never tied to an account). Content is the server's, never in strings.
- Lists (ledger 63): the title renames in the bar only while `draft`/`quoting` (`POST lists/{id}/rename/ {title}`; 400/409 sentence =
  the screen note, title falls back). Seller labels are drawn AS THE SERVER SENDS them — a virtual seller already carries "/ NAME",
  never prepend anything for `verified == false`; `virtualInvites[].name` is raw, draw "/ " + name. A row with no `candidates` shows
  "/ Workis" (`sourcedSeller`) — the app's own word, the API row has no seller. Need card (Jowi tab): question first, components folded
  to `core` while open, the main composer answers the open card (placeholder = the question); no field on the card.
- Jowi's stage on a draft list (ledger 66): `ui/screens/partner/JowiStage.kt`, a SECTION above the rows (never a sheet), one row
  at a time (tapped badge → first `askUse` → first `better` not yet bound). A chip only fills the line; `POST lists/items/{id}/use/
  {use}|{skip:true}` → `{card|null}`; "Buna geç" = bind `{cpid, sellerId}`; "+ Ekle" = add door with `fromItem` (the stage stays on
  the source row). Add replies carry `askUse` (camelCase only — `ask_use` is the web door's) → the stage opens on the new row. No price
  on the stage; its buttons are bordered — "Teklif iste" stays the page's one fill. `use` on a row decodes tolerantly (`PLUseTolerant`).
  Ledger 67: `bind/` answers `card | null` — the row's card after the bind; it stands for the row at once (`stageCards`, a null drops
  the stale one) until the re-read. The app NEVER derives a switch: after "Buna geç" the server says `fits` + `switched {from,to}` and
  the stage draws "Bu ürüne geçildi · <to>" where the alternative stood. Ledger 68: an empty variant price reads `priceWorkisAsks`;
  `priceOnRequest` is only the seller's own price-field placeholder.
- Console Panel (ledger 61, `ConsoleRoot.kt` `ConsoleHome`): the web's order tile for tile — two big tiles (Yeni başvurular with
  "N onay bekliyor" only above zero · Bekleyen soru with "en eski N gün"), count rows (Partnerler · Yazışmalar · Uzmanlar half-row,
  coordinator only), two activity tiles (Aktif koltuklar · Aktif firmalar carrying "N liste · N teklif · N sipariş"), the sidebar's
  doors (Bilgi · Ürünler · Gruplar), "Jowi yanıtladı" with ALL sources (>3 → two a row) and up to eight topics, then "/ Soru
  konuları" as its own section. Yazışmalar counts OPEN threads (`openConversations`, else open rows of the list), never the raw size.
- Alım hafızası (ledger 54): the order thread's `kind: "private"` reply keeps the sent words → "Tabloya ilet" re-posts them with
  `audience: "table"` (ORDER door only). The satisfaction tick (`SatisfactionCard.kt`, `SatisfactionTicks`) is drawn from the confirm
  reply's `satisfaction` right under the confirmation (Onaylar + the order stage) and as Onaylar's last section — words as sent,
  `counts.satisfaction` never in total, `reasons[]` chips → `reason`. Buyer Panel: `GET memory/nudges/` rows between "Sizde" and the
  doors (`NudgeRows`), the button = `link.text`, a failed read draws nothing. The need/match cards carry `destination` (the "→ …" line
  above the button, "değiştir" sheet, `needsChoice` chips; build sends `{into}`|`{newTitle}`, collect `listId`|`title`) and `memory`
  (the line above the reading; chips tick rows LOCALLY by `partnerId`, never sent) — `JowiCardMemory.kt`; history rows carry neither,
  the session keeps its values (`destPicks`/`keptMatch`/`KeptNeed`).
- Tab sets: role 3/4 and 5 → Panel · Hesap · Jowi; others → Pazar|Başvuru · Hesap · Jowi. Talepler (`cases/`) is retired.
  `GET auth/workspaces/` (`Graph.workspaces`, read once per session) adds Kararlar when a HOSPITALITY room exists, takes Başvuru
  off, and takes the Jowi tab off when the account is Jowi-only (a room and `workis.hasSeat != true`).
- Kararlar (ledgers 20–33, `ui/screens/decisions/`, `data/DecisionModels.kt`): the Jowi HOSPITALITY surface — a hotel manager's
  own decisions and whether the staff were told; NOT behind the seat wall. ONE door: `POST jowi/entry/` says instruction |
  question | unsure — the app never classifies; `kind` may only ever be `"question"`. No confirmation step (the Jowi tab's send
  rule); a MATERIAL rewrite is the one two-reading card. `restated` IS the record's sentence; `subject` is never drawn. The
  questions card draws no `restated` until the re-preview with `answers{}` comes back without questions (`settled`); a follow-up
  question JOINS the card; changing an answer drops the ones after it; `duplicate` = two plain buttons, "" closes without a POST.
  Delivery is three states with icon + words (`deliveredAt` / `deliveryError` verbatim / still going). The record is whole: no
  delete, no edit; "Geri çek" only on `canRetract`, the row stays with its trace. The live turn is two bubbles (hers = the
  yazışma's "mine" style); answered turns stay as an in-memory conversation (`history`, last 6, record items too, the quoted turn
  LAST), long-press = Kopyala · Yanıtla (`replyTo` on every door of that sentence), `trail` = "Dayanak". A refusal sentence is
  shown only when the server sent a `code`. Nothing is stored on the device.
  Başvuru (ledger 55, `SeatlessScreen`): on `seat:"none"` with `application: null`, `emailConflict: false` and the invitations
  answer in and EMPTY, the native wizard opens itself ONCE per tab life (`applyAutoOpened` in `MainScreen`, never a flag on the
  screen — it leaves the composition while the wizard is up); back from the intro = the plain page. `emailConflict: true` appends
  `seatlessConflict` under the text and never auto-opens. `applyUrl` is only the signal; the wizard prefills the signed-in address.
- Gruplar (ledger 62, roles 3|4): `ui/screens/console/GroupsScreen.kt` + `data/GroupsModels.kt`. ONE page: `GET console/groups/?sector=`
  is the whole screen and every POST answers the page re-read with `message` spread at the top level — the state IS the last
  answer; every body carries `sector` (the slug on screen). Actions never on Genel, never without `canWrite`; each behind its
  own flag (`renamable/movable/deletable`). The × on an alias chip and "+ başlık bağla" are their own clickables.
  Ledger 65: two tabs when `canWrite` (tree · headings); the headings tab is `GET console/groups/headings/` then
  `headings/suggestions/` ONCE after the list renders (slow, cached an hour) — a bind re-GETs `headings/` only and keeps the
  picks. The ONE group card is `ui/screens/console/GroupCardSheet.kt` (Jowi's picks · New group over `targets` · search over
  `groups`); it only chooses, the caller posts (`headings/bind/ {key, groupId} | {key, name, toSectorId, parentId?}`; on Ürünler the
  review cell `{field:"category", value: groupId}`, a new group via `console/groups/add/` found by name in the page, then the cell).
  Ürünler reads `seller/catalog/group-suggestions/` once per screen; "New group" rights come from the headings door, console mode only.
- Ürünler review list (ledgers 18/19, `CatalogScreen.kt` `ReviewSections`): grouped by `families[].category` AS SENT (the server
  normalises it; `categoryHint` is never the key). Two filter CHIPS that combine — Belge from `ingestionId` named by
  `ingestions[].label` (never assembled from filename/kind/brandHint), Marka from `brand` — drawn only with two+ choices, counts under
  the OTHER filter, selected = kiremit border, 0 = disabled, tap again clears. The family hero is `heroKey` (primary `media[]` row,
  `imageKey` fallback). The upload footer carries `uploadOneDocNote`.
- Knowledge (Bilgi) is ONE screen behind a seat: `ExpertKnowledgeScreen(seat = Expert | Console)` with
  `KnowledgeViewModel` (expert/* vs console/* doors; console adds semantic search, author tags, pairs).
- Expert slice 2: `ExpertEarnings.kt` (door = the Panel's "Bu ay" card) and `ExpertAccountSections.kt`
  (Hesap sections for role 5 + departure/rejoin). Money stays decimal-as-string end to end.
- Every "it's done" state is `OutcomeView` (glyph + mono title + one paragraph + one kiremit action).
- Cell-exit autosave rows: `FormRow` (ui/components/FormRows.kt) — `onCommit` fires when
  focus leaves; expert form saves a prefs draft, application detail POSTs one field per call. The FIRST mail a tester gets is Workis's own, not Firebase's template: the Django System page (`/workis/console/sistem/` › Test app invitation) or `python manage.py send_tester_invite --platform android --link <invite link> mail…` sends the branded HTML mail with the group's invite link (Firebase console › Testers & Groups › Invite links); Firebase then mails only the 'new build' notices.

## API rules (from API_CONTRACT.md — the mobile side never edits Django serializers)

- Base: `https://workis.ai/api/v1/` · header `Authorization: Token <key>` (NOT Bearer), `Accept-Language: tr|en` on every request.
- Responses are camelCase; money is decimal-as-string → `BigDecimal`, never Double.
- Decode tolerantly: nullable fields + `ignoreUnknownKeys = true` (backend lights fields up after client ships).
- 401 → wipe token, back to login. 403 → routing signal (missing seat profile → onboarding), not an error screen.
- No pagination on `/workis/` lists, no WebSockets (poll/pull-to-refresh), push not implemented yet.
- Login is ONE door (ledger 56/58): send-code calls `POST /auth/door/ {email, lang}` — `known: true` → the code step
  (`otp/verify/`, `confirmSignup: true` kept), `known: false` → the native wizard opens with the address (`applyUrl` is only
  the signal, never a web view). Refusals (400/429/502) show the server's `error` as it arrives. No apply card on the login.
  `POST /auth/validate-token/` on launch.
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
- Blue (`colors.blue`, ledger 75, 2026-10-28) means ONE thing: "sıra sizde" — the reader's own move is pending ("teklifinizi
  bekliyor"). One place per screen at most, always beside a word; never a link, a title, a document row (those are muted ink) or
  a second accent. Kiremit stays the one action colour.
- IBM Plex bundled in `res/font` (Sans variable + Mono statics). Mono only for logo/digits/eyebrows; Sans for reading text.
- Reading step (ledger 57, 2026-10-13): sizes written at call sites are DESIGN sizes; `ui/theme/Type.kt` renders ≤15 at +2
  (floor 11), 16+ as written, a paired `lineHeight` grows by the same amount. It lives in `ai.workis.jowi.ui.theme.Text` —
  import THAT `Text` in every file, never `androidx.compose.material3.Text`. A hand-built `TextStyle` for a text field goes
  through `.readingStep()`, a `SpanStyle` size through `readingSize()`. System font scale multiplies on top; no in-app text-size setting.
- Loading idioms: inline button spinner / breathing slash (`WorkisMark(breathing = true)`) / typing dots — never mix.
- iOS "Liquid Glass" is NOT faked — use Material 3 tonal surfaces. Cards: `surface` color, radius 20dp, 1dp `border` hairline.

## Scope

Port only Workis-era screens: Splash → OTP Login (TR/EN tabs + public Jowi ask) → Apply wizard →
Main tabs (Panel [role 3/4 console · role 5 expert] | Pazar/Başvuru [partner] · Hesap · Jowi) + console/expert/partner screens.
Do NOT port legacy jowi/Decora screens (ContentView, ChatView, Dashboard…) or `/api/ask-question/`.
`/workis/console/sistem/` stays web-only. Localization via `strings.xml` + `values-tr/` (not a hand-rolled struct).
