package dev.personal.ledger.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.personal.ledger.i18n.tr
import dev.personal.ledger.ui.UiMessage
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Motion
import dev.personal.ledger.ui.theme.Shapes
import dev.personal.ledger.ui.theme.Space
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Lightweight bottom sheet: fast spring in (240 ms), faster exit, drag down or tap the scrim to dismiss.
 * Custom instead of ModalBottomSheet so the entry surface opens instantly with the numpad already in place.
 */
@Composable
fun <T : Any> SheetHost(request: T?, onDismiss: () -> Unit, content: @Composable ColumnScope.(T) -> Unit) {
    var last by remember { mutableStateOf(request) }
    if (request != null) last = request
    val c = LedgerTheme.colors
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(request != null, enter = fadeIn(tween(Motion.MICRO)), exit = fadeOut(tween(Motion.FAST))) {
            Box(Modifier.fillMaxSize().background(c.scrim).clickable(MutableInteractionSource(), null, onClick = onDismiss))
        }
        AnimatedVisibility(
            request != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(Motion.STANDARD, easing = Motion.emphasized)) { it / 2 } + fadeIn(tween(Motion.FAST)),
            exit = slideOutVertically(tween(Motion.MICRO, easing = Motion.exit)) { it / 2 } + fadeOut(tween(Motion.MICRO)),
        ) {
            val drag = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            val threshold = with(LocalDensity.current) { 110.dp.toPx() }
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(top = 24.dp)
                    .offset { IntOffset(0, drag.value.roundToInt()) }
                    .fillMaxWidth()
                    .clip(Shapes.sheet)
                    .background(c.surface)
                    .clickable(MutableInteractionSource(), null) {}
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                // Grabber doubles as the drag handle so inner scrolling never fights the dismiss gesture.
                Box(
                    Modifier.fillMaxWidth().height(22.dp)
                        .draggable(
                            rememberDraggableState { d -> scope.launch { drag.snapTo((drag.value + d).coerceAtLeast(0f)) } },
                            Orientation.Vertical,
                            onDragStopped = { v -> if (drag.value > threshold || v > 1800f) onDismiss() else drag.animateTo(0f, Motion.snappy()) },
                        ),
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(c.hairline)) }
                last?.let { content(it) }
            }
        }
    }
}

/** Bottom snackbar with Undo. Auto-dismisses after 5 s; never blocks the UI underneath. */
@Composable
fun BoxScope.UndoBar(message: UiMessage?, bottomInset: androidx.compose.ui.unit.Dp, onDismiss: (Long) -> Unit, onUndo: (UiMessage) -> Unit) {
    var last by remember { mutableStateOf(message) }
    if (message != null) last = message
    LaunchedEffect(message?.id) {
        if (message != null) { delay(5000); onDismiss(message.id) }
    }
    val c = LedgerTheme.colors
    AnimatedVisibility(
        message != null,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomInset, start = Space.l, end = Space.l),
        enter = slideInVertically(Motion.gentle()) { it } + fadeIn(tween(Motion.FAST)),
        exit = slideOutVertically(tween(Motion.MICRO)) { it } + fadeOut(tween(Motion.FAST)),
    ) {
        val m = last ?: return@AnimatedVisibility
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(Shapes.button).background(c.cardFace)
                .padding(start = Space.l, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = c.onCardFace.copy(alpha = 0.7f))
            Spacer(Modifier.width(10.dp))
            Text(m.text, style = LedgerTheme.type.label, color = c.onCardFace, modifier = Modifier.weight(1f), maxLines = 2)
            if (m.undo != null) {
                Text(
                    tr("Undo"), style = LedgerTheme.type.bodyStrong, color = c.onCardFace,
                    modifier = Modifier.clip(Shapes.chip).clickable(role = Role.Button) { onUndo(m) }.padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * Calculator-style keypad. The Save key is the tallest target and sits in the bottom-right thumb zone.
 * `000` makes VND amounts two taps shorter.
 */
@Composable
fun Numpad(onDigit: (String) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit, saveEnabled: Boolean, saveLabel: String = tr("Save"), onSave: () -> Unit, modifier: Modifier = Modifier) {
    val c = LedgerTheme.colors
    val view = LocalView.current
    val gap = 6.dp
    val keyH = 60.dp
    @Composable
    fun Key(label: String, m: Modifier, onLong: (() -> Unit)? = null, desc: String = label, click: () -> Unit, content: @Composable () -> Unit = {
        Text(label, style = LedgerTheme.type.numpad, color = c.text)
    }) {
        Box(
            // Borderless keys: the digits are the interface; the press ripple is the feedback.
            m.height(keyH).clip(Shapes.key)
                .combinedClickable(onLongClick = onLong, role = Role.Button) { Haptics.tick(view); click() }
                .semantics { contentDescription = desc },
            contentAlignment = Alignment.Center,
        ) { content() }
    }
    Row(modifier.fillMaxWidth().padding(horizontal = Space.l), horizontalArrangement = Arrangement.spacedBy(gap)) {
        Column(Modifier.weight(3f), verticalArrangement = Arrangement.spacedBy(gap)) {
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("000", "0")).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { d -> Key(d, Modifier.weight(1f), click = { onDigit(d) }) }
                    if (row.size == 2) Key("", Modifier.weight(1f), onLong = onClear, desc = tr("Delete"), click = onBackspace) {
                        Icon(Icons.AutoMirrored.Rounded.Backspace, null, tint = c.text, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
        Box(
            Modifier.weight(1f).height(keyH * 4 + gap * 3).clip(Shapes.key)
                .background(if (saveEnabled) c.accent else c.surfaceAlt)
                .clickable(enabled = saveEnabled, role = Role.Button) { Haptics.confirm(view); onSave() }
                .semantics { contentDescription = saveLabel },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Check, null, tint = if (saveEnabled) c.onAccent else c.textFaint, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(4.dp))
                Text(saveLabel, style = LedgerTheme.type.label, color = if (saveEnabled) c.onAccent else c.textFaint)
            }
        }
    }
}
