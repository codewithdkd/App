package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ExpenseRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ExpenseRepository(database.expenseDao())
    }

    // Reactive streams from database
    val allTransactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingNotifications: StateFlow<List<DetectedNotification>> = repository.pendingNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBudgetLimits: StateFlow<List<BudgetLimit>> = repository.allBudgetLimits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filtering states
    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val selectedMonth = _selectedMonth.asStateFlow()

    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear = _selectedYear.asStateFlow()

    // Quick notification dialog state (from Intent)
    private val _intentQuickLogData = MutableStateFlow<QuickLogData?>(null)
    val intentQuickLogData = _intentQuickLogData.asStateFlow()

    // PDF sharing state
    private val _pdfShareUri = MutableSharedFlow<Uri>()
    val pdfShareUri = _pdfShareUri.asSharedFlow()

    // Backup & restore result notification
    private val _backupStatusMessage = MutableSharedFlow<String>()
    val backupStatusMessage = _backupStatusMessage.asSharedFlow()

    fun selectMonth(month: Int) {
        _selectedMonth.value = month
    }

    fun selectYear(year: Int) {
        _selectedYear.value = year
    }

    fun setQuickLogIntentData(amount: Double, appName: String, merchant: String) {
        _intentQuickLogData.value = QuickLogData(amount, appName, merchant)
    }

    fun clearQuickLogIntentData() {
        _intentQuickLogData.value = null
    }

    // Manual CRUD Actions
    fun addTransaction(amount: Double, title: String, category: String, isExpense: Boolean, timestamp: Long, notes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trans = Transaction(
                amount = amount,
                title = title.ifBlank { if (isExpense) "Manual Expense" else "Manual Income" },
                category = category,
                timestamp = timestamp,
                isExpense = isExpense,
                notes = notes
            )
            repository.insertTransaction(trans)
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTransaction(transaction)
        }
    }

    fun deleteTransaction(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTransactionById(id)
        }
    }

    // Notification Parsing Actions
    fun approveAndLogNotification(notificationId: Int, amount: Double, title: String, category: String, notes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val trans = Transaction(
                amount = amount,
                title = title.ifBlank { "Logged UPI payment" },
                category = category,
                timestamp = System.currentTimeMillis(),
                isExpense = true,
                notes = notes
            )
            repository.insertTransaction(trans)
            repository.markNotificationAsLogged(notificationId)
        }
    }

    fun dismissNotification(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteDetectedNotificationById(id)
        }
    }

    // Budget Limits Actions
    fun updateBudgetLimit(category: String, limit: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            if (limit <= 0) {
                repository.deleteBudgetLimitByCategory(category)
            } else {
                repository.insertBudgetLimit(BudgetLimit(category, limit))
            }
        }
    }

    // PDF Report Generation and shareable file creation
    fun generatePdfReport(context: Context, recordType: String) { // "month" or "year" or "all"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Fetch required data from flow or query helper
                val transactions = repository.getAllTransactionsRaw()
                val limits = allBudgetLimits.value

                val currentMonth = selectedMonth.value
                val currentYear = selectedYear.value

                val filtered = when (recordType) {
                    "month" -> {
                        transactions.filter {
                            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                            cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
                        }
                    }
                    "year" -> {
                        transactions.filter {
                            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                            cal.get(Calendar.YEAR) == currentYear
                        }
                    }
                    else -> transactions
                }

                // Create PdfDocument
                val pdfDocument = PdfDocument()
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
                var page = pdfDocument.startPage(pageInfo)
                var canvas: Canvas = page.canvas

                val paint = Paint()
                val boldPaint = Paint().apply { isFakeBoldText = true }

                // Title Header Area
                paint.color = Color.parseColor("#1A237E") // Deep Indigo Primary
                canvas.drawRect(0f, 0f, 595f, 130f, paint)

                paint.color = Color.WHITE
                paint.textSize = 24f
                boldPaint.color = Color.WHITE
                boldPaint.textSize = 24f
                canvas.drawText("FINANCE TRACKER REPORT", 30f, 50f, boldPaint)

                val displayDate = when (recordType) {
                    "month" -> {
                        val monthName = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(
                            Calendar.getInstance().apply { set(Calendar.MONTH, currentMonth); set(Calendar.YEAR, currentYear) }.time
                        )
                        "Statement for $monthName"
                    }
                    "year" -> "Statement for Year $currentYear"
                    else -> "All-time Transaction Ledger"
                }

                paint.textSize = 12f
                canvas.drawText(displayDate, 30f, 75f, paint)
                canvas.drawText("Generated on: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}", 30f, 95f, paint)

                // Summary Numbers
                val totalIncome = filtered.filter { !it.isExpense }.sumOf { it.amount }
                val totalExpense = filtered.filter { it.isExpense }.sumOf { it.amount }
                val netSavings = totalIncome - totalExpense

                // White paper background starting at Y=130
                var y = 160f

                // Draw Summary Box
                paint.color = Color.parseColor("#F5F5F5")
                canvas.drawRect(30f, y, 565f, y + 80f, paint)

                paint.color = Color.BLACK
                paint.textSize = 11f
                canvas.drawText("TOTAL INCOME", 50f, y + 30f, paint)
                canvas.drawText("TOTAL EXPENSES", 230f, y + 30f, paint)
                canvas.drawText("NET SAVINGS", 410f, y + 30f, paint)

                boldPaint.textSize = 14f
                boldPaint.color = Color.parseColor("#2E7D32") // Green
                canvas.drawText("₹${String.format(Locale.US, "%.2f", totalIncome)}", 50f, y + 55f, boldPaint)

                boldPaint.color = Color.parseColor("#C62828") // Red
                canvas.drawText("₹${String.format(Locale.US, "%.2f", totalExpense)}", 230f, y + 55f, boldPaint)

                boldPaint.color = if (netSavings >= 0) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
                canvas.drawText("₹${String.format(Locale.US, "%.2f", netSavings)}", 410f, y + 55f, boldPaint)

                y += 110f

                // Category Breakdowns section
                boldPaint.textSize = 12f
                boldPaint.color = Color.parseColor("#1A237E")
                canvas.drawText("Budgeting and Analysis Groupings", 30f, y, boldPaint)
                y += 15f

                paint.color = Color.parseColor("#E0E0E0")
                canvas.drawLine(30f, y, 565f, y, paint)
                y += 20f

                val categories = filtered.filter { it.isExpense }.groupBy { it.category }
                if (categories.isEmpty()) {
                    paint.color = Color.GRAY
                    paint.textSize = 11f
                    canvas.drawText("No expenses logged in this range to analyze.", 40f, y, paint)
                    y += 25f
                } else {
                    paint.color = Color.BLACK
                    paint.textSize = 10f
                    canvas.drawText("Category", 40f, y, paint)
                    canvas.drawText("Spent Amount", 230f, y, paint)
                    canvas.drawText("Budget Limit", 380f, y, paint)
                    canvas.drawText("Status / Balance", 480f, y, paint)
                    y += 15f

                    for ((catName, txs) in categories) {
                        if (y > 800) {
                            pdfDocument.finishPage(page)
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            y = 50f
                        }
                        val spent = txs.sumOf { it.amount }
                        val limit = limits.find { it.category.equals(catName, ignoreCase = true) }?.limitAmount ?: 0.0

                        paint.color = Color.BLACK
                        paint.textSize = 10f
                        canvas.drawText(catName, 40f, y, paint)
                        canvas.drawText("₹${String.format(Locale.US, "%.2f", spent)}", 230f, y, paint)

                        if (limit > 0) {
                            canvas.drawText("₹${String.format(Locale.US, "%.2f", limit)}", 380f, y, paint)
                            val budgetStatus = limit - spent
                            if (budgetStatus < 0) {
                                boldPaint.color = Color.parseColor("#C62828")
                                boldPaint.textSize = 10f
                                canvas.drawText("Exceeded by ₹${String.format(Locale.US, "%.2f", -budgetStatus)}", 480f, y, boldPaint)
                            } else {
                                boldPaint.color = Color.parseColor("#2E7D32")
                                boldPaint.textSize = 10f
                                canvas.drawText("Safe / ₹${String.format(Locale.US, "%.2f", budgetStatus)} left", 480f, y, boldPaint)
                            }
                        } else {
                            canvas.drawText("No Limit Set", 380f, y, paint)
                            canvas.drawText("-", 480f, y, paint)
                        }
                        y += 20f
                    }
                }

                y += 20f

                // Individual Ledger Items section
                boldPaint.textSize = 12f
                boldPaint.color = Color.parseColor("#1A237E")
                canvas.drawText("Detailed Ledger Records", 30f, y, boldPaint)
                y += 15f

                paint.color = Color.parseColor("#E0E0E0")
                canvas.drawLine(30f, y, 565f, y, paint)
                y += 20f

                if (filtered.isEmpty()) {
                    paint.color = Color.GRAY
                    paint.textSize = 11f
                    canvas.drawText("No matching transaction records available currently.", 40f, y, paint)
                } else {
                    paint.color = Color.BLACK
                    paint.textSize = 10f
                    canvas.drawText("Date", 40f, y, paint)
                    canvas.drawText("Title / Merchant", 120f, y, paint)
                    canvas.drawText("Category", 340f, y, paint)
                    canvas.drawText("Type", 440f, y, paint)
                    canvas.drawText("Amount", 500f, y, paint)
                    y += 15f

                    val sdfItem = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                    for (item in filtered) {
                        if (y > 800) {
                            pdfDocument.finishPage(page)
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            y = 50f
                        }

                        paint.color = Color.BLACK
                        paint.textSize = 9f
                        val itemDate = sdfItem.format(Date(item.timestamp))
                        canvas.drawText(itemDate, 40f, y, paint)
                        canvas.drawText(item.title.take(30), 120f, y, paint)
                        canvas.drawText(item.category, 340f, y, paint)

                        if (item.isExpense) {
                            boldPaint.color = Color.parseColor("#C62828")
                            canvas.drawText("Expense", 440f, y, paint)
                        } else {
                            boldPaint.color = Color.parseColor("#2E7D32")
                            canvas.drawText("Income", 440f, y, paint)
                        }

                        canvas.drawText("₹${String.format(Locale.US, "%.2f", item.amount)}", 500f, y, boldPaint)
                        y += 18f
                    }
                }

                pdfDocument.finishPage(page)

                // Save PDF to cache directory to easily share via FileProvider
                val cacheDir = File(context.cacheDir, "reports")
                if (!cacheDir.exists()) cacheDir.mkdirs()
                val pdfFile = File(cacheDir, "Finance_Report_${System.currentTimeMillis()}.pdf")
                val fos = FileOutputStream(pdfFile)
                pdfDocument.writeTo(fos)
                pdfDocument.close()
                fos.close()

                // Expose Uri through FileProvider
                val fileUri = FileProvider.getUriForFile(context, "com.example.fileprovider", pdfFile)
                _pdfShareUri.emit(fileUri)

            } catch (e: Exception) {
                Log.e("PdfGeneration", "Failed compiling report", e)
            }
        }
    }

    // Backup Database content to JSON structure (integrated with cloud storage backup & restore)
    fun backupDataToJSONString(): String {
        return try {
            val transactions = allTransactions.value
            val limits = allBudgetLimits.value

            val backupObject = JSONObject()

            // Map transactions
            val txArray = JSONArray()
            for (tx in transactions) {
                val obj = JSONObject().apply {
                    put("amount", tx.amount)
                    put("title", tx.title)
                    put("category", tx.category)
                    put("timestamp", tx.timestamp)
                    put("isExpense", tx.isExpense)
                    put("notes", tx.notes)
                }
                txArray.put(obj)
            }
            backupObject.put("transactions", txArray)

            // Map budget limits
            val limitsArray = JSONArray()
            for (limit in limits) {
                val obj = JSONObject().apply {
                    put("category", limit.category)
                    put("limitAmount", limit.limitAmount)
                }
                limitsArray.put(obj)
            }
            backupObject.put("limits", limitsArray)

            backupObject.toString()
        } catch (e: Exception) {
            ""
        }
    }

    // Restore Database content from JSON (integrated with cloud storage backup & restore)
    fun restoreDataFromJSON(jsonString: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backupObject = JSONObject(jsonString)

                val database = AppDatabase.getDatabase(getApplication())
                val dao = database.expenseDao()

                // Parse budget limits
                if (backupObject.has("limits")) {
                    val limitsArray = backupObject.getJSONArray("limits")
                    for (i in 0 until limitsArray.length()) {
                        val limitObj = limitsArray.getJSONObject(i)
                        val category = limitObj.getString("category")
                        val amount = limitObj.getDouble("limitAmount")
                        dao.insertBudgetLimit(BudgetLimit(category, amount))
                    }
                }

                // Parse transactions
                if (backupObject.has("transactions")) {
                    val txArray = backupObject.getJSONArray("transactions")
                    for (i in 0 until txArray.length()) {
                        val txObj = txArray.getJSONObject(i)
                        val amount = txObj.getDouble("amount")
                        val title = txObj.getString("title")
                        val category = txObj.getString("category")
                        val timestamp = txObj.getLong("timestamp")
                        val isExpense = txObj.getBoolean("isExpense")
                        val notes = txObj.optString("notes", "")

                        dao.insertTransaction(
                            Transaction(
                                amount = amount,
                                title = title,
                                category = category,
                                timestamp = timestamp,
                                isExpense = isExpense,
                                notes = notes
                            )
                        )
                    }
                }

                _backupStatusMessage.emit("SUCCESS: Safely imported backup records successfully!")
            } catch (e: Exception) {
                Log.e("BackupRestore", "Parsing error", e)
                _backupStatusMessage.emit("ERROR: Invalid backup file format or corrupted payload.")
            }
        }
    }
}

data class QuickLogData(
    val amount: Double,
    val appName: String,
    val merchant: String
)
