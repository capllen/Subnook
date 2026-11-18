package com.subnook.app.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import com.subnook.app.data.SummaryPeriod
import com.subnook.app.ui.components.Group
import com.subnook.app.ui.components.IconTile
import com.subnook.app.ui.components.Pill
import com.subnook.app.ui.theme.LocalExtra
import com.subnook.app.ui.theme.TabularNums
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.subnook.app.data.Category
import com.subnook.app.data.Currencies
import com.subnook.app.data.CycleUnit
import com.subnook.app.data.IconType
import com.subnook.app.data.PayMethod
import com.subnook.app.data.PresetCatalog
import com.subnook.app.data.Subscription
import com.subnook.app.domain.Balance
import com.subnook.app.domain.Billing
import com.subnook.app.ui.MainViewModel
import com.subnook.app.ui.components.CurrencyPickerDialog
import com.subnook.app.ui.components.IconFiles
import com.subnook.app.ui.components.IconPickerSheet
import com.subnook.app.ui.components.PaletteColors
import com.subnook.app.ui.components.SubIcon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.first

private class Form {
    var name by mutableStateOf("")
    var iconType by mutableStateOf(IconType.TEXT)
    var iconValue by mutableStateOf("")
    var color by mutableStateOf(PaletteColors[12])
    var amount by mutableStateOf("")
    var balance by mutableStateOf("")
    var currency by mutableStateOf("CNY")
    var cycleCount by mutableStateOf("1")
    var cycleUnit by mutableStateOf(CycleUnit.MONTH)
    var next by mutableStateOf(LocalDate.now().plusMonths(1))
    var payMethod by mutableStateOf(PayMethod.ALIPAY)
    var autoRenew by mutableStateOf(true)
    var category by mutableStateOf(Category.OTHER)
    var remind by mutableStateOf(true)
    var remindDays by mutableStateOf(3)
    var active by mutableStateOf(true)
    var note by mutableStateOf("")
    var presetKey by mutableStateOf<String?>(null)
    var original by mutableStateOf<Subscription?>(null)
    var loaded by mutableStateOf(false)

    fun load(s: Subscription) {
        original = s
        name = s.name; iconType = s.iconType; iconValue = s.iconValue; color = s.color
        amount = s.amount.toBigDecimal().stripTrailingZeros().toPlainString(); currency = s.currency
        balance = s.balance.toBigDecimal().stripTrailingZeros().toPlainString()
        cycleCount = s.cycleCount.toString(); cycleUnit = s.cycleUnit; next = LocalDate.ofEpochDay(s.nextBillingDay)
        payMethod = s.payMethod; autoRenew = s.autoRenew; category = s.category
        remind = s.remindEnabled; remindDays = s.remindDaysBefore; active = s.active; note = s.note; presetKey = s.presetKey
    }

    fun applyPreset(key: String, hasImage: Boolean) {
        val p = PresetCatalog.all.firstOrNull { it.key == key } ?: return
        presetKey = p.key
        name = p.name
        iconType = if (hasImage) IconType.PRESET else IconType.MATERIAL
        iconValue = if (hasImage) p.key else p.material
        color = p.color
        amount = p.price.toBigDecimal().stripTrailingZeros().toPlainString()
        currency = p.currency
        cycleCount = p.cycleCount.toString(); cycleUnit = p.cycleUnit
        next = Billing.shift(LocalDate.now(), p.cycleCount, p.cycleUnit, LocalDate.now().dayOfMonth)
        payMethod = p.payMethod; autoRenew = p.payMethod.defaultAuto
        category = p.category
    }

    fun effectiveAnchorDay(): Int {
        val o = original
        val dateChanged = o == null || o.nextBillingDay != next.toEpochDay()
        return if (o == null || dateChanged || o.cycleUnit != cycleUnit) next.dayOfMonth else o.anchorDay
    }

    fun build(): Subscription? {
        val amt = amount.replace(",", "").toDoubleOrNull() ?: return null
        val bal = balance.replace(",", "").toDoubleOrNull() ?: original?.balance ?: 0.0
        val cnt = cycleCount.toIntOrNull()?.takeIf { it in 1..999 } ?: return null
        if (name.isBlank()) return null
        val o = original
        val dateChanged = o == null || o.nextBillingDay != next.toEpochDay()
        val base = o ?: Subscription(name = "", amount = 0.0, currency = "", nextBillingDay = 0, anchorDay = 1)
        return base.copy(
            name = name.trim(), iconType = iconType, iconValue = iconValue, color = color,
            amount = amt, currency = currency, cycleCount = cnt, cycleUnit = cycleUnit,
            nextBillingDay = next.toEpochDay(),
            anchorDay = effectiveAnchorDay(),
            payMethod = payMethod, autoRenew = autoRenew, category = category,
            remindEnabled = remind, remindDaysBefore = remindDays, active = active, note = note.trim(),
            presetKey = presetKey,
            balance = bal,
            remindedForDay = o?.remindedForDay,
            overdueNotifiedDay = if (dateChanged) null else o?.overdueNotifiedDay,
        )
    }
}

/** 表单存进 Bundle（original 不存，恢复后从数据库重新读） */
private val FormSaver = androidx.compose.runtime.saveable.listSaver<Form, Any?>(
    save = { f ->
        listOf(
            f.name, f.iconType.name, f.iconValue, f.color, f.amount, f.currency, f.cycleCount, f.cycleUnit.name,
            f.next.toEpochDay(), f.payMethod.name, f.autoRenew, f.category.name, f.remind, f.remindDays, f.active,
            f.note, f.presetKey, f.loaded, f.balance,
        )
    },
    restore = { l ->
        Form().apply {
            name = l[0] as String; iconType = IconType.valueOf(l[1] as String); iconValue = l[2] as String
            color = l[3] as Int; amount = l[4] as String; currency = l[5] as String; cycleCount = l[6] as String
            cycleUnit = CycleUnit.valueOf(l[7] as String); next = LocalDate.ofEpochDay(l[8] as Long)
            payMethod = PayMethod.valueOf(l[9] as String); autoRenew = l[10] as Boolean; category = Category.valueOf(l[11] as String)
            remind = l[12] as Boolean; remindDays = l[13] as Int; active = l[14] as Boolean
            note = l[15] as String; presetKey = l[16] as String?; loaded = l[17] as Boolean
            balance = (l.getOrNull(18) as? String).orEmpty()
        }
    },
)

@Composable
fun EditScreen(vm: MainViewModel, id: Long, presetKey: String?, onBack: () -> Unit, prefillName: String = "") {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 表单状态可保存：去相册选图时 Activity 可能被系统回收重建，回来后填写的内容和选图结果都不能丢
    val form = rememberSaveable(saver = FormSaver) { Form() }
    val st by vm.state.collectAsState()
    val extra = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    var iconSheet by rememberSaveable { mutableStateOf(false) }
    var cropUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    val photo = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            iconSheet = false
            cropUri = uri
        }
    }
    var currencyDlg by remember { mutableStateOf(false) }
    var dateDlg by remember { mutableStateOf(false) }
    var deleteDlg by remember { mutableStateOf(false) }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(id, presetKey) {
        if (form.loaded) {
            // 从保存的状态恢复：只补回原始记录，不覆盖用户已改的内容
            if (id > 0 && form.original == null) form.original = vm.observeSub(id).first()
            return@LaunchedEffect
        }
        if (id > 0) {
            vm.observeSub(id).first()?.let(form::load)
        } else if (presetKey != null) {
            form.applyPreset(presetKey, IconFiles.presetRes(context, presetKey) != 0)
        } else {
            form.currency = vm.state.value.settings.displayCurrency
            // 从搜索框带过来的名称
            form.name = prefillName
        }
        form.loaded = true
    }
    val paymentsFlow = remember(id) { if (id > 0) vm.observePayments(id) else kotlinx.coroutines.flow.flowOf(emptyList()) }
    val payments by paymentsFlow.collectAsState(emptyList())

    val save = save@{
        // 编辑已有订阅时，原始记录还没读出来就先不保存，免得被当成新订阅
        if (id > 0 && form.original == null) return@save
        val s = form.build()
        if (s == null) {
            showErrors = true
            vm.toast("请填写名称、金额和正确的周期")
        } else {
            vm.save(s)
            onBack()
        }
    }

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopAppBar(
                title = { Text(if (id > 0) "编辑订阅" else "新建订阅", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") } },
                actions = {
                    if (id > 0) IconButton(onClick = { deleteDlg = true }) { Icon(Icons.Rounded.DeleteOutline, "删除", tint = extra.danger) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background, scrolledContainerColor = cs.background),
            )
        },
        bottomBar = {
            Box(
                Modifier.fillMaxWidth().background(cs.background).navigationBarsPadding().imePadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Box(
                    Modifier.fillMaxWidth().height(54.dp)
                        .shadow(12.dp, RoundedCornerShape(18.dp), spotColor = Color(0xFF4F46E5))
                        .clip(RoundedCornerShape(18.dp)).background(extra.accentBrush).clickable { save() },
                    contentAlignment = Alignment.Center,
                ) { Text(if (id > 0) "保存修改" else "添加订阅", style = MaterialTheme.typography.titleMedium, color = Color.White) }
            }
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 图标 + 名称
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clickable(interactionSource = null, indication = null) { iconSheet = true }) {
                    Box(
                        Modifier.shadow(18.dp, RoundedCornerShape(26.dp), spotColor = Color(form.color).copy(alpha = 0.6f), ambientColor = Color(form.color).copy(alpha = 0.3f)),
                    ) {
                        SubIcon(form.iconType, form.iconValue, form.color, form.name.ifBlank { "订" }, size = 88.dp)
                    }
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp).size(30.dp).clip(CircleShape)
                            .background(cs.background).padding(3.dp).clip(CircleShape).background(extra.accentBrush),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Edit, "更换图标", Modifier.size(15.dp), tint = Color.White) }
                }
                Spacer(Modifier.height(18.dp))
                Field(
                    form.name, { form.name = it }, "订阅名称",
                    isError = showErrors && form.name.isBlank(), modifier = Modifier.fillMaxWidth(),
                )
            }

            Group("金额与周期") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(
                            form.amount, { form.amount = it.filter { c -> c.isDigit() || c == '.' } }, "付款金额",
                            keyboard = KeyboardType.Decimal,
                            isError = showErrors && form.amount.toDoubleOrNull() == null,
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.titleLarge.merge(TabularNums),
                        )
                        Spacer(Modifier.width(10.dp))
                        CurrencyBadge(form.currency) { currencyDlg = true }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("每", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(8.dp))
                        Field(
                            form.cycleCount, { form.cycleCount = it.filter(Char::isDigit).take(3) }, null,
                            keyboard = KeyboardType.Number,
                            isError = showErrors && (form.cycleCount.toIntOrNull() ?: 0) < 1,
                            modifier = Modifier.width(64.dp),
                            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                        )
                        Spacer(Modifier.width(10.dp))
                        Segmented(CycleUnit.entries, form.cycleUnit, { it.label }, Modifier.weight(1f)) { form.cycleUnit = it }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).clickable { dateDlg = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconTile(Icons.Rounded.CalendarMonth, cs.primary, size = 34.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("下次付款日期", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            Text(form.next.format(DateTimeFormatter.ofPattern("yyyy年M月d日（E）", java.util.Locale.CHINA)), style = MaterialTheme.typography.titleSmall)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = cs.outline)
                    }
                }
            }

            Group("付款方式") {
                Column(Modifier.padding(16.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PayMethod.entries.forEach { m ->
                            Chip(m.label, form.payMethod == m) {
                                if (form.payMethod != m) form.autoRenew = m.defaultAuto
                                form.payMethod = m
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    SwitchRow(
                        Icons.Rounded.Autorenew, extra.success, "自动续费",
                        if (form.autoRenew) "到期自动扣款，账单日过后自动记账并更新下次日期" else "手动付款：到期后需点「我已付款」才会更新下次日期",
                        form.autoRenew,
                    ) { form.autoRenew = it }
                }
            }

            if (form.payMethod == PayMethod.BALANCE) {
                Group("余额") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Field(
                                form.balance,
                                { form.balance = it.filter { c -> c.isDigit() || c == '.' } },
                                "当前余额",
                                keyboard = KeyboardType.Decimal,
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.titleLarge.merge(TabularNums),
                            )
                            Spacer(Modifier.width(10.dp))
                            CurrencyBadge(form.currency) { currencyDlg = true }
                        }
                        BalanceForecastCard(
                            balance = form.balance.replace(",", "").toDoubleOrNull() ?: 0.0,
                            amount = form.amount.replace(",", "").toDoubleOrNull(),
                            currency = form.currency,
                            next = form.next,
                            cycleCount = form.cycleCount.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                            unit = form.cycleUnit,
                            anchorDay = form.effectiveAnchorDay(),
                        )
                    }
                }
            }

            Group("分类") {
                FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { c -> Chip(c.label, form.category == c) { form.category = c } }
                }
            }

            Group("提醒与状态") {
                Column(Modifier.padding(16.dp)) {
                    SwitchRow(
                        Icons.Rounded.NotificationsActive, extra.warning, "扣款提醒",
                        if (form.remindDays == 0) "仅在账单日当天提醒" else "从账单日前 ${form.remindDays} 天开始，每天提醒一次，直到账单日",
                        form.remind,
                    ) { form.remind = it }
                    if (form.remind) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("提前天数", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            StepButton("−", enabled = form.remindDays > 0) { form.remindDays-- }
                            Text(
                                if (form.remindDays == 0) "当天" else "${form.remindDays} 天",
                                style = MaterialTheme.typography.titleMedium.merge(TabularNums),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.width(64.dp),
                            )
                            StepButton("+", enabled = form.remindDays < 30) { form.remindDays++ }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 3, 7, 14).forEach { d -> Chip("${d}天", form.remindDays == d) { form.remindDays = d } }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = cs.outlineVariant)
                    Spacer(Modifier.height(14.dp))
                    SwitchRow(Icons.Rounded.PowerSettingsNew, cs.primary, "启用中", "关闭后不计入统计、不提醒（相当于已退订）", form.active) { form.active = it }
                }
            }

            Group("备注") {
                Field(
                    form.note, { form.note = it }, "账号、套餐等（可选）", singleLine = false,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }

            if (id > 0) {
                form.build()?.let { s ->
                    val cur = st.settings.displayCurrency
                    Group("折合") {
                        Row(Modifier.padding(16.dp)) {
                            listOf(SummaryPeriod.WEEK, SummaryPeriod.MONTH, SummaryPeriod.YEAR).forEach { p ->
                                Column(Modifier.weight(1f)) {
                                    Text(p.unitLabel, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                    Text(
                                        Currencies.formatList(st.rates.convert(Billing.periodCost(s, p), s.currency, cur), cur),
                                        style = MaterialTheme.typography.titleMedium.merge(TabularNums),
                                    )
                                }
                            }
                        }
                    }
                    if (!s.autoRenew && s.active) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { vm.skip(s); onBack() }, Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("跳过本期") }
                            Button(onClick = { vm.confirmPaid(s); onBack() }, Modifier.weight(1.4f).height(48.dp), shape = RoundedCornerShape(14.dp)) { Text("本期已付款") }
                        }
                    }
                }
                Group("付款记录 · ${payments.size}") {
                    if (payments.isEmpty()) {
                        Text(
                            "暂无记录。自动续费在账单日过后自动记账，手动付款在确认后记账。",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp),
                        )
                    }
                    payments.take(60).forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), color = cs.outlineVariant)
                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Currencies.formatList(p.amount, p.currency), style = MaterialTheme.typography.titleSmall.merge(TabularNums))
                                Text(
                                    LocalDate.ofEpochDay(p.billingDay).format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                                )
                            }
                            Pill(if (p.auto) "自动" else "手动确认", if (p.auto) extra.success else cs.primary)
                            IconButton(onClick = {
                                if (p.balanceUsed > 0.0) {
                                    val current = form.balance.replace(",", "").toDoubleOrNull() ?: 0.0
                                    form.balance = (current + p.balanceUsed).toBigDecimal().stripTrailingZeros().toPlainString()
                                }
                                vm.deletePayment(p)
                            }) { Icon(Icons.Rounded.Close, "删除记录", Modifier.size(18.dp), tint = cs.outline) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (iconSheet) {
        IconPickerSheet(
            color = form.color, name = form.name,
            onDismiss = { iconSheet = false },
            onPick = { t, v -> form.iconType = t; form.iconValue = v; iconSheet = false },
            onColor = { form.color = it },
            onPickPhoto = {
                photo.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )
    }
    cropUri?.let { uri ->
        com.subnook.app.ui.components.ImageCropDialog(
            uri,
            onDismiss = { cropUri = null },
            onDone = { name -> form.iconType = IconType.CUSTOM; form.iconValue = name; cropUri = null },
        )
    }
    if (currencyDlg) CurrencyPickerDialog(form.currency, { currencyDlg = false }) { form.currency = it; currencyDlg = false }
    if (dateDlg) {
        val state = rememberDatePickerState(initialSelectedDateMillis = form.next.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { dateDlg = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { form.next = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    dateDlg = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { dateDlg = false }) { Text("取消") } },
        ) { DatePicker(state) }
    }
    if (deleteDlg) {
        AlertDialog(
            onDismissRequest = { deleteDlg = false },
            title = { Text("删除「${form.name}」？") },
            text = { Text("订阅和它的付款记录都会被删除，无法恢复。如果只是退订，可以关闭「启用中」保留记录。") },
            confirmButton = {
                TextButton(onClick = { form.original?.let(vm::delete); deleteDlg = false; onBack() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteDlg = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun CurrencyBadge(code: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(Currencies.listSymbol(code), style = MaterialTheme.typography.titleMedium)
        Text(Currencies.name(code), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
}

@Composable
private fun BalanceForecastCard(
    balance: Double,
    amount: Double?,
    currency: String,
    next: LocalDate,
    cycleCount: Int,
    unit: CycleUnit,
    anchorDay: Int,
) {
    val cs = MaterialTheme.colorScheme
    val extra = LocalExtra.current
    val today = Billing.today()
    val forecast = amount?.takeIf { it > 0 }?.let {
        Balance.forecast(balance, it, next, cycleCount, unit, anchorDay)
    }
    val title = when {
        forecast == null -> "填写付款金额后自动计算"
        forecast.periods == 0 -> "余额不足 1 期"
        else -> "余额可用 ${Balance.periodsText(forecast)}"
    }
    val desc = when {
        forecast == null -> "余额按订阅币种填写，付款记账时自动扣减"
        forecast.periods == 0 ->
            "还需 ${Currencies.formatList(forecast.shortfall, currency)} · " +
                if (forecast.rechargeOn <= today) "建议立即充值"
                else "${Balance.dateText(forecast.rechargeOn, today)}前充值"
        forecast.rechargeOn <= today -> "余额即将用完，请尽快充值"
        else -> "预计 ${Balance.dateText(forecast.rechargeOn, today)} 用完，届时需充值"
    }
    val tint = when {
        forecast == null -> cs.primary
        forecast.periods == 0 -> extra.danger
        else -> extra.success
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.AccountBalanceWallet, tint, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        if (amount != null && amount > 0) {
            Text(
                "每期 ${Currencies.formatList(amount, currency)} · 记账时从余额扣减",
                style = MaterialTheme.typography.labelSmall, color = cs.outline,
                modifier = Modifier.padding(start = 42.dp),
            )
        }
    }
}

/** 填充式输入框（无描边，浅灰底） */
@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    singleLine: Boolean = true,
    textStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val cs = MaterialTheme.colorScheme
    TextField(
        value, onChange, modifier,
        label = label?.let { { Text(it) } },
        singleLine = singleLine, minLines = if (singleLine) 1 else 2,
        isError = isError, textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceContainer, unfocusedContainerColor = cs.surfaceContainer, errorContainerColor = cs.surfaceContainer,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(cs.surfaceContainerLowest)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = if (enabled) cs.primary else cs.outline)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val extra = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.clip(RoundedCornerShape(12.dp))
            .then(if (selected) Modifier.background(extra.accentBrush) else Modifier.background(cs.surfaceContainer))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) Color.White else cs.onSurface)
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainer).padding(4.dp)) {
        options.forEach { o ->
            val sel = o == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                    .then(if (sel) Modifier.shadow(3.dp, RoundedCornerShape(11.dp)).background(cs.surfaceContainerLowest) else Modifier)
                    .clickable { onSelect(o) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), style = MaterialTheme.typography.labelLarge, color = if (sel) cs.primary else cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SwitchRow(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(interactionSource = null, indication = null) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tint, size = 34.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked, onChange)
    }
}
