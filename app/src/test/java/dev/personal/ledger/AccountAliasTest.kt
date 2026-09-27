package dev.personal.ledger

import dev.personal.ledger.data.Account
import dev.personal.ledger.data.AccountType
import dev.personal.ledger.data.LedgerData
import dev.personal.ledger.data.Seeds
import dev.personal.ledger.domain.CommandParser
import dev.personal.ledger.domain.accountAliases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Accounts typed during setup must work in command entry without the user configuring anything. */
class AccountAliasTest {
    @Test fun aliasesFromTypedNames() {
        assertEquals("hdbank,hd", accountAliases("HDBank"))
        assertEquals("viettelmoney,vm,viettel", accountAliases("Viettel Money"))
        assertTrue("quylop" in accountAliases("Quỹ lớp").split(","))
        assertEquals("", accountAliases("  "))
    }

    @Test fun commandFindsACustomAccount() {
        val data = LedgerData(
            accounts = listOf(
                Account(1, "MB Bank", AccountType.BANK, "mb,mbbank"),
                Account(2, "HDBank", AccountType.BANK, accountAliases("HDBank")),
            ),
            categories = Seeds.categories,
            presets = Seeds.presets(),
        )
        val p = CommandParser.parse("50k ăn hd", data, 1)
        assertEquals(2L, p.account?.id)
        assertEquals(50_000L, p.amount)
        assertTrue(p.complete)
    }
}
