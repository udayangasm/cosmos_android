package com.pos.cosmos.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pos.cosmos.local.dao.*
import com.pos.cosmos.local.entities.*

@Database(
    entities = [
        CompanyEntity::class,
        CategoryEntity::class,
        ItemEntity::class,
        CustomerEntity::class,
        SupplierEntity::class,
        SalesRefEntity::class,
        InvoiceEntity::class,
        StockItemEntity::class,
        BillEntity::class,
        BillLineEntity::class,
        CustomerPaymentEntity::class,
        OwnerCustomerPaymentEntity::class,
        SupplierPaymentEntity::class,
        ExpenseEntity::class,
        BillTemplateEntity::class,
        BillingConfigEntity::class,
        UserPermissionEntity::class,
        UiConfigEntity::class,
        KitchenOrderEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class CosmosDatabase : RoomDatabase() {
    abstract fun companyDao(): CompanyDao
    abstract fun categoryDao(): CategoryDao
    abstract fun itemDao(): ItemDao
    abstract fun customerDao(): CustomerDao
    abstract fun supplierDao(): SupplierDao
    abstract fun salesRefDao(): SalesRefDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun stockItemDao(): StockItemDao
    abstract fun billDao(): BillDao
    abstract fun billLineDao(): BillLineDao
    abstract fun customerPaymentDao(): CustomerPaymentDao
    abstract fun ownerCustomerPaymentDao(): OwnerCustomerPaymentDao
    abstract fun supplierPaymentDao(): SupplierPaymentDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun billTemplateDao(): BillTemplateDao
    abstract fun billingConfigDao(): BillingConfigDao
    abstract fun userPermissionDao(): UserPermissionDao
    abstract fun uiConfigDao(): UiConfigDao
    abstract fun kitchenOrderDao(): KitchenOrderDao

    companion object {
        @Volatile private var instance: CosmosDatabase? = null

        /** v3 adds items.hideForNewOrders. A real migration (not destructive) so
         *  offline bills that are not pushed yet survive the app update. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN hideForNewOrders INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): CosmosDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CosmosDatabase::class.java,
                    "cosmos.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
