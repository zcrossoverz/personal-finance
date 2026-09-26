package dev.personal.ledger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * Money is always a Long in the currency's minor unit (đồng for VND). Amounts are positive except
 * ADJUSTMENT, whose amount is a signed balance delta. See docs/PRODUCT.md §8 for the ledger rules.
 */

enum class AccountType { CASH, BANK, EWALLET, SAVINGS, CREDIT_CARD, OTHER;
    val isLiability get() = this == CREDIT_CARD
}

@Serializable
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    /** Short keywords used by command entry and search, comma separated ("mb,mbbank"). */
    val aliases: String = "",
    /** For a credit card this is negative when the card starts with debt. */
    val openingBalance: Long = 0,
    val colorIndex: Int = 0,
    val creditLimit: Long? = null,
    val statementDay: Int? = null,
    val dueDay: Int? = null,
    /** Counts toward "Safe to spend". Savings and cards are excluded by default. */
    val spendable: Boolean = true,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

enum class CategoryNature { FIXED, FLEXIBLE }

@Serializable
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val icon: String,
    val colorIndex: Int = 0,
    val isIncome: Boolean = false,
    val nature: CategoryNature = CategoryNature.FLEXIBLE,
    val aliases: String = "",
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

enum class TxType {
    EXPENSE, INCOME, TRANSFER, REFUND, REIMBURSEMENT, ADJUSTMENT;

    /** Refunds and reimbursements reduce spending instead of adding income. */
    val offsetsExpense get() = this == REFUND || this == REIMBURSEMENT
}

@Serializable
@Entity(
    tableName = "transactions",
    indices = [Index("date"), Index("accountId"), Index("toAccountId"), Index("presetId"), Index("recurringId"), Index("installmentId")],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    val amount: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    /** Epoch millis, local wall time. */
    val date: Long,
    val note: String = "",
    /** Refund / reimbursement → the expense it offsets. */
    val linkedTxId: Long? = null,
    val presetId: Long? = null,
    val recurringId: Long? = null,
    val installmentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "splits", indices = [Index("txId")])
data class Split(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val txId: Long,
    val categoryId: Long,
    val amount: Long,
    val note: String = "",
)

@Serializable
@Entity(tableName = "presets")
data class Preset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val icon: String,
    val colorIndex: Int = 0,
    val type: TxType = TxType.EXPENSE,
    val categoryId: Long? = null,
    /** Null = use the account last used with this preset, then the global default. */
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    /** Seed amounts shown before the preset has history, comma separated. */
    val seedAmounts: String = "",
    /** Resolves to a time-appropriate subcategory (e.g. Ăn uống → Ăn trưa at noon). */
    val contextual: Boolean = false,
    val pinned: Boolean = false,
    val hidden: Boolean = false,
    val sortOrder: Int = 0,
)

enum class RecurringKind { SUBSCRIPTION, BILL, INCOME, TRANSFER }
enum class Cadence(val months: Int, val label: String) {
    WEEKLY(0, "Weekly"), MONTHLY(1, "Monthly"), QUARTERLY(3, "Quarterly"), YEARLY(12, "Yearly");

    /** Normalised monthly cost of one charge. */
    fun monthly(amount: Long): Long = when (this) {
        WEEKLY -> amount * 52 / 12
        MONTHLY -> amount
        QUARTERLY -> amount / 3
        YEARLY -> amount / 12
    }
}

@Serializable
@Entity(tableName = "recurring")
data class Recurring(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: RecurringKind,
    val name: String,
    /** Fixed amount, or the estimate used for forecasting when [variable]. */
    val amount: Long,
    val variable: Boolean = false,
    val cadence: Cadence = Cadence.MONTHLY,
    /** Epoch day of the next unpaid occurrence. */
    val nextDue: Long,
    /** Day-of-month the schedule is anchored to (prevents 31 → 28 → 28 drift). */
    val anchorDay: Int,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val autoPay: Boolean = false,
    val active: Boolean = true,
    val isSalary: Boolean = false,
    val icon: String = "receipt",
    val colorIndex: Int = 0,
    val note: String = "",
)

@Serializable
@Entity(tableName = "price_changes", indices = [Index("recurringId")])
data class PriceChange(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recurringId: Long,
    val amount: Long,
    val effectiveDay: Long,
)

enum class InstallmentStatus { ACTIVE, COMPLETED, CANCELLED }

@Serializable
@Entity(tableName = "installments")
data class Installment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val principal: Long,
    val interest: Long = 0,
    val fees: Long = 0,
    val periods: Int,
    /** Epoch day of the first payment. */
    val firstDue: Long,
    val accountId: Long,
    val categoryId: Long? = null,
    /** Payments made before the plan was entered in the app. */
    val prepaidPeriods: Int = 0,
    val autoPay: Boolean = true,
    val status: InstallmentStatus = InstallmentStatus.ACTIVE,
    val icon: String = "devices",
    val colorIndex: Int = 0,
    val note: String = "",
) {
    val totalPayable get() = principal + interest + fees
    /** Amount of period [index] (0-based). The last period absorbs rounding. */
    fun periodAmount(index: Int): Long {
        val base = totalPayable / periods
        return if (index == periods - 1) totalPayable - base * (periods - 1) else base
    }
}
