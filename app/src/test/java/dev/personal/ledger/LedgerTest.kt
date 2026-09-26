package dev.personal.ledger

import dev.personal.ledger.data.Account
import dev.personal.ledger.data.AccountType
import dev.personal.ledger.data.Cadence
import dev.personal.ledger.data.DemoData
import dev.personal.ledger.data.Installment
import dev.personal.ledger.data.LedgerData
import dev.personal.ledger.data.Recurring
import dev.personal.ledger.data.RecurringKind
import dev.personal.ledger.data.Seeds
import dev.personal.ledger.data.Settings
import dev.personal.ledger.data.Split
import dev.personal.ledger.data.TxType
import dev.personal.ledger.data.Txn
import dev.personal.ledger.domain.Analytics
import dev.personal.ledger.domain.Cards
import dev.personal.ledger.domain.CommandParser
import dev.personal.ledger.domain.ForecastCalc
import dev.personal.ledger.domain.Installments
import dev.personal.ledger.domain.Ledger
import dev.personal.ledger.domain.Money
import dev.personal.ledger.domain.ObKind
import dev.personal.ledger.domain.Obligations
import dev.personal.ledger.domain.Recurrence
import dev.personal.ledger.domain.SafeToSpendCalc
import dev.personal.ledger.domain.Search
import dev.personal.ledger.domain.atMillis
import dev.personal.ledger.domain.endMillis
import dev.personal.ledger.domain.startMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

class LedgerTest {
    private val today = LocalDate.of(2026, 9, 26)
    private val bank = Account(1, "MB Bank", AccountType.BANK, "mb", openingBalance = 10_000_000)
    private val vcb = Account(2, "Vietcombank", AccountType.BANK, "vcb", openingBalance = 0)
    private val card = Account(3, "VPBank Credit", AccountType.CREDIT_CARD, "vp", creditLimit = 30_000_000, statementDay = 20, dueDay = 5, spendable = false)
    private val savings = Account(4, "Savings", AccountType.SAVINGS, "tk", openingBalance = 20_000_000, spendable = false)
    private val accounts = listOf(bank, vcb, card, savings)

    private var id = 1L
    private fun tx(type: TxType, amount: Long, account: Long, date: LocalDate = today, to: Long? = null, cat: Long? = Seeds.LUNCH, linked: Long? = null, recurring: Long? = null, installment: Long? = null) =
        Txn(id++, type, amount, account, to, cat, date.atMillis(LocalTime.NOON), "", linked, null, recurring, installment)

    private fun data(vararg t: Txn, splits: List<Split> = emptyList(), recurring: List<Recurring> = emptyList(), installments: List<Installment> = emptyList()) =
        LedgerData(accounts, Seeds.categories, t.toList(), splits, emptyList(), recurring, emptyList(), installments)

    private val monthFrom = YearMonth.from(today).startMillis()
    private val monthTo = YearMonth.from(today).endMillis()

    @Test fun transferIsZeroSumAndNeverIncomeOrExpense() {
        val d = data(tx(TxType.TRANSFER, 5_000_000, bank.id, to = vcb.id, cat = null))
        val b = Ledger.balances(d)
        assertEquals(5_000_000L, b[bank.id])
        assertEquals(5_000_000L, b[vcb.id])
        assertEquals(accounts.sumOf { it.openingBalance }, b.values.sum())
        assertEquals(0L, Ledger.spending(d, monthFrom, monthTo))
        assertEquals(0L, Ledger.income(d, monthFrom, monthTo))
    }

    @Test fun cardPurchaseCountsOnceAndPaymentIsSettlement() {
        val d = data(
            tx(TxType.EXPENSE, 2_000_000, card.id, today.minusDays(2)),
            tx(TxType.TRANSFER, 2_000_000, bank.id, today.minusDays(1), to = card.id, cat = null),
        )
        assertEquals(2_000_000L, Ledger.spending(d, monthFrom, monthTo))
        assertEquals(0L, Ledger.income(d, monthFrom, monthTo))
        val b = Ledger.balances(d)
        assertEquals(0L, b[card.id])
        assertEquals(8_000_000L, b[bank.id])
        assertEquals(0L, Ledger.position(d).cardDebt)
    }

    @Test fun refundOffsetsExpenseAndIsNotIncome() {
        val purchase = tx(TxType.EXPENSE, 500_000, card.id, cat = Seeds.ONLINE)
        val d = data(purchase, tx(TxType.REFUND, 500_000, card.id, cat = null, linked = purchase.id))
        assertEquals(0L, Ledger.spending(d, monthFrom, monthTo))
        assertEquals(0L, Ledger.income(d, monthFrom, monthTo))
        assertEquals(0L, Ledger.balances(d)[card.id])
    }

    @Test fun reimbursementReducesCategoryNotIncome() {
        val dinner = tx(TxType.EXPENSE, 1_600_000, card.id, cat = Seeds.DINNER)
        val d = data(dinner, tx(TxType.REIMBURSEMENT, 1_200_000, bank.id, cat = Seeds.DINNER, linked = dinner.id))
        val m = Analytics.month(d, YearMonth.from(today), today)
        assertEquals(400_000L, m.spending)
        assertEquals(0L, m.income)
        assertEquals(400_000L, m.categories.first { it.category?.id == Seeds.FOOD }.amount)
    }

    @Test fun splitLinesDriveAnalyticsAndRefundsAllocateProportionally() {
        val order = tx(TxType.EXPENSE, 2_000_000, card.id, cat = null)
        val splits = listOf(Split(1, order.id, Seeds.ELECTRONICS, 1_500_000), Split(2, order.id, Seeds.HOUSEHOLD, 500_000))
        val refund = tx(TxType.REFUND, 400_000, card.id, cat = null, linked = order.id)
        val d = data(order, refund, splits = splits)
        val lines = Ledger.expenseLines(d)
        assertEquals(2_000_000L, lines.filter { it.tx.id == order.id }.sumOf { it.amount })
        assertEquals(1_200_000L, lines.filter { it.categoryId == Seeds.ELECTRONICS }.sumOf { it.amount })
        assertEquals(400_000L, lines.filter { it.categoryId == Seeds.HOUSEHOLD }.sumOf { it.amount })
        assertEquals(1_600_000L, Ledger.spending(d, monthFrom, monthTo))
    }

    @Test fun cardStatementIsDebtAtCloseMinusPayments() {
        val d = data(
            tx(TxType.EXPENSE, 3_000_000, card.id, LocalDate.of(2026, 9, 10)),
            tx(TxType.EXPENSE, 1_000_000, card.id, LocalDate.of(2026, 9, 22)),
            tx(TxType.TRANSFER, 500_000, bank.id, LocalDate.of(2026, 9, 24), to = card.id, cat = null),
        )
        val s = Cards.status(d, card, today)
        assertEquals(LocalDate.of(2026, 9, 20), s.statementClose)
        assertEquals(LocalDate.of(2026, 10, 5), s.dueDate)
        assertEquals(2_500_000L, s.statementBalance)
        assertEquals(3_500_000L, s.used)
        assertEquals(1_000_000L, s.unbilled)
        assertEquals(26_500_000L, s.available)
        assertFalse(s.overdue)
    }

    @Test fun installmentPaymentsAreExpensesAndRemainderIsLiability() {
        val plan = Installment(1, "Laptop", 24_000_000, 0, 0, 12, LocalDate.of(2026, 3, 5).toEpochDay(), card.id, Seeds.INSTALLMENTS, prepaidPeriods = 5)
        val d = data(
            tx(TxType.EXPENSE, 2_000_000, card.id, LocalDate.of(2026, 8, 5), cat = Seeds.INSTALLMENTS, installment = 1),
            tx(TxType.EXPENSE, 2_000_000, card.id, LocalDate.of(2026, 9, 5), cat = Seeds.INSTALLMENTS, installment = 1),
            installments = listOf(plan),
        )
        val p = Installments.progress(d, plan)
        assertEquals(7, p.paidPeriods)
        assertEquals(10_000_000L, p.remaining)
        assertEquals(LocalDate.of(2026, 10, 5), p.nextDue)
        val m = Analytics.month(d, YearMonth.of(2026, 9), today)
        assertEquals(2_000_000L, m.fixed)
        // Net worth counts the unpaid remainder once, and the card debt from charged installments separately.
        val pos = Ledger.position(d)
        assertEquals(10_000_000L, pos.installmentDebt)
        assertEquals(4_000_000L, pos.cardDebt)
    }

    @Test fun periodAmountsSumToTotalPayable() {
        val plan = Installment(1, "Phone", 11_760_000, 0, 480_000, 7, 0, 1)
        assertEquals(plan.totalPayable, (0 until plan.periods).sumOf { plan.periodAmount(it) })
    }

    @Test fun cardChargedSubscriptionIsNotCountedTwiceInForecast() {
        val netflix = Recurring(1, RecurringKind.SUBSCRIPTION, "Netflix", 260_000, cadence = Cadence.MONTHLY,
            nextDue = LocalDate.of(2026, 9, 28).toEpochDay(), anchorDay = 28, accountId = card.id, categoryId = Seeds.SUBS)
        val d = data(recurring = listOf(netflix))
        val items = Obligations.upcoming(d, today, today.plusDays(45))
        val sub = items.first { it.kind == ObKind.SUBSCRIPTION }
        assertEquals(0L, sub.cashDelta)
        // It lands in the next card statement (close Oct 20, due Nov 5) exactly once.
        val cardItems = items.filter { it.kind == ObKind.CARD }
        assertEquals(1, cardItems.size)
        assertEquals(260_000L, cardItems.single().amount)
        val f = ForecastCalc.compute(d, Settings(forecastIncludesEstimate = false), today)
        assertEquals(10_000_000L - 260_000L, f.points.last().known)
    }

    @Test fun safeToSpendFollowsTheFormula() {
        val salary = Recurring(1, RecurringKind.INCOME, "Salary", 25_000_000, nextDue = LocalDate.of(2026, 10, 10).toEpochDay(),
            anchorDay = 10, accountId = bank.id, isSalary = true)
        val rent = Recurring(2, RecurringKind.BILL, "Rent", 4_500_000, nextDue = LocalDate.of(2026, 9, 30).toEpochDay(), anchorDay = 30, accountId = bank.id)
        val afterSalary = Recurring(3, RecurringKind.BILL, "Later", 1_000_000, nextDue = LocalDate.of(2026, 10, 12).toEpochDay(), anchorDay = 12, accountId = bank.id)
        val d = data(tx(TxType.EXPENSE, 100_000, bank.id), recurring = listOf(salary, rent, afterSalary))
        val r = SafeToSpendCalc.compute(d, Settings(protectedSavings = 1_000_000, emergencyReserve = 500_000), today)
        assertEquals(9_900_000L, r.spendable)
        assertEquals(4_500_000L, r.obligations)
        assertEquals(1_500_000L, r.protected)
        assertEquals(9_900_000L - 4_500_000L - 1_500_000L, r.safe)
        assertEquals(14, r.daysLeft)
        assertEquals(r.safe / 14, r.perDay)
    }

    @Test fun recurrenceKeepsAnchorDay() {
        val jan31 = LocalDate.of(2026, 1, 31)
        val feb = Recurrence.next(jan31, Cadence.MONTHLY, 31)
        assertEquals(LocalDate.of(2026, 2, 28), feb)
        assertEquals(LocalDate.of(2026, 3, 31), Recurrence.next(feb, Cadence.MONTHLY, 31))
    }

    @Test fun moneyFormatting() {
        assertEquals("65k", Money.compact(65_000))
        assertEquals("8.4m", Money.compact(8_400_000))
        assertEquals("1.42m", Money.compact(1_420_000))
        assertEquals("17m", Money.compact(17_040_000))
        assertEquals("260k", Money.compact(260_000))
        assertEquals("−6.8m", Money.compact(-6_800_000))
        assertEquals("65,000 ₫", Money.full(65_000))
        assertEquals(1_500_000L, Money.parse("1tr5"))
        assertEquals(5_000_000L, Money.parse("5m"))
        assertEquals(85_000L, Money.parse("85k"))
        assertEquals(65_000L, Money.parse("65,000"))
    }

    @Test fun commandParser() {
        val demo = DemoData.build(LocalDateTime.of(2026, 9, 26, 12, 0))
        val a = CommandParser.parse("85k ăn", demo, DemoData.MB)
        assertEquals(TxType.EXPENSE, a.type); assertEquals(85_000L, a.amount); assertEquals(Seeds.FOOD, a.categoryId); assertTrue(a.complete)
        val b = CommandParser.parse("350k xăng mb", demo, DemoData.MB)
        assertEquals(Seeds.FUEL, b.categoryId); assertEquals(DemoData.MB, b.account?.id)
        val c = CommandParser.parse("250k shopee vp", demo, DemoData.MB)
        assertEquals(Seeds.ONLINE, c.categoryId); assertEquals(DemoData.VPB, c.account?.id)
        val t = CommandParser.parse("chuyển 5m mb vcb", demo, DemoData.MB)
        assertEquals(TxType.TRANSFER, t.type); assertEquals(5_000_000L, t.amount)
        assertEquals(DemoData.MB, t.account?.id); assertEquals(DemoData.VCB, t.toAccount?.id); assertTrue(t.complete)
        val y = CommandParser.parse("hôm qua 45 cafe", demo, DemoData.MB)
        assertEquals(1, y.daysAgo); assertEquals(45_000L, y.amount); assertEquals(Seeds.CAFE, y.categoryId)
        val i = CommandParser.parse("+15m lương", demo, DemoData.MB)
        assertEquals(TxType.INCOME, i.type); assertEquals(Seeds.SALARY, i.categoryId)
        val n = CommandParser.parse("120k ăn phở bò", demo, DemoData.MB)
        assertEquals("phở bò", n.note)
    }

    @Test fun searchParsing() {
        val demo = DemoData.build(LocalDateTime.of(2026, 9, 26, 12, 0))
        val q1 = Search.parse("mb september", demo, today)
        assertEquals(setOf(DemoData.MB), q1.accountIds); assertEquals(LocalDate.of(2026, 9, 1), q1.from)
        val q2 = Search.parse("> 1m", demo, today)
        assertEquals(1_000_000L, q2.min)
        assertTrue(Search.run(q2, demo).all { it.amount > 1_000_000 })
        val q3 = Search.parse("food this week", demo, today)
        assertTrue(Seeds.LUNCH in q3.categoryIds); assertEquals(LocalDate.of(2026, 9, 21), q3.from)
        val q4 = Search.parse("subscriptions", demo, today)
        assertTrue(Search.run(q4, demo).isNotEmpty())
        val q5 = Search.parse("shopee", demo, today)
        val shopee = Search.run(q5, demo)
        // Category words also match notes: the split order and the refund say "Shopee" but aren't in that category.
        assertTrue(shopee.any { demo.splitsByTx.containsKey(it.id) })
        assertTrue(shopee.any { it.type == TxType.REFUND })
    }

    @Test fun demoDataIsInternallyConsistent() {
        val now = LocalDateTime.of(2026, 9, 26, 12, 0)
        val d = DemoData.build(now)
        val nowMillis = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertTrue(d.transactions.all { it.date <= nowMillis })
        assertTrue(d.transactions.all { it.amount > 0 || it.type == TxType.ADJUSTMENT })
        d.splitsByTx.forEach { (txId, lines) -> assertEquals(d.txById[txId]!!.amount, lines.sumOf { it.amount }) }
        val b = Ledger.balances(d)
        // Realistic history: no everyday or savings account ever goes negative.
        d.accounts.filter { it.type != AccountType.CREDIT_CARD }.forEach { a ->
            var running = a.openingBalance
            d.transactions.sortedBy { it.date }.forEach { t -> running += Ledger.delta(t, a.id); assertTrue("${a.name} negative", running >= 0) }
        }
        assertTrue(b[DemoData.MB]!! >= 12_400_000L)
        val cardStatus = Cards.status(d, d.accountById[DemoData.VPB]!!, now.toLocalDate(), b)
        assertTrue(cardStatus.statementBalance > 0)
        assertEquals(LocalDate.of(2026, 10, 5), cardStatus.dueDate)
        // The previous statement was paid in full.
        val prev = Cards.status(d, d.accountById[DemoData.VPB]!!, LocalDate.of(2026, 9, 10))
        assertEquals(0L, prev.statementBalance)
        assertNotNull(Obligations.nextSalary(d, now.toLocalDate()))
        assertTrue("demo has overdue items", Obligations.upcoming(d, now.toLocalDate(), now.toLocalDate().plusDays(30)).none { it.overdue })
        assertEquals(7, Installments.progress(d, d.installments.first { it.id == 1L }).paidPeriods)
        val sts = SafeToSpendCalc.compute(d, DemoData.settings(), now.toLocalDate(), b)
        assertTrue(sts.items.none { it.cashDelta >= 0 })
        assertNull(sts.items.firstOrNull { it.kind == ObKind.INCOME })
        // A healthy-looking demo: positive safe-to-spend and a card statement in a realistic range.
        assertTrue("safe=${sts.safe}", sts.safe > 0)
        assertTrue("statement=${cardStatus.statementBalance}", cardStatus.statementBalance in 3_000_000L..9_000_000L)
        // Transfers never appear in spending.
        assertTrue(Ledger.expenseLines(d).none { it.tx.type == TxType.TRANSFER })
    }
}
