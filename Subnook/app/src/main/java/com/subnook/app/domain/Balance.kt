package com.subnook.app.domain

import com.subnook.app.data.CycleUnit
import com.subnook.app.data.Subscription
import java.time.LocalDate
import kotlin.math.floor

/** 余额能覆盖的期数，以及第一次覆盖不了、需要充值的账单日 */
data class BalanceForecast(
    val periods: Int,
    val rechargeOn: LocalDate,
    /** 距离付下一期还差多少（余额足够时为 0） */
    val shortfall: Double,
    /** 期数太多，已按上限截断 */
    val capped: Boolean = false,
)

object Balance {
    /** 上限约 100 年，避免余额很大时把日期算到离谱的年份 */
    const val MAX_PERIODS = 1200
    private const val EPSILON = 1e-9

    fun forecast(sub: Subscription): BalanceForecast? = forecast(
        balance = sub.balance,
        amount = sub.amount,
        next = LocalDate.ofEpochDay(sub.nextBillingDay),
        cycleCount = sub.cycleCount,
        unit = sub.cycleUnit,
        anchorDay = sub.anchorDay,
    )

    fun forecast(
        balance: Double,
        amount: Double,
        next: LocalDate,
        cycleCount: Int,
        unit: CycleUnit,
        anchorDay: Int,
    ): BalanceForecast? {
        if (amount <= 0.0 || cycleCount <= 0) return null
        val available = balance.coerceAtLeast(0.0)
        val raw = available / amount + EPSILON
        val capped = raw >= MAX_PERIODS
        val periods = if (capped) MAX_PERIODS else floor(raw).toInt()
        var rechargeOn = next
        repeat(periods) { rechargeOn = Billing.shift(rechargeOn, cycleCount, unit, anchorDay, 1) }
        return BalanceForecast(
            periods = periods,
            rechargeOn = rechargeOn,
            shortfall = (amount - available).coerceAtLeast(0.0),
            capped = capped,
        )
    }

    fun periodsText(forecast: BalanceForecast): String =
        if (forecast.capped) "$MAX_PERIODS+ 期" else "${forecast.periods} 期"

    /** 账单日文案：今年省略年份 */
    fun dateText(date: LocalDate, today: LocalDate = Billing.today()): String =
        if (date.year == today.year) "${date.monthValue}月${date.dayOfMonth}日"
        else "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
}
