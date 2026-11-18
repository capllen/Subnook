package com.subnook.app.domain

import com.subnook.app.data.PaymentRecord
import com.subnook.app.data.PayMethod
import com.subnook.app.data.SettingsRepository
import com.subnook.app.data.Subscription
import com.subnook.app.data.SubscriptionDao
import com.subnook.app.notify.Notifier
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 每日处理：
 * 1. 自动续费的订阅，账单日已过 -> 记一笔付款并滚动到下一个账单日
 * 2. 手动付款的订阅，保持不动，等待用户确认
 * 3. 发送"即将扣款/逾期未付"通知
 */
class RenewalProcessor(
    private val dao: SubscriptionDao,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
) {
    private val mutex = Mutex()

    suspend fun run(now: LocalDateTime = LocalDateTime.now()) = mutex.withLock {
        val today = now.toLocalDate()
        val s = settings.get()
        val notifyTimeReached = now.toLocalTime() >= LocalTime.of(s.notifyHour, s.notifyMinute)
        for (orig in dao.getAll()) {
            if (!orig.active) continue
            var sub = rollAutoRenew(orig, today)
            sub = remind(sub, today, notifyTimeReached)
            if (sub != orig) dao.upsert(sub)
        }
    }

    private suspend fun rollAutoRenew(sub: Subscription, today: LocalDate): Subscription {
        if (!sub.autoRenew) return sub
        var next = LocalDate.ofEpochDay(sub.nextBillingDay)
        var balance = sub.balance
        var guard = 0
        while (next < today && guard++ < 1000) {
            val used = balanceUsed(sub, balance)
            dao.insertPayment(
                PaymentRecord(
                    subscriptionId = sub.id, billingDay = next.toEpochDay(), amount = sub.amount,
                    currency = sub.currency, auto = true, balanceUsed = used,
                ),
            )
            balance = (balance - used).coerceAtLeast(0.0)
            next = Billing.next(sub, next)
        }
        return if (next.toEpochDay() == sub.nextBillingDay) sub
        else sub.copy(nextBillingDay = next.toEpochDay(), balance = balance)
    }

    private fun remind(sub: Subscription, today: LocalDate, timeReached: Boolean): Subscription {
        var out = sub
        val due = LocalDate.ofEpochDay(sub.nextBillingDay)
        if (shouldRemind(sub, today, timeReached)) {
            notifier.upcoming(sub, today)
            out = out.copy(remindedForDay = today.toEpochDay())
        }
        // 手动付款逾期：每天提醒一次
        if (!sub.autoRenew && sub.remindEnabled && due < today && timeReached && sub.overdueNotifiedDay != today.toEpochDay()) {
            notifier.overdue(sub, today)
            out = out.copy(overdueNotifiedDay = today.toEpochDay())
        }
        return out
    }

    companion object {
        /** 从账单日前 N 天开始，到账单日当天，每天到点提醒一次（remindedForDay 记录最后一次提醒的日期） */
        fun shouldRemind(sub: Subscription, today: LocalDate, timeReached: Boolean): Boolean {
            if (!sub.active || !sub.remindEnabled || !timeReached) return false
            if (sub.remindedForDay == today.toEpochDay()) return false
            val due = LocalDate.ofEpochDay(sub.nextBillingDay)
            return today >= due.minusDays(sub.remindDaysBefore.toLong()) && today <= due
        }
    }

    /** 用户确认手动付款：记账并推进一个周期 */
    suspend fun confirmPaid(id: Long) = mutex.withLock {
        val sub = dao.get(id) ?: return@withLock
        val used = balanceUsed(sub)
        dao.insertPayment(
            PaymentRecord(
                subscriptionId = sub.id, billingDay = sub.nextBillingDay, amount = sub.amount,
                currency = sub.currency, auto = false, balanceUsed = used,
            ),
        )
        val next = Billing.next(sub)
        dao.upsert(
            sub.copy(
                nextBillingDay = next.toEpochDay(), overdueNotifiedDay = null,
                balance = (sub.balance - used).coerceAtLeast(0.0),
            ),
        )
        notifier.cancel(sub.id)
    }

    /** 跳过本期（例如这期没续），不记账直接推进 */
    suspend fun skip(id: Long) = mutex.withLock {
        val sub = dao.get(id) ?: return@withLock
        dao.upsert(sub.copy(nextBillingDay = Billing.next(sub).toEpochDay(), overdueNotifiedDay = null))
        notifier.cancel(sub.id)
    }

    /** 余额付款最多扣到 0，不会把余额记成负数 */
    private fun balanceUsed(sub: Subscription, balance: Double = sub.balance): Double =
        if (sub.payMethod == PayMethod.BALANCE) minOf(balance, sub.amount).coerceAtLeast(0.0) else 0.0
}
