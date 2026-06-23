package com.pos.cosmos.services

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import com.pos.cosmos.utils.PdfReceiptGenerator
import com.pos.cosmos.utils.ReceiptData
import java.io.File
import java.io.OutputStream
import java.util.UUID

class PrinterService private constructor() {

    companion object {
        private const val TAG = "PrinterService"
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CHARSET = "GBK"  // For thermal printers

        // Singleton instance
        @Volatile
        private var instance: PrinterService? = null

        fun getInstance(): PrinterService {
            return instance ?: synchronized(this) {
                instance ?: PrinterService().also { instance = it }
            }
        }
    }

    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var isConnected = false
    private var connectedDeviceName: String? = null

    fun connectDevice(device: BluetoothDevice): Boolean {
        return try {
            // If already connected to the same device, return true
            if (isConnected() && connectedDeviceName == device.name) {
                Log.d(TAG, "Already connected to ${device.name}")
                return true
            }

            // If connected to a different device, disconnect first
            if (isConnected) {
                Log.d(TAG, "Disconnecting from previous device to connect to ${device.name}")
                disconnectDevice()
            }

            bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothSocket?.connect()
            outputStream = bluetoothSocket?.outputStream
            isConnected = true
            connectedDeviceName = device.name

            Log.d(TAG, "Connected to ${device.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connection failed: ${e.message}", e)
            isConnected = false
            connectedDeviceName = null
            false
        }
    }

    fun disconnectDevice() {
        try {
            outputStream?.close()
            bluetoothSocket?.close()
            outputStream = null
            bluetoothSocket = null
            isConnected = false
            connectedDeviceName = null
            Log.d(TAG, "Disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Disconnection error: ${e.message}")
        }
    }

    fun isConnected(): Boolean {
        return isConnected && bluetoothSocket?.isConnected == true
    }

    fun getConnectedDeviceName(): String? {
        return if (isConnected()) connectedDeviceName else null
    }

    fun sendData(data: ByteArray): Boolean {
        return try {
            if (!isConnected()) {
                Log.e(TAG, "Not connected to printer")
                return false
            }
            outputStream?.write(data)
            outputStream?.flush()
            Log.d(TAG, "Data sent successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}", e)
            false
        }
    }

    fun sendText(text: String): Boolean {
        return try {
            val data = text.toByteArray(charset(CHARSET))
            sendData(data)
        } catch (e: Exception) {
            Log.e(TAG, "Text send failed: ${e.message}")
            false
        }
    }

    fun printTest(): Boolean {
        try {
            val receipt = buildTestReceipt()
            return sendData(receipt)
        } catch (e: Exception) {
            Log.e(TAG, "Test print failed: ${e.message}", e)
            return false
        }
    }

    fun printReceipt(): Boolean {
        try {
            val receipt = buildReceipt()
            return sendData(receipt)
        } catch (e: Exception) {
            Log.e(TAG, "Receipt print failed: ${e.message}", e)
            return false
        }
    }

    fun printExactReceipt(receiptData: ByteArray): Boolean {
        try {
            return sendData(receiptData)
        } catch (e: Exception) {
            Log.e(TAG, "Exact receipt print failed: ${e.message}", e)
            return false
        }
    }

    fun printPdfReceipt(pdfGenerator: PdfReceiptGenerator, receiptData: ReceiptData): Boolean {
        return try {
            val pdfFile = pdfGenerator.generateReceiptPdf(receiptData)
            Log.d(TAG, "PDF receipt generated at: ${pdfFile.absolutePath}")

            // Render PDF to bitmap and print directly
            val bitmap = renderPdfToBitmap(pdfFile)
            if (bitmap != null) {
                Log.d(TAG, "PDF rendered to bitmap: ${bitmap.width}x${bitmap.height}")
                val success = printBitmap(bitmap)
                bitmap.recycle() // Free memory
                return success
            } else {
                Log.e(TAG, "Failed to render PDF to bitmap, falling back to text mode")
                return printReceiptData(receiptData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDF receipt printing failed: ${e.message}", e)
            false
        }
    }

    /**
     * Same as [printPdfReceipt] but also returns the generated PDF file so callers
     * can offer print-preview or share actions without re-generating the receipt.
     */
    fun printPdfReceiptAndReturnFile(pdfGenerator: PdfReceiptGenerator, receiptData: ReceiptData): File? {
        // 1) Generate the PDF first. If generation itself fails there is genuinely nothing
        //    to preview, share or print, so returning null here is correct.
        val pdfFile = try {
            pdfGenerator.generateReceiptPdf(receiptData)
        } catch (e: Exception) {
            Log.e(TAG, "PDF receipt generation failed: ${e.message}", e)
            return null
        }
        Log.d(TAG, "PDF receipt generated at: ${pdfFile.absolutePath}")

        // 2) Printing is best-effort. A transient print failure (Bluetooth hiccup, bitmap
        //    OOM, renderer contention, etc.) must NOT discard the already-generated file,
        //    otherwise the print-preview / share buttons silently stop working even though
        //    a perfectly valid receipt exists. Always return the file.
        try {
            val bitmap = renderPdfToBitmap(pdfFile)
            if (bitmap != null) {
                printBitmap(bitmap)
                bitmap.recycle()
            } else {
                Log.e(TAG, "Failed to render PDF to bitmap, falling back to text mode")
                printReceiptData(receiptData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "PDF printing failed (returning generated file anyway): ${e.message}", e)
        }
        return pdfFile
    }

    /**
     * Print an already-generated PDF file (e.g. a payment receipt) to the connected
     * thermal printer. Best-effort: returns false (without throwing) if no printer is
     * connected or the PDF can't be rendered, so callers can still preview / share it.
     */
    fun printPdfFile(pdfFile: File): Boolean {
        return try {
            if (!isConnected()) return false
            val bitmap = renderPdfToBitmap(pdfFile) ?: return false
            val success = printBitmap(bitmap)
            bitmap.recycle()
            success
        } catch (e: Exception) {
            Log.e(TAG, "printPdfFile failed: ${e.message}", e)
            false
        }
    }

    /**
     * Render PDF file to bitmap for thermal printing
     */
    private fun renderPdfToBitmap(pdfFile: File): Bitmap? {
        return try {
            val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)

            if (pdfRenderer.pageCount == 0) {
                Log.e(TAG, "PDF has no pages")
                pdfRenderer.close()
                fileDescriptor.close()
                return null
            }

            val page = pdfRenderer.openPage(0)

            // Calculate pixel width from the PDF page width at 203 DPI (standard thermal printer)
            // PDF page width is in points (1 point = 1/72 inch)
            // pixels = (page width in points / 72) * 203 DPI
            val dpi = 203f
            val pageWidthInInches = page.width.toFloat() / 72f
            val width = (pageWidthInInches * dpi).toInt()
            val height = (page.height.toFloat() / page.width.toFloat() * width).toInt()

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

            page.close()
            pdfRenderer.close()
            fileDescriptor.close()

            // Crop trailing white space from bottom
            val croppedBitmap = cropBottomWhitespace(bitmap)
            bitmap.recycle()

            // Convert to monochrome bitmap for thermal printing
            val monochromeBitmap = convertToMonochrome(croppedBitmap)
            croppedBitmap.recycle()

            monochromeBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering PDF to bitmap: ${e.message}", e)
            null
        }
    }

    /**
     * Crop trailing white space from the bottom of the bitmap
     * Scans from bottom up to find the last non-white row, then crops
     */
    private fun cropBottomWhitespace(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        var lastContentRow = height - 1

        // Scan from bottom up to find last row with non-white pixels
        outer@ for (y in height - 1 downTo 0) {
            for (x in 0 until width step 4) { // Sample every 4th pixel for speed
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // If any pixel is not near-white, this row has content
                if (r < 240 || g < 240 || b < 240) {
                    lastContentRow = y
                    break@outer
                }
            }
        }

        // Add a small margin (about 5mm at 203 DPI = ~40 pixels)
        val cropHeight = (lastContentRow + 40).coerceAtMost(height)

        return if (cropHeight < height) {
            Bitmap.createBitmap(bitmap, 0, 0, width, cropHeight)
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, width, height)
        }
    }

    /**
     * Convert bitmap to monochrome (black and white) for thermal printing
     */
    private fun convertToMonochrome(original: Bitmap): Bitmap {
        val width = original.width
        val height = original.height
        val monochrome = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = original.getPixel(x, y)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)

                // Calculate luminance
                val luminance = (0.299 * red + 0.587 * green + 0.114 * blue).toInt()

                // Threshold at 128 (adjust for better results)
                val bw = if (luminance < 128) Color.BLACK else Color.WHITE
                monochrome.setPixel(x, y, bw)
            }
        }

        return monochrome
    }

    /**
     * Print bitmap using ESC/POS raster graphics commands
     */
    private fun printBitmap(bitmap: Bitmap): Boolean {
        return try {
            if (!isConnected()) {
                Log.e(TAG, "Not connected to printer")
                return false
            }

            // Initialize printer
            val initCmd = byteArrayOf(0x1B, 0x40) // ESC @
            sendData(initCmd)

            val width = bitmap.width
            val height = bitmap.height

            Log.d(TAG, "Printing bitmap: ${width}x${height}")

            // Print bitmap line by line using GS v 0 command
            val bytesPerLine = (width + 7) / 8

            for (y in 0 until height) {
                val lineData = ByteArray(bytesPerLine)

                for (x in 0 until width) {
                    val pixel = bitmap.getPixel(x, y)
                    val isBlack = Color.red(pixel) < 128

                    if (isBlack) {
                        val byteIndex = x / 8
                        val bitIndex = 7 - (x % 8)
                        lineData[byteIndex] = (lineData[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                    }
                }

                // Send raster bitmap line: GS v 0
                val cmd = byteArrayOf(
                    0x1D, 0x76, 0x30, 0x00, // GS v 0 m
                    (bytesPerLine and 0xFF).toByte(),
                    ((bytesPerLine shr 8) and 0xFF).toByte(),
                    0x01, 0x00 // height = 1 line
                )

                sendData(cmd)
                sendData(lineData)
            }

            // Feed paper and cut
            val feedCmd = byteArrayOf(0x1B, 0x64, 0x03) // ESC d (feed 3 lines)
            sendData(feedCmd)

            val cutCmd = byteArrayOf(0x1D, 0x56, 0x00) // GS V (cut)
            sendData(cutCmd)

            Log.d(TAG, "Bitmap printed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Bitmap printing failed: ${e.message}", e)
            false
        }
    }

    private fun printReceiptData(receiptData: com.pos.cosmos.utils.ReceiptData): Boolean {
        return try {
            if (!isConnected()) {
                Log.e(TAG, "Not connected to printer")
                return false
            }

            val sb = StringBuilder()
            val dateTime = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(System.currentTimeMillis())

            // Initialize
            sb.append("\u001B@")

            // Center align
            sb.append("\u001B\u0061\u0001")

            // Large text - Company name
            sb.append("\u001B\u0021\u0030")
            sb.append(receiptData.companyName)
            sb.append("\n")

            // Normal size
            sb.append("\u001B\u0021\u0000")

            // Left align
            sb.append("\u001B\u0061\u0000")

            // Company details
            if (receiptData.address.isNotEmpty()) {
                sb.append(receiptData.address)
                sb.append("\n")
            }
            if (receiptData.contact.isNotEmpty()) {
                sb.append(receiptData.contact)
                sb.append("\n")
            }
            if (receiptData.email.isNotEmpty()) {
                sb.append(receiptData.email)
                sb.append("\n")
            }

            sb.append("==================================================\n")

            // Bill info
            sb.append(String.format("%-20s: %-15s\n", "Date/Time", dateTime))
            sb.append(String.format("%-20s: %-15s\n", "Customer", receiptData.customer))
            sb.append(String.format("%-20s: %-15s\n", "Sales Rep", receiptData.salesRep))

            sb.append("==================================================\n")

            // Table header
            sb.append(String.format("%-20s %6s %8s %10s\n", "Item", "Qty", "Price", "Amount"))
            sb.append("==================================================\n")

            // Items
            receiptData.items.forEach { item ->
                sb.append(String.format("%-20s %6d %8.2f %10.2f\n",
                    item.name.take(20),
                    item.quantity,
                    item.price,
                    item.amount
                ))
            }

            sb.append("\n")

            // Totals
            sb.append("==================================================\n")
            sb.append(String.format("%-30s %10.2f\n", "Gross Total:", receiptData.grossTotal))
            if (receiptData.discount > 0) {
                sb.append(String.format("%-30s %10.2f\n", "Discount:", receiptData.discount))
            }
            sb.append(String.format("%-30s %10.2f\n", "Net Total:", receiptData.netTotal))
            if (receiptData.cashPaid > 0) {
                sb.append(String.format("%-30s %10.2f\n", "Cash Paid:", receiptData.cashPaid))
                val change = receiptData.cashPaid - receiptData.netTotal
                if (change > 0) {
                    sb.append(String.format("%-30s %10.2f\n", "Change:", change))
                }
            }

            sb.append("==================================================\n")

            // Footer
            sb.append("\u001B\u0061\u0001")  // Center
            sb.append("\u001B\u0021\u0010")  // Double height
            sb.append("Thank you!\n")
            sb.append("Please visit again!\n")
            sb.append("\u001B\u0021\u0000")  // Normal
            sb.append("\u001B\u0061\u0000")  // Left

            sb.append("\n\n\n\n")

            // Cut paper
            sb.append("\u001D\u0056\u0000")

            val data = sb.toString().toByteArray(charset(CHARSET))
            sendData(data)
        } catch (e: Exception) {
            Log.e(TAG, "Receipt print failed: ${e.message}", e)
            false
        }
    }

    private fun buildTestReceipt(): ByteArray {
        val sb = StringBuilder()

        // Initialize printer
        sb.append("\u001B@")  // ESC @

        // Enable bold
        sb.append("\u001B\u0045\u0001")  // ESC E (bold)

        // Center align
        sb.append("\u001B\u0061\u0001")  // ESC a (center)

        // Large text
        sb.append("\u001B\u0021\u0030")  // Double size

        sb.append("TEST PRINT\n")
        sb.append("From COSMOS POS\n")

        // Normal size
        sb.append("\u001B\u0021\u0000")

        // Disable bold
        sb.append("\u001B\u0045\u0000")

        // Left align
        sb.append("\u001B\u0061\u0000")

        sb.append("\n")
        sb.append("Test successful!\n")
        sb.append("\n\n\n")

        // Cut paper
        sb.append("\u001D\u0056\u0000")  // GS V

        return sb.toString().toByteArray(charset(CHARSET))
    }

    private fun buildReceipt(): ByteArray {
        val sb = StringBuilder()
        val dateTime = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm:ss").format(System.currentTimeMillis())

        // Initialize
        sb.append("\u001B@")

        // Center align
        sb.append("\u001B\u0061\u0001")

        // Large text
        sb.append("\u001B\u0021\u0030")
        sb.append("COSMOS POS\n")

        // Normal size
        sb.append("\u001B\u0021\u0000")

        // Left align
        sb.append("\u001B\u0061\u0000")

        sb.append("==================================================\n")
        sb.append("Company: COSMOS Retail\n")
        sb.append("Address: 123 Main Street\n")
        sb.append("Phone: (555) 123-4567\n")
        sb.append("Email: info@cosmos.com\n")
        sb.append("==================================================\n")

        // Bill info
        sb.append(String.format("%-24s: %-15s\n", "Bill No", "10"))
        sb.append(String.format("%-24s: %-15s\n", "Date/Time", dateTime))
        sb.append(String.format("%-24s: %-15s\n", "Cashier", "John Doe"))

        sb.append("==================================================\n")

        // Table header
        sb.append("Item               Qty   Price    Amount\n")
        sb.append("==================================================\n")

        // Items
        sb.append("1. Atlas 80 Pages\n")
        sb.append(String.format("%-18s %.3f %8.2f %8.2f\n", "", 1.000, 100.00, 100.00))

        sb.append("\n")

        // Totals
        sb.append("==================================================\n")
        sb.append(String.format("%-30s %8.2f\n", "Gross Total:", 100.00))
        sb.append(String.format("%-30s %8.2f\n", "Discount:", 20.00))
        sb.append(String.format("%-30s %8.2f\n", "Net Total:", 80.00))
        sb.append(String.format("%-30s %8.2f\n", "Cash Paid:", 100.00))
        sb.append(String.format("%-30s %8.2f\n", "Change:", 20.00))

        sb.append("==================================================\n")

        // Footer
        sb.append("\u001B\u0061\u0001")  // Center
        sb.append("\u001B\u0021\u0010")  // Double height

        sb.append("Thank you!\n")
        sb.append("Please visit again!\n")

        sb.append("\u001B\u0021\u0000")  // Normal
        sb.append("\u001B\u0061\u0000")  // Left

        sb.append("\n\n\n\n")

        // Cut paper
        sb.append("\u001D\u0056\u0000")

        return sb.toString().toByteArray(charset(CHARSET))
    }
}
