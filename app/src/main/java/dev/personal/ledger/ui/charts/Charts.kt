package dev.personal.ledger.ui.charts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.personal.ledger.domain.Money
import dev.personal.ledger.ui.components.Haptics
import dev.personal.ledger.ui.theme.LedgerTheme
import dev.personal.ledger.ui.theme.Motion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A "nice" axis maximum and step (1, 2, 2.5, 5 × 10^n) so gridlines land on round numbers. */
internal fun niceScale(maxValue: Long, ticks: Int = 3): Pair<Long, Long> {
    if (maxValue <= 0) return 1L to 1L
    val raw = maxValue.toDouble() / ticks
    val mag = Math.pow(10.0, Math.floor(Math.log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= raw }
    val s = step.toLong().coerceAtLeast(1)
    return (s * ticks).let { if (it < maxValue) s * (ticks + 1) else it } to s
}

/** Pointer handling shared by scrubbable charts: press or drag reports the x position, release clears. */
internal fun Modifier.scrub(onMove: (x: Float?, width: Float, density: Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        onMove(down.position.x, size.width.toFloat(), density)
        var horizontalLock = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull() ?: break
            if (!change.pressed) break
            val delta = change.positionChange()
            if (!horizontalLock && abs(delta.x) > abs(delta.y)) horizontalLock = true
            if (horizontalLock) change.consume()
            onMove(change.position.x, size.width.toFloat(), density)
        }
        onMove(null, size.width.toFloat(), density)
    }
}

@Composable
internal fun rememberReveal(key: Any?): Animatable<Float, *> {
    val a = remember { Animatable(0f) }
    LaunchedEffect(key) { a.snapTo(0f); a.animateTo(1f, tween(650, easing = Motion.emphasized)) }
    return a
}

internal fun DrawScope.axisLabel(tm: TextMeasurer, text: String, x: Float, y: Float, style: TextStyle, alignEnd: Boolean = false, center: Boolean = false) {
    val r = tm.measure(text, style)
    val dx = when { alignEnd -> -r.size.width.toFloat(); center -> -r.size.width / 2f; else -> 0f }
    drawText(r, topLeft = Offset((x + dx).coerceIn(0f, size.width - r.size.width), y))
}

/**
 * Cumulative spending this month vs last month and the 3-month average.
 * Answers "Am I spending faster than usual?" — the gap between the lines is the answer.
 */
@Composable
fun CumulativeChart(
    current: LongArray,
    throughDay: Int,
    previous: LongArray,
    average: LongArray,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 188.dp,
    revealKey: Any? = null,
) {
    val c = LedgerTheme.colors
    val tm = rememberTextMeasurer()
    val labelStyle = LedgerTheme.type.caption.copy(color = c.textFaint)
    val reveal = rememberReveal(revealKey)
    val view = LocalView.current
    val days = current.size
    val maxV = maxOf(current.getOrElse(throughDay - 1) { 0 }, previous.maxOrNull() ?: 0, average.maxOrNull() ?: 0, current.take(throughDay.coerceAtLeast(0)).maxOrNull() ?: 0)
    val (top, step) = niceScale(maxV)
    Canvas(
        modifier.fillMaxWidth().height(height).scrub { x, width, density ->
            if (x == null) { onSelect(null); return@scrub }
            val w = width - 44 * density
            val idx = ((x / w) * (days - 1)).roundToInt().coerceIn(0, days - 1)
            if (idx != selected) { Haptics.scrub(view); onSelect(idx) }
        },
    ) {
        val right = 44.dp.toPx()
        val w = size.width - right
        val bottomPad = 22.dp.toPx()
        val h = size.height - bottomPad
        fun x(i: Int) = if (days <= 1) 0f else w * i / (days - 1)
        fun y(v: Long) = h - (v.toFloat() / top) * h * 0.96f

        // Grid and y labels.
        var g = 0L
        while (g <= top) {
            val yy = y(g)
            drawLine(c.chartGrid, Offset(0f, yy), Offset(w, yy), 1.dp.toPx())
            if (g > 0) axisLabel(tm, Money.compact(g), size.width, yy - 8.dp.toPx(), labelStyle, alignEnd = true)
            g += step
        }
        // X labels.
        listOf(1, 8, 15, 22, days).distinct().forEach { d ->
            axisLabel(tm, d.toString(), x(d - 1), h + 6.dp.toPx(), labelStyle, center = true)
        }

        fun series(values: LongArray, count: Int): Path {
            val p = Path()
            for (i in 0 until min(count, values.size)) {
                val px = x(i); val py = y(values[i])
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            return p
        }
        clipRect(right = w * reveal.value + 2f) {
            if (average.any { it > 0 }) drawPath(series(average, days), c.chartMuted, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))))
            if (previous.any { it > 0 }) drawPath(series(previous, days), c.chartMuted, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (throughDay > 0) {
                val line = series(current, throughDay)
                val area = Path().apply { addPath(line); lineTo(x(throughDay - 1), h); lineTo(0f, h); close() }
                drawPath(area, Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.22f), c.accent.copy(alpha = 0f)), startY = y(maxV), endY = h))
                drawPath(line, c.accent, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                val ex = x(throughDay - 1); val ey = y(current[throughDay - 1])
                drawCircle(c.accent.copy(alpha = 0.2f), 8.dp.toPx(), Offset(ex, ey))
                drawCircle(c.accent, 4.dp.toPx(), Offset(ex, ey))
            }
        }
        selected?.let { i ->
            val sx = x(i)
            drawLine(c.textFaint, Offset(sx, 0f), Offset(sx, h), 1.dp.toPx())
            if (previous.isNotEmpty()) drawCircle(c.chartMuted, 3.5.dp.toPx(), Offset(sx, y(previous[min(i, previous.size - 1)])))
            if (i < throughDay) {
                drawCircle(c.surface, 6.dp.toPx(), Offset(sx, y(current[i])))
                drawCircle(c.accent, 4.dp.toPx(), Offset(sx, y(current[i])))
            }
        }
    }
}

data class ForecastPointUi(val label: String, val known: Long, val estimate: Long, val inflow: Long, val outflow: Long)

/**
 * Balance forecast. Solid steps = scheduled items (known amounts or fixed schedules);
 * dashed = the same plus an estimate of everyday spending. Estimates are never drawn as solid.
 */
@Composable
fun ForecastChart(points: List<ForecastPointUi>, selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier, height: Dp = 180.dp, showEstimate: Boolean = true) {
    if (points.isEmpty()) return
    val c = LedgerTheme.colors
    val tm = rememberTextMeasurer()
    val labelStyle = LedgerTheme.type.caption.copy(color = c.textFaint)
    val reveal = rememberReveal(points.size)
    val view = LocalView.current
    val maxV = points.maxOf { max(it.known, if (showEstimate) it.estimate else it.known) }
    val minV = points.minOf { min(it.known, if (showEstimate) it.estimate else it.known) }
    // One step size for the whole range, with gridlines on round multiples (…, −10m, 0, 10m, 20m, …).
    val step = niceScale((max(maxV, 0) - min(minV, 0)).coerceAtLeast(1), 3).second
    val floor = if (minV < 0) Math.floorDiv(minV, step) * step else 0L
    val topNice = (Math.floorDiv(max(maxV, 1) - 1, step) + 1) * step
    Canvas(
        modifier.fillMaxWidth().height(height).scrub { x, width, density ->
            if (x == null) { onSelect(null); return@scrub }
            val w = width - 44 * density
            val idx = ((x / w) * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
            if (idx != selected) { Haptics.scrub(view); onSelect(idx) }
        },
    ) {
        val right = 44.dp.toPx()
        val w = size.width - right
        val h = size.height - 22.dp.toPx()
        val span = (topNice - floor).toFloat()
        fun x(i: Int) = w * i / (points.size - 1).coerceAtLeast(1)
        fun y(v: Long) = h - ((v - floor) / span) * h * 0.96f

        var g = floor
        while (g <= topNice) {
            val yy = y(g)
            drawLine(if (g == 0L && floor < 0) c.negative.copy(alpha = 0.5f) else c.chartGrid, Offset(0f, yy), Offset(w, yy), 1.dp.toPx())
            axisLabel(tm, Money.compact(g), size.width, yy - 8.dp.toPx(), labelStyle, alignEnd = true)
            g += step
        }
        val labelEvery = if (points.size > 30) 14 else 7
        points.forEachIndexed { i, p -> if (i % labelEvery == 0) axisLabel(tm, p.label, x(i), h + 6.dp.toPx(), labelStyle, center = i != 0) }

        clipRect(right = w * reveal.value + 2f) {
            if (showEstimate) {
                val est = Path()
                points.forEachIndexed { i, p -> if (i == 0) est.moveTo(x(i), y(p.estimate)) else est.lineTo(x(i), y(p.estimate)) }
                drawPath(est, c.accent.copy(alpha = 0.55f), style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()))))
            }
            val steps = Path()
            points.forEachIndexed { i, p ->
                val py = y(p.known)
                if (i == 0) steps.moveTo(x(i), py) else { steps.lineTo(x(i), y(points[i - 1].known)); steps.lineTo(x(i), py) }
            }
            val area = Path().apply { addPath(steps); lineTo(x(points.size - 1), h); lineTo(0f, h); close() }
            drawPath(area, Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.16f), c.accent.copy(alpha = 0f)), startY = 0f, endY = h))
            drawPath(steps, c.accent, style = Stroke(2.4.dp.toPx(), join = StrokeJoin.Round))
            points.forEachIndexed { i, p ->
                if (p.inflow > 0) drawCircle(c.positive, 3.5.dp.toPx(), Offset(x(i), y(p.known)))
                else if (p.outflow >= 1_000_000) drawCircle(c.text, 3.dp.toPx(), Offset(x(i), y(p.known)))
            }
        }
        selected?.let { i ->
            val sx = x(i)
            drawLine(c.textFaint, Offset(sx, 0f), Offset(sx, h), 1.dp.toPx())
            drawCircle(c.surface, 6.dp.toPx(), Offset(sx, y(points[i].known)))
            drawCircle(c.accent, 4.dp.toPx(), Offset(sx, y(points[i].known)))
        }
    }
}

data class MonthBarUi(val label: String, val spending: Long, val fixed: Long, val income: Long)

/** Six months side by side: bar = spending (fixed part solid), tick = income. Tap a month to select it. */
@Composable
fun MonthBarsChart(bars: List<MonthBarUi>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, height: Dp = 170.dp) {
    val c = LedgerTheme.colors
    val tm = rememberTextMeasurer()
    val labelStyle = LedgerTheme.type.caption.copy(color = c.textMuted)
    val valueStyle = LedgerTheme.type.caption.copy(color = c.text)
    val reveal = rememberReveal(bars.size)
    val view = LocalView.current
    val maxV = bars.maxOfOrNull { max(it.spending, it.income) } ?: 0
    val (top, _) = niceScale(maxV, 2)
    Canvas(
        modifier.fillMaxWidth().height(height).pointerInput(bars.size) {
            detectTapGestures { o ->
                val slot = size.width / bars.size
                val i = (o.x / slot).toInt().coerceIn(0, bars.size - 1)
                Haptics.tick(view); onSelect(i)
            }
        },
    ) {
        val labelH = 22.dp.toPx(); val valueH = 18.dp.toPx()
        val h = size.height - labelH - valueH
        val slot = size.width / bars.size
        val barW = min(slot * 0.46f, 30.dp.toPx())
        fun y(v: Long) = valueH + h - (v.toFloat() / top) * h * reveal.value
        bars.forEachIndexed { i, b ->
            val cx = slot * i + slot / 2
            val sel = i == selected
            val alpha = if (sel) 1f else 0.42f
            val left = cx - barW / 2
            // flexible (top) then fixed (bottom, solid)
            val yTop = y(b.spending); val yFixed = y(b.fixed); val base = valueH + h
            if (b.spending > 0) drawRoundRect(c.accent.copy(alpha = 0.45f * alpha), Offset(left, yTop), Size(barW, base - yTop), CornerRadius(6.dp.toPx()))
            if (b.fixed > 0) drawRoundRect(c.accent.copy(alpha = alpha), Offset(left, yFixed), Size(barW, base - yFixed), CornerRadius(6.dp.toPx()))
            if (b.income > 0) {
                val yi = y(b.income)
                drawLine(c.positive.copy(alpha = if (sel) 1f else 0.55f), Offset(cx - barW * 0.8f, yi), Offset(cx + barW * 0.8f, yi), 2.5.dp.toPx(), cap = StrokeCap.Round)
            }
            axisLabel(tm, b.label, cx, base + 6.dp.toPx(), if (sel) labelStyle.copy(color = c.text) else labelStyle, center = true)
            if (sel) axisLabel(tm, Money.compact(b.spending), cx, (yTop - valueH).coerceAtLeast(0f), valueStyle, center = true)
        }
    }
}

/** Month calendar heatmap. Intensity = quantile of daily spending, so one huge day doesn't flatten the rest. */
@Composable
fun CalendarHeatmap(
    daily: LongArray,
    firstDayOffset: Int,
    todayIndex: Int?,
    selected: Int?,
    onDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LedgerTheme.colors
    val tm = rememberTextMeasurer()
    val dayStyle = LedgerTheme.type.caption
    val view = LocalView.current
    val rows = (firstDayOffset + daily.size + 6) / 7
    val sorted = daily.filter { it > 0 }.sorted()
    fun level(v: Long): Int {
        if (v <= 0 || sorted.isEmpty()) return 0
        val rank = sorted.indexOfFirst { it >= v }.let { if (it < 0) sorted.size - 1 else it }
        return 1 + (rank * 4 / sorted.size).coerceIn(0, 3)
    }
    val alphas = listOf(0f, 0.18f, 0.36f, 0.62f, 0.92f)
    Canvas(
        modifier.fillMaxWidth().height(((rows * 46) + 4).dp).pointerInput(daily.size, firstDayOffset) {
            detectTapGestures { o ->
                val cell = size.width / 7f
                val col = (o.x / cell).toInt(); val row = (o.y / 46.dp.toPx()).toInt()
                val idx = row * 7 + col - firstDayOffset
                if (idx in daily.indices && (todayIndex == null || idx <= todayIndex)) { Haptics.tick(view); onDay(idx) }
            }
        },
    ) {
        val cell = size.width / 7f
        val gap = 4.dp.toPx()
        val cellH = 46.dp.toPx()
        for (i in daily.indices) {
            val pos = i + firstDayOffset
            val col = pos % 7; val row = pos / 7
            val tl = Offset(col * cell + gap / 2, row * cellH + gap / 2)
            val sz = Size(cell - gap, cellH - gap)
            val future = todayIndex != null && i > todayIndex
            val lv = if (future) 0 else level(daily[i])
            drawRoundRect(if (lv == 0) c.surfaceAlt.copy(alpha = if (future) 0.5f else 1f) else c.accent.copy(alpha = alphas[lv]), tl, sz, CornerRadius(10.dp.toPx()))
            if (i == selected) drawRoundRect(c.text, tl, sz, CornerRadius(10.dp.toPx()), style = Stroke(2.dp.toPx()))
            else if (i == todayIndex) drawRoundRect(c.accent, tl, sz, CornerRadius(10.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            val textColor = when { future -> c.textFaint.copy(alpha = 0.6f); lv >= 3 -> c.onAccent; else -> c.textMuted }
            val r = tm.measure((i + 1).toString(), dayStyle.copy(color = textColor))
            drawText(r, topLeft = Offset(tl.x + (sz.width - r.size.width) / 2, tl.y + (sz.height - r.size.height) / 2))
        }
    }
}

data class FlowNode(val label: String, val amount: Long, val color: Color)

/**
 * Two-column money flow: where income came from → where it went (top categories, Other, Kept).
 * Ribbons are proportional; every destination gets a label slot of at least 40dp so small flows stay legible.
 */
@Composable
fun MoneyFlowChart(sources: List<FlowNode>, targets: List<FlowNode>, modifier: Modifier = Modifier) {
    val c = LedgerTheme.colors
    val tm = rememberTextMeasurer()
    val nameStyle = LedgerTheme.type.label.copy(color = c.text)
    val valStyle = LedgerTheme.type.caption.copy(color = c.textMuted)
    val reveal = rememberReveal(targets.size + sources.size)
    val total = max(sources.sumOf { it.amount }, targets.sumOf { it.amount }).coerceAtLeast(1)
    val bodyDp = 240f; val minSlot = 40f; val gapDp = 6f
    val nodeDp = targets.map { max(bodyDp * it.amount / total, 3f) }
    val slotDp = nodeDp.map { max(it, minSlot) }
    val heightDp = slotDp.sum() + gapDp * (targets.size - 1)
    Canvas(modifier.fillMaxWidth().height(heightDp.dp)) {
        val px = density
        val nodeW = 8.dp.toPx()
        val rightX = size.width * 0.42f
        // Destination nodes centred in their slots.
        var y = 0f
        val dst = targets.indices.map { i ->
            val top = y + (slotDp[i] - nodeDp[i]) * px / 2
            y += (slotDp[i] + gapDp) * px
            top to nodeDp[i] * px
        }
        // Sources stacked and centred vertically.
        val srcH = sources.map { max(bodyDp * it.amount / total, 3f) * px }
        val srcBlock = srcH.sum() + 4.dp.toPx() * (sources.size - 1)
        var sy = (size.height - srcBlock) / 2
        val src = srcH.map { h -> (sy to h).also { sy += h + 4.dp.toPx() } }
        val srcCursor = src.map { it.first }.toMutableList()
        val dstCursor = dst.map { it.first }.toMutableList()
        val srcTotal = sources.sumOf { it.amount }.coerceAtLeast(1)
        targets.forEachIndexed { ti, t ->
            sources.forEachIndexed { si, s ->
                val share = t.amount.toDouble() * s.amount / srcTotal
                val h = (bodyDp * share / total).toFloat() * px
                val y0 = srcCursor[si]; val y1 = dstCursor[ti]
                val x0 = nodeW; val x1 = rightX
                val mid = (x0 + x1) / 2
                val path = Path().apply {
                    moveTo(x0, y0); cubicTo(mid, y0, mid, y1, x1, y1)
                    lineTo(x1, y1 + h); cubicTo(mid, y1 + h, mid, y0 + h, x0, y0 + h); close()
                }
                drawPath(path, t.color.copy(alpha = (if (c.isDark) 0.16f else 0.10f) * reveal.value))
                srcCursor[si] += h; dstCursor[ti] += h
            }
        }
        src.forEachIndexed { i, (top, h) -> drawRoundRect(sources[i].color, Offset(0f, top), Size(nodeW, h), CornerRadius(3.dp.toPx())) }
        dst.forEachIndexed { i, (top, h) ->
            drawRoundRect(targets[i].color, Offset(rightX, top), Size(nodeW, h), CornerRadius(3.dp.toPx()))
            val cy = top + h / 2
            val name = tm.measure(targets[i].label, nameStyle)
            val value = tm.measure(Money.compact(targets[i].amount) + " · " + (targets[i].amount * 100 / total) + "%", valStyle)
            val tx = rightX + nodeW + 10.dp.toPx()
            drawText(name, topLeft = Offset(tx, cy - name.size.height))
            drawText(value, topLeft = Offset(tx, cy))
        }
    }
}

/** Simple area line for balance / net-worth history with scrubbing. */
@Composable
fun LineChart(values: List<Long>, labels: List<String>, selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier, height: Dp = 150.dp, color: Color = LedgerTheme.colors.accent) {
    if (values.size < 2) return
    val c = LedgerTheme.colors
    val tm = rememberTextMeasurer()
    val labelStyle = LedgerTheme.type.caption.copy(color = c.textFaint)
    val reveal = rememberReveal(values.size)
    val view = LocalView.current
    val maxV = values.max(); val minV = values.min()
    val pad = ((maxV - minV) * 0.15).toLong().coerceAtLeast(1)
    val lo = minV - pad; val hi = maxV + pad
    Canvas(
        modifier.fillMaxWidth().height(height).scrub { x, width, _ ->
            if (x == null) { onSelect(null); return@scrub }
            val idx = ((x / width) * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
            if (idx != selected) { Haptics.scrub(view); onSelect(idx) }
        },
    ) {
        val h = size.height - 22.dp.toPx()
        fun x(i: Int) = size.width * i / (values.size - 1)
        fun y(v: Long) = h - ((v - lo).toFloat() / (hi - lo)) * h
        if (lo < 0 && hi > 0) drawLine(c.negative.copy(alpha = 0.4f), Offset(0f, y(0)), Offset(size.width, y(0)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
        val line = Path()
        values.forEachIndexed { i, v -> if (i == 0) line.moveTo(x(i), y(v)) else line.lineTo(x(i), y(v)) }
        clipRect(right = size.width * reveal.value + 2f) {
            val area = Path().apply { addPath(line); lineTo(size.width, h); lineTo(0f, h); close() }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.2f), color.copy(alpha = 0f)), endY = h))
            drawPath(line, color, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        val every = ((labels.size + 4) / 5).coerceAtLeast(1)
        labels.forEachIndexed { i, l -> if (i % every == 0 || i == labels.lastIndex) axisLabel(tm, l, x(i), h + 6.dp.toPx(), labelStyle, alignEnd = i == labels.lastIndex, center = i != 0 && i != labels.lastIndex) }
        selected?.let { i ->
            drawLine(c.textFaint, Offset(x(i), 0f), Offset(x(i), h), 1.dp.toPx())
            drawCircle(c.surface, 6.dp.toPx(), Offset(x(i), y(values[i])))
            drawCircle(color, 4.dp.toPx(), Offset(x(i), y(values[i])))
        }
    }
}

/** Horizontal stacked distribution bar (e.g. where money lives, fixed vs flexible). */
@Composable
fun StackedBar(parts: List<Pair<Long, Color>>, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val total = parts.sumOf { it.first }.coerceAtLeast(1)
    val reveal = rememberReveal(parts.size)
    Canvas(modifier.fillMaxWidth().height(height)) {
        var x = 0f
        val gap = 2.dp.toPx()
        parts.forEach { (v, col) ->
            val w = (size.width - gap * (parts.size - 1)) * v / total * reveal.value
            if (w > 0) drawRoundRect(col, Offset(x, 0f), Size(w, size.height), CornerRadius(size.height / 2))
            x += w + gap
        }
    }
}
