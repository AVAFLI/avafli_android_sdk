# Changelog

All notable changes to the WINR Android SDK will be documented in this file.

## [2.3.0] - 2026-08-04

### Added
- **Winner prize-claim flow** — when the backend marks the user as the drawn winner (`prizeClaim.status == "pending"` on `getActiveGiveaway`), the drawer opens on the winner splash instead of the dashboard: CONGRATULATIONS! + prize strip → single-page claim form (name, address, 50-state picker, optional phone/photo; email stays locked to the account) → `submitPrizeClaim` → confirmation with the gold OFFICIAL WINNER card and RETURN TO APP. Appears automatically; no integration work. The daily auto-claim still fires silently while the flow is up, and an already-submitted claim shows the normal dashboard. Mirrors iOS `2127ec1` (2.3.0).

### Changed
- Post-reveal come-back bar now celebrates "{N} ENTRIES ADDED / You're on a roll!" (animated swap on the CLAIM reveal and in any claimed-today dashboard state); pre-reveal/unclaimed states keep the come-back pitch.
- Prize card is dark and full-bleed: the prize image fills the whole card, the streak/total-entries stats sit in a solid black strip inside the top edge, and the headline overlays the bottom over a black→transparent scrim (right-aligned "WIN $1,000 / CASH PRIZE" for cash; centered "Win a {Prize}" + accent value line otherwise). The capture screen's white prize strip is unchanged.

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
