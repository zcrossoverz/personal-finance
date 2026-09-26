# Audit log

Every milestone was reviewed on a running emulator (Pixel-class, 1080×2400, light and dark), not just compiled.
This is what each pass found and what changed as a result.

## Pass 1 — first full build on device

| Area | Finding | Fix |
|---|---|---|
| Visual | Safe-to-spend amount truncated ("−2,774,0"); "0/day" shown when negative | Row no longer splits width; negative state reads "Over by X" with a one-line explanation |
| One-handed | Floating capture button covered list amounts and the card "Pay" button | Capture moved into the centre of the bottom bar |
| Duplication | Pace shown twice on Home (card + alert) | Pace alert removed; the card owns pace |
| Copy | "14 days to lương" | "14 days to payday" |
| Visual | Content scrolled under the transparent status bar | Translucent status-bar scrim |
| Density | Home "Recent" 12 rows — Home is operational, not a history | 8 rows + "All activity" |
| Data realism | Demo card debt 13.9m, 42 % over pace, negative safe-to-spend, savings account negative in history | Demo re-derived from measured per-account flows: 28m salary, 7m monthly savings transfer, ~8m card debt; test asserts no account ever goes negative and safe-to-spend is positive |
| Charts | Money-flow labels overlapped for small categories; ribbons from a second income source crossed everything | Every destination gets a ≥ 40dp label slot; a single income node, sources listed underneath |
| Visual | Currency symbol as large as the digits | "₫" rendered at 55 % size, muted — digits are the hero |
| Onboarding | Welcome CTAs mid-screen | Pinned to the bottom thumb zone |

## Pass 2 — fast-entry path

| Finding | Fix |
|---|---|
| Long-press quick-amount popup covered its own tile and had no depth | Custom position provider: above the tile, centred, clamped to the window; soft elevation |
| Composer and transfers had no learned amounts (Scenario D = 6 taps) | Learned amounts by category, and by route for transfers (MB → VCB → `5m` chip): Scenario D = 4 taps |
| Composer header said "Expense" with a placeholder icon before a category was chosen | "Choose a category" |
| "Skip" on a bill was a 24dp tag | Full-size "Skip this time" chip |
| Savings accounts cluttered the expense account row | Hidden there (still available for transfers) |
| Picker rows opened scrolled to the start, hiding the selected account/category | Rows open with the current choice in view |

## Pass 3 — correctness and honesty

| Finding | Fix |
|---|---|
| Search "shopee" missed the split order and the refund (category match only) | Category words also match notes/merchants; regression test added |
| "MacBook overdue" after the demo start date moved | Demo plan uses `prepaidPeriods`; test asserts no overdue items and 7/12 paid |
| Auto-pay installment still offered "Record payment" — a double-count trap | Button only for manual plans; auto plans state when the next payment posts |
| Insight "Giải trí +200 %" (660k vs 220k) outranked real signals | Category insights need ≥ 15 % **and** ≥ 500k difference, ranked by absolute gap |
| Forecast warned in red when only the *estimate* dipped below zero | Red only if scheduled items alone go negative; estimate dips are caution-coloured and labelled "Estimate, not a fact" |
| Forecast axis ticks at −5m / 15m / 35m | One step size across the range; gridlines on round multiples |
| Forecast headline showed the balance 45 days out | Headline is the lowest scheduled balance (the "will I make it?" number) |
| Overdue items appeared both as an alert and in "Next 7 days" | Overdue lives in the list (red badge); alerts only for what the list can't show |
| Reminders toggle showed "on" without notification permission | Reflects the real permission; onboarding asks once, in context |
| Empty states: all-zero 6-month chart and flat forecast | Hidden until there is data |

Verified on device: save → Undo fully restores balances; paying a variable bill advances its schedule and learns the
new amount (720k → 685k, next due moves to 27 Oct); notification deep link opens that bill's amount entry.

## Pass 4 — Vietnamese by default, design v2

Goal: make Vietnamese the default language and move away from a look that read as a generic, AI-generated template.

| Area | Finding | Change |
|---|---|---|
| Visual language | Indigo accent, pastel icon tiles, coloured pills, uppercase letter-spaced headers, coloured side bars on insight cards — each common in templates, together unmistakable | Ink/paper palette, one interactive colour, neutral icon wells with coloured glyphs, dot+caption tags, sentence-case headers, insights as a divided list |
| Typography | System Roboto, no personality, cramped Vietnamese diacritics | Be Vietnam Pro bundled; generous line heights |
| Numbers | Rolling counter would jitter with the new proportional digits | Directional slide on change |
| Credit cards | Tinted gradient panel | Rendered as a dark card object; due date promoted when close |
| Keypad | Grey tiles | Borderless keys, ink Save |
| Bottom bar | Material pill indicator | Outlined → filled icon, ink capture key |
| Charts | Category hues in money-flow ribbons and bars | Monochrome; colour only for kept/debt/estimate |
| Language | English only | `tr()` + complete Vietnamese table (default) + English setting; localized money (`65.000 ₫`, `8,4tr`) and dates (`T7, 26/09`) |
| Language switch | Bottom bar kept the old language (composables that don't read changed state aren't recomposed) | UI tree keyed on language; navigation moved into the ViewModel so the user stays on the same screen |
| Copy | Seed names "Subscription"/"Shopping" in a Vietnamese UI; heatmap weekday letters in English; pace line wrapping to two lines | "Gói đăng ký"/"Mua sắm"; T2…CN; shorter pace copy |
| Correctness | "Largest upcoming payment" picked a 7tr transfer to the user's own savings | Transfers excluded — only bills, subscriptions, installments and card dues are payments |
| Search | Vietnamese phrases split into words and missed ("ăn uống tuần này", "hoàn tiền") | Multi-word category names matched first; Vietnamese keywords for refunds, subscriptions, transfers, income |

`I18nTest` scans the sources: the build fails if any `tr()` string has no Vietnamese entry, or if a translation
changes the number of format arguments. It also pins the Vietnamese number and date formats.

Reviewed on device in Vietnamese and English, light and dark: Home, entry sheet, safe-to-spend breakdown, Plan,
Money, credit card, Insights (all sections), transaction detail, Settings, onboarding.

## Financial correctness (unit tests, `LedgerTest` 15 + `I18nTest` 3)

Transfers zero-sum and excluded from income/expense · card purchase + payment counted once · refund offsets, never
income · reimbursement reduces the category, not income · split lines drive analytics; refunds of splits allocate
proportionally · card statement = debt at close − credits · installment progress, remainder as liability, period
amounts sum to total · card-charged subscription appears once in the forecast (inside the card statement estimate) ·
safe-to-spend formula · recurrence keeps its anchor day (31 → 28 → 31) · money formatting/parsing · command parser ·
search parsing · demo-data invariants (no future transactions, splits sum, no negative history, no overdue items).

## Performance

Release build (R8): 2.9 MB APK including ~540 KB of bundled fonts, cold start ≈ 0.6 s on the emulator. All financial computation runs on
`Dispatchers.Default`; Insights computes per month asynchronously. Emulator frame statistics are dominated by
"slow issue draw commands" (GL translation) rather than UI-thread work, so they are not a reliable signal —
profile on a physical phone before optimising further (a Baseline Profile would be the first step).

## Overengineering check

One Gradle module; no DI framework, no use-case classes, no repository interfaces, no backend. The only
"architecture" is the split between pure `domain/` maths (tested) and Compose `ui/`. Charts are ~500 lines of Canvas
instead of a chart library because readability, scrubbing and dark mode needed direct control.

## Known limitations / next steps

- App lock couldn't be exercised on the emulator (no enrolled credential); the toggle is disabled with an explanation
  when the device has no screen lock.
- Single currency, whole units only (fine for VND; USD/EUR cents are not supported).
- One flexible-spending budget, no per-category budgets.
- No home-screen widget yet — launcher shortcuts cover the top 3 presets plus "Quick command" and "Upcoming".
- Preset reordering uses up/down controls rather than drag-and-drop.
- Room schema version 1 with schema export on; migrations will be needed with the first schema change.
- Category and preset names are user data: seeded in Vietnamese and not translated when switching to English.
- A recurring *transfer* into a credit card would overlap with the card's statement estimate in the forecast; use
  the card due instead (card payments are always derived from the statement).

## Build & release

GitHub Actions (`.github/workflows/android.yml`) runs the unit tests and builds debug + release APKs on every push
and pull request; `v*` tags publish a GitHub Release with the signed release APK. Release signing comes from
repository secrets (keystore kept outside the repo); v1.0.0 was verified to be signed with the release certificate.
