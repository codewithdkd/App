package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val title: String,
    val category: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isExpense: Boolean = true,
    val notes: String = ""
)

@Entity(tableName = "detected_notifications")
data class DetectedNotification(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val appName: String,
    val title: String,
    val text: String,
    val amount: Double,
    val sender: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isLogged: Boolean = false
)

@Entity(tableName = "budget_limits")
data class BudgetLimit(
    @PrimaryKey val category: String,
    val limitAmount: Double
)
