# Ledger — Product & UX Definition

Personal, single-user, offline-first Android money app. This document was written **before** implementation
and is the source of truth for what the app is, how it is organised and how it behaves.

---

## 1. Audit of the brief (senior product designer + senior mobile engineer)

**What actually matters.** The brief lists ~30 features, but daily value comes from three loops:

| Loop | Frequency | What must be true |
|---|---|---|
| Capture a normal expense | 3–10× / day | ≤ 3 interactions, zero forms, zero confirmations, never blocked by animation, lock, or network |
| "Am I OK?" glance | 1–3× / day | Cash on hand, safe-to-spend and the next obligations visible on launch without a tap |
| Weekly / monthly review | 1× / week | Honest analytics that answer specific questions, with drill-down to the transactions |

Everything else (subscriptions, installments, cards, forecast, net worth) exists to make the second and third
loops **correct**. They are rarely edited but constantly read.

**Risks identified up front**

1. *Feature sprawl pushes capture off the home screen.* → Home is operational only: no charts on Home.
2. *Presets that reorder themselves destroy muscle memory.* → Pinned presets never move; only unpinned ones adapt.
3. *Double counting* (card purchase + card payment, refunds as income, transfers as expense, installment purchase +
   installment payments). → A single ledger rule table (§8) that every calculation goes through, with unit tests.
4. *Safe-to-spend that lies.* → Always show the formula, and a visible "confidence" note when inputs are missing.
5. *Forecast presented as fact.* → Three visual grammars: solid = recorded, solid-light = scheduled, dashed = estimate.
6. *Biometric lock adds a tap to every capture.* → Optional, with a grace window; off by default.
7. *Over-engineering.* → One Gradle module, Room + pure Kotlin calculators, no DI framework, no backend.

---

## 2. Product principles (in priority order, from the brief)

1. Fast daily interaction  2. Clear financial information  3. Beautiful, modern UX  4. Useful insight
5. Smooth navigation/animation  6. Flexible advanced features  7. Technical sophistication

Derived rules used to settle every design argument:

* **The number is the hero.** Labels are small and quiet; amounts are large, tabular, and aligned.
* **One accent colour.** Colour carries meaning (liability, overdue, estimate, income) — never decoration.
* **Sheets, not screens.** Anything that is an action on one object opens a bottom sheet over its context.
* **Undo, not "Are you sure?".** Every destructive or fast action is reversible for 5 s.
* **Facts, estimates and forecasts look different.** Tagged `Fact`, `Estimate`, `Forecast` in insights.
* **Thumb first.** Frequent actions live in the lower 2/3 of the screen.

---

## 3. Information architecture

Four destinations in a bottom bar, mirroring the product modules (CAPTURE is on Home, not a tab):

```
┌ Home ──────────── state + capture (operational, no charts)
│   Cash on hand · Spent this month · Safe to spend
│   Quick presets (grid)               ← the defining interaction
│   Alerts (only when something needs attention)
│   Next 7 days of obligations
│   Recent transactions → Activity
│
├ Money ─────────── where money lives
│   Net worth (assets vs liabilities, timeline)
│   Accounts grouped: Everyday · Savings · Credit cards
│   Account detail (history, transactions, card statement, "Pay card")
│   Activity (all transactions + search)
│
├ Plan ──────────── what is coming
│   Safe to spend (formula + assumptions)
│   Cash-flow forecast (45 days)
│   Obligation timeline (bills, subscriptions, installments, card dues, salary)
│   Subscriptions · Installments · Bills (lists + detail)
│
└ Insights ──────── what happened
    Month cash flow (income / expenses / saved)
    Spending pace (cumulative vs last month vs 3-mo average)
    Deterministic insights (Fact / Estimate / Forecast)
    Categories → subcategories → transactions
    Fixed vs flexible · Calendar heatmap · Money flow · 6-month comparison
```

Global: **Capture button** in the centre of the bottom bar (every tab) opens the Composer for income, transfers and
command entry. It started as a floating button; the first audit showed it covering amounts and the card "Pay" button,
so it moved into the bar where it is always in thumb reach and never overlaps content. Settings (gear on Home) holds presets, accounts, safe-to-spend config, security, backup.

Why not the brief's "Home / Analytics / Obligations / Accounts"? Same shape, but *Plan* merges obligations with
safe-to-spend and the forecast — they answer the same question ("what's coming and can I afford it?"), and the
Activity list moves under Money because transactions belong to accounts.

---

## 4. Interaction model

| Gesture | Meaning |
|---|---|
| Tap preset | Open Quick Entry for that preset (numpad focused, account preselected) |
| Long-press preset | Pop quick amounts (learned from history) → tapping one **saves immediately** with Undo |
| Tap capture button | Composer: Expense / Income / Transfer, any category, split, command line |
| Tap transaction | Detail sheet: edit, duplicate, refund, split, change account/category, note, delete |
| Swipe transaction ← | Delete (Undo snackbar) |
| Swipe transaction → | Duplicate as today |
| Tap obligation | Pay / enter amount sheet (1 tap to confirm a fixed amount) |
| Drag on chart | Scrub; value callout follows the finger, haptic tick on each data point |
| Tap heatmap day / bar | Drill into that day / month / category |

Quick Entry surface: a custom numpad (no system keyboard, no layout jump) with `000` key, learned amount chips,
an account chip row and a date chip (Today / Yesterday / pick). Save is the largest key, in the thumb corner.

---

## 5. Key flows & interaction budget (measured on device — see README for the final table)

| Scenario | Path | Interactions |
|---|---|---|
| A. 55k lunch, default bank | Tap *Ăn uống* → tap `55k` chip → Save | **3** (or 2: long-press → `55k`) |
| B. 100k fuel | Tap *Xăng xe* → tap `100k` chip → Save | **3** |
| C. Shopee on credit card | Tap *Shopee* (preset remembers VPBank card) → type amount → Save | **3** + digits |
| D. 5m transfer MB → VCB | Capture → Transfer → pick VCB → 5,000,000 → Save; or a transfer preset: 3 | 5 / **3** |
| E. Electricity from reminder | Tap notification → type amount → Save | **2** + digits |
| F. Next month's mandatory payments | Plan tab → "Next month" section (total shown in header) | **1–2** |
| G. Safe to spend until salary | Visible on Home at launch; tap for breakdown | **0** / 1 |

---

## 6. Screen hierarchy (Home, top → bottom)

1. Top row: month · privacy toggle · settings (rare actions only in the top corners)
2. **Cash on hand** hero (display size) · card debt · spent this month vs pace
3. **Safe to spend** strip: amount · per-day · days to salary · confidence
4. **Quick presets** 4-column grid (pinned first, then adaptive)
5. Alerts (overdue bill, card due ≤ 5 days, spending pace > 15 % ahead) — hidden when empty
6. Next 7 days (max 4 rows) → Plan
7. Recent transactions (Today / Yesterday groups) → Activity

---

## 7. Design system

Three layers (primitive → semantic → component), implemented in `ui/theme`.

**Colour (semantic)**

| Token | Light | Dark | Use |
|---|---|---|---|
| bg | `#F5F6F8` | `#0A0C0F` | window |
| surface | `#FFFFFF` | `#13161B` | cards |
| surfaceAlt | `#EEF0F3` | `#1A1E24` | chips, keys, inset areas |
| hairline | `#E3E6EA` | `#23282F` | 1dp dividers (light mode only on cards) |
| text | `#0D1015` | `#F1F3F6` | primary |
| textMuted | `#5A6371` | `#9BA3AF` | labels (≥ 4.5:1) |
| textFaint | `#8B94A1` | `#6B7380` | tertiary, axis |
| accent | `#3D5AFE`→`#4263EB` | `#7B93FF` | the one brand colour: CTAs, selection, primary series |
| positive | `#0F9D6B` | `#3DD68C` | income, under pace |
| negative | `#D93A3F` | `#FF6B6E` | liabilities, overdue, over pace |
| caution | `#B7791F` | `#F2B55A` | due soon, estimates |

Expenses are **not** red — spending is normal. Red is reserved for debt and problems.
Category identity uses 8 muted hues at 14 % tint behind the icon; charts use accent + neutral, not rainbows.

**Type** — Inter (downloadable, falls back to system sans), tabular figures for every amount.

| Role | Size / weight | Use |
|---|---|---|
| hero | 40 / SemiBold, -2 % tracking | cash on hand, quick-entry amount |
| display | 30 / SemiBold | screen hero numbers |
| title | 20 / SemiBold | screen titles |
| headline | 17 / SemiBold | card titles, list amounts |
| body | 15 / Regular | text |
| label | 13 / Medium | chips, meta |
| caption | 12 / Medium, +2 % tracking | section labels, axis (never smaller) |

**Space** 4 · 8 · 12 · 16 · 20 · 24 · 32 · 40. Page gutter 20 dp. Card padding 16–20 dp.
**Shape** card 22 dp · tile 18 dp · chip 12 dp · sheet 28 dp top · pill full.
**Elevation** none in dark mode (tonal steps only); light mode cards: hairline + 1 dp soft shadow.
**Motion** tap feedback ≤ 100 ms · micro 160 ms · standard 240 ms · emphasized spring (damping 0.85).
Save → sheet dismiss 180 ms, row slides in 240 ms, totals roll 400 ms; none block input.
**Touch** ≥ 48 dp targets, numpad keys 64 dp tall.
**Icons** one family (Material Symbols Rounded), 22 dp in lists, 24 dp in tiles.

**Number format** `65,000 ₫` in full; compact `65k`, `8.4m`, `1.42m`, `1.2b` (≤ 3 significant digits).

---

## 8. Ledger semantics (the rules that prevent double counting)

All money is `Long` in the currency's minor unit (đồng for VND). Every transaction has a positive `amount`,
except `ADJUSTMENT`, whose amount is a signed delta.

| Type | Balance effect | Counts as expense | Counts as income |
|---|---|---|---|
| EXPENSE | account −a (card: debt +a) | **+a** in its category (or split lines) | – |
| INCOME | account +a | – | **+a** |
| TRANSFER | from −a, to +a (incl. paying a card) | – | – |
| REFUND | account +a | **−a** in the original category | – |
| REIMBURSEMENT | account +a | **−a** in the original category | – |
| ADJUSTMENT | account ±a | – | – |

* Credit-card purchase = EXPENSE on the card at purchase time. Paying the bill = TRANSFER bank → card.
* Statement balance = debt at the last statement close − payments since, floored at 0. Due = next due day.
* Installments: each payment is an EXPENSE (fixed spending) when it happens; the unpaid remainder is a
  liability in net worth. The original purchase is **not** recorded as an expense (no double count).
* Split transactions: analytics use split lines; the lines must sum to the parent amount.
* Refunds/reimbursements offset spending in the period they arrive (cash basis), never inflate income.
* Net worth = Σ asset balances − Σ card debt − remaining installment debt. Cash flow = income − expenses.
  They are shown in different places and never mixed.

Invariants (enforced by unit tests in `LedgerTest`): Σ balance changes of a transfer = 0; paying a card changes
neither income nor expense; a refund never increases income; split lines sum to the parent; card purchase +
payment counts the expense exactly once; forecast never includes an obligation twice.

---

## 9. Safe to spend

```
Spendable cash      Σ everyday accounts (cash, bank, e-wallet) marked "counts toward safe-to-spend"
− Card dues         statement balances due before next salary
− Obligations       bills + subscriptions + installments due before next salary (variable = estimate)
− Protected         protected savings + emergency reserve (configurable)
= Safe to spend     ÷ days until next salary = per-day
```

Confidence notes appear when: no salary date, variable bills estimated, a card has no statement day, or no
transactions in 14 days.

---

## 10. Implementation shape

Single `app` module · Kotlin · Jetpack Compose · Room · kotlinx-serialization (backup) · WorkManager
(daily reminder check) · androidx.biometric. A tiny `AppContainer` instead of DI. All financial maths lives in
pure Kotlin (`domain/`) operating on an in-memory snapshot of the database — a personal ledger is thousands of
rows, so this is fast and makes every rule unit-testable.
