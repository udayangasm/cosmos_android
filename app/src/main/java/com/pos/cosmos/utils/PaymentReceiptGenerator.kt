package com.pos.cosmos.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.pos.cosmos.api.ApiConfig
import com.pos.cosmos.services.PrinterService
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A single label/value row on a payment receipt (e.g. "Amount Paid" / "1500.00").
 */
data class PaymentReceiptLine(
    val label: String,
    val value: String,
    val bold: Boolean = false,
    // Render this row a couple of points larger — used for the headline amount.
    val emphasize: Boolean = false,
    // Render a short dashed divider instead of a label/value row (separates blocks).
    val separator: Boolean = false,
    // Render as a single left-aligned "label value" line instead of a two-column row with the
    // value right-aligned (used for the customer / period / counts header lines).
    val inline: Boolean = false
)

/**
 * Data for a payment receipt. The company-header fields mirror [ReceiptData] and are
 * populated from the same bill template so payment receipts look identical to bill
 * receipts; the body is a simple list of label/value lines instead of an item grid.
 */
data class PaymentReceiptData(
    val title: String,
    val lines: List<PaymentReceiptLine>,
    // Company header (sourced from the bill template)
    val companyName: String = "COSMOS POS",
    val address: String = "",
    val contact: String = "",
    val email: String = "",
    val companyNameFont: Int = 14,
    val addressFont: Int = 12,
    val contactFont: Int = 12,
    val emailFont: Int = 12,
    val lastLineFont: Int = 10,
    val companyGap: Int = 0,
    val addressGap: Int = 0,
    val contactGap: Int = 0,
    val emailGap: Int = 0,
    val lastLineGap: Int = 0,
    val upperGap: Int = 0,
    val leftGap: Int = 0,
    val logoDataUrl: String = "",
    val logoLeft: Int = 0,
    val logoUpper: Int = 0,
    val logoWidth: Int = 0,
    val logoHeight: Int = 0,
    val lastLine: String = "",
    val dateLabel: String = "Date:",
    val titleFontSize: Float = 13f,
    val bodyFontSize: Float = 11f
)

class PaymentReceiptGenerator(private val context: Context, paperWidthMm: Float = 80f) {

    companion object {
        private const val MM_TO_POINTS = 72f / 25.4f
        private const val TAG = "PaymentReceiptGen"
    }

    private val paperWidthPt = paperWidthMm * MM_TO_POINTS
    private val separatorLine: String = "-".repeat((paperWidthMm * 0.7f).toInt())

    fun generate(data: PaymentReceiptData): File {
        val fileName = "payment_receipt_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)

        val writer = PdfWriter(file)
        val pdfDocument = PdfDocument(writer)
        // Generous fixed height so a continuous thermal receipt never spills to page 2.
        val pageHeight = 500f * MM_TO_POINTS
        val pageSize = PageSize(paperWidthPt, pageHeight)
        val document = Document(pdfDocument, pageSize)

        val topMarginPt = data.upperGap.toFloat() * MM_TO_POINTS
        val leftMarginPt = data.leftGap.toFloat() * MM_TO_POINTS
        document.setMargins(topMarginPt, 0f, 0f, leftMarginPt)

        // Width available for the label/value tables.
        val contentWidthPt = (paperWidthPt - leftMarginPt - 2f * MM_TO_POINTS).coerceAtLeast(40f)

        try {
            // Logo (absolute positioning, top-left origin like jsPDF -> bottom-left for iText)
            if (data.logoDataUrl.isNotEmpty()) {
                try {
                    val imageBytes = Base64.decode(data.logoDataUrl, Base64.DEFAULT)
                    val imageData = ImageDataFactory.create(imageBytes)
                    val logoLeftPt = data.logoLeft.toFloat() * MM_TO_POINTS
                    val logoUpperPt = data.logoUpper.toFloat() * MM_TO_POINTS
                    val logoWidthPt = data.logoWidth.toFloat() * MM_TO_POINTS
                    val logoHeightPt = data.logoHeight.toFloat() * MM_TO_POINTS
                    val absoluteY = pageSize.height - logoUpperPt - logoHeightPt
                    val image = Image(imageData)
                        .setFixedPosition(logoLeftPt, absoluteY)
                        .setWidth(logoWidthPt)
                        .setHeight(logoHeightPt)
                    document.add(image)
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding logo: ${e.message}")
                }
            }

            // Company name
            document.add(
                Paragraph(data.companyName)
                    .setFontSize(data.companyNameFont.toFloat())
                    .setBold()
                    .setMargin(0f)
                    .setPadding(0f)
            )
            if (data.companyGap > 0) gap(document, data.companyGap)

            if (data.address.isNotEmpty()) {
                document.add(Paragraph(data.address).setFontSize(data.addressFont.toFloat()).setMargin(0f).setPadding(0f))
                if (data.addressGap > 0) gap(document, data.addressGap)
            }
            if (data.contact.isNotEmpty()) {
                document.add(Paragraph(data.contact).setFontSize(data.contactFont.toFloat()).setMargin(0f).setPadding(0f))
                if (data.contactGap > 0) gap(document, data.contactGap)
            }
            if (data.email.isNotEmpty()) {
                document.add(Paragraph(data.email).setFontSize(data.emailFont.toFloat()).setMargin(0f).setPadding(0f))
                if (data.emailGap > 0) gap(document, data.emailGap)
            }

            gap(document, 2)
            document.add(Paragraph(separatorLine).setFontSize(data.bodyFontSize).setMargin(0f).setPadding(0f))

            // Title (e.g. "CUSTOMER PAYMENT RECEIPT")
            document.add(
                Paragraph(data.title)
                    .setFontSize(data.titleFontSize)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setWidth(UnitValue.createPointValue(contentWidthPt))
                    .setMargin(0f)
                    .setPadding(0f)
            )
            document.add(Paragraph(separatorLine).setFontSize(data.bodyFontSize).setMargin(0f).setPadding(0f))
            gap(document, 2)

            // Date
            val dateTime = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()).format(Date())
            document.add(
                Paragraph("${data.dateLabel} $dateTime")
                    .setFontSize(data.bodyFontSize)
                    .setMargin(0f)
                    .setPadding(0f)
            )
            gap(document, 2)

            // Body lines as right-aligned value rows
            data.lines.forEach { line ->
                if (line.separator) {
                    document.add(Paragraph(separatorLine).setFontSize(data.bodyFontSize).setMargin(0f).setPadding(0f))
                    gap(document, 2)
                    return@forEach
                }
                val fontSize = if (line.emphasize) data.bodyFontSize + 2f else data.bodyFontSize
                val table = Table(floatArrayOf(1f, 1f))
                table.setWidth(UnitValue.createPointValue(contentWidthPt))
                table.setMargin(0f)
                table.setPadding(0f)
                val labelP = Paragraph(line.label).setFontSize(fontSize).setMargin(0f).setPadding(0f)
                val valueP = Paragraph(line.value).setFontSize(fontSize).setTextAlignment(TextAlignment.RIGHT).setMargin(0f).setPadding(0f)
                if (line.bold || line.emphasize) { labelP.setBold(); valueP.setBold() }
                table.addCell(Cell().add(labelP).setBorder(null).setPadding(0f))
                table.addCell(Cell().add(valueP).setBorder(null).setPadding(0f))
                document.add(table)
                gap(document, 2)
            }

            document.add(Paragraph(separatorLine).setFontSize(data.bodyFontSize).setMargin(0f).setPadding(0f))

            if (data.lastLineGap > 0) gap(document, data.lastLineGap)
            if (data.lastLine.isNotEmpty()) {
                document.add(Paragraph(data.lastLine).setFontSize(data.lastLineFont.toFloat()).setMargin(0f).setPadding(0f))
            }
        } finally {
            document.close()
        }

        return file
    }

    private fun gap(document: Document, mm: Int) {
        document.add(Paragraph().setHeight(mm.toFloat() * MM_TO_POINTS).setMargin(0f))
    }
}

/**
 * Map a bill template's company-header fields onto [PaymentReceiptData]. Falls back to
 * sensible defaults when the template is null (e.g. offline with no cached template),
 * so a receipt is still produced.
 */
fun buildPaymentReceiptData(
    template: JsonObject?,
    title: String,
    lines: List<PaymentReceiptLine>
): PaymentReceiptData {
    if (template == null) {
        return PaymentReceiptData(title = title, lines = lines)
    }
    fun str(key: String, default: String = "") = template.get(key)?.asString ?: default
    fun int(key: String, default: Int) = try { template.get(key)?.asInt ?: default } catch (e: Exception) { default }
    return PaymentReceiptData(
        title = title,
        lines = lines,
        companyName = str("companyName", "COSMOS POS"),
        address = str("address"),
        contact = str("contact"),
        email = str("email"),
        companyNameFont = int("companyFont", 14),
        addressFont = int("addressFont", 12),
        contactFont = int("contactFont", 12),
        emailFont = int("emailFont", 12),
        lastLineFont = int("lastLineFont", 10),
        companyGap = int("companyGap", 0),
        addressGap = int("addressGap", 0),
        contactGap = int("contactGap", 0),
        emailGap = int("emailGap", 0),
        lastLineGap = int("lastLineGap", 0),
        upperGap = int("upperGap", 0),
        leftGap = int("leftGap", 0),
        logoDataUrl = str("logoDataURL"),
        logoLeft = int("logoLeft", 0),
        logoUpper = int("logoUpper", 0),
        logoWidth = int("logoWidth", 0),
        logoHeight = int("logoHeight", 0),
        lastLine = str("lastLine"),
        dateLabel = str("dateLabel", "Date:")
    )
}

/**
 * Load the bill template used for the company header on payment receipts. Tries the
 * network first (and refreshes the cache), then falls back to the cached template.
 * Mirrors the loader used by the bill screens so the same header/logo are reused.
 * Call from a background (IO) context.
 */
fun loadBillTemplateForPaymentReceipt(context: Context, token: String, use58mm: Boolean): JsonObject? {
    val billType = if (use58mm) "android58" else "android"
    val fresh = fetchBillTemplate(token, billType)
    if (fresh != null) {
        BillTemplateCache.saveTemplate(context, fresh, billType)
        return fresh
    }
    return BillTemplateCache.getTemplate(context, billType)
}

/**
 * Generate a payment receipt PDF, print it to a connected thermal printer (best-effort),
 * and return the file together with the (possibly freshly-loaded) bill template so the
 * caller can cache it. Returns a null file when there is nothing to show — i.e. preview
 * and share are both off and no printer is connected (in which case generation is skipped
 * entirely). The returned file is non-null only when preview or share is enabled.
 *
 * Run on a background (IO) dispatcher — it does network + PDF work.
 */
fun producePaymentReceipt(
    context: Context,
    token: String,
    template: JsonObject?,
    use58mm: Boolean,
    showPreview: Boolean,
    enableShare: Boolean,
    title: String,
    lines: List<PaymentReceiptLine>
): Pair<File?, JsonObject?> {
    val printerService = PrinterService.getInstance()
    val connected = printerService.isConnected()
    // No output channel wants the receipt — don't spend time generating it.
    if (!showPreview && !enableShare && !connected) return Pair(null, template)

    val tmpl = template ?: loadBillTemplateForPaymentReceipt(context, token, use58mm)
    val paperWidthMm = if (use58mm) 58f else 80f
    val data = buildPaymentReceiptData(tmpl, title, lines)
    val pdf = try {
        PaymentReceiptGenerator(context, paperWidthMm).generate(data)
    } catch (e: Exception) {
        Log.e("PaymentReceiptGen", "generate failed: ${e.message}", e)
        return Pair(null, tmpl)
    }

    // Best-effort thermal print; never blocks preview/share.
    printerService.printPdfFile(pdf)

    return Pair(if (showPreview || enableShare) pdf else null, tmpl)
}

/**
 * Generate the grouped "Payments by bill" receipt (bill items rendered exactly like the small
 * bill template, followed by the payments against each bill), print it (best-effort), and return
 * the file for preview/share. Same gating as [producePaymentReceipt]: when preview and share are
 * both off and no printer is connected, generation is skipped. Run on a background dispatcher.
 */
fun producePaymentsByBillReceipt(
    context: Context,
    use58mm: Boolean,
    showPreview: Boolean,
    enableShare: Boolean,
    title: String,
    summaryLines: List<PaymentReceiptLine>,
    sections: List<com.pos.cosmos.utils.PaymentBillBlock>,
    grandTotalLines: List<PaymentReceiptLine>
): File? {
    val printerService = PrinterService.getInstance()
    val connected = printerService.isConnected()
    if (!showPreview && !enableShare && !connected) return null
    if (sections.isEmpty()) return null

    val paperWidthMm = if (use58mm) 58f else 80f
    val pdf = try {
        PdfReceiptGenerator(context, paperWidthMm).generatePaymentsByBillPdf(title, summaryLines, sections, grandTotalLines)
    } catch (e: Exception) {
        Log.e("PaymentReceiptGen", "payments-by-bill generate failed: ${e.message}", e)
        return null
    }

    printerService.printPdfFile(pdf)
    return if (showPreview || enableShare) pdf else null
}

private fun fetchBillTemplate(token: String, targetBillType: String): JsonObject? {
    return try {
        val connection = URL("${ApiConfig.BASE_URL_8080}/bill/bill-templates")
            .openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Celonis-Auth", "totally_secret")
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        if (connection.responseCode == 200) {
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            val allTemplates = Gson().fromJson(content, com.google.gson.JsonArray::class.java)
            var result: JsonObject? = null
            if (allTemplates != null && allTemplates.size() > 0) {
                for (i in 0 until allTemplates.size()) {
                    val t = allTemplates.get(i).asJsonObject
                    if (t.get("billType")?.asString == targetBillType) { result = t; break }
                }
                if (result == null && targetBillType != "android") {
                    for (i in 0 until allTemplates.size()) {
                        val t = allTemplates.get(i).asJsonObject
                        if (t.get("billType")?.asString == "android") { result = t; break }
                    }
                }
                if (result == null) {
                    for (i in 0 until allTemplates.size()) {
                        val t = allTemplates.get(i).asJsonObject
                        if (t.get("billType")?.asString == "small") { result = t; break }
                    }
                }
            }
            result
        } else null
    } catch (e: Exception) {
        Log.e("PaymentReceiptGen", "fetchBillTemplate error: ${e.message}")
        null
    }
}
