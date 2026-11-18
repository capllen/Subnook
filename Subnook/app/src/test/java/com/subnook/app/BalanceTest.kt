package com.subnook.app

import com.subnook.app.data.CycleUnit
import com.subnook.app.data.PayMethod
import com.subnook.app.data.Subscription
import com.subnook.app.domain.Balance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BalanceTest {
    private fun sub(
        next: LocalDate,
        amount: Double,
        balance: Double,
        count: Int = 1,
        unit: CycleUnit = CycleUnit.MONTH,
    ) = Subscription(
        name = "t", amount = amount, currency = "CNY", cycleCount = count, cycleUnit = unit,
        nextBillingDay = next.toEpochDay(), anchorDay = next.dayOfMonth,
        payMethod = PayMethod.BALANCE, balance = balance,
    )

    @Test fun exactMultipleCoversWholePeriods() {
        val f = Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 30.0, balance = 90.0))!!
        assertEquals(3, f.periods)
        assertEquals(LocalDate.of(2027, 2, 20), f.rechargeOn)
        assertEquals(0.0, f.shortfall, 1e-9)
    }

    @Test fun partialPeriodNeedsRechargeAfterCoveredOnes() {
        val f = Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 30.0, balance = 100.0))!!
        assertEquals(3, f.periods)
        assertEquals(LocalDate.of(2027, 2, 20), f.rechargeOn)
    }

    @Test fun insufficientBalanceNeedsRechargeOnNextBill() {
        val f = Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 30.0, balance = 10.0))!!
        assertEquals(0, f.periods)
        assertEquals(LocalDate.of(2026, 11, 20), f.rechargeOn)
        assertEquals(20.0, f.shortfall, 1e-9)
    }

    @Test fun weeklyCycle() {
        val f = Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 10.0, balance = 35.0, unit = CycleUnit.WEEK))!!
        assertEquals(3, f.periods)
        assertEquals(LocalDate.of(2026, 12, 11), f.rechargeOn)
    }

    @Test fun yearlyCycle() {
        val f = Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 100.0, balance = 250.0, unit = CycleUnit.YEAR))!!
        assertEquals(2, f.periods)
        assertEquals(LocalDate.of(2028, 11, 20), f.rechargeOn)
    }

    @Test fun floatingPointDoesNotLoseAPeriod() {
        val f = Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 0.1, balance = 0.3))!!
        assertEquals(3, f.periods)
    }

    @Test fun noForecastWithoutAmount() {
        assertNull(Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 0.0, balance = 100.0)))
    }

    @Test fun veryLargeBalanceIsCapped() {
        val f = Balance.forecast(sub(LocalDate.of(2026, 11, 20), amount = 1.0, balance = 1_000_000.0))!!
        assertEquals(Balance.MAX_PERIODS, f.periods)
        assertTrue(f.capped)
    }
}
