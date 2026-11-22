package com.subnook.app.domain

import com.subnook.app.data.CycleUnit
import com.subnook.app.data.RateTable
import com.subnook.app.data.Subscription
import com.subnook.app.data.SummaryPeriod
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object Billing {

    /** 单元测试固定「今天」用；正式运行为 null */
    @Volatile var todayOverride: LocalDate? = null

    fun today(): LocalDate = todayOverride ?: LocalDate.now()

    fun LocalDate.withAnchor(anchorDay: Int): LocalDate =
        withDayOfMonth(minOf(anchorDay, lengthOfMonth()))

    /** date 往后推 n 个周期（n 可为负） */
    fun shift(date: LocalDate, count: Int, unit: CycleUnit, anchorDay: Int, n: Int = 1): LocalDate {
        val steps = count.toLong() * n
        return when (unit) {
            CycleUnit.DAY -> date.plusDays(steps)
            CycleUnit.WEEK -> date.plusWeeks(steps)
            CycleUnit.MONTH -> date.plusMonths(steps).withAnchor(anchorDay)
            CycleUnit.YEAR -> date.plusYears(steps).withAnchor(anchorDay)
        }
    }

    fun next(sub: Subscription, from: LocalDate = LocalDate.ofEpochDay(sub.nextBillingDay)): LocalDate =
        shift(from, sub.cycleCount, sub.cycleUnit, sub.anchorDay, 1)

    fun prev(sub: Subscription, from: LocalDate): LocalDate =
        shift(from, sub.cycleCount, sub.cycleUnit, sub.anchorDay, -1)

    /**
     * 起始账单日没填时的默认值：今年的第一个账单日（从下次付款日按周期往前推，不早于今年 1 月 1 日）。
     * 下次付款日本身在今年之前（很久没付）就用下次付款日。
     */
    fun defaultFirstBillingDay(sub: Subscription, year: Int = today().year): LocalDate {
        val jan1 = LocalDate.of(year, 1, 1)
        var d = LocalDate.ofEpochDay(sub.nextBillingDay)
        var guard = 0
        while (guard++ < 5000) {
            val p = prev(sub, d)
            if (p < jan1) break
            d = p
        }
        return d
    }

    /**
     * 由起始账单日和周期算下次付款日：起始账单日在今天之后就是它本身（第一笔还没到）；
     * 否则是「起始账单日 + k 个周期」（k ≥ 1）里第一个不早于今天的（起始当天的那笔算已付）。
     * 每次都从起始账单日直接加 k 个周期，月底的日子不会越推越早（1 月 31 日 → 2 月 28 日 → 3 月 31 日）。
     */
    fun nextFromFirst(first: LocalDate, count: Int, unit: CycleUnit, today: LocalDate = today()): LocalDate {
        if (first > today) return first
        var k = 1
        var d = shift(first, count, unit, first.dayOfMonth, k)
        while (d < today && k < 100_000) d = shift(first, count, unit, first.dayOfMonth, ++k)
        return d
    }

    /** 订阅实际用的起始账单日：填了用填的，没填用今年的第一个账单日 */
    fun firstBillingDate(sub: Subscription): LocalDate =
        sub.firstBillingDay?.let(LocalDate::ofEpochDay) ?: defaultFirstBillingDay(sub)

    /** 订阅在 [from, to] 区间内的所有账单日（基于下次付款日向前/向后推算） */
    fun occurrences(sub: Subscription, fromDate: LocalDate, toDate: LocalDate): List<LocalDate> {
        var from = fromDate
        var to = toDate
        // 分期只有第 1 期到最后一期之间的还款日
        if (sub.isInstallment) {
            if (Installments.isPaidOff(sub)) return emptyList()
            from = maxOf(from, Installments.startDate(sub))
            to = minOf(to, Installments.endDate(sub))
            if (from > to) return emptyList()
        } else {
            // 订阅不会早于起始账单日（没填就是今年的第一个账单日）
            from = maxOf(from, firstBillingDate(sub))
            if (from > to) return emptyList()
        }
        val out = mutableListOf<LocalDate>()
        val base = LocalDate.ofEpochDay(sub.nextBillingDay)
        var d = base
        var guard = 0
        while (d >= from && guard++ < 5000) {
            if (d <= to) out += d
            d = prev(sub, d)
        }
        d = next(sub, base)
        guard = 0
        while (d <= to && guard++ < 5000) {
            if (d >= from) out += d
            d = next(sub, d)
        }
        return out.sorted()
    }

    /**
     * 还没付的账单日：只从「下次付款日期」往后算。
     * 下次付款日之前的周期要么已付（有付款记录），要么跳过了，不能再当成待付。
     */
    fun upcoming(sub: Subscription, from: LocalDate, to: LocalDate): List<LocalDate> =
        occurrences(sub, maxOf(from, LocalDate.ofEpochDay(sub.nextBillingDay)), to)

    /** 某个账单日要付的金额：订阅每期一样，分期按还款计划 */
    fun amountOn(sub: Subscription, date: LocalDate): Double =
        if (sub.isInstallment) Installments.amountOn(sub, date) else sub.amount

    /**
     * [from, to] 内按账单计划要付的合计（原币种）：不管付没付、有没有记账都算，
     * 装 App 之前的账单也按周期往前推出来。首页「本月」小计和统计页每月柱状图用这个。
     */
    fun scheduledCost(sub: Subscription, from: LocalDate, to: LocalDate): Double =
        occurrences(sub, from, to).sumOf { amountOn(sub, it) }

    /** [from, to] 内还没付的账单合计（原币种） */
    fun upcomingCost(sub: Subscription, from: LocalDate, to: LocalDate): Double =
        upcoming(sub, from, to).sumOf { amountOn(sub, it) }

    /**
     * 「未来一年」的最后一天：从今天起正好一年（不含一年后的同一天），
     * 月付订阅在这段时间里正好 12 期、年付正好 1 期。首页和统计页都用这个窗口，数字才一致。
     */
    fun yearEnd(today: LocalDate): LocalDate = today.plusYears(1).minusDays(1)

    /** 「未来 N 天」的最后一天（含今天共 N 天）：未来7天、未来30天 */
    fun daysEnd(today: LocalDate, days: Int): LocalDate = today.plusDays(days - 1L)

    /** 折算为每年花费（原币种） */
    fun yearlyCost(sub: Subscription): Double {
        val cycleDays = sub.cycleCount * sub.cycleUnit.approxDays
        return sub.amount * CycleUnit.YEAR.approxDays / cycleDays
    }

    fun periodCost(sub: Subscription, period: SummaryPeriod): Double {
        // 月/年周期按精确比例，避免 30 元/月 显示成 29.99
        val perYear = when (sub.cycleUnit) {
            CycleUnit.MONTH -> sub.amount * 12.0 / sub.cycleCount
            CycleUnit.YEAR -> sub.amount / sub.cycleCount
            else -> yearlyCost(sub)
        }
        return when (period) {
            SummaryPeriod.YEAR -> perYear
            SummaryPeriod.MONTH -> perYear / 12.0
            SummaryPeriod.WEEK -> perYear * 7.0 / CycleUnit.YEAR.approxDays
        }
    }

    /**
     * 今年（1 月 1 日到 12 月 31 日）按账单计划应付的合计（原币种）：订阅按周期推出全年的每一期（装 App 之前的也算），
     * 分期只算落在今年的那几期。首页 / 统计页紫色卡片的平均支出、首页占比条都用这个。
     */
    fun calendarYearCost(sub: Subscription, year: Int): Double =
        scheduledCost(sub, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))

    /** 平均支出（显示币种）：今年应付合计 ÷ 12 = 每月，÷ 今年的周数 = 每周，按年就是今年合计 */
    fun total(subs: List<Subscription>, period: SummaryPeriod, rates: RateTable, currency: String, today: LocalDate = today()): Double {
        val year = subs.filter { it.active }.sumOf { rates.convert(calendarYearCost(it, today.year), it.currency, currency) }
        return when (period) {
            SummaryPeriod.YEAR -> year
            SummaryPeriod.MONTH -> year / 12.0
            SummaryPeriod.WEEK -> year * 7.0 / today.lengthOfYear()
        }
    }

    /** 进入「待付款」的天数窗口：3 天内到期（含已逾期） */
    const val PAYABLE_DAYS = 3L

    /** 是否显示在「待付款」里并提供「已付款」按钮（不分自动/手动，自动扣款也可能提前） */
    fun isPayable(sub: Subscription, today: LocalDate = today()): Boolean =
        sub.active && daysUntil(sub, today) <= PAYABLE_DAYS

    fun daysUntil(sub: Subscription, today: LocalDate = today()): Long =
        ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(sub.nextBillingDay))

    fun cycleText(sub: Subscription): String =
        if (sub.cycleCount == 1) "每${sub.cycleUnit.label}" else "每${sub.cycleCount}${sub.cycleUnit.label}"

    fun dueText(days: Long): String = when {
        days < -1 -> "已逾期 ${-days} 天"
        days == -1L -> "昨天到期"
        days == 0L -> "今天"
        days == 1L -> "明天"
        days == 2L -> "后天"
        else -> "$days 天后"
    }
}
