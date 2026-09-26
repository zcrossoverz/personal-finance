package dev.personal.ledger.backup

import dev.personal.ledger.i18n.tr
import dev.personal.ledger.data.LedgerData
import dev.personal.ledger.data.Settings
import dev.personal.ledger.domain.Fmt
import dev.personal.ledger.domain.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A complete, human-readable backup. The user owns this file; nothing depends on a remote service. */
@Serializable
data class BackupFile(
    val format: String = "ledger-backup",
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: Settings,
    val data: LedgerData,
)

object Backup {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(settings: Settings, data: LedgerData): String = json.encodeToString(BackupFile.serializer(), BackupFile(settings = settings, data = data))

    fun decode(text: String): BackupFile {
        val file = json.decodeFromString(BackupFile.serializer(), text)
        require(file.format == "ledger-backup") { tr("This file isn't a Ledger backup.") }
        require(file.version <= 1) { tr("This backup was made by a newer version of the app.") }
        return file
    }

    /** Flat CSV for spreadsheets: one row per transaction (split lines expanded). */
    fun csv(data: LedgerData): String = buildString {
        appendLine("date,time,type,amount,account,to_account,category,parent_category,note,split")
        fun esc(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        for (t in data.transactions.sortedBy { it.date }) {
            val dt = t.date.toLocalDateTime()
            val acc = data.accountById[t.accountId]?.name ?: ""
            val to = t.toAccountId?.let { data.accountById[it]?.name } ?: ""
            val splits = data.splitsByTx[t.id]
            val rows = splits?.map { Triple(it.categoryId, it.amount, it.note.ifBlank { t.note }) } ?: listOf(Triple(t.categoryId, t.amount, t.note))
            rows.forEach { (catId, amount, note) ->
                val cat = catId?.let { data.categoryById[it] }
                val parent = cat?.parentId?.let { data.categoryById[it]?.name } ?: ""
                appendLine(listOf(dt.toLocalDate().toString(), Fmt.time(t.date), t.type.name, amount.toString(), acc, to,
                    cat?.name ?: "", parent, note, if (splits != null) "yes" else "").joinToString(",") { esc(it) })
            }
        }
    }
}
