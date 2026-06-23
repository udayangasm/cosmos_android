package com.pos.cosmos.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pos.cosmos.models.SalesStatPoint
import com.pos.cosmos.models.TopSellingItem
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SalesLineChart(
    data: List<SalesStatPoint>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No data available", color = Color.Gray)
        }
        return
    }

    val maxValue = remember(data) { data.maxOfOrNull { it.amount } ?: 0.0 }
    val minValue = remember(data) { data.minOfOrNull { it.amount } ?: 0.0 }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(vertical = 8.dp)
        ) {
            val width = size.width
            val height = size.height
            val spacing = width / (data.size - 1).coerceAtLeast(1)

            val points = data.mapIndexed { index, point ->
                val x = index * spacing
                val y = height - ((point.amount - minValue) / (maxValue - minValue).coerceAtLeast(1.0) * height).toFloat()
                Offset(x, y)
            }

            // Draw gradient fill
            val path = Path().apply {
                moveTo(points.first().x, height)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, height)
                close()
            }

            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6366F1).copy(alpha = 0.3f),
                        Color(0xFF6366F1).copy(alpha = 0.05f)
                    )
                )
            )

            // Draw line
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }

            drawPath(
                path = linePath,
                color = Color(0xFF6366F1),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw points
            points.forEach { point ->
                drawCircle(
                    color = Color.White,
                    radius = 6.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = Color(0xFF6366F1),
                    radius = 4.dp.toPx(),
                    center = point
                )
            }
        }

        // X-axis labels
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            items(data.takeLast(6)) { point ->
                Text(
                    text = point.date,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
fun DonutChart(
    items: List<TopSellingItem>,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(250.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No data available", color = Color.Gray)
        }
        return
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Donut chart
        Canvas(
            modifier = Modifier
                .size(180.dp)
                .padding(8.dp)
        ) {
            val strokeWidth = 40.dp.toPx()
            var startAngle = -90f

            items.forEach { item ->
                val sweepAngle = (item.percentage / 100f) * 360f

                drawArc(
                    color = Color(item.color),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth)
                )

                startAngle += sweepAngle
            }
        }

        // Legend
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEach { item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(item.color))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.itemName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = "${item.percentage.toInt()}%",
                            fontSize = 9.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StockGauge(
    currentStock: Int,
    maxStock: Int,
    lowStockCount: Int,
    modifier: Modifier = Modifier
) {
    val percentage = if (maxStock > 0) (currentStock.toFloat() / maxStock.toFloat()) else 0f

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .size(150.dp)
                .padding(16.dp)
        ) {
            val strokeWidth = 25.dp.toPx()
            val sweepAngle = 180f
            val gaugeStartAngle = 180f

            // Background arc
            drawArc(
                color = Color(0xFFE5E7EB),
                startAngle = gaugeStartAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(size.width - strokeWidth, size.height - strokeWidth)
            )

            // Progress arc
            val progressColor = when {
                percentage < 0.3f -> Color(0xFFEF4444)
                percentage < 0.7f -> Color(0xFFF59E0B)
                else -> Color(0xFF10B981)
            }

            drawArc(
                color = progressColor,
                startAngle = gaugeStartAngle,
                sweepAngle = sweepAngle * percentage,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(size.width - strokeWidth, size.height - strokeWidth)
            )
        }

        Text(
            text = currentStock.toString(),
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A2E)
        )
        Text(
            text = "Units",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StockIndicator(
                label = "Low Stock",
                value = lowStockCount.toString(),
                color = Color(0xFFEF4444)
            )
            StockIndicator(
                label = "In Stock",
                value = currentStock.toString(),
                color = Color(0xFF10B981)
            )
        }
    }
}

@Composable
fun StockIndicator(
    label: String,
    value: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}
