package com.pos.cosmos.models

data class Expense(
    val id: Long = 0,
    val date: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val categoryId: Long = 0
)
