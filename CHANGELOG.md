# Changelog

All notable changes to the Avafli Android SDK (formerly the WINR Android SDK)
will be documented in this file. Entries for 2.9.6 and earlier predate the
rebrand and use the former WINR names.


## 3.0.0 — 2026-08-25

**Full brand rename: WINR → Avafli.** Behavior, backend, and API keys are
unchanged — this release renames the SDK's coordinates, packages, and public
symbols. The **2.9.x line stays functional but frozen**: existing integrations
keep working against the same backend, but all new features and fixes ship as
Avafli 3.x only.

- **Gradle coordinates** — the module is now `:avaflisdk` and the Maven
  artifact `com.avafli:avafli-sdk`; via JitPack:
  `com.github.AVAFLI:avafli_android_sdk:v3.0.0`
  (was `com.github.AVAFLI:winr_android_sdk:v2.9.6`).
- **Package** — `com.avafli.winrsdk` → `com.avafli.avaflisdk`.
- **Public API symbols renamed:**

  | 2.x (WINR) | 3.x (Avafli) |
  | ---------- | ------------ |
  | `WINR.configure(...)` | `Avafli.configure(...)` |
  | `WINRConfiguration` | `AvafliConfiguration` |
  | `WINRUser` / `WINRUser.GUEST` | `AvafliUser` / `AvafliUser.GUEST` |
  | `WINROptions` | `AvafliOptions` |
  | `WINREnvironment` | `AvafliEnvironment` |
  | `WINRError` | `AvafliError` |
  | `WINR.optOut()` | `Avafli.optOut()` |
  | `WINR.registerForPushNotifications(...)` | `Avafli.registerForPushNotifications(...)` |
  | `WINR.onNewToken(...)` | `Avafli.onNewToken(...)` |

- **Internal symbols, resources, and the example app** renamed to the Avafli
  prefix throughout (`WinrApi` → `AvafliApi`, `WINRV2*`/`V2*` composables →
  `AvafliV2*`, `winr_*` resources → `avafli_*`).
- **Delete bridge accepts both schemes** — the in-app privacy page's
  delete-my-data bridge is intercepted as `avafli://delete` AND the legacy
  `winr://delete`, so either build of the hosted page works.
- **User-visible branding** — all "WINR" / "WINR Media" strings in the
  experience and the example app now read Avafli.
- **Unchanged on purpose (compatibility):** legal document URLs remain on
  `winrmedia.com`; API key prefixes remain `winr_live_` / `winr_test_`; guest
  ids remain `winr_guest_…`; analytics event names (e.g.
  `winr_daily_entry_claimed`) and the `utm_medium=winr_share` share tag are
  unchanged; on-device storage keys are unchanged so existing installs keep
  their streaks, entries, auth, and opt-out state across the upgrade.


## 2.9.6 — 2026-08-18

- **Taller drawer: dashboard and capture fit without scrolling; bottom
  insets respected** — the V2 sheet now rises to just below the status bar
  (small reveal of the host app, matching the iOS feel) instead of a fixed
  90% of the screen, and everything inside the drawer is inset above the
  navigation bars, so nothing renders under the gesture bar. On tall
  gesture-nav phones the old geometry clipped the capture screen's legal
  fine print mid-line and pushed the dashboard's legal row out of view
  entirely; now the full dashboard (winner banner, prize card, progress
  tiles, streak callout, GOT IT, legal row) and the capture screen fit with
  no scrolling on typical phones — scrolling remains as a fallback for very
  short screens. The capture screen's leftover height also no longer pools
  as one dead block above the footer: it splits evenly around the form, and
  the dashboard's GOT IT + legal row anchor to the drawer's bottom.
- **Reveal-beat crash fixed (API 34/35)** — a platform race in
  `AnimatedImageDrawable`'s animation-end dispatch (the posted callback
  lambda re-reads the callback list without a null check; tearing down a
  confetti burst on the same frame the GIF ends nulls it) could crash the
  app right after the daily celebration. The decoded GIF now keeps a
  permanent no-op animation callback registered so the framework's posted
  dispatch always has a list to iterate.

## 2.9.5 — 2026-08-18

- **Delete confirmation presents over the experience** — on the privacy
  page's `winr://delete` bridge the legal webview now closes FIRST, and the
  destructive delete confirmation raises over the SDK screen the user came
  from (matching iOS/web). Cancel therefore returns to that screen, not the
  privacy page.
- **"Privacy choices" link removed from How it works** — redundant now that
  the delete path lives inside the Privacy Policy webview, which stays
  findable via the OFFICIAL RULES • PRIVACY POLICY rows (dashboard and
  code-entry screens) and the capture screen's inline links.

## 2.9.4 — 2026-08-18

- **Legal documents open inside the experience** — Official Rules and the
  Privacy Policy now present in an in-drawer WebView screen (slim header with
  the document title + X, gunmetal chrome, loading indicator, simple error +
  retry) instead of bouncing the user out to a browser (`ACTION_VIEW`). Every
  legal affordance routes there: the capture screen's inline consent-sentence
  links, the OFFICIAL RULES • PRIVACY POLICY rows (dashboard and code-entry
  screens), and the how-it-works fine print. Rules load the configured
  `rulesUrl`; Privacy loads `WINRConstants.PRIVACY_URL` with `?app=1`
  appended (okhttp HttpUrl building, so existing query strings extend
  correctly) — the parallel in-app build of the page.
- **"Delete my data" moved into the privacy webview** — the native "Privacy
  choices" dialog is gone. The how-it-works "Privacy choices" fine print now
  opens the privacy webview directly, whose `?app=1` build renders the
  delete-my-data section; choosing delete there navigates `winr://delete`,
  which the WebView intercepts (no manifest change, no exported component, no
  scheme registration — and no JavaScript interface objects; JS is enabled
  for the page but the bridge is navigation-only) and raises the EXISTING
  destructive confirmation + authenticated erasure flow, now rendered at the
  V2 root over whatever screen is up.
- **Share-link UTM tagging** — when the publisher's `shareUrl` is included
  in a share action, the SDK appends `utm_source={network}&utm_medium=winr_share`
  ({network} = x | facebook | instagram | snapchat | tiktok, per the tapped
  button; system-share-sheet paths keep their network's value). Built with
  okhttp's HttpUrl so URLs with existing query strings extend correctly, and
  a shareUrl that already carries a `utm_source` param is left untouched
  (publisher tagging wins). Share-text URLs only — nothing else changes.


## 2.9.3 — 2026-08-17

- **Claim review: legal sentence removed** — the "By submitting you agree to
  the Official Rules / Privacy Policy" sentence (and its links) is gone from
  the "ALMOST DONE" review screen entirely (Ryan's direction, Joe's updated
  Figma). The screen keeps only the optional likeness checkbox, the submit
  CTA, and the secure-and-encrypted note; the legal linking lives on the
  capture screen.
- **Likeness consent names the publisher** — the optional checkbox now reads
  "I authorize {name} and its promotional partners…" where {name} is the
  server-fed `sdkConfig.appName` (new field) when present, else the host
  app's launcher label (the same source as the share line), else the previous
  generic "this app's publisher" wording.
- **Winner splash confetti** — the CONGRATULATIONS! splash gains a confetti
  animation layer per Joe's frame: the winner-modal gold drift behind the
  content plus a one-shot celebration burst (Joe's Figma GIF, same machinery
  as the Day 2+ tile reveal) over the trophy on appearance — both
  non-blocking.
- **Capture screen brand accents** — in the "VISIT. EARN. WIN." title the
  word "EARN." now renders in the publisher's primary brand color, and the
  two consent checkboxes (18+ age gate and marketing consent) are tinted the
  same primary: checked is a primary fill with a contrasting check, unchecked
  a primary-tinted border.
- **Claim confirmation matches Joe's frame (5386:5807)** — the "YOUR PRIZE
  CLAIM HAS BEEN SUBMITTED" screen gains the same confetti celebration as the
  winner splash (gold drift layer + one-shot burst on appearance,
  non-blocking); the "3-5 Business Days" card is now a solid gunmetal card
  with a subtle border and the envelope icon inside a publisher-accent-stroked
  circle (the business-days text already used the accent); and the gold
  winner card's OFFICIAL / WINNER labels render in the publisher's primary
  accent while the card body and typography stay gold-family.
- **Top glow removed everywhere** — the blue radial top gradient
  (`WINRV2TopGlow`) is gone from every screen, not just capture: the
  code-entry screen (the last holdout, kept in 2.9) now uses the same flat
  gunmetal drawer background, and the unused glow component is deleted. The
  gold sparkle prize art on the winner-flow screens is untouched.

## 2.9.2 — 2026-08-14

- **Capture screen: one legal text, anchored to the bottom** — the email
  capture screen no longer shows its legal text twice. "Official Rules" and
  "Privacy Policy" inside the consent sentence are now underlined tappable
  links, and the separate "OFFICIAL RULES • PRIVACY POLICY" links row is
  gone from this screen only — the code-entry and claim screens keep theirs.
  The legal block (sentence + "Powered by © WINR Media") is anchored to the
  bottom of the drawer via a weighted spacer instead of sitting congested
  under the CTA; with the keyboard open or on short screens it degrades to
  the normal scrolling behavior and never overlaps the button.
- **Privacy Policy links open the Privacy Policy** — every "Privacy Policy"
  affordance (capture sentence span, the OFFICIAL RULES • PRIVACY POLICY rows
  on code entry and the dashboard, the claim-review consent sentence, and the
  "Privacy choices" dialog — whose combined "Official Rules & Privacy Policy"
  link is now two independent links, matching iOS)
  previously opened the rules URL — a latent cross-platform bug. They now open
  `WINRConstants.PRIVACY_URL` (`https://winrmedia.com/sdk/privacy`, mirroring
  iOS `WINRConstants.privacyURL`); "Official Rules" keeps the configured
  rules URL. The claim-review sentence's two phrases are now independent
  link spans instead of one whole-line tap target.

## 2.9.1 — 2026-08-14

- **Official brand share icons** — the winner claim "Share on Social Media:"
  row (X / Facebook / Instagram / Snapchat / TikTok) now renders the official
  WINR brand glyph set from Figma instead of the hand-drawn approximations.
  White vector fills on the same dark button chrome; sizing and share behavior
  unchanged.

## 2.9.0 — 2026-08-14

The 14 Aug team decisions.

- **Keyboard never blocks fields** — every text-input screen (email capture,
  6-digit code entry, winner claim names/address/story) stays fully scrollable
  with the IME open: `imePadding()` on the scrolling form columns, focused
  fields scroll themselves above the keyboard (`BringIntoViewRequester`), and
  `WINRExperienceActivity` pins `adjustResize` (manifest + window).
- **Capture screen background** — the blue radial gradient is gone; the email
  capture screen now uses the same flat gunmetal the streak dashboard drawer
  uses.
- **Claim review ("ALMOST DONE!")** — the "information is accurate" and "agree
  to Official Rules" checkboxes are removed (the rules/privacy links stay
  tappable). Only the likeness/promo checkbox remains, OPTIONAL and unchecked
  by default — SUBMIT never gates on it. Its state rides the payload as the
  new `promoConsentGranted: Boolean` on `submitPrizeClaim`.
- **Share step last** — "PLEASE SHARE A LITTLE" now shows AFTER a successful
  submit (form is 3 steps + review); closing it loses nothing. A story typed
  there posts after submit via the new `attachClaimStory` callable
  (`{ story }` → `{ saved }`), fired on DONE and on any dismiss/close/back so
  a swipe-away never loses it — fire-and-forget with one retry, never blocking
  the flow.
- **Real share actions** — X opens `twitter.com/intent/tweet?text=` prefilled
  with "I just won {prize} in {appName}!" plus the publisher's new OPTIONAL
  `sdkConfig.shareUrl`; Facebook opens `sharer.php` with the shareUrl only
  (platform rule: no prefilled text); Instagram/Snapchat/TikTok use the system
  share sheet with text + link.
- **Address autocomplete on the claim form** — when the new OPTIONAL
  `sdkConfig.placesApiKey` is present, the claim address step's street field
  suggests US street addresses as you type (Google Places API New over the
  SDK's existing HTTP stack — no Places SDK dependency; debounced ~300ms,
  min 3 chars, up to 5 suggestions with a "powered by Google" attribution
  row). Tapping a suggestion fills street/city/state/zip; everything stays
  hand-editable, any Places failure degrades silently to plain typing, and
  an absent key keeps the previous behavior exactly.
- **Zip field no longer clipped** — the State/Zip row now splits by weight
  instead of a fixed zip width.
- **Privacy choices surface** — "Delete my data" is no longer direct from
  "How it works"; the Privacy choices surface hosts the policy link and the
  delete action (existing destructive confirmation unchanged).
- **Adoption re-entry** — when the register/status response carries the new
  OPTIONAL `adoptionPending: true`, the next open calls the new
  `restageAdoption` callable (response `{ sent }`) and resumes at the code
  screen with a "pick up where you left off" subtitle; "Send a new code"
  restages again.

All new response fields are optional/nullable against current production.

## 2.8.0 — 2026-08-13

- Version alignment with the 2.8.0 platform release. No functional changes; `environment` already defaulted to `Production`, and sandbox API keys (`winr_test_…`) are the supported way to test.
## 2.7.0 — 2026-08-11

2.7.0 — 'Verify your email' soft-verification: a persistent chip on the streak
dashboard lets users confirm a newly-typed email (reusing the code screen);
never blocks daily play, only prize-draw eligibility.


## 2.6.3 — 2026-08-11

2.6.3 — firstName/lastName are now optional on WINRUser; pass only the identity
data you have and the SDK captures the rest (email via the capture screen).

## 2.6.2 — 2026-08-11

Age-gate text honors publisher config; push notifications functional on
Android/web; resend keeps the code screen; error screens pick up publisher
branding.

- **Age-gate label honors server config** — the capture screen no longer
  hardcodes "I confirm I am 18 years of age or older". It renders the
  publisher's `ageGateText` verbatim when present (nested `emailCapture` copy
  wins over the flat legacy field), and otherwise BUILDS the sentence from the
  server's `ageGateMinAge` (new top-level `SdkConfig` field, default 18). 18 is
  never asserted over a publisher-configured minimum. Matches web/Flutter.
- **Push notifications actually register** — `PushNotificationManager`
  resolves the FCM token through the real `FirebaseMessaging.getInstance().token`
  API (success/failure listeners) instead of a reflection stub that always
  returned null. Added the `POST_NOTIFICATIONS` manifest permission and an
  Android 13+ (TIRAMISU) runtime permission request in
  `registerForPushNotifications`. All of it stays gated on
  `WINROptions.enablePushReminders`; a disabled host is a no-op.
- **Resend keeps the code screen up** — "Send a new code" no longer flips back
  to email capture before re-submitting, so a failed resend can't strand the
  user on capture. The code-entry screen stays up throughout; a transport
  failure surfaces in the code-error slot ("Couldn't send a new code…") and a
  success leaves the user ready to type. Original consents are reused.
- **Code-error taxonomy** — adoption-code failures now map three ways off the
  backend response: contains "expired" → "That code expired…", contains
  "attempts" → "Too many attempts. Request a new code.", otherwise → "That code
  didn't match…". Matches web/Flutter.
- **Impression counter no longer burned before render** — the unregistered
  auto-open impression count is incremented only AFTER a presentable Activity is
  confirmed, not before the check. A missing/finishing Activity no longer
  consumes one of the (default 3) unregistered impressions. Matches web/Flutter.

## 2.6.1 — 2026-08-11

In-experience privacy opt-out (delete my data); District of Columbia added to
the prize-claim form.

- **Privacy choices** — the how-it-works ("?") screen gains a muted "Privacy
  choices" link. It raises a destructive confirmation ("Delete my data & stop
  participating"); confirming performs the existing RTD opt-out against
  `/optOut`, persists the local silence flags, shows "Your data has been
  deleted.", and dismisses the experience. Failure keeps the confirmation up
  with "Something went wrong. Please check your connection and try again." —
  never a pretended success.
- **District of Columbia** in the prize-claim state dropdown, per the official
  rules' "50 states and the District of Columbia".

## 2.6.0 — 2026-08-10

User-facing error messaging per the Master Field List; honest failure states —
no fabricated claim success.

- **Inline field validation with real messages** (all copy centralized in
  `V2Strings`):
  - Email capture: "Please enter a valid email address." under the field,
    shown only after the field is touched or a submit is attempted.
  - Winner claim step 1: "Please enter a valid first name." / "Please enter a
    valid last name." (unicode letters, spaces, apostrophes, hyphens, periods;
    max 50). `WINRClaimStepField` gained an optional `errorText` slot.
  - Claim-form phone stays OPTIONAL, but a non-empty value must reduce to a
    valid 10-digit US number ("Please enter a valid 10-digit mobile number.");
    an invalid one blocks CONTINUE. The bare 10 digits are what's submitted.
- **Dedicated geo-blocked state.** The backend's US-only geo-fence rejection
  (permission-denied from `enforceGeoFence`) is now typed
  (`WINRError.GeoBlocked`) and rendered as "Not available in your location"
  with an explanation — no more generic empty state.
- **Dedicated session-expired state.** A failed token refresh shows "Your
  session has expired. Please try again." with a RETRY button that re-registers
  the device and reloads. All other errors keep the friendly empty state; raw
  backend error text is never rendered.
- **Duplicate same-day entry is said out loud.** When a claim comes back
  already-claimed and this device didn't know (raced another device/open), the
  dashboard shows a transient notice — "You've already entered today. Come back
  tomorrow to keep your streak going!" — instead of silently celebrating a
  grant that didn't happen.
- **Auto-claim transport failure is no longer silent.** The dashboard settles
  UNCLAIMED (as before — never a fake success) and now says so: "We couldn't
  record today's entry. Check your connection and try again." with a TRY AGAIN
  affordance.
- **Failed email submit no longer proceeds as success.** The user stays on the
  capture screen with "Something went wrong sending your email. Please try
  again." and can retry; the local "email submitted" flag is only persisted
  after the backend accepts.

## 2.5.1 — 2026-08-10

Consent correctness and cross-device security.

- **Marketing consent checkbox starts UNCHECKED** — consent is an affirmative
  act (pre-ticked boxes are invalid under GDPR and disfavored by US state
  regulators). Declining still blocks nothing.
- **Email pre-fill**: pass your signed-in user's email via `WINRUser.email` and
  the capture screen shows it read-only — the address the user consents for is
  always one they proved to you. Malformed values fall back to the editable
  field.
- **Guest sessions**: no account system, or the user is signed out? Use the
  guest sentinel (or omit the user on web). The SDK mints a stable per-install
  `winr_guest_…` id for attribution; re-configure with the real user later and
  the streak carries over.
- **Verified adoption**: typing an email that already belongs to an existing
  WINR account now requires a 6-digit code sent to that inbox before the
  streak transfers to the new device. Fresh signups and pre-filled partner
  emails never see it.

## [2.5.0] - 2026-08-06

### Breaking

`WINR.deleteAccount()` is **removed**. Use `WINR.optOut()`. Same reasoning as
the other WINR SDKs: the old call hard-deleted entry records (the evidence a
drawing was fair), left no tombstone so delete-and-re-register farmed entries,
and never cleaned prize-claim PII. `optOut()` is identity-wide and complete.

`StreakEngine.doubleEntries()` is also removed — it served the retired
rewarded-video flow and had no callers.

### Fixed

- **The example app never worked.** It shipped `apiKey = "YOUR_WINR_API_KEY"`,
  so registration failed and the experience silently never appeared.
- **Integration errors were reported as account suspension.** Any
  `PERMISSION_DENIED` was treated as a suspended publisher, but the backend
  returns that for four causes — invalid API key, unauthorized bundle id,
  suspended key, suspended publisher. A developer with a wrong key was told to
  check their billing. Suspension now requires the server to actually say so,
  and genuine integration failures log at ERROR with the server's own wording.

## [2.4.0] - 2026-08-05

### Added
- **Marketing-consent checkbox on the capture screen.** A second checkbox sits
  directly below the 18+ age gate, styled identically (same 20dp box, drawn
  check, spacing, and text treatment), reading the server-supplied
  `copy.emailCapture.emailConsentText` — falling back to the flat legacy
  `copy.emailConsentText`, then to "I agree to receive marketing emails from
  this app". The backend populates that key with a publisher-named string
  ("…from {PublisherName}"); the name is interpolated server-side, never in
  the SDK. The box is PRE-CHECKED by default and covers MARKETING email only:
  declining it affects neither entry nor winner contact. The CTA stays
  governed by the age gate plus a valid email, so a user may untick the
  marketing box and still enter, and a winner is still contacted about their
  prize — that is operational and no checkbox gates it.

### Changed
- **Age confirmation is now transmitted and stored server-side.** `submitEmail`
  sends the real state of both checkboxes as `ageConfirmed` and
  `marketingConsent` (previously the 18+ tick was a purely local gate that
  never left the device, and consent was hardcoded to `true`). `ageConfirmed`
  is always sent — the backend reads it to detect a 2.4.0+ client.

### Tests
- 3 new: both consent flags on the wire (including a declined marketing
  consent with a confirmed age) and the ViewModel forwarding the checkbox
  states verbatim.

## [2.3.3] - 2026-08-05

Load-experience defects found testing the SDK inside a real publisher app
(ports the Flutter/iOS 2.3.3 work).

### Fixed
- **The drawer no longer sits on a spinner for seconds.** It auto-opens ahead
  of its sequential network calls (`registerDevice` → `getActiveGiveaway` →
  claim). When the device already holds a giveaway config and a persisted
  streak, the real dashboard now paints IMMEDIATELY from that cache —
  synchronously with the Activity's `setContent`, before any request resolves
  — and the fresh response reconciles silently in place, the same no-replay
  reconcile the celebration staging already used. A celebration staged AFTER
  the cache render still fires exactly once: the come-back bar now accepts a
  late-arriving toast (one-shot guarded, so it can never play twice) instead
  of missing it. The email-capture gate is unchanged: an unconsented user
  never sees a cached dashboard. A local streak-engine failure after a
  successful cache render leaves the live dashboard up rather than replacing
  it with an error screen.
- **Cold start shows a skeleton, not a bare spinner.** With nothing cached to
  paint, the loading view is now a pulsing block-out of the real layout (grab
  handle, header, prize card, three streak tiles, come-back bar, CTA pill) in
  the drawer's own gunmetal, on one shared pulse so it reads as a single
  surface breathing — replacing the centered `CircularProgressIndicator` and
  "Loading…".
- **The prize image arrives with the card instead of popping in after it.**
  The publisher's `prizeImageUrl` and `branding.logoUrl` are now decoded into
  the SDK's image cache as soon as it learns the giveaway config — at
  registration, on every giveaway refresh, and once more when the experience
  root mounts, mirroring the bundled-GIF prewarm — so the card normally paints
  its art on its first frame. Warmed URLs are tracked so repeat refreshes are
  free, and a failed URL is dropped so the next refresh retries it. A cold URL
  fades in over ~200ms against the card's deep-charcoal placeholder rather
  than flashing, and a broken one falls back to the bundled cash hero.
- **Email consent cache is refreshed on submit.** A successful email submit
  now marks the SDK-level consent flag immediately rather than waiting for the
  next `getActiveGiveaway` to echo it back, so the unregistered-impression cap
  can never burn an auto-open on a user who just registered.

### Tests
- 15 new: the cache-render-vs-skeleton decision (including the email-gate
  bail-out, the silent reconcile, and the never-stomp-fresher-truth guard) and
  the image warmer (warm once, no-op on repeat, retry after failure).

## [2.3.0] - 2026-08-04

### Added
- **Winner prize-claim flow (Joe's stepped Figma design)** — when the backend marks the user as the drawn winner (`prizeClaim.status == "pending"` on `getActiveGiveaway`), the drawer opens on the winner splash instead of the dashboard: CONGRATULATIONS! + prize strip → the stepped form over the gold-sparkle backdrop — STEP 1 OF 4 "TELL US ABOUT YOURSELF" (names, the LOCKED masked winning email from `prizeClaim.maskedEmail` with a generic fallback, optional phone) → STEP 2 "WHERE SHOULD WE SEND YOUR PRIZE?" (US address, 50-state dropdown, Country locked to United States) → STEP 3 "SHOW OFF YOUR WIN!" (optional photo via the system photo picker — both UPLOAD PHOTO and TAKE PHOTO open it; the SDK adds no camera permission) → STEP 4 "PLEASE SHARE A LITTLE" (optional story + the Share on Social Media glyph row opening the system share sheet) → review "ALMOST DONE!" with the three consent checkboxes PRE-CHECKED (defaults true per the Aug 2026 CTO decision; unticking any disables SUBMIT) → `submitPrizeClaim` (now including `story`) → confirmation with the gold OFFICIAL WINNER card (trophy breaking the top border, serif name, "MONTH, YYYY • claimNumber") and RETURN TO APP. Progress dots + "STEP N OF 4", back chevron from step 2, one-direction horizontal slide between steps. Appears automatically; no integration work. The daily auto-claim still fires silently while the flow is up, and an already-submitted claim shows the normal dashboard. Full parity with iOS/web 2.3.0's stepped flow.

### Changed
- **First-frame celebration beat, Day 1 AND Day 2+ (unified)** — on a claim-day open the dashboard mounts with a grant already staged, so the celebration is the first visible frame. Day 2+ stages a PREDICTED grant from the pre-claim status (ladder math mirrors the backend) while the real claim runs in the background and reconciles totals/streak silently in place (no second celebration; failures settle back to server truth quietly). Day 1's "You're in!" welcome modal is GONE (CTO decision): after email submit the claim is awaited while the capture spinner is still up, and the dashboard mounts celebrating from the REAL grant — count-up 0 → N with burst, Day-1 tile explosion + check + falling confetti, toast-first bar headlined "YOU'RE IN!" (Day 2+ keeps "YOU'RE ON A ROLL!"; the subline is unchanged), GOT IT closes. The 2.2.0 "CLAIM N ENTRIES" tap is gone — nothing to press, the pill reads GOT IT throughout.
- **Toast-first come-back bar, new copy** — on celebration opens the bar's first visible state is the "YOU'RE ON A ROLL! / Your {N} entries have been added automatically." toast; it holds ~2.5s, then slides once to the resting come-back pitch. Non-celebration opens rest on the pitch; a late-arriving grant still slides the toast in and back.
- **Reveal-beat tile: confetti-burst explosion + restored check/confetti** — the active day tile keeps the drawn draw-on checkmark, falling-confetti field, and pulse glow, now topped by a one-shot confetti-burst GIF explosion that overflows the tile (the big-check tile-burst GIF was rejected and removed). The burst fires only on the reveal, never on a same-day reopen.
- **Count-up total with burst** — Total Entries counts up (ease-out) and pops a confetti burst as it lands.
- **Prize card — the Delta A/B visuals** — dark and full-bleed: the prize image fills the whole card, the streak/total-entries stats sit in a solid black strip inside the top edge, and the headline overlays the bottom over a black→transparent scrim, in two layouts (A: right-aligned "WIN $1,000 / CASH PRIZE" for cash; B: centered "Win a {Prize}" + accent value line otherwise). The capture screen's white prize strip is unchanged.

## [2.2.0] - 2026-08-04
### Changed
- **Day 2+ reveal flow** — the auto-claim still fires silently the moment the drawer opens, but returning users no longer get the celebration modal. The dashboard opens pinned to yesterday's numbers (streak N-1, pre-claim total, today's tile glowing with a flame and no checkmark) behind a "CLAIM N ENTRIES" button; tapping it is the celebration — the tile checks off with confetti, the streak label and total spring forward, and the button becomes GOT IT. Mirrors iOS `e7fae27`.
- Day 1 keeps the "You're in!" celebration modal as its reveal, and its GOT IT now dismisses the whole experience (previously it settled on the dashboard)
- Email-capture CTA renamed: "GET MY N ENTRIES" → "CLAIM MY N ENTRIES"

### Added
- New `Ready` streak-tile state (radial glow like active, white flame icon, no checkmark, no confetti) used pre-reveal

## [2.1.0] - 2026-08-04
### Removed (BREAKING)
- Manual launch API removed — `WINR.present(...)` and `WINR.isServiceAvailable()` are no longer public. The experience is exclusively auto-opened by the SDK (once per calendar day, after `configure()`); publishers cannot launch it manually.

### Fixed
- JitPack build: declare `maven-publish` via `id("maven-publish")` and run `:winrsdk:publishToMavenLocal` as an explicit `install` step in `jitpack.yml`, so JitPack no longer injects a duplicate `maven-publish` plugin (the 2.0.1 build failure).

## [2.0.1] - 2026-08-04
### Fixed
- Ship the Gradle wrapper + JitPack build config so `com.github.AVAFLI:winr_android_sdk` resolves from JitPack.

## [2.0.0] - 2026-08-03

### Added
- **V2 experience** — full port of the iOS V2 design (WINR-High-V2 Figma): gunmetal bottom drawer over the host app (dim backdrop, rounded top corners, spring slide-up), bundled Inter/Oswald fonts, prize card with cash lockup / prize headline, horizontally scrolling streak rail with accelerator milestone tiles, come-back bar, celebration modal (looping confetti, animated checkmark), and how-it-works screen — built with Jetpack Compose
- **Auto-open** — the experience presents itself on the first app-open of each calendar day (after registration and on activity resumes), always on; respects the server kill switch (`sdkConfig.experience.autoOpenEnabled`), the unregistered impression cap (default 3), and RTD opt-out
- **Auto-claim** — daily entries are claimed automatically when the experience opens; a celebration modal confirms the grant
- **Winner announcements** — "WE HAVE A WINNER!" banner and winner modal, driven by the giveaway's `latestWinner`
- **Visit mode** — `streakMode: "visit"` giveaways use a never-resetting streak with visit-based copy, for low-frequency apps
- **Ladder accelerators** — streak ladder math mirrors the backend exactly, including milestone accelerators beyond the explicit ladder
- **RTD opt-out** — new `WINR.optOut()` tombstones the person on the backend and permanently silences the experience on the device

### Changed
- Branding is server-driven and limited to logo, prize image, and primary color
- `WINR.present` is now optional — the default integration is `configure()` only

### Removed (BREAKING)
- Rewarded-video/bonus entry flow (`WINROptions.rewardedVideoProvider`, provider interface, bonus claim UI)
- The `autoPresent` option — auto-open is always on (server kill switch replaces it)
- V1 server-driven copy/media theming

## [1.0.0]

- Initial release: daily entry sweepstakes, streak system, email capture with age gate, rewarded video provider interface, FCM push reminders, server-driven SDK config, GDPR deletion, certificate pinning, analytics adapter system
