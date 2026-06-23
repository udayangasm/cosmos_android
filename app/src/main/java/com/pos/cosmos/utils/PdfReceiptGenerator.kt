package com.pos.cosmos.utils

import android.content.Context
import android.graphics.Color as AndroidColor
import android.util.Base64
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.kernel.colors.Color
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.geom.PageSize
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.io.ByteArrayOutputStream

data class ReceiptItem(
    val name: String,
    val quantity: Int,
    val price: Double,
    val amount: Double,
    val discountValue: Double = 0.0
)

data class ReceiptData(
    val items: List<ReceiptItem>,
    val returnItems: List<ReceiptItem> = emptyList(),
    val totalReturn: Double = 0.0,
    val customer: String = "Walk-in",
    val salesRep: String = "N/A",
    val grossTotal: Double,
    val discount: Double,
    val netTotal: Double,
    val cashPaid: Double = 0.0,
    val billNo: String = "",
    val companyName: String = "COSMOS POS",
    val address: String = "",
    val contact: String = "",
    val email: String = "",
    // Font sizes
    val companyNameFont: Int = 14,
    val addressFont: Int = 8,
    val contactFont: Int = 8,
    val emailFont: Int = 8,
    val lastLineFont: Int = 10,
    // Spacing/gaps
    val companyGap: Int = 0,
    val addressGap: Int = 0,
    val contactGap: Int = 0,
    val emailGap: Int = 0,
    val lastLineGap: Int = 0,
    val upperGap: Int = 0,
    val leftGap: Int = 0,
    // Logo fields
    val logoDataUrl: String = "",
    val logoLeft: Int = 0,
    val logoUpper: Int = 0,
    val logoWidth: Int = 0,
    val logoHeight: Int = 0,
    // Last line
    val lastLine: String = "",
    // Bill detail labels
    val billNoLabel: String = "Bill No :",
    val dateLabel: String = "Date:",
    val itemHeader: String = "Item",
    val priceHeader: String = "Price",
    val qtyHeader: String = "Qty",
    val amountHeader: String = "Amount",
    val grossTotalLabel: String = "Gross Total:",
    val discountLabel: String = "Discount:",
    val netTotalLabel: String = "Net Total:",
    val cashLabel: String = "Cash:",
    val balanceLabel: String = "Balance:",
    val outstandingLabel: String = "Outstanding:",
    // Split payment breakdown (label -> amount). When non-empty, each entry is
    // printed instead of the single "Cash:" line.
    val paymentBreakdown: List<Pair<String, Double>> = emptyList(),
    // Bill detail font sizes
    val billNoFontSize: Float = 11f,
    val dateFontSize: Float = 11f,
    val headerFontSize: Float = 11f,
    val itemFontSize: Float = 10f,
    val grossTotalFontSize: Float = 11f,
    val discountFontSize: Float = 11f,
    val netTotalFontSize: Float = 13f,
    val cashFontSize: Float = 11f,
    val balanceFontSize: Float = 11f,
    // Bill detail gaps (in mm, will be converted to points)
    val billNoGap: Float = 5f,
    val dateGap: Float = 5f,
    val separatorGap: Float = 5f,
    val headerGap: Float = 5f,
    val itemNameGap: Float = 5f,
    val itemGap: Float = 5f,
    val grossTotalGap: Float = 5f,
    val discountGap: Float = 6f,
    val netTotalGap: Float = 5f,
    val cashGapMm: Float = 5f,
    val balanceGap: Float = 5f,
    // Column positions for item details (mm from left)
    val colPriceMm: Float = 30f,
    val colQtyMm: Float = 50f,
    val colAmountMm: Float = 70f,
    // Column positions for headers (mm from left, defaults to item positions)
    val colHeaderPriceMm: Float = 30f,
    val colHeaderQtyMm: Float = 50f,
    val colHeaderAmountMm: Float = 70f,
    // Flag to indicate if this is an order (not a bill)
    val isOrder: Boolean = false,
    // Per-line item discount column on small bills
    val showLineDiscount: Boolean = false,
    val totalItemDiscount: Double = 0.0,
    val discountColHeader: String = "Dis.",
    val totalItemDiscountLabel: String = "Total item discount:"
)

class PdfReceiptGenerator(private val context: Context, paperWidthMm: Float = 80f) {

    companion object {
        // Convert mm to points (1 inch = 25.4mm, 1 inch = 72 points)
        private const val MM_TO_POINTS = 72f / 25.4f
    }

    private val PAPER_WIDTH = paperWidthMm * MM_TO_POINTS

    // Generate separator line that fits the paper width
    // At ~5.5 points per dash character at 11f font size
    private val separatorLine: String = "-".repeat((paperWidthMm * 0.7f).toInt())

    fun generateReceiptPdf(receiptData: ReceiptData): File {
        android.util.Log.d("PdfReceipt", "=== RECEIPT DATA ===")
        android.util.Log.d("PdfReceipt", "companyName: '${receiptData.companyName}'")
        android.util.Log.d("PdfReceipt", "companyNameFont: ${receiptData.companyNameFont}")
        android.util.Log.d("PdfReceipt", "address: '${receiptData.address}'")
        android.util.Log.d("PdfReceipt", "contact: '${receiptData.contact}'")
        android.util.Log.d("PdfReceipt", "email: '${receiptData.email}'")
        android.util.Log.d("PdfReceipt", "lastLine: '${receiptData.lastLine}'")
        android.util.Log.d("PdfReceipt", "upperGap: ${receiptData.upperGap}, leftGap: ${receiptData.leftGap}")
        android.util.Log.d("PdfReceipt", "logoDataUrl length: ${receiptData.logoDataUrl.length}")
        android.util.Log.d("PdfReceipt", "logoLeft: ${receiptData.logoLeft}, logoUpper: ${receiptData.logoUpper}")
        android.util.Log.d("PdfReceipt", "logoWidth: ${receiptData.logoWidth}, logoHeight: ${receiptData.logoHeight}")
        android.util.Log.d("PdfReceipt", "PAPER_WIDTH: $PAPER_WIDTH, MM_TO_POINTS: $MM_TO_POINTS")
        android.util.Log.d("PdfReceipt", "colPrice: ${receiptData.colPriceMm}, colQty: ${receiptData.colQtyMm}, colAmount: ${receiptData.colAmountMm}")
        android.util.Log.d("PdfReceipt", "colHeaderPrice: ${receiptData.colHeaderPriceMm}, colHeaderQty: ${receiptData.colHeaderQtyMm}, colHeaderAmount: ${receiptData.colHeaderAmountMm}")

        val fileName = "receipt_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)

        val writer = PdfWriter(file)
        val pdfDocument = PdfDocument(writer)

        // Use a generous fixed page height for thermal receipt
        // Thermal paper is continuous so extra white space just feeds more paper
        // This prevents content overflow to page 2 (printer only renders page 1)
        // 500mm = ~1417 points, enough for any receipt
        val calculatedHeight = 500f * MM_TO_POINTS
        android.util.Log.d("PdfReceipt", "pageHeight: $calculatedHeight, PAPER_WIDTH: $PAPER_WIDTH")
        val pageSize = PageSize(PAPER_WIDTH, calculatedHeight)
        val document = Document(pdfDocument, pageSize)

        // Set margins - convert mm to points to match jsPDF behavior
        // jsPDF uses mm for upperGap and leftGap, so we must convert
        val topMarginPt = receiptData.upperGap.toFloat() * MM_TO_POINTS
        val leftMarginPt = receiptData.leftGap.toFloat() * MM_TO_POINTS
        document.setMargins(
            topMarginPt,  // top (mm -> points)
            0f,  // right (no margin)
            0f,  // bottom (no margin - no extra space!)
            leftMarginPt  // left (mm -> points)
        )

        try {
            // Add logo using absolute positioning if available
            // jsPDF addImage(img, 'JPEG', logoLeft, logoUpper, logoWidth, logoHeight)
            // jsPDF uses mm with origin at top-left - same as the template values
            // iText uses points with origin at bottom-left, so we convert mm->points
            // and flip Y: absoluteY = pageHeight - (logoUpper * mmToPt) - (logoHeight * mmToPt)
            if (receiptData.logoDataUrl.isNotEmpty()) {
                try {
                    val imageBytes = Base64.decode(receiptData.logoDataUrl, Base64.DEFAULT)
                    val imageData = com.itextpdf.io.image.ImageDataFactory.create(imageBytes)

                    val logoLeftPt = receiptData.logoLeft.toFloat() * MM_TO_POINTS
                    val logoUpperPt = receiptData.logoUpper.toFloat() * MM_TO_POINTS
                    val logoWidthPt = receiptData.logoWidth.toFloat() * MM_TO_POINTS
                    val logoHeightPt = receiptData.logoHeight.toFloat() * MM_TO_POINTS

                    // Convert from top-left origin (jsPDF) to bottom-left origin (iText)
                    val absoluteX = logoLeftPt
                    val absoluteY = pageSize.height - logoUpperPt - logoHeightPt

                    val image = Image(imageData)
                        .setFixedPosition(absoluteX, absoluteY)
                        .setWidth(logoWidthPt)
                        .setHeight(logoHeightPt)
                    document.add(image)
                } catch (e: Exception) {
                    android.util.Log.e("PdfReceiptGenerator", "Error adding logo: ${e.message}")
                }
            }

            // Company Name
            val companyName = Paragraph(receiptData.companyName)
                .setFontSize(receiptData.companyNameFont.toFloat())
                .setBold()
                .setMargin(0f)
                .setPadding(0f)
            document.add(companyName)

            // Company gap (use as-is, already in correct units)
            if (receiptData.companyGap > 0) {
                document.add(Paragraph().setHeight(receiptData.companyGap.toFloat() * MM_TO_POINTS).setMargin(0f))
            }

            // Address
            if (receiptData.address.isNotEmpty()) {
                val address = Paragraph(receiptData.address)
                    .setFontSize(receiptData.addressFont.toFloat())
                    .setMargin(0f)
                    .setPadding(0f)
                document.add(address)

                if (receiptData.addressGap > 0) {
                    document.add(Paragraph().setHeight(receiptData.addressGap.toFloat() * MM_TO_POINTS).setMargin(0f))
                }
            }

            // Contact
            if (receiptData.contact.isNotEmpty()) {
                val contact = Paragraph(receiptData.contact)
                    .setFontSize(receiptData.contactFont.toFloat())
                    .setMargin(0f)
                    .setPadding(0f)
                document.add(contact)

                if (receiptData.contactGap > 0) {
                    document.add(Paragraph().setHeight(receiptData.contactGap.toFloat() * MM_TO_POINTS).setMargin(0f))
                }
            }

            // Email
            if (receiptData.email.isNotEmpty()) {
                val email = Paragraph(receiptData.email)
                    .setFontSize(receiptData.emailFont.toFloat())
                    .setMargin(0f)
                    .setPadding(0f)
                document.add(email)

                if (receiptData.emailGap > 0) {
                    document.add(Paragraph().setHeight(receiptData.emailGap.toFloat() * MM_TO_POINTS).setMargin(0f))
                }
            }

            // Add bill details and items
            addBillContent(document, receiptData)

            // Last line gap (use as-is, already in correct units)
            if (receiptData.lastLineGap > 0) {
                document.add(Paragraph().setHeight(receiptData.lastLineGap.toFloat() * MM_TO_POINTS).setMargin(0f))
            }

            if (receiptData.lastLine.isNotEmpty()) {
                val lastLine = Paragraph(receiptData.lastLine)
                    .setFontSize(receiptData.lastLineFont.toFloat())
                    .setMargin(0f)
                    .setPadding(0f)
                document.add(lastLine)
            }

        } finally {
            document.close()
        }

        return file
    }

    private fun addBillContent(document: Document, data: ReceiptData) {
        val dateTime = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()).format(Date())
        val leftMarginPt = data.leftGap.toFloat() * MM_TO_POINTS

        // Bill No
        val billNoText = if (data.isOrder) {
            if (data.billNo.isNotEmpty()) "Order No: ${data.billNo}" else "Order No: "
        } else {
            if (data.billNo.isNotEmpty()) "${data.billNoLabel} ${data.billNo}" else "${data.billNoLabel} "
        }
        document.add(Paragraph(billNoText)
            .setFontSize(data.billNoFontSize)
            .setMargin(0f)
            .setPadding(0f))
        if (data.billNoGap > 0) {
            document.add(Paragraph().setHeight(data.billNoGap * MM_TO_POINTS).setMargin(0f))
        }

        // Customer (only show if customer is selected)
        if (data.customer.isNotEmpty() && data.customer != "CASH" && data.customer != "Walk-in") {
            document.add(Paragraph("Customer: ${data.customer}")
                .setFontSize(data.billNoFontSize)
                .setMargin(0f)
                .setPadding(0f))
        }

        // Date
        document.add(Paragraph("${data.dateLabel} $dateTime")
            .setFontSize(data.dateFontSize)
            .setMargin(0f)
            .setPadding(0f))
        if (data.dateGap > 0) {
            document.add(Paragraph().setHeight(data.dateGap * MM_TO_POINTS).setMargin(0f))
        }

        // Separator
        document.add(Paragraph(separatorLine)
            .setFontSize(data.headerFontSize)
            .setMargin(0f)
            .setPadding(0f))
        if (data.separatorGap > 0) {
            document.add(Paragraph().setHeight(data.separatorGap * MM_TO_POINTS).setMargin(0f))
        }

        // Column positions are RIGHT-EDGE positions in mm (matching jsPDF behavior)
        // e.g., colPrice=30 means Price text right-aligns at 30mm from left margin
        val colPricePt = data.colPriceMm * MM_TO_POINTS
        val colQtyPt = data.colQtyMm * MM_TO_POINTS
        val colAmountPt = data.colAmountMm * MM_TO_POINTS
        val colHeaderPricePt = data.colHeaderPriceMm * MM_TO_POINTS
        val colHeaderQtyPt = data.colHeaderQtyMm * MM_TO_POINTS
        val colHeaderAmountPt = data.colHeaderAmountMm * MM_TO_POINTS

        // Header table layout - with or without an extra Dis. column.
        // When showLineDiscount is on, an extra column sits at the midpoint between Price and Qty.
        val colHeaderDiscountPt = (colHeaderPricePt + colHeaderQtyPt) / 2f
        val headerTable: Table
        if (data.showLineDiscount) {
            val hItemW = (colHeaderPricePt * 0.5f).coerceAtLeast(10f)
            val hPriceW = (colHeaderPricePt * 0.5f).coerceAtLeast(10f)
            val hDiscW = (colHeaderDiscountPt - colHeaderPricePt).coerceAtLeast(10f)
            val hQtyW = (colHeaderQtyPt - colHeaderDiscountPt).coerceAtLeast(10f)
            val hAmountW = (colHeaderAmountPt - colHeaderQtyPt).coerceAtLeast(10f)
            headerTable = Table(floatArrayOf(hItemW, hPriceW, hDiscW, hQtyW, hAmountW))
            headerTable.setWidth(UnitValue.createPointValue(colHeaderAmountPt))
            headerTable.setMargin(0f)
            headerTable.setPadding(0f)
            headerTable.addCell(Cell().add(Paragraph(data.itemHeader).setFontSize(data.headerFontSize).setBold()).setBorder(null).setPadding(0f))
            headerTable.addCell(Cell().add(Paragraph(data.priceHeader).setFontSize(data.headerFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            headerTable.addCell(Cell().add(Paragraph(data.discountColHeader).setFontSize(data.headerFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            headerTable.addCell(Cell().add(Paragraph(data.qtyHeader).setFontSize(data.headerFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            headerTable.addCell(Cell().add(Paragraph(data.amountHeader).setFontSize(data.headerFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
        } else {
            val hItemW = (colHeaderPricePt * 0.5f).coerceAtLeast(10f)
            val hPriceW = (colHeaderPricePt * 0.5f).coerceAtLeast(10f)
            val hQtyW = (colHeaderQtyPt - colHeaderPricePt).coerceAtLeast(10f)
            val hAmountW = (colHeaderAmountPt - colHeaderQtyPt).coerceAtLeast(10f)
            headerTable = Table(floatArrayOf(hItemW, hPriceW, hQtyW, hAmountW))
            headerTable.setWidth(UnitValue.createPointValue(colHeaderAmountPt))
            headerTable.setMargin(0f)
            headerTable.setPadding(0f)
            headerTable.addCell(Cell().add(Paragraph(data.itemHeader).setFontSize(data.headerFontSize).setBold()).setBorder(null).setPadding(0f))
            headerTable.addCell(Cell().add(Paragraph(data.priceHeader).setFontSize(data.headerFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            headerTable.addCell(Cell().add(Paragraph(data.qtyHeader).setFontSize(data.headerFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            headerTable.addCell(Cell().add(Paragraph(data.amountHeader).setFontSize(data.headerFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
        }
        document.add(headerTable)
        if (data.headerGap > 0) {
            document.add(Paragraph().setHeight(data.headerGap * MM_TO_POINTS).setMargin(0f))
        }

        // Separator after header
        document.add(Paragraph(separatorLine)
            .setFontSize(data.headerFontSize)
            .setMargin(0f)
            .setPadding(0f))
        if (data.separatorGap > 0) {
            document.add(Paragraph().setHeight(data.separatorGap * MM_TO_POINTS).setMargin(0f))
        }

        // Item detail column widths - 3 or 4 columns, total width = colAmountPt
        // Price right-aligns at colPrice, [Dis. at midpoint], Qty at colQty, Amount at colAmount
        val colDiscountPt = (colPricePt + colQtyPt) / 2f
        val dPriceW = colPricePt.coerceAtLeast(10f)
        val dDiscountW = (colDiscountPt - colPricePt).coerceAtLeast(10f)
        val dQtyW = (colQtyPt - colPricePt).coerceAtLeast(10f)
        val dQtyWithDiscW = (colQtyPt - colDiscountPt).coerceAtLeast(10f)
        val dAmountW = (colAmountPt - colQtyPt).coerceAtLeast(10f)

        // Items
        data.items.forEach { item ->
            // Item name
            document.add(Paragraph(item.name)
                .setFontSize(data.itemFontSize)
                .setFixedLeading(data.itemFontSize)
                .setMargin(0f)
                .setPadding(0f))
            if (data.itemNameGap > 0) {
                document.add(Paragraph().setHeight(data.itemNameGap * MM_TO_POINTS).setFixedLeading(0f).setMargin(0f).setPadding(0f))
            }

            // Price, [Dis.], Qty, Amount - right-aligned at their column positions
            val detailsTable = if (data.showLineDiscount) {
                Table(floatArrayOf(dPriceW, dDiscountW, dQtyWithDiscW, dAmountW))
            } else {
                Table(floatArrayOf(dPriceW, dQtyW, dAmountW))
            }
            detailsTable.setWidth(UnitValue.createPointValue(colAmountPt))
            detailsTable.setMargin(0f)
            detailsTable.setPadding(0f)

            detailsTable.addCell(Cell().add(
                Paragraph(String.format("%.2f", item.price))
                    .setFontSize(data.itemFontSize)
                    .setFixedLeading(data.itemFontSize)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setMargin(0f).setPadding(0f)
            ).setBorder(null).setPadding(0f).setMargin(0f))

            if (data.showLineDiscount) {
                detailsTable.addCell(Cell().add(
                    Paragraph(String.format("%.2f", item.discountValue))
                        .setFontSize(data.itemFontSize)
                        .setFixedLeading(data.itemFontSize)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setMargin(0f).setPadding(0f)
                ).setBorder(null).setPadding(0f).setMargin(0f))
            }

            detailsTable.addCell(Cell().add(
                Paragraph("x${item.quantity}")
                    .setFontSize(data.itemFontSize)
                    .setFixedLeading(data.itemFontSize)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setMargin(0f).setPadding(0f)
            ).setBorder(null).setPadding(0f).setMargin(0f))

            detailsTable.addCell(Cell().add(
                Paragraph(String.format("%.2f", item.amount))
                    .setFontSize(data.itemFontSize)
                    .setFixedLeading(data.itemFontSize)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setMargin(0f).setPadding(0f)
            ).setBorder(null).setPadding(0f).setMargin(0f))

            document.add(detailsTable)
            if (data.itemGap > 0) {
                document.add(Paragraph().setHeight(data.itemGap * MM_TO_POINTS).setFixedLeading(0f).setMargin(0f).setPadding(0f))
            }
        }

        // Return items section
        if (data.returnItems.isNotEmpty()) {
            document.add(Paragraph(separatorLine)
                .setFontSize(data.headerFontSize)
                .setMargin(0f)
                .setPadding(0f))
            document.add(Paragraph("Returns")
                .setFontSize(data.headerFontSize)
                .setBold()
                .setMargin(0f)
                .setPadding(0f))
            if (data.itemGap > 0) {
                document.add(Paragraph().setHeight(data.itemGap * MM_TO_POINTS).setFixedLeading(0f).setMargin(0f).setPadding(0f))
            }
            data.returnItems.forEach { item ->
                document.add(Paragraph(item.name)
                    .setFontSize(data.itemFontSize)
                    .setFixedLeading(data.itemFontSize)
                    .setMargin(0f)
                    .setPadding(0f))
                if (data.itemNameGap > 0) {
                    document.add(Paragraph().setHeight(data.itemNameGap * MM_TO_POINTS).setFixedLeading(0f).setMargin(0f).setPadding(0f))
                }
                val retTable = Table(floatArrayOf(dPriceW, dQtyW, dAmountW))
                retTable.setWidth(UnitValue.createPointValue(colAmountPt))
                retTable.setMargin(0f)
                retTable.setPadding(0f)
                retTable.addCell(Cell().add(
                    Paragraph(String.format("%.2f", item.price))
                        .setFontSize(data.itemFontSize).setFixedLeading(data.itemFontSize)
                        .setTextAlignment(TextAlignment.RIGHT).setMargin(0f).setPadding(0f)
                ).setBorder(null).setPadding(0f).setMargin(0f))
                retTable.addCell(Cell().add(
                    Paragraph("x${item.quantity}")
                        .setFontSize(data.itemFontSize).setFixedLeading(data.itemFontSize)
                        .setTextAlignment(TextAlignment.RIGHT).setMargin(0f).setPadding(0f)
                ).setBorder(null).setPadding(0f).setMargin(0f))
                retTable.addCell(Cell().add(
                    Paragraph("-${String.format("%.2f", item.amount)}")
                        .setFontSize(data.itemFontSize).setFixedLeading(data.itemFontSize)
                        .setTextAlignment(TextAlignment.RIGHT).setMargin(0f).setPadding(0f)
                ).setBorder(null).setPadding(0f).setMargin(0f))
                document.add(retTable)
                if (data.itemGap > 0) {
                    document.add(Paragraph().setHeight(data.itemGap * MM_TO_POINTS).setFixedLeading(0f).setMargin(0f).setPadding(0f))
                }
            }
        }

        // Totals separator
        document.add(Paragraph(separatorLine)
            .setFontSize(data.headerFontSize)
            .setMargin(0f)
            .setPadding(0f))
        if (data.separatorGap > 0) {
            document.add(Paragraph().setHeight(data.separatorGap * MM_TO_POINTS).setMargin(0f))
        }

        // Totals - label left-aligned, value right-aligned at colAmount position
        // 2 columns, total width = colAmountPt
        val totalsLabelW = (colAmountPt - dAmountW).coerceAtLeast(10f)
        val totalsValueW = dAmountW
        val totalsWidths = floatArrayOf(totalsLabelW, totalsValueW)

        // Total item discount (only when line-discount mode is on and there's a discount)
        if (data.showLineDiscount && data.totalItemDiscount > 0) {
            val tidTable = Table(totalsWidths)
            tidTable.setWidth(UnitValue.createPointValue(colAmountPt))
            tidTable.setMargin(0f)
            tidTable.addCell(Cell().add(Paragraph(data.totalItemDiscountLabel).setFontSize(data.grossTotalFontSize).setBold()).setBorder(null).setPadding(0f))
            tidTable.addCell(Cell().add(Paragraph(String.format("%.2f", data.totalItemDiscount)).setFontSize(data.grossTotalFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            document.add(tidTable)
            if (data.grossTotalGap > 0) {
                document.add(Paragraph().setHeight(data.grossTotalGap * MM_TO_POINTS).setMargin(0f))
            }
            // Separator after Total item discount, mirroring the web small bill
            document.add(Paragraph(separatorLine)
                .setFontSize(data.headerFontSize)
                .setMargin(0f)
                .setPadding(0f))
            if (data.separatorGap > 0) {
                document.add(Paragraph().setHeight(data.separatorGap * MM_TO_POINTS).setMargin(0f))
            }
        }

        // Gross Total
        val gtTable = Table(totalsWidths)
        gtTable.setWidth(UnitValue.createPointValue(colAmountPt))
        gtTable.setMargin(0f)
        gtTable.addCell(Cell().add(Paragraph(data.grossTotalLabel).setFontSize(data.grossTotalFontSize)).setBorder(null).setPadding(0f))
        gtTable.addCell(Cell().add(Paragraph(String.format("%.2f", data.grossTotal)).setFontSize(data.grossTotalFontSize).setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
        document.add(gtTable)
        if (data.grossTotalGap > 0) {
            document.add(Paragraph().setHeight(data.grossTotalGap * MM_TO_POINTS).setMargin(0f))
        }

        // Total Return
        if (data.totalReturn > 0) {
            val retTotalTable = Table(totalsWidths)
            retTotalTable.setWidth(UnitValue.createPointValue(colAmountPt))
            retTotalTable.setMargin(0f)
            retTotalTable.addCell(Cell().add(Paragraph("Total Return:").setFontSize(data.grossTotalFontSize)).setBorder(null).setPadding(0f))
            retTotalTable.addCell(Cell().add(Paragraph("-${String.format("%.2f", data.totalReturn)}").setFontSize(data.grossTotalFontSize).setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            document.add(retTotalTable)
            if (data.grossTotalGap > 0) {
                document.add(Paragraph().setHeight(data.grossTotalGap * MM_TO_POINTS).setMargin(0f))
            }
        }

        // Discount
        if (data.discount > 0) {
            val discTable = Table(totalsWidths)
            discTable.setWidth(UnitValue.createPointValue(colAmountPt))
            discTable.setMargin(0f)
            discTable.addCell(Cell().add(Paragraph(data.discountLabel).setFontSize(data.discountFontSize).setBold()).setBorder(null).setPadding(0f))
            discTable.addCell(Cell().add(Paragraph(String.format("%.2f", data.discount)).setFontSize(data.discountFontSize).setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            document.add(discTable)
            if (data.discountGap > 0) {
                document.add(Paragraph().setHeight(data.discountGap * MM_TO_POINTS).setMargin(0f))
            }
        }

        // Net Total
        val ntTable = Table(totalsWidths)
        ntTable.setWidth(UnitValue.createPointValue(colAmountPt))
        ntTable.setMargin(0f)
        ntTable.addCell(Cell().add(Paragraph(data.netTotalLabel).setFontSize(data.netTotalFontSize).setBold()).setBorder(null).setPadding(0f))
        ntTable.addCell(Cell().add(Paragraph(String.format("%.2f", data.netTotal)).setFontSize(data.netTotalFontSize).setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
        document.add(ntTable)
        if (data.netTotalGap > 0) {
            document.add(Paragraph().setHeight(data.netTotalGap * MM_TO_POINTS).setMargin(0f))
        }

        // Payments — split breakdown (Cash / Cheque / Card …) when present,
        // otherwise the single cash line.
        if (data.paymentBreakdown.isNotEmpty()) {
            for ((label, amt) in data.paymentBreakdown) {
                if (amt <= 0) continue
                val payTable = Table(totalsWidths)
                payTable.setWidth(UnitValue.createPointValue(colAmountPt))
                payTable.setMargin(0f)
                payTable.addCell(Cell().add(Paragraph(label).setFontSize(data.cashFontSize)).setBorder(null).setPadding(0f))
                payTable.addCell(Cell().add(Paragraph(String.format("%.2f", amt)).setFontSize(data.cashFontSize).setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
                document.add(payTable)
                if (data.cashGapMm > 0) {
                    document.add(Paragraph().setHeight(data.cashGapMm * MM_TO_POINTS).setMargin(0f))
                }
            }
        } else if (data.cashPaid > 0 && data.cashLabel.isNotBlank()) {
            val cashTable = Table(totalsWidths)
            cashTable.setWidth(UnitValue.createPointValue(colAmountPt))
            cashTable.setMargin(0f)
            cashTable.addCell(Cell().add(Paragraph(data.cashLabel).setFontSize(data.cashFontSize)).setBorder(null).setPadding(0f))
            cashTable.addCell(Cell().add(Paragraph(String.format("%.2f", data.cashPaid)).setFontSize(data.cashFontSize).setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            document.add(cashTable)
            if (data.cashGapMm > 0) {
                document.add(Paragraph().setHeight(data.cashGapMm * MM_TO_POINTS).setMargin(0f))
            }
        }

        // Balance (overpayment / change to return) or Outstanding (paid less than net total)
        val balance = data.cashPaid - data.netTotal
        val balLabel: String
        val balValue: Double
        if (balance > 0) {
            // Customer paid more than the bill — change to return.
            balLabel = data.balanceLabel
            balValue = balance
        } else {
            // Paid less than (or equal to) the net total — remaining is Outstanding.
            balLabel = data.outstandingLabel
            balValue = data.netTotal - data.cashPaid
        }
        if (balValue > 0 && !data.isOrder) {
            val balTable = Table(totalsWidths)
            balTable.setWidth(UnitValue.createPointValue(colAmountPt))
            balTable.setMargin(0f)
            balTable.addCell(Cell().add(Paragraph(balLabel).setFontSize(data.balanceFontSize)).setBorder(null).setPadding(0f))
            balTable.addCell(Cell().add(Paragraph(String.format("%.2f", balValue)).setFontSize(data.balanceFontSize).setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            document.add(balTable)
            if (data.balanceGap > 0) {
                document.add(Paragraph().setHeight(data.balanceGap * MM_TO_POINTS).setMargin(0f))
            }
        }

        // Final separator
        document.add(Paragraph(separatorLine)
            .setFontSize(data.headerFontSize)
            .setMargin(0f)
            .setPadding(0f))
    }

    // ------------------------------------------------------------------
    // Grouped "Payments by bill" receipt
    //
    // Renders one continuous receipt where each bill's items are shown exactly like the small
    // bill template (item grid + Gross/Discount/Net), followed by the payments made against that
    // bill, with a blank line break between bills. The per-bill grid/totals rendering mirrors
    // addBillContent so the layout is identical to a printed credit/cash small bill.
    // ------------------------------------------------------------------

    fun generatePaymentsByBillPdf(
        title: String,
        summaryLines: List<PaymentReceiptLine>,
        sections: List<PaymentBillBlock>,
        grandTotalLines: List<PaymentReceiptLine>
    ): File {
        val fileName = "payments_by_bill_${System.currentTimeMillis()}.pdf"
        val file = File(context.cacheDir, fileName)

        val writer = PdfWriter(file)
        val pdfDocument = PdfDocument(writer)
        val pageHeight = 500f * MM_TO_POINTS
        val pageSize = PageSize(PAPER_WIDTH, pageHeight)
        val document = Document(pdfDocument, pageSize)

        // Header fields/labels come from the first section's template-derived data.
        val header = sections.firstOrNull()?.data ?: ReceiptData(items = emptyList(), grossTotal = 0.0, discount = 0.0, netTotal = 0.0)

        val topMarginPt = header.upperGap.toFloat() * MM_TO_POINTS
        val leftMarginPt = header.leftGap.toFloat() * MM_TO_POINTS
        document.setMargins(topMarginPt, 0f, 0f, leftMarginPt)
        val contentWidthPt = (PAPER_WIDTH - leftMarginPt - 2f * MM_TO_POINTS).coerceAtLeast(40f)

        try {
            drawCompanyHeaderBlock(document, header, pageHeight)

            // Title
            gapMm(document, 2f)
            drawDottedSep(document, header)
            document.add(
                Paragraph(title)
                    .setFontSize(header.netTotalFontSize)
                    .setBold()
                    .setTextAlignment(TextAlignment.CENTER)
                    .setWidth(UnitValue.createPointValue(contentWidthPt))
                    .setMargin(0f)
                    .setPadding(0f)
            )
            drawDottedSep(document, header)
            gapMm(document, 2f)

            // Date the receipt was produced.
            val dateTime = SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.getDefault()).format(Date())
            document.add(Paragraph("${header.dateLabel} $dateTime").setFontSize(header.dateFontSize).setMargin(0f).setPadding(0f))
            gapMm(document, 2f)

            // Search summary (customer / period / counts).
            summaryLines.forEach { drawPaymentLine(document, header, it) }

            // Each bill: small-bill item grid + the payments against it.
            sections.forEach { section ->
                val d = section.data
                drawDottedSep(document, d)
                gapMm(document, d.separatorGap)

                if (section.customerName.isNotBlank()) {
                    document.add(Paragraph("Customer: ${section.customerName}").setFontSize(d.billNoFontSize).setBold().setMargin(0f).setPadding(0f))
                    gapMm(document, 1f)
                }
                document.add(Paragraph("${d.billNoLabel} ${d.billNo}").setFontSize(d.billNoFontSize).setBold().setMargin(0f).setPadding(0f))
                if (section.billDate.isNotBlank()) {
                    gapMm(document, 1f)
                    document.add(Paragraph("Bill Date: ${section.billDate}").setFontSize(d.billNoFontSize).setMargin(0f).setPadding(0f))
                }
                gapMm(document, d.billNoGap)

                // Bill items + Gross/Discount/Net — identical to the small bill template.
                if (d.items.isNotEmpty()) {
                    drawBillGrid(document, d)
                }

                // Payments made against this bill.
                section.paymentLines.forEach { drawPaymentLine(document, d, it) }

                // Blank line break after each bill.
                gapMm(document, 4f)
            }

            drawDottedSep(document, header)
            gapMm(document, 2f)
            grandTotalLines.forEach { drawPaymentLine(document, header, it) }
            drawDottedSep(document, header)

            if (header.lastLineGap > 0) gapMm(document, header.lastLineGap.toFloat())
            if (header.lastLine.isNotEmpty()) {
                document.add(Paragraph(header.lastLine).setFontSize(header.lastLineFont.toFloat()).setMargin(0f).setPadding(0f))
            }
        } finally {
            document.close()
        }

        return file
    }

    private fun gapMm(document: Document, mm: Float) {
        if (mm > 0) document.add(Paragraph().setHeight(mm * MM_TO_POINTS).setMargin(0f))
    }

    // A true full-width dotted rule for the grouped "payments by bill" receipt. Drawn as a single
    // cell's dotted bottom border spanning the full grid width (data.colAmountMm), so it always
    // reaches edge-to-edge instead of stopping short like a fixed run of dash characters.
    private fun drawDottedSep(document: Document, data: ReceiptData) {
        val colAmountPt = data.colAmountMm * MM_TO_POINTS
        val table = Table(floatArrayOf(1f))
        table.setWidth(UnitValue.createPointValue(colAmountPt))
        table.setMargin(0f)
        table.setPadding(0f)
        val cell = Cell()
            .add(Paragraph(" ").setFontSize(1f).setMargin(0f).setPadding(0f))
            .setBorder(null)
            .setBorderBottom(com.itextpdf.layout.borders.DottedBorder(0.75f))
            .setMargin(0f)
            .setPadding(0f)
        table.addCell(cell)
        document.add(table)
    }

    // Company name / address / contact / email / logo — mirrors the header drawn at the top of
    // generateReceiptPdf so the grouped receipt carries the same company header as a small bill.
    private fun drawCompanyHeaderBlock(document: Document, data: ReceiptData, pageHeight: Float) {
        if (data.logoDataUrl.isNotEmpty()) {
            try {
                val imageBytes = Base64.decode(data.logoDataUrl, Base64.DEFAULT)
                val imageData = com.itextpdf.io.image.ImageDataFactory.create(imageBytes)
                val logoLeftPt = data.logoLeft.toFloat() * MM_TO_POINTS
                val logoUpperPt = data.logoUpper.toFloat() * MM_TO_POINTS
                val logoWidthPt = data.logoWidth.toFloat() * MM_TO_POINTS
                val logoHeightPt = data.logoHeight.toFloat() * MM_TO_POINTS
                val absoluteY = pageHeight - logoUpperPt - logoHeightPt
                document.add(
                    Image(imageData)
                        .setFixedPosition(logoLeftPt, absoluteY)
                        .setWidth(logoWidthPt)
                        .setHeight(logoHeightPt)
                )
            } catch (e: Exception) {
                android.util.Log.e("PdfReceiptGenerator", "Error adding logo: ${e.message}")
            }
        }
        document.add(Paragraph(data.companyName).setFontSize(data.companyNameFont.toFloat()).setBold().setMargin(0f).setPadding(0f))
        if (data.companyGap > 0) gapMm(document, data.companyGap.toFloat())
        if (data.address.isNotEmpty()) {
            document.add(Paragraph(data.address).setFontSize(data.addressFont.toFloat()).setMargin(0f).setPadding(0f))
            if (data.addressGap > 0) gapMm(document, data.addressGap.toFloat())
        }
        if (data.contact.isNotEmpty()) {
            document.add(Paragraph(data.contact).setFontSize(data.contactFont.toFloat()).setMargin(0f).setPadding(0f))
            if (data.contactGap > 0) gapMm(document, data.contactGap.toFloat())
        }
        if (data.email.isNotEmpty()) {
            document.add(Paragraph(data.email).setFontSize(data.emailFont.toFloat()).setMargin(0f).setPadding(0f))
            if (data.emailGap > 0) gapMm(document, data.emailGap.toFloat())
        }
    }

    // A label/value row aligned to the small bill's amount column (used for the payment detail
    // rows and the summary/grand-total rows on the grouped receipt).
    private fun drawPaymentLine(document: Document, data: ReceiptData, line: PaymentReceiptLine) {
        val colAmountPt = data.colAmountMm * MM_TO_POINTS
        val colQtyPt = data.colQtyMm * MM_TO_POINTS
        val dAmountW = (colAmountPt - colQtyPt).coerceAtLeast(10f)
        if (line.separator) {
            drawDottedSep(document, data)
            gapMm(document, data.separatorGap)
            return
        }
        if (line.label.isEmpty() && line.value.isEmpty()) {
            // Blank spacer line.
            gapMm(document, 3f)
            return
        }
        val fontSize = if (line.emphasize) data.netTotalFontSize else data.grossTotalFontSize
        if (line.inline) {
            // Single left-aligned "label value" line (not a right-aligned two-column row).
            val text = if (line.value.isBlank()) line.label else "${line.label} ${line.value}"
            val p = Paragraph(text).setFontSize(fontSize).setMargin(0f).setPadding(0f)
            if (line.bold || line.emphasize) p.setBold()
            document.add(p)
            gapMm(document, 2f)
            return
        }
        val labelW = (colAmountPt - dAmountW).coerceAtLeast(10f)
        val table = Table(floatArrayOf(labelW, dAmountW))
        table.setWidth(UnitValue.createPointValue(colAmountPt))
        table.setMargin(0f)
        table.setPadding(0f)
        val lp = Paragraph(line.label).setFontSize(fontSize).setMargin(0f).setPadding(0f)
        val vp = Paragraph(line.value).setFontSize(fontSize).setTextAlignment(TextAlignment.RIGHT).setMargin(0f).setPadding(0f)
        if (line.bold || line.emphasize) { lp.setBold(); vp.setBold() }
        table.addCell(Cell().add(lp).setBorder(null).setPadding(0f))
        table.addCell(Cell().add(vp).setBorder(null).setPadding(0f))
        document.add(table)
        gapMm(document, 2f)
    }

    // Item grid + Gross/Discount/Net for one bill. This is a faithful copy of the item-table and
    // subtotals portion of addBillContent (without the Cash/Balance section), so a bill printed
    // on the grouped payments receipt is identical to the small bill template.
    private fun drawBillGrid(document: Document, data: ReceiptData) {
        val colPricePt = data.colPriceMm * MM_TO_POINTS
        val colQtyPt = data.colQtyMm * MM_TO_POINTS
        val colAmountPt = data.colAmountMm * MM_TO_POINTS
        val colHeaderPricePt = data.colHeaderPriceMm * MM_TO_POINTS
        val colHeaderQtyPt = data.colHeaderQtyMm * MM_TO_POINTS
        val colHeaderAmountPt = data.colHeaderAmountMm * MM_TO_POINTS

        // Header separator + column headers.
        drawDottedSep(document, data)
        if (data.separatorGap > 0) gapMm(document, data.separatorGap)

        val colHeaderDiscountPt = (colHeaderPricePt + colHeaderQtyPt) / 2f
        val headerTable: Table
        if (data.showLineDiscount) {
            val hItemW = (colHeaderPricePt * 0.5f).coerceAtLeast(10f)
            val hPriceW = (colHeaderPricePt * 0.5f).coerceAtLeast(10f)
            val hDiscW = (colHeaderDiscountPt - colHeaderPricePt).coerceAtLeast(10f)
            val hQtyW = (colHeaderQtyPt - colHeaderDiscountPt).coerceAtLeast(10f)
            val hAmountW = (colHeaderAmountPt - colHeaderQtyPt).coerceAtLeast(10f)
            headerTable = Table(floatArrayOf(hItemW, hPriceW, hDiscW, hQtyW, hAmountW))
            headerTable.setWidth(UnitValue.createPointValue(colHeaderAmountPt))
            headerTable.setMargin(0f)
            headerTable.setPadding(0f)
            headerTable.addCell(Cell().add(Paragraph(data.itemHeader).setFontSize(data.headerFontSize).setBold()).setBorder(null).setPadding(0f))
            headerTable.addCell(Cell().add(Paragraph(data.priceHeader).setFontSize(data.headerFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            headerTable.addCell(Cell().add(Paragraph(data.discountColHeader).setFontSize(data.headerFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            headerTable.addCell(Cell().add(Paragraph(data.qtyHeader).setFontSize(data.headerFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            headerTable.addCell(Cell().add(Paragraph(data.amountHeader).setFontSize(data.headerFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
        } else {
            val hItemW = (colHeaderPricePt * 0.5f).coerceAtLeast(10f)
            val hPriceW = (colHeaderPricePt * 0.5f).coerceAtLeast(10f)
            val hQtyW = (colHeaderQtyPt - colHeaderPricePt).coerceAtLeast(10f)
            val hAmountW = (colHeaderAmountPt - colHeaderQtyPt).coerceAtLeast(10f)
            headerTable = Table(floatArrayOf(hItemW, hPriceW, hQtyW, hAmountW))
            headerTable.setWidth(UnitValue.createPointValue(colHeaderAmountPt))
            headerTable.setMargin(0f)
            headerTable.setPadding(0f)
            headerTable.addCell(Cell().add(Paragraph(data.itemHeader).setFontSize(data.headerFontSize).setBold()).setBorder(null).setPadding(0f))
            headerTable.addCell(Cell().add(Paragraph(data.priceHeader).setFontSize(data.headerFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            headerTable.addCell(Cell().add(Paragraph(data.qtyHeader).setFontSize(data.headerFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            headerTable.addCell(Cell().add(Paragraph(data.amountHeader).setFontSize(data.headerFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
        }
        document.add(headerTable)
        if (data.headerGap > 0) gapMm(document, data.headerGap)
        drawDottedSep(document, data)
        if (data.separatorGap > 0) gapMm(document, data.separatorGap)

        val colDiscountPt = (colPricePt + colQtyPt) / 2f
        val dPriceW = colPricePt.coerceAtLeast(10f)
        val dDiscountW = (colDiscountPt - colPricePt).coerceAtLeast(10f)
        val dQtyW = (colQtyPt - colPricePt).coerceAtLeast(10f)
        val dQtyWithDiscW = (colQtyPt - colDiscountPt).coerceAtLeast(10f)
        val dAmountW = (colAmountPt - colQtyPt).coerceAtLeast(10f)

        data.items.forEach { item ->
            document.add(Paragraph(item.name).setFontSize(data.itemFontSize).setFixedLeading(data.itemFontSize).setMargin(0f).setPadding(0f))
            if (data.itemNameGap > 0) {
                document.add(Paragraph().setHeight(data.itemNameGap * MM_TO_POINTS).setFixedLeading(0f).setMargin(0f).setPadding(0f))
            }
            val detailsTable = if (data.showLineDiscount) {
                Table(floatArrayOf(dPriceW, dDiscountW, dQtyWithDiscW, dAmountW))
            } else {
                Table(floatArrayOf(dPriceW, dQtyW, dAmountW))
            }
            detailsTable.setWidth(UnitValue.createPointValue(colAmountPt))
            detailsTable.setMargin(0f)
            detailsTable.setPadding(0f)
            detailsTable.addCell(Cell().add(
                Paragraph(String.format("%.2f", item.price)).setFontSize(data.itemFontSize).setFixedLeading(data.itemFontSize).setTextAlignment(TextAlignment.RIGHT).setMargin(0f).setPadding(0f)
            ).setBorder(null).setPadding(0f).setMargin(0f))
            if (data.showLineDiscount) {
                detailsTable.addCell(Cell().add(
                    Paragraph(String.format("%.2f", item.discountValue)).setFontSize(data.itemFontSize).setFixedLeading(data.itemFontSize).setTextAlignment(TextAlignment.RIGHT).setMargin(0f).setPadding(0f)
                ).setBorder(null).setPadding(0f).setMargin(0f))
            }
            detailsTable.addCell(Cell().add(
                Paragraph("x${item.quantity}").setFontSize(data.itemFontSize).setFixedLeading(data.itemFontSize).setTextAlignment(TextAlignment.RIGHT).setMargin(0f).setPadding(0f)
            ).setBorder(null).setPadding(0f).setMargin(0f))
            detailsTable.addCell(Cell().add(
                Paragraph(String.format("%.2f", item.amount)).setFontSize(data.itemFontSize).setFixedLeading(data.itemFontSize).setTextAlignment(TextAlignment.RIGHT).setMargin(0f).setPadding(0f)
            ).setBorder(null).setPadding(0f).setMargin(0f))
            document.add(detailsTable)
            if (data.itemGap > 0) {
                document.add(Paragraph().setHeight(data.itemGap * MM_TO_POINTS).setFixedLeading(0f).setMargin(0f).setPadding(0f))
            }
        }

        if (data.returnItems.isNotEmpty()) {
            drawDottedSep(document, data)
            document.add(Paragraph("Returns").setFontSize(data.headerFontSize).setBold().setMargin(0f).setPadding(0f))
            if (data.itemGap > 0) {
                document.add(Paragraph().setHeight(data.itemGap * MM_TO_POINTS).setFixedLeading(0f).setMargin(0f).setPadding(0f))
            }
            data.returnItems.forEach { item ->
                document.add(Paragraph(item.name).setFontSize(data.itemFontSize).setFixedLeading(data.itemFontSize).setMargin(0f).setPadding(0f))
                if (data.itemNameGap > 0) {
                    document.add(Paragraph().setHeight(data.itemNameGap * MM_TO_POINTS).setFixedLeading(0f).setMargin(0f).setPadding(0f))
                }
                val retTable = Table(floatArrayOf(dPriceW, dQtyW, dAmountW))
                retTable.setWidth(UnitValue.createPointValue(colAmountPt))
                retTable.setMargin(0f)
                retTable.setPadding(0f)
                retTable.addCell(Cell().add(
                    Paragraph(String.format("%.2f", item.price)).setFontSize(data.itemFontSize).setFixedLeading(data.itemFontSize).setTextAlignment(TextAlignment.RIGHT).setMargin(0f).setPadding(0f)
                ).setBorder(null).setPadding(0f).setMargin(0f))
                retTable.addCell(Cell().add(
                    Paragraph("x${item.quantity}").setFontSize(data.itemFontSize).setFixedLeading(data.itemFontSize).setTextAlignment(TextAlignment.RIGHT).setMargin(0f).setPadding(0f)
                ).setBorder(null).setPadding(0f).setMargin(0f))
                retTable.addCell(Cell().add(
                    Paragraph("-${String.format("%.2f", item.amount)}").setFontSize(data.itemFontSize).setFixedLeading(data.itemFontSize).setTextAlignment(TextAlignment.RIGHT).setMargin(0f).setPadding(0f)
                ).setBorder(null).setPadding(0f).setMargin(0f))
                document.add(retTable)
                if (data.itemGap > 0) {
                    document.add(Paragraph().setHeight(data.itemGap * MM_TO_POINTS).setFixedLeading(0f).setMargin(0f).setPadding(0f))
                }
            }
        }

        // Totals separator.
        drawDottedSep(document, data)
        if (data.separatorGap > 0) gapMm(document, data.separatorGap)

        val totalsLabelW = (colAmountPt - dAmountW).coerceAtLeast(10f)
        val totalsWidths = floatArrayOf(totalsLabelW, dAmountW)

        if (data.showLineDiscount && data.totalItemDiscount > 0) {
            val tidTable = Table(totalsWidths)
            tidTable.setWidth(UnitValue.createPointValue(colAmountPt))
            tidTable.setMargin(0f)
            tidTable.addCell(Cell().add(Paragraph(data.totalItemDiscountLabel).setFontSize(data.grossTotalFontSize).setBold()).setBorder(null).setPadding(0f))
            tidTable.addCell(Cell().add(Paragraph(String.format("%.2f", data.totalItemDiscount)).setFontSize(data.grossTotalFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            document.add(tidTable)
            if (data.grossTotalGap > 0) gapMm(document, data.grossTotalGap)
            drawDottedSep(document, data)
            if (data.separatorGap > 0) gapMm(document, data.separatorGap)
        }

        val gtTable = Table(totalsWidths)
        gtTable.setWidth(UnitValue.createPointValue(colAmountPt))
        gtTable.setMargin(0f)
        gtTable.addCell(Cell().add(Paragraph(data.grossTotalLabel).setFontSize(data.grossTotalFontSize)).setBorder(null).setPadding(0f))
        gtTable.addCell(Cell().add(Paragraph(String.format("%.2f", data.grossTotal)).setFontSize(data.grossTotalFontSize).setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
        document.add(gtTable)
        if (data.grossTotalGap > 0) gapMm(document, data.grossTotalGap)

        if (data.totalReturn > 0) {
            val retTotalTable = Table(totalsWidths)
            retTotalTable.setWidth(UnitValue.createPointValue(colAmountPt))
            retTotalTable.setMargin(0f)
            retTotalTable.addCell(Cell().add(Paragraph("Total Return:").setFontSize(data.grossTotalFontSize)).setBorder(null).setPadding(0f))
            retTotalTable.addCell(Cell().add(Paragraph("-${String.format("%.2f", data.totalReturn)}").setFontSize(data.grossTotalFontSize).setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            document.add(retTotalTable)
            if (data.grossTotalGap > 0) gapMm(document, data.grossTotalGap)
        }

        if (data.discount > 0) {
            val discTable = Table(totalsWidths)
            discTable.setWidth(UnitValue.createPointValue(colAmountPt))
            discTable.setMargin(0f)
            discTable.addCell(Cell().add(Paragraph(data.discountLabel).setFontSize(data.discountFontSize).setBold()).setBorder(null).setPadding(0f))
            discTable.addCell(Cell().add(Paragraph(String.format("%.2f", data.discount)).setFontSize(data.discountFontSize).setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
            document.add(discTable)
            if (data.discountGap > 0) gapMm(document, data.discountGap)
        }

        val ntTable = Table(totalsWidths)
        ntTable.setWidth(UnitValue.createPointValue(colAmountPt))
        ntTable.setMargin(0f)
        ntTable.addCell(Cell().add(Paragraph(data.netTotalLabel).setFontSize(data.netTotalFontSize).setBold()).setBorder(null).setPadding(0f))
        ntTable.addCell(Cell().add(Paragraph(String.format("%.2f", data.netTotal)).setFontSize(data.netTotalFontSize).setBold().setTextAlignment(TextAlignment.RIGHT)).setBorder(null).setPadding(0f))
        document.add(ntTable)
        if (data.netTotalGap > 0) gapMm(document, data.netTotalGap)
    }
}

/**
 * One bill on the grouped "Payments by bill" receipt: the bill's items/totals ([data], built
 * from the small bill template), the (optional) customer name and bill date shown above the grid,
 * and the [paymentLines] for the payments made against that bill.
 */
data class PaymentBillBlock(
    val data: ReceiptData,
    val customerName: String,
    val billDate: String,
    val paymentLines: List<PaymentReceiptLine>
)
