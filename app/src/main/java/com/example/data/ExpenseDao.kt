package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactionsFlow(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllTransactionsRaw(): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Int)

    @Query("SELECT * FROM detected_notifications WHERE isLogged = 0 ORDER BY timestamp DESC")
    fun getPendingNotificationsFlow(): Flow<List<DetectedNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetectedNotification(notification: DetectedNotification)

    @Query("UPDATE detected_notifications SET isLogged = 1 WHERE id = :id")
    suspend fun markNotificationAsLogged(id: Int)

    @Query("DELETE FROM detected_notifications WHERE id = :id")
    suspend fun deleteDetectedNotificationById(id: Int)

    @Query("SELECT * FROM budget_limits")
    fun getAllBudgetLimitsFlow(): Flow<List<BudgetLimit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgetLimit(limit: BudgetLimit)

    @Query("DELETE FROM budget_limits WHERE category = :category")
    suspend fun deleteBudgetLimitByCategory(category: String)
}
