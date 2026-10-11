package com.jeff.payday

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.time.format.TextStyle as JTextStyle

// ---------- Colours ----------
val Teal = Color(0xFF0F5C6E)
val TealLight = Color(0xFFD6E8EC)
val TealPast = Color(0xFF9CC3CC)
val Paper = Color(0xFFEEF1F4)
val Ink = Color(0xFF1B2433)
val Mute = Color(0xFF6B7686)
val Line = Color(0xFFDCE1E8)
val Track = Color(0xFFE3E8EE)
val FixedC = Color(0xFFD99A2B)
val FixedBg = Color(0xFFFAEFD9)
val MiscC = Color(0xFF7A4FA0)
val MiscLight = Color(0xFFC8A6E6)
val MiscBg = Color(0xFFEFE6F7)
val LeftC = Color(0xFF2E8B57)
val Danger = Color(0xFFB3261E)

val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
val longDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

fun cycleName(c: YearMonth, current: YearMonth): String {
    val m = c.month.getDisplayName(JTextStyle.FULL, Locale.getDefault())
    return if (c.year == current.year) m else "$m ${c.year}"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(applicationContext)
        setContent { PaydayTheme { PaydayApp(store) } }
    }
}

@Composable
fun PaydayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Teal,
            onPrimary = Color.White,
            background = Paper,
            onBackground = Ink,
            surface = Color.White,
            onSurface = Ink,
            surfaceContainer = Color.White,
            surfaceContainerHigh = Color.White,
            secondaryContainer = TealLight
        ),
        content = content
    )
}

enum class Tab(val label: String, val icon: String) {
    Overview("Overview", "◔"),
    Monthly("Monthly", "↻"),
    Expenses("Expenses", "≡"),
    History("History", "▤")
}

// ---------- App shell ----------
@Composable
fun PaydayApp(store: Store) {
    val current = cycleOf(LocalDate.now())
    var tab by remember { mutableStateOf(Tab.Overview) }
    var viewed by remember { mutableStateOf(current) }

    var addingExpense by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var addingRecurring by remember { mutableStateOf(false) }
    var editingRecurring by remember { mutableStateOf<Recurring?>(null) }
    var editingSalary by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Paper,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Text(t.icon, fontSize = 18.sp) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Teal,
                            selectedTextColor = Teal,
                            indicatorColor = TealLight,
                            unselectedIconColor = Mute,
                            unselectedTextColor = Mute
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            when (tab) {
                Tab.Overview, Tab.Expenses -> ExtendedFloatingActionButton(
                    onClick = { addingExpense = true },
                    containerColor = Teal,
                    contentColor = Color.White
                ) { Text("＋  Add expense", fontWeight = FontWeight.Bold) }

                Tab.Monthly -> ExtendedFloatingActionButton(
                    onClick = { addingRecurring = true },
                    containerColor = Teal,
                    contentColor = Color.White
                ) { Text("＋  Add monthly cost", fontWeight = FontWeight.Bold) }

                Tab.History -> {}
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                Tab.Overview -> OverviewScreen(
                    store, viewed, current,
                    onCycle = { viewed = it },
                    onExpenseClick = { editingExpense = it },
                    onSeeAll = { tab = Tab.Expenses }
                )
                Tab.Monthly -> MonthlyScreen(
                    store, current,
                    onEditSalary = { editingSalary = true },
                    onRecurringClick = { editingRecurring = it }
                )
                Tab.Expenses -> ExpensesScreen(
                    store, viewed, current,
                    onCycle = { viewed = it },
                    onExpenseClick = { editingExpense = it }
                )
                Tab.History -> HistoryScreen(
                    store, current,
                    onOpen = { viewed = it; tab = Tab.Overview }
                )
            }
        }
    }

    // ----- Dialogs -----
    if (addingExpense || editingExpense != null) {
        val e = editingExpense
        key(e?.id) {
            ExpenseDialog(
                initial = e,
                onDismiss = { addingExpense = false; editingExpense = null },
                onSave = { amount, note, category, date, everyMonth ->
                    when {
                        e != null -> store.editExpense(e.id, amount, note, category, date)
                        everyMonth -> store.addRecurring(note.ifBlank { category }, amount, emojiFor(category), current)
                        else -> store.addExpense(amount, note, category, date)
                    }
                    addingExpense = false; editingExpense = null
                },
                onDelete = if (e != null) ({ store.deleteExpense(e.id); editingExpense = null }) else null
            )
        }
    }

    if (addingRecurring || editingRecurring != null) {
        val r = editingRecurring
        key(r?.id) {
            RecurringDialog(
                initial = r,
                current = current,
                onDismiss = { addingRecurring = false; editingRecurring = null },
                onSave = { name, amount, emoji ->
                    if (r != null) store.editRecurring(r.id, name, amount, emoji, current)
                    else store.addRecurring(name, amount, emoji, current)
                    addingRecurring = false; editingRecurring = null
                },
                onDelete = if (r != null) ({ store.deleteRecurring(r.id, current); editingRecurring = null }) else null
            )
        }
    }

    if (editingSalary) {
        SalaryDialog(
            amount = store.salaryFor(current),
            onDismiss = { editingSalary = false },
            onSave = { store.setSalary(it, current); editingSalary = false }
        )
    }
}

// ---------- Shared bits ----------
@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        content = content
    )
}

@Composable
fun MoneyRow(emoji: String, emojiBg: Color, title: String, subtitle: String, amount: Long, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(emojiBg),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 17.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, fontSize = 12.sp, color = Mute)
        }
        Text(inr(amount), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
    }
}

@Composable
fun SectionTitle(text: String, right: String? = null, rightColor: Color = Teal, onRight: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 2.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink, modifier = Modifier.weight(1f))
        if (right != null) {
            val m = if (onRight != null) Modifier.clickable(onClick = onRight) else Modifier
            Text(right, color = rightColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = m)
        }
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(text, color = Mute, fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp))
}

@Composable
fun CycleHeader(cycle: YearMonth, current: YearMonth, onCycle: (YearMonth) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(cycleName(cycle, current), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
            val shape = RoundedCornerShape(20.dp)
            Row(
                Modifier.clip(shape).background(Color.White).border(1.dp, Line, shape),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "‹",
                    Modifier.clickable { onCycle(cycle.minusMonths(1)) }.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 20.sp, color = Teal
                )
                Text("${cycleStart(cycle).format(shortDate)} – ${cycleEnd(cycle).format(shortDate)}", fontSize = 12.sp, color = Mute)
                val canNext = cycle.isBefore(current)
                Text(
                    "›",
                    Modifier.clickable(enabled = canNext) { onCycle(cycle.plusMonths(1)) }.padding(horizontal = 12.dp, vertical = 4.dp),
                    fontSize = 20.sp, color = if (canNext) Teal else Line
                )
            }
        }
        if (cycle != current) {
            Text(
                "Back to this month",
                Modifier.clickable { onCycle(current) }.padding(top = 6.dp),
                color = Teal, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ---------- Overview ----------
@Composable
fun OverviewScreen(
    store: Store,
    cycle: YearMonth,
    current: YearMonth,
    onCycle: (YearMonth) -> Unit,
    onExpenseClick: (Expense) -> Unit,
    onSeeAll: () -> Unit
) {
    val salary = store.salaryFor(cycle)
    val fixed = store.recurringFor(cycle).sumOf { it.amount }
    val expenses = store.expensesFor(cycle)
    val misc = expenses.sumOf { it.amount }
    val left = salary - fixed - misc

    val start = cycleStart(cycle)
    val totalDays = ChronoUnit.DAYS.between(start, cycleEnd(cycle)).toInt() + 1
    val today = LocalDate.now()
    val isCurrent = cycle == current
    val dayNum = if (isCurrent) ChronoUnit.DAYS.between(start, today).toInt() + 1 else totalDays

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { CycleHeader(cycle, current, onCycle) }

        // Hero
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Teal).padding(18.dp)
            ) {
                Text(
                    if (left >= 0) (if (isCurrent) "Left this month" else "Left over") else "Over by",
                    color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp
                )
                Text(
                    inr(kotlin.math.abs(left)),
                    color = if (left >= 0) Color.White else Color(0xFFFFC2C2),
                    fontSize = 40.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                SplitBar(fixed, misc, salary)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Legend("Monthly", fixed, FixedC)
                    Legend("Expenses", misc, MiscLight)
                    Legend("Salary", salary, Color.White)
                }
            }
        }

        // Day strip
        item {
            WhiteCard {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        if (isCurrent) "Day $dayNum of $totalDays" else "$totalDays days",
                        fontSize = 12.sp, color = Mute, modifier = Modifier.weight(1f)
                    )
                    val nextPay = ChronoUnit.DAYS.between(today, cycleEnd(current).plusDays(1))
                    Text(
                        if (isCurrent) "Next salary in $nextPay day${if (nextPay == 1L) "" else "s"}" else "Finished",
                        fontSize = 12.sp, color = Mute
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (i in 1..totalDays) {
                        val c = when {
                            isCurrent && i == dayNum -> Teal
                            i <= dayNum -> TealPast
                            else -> Track
                        }
                        Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(3.dp)).background(c))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(start.format(shortDate), fontSize = 12.sp, color = Mute, modifier = Modifier.weight(1f))
                    Text(cycleEnd(cycle).format(shortDate), fontSize = 12.sp, color = Mute)
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        // Stats
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isCurrent) {
                    val remaining = (totalDays - dayNum + 1).coerceAtLeast(1)
                    Stat("Spent per day", inr(misc / dayNum.coerceAtLeast(1)), Ink, Modifier.weight(1f))
                    Stat(
                        "Can spend per day",
                        inr((left / remaining).coerceAtLeast(0)),
                        if (left > 0) LeftC else Danger,
                        Modifier.weight(1f)
                    )
                } else {
                    Stat("Spent per day", inr(misc / totalDays), Ink, Modifier.weight(1f))
                    Stat(if (left >= 0) "Saved" else "Overspent", inr(kotlin.math.abs(left)), if (left >= 0) LeftC else Danger, Modifier.weight(1f))
                }
            }
        }

        item { SectionTitle(if (isCurrent) "Recent expenses" else "Expenses", "See all", onRight = onSeeAll) }

        item {
            WhiteCard {
                if (expenses.isEmpty()) {
                    EmptyNote("No expenses yet. Tap Add expense to log one.")
                } else {
                    expenses.take(5).forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(color = Paper)
                        MoneyRow(emojiFor(e.category), MiscBg, e.title, dayLabel(e.date), e.amount) { onExpenseClick(e) }
                    }
                }
            }
        }
    }
}

fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> d.format(shortDate)
    }
}

@Composable
fun SplitBar(fixed: Long, misc: Long, salary: Long) {
    val total = maxOf(salary, fixed + misc).toFloat().coerceAtLeast(1f)
    Row(
        Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.2f))
    ) {
        if (fixed > 0) Box(Modifier.weight(fixed / total).fillMaxHeight().background(FixedC))
        if (misc > 0) Box(Modifier.weight(misc / total).fillMaxHeight().background(MiscLight))
        val rest = 1f - (fixed + misc) / total
        if (rest > 0.001f) Spacer(Modifier.weight(rest))
    }
}

@Composable
fun Legend(label: String, amount: Long, dot: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(dot))
            Spacer(Modifier.width(5.dp))
            Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        }
        Text(inr(amount), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
    }
}

@Composable
fun Stat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {
        Text(label, fontSize = 12.sp, color = Mute)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = color)
    }
}

// ---------- Monthly ----------
@Composable
fun MonthlyScreen(
    store: Store,
    current: YearMonth,
    onEditSalary: () -> Unit,
    onRecurringClick: (Recurring) -> Unit
) {
    val salary = store.salaryFor(current)
    val items = store.recurringFor(current)
    val fixed = items.sumOf { it.amount }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Monthly", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                Text("Repeats every 28th", fontSize = 12.sp, color = Mute)
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(16.dp)).background(Color.White)
            ) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(LeftC))
                Row(Modifier.weight(1f).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Salary", fontSize = 12.sp, color = Mute)
                        Text(inr(salary), fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = Ink)
                        Text("Same every month until you change it", fontSize = 12.sp, color = Mute)
                    }
                    TextButton(onClick = onEditSalary) { Text("Edit", fontWeight = FontWeight.Bold) }
                }
            }
        }
        item { SectionTitle("Fixed costs", inr(fixed), rightColor = FixedC) }
        item {
            WhiteCard {
                if (items.isEmpty()) {
                    EmptyNote("Add rent, EMIs, SIPs… anything that comes out every month.")
                } else {
                    items.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(color = Paper)
                        MoneyRow(r.emoji, FixedBg, r.name, "Every month", r.amount) { onRecurringClick(r) }
                    }
                }
            }
        }
        item {
            Text(
                "After fixed costs you have ${inr(salary - fixed)} a month for everything else.\n" +
                    "Tap a cost to edit or delete it. Changes apply from this month, past months stay as they were.",
                fontSize = 12.sp, color = Mute, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

// ---------- Expenses ----------
@Composable
fun ExpensesScreen(
    store: Store,
    cycle: YearMonth,
    current: YearMonth,
    onCycle: (YearMonth) -> Unit,
    onExpenseClick: (Expense) -> Unit
) {
    val expenses = store.expensesFor(cycle)
    val total = expenses.sumOf { it.amount }
    val byCat = expenses.groupBy { it.category }
        .map { (cat, list) -> cat to list.sumOf { it.amount } }
        .sortedByDescending { it.second }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { CycleHeader(cycle, current, onCycle) }
        item {
            WhiteCard {
                Spacer(Modifier.height(4.dp))
                Text("Spent on expenses", fontSize = 12.sp, color = Mute)
                Text(inr(total), fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = MiscC)
                Text("${expenses.size} item${if (expenses.size == 1) "" else "s"}", fontSize = 12.sp, color = Mute)
                if (byCat.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    val max = byCat.first().second.toFloat().coerceAtLeast(1f)
                    byCat.forEach { (cat, amt) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${emojiFor(cat)} $cat", fontSize = 13.sp, color = Ink, modifier = Modifier.width(110.dp))
                            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Track)) {
                                Box(Modifier.fillMaxWidth(amt / max).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(MiscLight))
                            }
                            Text(inr(amt), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink,
                                textAlign = TextAlign.End, modifier = Modifier.width(80.dp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        item { SectionTitle("All expenses") }
        item {
            WhiteCard {
                if (expenses.isEmpty()) {
                    EmptyNote("Nothing logged for this month.")
                } else {
                    expenses.forEachIndexed { i, e ->
                        if (i > 0) HorizontalDivider(color = Paper)
                        MoneyRow(emojiFor(e.category), MiscBg, e.title, "${e.category} · ${dayLabel(e.date)}", e.amount) { onExpenseClick(e) }
                    }
                }
            }
        }
    }
}

// ---------- History ----------
@Composable
fun HistoryScreen(store: Store, current: YearMonth, onOpen: (YearMonth) -> Unit) {
    val first = store.firstCycle(current)
    val cycles = generateSequence(current) { it.minusMonths(1) }
        .takeWhile { !it.isBefore(first) }
        .toList()

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("History", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 8.dp))
        }
        items(cycles) { c ->
            val salary = store.salaryFor(c)
            val spent = store.recurringFor(c).sumOf { it.amount } + store.expensesFor(c).sumOf { it.amount }
            val left = salary - spent
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
                    .clickable { onOpen(c) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        cycleName(c, current) + if (c == current) "  (now)" else "",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink
                    )
                    Text("Spent ${inr(spent)} of ${inr(salary)}", fontSize = 12.sp, color = Mute)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(inr(left), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, fontSize = 17.sp,
                        color = if (left >= 0) LeftC else Danger)
                    Text(if (left >= 0) "left" else "over", fontSize = 11.sp, color = Mute)
                }
            }
        }
    }
}

// ---------- Dialog pieces ----------
@Composable
fun Chip(text: String, selected: Boolean, selectedColor: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Text(
        text,
        Modifier
            .clip(shape)
            .background(if (selected) selectedColor else Color.White)
            .border(1.dp, if (selected) selectedColor else Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        color = if (selected) Color.White else Ink,
        fontSize = 13.sp
    )
}

@Composable
fun Segmented(everyMonth: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Track).padding(3.dp)) {
        listOf(false to "Just this month", true to "Every month").forEach { (value, label) ->
            val on = everyMonth == value
            Text(
                label,
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) Color.White else Color.Transparent)
                    .clickable { onChange(value) }
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = if (on) Ink else Mute
            )
        }
    }
}

@Composable
fun AmountField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(s.filter { it.isDigit() }.take(9)) },
        label = { Text("Amount (₹)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun DialogButtons(onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    Row {
        if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = Danger) }
        TextButton(onClick = onDismiss) { Text("Cancel") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseDialog(
    initial: Expense?,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, LocalDate, Boolean) -> Unit,
    onDelete: (() -> Unit)?
) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "Food") }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var everyMonth by remember { mutableStateOf(false) }
    val amt = amount.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add expense" else "Edit expense") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AmountField(amount) { amount = it }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!everyMonth) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                                date.year, date.monthValue - 1, date.dayOfMonth
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Date: ${date.format(longDate)}") }
                }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CATEGORIES.forEach { c ->
                        Chip("${c.emoji} ${c.name}", category == c.name, MiscC) { category = c.name }
                    }
                }
                if (initial == null) {
                    Spacer(Modifier.height(12.dp))
                    Segmented(everyMonth) { everyMonth = it }
                    if (everyMonth) {
                        Spacer(Modifier.height(6.dp))
                        Text("Goes into Monthly and repeats every 28th until you delete it.", fontSize = 12.sp, color = Mute)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = amt > 0, onClick = { onSave(amt, note.trim(), category, date, everyMonth) }) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { DialogButtons(onDelete, onDismiss) }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringDialog(
    initial: Recurring?,
    current: YearMonth,
    onDismiss: () -> Unit,
    onSave: (String, Long, String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amount by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var emoji by remember { mutableStateOf(initial?.emoji ?: "🏠") }
    val amt = amount.toLongOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add monthly cost" else "Edit monthly cost") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (Rent, Bike EMI…)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                AmountField(amount) { amount = it }
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FIXED_EMOJIS.forEach { e -> Chip(e, emoji == e, FixedC) { emoji = e } }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (initial == null) "Counts from ${cycleName(current, current)} and every month after, until you delete it."
                    else "Changes apply from ${cycleName(current, current)}. Deleting removes it from this month on, past months keep it.",
                    fontSize = 12.sp, color = Mute
                )
            }
        },
        confirmButton = {
            TextButton(enabled = amt > 0 && name.isNotBlank(), onClick = { onSave(name.trim(), amt, emoji) }) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { DialogButtons(onDelete, onDismiss) }
    )
}

@Composable
fun SalaryDialog(amount: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(amount.toString()) }
    val amt = text.toLongOrNull() ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Salary") },
        text = {
            Column {
                AmountField(text) { text = it }
                Spacer(Modifier.height(8.dp))
                Text("Applies from this month on. Past months keep their old salary.", fontSize = 12.sp, color = Mute)
            }
        },
        confirmButton = {
            TextButton(enabled = amt > 0, onClick = { onSave(amt) }) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
