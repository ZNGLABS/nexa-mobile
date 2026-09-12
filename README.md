# NEXA Mobile

Native Android layer for [NEXA EXCHANGE](https://nexa-exchange.fr) — a Solana DEX with
spot swaps and Phoenix Flight perpetuals, live on the Solana dApp Store since July 2026.

Built for **CLOCK IN**, the Solana Mobile hackathon (8 September → 9 October 2026).

---

## Why this repository exists

NEXA already ships an Android app, but it is a **TWA** — a thin wrapper that loads the
website. The CLOCK IN rules are blunt about that:

> *"Direct ports or minimal conversions of existing web apps, including PWA wrappers with
> little to no mobile optimisation, will score poorly and are unlikely to win."*

That judgement is correct. A wrapper cannot do the one thing a trader actually needs from
a phone: **tell them something happened while they were not looking.** Close the tab and
the web app stops existing.

So this repository is not a port. It is new, native Android code that does what the web
cannot, written during the hackathon window. The repository history is the evidence.

## What is new here, concretely

| Capability | Why a web app cannot do it |
|---|---|
| **Foreground service** polling Phoenix every 60 s | A browser tab stops when it is closed |
| **Native price alerts** on any of 82 perp markets | No background execution, no OS notification channel |
| **Restart after reboot** (`BOOT_COMPLETED`) | An alert set at night would silently die |
| **Home screen widget** with the live mark price | No web equivalent on Android |
| **Direct Solana + Phoenix access from Kotlin** | — |

## Architecture

```
Android app (Kotlin, Jetpack Compose)
  ├── data/PhoenixApi.kt     HTTPS → perp-api.phoenix.trade   (markets, mark prices)
  ├── data/AlertStore.kt     SharedPreferences                (alerts stay on device)
  ├── service/               Foreground service + boot receiver + notifications
  ├── widget/                AppWidgetProvider, refreshed by the service
  └── ui/                    Compose screens, NEXA colours
```

**There is no backend.** An earlier design routed reads through a Supabase Edge Function
that would decode Phoenix trader accounts with `@ellipsis-labs/rise`. Measured on
12 September 2026: that SDK pulls in `ws`, which needs `node:url`, `bufferutil` and
`utf-8-validate` — none of which exist in the Supabase edge runtime, and the function
fails to boot. Removing the backend is also the better design for a background monitor:
one fewer component that can be down at 3 a.m.

Both Phoenix endpoints used are public and unauthenticated, so **the APK contains no
secrets of any kind**.

## Build it yourself

No keystore, no secret, no local Android SDK needed beyond the standard one.

```bash
git clone https://github.com/ZNGLABS/nexa-mobile
cd nexa-mobile
gradle :app:assembleRelease        # Gradle 8.4, JDK 17, Android SDK 34
```

The APK lands in `app/build/outputs/apk/release/`. CI builds every commit and publishes
the APK to the [`latest`](https://github.com/ZNGLABS/nexa-mobile/releases/tag/latest)
pre-release, downloadable without a GitHub account.

Release builds are signed with Gradle's **debug key** on purpose: this repository is
public, so it holds no signing material. The APK installs and runs normally for review.
Its application id is `fr.nexaexchange.mobile`, deliberately different from the published
`fr.nexaexchange.dex`, so both can sit on the same Seeker side by side.

## Status — honest

| | |
|---|---|
| ✅ Markets list, live prices, 24 h change, search | working |
| ✅ Price alerts, background service, reboot recovery | working |
| ✅ Home screen widget | working |
| ⏳ Mobile Wallet Adapter sign-in | not started |
| ⏳ Open positions and PnL read from the trader account | not started |
| ⏳ Liquidation-distance alerts | not started |

The last two need the Phoenix trader account decoded in Kotlin. The byte layout has been
partially mapped against the official SDK (collateral at offset 88, flags at 96 on a
1 520-byte account) but **the positions map layout is not yet verified against a real
account holding a position**, so it is not shipped. No liquidation figure will appear in
this app until it is checked against ground truth — a wrong safety threshold is worse
than none.

## About NEXA EXCHANGE

Live at [nexa-exchange.fr](https://nexa-exchange.fr) · on the Solana dApp Store as
`fr.nexaexchange.dex` · 4.6★ from 8 reviews · registered Phoenix Flight builder
(`4w1F9Dzua91TQwgsTKdzxugYYWPTiyThHtJE32xvA9rK`).

Built by ZNG Labs. [@nexa_exchange](https://x.com/nexa_exchange)
