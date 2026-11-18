package com.subnook.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

enum class CycleUnit(val label: String, val approxDays: Double) {
    DAY("天", 1.0),
    WEEK("周", 7.0),
    MONTH("月", 365.2425 / 12.0),
    YEAR("年", 365.2425),
}

enum class PayMethod(val label: String, val defaultAuto: Boolean) {
    GOOGLE_PLAY("Google Play", true),
    APP_STORE("App Store", true),
    WECHAT("微信", true),
    ALIPAY("支付宝", true),
    CREDIT_CARD("信用卡", true),
    PAYPAL("PayPal", true),
    BANK_CARD("银行卡", true),
    BALANCE("余额", true),
    MANUAL("手动付款", false),
    OTHER("其他", false),
}

enum class Category(val label: String) {
    VIDEO("影视"),
    MUSIC("音乐音频"),
    AI("AI"),
    CLOUD("云存储"),
    TOOLS("工具效率"),
    EDU("学习阅读"),
    SHOPPING("购物外卖"),
    SOCIAL("社交"),
    GAME("游戏"),
    VPN("网络"),
    TELECOM("通信运营商"),
    NEWS("新闻资讯"),
    LIFE("生活"),
    OTHER("其他"),
}

/** 图标类型：PRESET=内置品牌图(value=预设key) / MATERIAL=设计图标(value=图标名) / CUSTOM=相册图片(value=文件名) / TEXT=文字(value=文字) */
enum class IconType { PRESET, MATERIAL, CUSTOM, TEXT }

@Serializable
@Entity(tableName = "subscriptions")
data class Subscription(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val iconType: IconType = IconType.TEXT,
    val iconValue: String = "",
    val color: Int = 0xFF00897B.toInt(),
    val amount: Double,
    val currency: String,
    val cycleCount: Int = 1,
    val cycleUnit: CycleUnit = CycleUnit.MONTH,
    /** 下次付款日期 (LocalDate.toEpochDay) */
    val nextBillingDay: Long,
    /** 账单日锚点(1..31)，用于按月/按年计算时保持月末日期不漂移 */
    val anchorDay: Int,
    val payMethod: PayMethod = PayMethod.OTHER,
    val autoRenew: Boolean = true,
    val category: Category = Category.OTHER,
    val remindEnabled: Boolean = true,
    val remindDaysBefore: Int = 3,
    val active: Boolean = true,
    val note: String = "",
    val presetKey: String? = null,
    /** 最后一次发送"即将扣款"提醒的日期（每天最多提醒一次） */
    val remindedForDay: Long? = null,
    /** 手动付款逾期提醒最后发送的日期 */
    val overdueNotifiedDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** 手动排序的位置，越小越靠前（DB v2 新增） */
    @ColumnInfo(defaultValue = "0") val sortIndex: Int = 0,
    /** 余额付款的剩余余额，币种与订阅金额相同（DB v3 新增） */
    @ColumnInfo(defaultValue = "0") val balance: Double = 0.0,
)

@Serializable
@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(
        entity = Subscription::class,
        parentColumns = ["id"],
        childColumns = ["subscriptionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("subscriptionId"), Index("billingDay")],
)
data class PaymentRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: Long,
    /** 该笔对应的账单日 */
    val billingDay: Long,
    val amount: Double,
    val currency: String,
    /** true=自动续费自动记账，false=手动确认 */
    val auto: Boolean,
    val recordedAt: Long = System.currentTimeMillis(),
    /** 这笔付款从订阅余额里扣掉的钱；0 表示不是余额付款（DB v3 新增） */
    @ColumnInfo(defaultValue = "0") val balanceUsed: Double = 0.0,
)

data class Preset(
    val key: String,
    val name: String,
    val category: Category,
    val colorLong: Long,
    val price: Double,
    val currency: String,
    val cycleCount: Int,
    val cycleUnit: CycleUnit,
    val payMethod: PayMethod,
    val material: String,
) {
    val color: Int get() = colorLong.toInt()
}
