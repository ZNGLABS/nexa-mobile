# How the liquidation price is verified

This app shows a liquidation price. A wrong one is worse than none, so this file records
exactly how the number is obtained and what it was checked against.

Everything below was measured on **Solana mainnet on 16 September 2026**, against a real
position, and can be reproduced by anyone with a Phoenix Flight trader account.

---

## The number is not our estimate

Phoenix ships read-only **view** instructions. The app builds one, **simulates** it, and reads
the program's own answer out of the transaction return data. Nothing is ever signed and nothing
is ever sent.

| | |
|---|---|
| View program | `RiSeVw3ZjNfsaXPRb4mgaqYaEEt41pNNJoDvVh7pgQj` |
| Eternal program | `EtrnLzgbS7nMMy5fbD42kXiUzGg8XQzJ972Xtk1cjWih` |
| Flight proxy | `F1ightu9cujFYo34k9CabifLrJT8qzfDVM2Q7BqhJn2W` |
| Return data | 56 bytes, decoded in Kotlin |

## The position it was checked against

```
side         long
size         0.0200 SOL
entry        97.38
collateral   0.4817 USDC
```

## Result

```
program answer      liquidationPriceTicks 7478   ->  74.78
independent maths                                    74.7788
difference                                            0.0012   (tick rounding)
```

The independent calculation solves *equity = maintenance margin*. The maintenance ratio is not
assumed: it is derived from the payload itself, and comes out at exactly **2.00 %** on this
market.

The entry price was cross-checked twice as well, by two independent paths:

```
97.38   read from the raw trader account bytes
97.38   returned by the program
```

## Two properties that make the threshold trustworthy

**The alert is computed entirely in ticks.** Mark price and liquidation price arrive from the
program in the same unit, so no conversion sits between the data and the decision. A unit
mistake cannot move the threshold. The dollar figure shown on screen derives its tick factor
from the market itself rather than assuming a convention.

**When the program does not answer, the app shows nothing and fires nothing — and says why.**
False reassurance is worse than silence, and silent absence is worse than both.

## Trader account layout

Mapped against the official SDK on a 1 520-byte account:

```
offset 88    collateral
offset 96    flags
```

The version field is **two bytes**. Reading it as one byte shifted status and side by one
position — a real defect, found on device and fixed in commit `5778382`.

## No SDK

Base58, transaction serialisation and the 56-byte return decoding are all implemented in Kotlin
in this repository. Each was verified separately against the official tooling before being
trusted. The app carries no Solana SDK and no secrets of any kind: both Phoenix endpoints it
uses are public and unauthenticated.

## Reproducing it

```bash
git clone https://github.com/ZNGLABS/nexa-mobile
cd nexa-mobile
./gradlew :app:assembleRelease
```

Needs **JDK 17** and **Android SDK platform 35**. Measured on 17 September 2026 from a clean
clone on a machine that had never built this project: the command produces an APK of
**11 855 258 bytes**, the same byte count CI produces — so the two toolchains agree.

Connect a wallet holding a Phoenix Flight position and the liquidation row appears, with the
distance in percent. With no position, the app says so rather than showing a placeholder.
