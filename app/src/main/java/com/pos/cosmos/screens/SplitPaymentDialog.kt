package com.pos.cosmos.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pos.cosmos.api.ApiConfig
import com.pos.cosmos.utils.LocationHelper
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class SplitPaymentRow(
    var amount: String = "",
    var type: String = "cash",            // cash / cheque / card
    var trn: String = "",
    var chequeNumber: String = "",
    var chequeDate: String = "",
    var bankAndBranch: String = "",
    var accountNumber: String = "",
    var cardLastFourDigits: String = ""
)

/**
 * Multi-row payment dialog. Sum of row amounts must be > 0 and <= billTotal.
 * Pending balance (billTotal - sum) is allowed and will show up on the
 * Payments screen as an unpaid amount for the bill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPaymentDialog(
    billTotal: Double,
    onDismiss: () -> Unit,
    onConfirm: (List<SplitPaymentRow>) -> Unit
) {
    var rows by remember { mutableStateOf(listOf(SplitPaymentRow())) }
    var error by remember { mutableStateOf<String?>(null) }

    val total = rows.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val pending = (billTotal - total).coerceAtLeast(0.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split Payment") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("Bill Total: %.2f".format(billTotal), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                rows.forEachIndexed { idx, row ->
                    PaymentRowEditor(
                        index = idx,
                        row = row,
                        onChange = { updated ->
                            rows = rows.toMutableList().also { it[idx] = updated }
                        },
                        onRemove = if (rows.size > 1) {
                            { rows = rows.toMutableList().also { it.removeAt(idx) } }
                        } else null
                    )
                    Divider(Modifier.padding(vertical = 6.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { rows = rows + SplitPaymentRow() }) {
                        Icon(Icons.Default.Add, contentDescription = "Add row")
                        Spacer(Modifier.width(4.dp))
                        Text("Add Payment")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Entered: %.2f".format(total), fontWeight = FontWeight.SemiBold)
                Text(
                    "Pending: %.2f".format(pending),
                    fontWeight = FontWeight.SemiBold,
                    color = if (pending > 0) Color(0xFFB26A00) else Color(0xFF2E7D32)
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Color.Red, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cleanRows = rows.filter { (it.amount.toDoubleOrNull() ?: 0.0) > 0.0 }
                if (cleanRows.isEmpty()) {
                    error = "Add at least one payment with amount > 0."
                    return@Button
                }
                val sum = cleanRows.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
                if (sum > billTotal + 0.005) {
                    error = "Total entered (%.2f) exceeds bill total (%.2f).".format(sum, billTotal)
                    return@Button
                }
                onConfirm(cleanRows)
            }) { Text("Save Bill") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentRowEditor(
    index: Int,
    row: SplitPaymentRow,
    onChange: (SplitPaymentRow) -> Unit,
    onRemove: (() -> Unit)?
) {
    var showDatePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("#${index + 1}", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
            // Payment type chips
            PaymentTypeChip("cash", row.type) { onChange(row.copy(type = "cash")) }
            PaymentTypeChip("cheque", row.type) { onChange(row.copy(type = "cheque")) }
            PaymentTypeChip("card", row.type) { onChange(row.copy(type = "card")) }
            Spacer(Modifier.weight(1f))
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red)
                }
            }
        }

        // Amount — always shown
        OutlinedTextField(
            value = row.amount,
            onValueChange = { onChange(row.copy(amount = it)) },
            label = { Text("Amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )

        // Cheque-only fields (mirror ChequePaymentDialog: number, date+picker, bank, account)
        if (row.type == "cheque") {
            OutlinedTextField(
                value = row.chequeNumber,
                onValueChange = { onChange(row.copy(chequeNumber = it)) },
                label = { Text("Cheque Number *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedTextField(
                value = row.chequeDate,
                onValueChange = { },
                label = { Text("Cheque Date") },
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { showDatePicker = true },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select date")
                    }
                }
            )
            OutlinedTextField(
                value = row.bankAndBranch,
                onValueChange = { onChange(row.copy(bankAndBranch = it)) },
                label = { Text("Bank & Branch") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            OutlinedTextField(
                value = row.accountNumber,
                onValueChange = { onChange(row.copy(accountNumber = it)) },
                label = { Text("Account Number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        // Card-only field (mirror CardPaymentDialog: last 4 digits)
        if (row.type == "card") {
            OutlinedTextField(
                value = row.cardLastFourDigits,
                onValueChange = { onChange(row.copy(cardLastFourDigits = it)) },
                label = { Text("Card Last 4 Digits") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        }

        // TRN — shown for every payment type (matches single-payment dialogs)
        OutlinedTextField(
            value = row.trn,
            onValueChange = { onChange(row.copy(trn = it)) },
            label = { Text("TRN (Optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        onChange(row.copy(chequeDate = fmt.format(Date(millis))))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun PaymentTypeChip(value: String, selected: String, onClick: () -> Unit) {
    val isSel = selected == value
    FilterChip(
        selected = isSel,
        onClick = onClick,
        label = { Text(value.replaceFirstChar { it.uppercase() }) },
        modifier = Modifier.padding(end = 6.dp)
    )
}

/**
 * Posts the bill with multiple payment rows in a single request to
 * `/bill/saveBillWithPayment`. Backend (BillFacade) reads the new optional
 * `payments` array and skips the single-`payment` branch when present.
 *
 * Online only. If the device is offline, callers should fall back to the
 * existing single-payment flow.
 *
 * Returns the saved bill number string, or an error string ("Failed: ..." /
 * "Exception: ...") on failure.
 */
suspend fun saveBillWithSplitPayments(
    context: Context,
    token: String,
    items: List<BillItem>,
    customer: String,
    customerId: Long,
    salesRep: String,
    salesRepId: Long,
    discount: Double,
    billType: String,                  // typically "CREDIT"
    billNumber: String,
    payments: List<SplitPaymentRow>,
    orderNo: Int = 0,
    memo: String = ""
): String? {
    return try {
        val billNo = billNumber.toLongOrNull() ?: System.currentTimeMillis() / 1000
        val grossTotal = items.sumOf { it.amount }
        val netAmount = grossTotal - items.sumOf { it.returnAmount } - discount

        val location = LocationHelper.getCurrentLocation(context)

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val currentDate = dateFormatter.format(Date())

        val billItemsArr = JsonArray()
        items.forEach { item ->
            val o = JsonObject()
            o.addProperty("itemCode", item.code)
            o.addProperty("itemName", item.name)
            o.addProperty("invoiceId", item.invoiceId)
            o.addProperty("supplierId", item.supplierId)
            o.addProperty("itemId", item.id)
            o.addProperty("qty", item.quantity.toString())
            o.addProperty("unitPrice", item.unitPrice)
            o.addProperty("discount", item.discount)
            o.addProperty("discountValue", item.discountValue)
            o.addProperty("discountPercentage", item.discountPercentage)
            o.addProperty("totalPrice", item.amount)
            o.addProperty("totalQty", item.totalQty)
            o.addProperty("freeIssueQty", item.freeIssueQty)
            billItemsArr.add(o)
        }

        val userEvent = JsonObject().apply {
            addProperty("event", "Save Credit Bill (split payment) $billNo")
            addProperty("latitude", location.latitude.toString())
            addProperty("longitude", location.longitude.toString())
            addProperty("userName", "")
            addProperty("eventTime", "")
            add("mapLocations", null)
        }

        val billData = JsonObject().apply {
            add("billItems", billItemsArr)
            addProperty("billNo", billNo)
            addProperty("cash", 0)
            addProperty("billType", billType)
            addProperty("salesRefId", salesRepId)
            addProperty("discount", discount)
            addProperty("discountValue", discount)
            addProperty("discountPercentage", if (grossTotal > 0) (discount / grossTotal * 100) else 0.0)
            addProperty("customerId", customerId)
            addProperty("paidAmount", 0)
            addProperty("total", netAmount)
            addProperty("billingDate", currentDate)
            addProperty("id", 0)
            addProperty("customerName", if (customerId <= 0 && customer.isNotEmpty()) customer else "")
            addProperty("salesRefName", "")
            add("userEvent", userEvent)
            addProperty("paymentMethod", "")
            addProperty("paymentDetails", "")
            if (orderNo > 0) addProperty("orderNo", orderNo) else add("orderNo", null)
            addProperty("memo", memo)
        }

        val paymentsArr = JsonArray()
        payments.forEach { row ->
            val amountVal = row.amount.toDoubleOrNull() ?: 0.0
            if (amountVal <= 0.0) return@forEach
            val p = JsonObject().apply {
                addProperty("id", 0)
                addProperty("customerId", customerId)
                addProperty("billId", 0)
                addProperty("paidAmount", amountVal)
                addProperty("type", row.type)
                addProperty(
                    "chequeNumber",
                    if (row.type == "card") row.cardLastFourDigits else row.chequeNumber
                )
                addProperty(
                    "chequeDate",
                    if (row.chequeDate.isNotEmpty()) row.chequeDate else currentDate
                )
                addProperty("bankAndBranch", row.bankAndBranch)
                addProperty("accountNumber", row.accountNumber)
                addProperty("paymentDate", currentDate)
                addProperty("active", "Y")
                addProperty("salesRepId", 0)
                // Cash uses cashTRN field, cheque/card use trn — mirrors single-payment save.
                addProperty("trn", if (row.type == "cash") "" else row.trn)
                addProperty("cashTRN", if (row.type == "cash") row.trn else "")
                addProperty("paymentDateStr", "")
                addProperty(
                    "customerName",
                    if (customerId <= 0 && customer.isNotEmpty()) customer else ""
                )
                addProperty(
                    "chequeStatus",
                    if (row.type == "cheque") "pending" else ""
                )
                addProperty("chequeDateStr", "")
            }
            paymentsArr.add(p)
        }

        val requestData = JsonObject().apply {
            add("bill", billData)
            add("payments", paymentsArr)
        }

        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/saveBillWithPayment")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.doOutput = true

        val jsonString = requestData.toString()
        Log.d("SplitPaymentDialog", "=== SAVE BILL WITH SPLIT PAYMENTS ===")
        Log.d("SplitPaymentDialog", jsonString)
        Log.d("SplitPaymentDialog", "=====================================")

        connection.outputStream.bufferedWriter().use { it.write(jsonString) }

        val responseCode = connection.responseCode
        if (responseCode in 200..299) {
            val body = connection.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            Log.d("SplitPaymentDialog", "Saved. Response: $body")
            // Apply any item-wise returns via the backend's salesReturn flow
            // (returnType honours the damages config, same as Image Bills).
            applyItemReturns(context, token, billNo, billType, items)
            // Parse out bill.billNo to return
            try {
                val parsed = com.google.gson.JsonParser.parseString(body)
                if (parsed.isJsonObject) {
                    val obj = parsed.asJsonObject
                    val billObj = obj.getAsJsonObject("bill")
                    if (billObj != null && billObj.has("billNo")) {
                        return billObj.get("billNo").asString
                    }
                }
            } catch (_: Exception) {
            }
            billNo.toString()
        } else {
            val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
            Log.e("SplitPaymentDialog", "Save failed: $err")
            "Failed: $err"
        }
    } catch (e: Exception) {
        Log.e("SplitPaymentDialog", "Exception: ${e.message}", e)
        "Exception: ${e.message}"
    }
}
