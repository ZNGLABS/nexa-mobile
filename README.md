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

| Capability | Why a web app cannot do it | Verified |
|---|---|---|
| **Foreground service** polling Phoenix every 60 s | A browser tab stops when it is closed | ✅ on a Seeker |
| **Native price alerts** on any of 82 perp markets | No background execution, no OS notification channel | ✅ on a Seeker |
| **Restart after reboot** (`BOOT_COMPLETED`) | An alert set at night would silently die | ✅ on a Seeker |
| **Home screen widget** with the live mark price | No web equivalent on Android | ✅ on a Seeker |
| **Wallet connection over Mobile Wallet Adapter** | — | ✅ on a Seeker |
| **Liquidation alerts** fired at 15 / 8 / 3 % of distance | A closed tab cannot watch a position while the phone sleeps | ✅ shipped, not yet seen firing |

"Verified on a Seeker" means exactly that: recorded on a physical Solana Seeker and
watched frame by frame, not assumed from the fact that it compiles. That review found
six defects that compiling had not: a locale bug printing `$76 761,00` in an English UI;
an alerts list that pushed the market list off screen; rows re-sorting under the reader's
finger every ten seconds; a placeholder launcher icon; a widget that sat on `waiting…`
for up to a minute after being placed; and — the worst of them — a widget that would have
stayed blank forever, silently, if the monitor happened to be off. All are fixed in the
history.

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

No keystore, no secret, nothing to install but a JDK and the Android SDK.

```bash
git clone https://github.com/ZNGLABS/nexa-mobile
cd nexa-mobile
./gradlew :app:assembleRelease
```

The wrapper fetches Gradle 8.14.5 itself. You need **JDK 17** and **Android SDK
platform 35** (set `sdk.dir` in `local.properties`, or `ANDROID_HOME`).

Measured on 17 September 2026 from a clean clone on a machine that had never built this
project: the command above succeeds and produces an APK of **11 855 258 bytes** — byte
for byte the size CI produced from the same commit, so the two toolchains agree.

That figure belongs to that commit, not to the repository. The APK published on the
`latest` pre-release is whatever the newest commit builds: **12 078 074 bytes** at
`6a45fb8`. What is being claimed here is the agreement between a clean clone and CI at
one commit, not a constant.

> Until that date this repository shipped **without a Gradle wrapper**, and the
> instructions here named Gradle 8.4 and SDK 34 — neither of which builds it. Anyone
> cloning and running the documented command got `./gradlew: No such file or directory`,
> or a failure on `compileSdk 35`. CI never caught it because CI installs Gradle itself
> and never calls the wrapper. A repository that only its own CI can build is not a
> repository someone else can clone and run.

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
| ✅ Markets list, live prices, 24 h change, search | working, device-tested |
| ✅ Price alerts, background service, reboot recovery | working, device-tested |
| ✅ Home screen widget | working, device-tested |
| ✅ Mobile Wallet Adapter connect / disconnect | working, device-tested |
| ✅ Open positions and PnL, decoded from the trader account | working, device-tested |
| ✅ Liquidation price and distance, from the on-chain view | working, device-tested |
| ✅ Liquidation alerts in the background at 15 / 8 / 3 % | shipped, not yet seen firing |

## The liquidation price is the program's own number

The app does not estimate where you get liquidated. Phoenix ships read-only *view*
instructions; the app builds one, **simulates** it — nothing is ever sent — and reads the
program's own computation out of the transaction return data.

Verified on mainnet on 16 September 2026 against a real 0.02 SOL long entered at 97.38
with 0.4817 USDC of collateral:

```
program answer     liquidationPriceTicks 7478  ->  74.78
independent maths                               74.7788
difference                                       0.0012   (tick rounding)
```

The independent check solves *equity = maintenance margin*, with the maintenance ratio
derived from the payload itself — exactly 2.00 % on this market. Entry price was
cross-checked twice as well: 97.38 from the raw account bytes, 97.38 from the program.

Two properties worth stating, because they are the reason to trust a safety threshold:

- **The alert is computed entirely in ticks.** Mark and liquidation arrive in the same
  unit, so no conversion sits between the data and the decision. A unit mistake cannot
  move the threshold. The dollar figure shown on screen derives its tick factor from the
  market itself rather than assuming a convention.
- **When the program does not answer, the app shows nothing and fires nothing** — and
  says why. False reassurance is worse than silence; silent absence is worse than both.

Everything above runs in Kotlin with no SDK: Base58, transaction serialisation and the
56-byte return decoding are implemented here, each verified separately against the
official tooling before being trusted.

## What is not proven yet

One line of the status table above says `shipped, not yet seen firing`, and it stays that
way on purpose. The three liquidation thresholds are in the APK and their input — the
program's own liquidation price — is the number verified above. But **no liquidation
alert has yet been watched firing on a live position**, because seeing one means letting
a real position drift toward liquidation. Until that has been observed on a device, this
README will not claim it.

The same rule applies to everything else here: a row says `device-tested` only when the
behaviour was recorded on a physical Seeker and watched back. Nothing in this repository
is marked working because it compiles.

## About NEXA EXCHANGE

Live at [nexa-exchange.fr](https://nexa-exchange.fr) · on the Solana dApp Store as
`fr.nexaexchange.dex` · 4.6★ from 8 reviews · registered Phoenix Flight builder
(`4w1F9Dzua91TQwgsTKdzxugYYWPTiyThHtJE32xvA9rK`).

Built by ZNG Labs. [@nexa_exchange](https://x.com/nexa_exchange)
