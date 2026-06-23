package com.pos.cosmos.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

private const val TAG = "ReceiptPreviewDialog"

private fun renderPdfPages(pdfFile: File, targetWidthPx: Int = 720): List<Bitmap> {
    return try {
        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val pages = mutableListOf<Bitmap>()
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val scale = targetWidthPx.toFloat() / page.width
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(targetWidthPx, height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pages.add(bmp)
            page.close()
        }
        renderer.close()
        pfd.close()
        pages
    } catch (e: Exception) {
        Log.e(TAG, "Failed to render PDF", e)
        emptyList()
    }
}

@Composable
fun ReceiptPreviewDialog(
    pdfFile: File,
    canShare: Boolean,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val pages = remember(pdfFile.absolutePath) { renderPdfPages(pdfFile) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Receipt Preview",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color(0xFFE0E0E0))
                        .padding(8.dp)
                ) {
                    if (pages.isEmpty()) {
                        Text(
                            "Failed to render receipt",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pages) { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Receipt page",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (canShare) {
                        Button(
                            onClick = onShare,
                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF1976D2))
                        ) {
                            Text("Share")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Button(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}
