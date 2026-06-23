package com.pos.cosmos.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.pos.cosmos.local.entities.*

/**
 * Phase 1 DAOs — minimal surface (count + insert + selectAll + clear) so the
 * SyncDataScreen can render row counts and the SyncRepository can do upserts.
 *
 * Per-screen query methods (findByBarcode, ledger queries, etc.) will be added
 * in phase 2 as the offline screens consume them.
 */

@Dao
interface CompanyDao {
    @Query("SELECT COUNT(*) FROM companies") suspend fun count(): Int
    @Query("DELETE FROM companies") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<CompanyEntity>)
    @Query("SELECT * FROM companies") suspend fun all(): List<CompanyEntity>
}

@Dao
interface CategoryDao {
    @Query("SELECT COUNT(*) FROM categories") suspend fun count(): Int
    @Query("SELECT MAX(updatedAt) FROM categories") suspend fun maxUpdatedAt(): Long?
    @Query("DELETE FROM categories") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<CategoryEntity>)
    @Query("SELECT * FROM categories WHERE active = 'Y' ORDER BY name") suspend fun all(): List<CategoryEntity>
}

@Dao
interface ItemDao {
    @Query("SELECT COUNT(*) FROM items") suspend fun count(): Int
    @Query("SELECT MAX(updatedAt) FROM items") suspend fun maxUpdatedAt(): Long?
    @Query("DELETE FROM items") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<ItemEntity>)
    @Query("SELECT * FROM items WHERE active = 'Y' ORDER BY name") suspend fun all(): List<ItemEntity>
    @Query("SELECT * FROM items WHERE barcode = :barcode AND active = 'Y' LIMIT 1")
    suspend fun findByBarcode(barcode: String): ItemEntity?
    @Query("SELECT * FROM items WHERE name LIKE '%' || :query || '%' AND active = 'Y' LIMIT 100")
    suspend fun searchByName(query: String): List<ItemEntity>
}

@Dao
interface CustomerDao {
    @Query("SELECT COUNT(*) FROM customers") suspend fun count(): Int
    @Query("SELECT MAX(updatedAt) FROM customers") suspend fun maxUpdatedAt(): Long?
    @Query("DELETE FROM customers") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<CustomerEntity>)
    @Query("SELECT * FROM customers WHERE active = 'Y' ORDER BY name") suspend fun all(): List<CustomerEntity>
    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' AND active = 'Y' LIMIT 100")
    suspend fun searchByName(query: String): List<CustomerEntity>
}

@Dao
interface SupplierDao {
    @Query("SELECT COUNT(*) FROM suppliers") suspend fun count(): Int
    @Query("SELECT MAX(updatedAt) FROM suppliers") suspend fun maxUpdatedAt(): Long?
    @Query("DELETE FROM suppliers") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<SupplierEntity>)
    @Query("SELECT * FROM suppliers WHERE active = 'Y' ORDER BY name") suspend fun all(): List<SupplierEntity>
}

@Dao
interface SalesRefDao {
    @Query("SELECT COUNT(*) FROM sales_refs") suspend fun count(): Int
    @Query("DELETE FROM sales_refs") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<SalesRefEntity>)
    @Query("SELECT * FROM sales_refs WHERE active = 'Y'") suspend fun all(): List<SalesRefEntity>
}

@Dao
interface InvoiceDao {
    @Query("SELECT COUNT(*) FROM invoices") suspend fun count(): Int
    @Query("DELETE FROM invoices") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<InvoiceEntity>)
    @Query("SELECT * FROM invoices WHERE isSynced = 0") suspend fun unsynced(): List<InvoiceEntity>
}

@Dao
interface StockItemDao {
    @Query("SELECT COUNT(*) FROM stock_items") suspend fun count(): Int
    @Query("DELETE FROM stock_items") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<StockItemEntity>)
}

@Dao
interface BillDao {
    @Query("SELECT COUNT(*) FROM bills") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM bills WHERE isSynced = 0") suspend fun unsyncedCount(): Int
    @Query("DELETE FROM bills") suspend fun clear()
    @Query("DELETE FROM bills WHERE isSynced = 1") suspend fun clearSynced()
    @Query("DELETE FROM bill_lines WHERE billLocalId IN (SELECT id FROM bills WHERE isSynced = 1)")
    suspend fun clearSyncedLines()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(bill: BillEntity): Long
    @Update suspend fun update(bill: BillEntity)
    @Query("SELECT * FROM bills WHERE isSynced = 0 AND active = 'Y'") suspend fun unsynced(): List<BillEntity>
    @Query("SELECT * FROM bills ORDER BY createdAt DESC LIMIT :limit") suspend fun recent(limit: Int = 100): List<BillEntity>
    @Query("UPDATE bills SET isSynced = 1, serverId = :serverId WHERE id = :localId")
    suspend fun markSynced(localId: Long, serverId: Long)
    @Query("SELECT MAX(billNo) FROM bills") suspend fun maxBillNo(): Long?
    @Query("SELECT MAX(billNo) FROM bills WHERE billType = :billType")
    suspend fun maxBillNoForType(billType: String): Long?
}

@Dao
interface BillLineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(lines: List<BillLineEntity>)
    @Query("SELECT * FROM bill_lines WHERE billLocalId = :billLocalId") suspend fun forBill(billLocalId: Long): List<BillLineEntity>
    @Query("DELETE FROM bill_lines WHERE billLocalId = :billLocalId") suspend fun deleteForBill(billLocalId: Long)
}

@Dao
interface CustomerPaymentDao {
    @Query("SELECT COUNT(*) FROM customer_payments") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM customer_payments WHERE isSynced = 0") suspend fun unsyncedCount(): Int
    @Query("DELETE FROM customer_payments") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<CustomerPaymentEntity>)
    @Query("SELECT * FROM customer_payments WHERE isSynced = 0") suspend fun unsynced(): List<CustomerPaymentEntity>
}

@Dao
interface OwnerCustomerPaymentDao {
    @Query("SELECT COUNT(*) FROM owner_customer_payments") suspend fun count(): Int
    @Query("SELECT COUNT(*) FROM owner_customer_payments WHERE isSynced = 0") suspend fun unsyncedCount(): Int
    @Query("DELETE FROM owner_customer_payments") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<OwnerCustomerPaymentEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(row: OwnerCustomerPaymentEntity): Long
    @Query("SELECT * FROM owner_customer_payments WHERE isSynced = 0") suspend fun unsynced(): List<OwnerCustomerPaymentEntity>
    @Query("UPDATE owner_customer_payments SET isSynced = 1, serverId = :serverId WHERE id = :id") suspend fun markSynced(id: Long, serverId: Long)
}

@Dao
interface SupplierPaymentDao {
    @Query("SELECT COUNT(*) FROM supplier_payments") suspend fun count(): Int
    @Query("DELETE FROM supplier_payments") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<SupplierPaymentEntity>)
}

@Dao
interface ExpenseDao {
    @Query("SELECT COUNT(*) FROM expenses") suspend fun count(): Int
    @Query("DELETE FROM expenses") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<ExpenseEntity>)
}

@Dao
interface BillTemplateDao {
    @Query("SELECT COUNT(*) FROM bill_templates") suspend fun count(): Int
    @Query("DELETE FROM bill_templates") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<BillTemplateEntity>)
}

@Dao
interface BillingConfigDao {
    @Query("SELECT COUNT(*) FROM billing_configs") suspend fun count(): Int
    @Query("DELETE FROM billing_configs") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<BillingConfigEntity>)
    @Query("SELECT * FROM billing_configs") suspend fun all(): List<BillingConfigEntity>
}

@Dao
interface UserPermissionDao {
    @Query("SELECT COUNT(*) FROM user_permissions") suspend fun count(): Int
    @Query("DELETE FROM user_permissions") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<UserPermissionEntity>)
    @Query("SELECT * FROM user_permissions WHERE username = :username LIMIT 1")
    suspend fun forUser(username: String): UserPermissionEntity?
}

@Dao
interface UiConfigDao {
    @Query("SELECT COUNT(*) FROM ui_configs") suspend fun count(): Int
    @Query("DELETE FROM ui_configs") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<UiConfigEntity>)
    @Query("SELECT * FROM ui_configs") suspend fun all(): List<UiConfigEntity>
}

@Dao
interface KitchenOrderDao {
    @Query("SELECT COUNT(*) FROM kitchen_orders") suspend fun count(): Int
    @Query("DELETE FROM kitchen_orders") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAll(rows: List<KitchenOrderEntity>)
}
