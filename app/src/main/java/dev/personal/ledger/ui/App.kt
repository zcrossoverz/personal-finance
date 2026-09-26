package dev.personal.ledger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.ui.unit.sp
import dev.personal.ledger.i18n.tr
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.personal.ledger.data.TxType
import dev.personal.ledger.ui.components.Divider
import dev.personal.ledger.ui.components.Haptics
import dev.personal.ledger.ui.components.SheetHost
import dev.personal.ledger.ui.components.UndoBar
import dev.personal.ledger.ui.entry.EntrySheet
import dev.personal.ledger.ui.entry.SplitSheet
import dev.personal.ledger.ui.entry.TxDetailSheet
import dev.personal.ledger.ui.home.HomeScreen
import dev.personal.ledger.ui.insights.CategoryDetailScreen
import dev.personal.ledger.ui.insights.DaySheet
import dev.personal.ledger.ui.insights.InsightsScreen
import dev.personal.ledger.ui.money.AccountDetailScreen
import dev.personal.ledger.ui.money.ActivityScreen
import dev.personal.ledger.ui.money.EditAccountScreen
import dev.personal.ledger.ui.money.MoneyScreen
import dev.personal.ledger.ui.plan.EditInstallmentScreen
import dev.personal.ledger.ui.plan.EditRecurringScreen
import dev.personal.ledger.ui.plan.InstallmentDetailScreen
import dev.personal.ledger.ui.plan.InstallmentListScreen
import dev.personal.ledger.ui.plan.PlanScreen
import dev.personal.ledger.ui.plan.RecurringListScreen
import dev.personal.ledger.ui.plan.SafeToSpendSheet
import dev.personal.ledger.ui.settings.PresetsScreen
import dev.personal.ledger.ui.settings.SettingsScreen
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Motion
import dev.personal.ledger.ui.theme.Space

/** Navigation state: one back stack per tab, so switching tabs never loses your place. */
class Nav {
    var tab by mutableStateOf(Tab.HOME)
    private val stacks = Tab.entries.associateWith { mutableStateListOf<Route>() }
    var sheet by mutableStateOf<SheetRequest?>(null)
    var forward by mutableStateOf(true)
        private set

    val stack get() = stacks.getValue(tab)
    val top: Route? get() = stack.lastOrNull()

    fun push(r: Route) { forward = true; stack.add(r) }
    fun pop(): Boolean { if (stack.isEmpty()) return false; forward = false; stack.removeAt(stack.lastIndex); return true }
    fun select(t: Tab) {
        if (t == tab) { if (stack.isNotEmpty()) { forward = false; stack.clear() } } else { forward = true; tab = t }
    }
    fun open(s: SheetRequest) { sheet = s }
    fun closeSheet() { sheet = null }
    fun entry(req: EntryRequest) { sheet = SheetRequest.Entry(req) }
}

@Composable
fun LedgerRoot(vm: LedgerViewModel, launch: MutableState<LaunchAction?>) {
    val nav = vm.nav
    val dash by vm.dashboard.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val c = LedgerTheme.colors
    val holder = rememberSaveableStateHolder()

    // External entry points (notification → bill amount, launcher shortcut → preset / command).
    LaunchedEffect(launch.value, dash != null) {
        val a = launch.value ?: return@LaunchedEffect
        val d = dash ?: return@LaunchedEffect
        when (a) {
            is LaunchAction.PayRecurring -> d.data.recurring.firstOrNull { it.id == a.id }?.let { r ->
                nav.entry(EntryRequest(type = if (r.kind == dev.personal.ledger.data.RecurringKind.INCOME) TxType.INCOME else TxType.EXPENSE,
                    categoryId = r.categoryId, accountId = r.accountId, amount = if (r.variable) 0 else r.amount, recurringId = r.id))
            }
            is LaunchAction.PayCard -> d.cards.firstOrNull { it.account.id == a.id }?.let { s ->
                nav.entry(EntryRequest(type = TxType.TRANSFER, toAccountId = s.account.id, amount = s.statementBalance, cardId = s.account.id))
            }
            is LaunchAction.Preset -> nav.entry(EntryRequest(presetId = a.id))
            LaunchAction.Command -> nav.entry(EntryRequest(composer = true, command = true))
            LaunchAction.Plan -> nav.select(Tab.PLAN)
        }
        launch.value = null
    }

    BackHandler(nav.sheet != null) { nav.closeSheet() }
    BackHandler(nav.sheet == null && nav.stack.isNotEmpty()) { nav.pop() }
    BackHandler(nav.sheet == null && nav.stack.isEmpty() && nav.tab != Tab.HOME) { nav.select(Tab.HOME) }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                val key: Any = nav.top ?: nav.tab
                AnimatedContent(
                    targetState = key,
                    transitionSpec = {
                        val tabSwitch = initialState is Tab && targetState is Tab
                        when {
                            tabSwitch -> fadeIn(tween(Motion.MICRO)) togetherWith fadeOut(tween(Motion.FAST))
                            nav.forward -> (slideInHorizontally(tween(Motion.STANDARD, easing = Motion.emphasized)) { it / 4 } + fadeIn(tween(Motion.MICRO))) togetherWith
                                (slideOutHorizontally(tween(Motion.STANDARD)) { -it / 8 } + fadeOut(tween(Motion.FAST)))
                            else -> (slideInHorizontally(tween(Motion.STANDARD, easing = Motion.emphasized)) { -it / 8 } + fadeIn(tween(Motion.MICRO))) togetherWith
                                (slideOutHorizontally(tween(Motion.MICRO)) { it / 4 } + fadeOut(tween(Motion.FAST)))
                        }
                    },
                    label = "nav",
                ) { k ->
                    holder.SaveableStateProvider(k.toString()) {
                        val d = dash
                        if (d == null) Box(Modifier.fillMaxSize())
                        else when (k) {
                            Tab.HOME -> HomeScreen(d, vm, nav)
                            Tab.MONEY -> MoneyScreen(d, vm, nav)
                            Tab.PLAN -> PlanScreen(d, vm, nav)
                            Tab.INSIGHTS -> InsightsScreen(d, vm, nav)
                            is Route.Activity -> ActivityScreen(d, vm, nav, k.query)
                            is Route.AccountDetail -> AccountDetailScreen(d, vm, nav, k.id)
                            is Route.RecurringList -> RecurringListScreen(d, vm, nav, k.kind)
                            Route.InstallmentList -> InstallmentListScreen(d, vm, nav)
                            is Route.InstallmentDetail -> InstallmentDetailScreen(d, vm, nav, k.id)
                            is Route.CategoryDetail -> CategoryDetailScreen(d, vm, nav, k.categoryId, k.month)
                            Route.Settings -> SettingsScreen(d, vm, nav)
                            Route.Presets -> PresetsScreen(d, vm, nav)
                            is Route.EditAccount -> EditAccountScreen(d, vm, nav, k.id)
                            is Route.EditRecurring -> EditRecurringScreen(d, vm, nav, k.id, k.kind)
                            is Route.EditInstallment -> EditInstallmentScreen(d, vm, nav, k.id)
                        }
                    }
                }
            }
            BottomBar(nav)
        }

        // Content scrolls under a translucent status-bar scrim instead of colliding with the clock.
        Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(c.bg.copy(alpha = 0.94f)))

        UndoBar(message, bottomInset = 88.dp, onDismiss = vm::dismissMessage) { m ->
            vm.dismissMessage(m.id)
            m.undo?.let { u -> vm.run { u() } }
        }

        val d = dash
        SheetHost(nav.sheet, onDismiss = nav::closeSheet) { s ->
            if (d != null) when (s) {
                is SheetRequest.Entry -> EntrySheet(d, vm, nav, s.req)
                is SheetRequest.TxDetail -> TxDetailSheet(d, vm, nav, s.id)
                is SheetRequest.Day -> DaySheet(d, nav, s.date)
                SheetRequest.SafeToSpend -> SafeToSpendSheet(d, nav)
                is SheetRequest.Split -> SplitSheet(d, vm, nav, s.txId)
            }
        }
    }
}

@Composable
private fun BottomBar(nav: Nav) {
    val c = LedgerTheme.colors
    val view = LocalView.current
    Column(Modifier.fillMaxWidth().background(c.bg)) {
        Divider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(62.dp).padding(horizontal = Space.s), verticalAlignment = Alignment.CenterVertically) {
            // Outlined when idle, filled when selected: state is carried by the glyph, not by a coloured pill.
            val items: List<Triple<Tab, ImageVector, ImageVector>> = listOf(
                Triple(Tab.HOME, Icons.Outlined.Home, Icons.Rounded.Home),
                Triple(Tab.MONEY, Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
                Triple(Tab.PLAN, Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
                Triple(Tab.INSIGHTS, Icons.Outlined.Insights, Icons.Rounded.Insights),
            )
            items.forEachIndexed { i, (t, idle, active) ->
                // Capture lives in the centre of the bar: always in thumb reach, never covering content.
                if (i == 2) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(52.dp, 42.dp).clip(RoundedTab).background(c.accent)
                            .clickable(role = Role.Button) { Haptics.tick(view); nav.entry(EntryRequest(composer = true)) }
                            .semantics { contentDescription = tr("New transaction") },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Add, null, tint = c.onAccent, modifier = Modifier.size(24.dp)) }
                }
                val sel = nav.tab == t
                Column(
                    Modifier.weight(1f).height(56.dp).clip(RoundedTab).clickable(role = Role.Tab) { Haptics.tick(view); nav.select(t) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(if (sel) active else idle, null, tint = if (sel) c.text else c.textFaint, modifier = Modifier.size(23.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(t.label, style = LedgerTheme.type.caption.copy(fontSize = 11.sp), color = if (sel) c.text else c.textFaint, maxLines = 1)
                }
            }
        }
    }
}

private val RoundedTab = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
