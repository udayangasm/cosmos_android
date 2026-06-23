package com.pos.cosmos.models

data class PrinterInfo(
    val id: String,
    val name: String? = null,
    val connected: Boolean = false
)
