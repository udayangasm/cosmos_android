package com.pos.cosmos.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pos.cosmos.utils.LocalResponsiveDimensions

@Composable
fun StatCard(
    title: String,
    value: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    val dimens = LocalResponsiveDimensions.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.scaledDp(100f)),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimens.spacingMedium),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = dimens.fontBody2,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .size(dimens.scaledDp(40f))
                        .background(iconColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(dimens.iconNormal)
                            .background(iconColor, RoundedCornerShape(4.dp))
                    )
                }
            }

            Text(
                text = value,
                fontSize = dimens.fontDisplay,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A2E)
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val dimens = LocalResponsiveDimensions.current
    Column(modifier = modifier) {
        Text(
            text = title,
            fontSize = dimens.fontHeadline,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1A1A2E)
        )
        subtitle?.let {
            Text(
                text = it,
                fontSize = dimens.fontBody2,
                color = Color.Gray,
                modifier = Modifier.padding(top = dimens.spacingTiny)
            )
        }
    }
}

@Composable
fun ChartCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dimens = LocalResponsiveDimensions.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacingNormal)
        ) {
            SectionHeader(title = title, subtitle = subtitle)
            Spacer(modifier = Modifier.height(dimens.spacingNormal))
            content()
        }
    }
}
