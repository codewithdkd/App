package com.example.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val expenseDao: ExpenseDao) {
    val allTransactions: Flow<List<Transaction>> = expenseDao.getAllTransactionsFlow()
    val pendingNotifications: Flow<List<DetectedNotification>> = expenseDao.getPendingNotificationsFlow()
    val allBudgetLimits: Flow<List<BudgetLimit>> = expenseDao.getAllBudgetLimitsFlow()

    suspend fun getAllTransactionsRaw(): List<Transaction> {
        return expenseDao.getAllTransactionsRaw()
    }

    suspend fun insertTransaction(transaction: Transaction) {
        expenseDao.insertTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: Transaction) {
        expenseDao.updateTransaction(transaction)
    }

    suspend fun deleteTransactionById(id: Int) {
        expenseDao.deleteTransactionById(id)
    }

    suspend fun insertDetectedNotification(notification: DetectedNotification) {
        expenseDao.insertDetectedNotification(notification)
    }

    suspend fun markNotificationAsLogged(id: Int) {
        expenseDao.markNotificationAsLogged(id)
    }

    suspend fun deleteDetectedNotificationById(id: Int) {
        expenseDao.deleteDetectedNotificationById(id)
    }

    suspend fun insertBudgetLimit(limit: BudgetLimit) {
        expenseDao.insertBudgetLimit(limit)
    }

    suspend fun deleteBudgetLimitByCategory(category: String) {
        expenseDao.deleteBudgetLimitByCategory(category)
    }
}
