package dev.personal.ledger.data

import dev.personal.ledger.data.Seeds.CAFE
import dev.personal.ledger.data.Seeds.CLOTHES
import dev.personal.ledger.data.Seeds.DINNER
import dev.personal.ledger.data.Seeds.ELECTRONICS
import dev.personal.ledger.data.Seeds.FAMILY
import dev.personal.ledger.data.Seeds.FUEL
import dev.personal.ledger.data.Seeds.FUN
import dev.personal.ledger.data.Seeds.GIFTS
import dev.personal.ledger.data.Seeds.GROCERY
import dev.personal.ledger.data.Seeds.HEALTH
import dev.personal.ledger.data.Seeds.HOUSEHOLD
import dev.personal.ledger.data.Seeds.INSTALLMENTS
import dev.personal.ledger.data.Seeds.INTERNET
import dev.personal.ledger.data.Seeds.LUNCH
import dev.personal.ledger.data.Seeds.ONLINE
import dev.personal.ledger.data.Seeds.PARKING
import dev.personal.ledger.data.Seeds.PHONE
import dev.personal.ledger.data.Seeds.POWER
import dev.personal.ledger.data.Seeds.RENT
import dev.personal.ledger.data.Seeds.SALARY
import dev.personal.ledger.data.Seeds.SUBS
import dev.personal.ledger.data.Seeds.TAXI
import dev.personal.ledger.data.Seeds.WATER
import dev.personal.ledger.domain.Cards
import dev.personal.ledger.domain.Installments
import dev.personal.ledger.domain.Ledger
import dev.personal.ledger.domain.Recurrence
import dev.personal.ledger.domain.atMillis
import dev.personal.ledger.domain.dayClamped
import dev.personal.ledger.domain.endMillis
import dev.personal.ledger.domain.isWeekend
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import kotlin.random.Random

/**
 * A realistic 7-month history for one person in Hà Nội, generated relative to [now] so every screen has
 * something meaningful to show: salary, rent, bills, subscriptions on a card, two installment plans,
 * a refund, a reimbursed group dinner, a split Shopee order and gradually rising online shopping.
 */
object DemoData {
    const val CASH = 1L; const val MB = 2L; const val VCB = 3L; const val MOMO = 4L; const val SAVINGS = 5L; const val VPB = 6L

    fun build(now: LocalDateTime = LocalDateTime.now()): LedgerData {
        val today = now.toLocalDate()
        val rnd = Random(20260926)
        // History starts on a pay day so the first days are not an artificial overdraft.
        val start = YearMonth.from(today).minusMonths(6).atDay(10)
        val tx = ArrayList<Txn>()
        var nextId = 1L
        fun add(type: TxType, amount: Long, account: Long, date: LocalDate, time: LocalTime, category: Long? = null, note: String = "",
                to: Long? = null, preset: Long? = null, recurring: Long? = null, installment: Long? = null, linked: Long? = null): Long {
            val millis = date.atMillis(time)
            if (millis > now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()) return -1
            val id = nextId++
            tx += Txn(id, type, amount, account, to, category, millis, note, linked, preset, recurring, installment, millis)
            return id
        }
        fun pick(vararg v: Long) = v[rnd.nextInt(v.size)]
        fun t(h1: Int, h2: Int) = LocalTime.of(rnd.nextInt(h1, h2), rnd.nextInt(0, 60))

        val accounts = listOf(
            Account(CASH, "Tiền mặt", AccountType.CASH, "cash,tm,tienmat", colorIndex = 1, sortOrder = 3),
            Account(MB, "MB Bank", AccountType.BANK, "mb,mbbank", colorIndex = 2, sortOrder = 0),
            Account(VCB, "Vietcombank", AccountType.BANK, "vcb,vietcombank", colorIndex = 0, sortOrder = 1),
            Account(MOMO, "MoMo", AccountType.EWALLET, "momo", colorIndex = 4, sortOrder = 2),
            Account(SAVINGS, "Tiết kiệm VCB", AccountType.SAVINGS, "tk,saving,tietkiem", colorIndex = 6, spendable = false, sortOrder = 4),
            Account(VPB, "VPBank Credit", AccountType.CREDIT_CARD, "vp,vpbank,visa", colorIndex = 5, creditLimit = 30_000_000,
                statementDay = 20, dueDay = 5, spendable = false, sortOrder = 5),
        )

        // ---- schedules ----
        fun anchor(day: Int): Pair<Long, Int> {
            // First occurrence strictly after now's date for the anchor (history is generated separately).
            var d = YearMonth.from(today).dayClamped(day)
            if (!d.isAfter(today)) d = YearMonth.from(today).plusMonths(1).dayClamped(day)
            return d.toEpochDay() to day
        }
        fun rec(id: Long, kind: RecurringKind, name: String, amount: Long, day: Int, account: Long, category: Long?, icon: String, color: Int,
                variable: Boolean = false, autoPay: Boolean = false, salary: Boolean = false, to: Long? = null, cadence: Cadence = Cadence.MONTHLY, dueOverride: LocalDate? = null): Recurring {
            val (next, a) = anchor(day)
            return Recurring(id, kind, name, amount, variable, cadence, dueOverride?.toEpochDay() ?: next, a, account, to, category, autoPay, true, salary, icon, color)
        }
        val recurring = mutableListOf(
            rec(1, RecurringKind.INCOME, "Lương", 28_000_000, 10, MB, SALARY, "salary", 0, autoPay = true, salary = true),
            rec(2, RecurringKind.BILL, "Tiền trọ", 4_500_000, 30, MB, RENT, "home", 7),
            rec(3, RecurringKind.BILL, "Điện EVN", 720_000, 27, MB, POWER, "bolt", 7, variable = true),
            rec(4, RecurringKind.BILL, "Nước", 120_000, 29, MOMO, WATER, "water", 7, variable = true),
            rec(5, RecurringKind.BILL, "Internet FPT", 250_000, 2, MB, INTERNET, "wifi", 7, autoPay = true),
            rec(6, RecurringKind.BILL, "Viettel 4G", 99_000, 15, MOMO, PHONE, "phone", 7, autoPay = true),
            rec(7, RecurringKind.SUBSCRIPTION, "Netflix", 260_000, 28, VPB, SUBS, "tv", 5, autoPay = true),
            rec(8, RecurringKind.SUBSCRIPTION, "Spotify", 59_000, 12, VPB, SUBS, "music", 0, autoPay = true),
            rec(9, RecurringKind.SUBSCRIPTION, "ChatGPT Plus", 520_000, 4, VPB, SUBS, "sparkle", 0, autoPay = true),
            rec(10, RecurringKind.SUBSCRIPTION, "iCloud+", 59_000, 1, MOMO, SUBS, "cloud", 2, autoPay = true),
            rec(11, RecurringKind.SUBSCRIPTION, "Google One", 45_000, 18, VPB, SUBS, "cloud", 3, autoPay = true),
            rec(12, RecurringKind.SUBSCRIPTION, "YouTube Premium", 79_000, 22, VPB, SUBS, "play", 5, autoPay = true),
            rec(13, RecurringKind.SUBSCRIPTION, "California Fitness", 450_000, 6, MB, SUBS, "gym", 4, autoPay = true),
            rec(14, RecurringKind.TRANSFER, "Tiết kiệm hàng tháng", 7_000_000, 11, MB, null, "savings", 6, autoPay = true, to = SAVINGS),
            rec(15, RecurringKind.BILL, "Gửi bố mẹ", 2_000_000, 12, MB, FAMILY, "family", 4, autoPay = true),
        )
        val priceChanges = mutableListOf<PriceChange>()
        recurring.forEach { priceChanges += PriceChange(0, it.id, it.amount, start.toEpochDay()) }
        // Netflix raised its price three months ago.
        priceChanges.removeAll { it.recurringId == 7L }
        priceChanges += PriceChange(0, 7, 220_000, start.toEpochDay())
        val netflixRaise = YearMonth.from(today).minusMonths(3).atDay(1)
        priceChanges += PriceChange(0, 7, 260_000, netflixRaise.toEpochDay())

        val installments = listOf(
            Installment(1, "MacBook Air M4", 24_000_000, 0, 0, 12, YearMonth.from(today).minusMonths(6).dayClamped(5).toEpochDay(), VPB, INSTALLMENTS, 1, true, InstallmentStatus.ACTIVE, "laptop", 5, "0% qua thẻ VPBank"),
            Installment(2, "iPhone 16", 11_760_000, 0, 480_000, 6, YearMonth.from(today).minusMonths(2).dayClamped(15).toEpochDay(), MB, INSTALLMENTS, 0, true, InstallmentStatus.ACTIVE, "smartphone", 2, "Home Credit"),
        )

        // ---- history ----
        var d = start
        var monthIndex = 0
        var lastMonth = YearMonth.from(start)
        while (!d.isAfter(today)) {
            val ym = YearMonth.from(d)
            if (ym != lastMonth) { monthIndex++; lastMonth = ym }
            val weekend = d.isWeekend
            val dom = d.dayOfMonth

            // Salary & schedules (history for each recurring on its anchor day).
            recurring.forEach { r ->
                if (dom != ym.dayClamped(r.anchorDay).dayOfMonth) return@forEach
                val amount = when (r.id) {
                    3L -> if (ym.monthValue in 5..8) pick(890_000, 960_000, 1_080_000) else pick(610_000, 680_000, 720_000)
                    4L -> pick(105_000, 118_000, 132_000)
                    7L -> if (d.isBefore(netflixRaise)) 220_000 else 260_000
                    else -> r.amount
                }
                val type = when (r.kind) { RecurringKind.INCOME -> TxType.INCOME; RecurringKind.TRANSFER -> TxType.TRANSFER; else -> TxType.EXPENSE }
                val time = if (r.kind == RecurringKind.INCOME || r.autoPay) LocalTime.of(8, 0) else t(19, 22)
                // Leave this month's variable bills and rent open so the reminder flow is visible.
                if (!d.isBefore(today.minusDays(0)) && !r.autoPay) return@forEach
                add(type, amount, r.accountId, d, time, r.categoryId, r.name, r.toAccountId, recurring = r.id)
            }

            // Installment payments on their due day.
            installments.forEach { p ->
                val first = LocalDate.ofEpochDay(p.firstDue)
                for (i in p.prepaidPeriods until p.periods) if (Installments.dueOf(first, i) == d)
                    add(TxType.EXPENSE, p.periodAmount(i), p.accountId, d, LocalTime.of(8, 0), p.categoryId, "${p.name} · ${i + 1}/${p.periods}", installment = p.id)
            }

            // Everyday life.
            if (!weekend && rnd.nextFloat() < 0.85f) add(TxType.EXPENSE, pick(35_000, 40_000, 45_000, 45_000, 50_000, 55_000, 55_000, 65_000), if (rnd.nextFloat() < 0.7f) MB else CASH, d, t(11, 13), LUNCH, preset = 1)
            if (rnd.nextFloat() < (if (weekend) 0.8f else 0.6f)) add(TxType.EXPENSE, pick(29_000, 35_000, 45_000, 55_000, 65_000), MOMO, d, if (rnd.nextBoolean()) t(8, 10) else t(14, 16), CAFE, preset = 2)
            if (weekend) {
                if (rnd.nextFloat() < 0.75f) add(TxType.EXPENSE, pick(150_000, 220_000, 280_000, 360_000), if (rnd.nextFloat() < 0.3f) VPB else MB, d, t(18, 21), DINNER, preset = 1)
            } else if (rnd.nextFloat() < 0.45f) add(TxType.EXPENSE, pick(45_000, 60_000, 80_000, 120_000), CASH, d, t(18, 21), DINNER, preset = 1)
            if (rnd.nextFloat() < 0.28f) add(TxType.EXPENSE, pick(25_000, 38_000, 52_000, 67_000), MOMO, d, t(7, 22), TAXI, preset = 3)
            if (dom % 6 == 1) add(TxType.EXPENSE, pick(70_000, 80_000, 100_000), CASH, d, t(7, 9), FUEL, preset = 4)
            if (!weekend && rnd.nextFloat() < 0.15f) add(TxType.EXPENSE, 5_000, CASH, d, t(8, 9), PARKING)
            if (d.dayOfWeek.value == 3 || d.dayOfWeek.value == 7) add(TxType.EXPENSE, pick(160_000, 220_000, 290_000, 380_000), MB, d, t(17, 19), GROCERY, preset = 7)
            // Online shopping drifts upward month after month.
            if (rnd.nextFloat() < 0.10f + monthIndex * 0.012f) add(TxType.EXPENSE, pick(89_000, 159_000, 249_000, 390_000) + monthIndex * 15_000L, VPB, d, t(21, 23), ONLINE, preset = 5)
            if (rnd.nextFloat() < 0.025f) add(TxType.EXPENSE, pick(350_000, 590_000, 790_000), VPB, d, t(15, 20), CLOTHES, preset = 6)
            if (weekend && rnd.nextFloat() < 0.25f) add(TxType.EXPENSE, pick(180_000, 240_000, 300_000), MOMO, d, t(19, 21), FUN, "CGV", preset = 14)
            if (dom == 17) add(TxType.EXPENSE, pick(120_000, 260_000, 410_000), CASH, d, t(10, 18), HEALTH, "Nhà thuốc Long Châu")

            // Money movement between own accounts.
            if (dom == 1) add(TxType.TRANSFER, 1_800_000, MB, d, LocalTime.of(12, 5), note = "Rút tiền ATM", to = CASH)
            if (dom == 9 || dom == 24) add(TxType.TRANSFER, 1_000_000, MB, d, LocalTime.of(9, 15), note = "Nạp MoMo", to = MOMO)
            d = d.plusDays(1)
        }

        // Specific stories.
        val lastM = YearMonth.from(today).minusMonths(1)
        val shopeeOrder = add(TxType.EXPENSE, 700_000, VPB, lastM.atDay(8), LocalTime.of(22, 10), ONLINE, "Shopee · nồi chiên", preset = 5)
        add(TxType.REFUND, 700_000, VPB, lastM.atDay(19), LocalTime.of(10, 40), ONLINE, "Hoàn tiền Shopee", linked = shopeeOrder)
        val split = add(TxType.EXPENSE, 2_000_000, VPB, today.minusDays(4), LocalTime.of(21, 30), null, "Shopee · tai nghe + đồ nhà", preset = 5)
        val splits = listOf(Split(1, split, ELECTRONICS, 1_500_000, "Tai nghe Sony"), Split(2, split, HOUSEHOLD, 500_000, "Đồ gia dụng"))
        val dinner = add(TxType.EXPENSE, 1_600_000, MB, today.minusDays(6), LocalTime.of(20, 15), DINNER, "Lẩu sinh nhật Minh (4 người)")
        add(TxType.REIMBURSEMENT, 1_200_000, MB, today.minusDays(5), LocalTime.of(9, 5), DINNER, "Bạn bè chuyển lại tiền lẩu", linked = dinner)
        add(TxType.TRANSFER, 5_000_000, MB, today.minusDays(12), LocalTime.of(10, 0), note = "Chuyển sang VCB", to = VCB)
        add(TxType.EXPENSE, 450_000, MB, today.minusDays(3), LocalTime.of(19, 0), GIFTS, "Quà sinh nhật mẹ")

        // Card statements are paid in full two days before each due date, computed on the history built so far.
        val card = accounts.first { it.id == VPB }
        var close = YearMonth.from(start).dayClamped(20)
        while (true) {
            val due = Cards.dueFor(close, 20, 5).minusDays(2)
            if (due.isAfter(today) || !due.isAfter(close)) break
            val snapshot = LedgerData(accounts = accounts, transactions = tx, splits = splits)
            val debt = -(Ledger.balances(snapshot, close.endMillis())[card.id] ?: 0)
            val credits = tx.filter { it.date > close.endMillis() && it.date < due.atMillis(LocalTime.MIN) && ((it.type == TxType.TRANSFER && it.toAccountId == VPB) || (it.type.offsetsExpense && it.accountId == VPB)) }.sumOf { it.amount }
            val owe = debt - credits
            if (owe > 0) add(TxType.TRANSFER, owe, MB, due, LocalTime.of(20, 30), note = "Thanh toán thẻ VPBank", to = VPB)
            close = YearMonth.from(close).plusMonths(1).dayClamped(20)
        }

        // Opening balances chosen so today's balances look like the brief's example, then raised where needed so no
        // everyday or savings account ever dips below a small floor in the generated history.
        val targets = mapOf(CASH to 820_000L, MB to 12_400_000L, VCB to 5_700_000L, MOMO to 640_000L, SAVINGS to 60_000_000L)
        val zeroed = accounts.map { it.copy(openingBalance = 0) }
        val flows = Ledger.balances(LedgerData(accounts = zeroed, transactions = tx))
        val sorted = tx.sortedBy { it.date }
        val finalAccounts = accounts.map { a ->
            val target = targets[a.id] ?: return@map a
            var opening = target - (flows[a.id] ?: 0)
            var running = 0L; var lowest = 0L
            sorted.forEach { t -> running += Ledger.delta(t, a.id); lowest = minOf(lowest, running) }
            val floor = if (a.type == AccountType.CASH || a.type == AccountType.EWALLET) 50_000L else 500_000L
            if (opening + lowest < floor) opening = floor - lowest
            a.copy(openingBalance = opening)
        }

        // Recurring schedules: next due after the last posted occurrence.
        val finalRecurring = recurring.map { r ->
            val last = tx.filter { it.recurringId == r.id }.maxOfOrNull { it.date }
            if (last == null) r else {
                val lastDue = java.time.Instant.ofEpochMilli(last).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                r.copy(nextDue = Recurrence.next(lastDue, r.cadence, r.anchorDay).toEpochDay())
            }
        }

        return LedgerData(
            accounts = finalAccounts,
            categories = Seeds.categories,
            transactions = tx.sortedByDescending { it.date },
            splits = splits,
            presets = Seeds.presets().map { if (it.id == 5L) it.copy(accountId = VPB) else it },
            recurring = finalRecurring,
            priceChanges = priceChanges.mapIndexed { i, p -> p.copy(id = i + 1L) },
            installments = installments,
        )
    }

    // Demo data is fictional, so the screen is not protected: it can be screenshotted for review.
    fun settings() = Settings(onboarded = true, defaultAccountId = MB, protectedSavings = 2_000_000, emergencyReserve = 1_000_000, demo = true, secureScreen = false)
}
