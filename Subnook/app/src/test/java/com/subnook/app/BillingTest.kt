package com.subnook.app

import com.subnook.app.data.CycleUnit
import com.subnook.app.data.RateTable
import com.subnook.app.data.Subscription
import com.subnook.app.data.SummaryPeriod
import com.subnook.app.data.InstallmentMethod
import com.subnook.app.data.SubKind
import com.subnook.app.domain.Billing
import com.subnook.app.domain.Installments
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class BillingTest {
    // 没填起始账单日时从「今年」第一个账单日推算，测试里固定今天，明年跑也不会变
    @org.junit.Before fun pinToday() { Billing.todayOverride = LocalDate.of(2026, 11, 22) }
    @org.junit.After fun unpinToday() { Billing.todayOverride = null }

    @Test fun deletingLatestPaymentRollsBackNextBillingDay() {
        fun pay(day: LocalDate, prepay: Boolean = false) =
            com.subnook.app.data.PaymentRecord(subscriptionId = 0, billingDay = day.toEpochDay(), amount = 30.0, currency = "CNY", auto = false, prepay = prepay)
        // 手动订阅 11 月 23 日那期误点了「我已付款」，下次变成 12 月 23 日；删掉这笔就回到 11 月 23 日
        val manual = sub(LocalDate.of(2026, 12, 23)).copy(autoRenew = false, overdueNotifiedDay = 5L)
        val nov23 = LocalDate.of(2026, 11, 23)
        val rolled = Billing.rollbackOnDelete(manual, pay(nov23), latestBillingDay = nov23.toEpochDay())
        assertEquals(nov23.toEpochDay(), rolled?.nextBillingDay)
        assertEquals(null, rolled?.overdueNotifiedDay)
        // 不是最近一笔：只删记录
        assertEquals(null, Billing.rollbackOnDelete(manual, pay(LocalDate.of(2026, 10, 23)), latestBillingDay = nov23.toEpochDay()))
        // 提前还款走撤销流程
        assertEquals(null, Billing.rollbackOnDelete(manual, pay(nov23, prepay = true), latestBillingDay = nov23.toEpochDay()))
        // 自动续费：已经过去的那期不回滚（回滚了后台会马上再记一笔）；还没到的（提前确认的）照样回滚
        val auto = sub(LocalDate.of(2026, 12, 23))
        assertEquals(null, Billing.rollbackOnDelete(auto, pay(LocalDate.of(2026, 11, 20)), latestBillingDay = LocalDate.of(2026, 11, 20).toEpochDay()))
        assertEquals(nov23.toEpochDay(), Billing.rollbackOnDelete(auto, pay(nov23), latestBillingDay = nov23.toEpochDay())?.nextBillingDay)
    }

    @Test fun nextFromFirstBillingDay() {
        val today = LocalDate.of(2026, 11, 22)
        // 1 月 5 日起每月：下一次是 12 月 5 日
        assertEquals(LocalDate.of(2026, 12, 5), Billing.nextFromFirst(LocalDate.of(2026, 1, 5), 1, CycleUnit.MONTH, today))
        // 今天开始订：今天这笔算已付，下一次是下个月
        assertEquals(LocalDate.of(2026, 12, 22), Billing.nextFromFirst(today, 1, CycleUnit.MONTH, today))
        // 某一期正好是今天：下次就是今天
        assertEquals(today, Billing.nextFromFirst(LocalDate.of(2026, 10, 22), 1, CycleUnit.MONTH, today))
        // 起始账单日还没到：第一笔就是它
        assertEquals(LocalDate.of(2026, 12, 1), Billing.nextFromFirst(LocalDate.of(2026, 12, 1), 1, CycleUnit.MONTH, today))
        // 月底：1 月 31 日起，11 月是 30 日，不会被 2 月带成 28 日
        assertEquals(LocalDate.of(2026, 11, 30), Billing.nextFromFirst(LocalDate.of(2026, 1, 31), 1, CycleUnit.MONTH, today))
        // 年付、每 2 周
        assertEquals(LocalDate.of(2027, 3, 1), Billing.nextFromFirst(LocalDate.of(2024, 3, 1), 1, CycleUnit.YEAR, today))
        assertEquals(LocalDate.of(2026, 11, 26), Billing.nextFromFirst(LocalDate.of(2026, 11, 12), 2, CycleUnit.WEEK, today))
    }

    @Test fun defaultFirstBillingDayIsFirstBillThisYear() {
        assertEquals(LocalDate.of(2026, 1, 5), Billing.defaultFirstBillingDay(sub(LocalDate.of(2026, 12, 5))))
        assertEquals(LocalDate.of(2026, 3, 1), Billing.defaultFirstBillingDay(sub(LocalDate.of(2027, 3, 1), unit = CycleUnit.YEAR)))
        // 今年没有账单（下次就在明年、两年一付）：就是下次付款日
        assertEquals(LocalDate.of(2027, 3, 1), Billing.defaultFirstBillingDay(sub(LocalDate.of(2027, 3, 1), count = 2, unit = CycleUnit.YEAR)))
        // 默认值以前的不推算：去年 12 月没有账单
        assertEquals(emptyList<LocalDate>(), Billing.occurrences(sub(LocalDate.of(2026, 12, 5)), LocalDate.of(2025, 12, 1), LocalDate.of(2025, 12, 31)))
    }
    private fun sub(next: LocalDate, count: Int = 1, unit: CycleUnit = CycleUnit.MONTH, amount: Double = 30.0, cur: String = "CNY") =
        Subscription(name = "t", amount = amount, currency = cur, cycleCount = count, cycleUnit = unit,
            nextBillingDay = next.toEpochDay(), anchorDay = next.dayOfMonth)

    @Test fun monthEndAnchorDoesNotDrift() {
        val s = sub(LocalDate.of(2026, 1, 31))
        val feb = Billing.next(s)
        assertEquals(LocalDate.of(2026, 2, 28), feb)
        assertEquals(LocalDate.of(2026, 3, 31), Billing.next(s, feb))
    }

    @Test fun leapDayYearly() {
        val s = sub(LocalDate.of(2028, 2, 29), unit = CycleUnit.YEAR)
        assertEquals(LocalDate.of(2029, 2, 28), Billing.next(s))
    }

    @Test fun occurrencesInMonth() {
        val s = sub(LocalDate.of(2026, 11, 20), unit = CycleUnit.WEEK)
        val occ = Billing.occurrences(s, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertEquals(listOf(6, 13, 20, 27), occ.map { it.dayOfMonth })
    }

    @Test fun upcomingSkipsCyclesBeforeNextBillingDay() {
        // 11 月 18 日那期已提前确认付款，下次账单日推到 12 月 18 日：11 月里不该再有待付
        val s = sub(LocalDate.of(2026, 12, 18))
        assertEquals(emptyList<LocalDate>(), Billing.upcoming(s, LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 30)))
        assertEquals(listOf(LocalDate.of(2026, 12, 18)), Billing.upcoming(s, LocalDate.of(2026, 11, 17), LocalDate.of(2026, 12, 31)))
        // 下次账单日在区间内时照常返回
        val t = sub(LocalDate.of(2026, 11, 20), unit = CycleUnit.WEEK)
        assertEquals(listOf(20, 27), Billing.upcoming(t, LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 30)).map { it.dayOfMonth })
    }

    @Test fun next12MonthsMatchesYearlyCostForMonthlyAndYearly() {
        // 首页和统计页「未来一年」用同一个窗口：月付 / 年付订阅不管账单日在今天前后，都正好是一年的钱
        val today = LocalDate.of(2026, 11, 22)
        val end = Billing.yearEnd(today)
        for (day in listOf(22, 23, 25, 31)) {
            val first = LocalDate.of(2026, 11, 1).withDayOfMonth(minOf(day, 30)).let { if (it < today) it.plusMonths(1) else it }
            val m = sub(first.withDayOfMonth(minOf(day, first.lengthOfMonth()))).copy(anchorDay = day)
            assertEquals("day $day", 360.0, Billing.upcomingCost(m, today, end), 1e-9)
        }
        for (next in listOf(today, LocalDate.of(2027, 3, 1), end)) {
            assertEquals(99.0, Billing.upcomingCost(sub(next, unit = CycleUnit.YEAR, amount = 99.0), today, end), 1e-9)
        }
        // 明天到期的月付：一年后的同一天不算进来
        assertEquals(360.0, Billing.upcomingCost(sub(today.plusDays(1)), today, end), 1e-9)
        assertEquals(360.0, Billing.upcomingCost(sub(LocalDate.of(2026, 12, 10)), today, end), 1e-9)
    }

    @Test fun scheduledCostCountsWholeMonthIncludingBillsBeforeInstall() {
        // 11 月 15 日才装 App，5 号那期在装之前就付了，下次账单日填的是 12 月 5 日：11 月整月仍然要算上 5 号那期
        val nov1 = LocalDate.of(2026, 11, 1)
        val nov30 = LocalDate.of(2026, 11, 30)
        assertEquals(30.0, Billing.scheduledCost(sub(LocalDate.of(2026, 12, 5)), nov1, nov30), 1e-9)
        // 本月还没到的、逾期没付的也都在
        assertEquals(30.0, Billing.scheduledCost(sub(LocalDate.of(2026, 11, 25)), nov1, nov30), 1e-9)
        assertEquals(30.0, Billing.scheduledCost(sub(LocalDate.of(2026, 11, 3)), nov1, nov30), 1e-9)
        // 周付：11 月有 4 个周五
        assertEquals(40.0, Billing.scheduledCost(sub(LocalDate.of(2026, 11, 20), unit = CycleUnit.WEEK, amount = 10.0), nov1, nov30), 1e-9)
        // 年付：账单日不在 11 月就是 0
        assertEquals(0.0, Billing.scheduledCost(sub(LocalDate.of(2027, 3, 1), unit = CycleUnit.YEAR), nov1, nov30), 1e-9)
    }

    @Test fun averageIsThisYearsScheduledCostOverTwelve() {
        val r = RateTable(mapOf("CNY" to 1.0), 1)
        val today = LocalDate.of(2026, 11, 22)
        fun monthAvg(vararg s: Subscription) = Billing.total(s.toList(), SummaryPeriod.MONTH, r, "CNY", today)
        // 月付：12 期；年付：今年 1 期
        assertEquals(30.0, monthAvg(sub(LocalDate.of(2026, 12, 5))), 1e-9)
        assertEquals(10.0, monthAvg(sub(LocalDate.of(2027, 3, 1), unit = CycleUnit.YEAR, amount = 120.0)), 1e-9)
        // 分期：每期 100，2026-10 起 12 期 → 今年只有 10、11、12 月三期 = 300 / 12
        val inst = Installments.normalize(
            Subscription(
                name = "手机", amount = 0.0, currency = "CNY", nextBillingDay = LocalDate.of(2026, 12, 15).toEpochDay(), anchorDay = 15,
                kind = SubKind.INSTALLMENT, instPrincipal = 1200.0, instTerms = 12, instRate = 0.0, instMethod = InstallmentMethod.INTEREST_FREE,
                instStartDay = LocalDate.of(2026, 10, 15).toEpochDay(),
            ),
        )
        assertEquals(25.0, monthAvg(inst), 1e-9)
        assertEquals(300.0, Billing.total(listOf(inst), SummaryPeriod.YEAR, r, "CNY", today), 1e-9)
        // 周付 10 元，2026 年 52 个周五
        assertEquals(520.0, Billing.total(listOf(sub(LocalDate.of(2026, 11, 27), unit = CycleUnit.WEEK, amount = 10.0)), SummaryPeriod.YEAR, r, "CNY", today), 1e-9)
    }

    @Test fun firstBillingDayStopsBackwardProjection() {
        val r = RateTable(mapOf("CNY" to 1.0), 1)
        val today = LocalDate.of(2026, 11, 22)
        // 月付 30，下次 12 月 5 日；从 2026-09-05 才开始订：今年只有 9、10、11、12 月四期
        val s = sub(LocalDate.of(2026, 12, 5)).copy(firstBillingDay = LocalDate.of(2026, 9, 5).toEpochDay())
        assertEquals(120.0, Billing.total(listOf(s), SummaryPeriod.YEAR, r, "CNY", today), 1e-9)
        assertEquals(10.0, Billing.total(listOf(s), SummaryPeriod.MONTH, r, "CNY", today), 1e-9)
        assertEquals(emptyList<LocalDate>(), Billing.occurrences(s, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
        assertEquals(listOf(LocalDate.of(2026, 9, 5)), Billing.occurrences(s, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
        // 没填：一直往前推，今年 12 期
        assertEquals(360.0, Billing.total(listOf(s.copy(firstBillingDay = null)), SummaryPeriod.YEAR, r, "CNY", today), 1e-9)
    }

    @Test fun periodCosts() {
        val m = sub(LocalDate.now(), amount = 30.0)
        assertEquals(30.0, Billing.periodCost(m, SummaryPeriod.MONTH), 1e-9)
        assertEquals(360.0, Billing.periodCost(m, SummaryPeriod.YEAR), 1e-9)
        val y = sub(LocalDate.now(), unit = CycleUnit.YEAR, amount = 120.0)
        assertEquals(10.0, Billing.periodCost(y, SummaryPeriod.MONTH), 1e-9)
        val q = sub(LocalDate.now(), count = 3, amount = 90.0)
        assertEquals(30.0, Billing.periodCost(q, SummaryPeriod.MONTH), 1e-9)
    }

    @Test fun currencyConversion() {
        val r = RateTable(mapOf("USD" to 1.0, "CNY" to 7.0, "EUR" to 0.5), 1)
        assertEquals(70.0, r.convert(10.0, "USD", "CNY"), 1e-9)
        assertEquals(14.0, r.convert(1.0, "EUR", "CNY"), 1e-9)
        val total = Billing.total(listOf(sub(LocalDate.now(), amount = 10.0, cur = "USD"), sub(LocalDate.now(), amount = 7.0)), SummaryPeriod.MONTH, r, "CNY")
        assertEquals(77.0, total, 1e-9)
    }
}
