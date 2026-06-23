package com.pos.cosmos.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pos.cosmos.models.PromotionOffer

/**
 * Promotion offer pop-up shared by the Order Form and Credit Bill screens.
 * Mirrors the web frontend's "Promotion Offer!" dialog: each qualifying promo
 * is listed with Apply / No thanks buttons; closing the dialog declines all.
 */
@Composable
fun PromotionOfferDialog(
    offers: List<PromotionOffer>,
    onApply: (PromotionOffer) -> Unit,
    onDecline: (PromotionOffer) -> Unit,
    onDeclineAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDeclineAll,
        title = { Text("Promotion Offer!", fontWeight = FontWeight.Bold, color = Color(0xFFE94560)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                offers.forEach { offer ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                offer.name ?: "",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.Black
                            )
                            if (!offer.description.isNullOrEmpty()) {
                                Text(offer.description, fontSize = 12.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                buildString {
                                    append("Get ${offer.offerText()} FREE")
                                    if (offer.freeValue() > 0) {
                                        append(" (worth Rs. ${String.format("%,.2f", offer.freeValue())})")
                                    }
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E7D32)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { onApply(offer) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                ) {
                                    Text("Apply")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(onClick = { onDecline(offer) }) {
                                    Text("No, thanks")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDeclineAll) {
                Text("Dismiss All")
            }
        }
    )
}
