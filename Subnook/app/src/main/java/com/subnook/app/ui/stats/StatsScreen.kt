package com.subnook.app.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.mutableStateOf
import com.subnook.app.ui.components.CurrencyPickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnook.app.data.Category
import com.subnook.app.data.Currencies
import com.subnook.app.data.PayMethod
import com.subnook.app.data.Subscription
import com.subnook.app.data.SummaryPeriod
import com.subnook.app.domain.Billing
import com.subnook.app.ui.MainViewModel
import com.subnook.app.ui.UiState
import com.subnook.app.ui.components.Group
import com.subnook.app.ui.components.SoftCard
import com.subnook.app.ui.components.SubIcon
import com.subnook.app.ui.theme.LocalExtra
import com.subnook.app.ui.theme.TabularNums
import java.time.LocalDate
import java.time.YearMonth

/** 统计图表用的分类配色（与品牌色无关，保证区分度） */
private val ChartColors = listOf(
    Color(0xFF6366F1), Color(0xFFF59E0B), Color(0xFF10B981), Color(0xFFEF4444), Color(0xFF06B6D4),
    Color(0xFFEC4899), Color(0xFF84CC16), Color(0xFFF97316), Color(0xFF8B5CF6), Color(0xFF14B8A6),
    Color(0xFF64748B), Color(0xFFA855F7), Color(0xFFEAB308), Color(0xFF0EA5E9),
)

/** 柱状图往前显示几个月 */
private const val PAST_MONTHS = 12

private data class Slice(val label: String, val value: Double, val color: Color, val count: Int)

@Composable
fun StatsScreen(vm: MainViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val st by vm.state.collectAsState()
    val payments by vm.observeAllPayments().collectAsState(emptyList())
    val cs = MaterialTheme.colorScheme
    val cur = st.settings.displayCurrency
    val active = st.active
    var pickCurrency by remember { mutableStateOf(false) }
    // 紫色卡片的大数字是平均数；下面默认按「未来一年」实际待付，点柱状图某个月就只看那个月（整月按账单计划），点空白处回到未来一年
    val today = LocalDate.now()
    val end = Billing.yearEnd(today)
    // 每月柱状图：前 12 个月 + 本月起 12 个月，可以左右滑；每个月都按账单计划算整月
    // （本月 1 号到今天的账单、装 App 之前的也算，不早于起始账单日），和首页「本月」小计一致
    val months = (-PAST_MONTHS until 12).map { YearMonth.from(today).plusMonths(it.toLong()) }
    val ranges = months.map { ym -> ym.atDay(1) to ym.atEndOfMonth() }
    val perMonth = ranges.map { (f, t) -> active.sumOf { st.rates.convert(Billing.scheduledCost(it, f, t), it.currency, cur) } }
    var selMonth by remember { mutableStateOf<YearMonth?>(null) }
    val selIdx = months.indexOf(selMonth).takeIf { it >= 0 }
    val scope = selIdx?.let { "${months[it].year}年${months[it].monthValue}月" } ?: "未来一年"
    // 所选时间段内每项要付多少（原币种）；这段时间没有账单的不参与下面的统计
    val yearOrig = active.associate { it.id to Billing.upcomingCost(it, today, end) }
    val origOf = selIdx?.let { i -> active.associate { it.id to Billing.scheduledCost(it, ranges[i].first, ranges[i].second) } } ?: yearOrig
    val inScope = active.filter { origOf.getValue(it.id) > 0 }
    fun cost(s: Subscription) = st.rates.convert(origOf.getValue(s.id), s.currency, cur)
    val scopeTotal = inScope.sumOf(::cost)

    // 颜色按「未来一年」的排名固定下来，切换月份时同一个分类 / 付款方式不变色（饼图动画才连贯）
    fun colorsBy(key: (Subscription) -> String, offset: Int): Map<String, Color> =
        active.groupBy(key).entries.sortedByDescending { (_, l) -> l.sumOf { st.rates.convert(yearOrig.getValue(it.id), it.currency, cur) } }
            .mapIndexed { i, (k, _) -> k to ChartColors[(i + offset) % ChartColors.size] }.toMap()
    val categoryColors = colorsBy({ it.categoryLabel }, 0)
    val methodColors = colorsBy({ it.payMethodLabel }, 3)

    // 分类
    val byCategory = inScope.groupBy { it.categoryLabel }.map { (c, l) -> c to l }
        .sortedByDescending { (_, l) -> l.sumOf(::cost) }
        .map { (c, l) -> Slice(c, l.sumOf(::cost), categoryColors.getValue(c), l.size) }

    // 已付
    val byId = st.subs.associateBy { it.id }
    val paidThisYear = payments.filter { LocalDate.ofEpochDay(it.billingDay).year == today.year }
        .sumOf { st.rates.convert(it.amount, it.currency, cur) }
    val paidAll = payments.sumOf { st.rates.convert(it.amount, it.currency, cur) }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text("支出统计", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background, scrolledContainerColor = cs.background),
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { PeriodTiles(st, onCurrency = { pickCurrency = true }) }

            if (active.isEmpty()) {
                item {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Text("还没有启用中的订阅，添加后这里会显示统计。", Modifier.padding(24.dp), color = cs.onSurfaceVariant)
                    }
                }
                return@LazyColumn
            }

            item {
                // 大数字始终等于下面各组统计的合计：默认未来一年（和紫色卡片一致），选中月份时是那个月整月
                // 标题就是当前统计范围（未来一年 / 2027年1月），和下面各组的「（未来一年）」一致
                Group(scope) {
                    // 点卡片里柱子以外的空白处：取消选中，回到未来一年
                    Column(Modifier.clickable(interactionSource = null, indication = null) { selMonth = null }.padding(16.dp)) {
                        Text(
                            Currencies.format(scopeTotal, cur),
                            style = MaterialTheme.typography.headlineSmall.merge(TabularNums),
                        )
                        Spacer(Modifier.height(14.dp))
                        MonthBars(months, perMonth, PAST_MONTHS, selIdx) { i -> selMonth = if (i == selIdx) null else months[i] }
                    }
                }
            }

            if (inScope.isEmpty()) {
                item {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Text("${scope}没有账单", Modifier.padding(24.dp), color = cs.onSurfaceVariant)
                    }
                }
            } else {
                item {
                    Group("按分类（$scope）") {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Donut(byCategory, Modifier.size(128.dp)) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${byCategory.size}", style = MaterialTheme.typography.headlineSmall)
                                        Text("个分类", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                                    }
                                }
                                Spacer(Modifier.width(18.dp))
                                byCategory.firstOrNull()?.let { top ->
                                    Column(Modifier.weight(1f)) {
                                        Text("花得最多", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(Modifier.size(10.dp).clip(CircleShape).background(top.color))
                                            Spacer(Modifier.width(6.dp))
                                            Text(top.label, style = MaterialTheme.typography.titleMedium)
                                        }
                                        Text(
                                            "${Currencies.format(top.value, cur)} · ${top.count} 个",
                                            style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = cs.onSurfaceVariant,
                                        )
                                        Text(
                                            "占 ${if (scopeTotal > 0) kotlin.math.round(top.value / scopeTotal * 100).toInt() else 0}%",
                                            style = MaterialTheme.typography.headlineSmall, color = top.color,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                byCategory.forEach { s -> LegendRow(s, scopeTotal, cur) }
                            }
                        }
                    }
                }

                item {
                    Group("花费排行（$scope）") {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            inScope.sortedByDescending(::cost).take(10).forEachIndexed { i, s ->
                                RankRow(i + 1, s, cost(s), scopeTotal, cur) { onOpen(s.id) }
                            }
                        }
                    }
                }

                item {
                    val byMethod = inScope.groupBy { it.payMethodLabel }
                        .map { (m, l) -> m to l }.sortedByDescending { (_, l) -> l.sumOf(::cost) }
                        .map { (m, l) -> Slice(m, l.sumOf(::cost), methodColors.getValue(m), l.size) }
                    Group("按付款方式（$scope）") {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            StackBar(byMethod)
                            byMethod.forEach { s -> LegendRow(s, scopeTotal, cur) }
                        }
                    }
                }

                // 有分期时：订阅和分期各占多少（两者都已计入上面的总额和各项统计）
                if (inScope.any { it.isInstallment }) item {
                    val subs = inScope.filter { !it.isInstallment }
                    val inst = inScope.filter { it.isInstallment }
                    val parts = listOf(
                        Slice("订阅", subs.sumOf(::cost), ChartColors[0], subs.size),
                        Slice("分期还款", inst.sumOf(::cost), ChartColors[7], inst.size),
                    ).filter { it.count > 0 }
                    val left = active.filter { it.isInstallment }
                        .sumOf { st.rates.convert(com.subnook.app.domain.Installments.summary(it).remainingAmount, it.currency, cur) }
                    Group("订阅 / 分期（$scope）") {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            StackBar(parts)
                            parts.forEach { s -> LegendRow(s, scopeTotal, cur) }
                            Text(
                                "分期剩余待还 ${Currencies.format(left, cur)}",
                                style = MaterialTheme.typography.bodySmall.merge(TabularNums), color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    val auto = inScope.filter { it.autoRenew }
                    val manual = inScope.filter { !it.autoRenew }
                    val extra = LocalExtra.current
                    val parts = listOf(
                        Slice("自动续费", auto.sumOf(::cost), extra.success, auto.size),
                        Slice("手动付款", manual.sumOf(::cost), extra.warning, manual.size),
                    ).filter { it.count > 0 }
                    Group("自动 / 手动（$scope）") {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            StackBar(parts)
                            parts.forEach { s -> LegendRow(s, scopeTotal, cur) }
                        }
                    }
                }

                item {
                    val byCur = inScope.groupBy { it.currency }.map { (c, l) -> c to l }
                        .sortedByDescending { (_, l) -> l.sumOf(::cost) }
                    Group("按原始币种（$scope）") {
                        Column(Modifier.padding(vertical = 6.dp)) {
                            byCur.forEach { (c, l) ->
                                val orig = l.sumOf { origOf.getValue(it.id) }
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    // 只有这一栏显示币种代码，方便区分 ¥(CNY) 和 JP¥ 等
                                    Box(
                                        Modifier.clip(RoundedCornerShape(8.dp)).background(cs.surfaceContainer).padding(horizontal = 8.dp, vertical = 3.dp),
                                    ) { Text(c, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "${Currencies.name(c)} · ${l.size} 个", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(Currencies.format(orig, c), style = MaterialTheme.typography.titleSmall.merge(TabularNums))
                                        if (c != cur) Text("≈ " + Currencies.format(l.sumOf(::cost), cur), style = MaterialTheme.typography.labelSmall, color = cs.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Group("已付款记录") {
                    Row(Modifier.padding(16.dp)) {
                        StatCell("今年已付", Currencies.format(paidThisYear, cur), Modifier.weight(1f))
                        StatCell("累计已付", Currencies.format(paidAll, cur), Modifier.weight(1f))
                        StatCell("付款笔数", "${payments.size}", Modifier.weight(0.7f))
                    }
                    if (payments.isNotEmpty()) {
                        // 最多显示 3 条，多的在框里滑动（以前只显示最近 5 条）
                        val recent = payments.filter { byId.containsKey(it.subscriptionId) }
                        com.subnook.app.ui.components.CappedList(recent, rowHeight = 44.dp, key = { it.id }) { p ->
                            val s = byId.getValue(p.subscriptionId)
                            Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                SubIcon(s.iconType, s.iconValue, s.color, s.name, size = 28.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(s.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    LocalDate.ofEpochDay(p.billingDay).let { "${it.monthValue}月${it.dayOfMonth}日  " },
                                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
                                )
                                Text(Currencies.format(p.amount, p.currency), style = MaterialTheme.typography.labelLarge.merge(TabularNums))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding()) }
        }
    }

    if (pickCurrency) {
        CurrencyPickerDialog(cur, onDismiss = { pickCurrency = false }) {
            vm.setCurrency(it); pickCurrency = false
        }
    }
}

@Composable
private fun PeriodTiles(st: UiState, onCurrency: () -> Unit) {
    val extra = LocalExtra.current
    val cur = st.settings.displayCurrency
    val today = LocalDate.now()
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(extra.heroBrush).padding(18.dp)) {
        Column {
            // 大数字是每月平均（和首页紫色卡片一样）；下面一排是实际待付：未来7天 / 未来30天 / 未来一年。币种在右上角切换（与首页紫色卡片一致），金额里不再带代码
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("当年每月平均支出", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onCurrency)
                        .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("$cur · ${Currencies.name(cur)}", style = MaterialTheme.typography.labelLarge, color = Color.White)
                    Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                Currencies.format(st.total(SummaryPeriod.MONTH), cur),
                style = MaterialTheme.typography.headlineMedium.merge(TabularNums), color = Color.White, maxLines = 1,
            )
            Spacer(Modifier.height(14.dp))
            Row {
                listOf(
                    "未来7天" to st.upcoming(today, Billing.daysEnd(today, 7)),
                    "未来30天" to st.upcoming(today, Billing.daysEnd(today, 30)),
                    "未来一年" to st.upcoming(today, Billing.yearEnd(today)),
                ).forEachIndexed { i, (label, v) ->
                    // 一年的金额位数最多，给宽一点
                    Column(Modifier.weight(if (i == 2) 1.25f else 1f)) {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.65f))
                        Text(
                            Currencies.format(v, cur),
                            style = MaterialTheme.typography.titleSmall.merge(TabularNums), color = Color.White,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** 环形图里一个分类的扇区：起点 / 终点都是 0~1（占整圈的比例） */
private data class Arc(val start: Float, val end: Float, val color: Color)

private fun arcsOf(slices: List<Slice>): Map<String, Arc> {
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: return emptyMap()
    var acc = 0.0
    return slices.associate { s ->
        val a = Arc((acc / total).toFloat(), ((acc + s.value) / total).toFloat(), s.color)
        acc += s.value
        s.label to a
    }
}

/** 从 from 过渡到 to 的第 t（0~1）帧：新出现的分类从自己的起点长出来，消失的分类缩回自己的起点 */
private fun blend(from: Map<String, Arc>, to: Map<String, Arc>, t: Float): Map<String, Arc> {
    fun lerp(a: Float, b: Float) = a + (b - a) * t
    val out = LinkedHashMap<String, Arc>()
    for ((k, b) in to) {
        // 第一次出现（页面刚打开）时整圈从 12 点方向顺时针扫出来
        val a = from[k] ?: if (from.isEmpty()) Arc(0f, 0f, b.color) else Arc(b.start, b.start, b.color)
        out[k] = Arc(lerp(a.start, b.start), lerp(a.end, b.end), b.color)
    }
    for ((k, a) in from) if (k !in to) out[k] = Arc(a.start, lerp(a.end, a.start), a.color)
    return out
}

@Composable
private fun Donut(slices: List<Slice>, modifier: Modifier, center: @Composable () -> Unit) {
    val target = remember(slices) { arcsOf(slices) }
    // 动画进行中又换了月份：从当前画面上的位置接着过渡，不跳回去
    var from by remember { mutableStateOf<Map<String, Arc>>(emptyMap()) }
    var to by remember { mutableStateOf<Map<String, Arc>>(emptyMap()) }
    val anim = remember { Animatable(1f) }
    LaunchedEffect(target) {
        from = blend(from, to, anim.value)
        to = target
        anim.snapTo(0f)
        anim.animateTo(1f, tween(if (from.isEmpty()) 700 else 450, easing = FastOutSlowInEasing))
    }
    val track = MaterialTheme.colorScheme.surfaceContainer
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.16f
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            val arcs = blend(from, to, anim.value).values
            val gap = if (arcs.count { it.end - it.start > 0.001f } > 1) 2.5f else 0f
            arcs.forEach { a ->
                val sweep = (a.end - a.start) * 360f
                if (sweep <= 0.05f) return@forEach
                drawArc(
                    a.color, -90f + a.start * 360f + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false,
                    Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
                )
            }
        }
        center()
    }
}

@Composable
private fun LegendRow(s: Slice, total: Double, cur: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
        Spacer(Modifier.width(8.dp))
        Text(
            if (s.count > 0) "${s.label}  ·  ${s.count} 个" else s.label,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${if (total > 0) kotlin.math.round(s.value / total * 100).toInt() else 0}%",
            style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(40.dp), textAlign = TextAlign.End,
        )
        Text(
            Currencies.format(s.value, cur), style = MaterialTheme.typography.labelLarge.merge(TabularNums),
            modifier = Modifier.width(92.dp), textAlign = TextAlign.End, maxLines = 1,
        )
    }
}

@Composable
private fun StackBar(parts: List<Slice>) {
    val total = parts.sumOf { it.value }
    Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)) {
        if (total > 0) parts.forEach { s ->
            val w = (s.value / total).toFloat()
            if (w > 0f) Box(Modifier.weight(w).fillMaxHeight().background(s.color))
        }
    }
}

/**
 * 每月柱状图：一屏显示 12 个月，可以左右滑（前面是过去的月份）。点某个月选中（下面的统计跟着只看这个月），
 * 再点一次取消；柱子上方的空白交给外面（取消选中）。打开时本月大约在左边五分之一的位置。
 */
@Composable
private fun MonthBars(months: List<YearMonth>, values: List<Double>, currentIndex: Int, selected: Int?, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val gap = 5.dp
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val barW = (maxWidth - gap * 11) / 12
        val density = androidx.compose.ui.platform.LocalDensity.current
        val barPx = with(density) { barW.toPx() }
        val stepPx = barPx + with(density) { gap.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }
        // 本月柱子的中心对准左边五分之一处
        val initial = (currentIndex * stepPx + barPx / 2 - widthPx / 5).toInt().coerceAtLeast(0)
        val scroll = androidx.compose.foundation.rememberScrollState(initial)
        Row(
            Modifier.fillMaxWidth().height(112.dp).horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            values.forEachIndexed { i, v ->
                val frac = (v / max).toFloat().coerceAtLeast(0.03f)
                val on = i == selected
                val pick = Modifier.clickable(interactionSource = null, indication = null) { onSelect(i) }
                Column(Modifier.width(barW).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        // 可点范围至少占三分之一高度，矮柱子也好点；再往上才算空白
                        Box(Modifier.fillMaxWidth().fillMaxHeight(maxOf(frac, 0.34f)).then(pick), contentAlignment = Alignment.BottomCenter) {
                            Box(
                                Modifier.fillMaxWidth().fillMaxHeight(frac / maxOf(frac, 0.34f))
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .then(
                                        when {
                                            on -> Modifier.background(extra.accentBrush)
                                            selected == null -> Modifier.background(cs.primary.copy(alpha = if (i < currentIndex) 0.28f else 0.45f))
                                            else -> Modifier.background(cs.primary.copy(alpha = 0.18f))
                                        },
                                    ),
                            )
                        }
                    }
                    // 柱子太窄放不下年份，选中后标题里有年份
                    Text(
                        "${months[i].monthValue}",
                        pick.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center, maxLines = 1,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = when {
                            on -> cs.primary
                            i == currentIndex -> cs.onSurface
                            else -> cs.onSurfaceVariant
                        },
                        fontWeight = if (on || i == currentIndex) FontWeight.Bold else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun RankRow(rank: Int, s: Subscription, value: Double, total: Double, cur: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val frac = if (total > 0) (value / total).toFloat() else 0f
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.width(22.dp))
        SubIcon(s.iconType, s.iconValue, s.color, s.name, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(Currencies.format(value, cur), style = MaterialTheme.typography.labelLarge.merge(TabularNums))
            }
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(cs.surfaceContainer)) {
                Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.01f)).fillMaxHeight().clip(CircleShape).background(cs.primary.copy(alpha = 0.75f)))
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium.merge(TabularNums), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Suppress("unused") private val keepImports = listOf(Category.OTHER, PayMethod.OTHER)
