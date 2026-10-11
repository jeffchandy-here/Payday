package com.jeff.payday

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

// ---------- Salary cycle ----------
// Salary lands on the 28th. A cycle is named after the month the salary is FOR:
// "October" = Sep 28 .. Oct 27.
const val PAY_DAY = 28

fun cycleOf(date: LocalDate): YearMonth {
    val ym = YearMonth.from(date)
    return if (date.dayOfMonth >= PAY_DAY) ym.plusMonths(1) else ym
}

fun cycleStart(c: YearMonth): LocalDate = c.minusMonths(1).atDay(PAY_DAY)
fun cycleEnd(c: YearMonth): LocalDate = c.atDay(PAY_DAY - 1)

// ---------- Data ----------
data class SalaryEntry(val from: YearMonth, val amount: Long)

/** A monthly cost (rent, EMI...). Counts in every cycle from [start] until [end] (exclusive). */
data class Recurring(
    val id: String,
    val name: String,
    val amount: Long,
    val emoji: String,
    val start: YearMonth,
    val end: YearMonth?
) {
    fun activeIn(c: YearMonth): Boolean =
        !c.isBefore(start) && (end == null || c.isBefore(end))
}

data class Expense(
    val id: String,
    val amount: Long,
    val note: String,
    val category: String,
    val date: LocalDate,
    val createdAt: Long
) {
    val title: String get() = note.ifBlank { category }
}

data class AppData(
    val salaries: List<SalaryEntry>,
    val recurring: List<Recurring>,
    val expenses: List<Expense>
)

data class Category(val name: String, val emoji: String)

val CATEGORIES = listOf(
    Category("Groceries", "🛒"),
    Category("Food", "🍔"),
    Category("Travel", "⛽"),
    Category("Bills", "⚡"),
    Category("Shopping", "🛍️"),
    Category("Fun", "🎬"),
    Category("Health", "💊"),
    Category("Other", "📦")
)

val FIXED_EMOJIS = listOf("🏠", "🏍️", "🚗", "📱", "📈", "📶", "💳", "🎓", "🛡️", "📦")

fun emojiFor(category: String): String =
    CATEGORIES.find { it.name == category }?.emoji ?: "📦"

/** Indian grouping: 100000 -> ₹1,00,000 */
fun inr(value: Long): String {
    val neg = value < 0
    val s = kotlin.math.abs(value).toString()
    val grouped = if (s.length <= 3) s else {
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val parts = mutableListOf<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        parts.joinToString(",") + "," + last3
    }
    return (if (neg) "-₹" else "₹") + grouped
}

// ---------- Store (saved as a JSON file on the phone) ----------
class Store(context: Context) {
    private val file = File(context.filesDir, "payday.json")

    var data by mutableStateOf(load())
        private set

    fun salaryFor(c: YearMonth): Long =
        data.salaries.filter { !it.from.isAfter(c) }.maxByOrNull { it.from }?.amount ?: 0L

    fun recurringFor(c: YearMonth): List<Recurring> =
        data.recurring.filter { it.activeIn(c) }.sortedByDescending { it.amount }

    fun expensesFor(c: YearMonth): List<Expense> =
        data.expenses.filter { cycleOf(it.date) == c }
            .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.createdAt })

    /** Earliest cycle that has any data, used by History. */
    fun firstCycle(current: YearMonth): YearMonth {
        val candidates = data.expenses.map { cycleOf(it.date) } + data.recurring.map { it.start } + current
        return candidates.minOrNull() ?: current
    }

    // Salary: change applies from [from] onward, earlier months keep the old value.
    fun setSalary(amount: Long, from: YearMonth) {
        val kept = data.salaries.filter { it.from.isBefore(from) }
        update(data.copy(salaries = kept + SalaryEntry(from, amount)))
    }

    fun addRecurring(name: String, amount: Long, emoji: String, from: YearMonth) {
        update(data.copy(recurring = data.recurring + Recurring(newId(), name, amount, emoji, from, null)))
    }

    // Edit: new amount applies from this month. Past months keep the old amount.
    fun editRecurring(id: String, name: String, amount: Long, emoji: String, now: YearMonth) {
        val old = data.recurring.find { it.id == id } ?: return
        val list = if (!old.start.isBefore(now)) {
            data.recurring.map { if (it.id == id) it.copy(name = name, amount = amount, emoji = emoji) else it }
        } else {
            data.recurring.map { if (it.id == id) it.copy(end = now) else it } +
                Recurring(newId(), name, amount, emoji, now, old.end)
        }
        update(data.copy(recurring = list))
    }

    // Delete: stops from this month. Past months still show it.
    fun deleteRecurring(id: String, now: YearMonth) {
        val old = data.recurring.find { it.id == id } ?: return
        val list = if (!old.start.isBefore(now)) {
            data.recurring.filter { it.id != id }
        } else {
            data.recurring.map { if (it.id == id) it.copy(end = now) else it }
        }
        update(data.copy(recurring = list))
    }

    fun addExpense(amount: Long, note: String, category: String, date: LocalDate) {
        val e = Expense(newId(), amount, note, category, date, System.currentTimeMillis())
        update(data.copy(expenses = data.expenses + e))
    }

    fun editExpense(id: String, amount: Long, note: String, category: String, date: LocalDate) {
        update(data.copy(expenses = data.expenses.map {
            if (it.id == id) it.copy(amount = amount, note = note, category = category, date = date) else it
        }))
    }

    fun deleteExpense(id: String) {
        update(data.copy(expenses = data.expenses.filter { it.id != id }))
    }

    private fun newId() = UUID.randomUUID().toString()

    private fun update(newData: AppData) {
        data = newData
        save()
    }

    // ---------- JSON ----------
    private fun defaultData() = AppData(
        salaries = listOf(SalaryEntry(YearMonth.of(2000, 1), 100_000L)),
        recurring = emptyList(),
        expenses = emptyList()
    )

    private fun load(): AppData {
        if (!file.exists()) return defaultData()
        return try {
            parse(JSONObject(file.readText()))
        } catch (e: Exception) {
            // Keep the broken file aside instead of overwriting it.
            file.renameTo(File(file.parentFile, "payday-broken-${System.currentTimeMillis()}.json"))
            defaultData()
        }
    }

    private fun parse(root: JSONObject): AppData {
        val salaries = mutableListOf<SalaryEntry>()
        val sArr = root.getJSONArray("salaries")
        for (i in 0 until sArr.length()) {
            val o = sArr.getJSONObject(i)
            salaries.add(SalaryEntry(YearMonth.parse(o.getString("from")), o.getLong("amount")))
        }

        val recurring = mutableListOf<Recurring>()
        val rArr = root.getJSONArray("recurring")
        for (i in 0 until rArr.length()) {
            val o = rArr.getJSONObject(i)
            recurring.add(
                Recurring(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    amount = o.getLong("amount"),
                    emoji = o.optString("emoji", "📦"),
                    start = YearMonth.parse(o.getString("start")),
                    end = if (o.has("end")) YearMonth.parse(o.getString("end")) else null
                )
            )
        }

        val expenses = mutableListOf<Expense>()
        val eArr = root.getJSONArray("expenses")
        for (i in 0 until eArr.length()) {
            val o = eArr.getJSONObject(i)
            expenses.add(
                Expense(
                    id = o.getString("id"),
                    amount = o.getLong("amount"),
                    note = o.optString("note", ""),
                    category = o.optString("category", "Other"),
                    date = LocalDate.parse(o.getString("date")),
                    createdAt = o.optLong("createdAt", 0L)
                )
            )
        }
        return AppData(salaries, recurring, expenses)
    }

    private fun save() {
        val root = JSONObject()
        root.put("version", 1)

        val sArr = JSONArray()
        data.salaries.forEach {
            sArr.put(JSONObject().put("from", it.from.toString()).put("amount", it.amount))
        }
        root.put("salaries", sArr)

        val rArr = JSONArray()
        data.recurring.forEach {
            val o = JSONObject()
                .put("id", it.id)
                .put("name", it.name)
                .put("amount", it.amount)
                .put("emoji", it.emoji)
                .put("start", it.start.toString())
            if (it.end != null) o.put("end", it.end.toString())
            rArr.put(o)
        }
        root.put("recurring", rArr)

        val eArr = JSONArray()
        data.expenses.forEach {
            eArr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("amount", it.amount)
                    .put("note", it.note)
                    .put("category", it.category)
                    .put("date", it.date.toString())
                    .put("createdAt", it.createdAt)
            )
        }
        root.put("expenses", eArr)

        val text = root.toString()
        val tmp = File(file.parentFile, "payday.json.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }
}
