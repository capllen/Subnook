package com.subnook.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.subnook.app.App
import com.subnook.app.data.AppSettings
import com.subnook.app.data.PaymentRecord
import com.subnook.app.data.RateTable
import com.subnook.app.data.SortOrder
import com.subnook.app.data.Subscription
import com.subnook.app.data.SummaryPeriod
import com.subnook.app.domain.Billing
import com.subnook.app.notify.Scheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val subs: List<Subscription> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val rates: RateTable = RateTable(emptyMap(), 0),
    val loaded: Boolean = false,
) {
    val active get() = subs.filter { it.active }
    val inactive get() = subs.filter { !it.active }

    fun total(period: SummaryPeriod = settings.period) = Billing.total(subs, period, rates, settings.displayCurrency)

    fun sortedActive(): List<Subscription> = sorted(active)
    fun sortedInactive(): List<Subscription> = sorted(inactive)

    private fun sorted(list: List<Subscription>): List<Subscription> {
        fun monthly(s: Subscription) = rates.convert(Billing.periodCost(s, SummaryPeriod.MONTH), s.currency, settings.displayCurrency)
        // 中文名按拼音排，英文不区分大小写
        val byName = compareBy(nameCollator) { s: Subscription -> s.name.trim() }
        val key: Comparator<Subscription> = when (settings.sort) {
            SortOrder.MANUAL -> return list.sortedWith(compareBy<Subscription> { it.sortIndex }.thenBy { it.id })
            SortOrder.NEXT_DATE -> compareBy { it.nextBillingDay }
            SortOrder.AMOUNT -> compareBy { monthly(it) }
            SortOrder.NAME -> byName
            SortOrder.CATEGORY -> compareBy { it.category.ordinal }
            SortOrder.CREATED -> compareBy<Subscription> { it.createdAt }.thenBy { it.id }
        }
        // 正序 / 倒序只作用于主排序键；值相同时始终按名称正序，顺序稳定
        return list.sortedWith((if (settings.sortAscending) key else key.reversed()).then(byName))
    }

    private companion object {
        val nameCollator: java.text.Collator = java.text.Collator.getInstance(java.util.Locale.CHINA).apply {
            strength = java.text.Collator.SECONDARY
        }
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as App
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val state: StateFlow<UiState> = combine(a.dao.observeAll(), a.settings.flow, a.rates.table) { subs, s, r ->
        UiState(subs, s, r, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun observeSub(id: Long): Flow<Subscription?> = a.dao.observe(id)
    fun observePayments(id: Long): Flow<List<PaymentRecord>> = a.dao.observePayments(id)
    fun observePaymentsBetween(from: Long, to: Long) = a.dao.observePaymentsBetween(from, to)
    fun observeAllPayments() = a.dao.observeAllPayments()

    fun toast(msg: String) { _messages.tryEmit(msg) }

    fun save(sub: Subscription, onDone: () -> Unit = {}) = viewModelScope.launch {
        // 新订阅排在手动顺序的最后
        a.dao.upsert(if (sub.id == 0L) sub.copy(sortIndex = a.dao.maxSortIndex() + 1) else sub)
        Scheduler.runAll(a)
        onDone()
    }

    fun delete(sub: Subscription) = viewModelScope.launch {
        a.dao.delete(sub)
        a.notifier.cancel(sub.id)
        toast("已删除「${sub.name}」")
    }

    fun deletePayment(p: PaymentRecord) = viewModelScope.launch { a.dao.deletePaymentWithRefund(p) }

    fun confirmPaid(sub: Subscription) = viewModelScope.launch {
        a.processor.confirmPaid(sub.id)
        toast("已记录付款，下次账单日已更新")
    }

    fun skip(sub: Subscription) = viewModelScope.launch {
        a.processor.skip(sub.id)
        toast("已跳过本期")
    }

    fun setPeriod(p: SummaryPeriod) = viewModelScope.launch { a.settings.setPeriod(p) }
    fun setCurrency(c: String) = viewModelScope.launch { a.settings.setCurrency(c) }
    /** 选排序方式和方向；从其他方式切到手动排序时，把当前看到的顺序存为手动顺序的起点 */
    fun setSort(s: SortOrder, asc: Boolean) = viewModelScope.launch {
        val st = state.value
        if (s == SortOrder.MANUAL && st.settings.sort != SortOrder.MANUAL) {
            a.dao.setManualOrder(st.sortedActive().map { it.id } + st.sortedInactive().map { it.id })
        }
        a.settings.setSort(s, asc)
    }

    /** 拖动排序结束：[activeIds] 是全部启用中订阅的新顺序，并切到手动排序 */
    fun reorder(activeIds: List<Long>) = viewModelScope.launch {
        a.dao.setManualOrder(activeIds + state.value.sortedInactive().map { it.id })
        a.settings.setSort(SortOrder.MANUAL, true)
    }
    fun setTheme(t: com.subnook.app.data.ThemeMode) = viewModelScope.launch { a.settings.setTheme(t) }
    fun setNotifyTime(h: Int, m: Int) = viewModelScope.launch {
        a.settings.setNotifyTime(h, m)
        Scheduler.scheduleDailyAlarm(a)
    }

    fun refreshRates() = viewModelScope.launch {
        a.rates.refresh()
            .onSuccess { toast("汇率已更新") }
            .onFailure { toast("汇率更新失败：${it.message ?: it.javaClass.simpleName}") }
    }

    fun export(uri: Uri) = viewModelScope.launch {
        runCatching { a.backup.export(uri) }
            .onSuccess { toast("已导出 $it 个订阅") }
            .onFailure { toast("导出失败：${it.message}") }
    }

    fun import(uri: Uri) = viewModelScope.launch {
        runCatching { a.backup.import(uri) }
            .onSuccess {
                Scheduler.runAll(a)
                toast("已导入 ${it.subs} 个订阅、${it.payments} 条付款记录")
            }
            .onFailure { toast("导入失败：${it.message}") }
    }

    fun onResume() = viewModelScope.launch {
        Scheduler.runAll(a)
        a.rates.refreshIfStale()
    }
}
