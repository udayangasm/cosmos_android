package com.pos.cosmos.local

import android.content.Context
import com.pos.cosmos.local.entities.BillEntity
import com.pos.cosmos.local.entities.BillLineEntity
import com.pos.cosmos.local.entities.CustomerPaymentEntity
import com.pos.cosmos.sync.SyncWorker
import com.pos.cosmos.utils.AppSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Writes a bill (and optionally a customer payment) to the local Room DB
 * with isSynced=false, then ensures the periodic SyncWorker is scheduled.
 * Mirrors the desktop CashBillForm/CreditBillForm SaveBillLocal path.
 */
data class LocalBillLine(
    val itemId: Long,
    val unitPrice: Double,
    val qty: Double,
    val discount: Double = 0.0,
    val discountValue: Double = 0.0,
    val freeIssueQty: Double = 0.0
)

data class LocalPayment(
    val amount: Double,
    val method: String,
    val note: String? = null
)

object LocalBillSaver {

    /** Returns the next local bill number across all types (MAX+1). */
    suspend fun nextBillNo(context: Context): Long {
        val db = CosmosDatabase.get(context)
        return (db.billDao().maxBillNo() ?: 0L) + 1L
    }

    /** Returns the next local bill number for a specific billType (e.g. "CASH" or "CREDIT"). */
    suspend fun nextBillNoForType(context: Context, billType: String): Long {
        val db = CosmosDatabase.get(context)
        return (db.billDao().maxBillNoForType(billType) ?: 0L) + 1L
    }

    /**
     * Saves a bill locally. If [billNo] is 0 or negative, a new sequential
     * number is allocated via [nextBillNo]. Returns the effective bill number
     * that was written (for display / receipt printing).
     */
    suspend fun saveBill(
        context: Context,
        billNo: Long = 0,
        billType: String,
        customerId: Long,
        cashCustomerName: String?,
        salesRefId: Long,
        total: Double,
        discount: Double,
        discountValue: Double,
        cash: Double,
        cardPayment: Double = 0.0,
        lines: List<LocalBillLine>,
        payment: LocalPayment? = null
    ): Long {
        val db = CosmosDatabase.get(context)
        val dateStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val effectiveBillNo = if (billNo > 0) billNo else nextBillNoForType(context, billType)

        val bill = BillEntity(
            serverId = null,
            billNo = effectiveBillNo,
            billingDate = dateStr,
            billType = billType,
            customerId = customerId,
            cashCustomerName = cashCustomerName,
            salesRefId = salesRefId,
            total = total,
            discount = discount,
            discountValue = discountValue,
            cash = cash,
            cardPayment = cardPayment,
            companyCode = 0,
            createdByUser = null,
            deviceName = android.os.Build.MODEL,
            isSynced = false,
            active = "Y"
        )
        val localId = db.billDao().insert(bill)

        db.billLineDao().insertAll(lines.map {
            BillLineEntity(
                billLocalId = localId,
                itemId = it.itemId,
                unitPrice = it.unitPrice,
                qty = it.qty,
                returnQty = 0.0,
                discount = it.discount,
                discountValue = it.discountValue,
                freeIssueQty = it.freeIssueQty
            )
        })

        if (payment != null && customerId > 0) {
            db.customerPaymentDao().upsertAll(
                listOf(
                    CustomerPaymentEntity(
                        customerId = customerId,
                        paymentDate = dateStr,
                        amount = payment.amount,
                        method = payment.method,
                        note = payment.note,
                        companyCode = 0,
                        isSynced = false
                    )
                )
            )
        }

        // Keep the periodic worker registered as a safety net. The actual
        // push happens on the in-app 5-minute sync loop (MainActivity) — we
        // do NOT trigger an immediate push here, so the bill stays purely
        // local until the next scheduled tick. This matches desktop's
        // SyncDataService timer behavior.
        val settings = AppSettings.get(context)
        runCatching {
            SyncWorker.schedule(context, settings.autoSyncIntervalMinutes.value)
        }
        return effectiveBillNo
    }
}
