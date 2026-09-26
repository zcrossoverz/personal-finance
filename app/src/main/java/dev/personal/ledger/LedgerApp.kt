package dev.personal.ledger

import android.app.Application
import dev.personal.ledger.data.LedgerDatabase
import dev.personal.ledger.data.Prefs
import dev.personal.ledger.data.Repository
import dev.personal.ledger.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Hand-wired dependencies. A single-user app does not need a DI framework. */
class LedgerApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = LedgerDatabase.open(this)
        repo = Repository(db.dao(), Prefs(this), scope)
        scope.launch { if (repo.prefs.settings.value.onboarded) repo.autoPost() }
        Reminders.schedule(this)
    }
}
