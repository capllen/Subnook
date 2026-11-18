package com.subnook.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY active DESC, nextBillingDay ASC")
    fun observeAll(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions")
    suspend fun getAll(): List<Subscription>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun get(id: Long): Subscription?

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    fun observe(id: Long): Flow<Subscription?>

    @Upsert
    suspend fun upsert(sub: Subscription): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subs: List<Subscription>)

    @Delete
    suspend fun delete(sub: Subscription)

    @Query("DELETE FROM subscriptions")
    suspend fun deleteAll()

    @Insert
    suspend fun insertPayment(p: PaymentRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(ps: List<PaymentRecord>)

    @Delete
    suspend fun deletePayment(p: PaymentRecord)

    /** 删除一条付款记录；如果当时从余额扣过款，把这部分加回余额 */
    @Transaction
    suspend fun deletePaymentWithRefund(p: PaymentRecord) {
        deletePayment(p)
        if (p.balanceUsed <= 0.0) return
        val sub = get(p.subscriptionId) ?: return
        upsert(sub.copy(balance = sub.balance + p.balanceUsed))
    }

    @Query("SELECT * FROM payments WHERE subscriptionId = :subId ORDER BY billingDay DESC")
    fun observePayments(subId: Long): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payments WHERE billingDay BETWEEN :from AND :to")
    fun observePaymentsBetween(from: Long, to: Long): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payments")
    suspend fun getAllPayments(): List<PaymentRecord>

    @Query("SELECT * FROM payments ORDER BY billingDay DESC")
    fun observeAllPayments(): Flow<List<PaymentRecord>>

    @Query("UPDATE subscriptions SET sortIndex = :index WHERE id = :id")
    suspend fun setSortIndex(id: Long, index: Int)

    /** 按 [ids] 的顺序写入手动排序位置 */
    @Transaction
    suspend fun setManualOrder(ids: List<Long>) {
        ids.forEachIndexed { i, id -> setSortIndex(id, i) }
    }

    @Query("SELECT COALESCE(MAX(sortIndex), -1) FROM subscriptions")
    suspend fun maxSortIndex(): Int

    @Transaction
    suspend fun replaceAll(subs: List<Subscription>, payments: List<PaymentRecord>) {
        deleteAll() // payments cascade
        insertAll(subs)
        insertPayments(payments)
    }
}

@Database(entities = [Subscription::class, PaymentRecord::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): SubscriptionDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "subnook.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v2：新增手动排序位置 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN sortIndex INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3：余额付款 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN balance REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE payments ADD COLUMN balanceUsed REAL NOT NULL DEFAULT 0")
            }
        }
    }
}
