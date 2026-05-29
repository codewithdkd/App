package com.example.ui.screens

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BudgetLimit
import com.example.ui.ExpenseViewModel
import com.example.ui.QuickLogData
import com.example.data.Transaction
import java.text.SimpleDateFormat
import java.util.*

enum class AppTab(val title: String) {
    Dashboard("Dashboard"),
    Limits("Limits"),
    Ledger("Ledger"),
    Analytics("Backup & Reports")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: ExpenseViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(AppTab.Dashboard) }
    
    // Core database states
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val pendingNotifications by viewModel.pendingNotifications.collectAsStateWithLifecycle()
    val budgetLimits by viewModel.allBudgetLimits.collectAsStateWithLifecycle()

    // Dialog & Interaction control
    var showAddDialog by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var customStatusMsg by remember { mutableStateOf("") }
    
    // Check if notification permission is present
    var isNotificationServiceActive by remember { mutableStateOf(false) }
    
    // Trigger check periodically and on resume
    LaunchedEffect(Unit) {
        isNotificationServiceActive = checkNotificationServiceEnabled(context)
    }

    // Capture share updates
    LaunchedEffect(viewModel.pdfShareUri) {
        viewModel.pdfShareUri.collect { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PDF Statement Report"))
        }
    }

    // Capture backup results
    LaunchedEffect(viewModel.backupStatusMessage) {
        viewModel.backupStatusMessage.collect { msg ->
            customStatusMsg = msg
        }
    }

    // Check if redirect quick log data is present from system tray trigger
    val quickLogData by viewModel.intentQuickLogData.collectAsStateWithLifecycle()

    // File launcher for Backing up SAF File
    val createBackupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val jsonPayload = viewModel.backupDataToJSONString()
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonPayload.toByteArray())
                }
                customStatusMsg = "Backup file safely saved to selected Cloud Storage location!"
            } catch (e: Exception) {
                customStatusMsg = "Backup export failed: ${e.localizedMessage}"
            }
        }
    }

    // File launcher for Importing SAF Backup File
    val openBackupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val payload = inputStream.bufferedReader().use { it.readText() }
                    viewModel.restoreDataFromJSON(payload)
                }
            } catch (e: Exception) {
                customStatusMsg = "Backup recovery failed: ${e.localizedMessage}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Finance Tracker",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    // Quick Status Indicator
                    IconButton(
                        onClick = {
                            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            imageVector = if (isNotificationServiceActive) Icons.Filled.NotificationsActive else Icons.Default.NotificationsOff,
                            contentDescription = "Sync Notifications",
                            tint = if (isNotificationServiceActive) Color(0xFF00E676) else MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            ) {
                AppTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    val tabIcon = when (tab) {
                        AppTab.Dashboard -> if (isSelected) Icons.Filled.Dashboard else Icons.Outlined.Dashboard
                        AppTab.Limits -> if (isSelected) Icons.Filled.DisplaySettings else Icons.Outlined.DisplaySettings
                        AppTab.Ledger -> if (isSelected) Icons.Filled.ReceiptLong else Icons.Outlined.ReceiptLong
                        AppTab.Analytics -> if (isSelected) Icons.Filled.CloudUpload else Icons.Outlined.CloudUpload
                    }
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = { Icon(tabIcon, contentDescription = tab.title) },
                        label = { Text(tab.title, fontWeight = FontWeight.Medium) },
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentTab != AppTab.Analytics) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.testTag("add_expense_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Quick Add Transaction")
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Warning Notification Access setup segment
                if (!isNotificationServiceActive) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning Notification Permissions Required",
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Automated UPI Logging is Off",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Access is required to automatically capture payment messages in real-time.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Button(
                                onClick = {
                                    val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                    context.startActivity(intent)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                                    contentColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Sub-screen rendering
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (currentTab) {
                        AppTab.Dashboard -> {
                            DashboardScreen(
                                transactions = transactions,
                                pendingNotifications = pendingNotifications,
                                budgetLimits = budgetLimits,
                                onLogNotification = { notif ->
                                    viewModel.approveAndLogNotification(
                                        notificationId = notif.id,
                                        amount = notif.amount,
                                        title = notif.sender,
                                        category = "Others",
                                        notes = notif.text
                                    )
                                },
                                onDismissNotification = { viewModel.dismissNotification(it.id) },
                                onEditTransaction = { transactionToEdit = it },
                                onDeleteTransaction = { viewModel.deleteTransaction(it) }
                            )
                        }
                        AppTab.Limits -> {
                            LimitsScreen(
                                transactions = transactions,
                                budgetLimits = budgetLimits,
                                onSaveLimit = { category, limit ->
                                    viewModel.updateBudgetLimit(category, limit)
                                }
                            )
                        }
                        AppTab.Ledger -> {
                            LedgerScreen(
                                transactions = transactions,
                                selectedMonth = viewModel.selectedMonth.collectAsStateWithLifecycle().value,
                                selectedYear = viewModel.selectedYear.collectAsStateWithLifecycle().value,
                                onMonthSelected = { viewModel.selectMonth(it) },
                                onYearSelected = { viewModel.selectYear(it) },
                                onEditTransaction = { transactionToEdit = it },
                                onDeleteTransaction = { viewModel.deleteTransaction(it) }
                            )
                        }
                        AppTab.Analytics -> {
                            BackupReportsScreen(
                                viewModel = viewModel,
                                transactions = transactions,
                                budgetLimits = budgetLimits,
                                onCreateBackupFile = { createBackupFileLauncher.launch("Finance_Tracker_Backup.json") },
                                onOpenBackupFile = { openBackupFileLauncher.launch(arrayOf("application/json")) }
                            )
                        }
                    }
                }
            }

            // Quick Status Snackbar popup notification simulation for manual interaction
            if (customStatusMsg.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 100.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = customStatusMsg,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { customStatusMsg = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Status indicator",
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }

            // Standard Add / Edit transaction dialog
            if (showAddDialog || transactionToEdit != null) {
                AddEditTransactionDialog(
                    transaction = transactionToEdit,
                    onDismiss = {
                        showAddDialog = false
                        transactionToEdit = null
                    },
                    onSave = { amount, title, category, isExpense, timestamp, notes ->
                        if (transactionToEdit != null) {
                            viewModel.updateTransaction(
                                transactionToEdit!!.copy(
                                    amount = amount,
                                    title = title,
                                    category = category,
                                    isExpense = isExpense,
                                    timestamp = timestamp,
                                    notes = notes
                                )
                            )
                        } else {
                            viewModel.addTransaction(amount, title, category, isExpense, timestamp, notes)
                        }
                        showAddDialog = false
                        transactionToEdit = null
                    }
                )
            }

            // External tray Trigger quick alert popup
            if (quickLogData != null) {
                QuickActionNotificationDialog(
                    data = quickLogData!!,
                    onDismiss = { viewModel.clearQuickLogIntentData() },
                    onApprove = { category, notes ->
                        viewModel.addTransaction(
                            amount = quickLogData!!.amount,
                            title = "UPI via ${quickLogData!!.appName}",
                            category = category,
                            isExpense = true,
                            timestamp = System.currentTimeMillis(),
                            notes = notes.ifBlank { "Auto-parsed: ${quickLogData!!.merchant}" }
                        )
                        viewModel.clearQuickLogIntentData()
                    }
                )
            }
        }
    }
}

private fun checkNotificationServiceEnabled(context: Context): Boolean {
    val cn = ComponentName(context, "com.example.service.TransactionNotificationListener")
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(cn.flattenToString())
}


@Composable
fun DashboardScreen(
    transactions: List<Transaction>,
    pendingNotifications: List<com.example.data.DetectedNotification>,
    budgetLimits: List<BudgetLimit>,
    onLogNotification: (com.example.data.DetectedNotification) -> Unit,
    onDismissNotification: (com.example.data.DetectedNotification) -> Unit,
    onEditTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Int) -> Unit
) {
    val cal = Calendar.getInstance()
    val thisMonth = cal.get(Calendar.MONTH)
    val thisYear = cal.get(Calendar.YEAR)

    // Spends metrics for current active month
    val currentMonthTransactions = transactions.filter {
        val tCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
        tCal.get(Calendar.MONTH) == thisMonth && tCal.get(Calendar.YEAR) == thisYear
    }

    val totalIncome = currentMonthTransactions.filter { !it.isExpense }.sumOf { it.amount }
    val totalExpense = currentMonthTransactions.filter { it.isExpense }.sumOf { it.amount }
    val totalBudgetLimits = budgetLimits.sumOf { it.limitAmount }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag("dashboard_scroller")
    ) {
        // Core monthly progress wheel
        item {
            Card(
                elevation = CardDefaults.cardElevation(2.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Current Month Summary",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(170.dp)
                    ) {
                        val progressFraction = if (totalBudgetLimits > 0f) {
                            (totalExpense / totalBudgetLimits).coerceIn(0.0, 1.0).toFloat()
                        } else {
                            0f
                        }

                        val trackColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
                        val arcColor = if (totalExpense > totalBudgetLimits && totalBudgetLimits > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }

                        Canvas(modifier = Modifier.size(150.dp)) {
                            drawArc(
                                color = trackColor,
                                startAngle = -220f,
                                sweepAngle = 260f,
                                useCenter = false,
                                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = arcColor,
                                startAngle = -220f,
                                sweepAngle = progressFraction * 260f,
                                useCenter = false,
                                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Spent",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "₹${String.format(Locale.getDefault(), "%,.0f", totalExpense)}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (totalBudgetLimits > 0) {
                                Text(
                                    text = "Limit: ₹${String.format(Locale.getDefault(), "%,.0f", totalBudgetLimits)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            } else {
                                Text(
                                    text = "No limits configured",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Income", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                "₹${String.format(Locale.getDefault(), "%,.0f", totalIncome)}",
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Divider(
                            modifier = Modifier
                                .height(28.dp)
                                .width(1.dp)
                                .align(Alignment.CenterVertically)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Safe To Spend", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            val remaining = totalBudgetLimits - totalExpense
                            Text(
                                text = if (totalBudgetLimits > 0) {
                                    if (remaining >= 0) "₹${String.format(Locale.getDefault(), "%,.0f", remaining)}"
                                    else "Exceeded"
                                } else "₹${String.format(Locale.getDefault(), "%,.0f", totalIncome - totalExpense)}",
                                color = if (totalBudgetLimits > 0 && remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Notification Parser Tray
        if (pendingNotifications.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "Detected Transactions (${pendingNotifications.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    pendingNotifications.forEach { notif ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CurrencyExchange,
                                        contentDescription = "Parsed UPI Notif",
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            notif.sender,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                            Text(notif.appName, fontSize = 9.sp, color = Color.White)
                                        }
                                    }
                                    Text(
                                        notif.text,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1
                                    )
                                }
                                Column(
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        "₹${notif.amount}",
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row {
                                        IconButton(
                                            onClick = { onDismissNotification(notif) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Dismiss",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = { onLogNotification(notif) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text("Log", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Entries List
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Ledger Activity",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        if (transactions.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = "Empty tracker entries",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Your finance tracker is empty!",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Get started by adding manual transactions or syncing incoming payment notifications.",
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        } else {
            val recents = transactions.take(5)
            items(recents) { item ->
                TransactionListItem(
                    transaction = item,
                    onEdit = { onEditTransaction(item) },
                    onDelete = { onDeleteTransaction(item.id) }
                )
            }
        }
    }
}

@Composable
fun TransactionListItem(
    transaction: Transaction,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val iconPack = getCategoryIcon(transaction.category)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (transaction.isExpense) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                            } else {
                                Color(0xFFE8F5E9)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconPack,
                        contentDescription = transaction.category,
                        tint = if (transaction.isExpense) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color(0xFF2E7D32)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = transaction.category,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    val formattedPrice = String.format(Locale.getDefault(), "%,.2f", transaction.amount)
                    Text(
                        text = if (transaction.isExpense) "- ₹$formattedPrice" else "+ ₹$formattedPrice",
                        fontWeight = FontWeight.Bold,
                        color = if (transaction.isExpense) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                        fontSize = 14.sp
                    )

                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    Text(
                        text = sdf.format(Date(transaction.timestamp)),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(10.dp))
                if (transaction.notes.isNotEmpty()) {
                    Text(
                        text = "Notes: ${transaction.notes}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Transaction record",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Transaction record",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LimitsScreen(
    transactions: List<Transaction>,
    budgetLimits: List<BudgetLimit>,
    onSaveLimit: (String, Double) -> Unit
) {
    val categories = listOf("Food & Dining", "Shopping", "Transportation", "Entertainment", "Bills & Utilities", "Housing & Rent", "Salary & Profits", "Others")
    
    val cal = Calendar.getInstance()
    val spentGroup = transactions
        .filter {
            val tCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            it.isExpense && tCal.get(Calendar.MONTH) == cal.get(Calendar.MONTH) && tCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
        }
        .groupBy { it.category }

    var selectedCategoryForLimit by remember { mutableStateOf("") }
    var inputLimitAmount by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Configure Limits & Budgets",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Secure spending controls on key expenses. Set a budget limit for each category. Excess spending creates warning alerts.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // List of configured limits vs spent progress
        items(categories) { category ->
            val limit = budgetLimits.find { it.category.equals(category, ignoreCase = true) }?.limitAmount ?: 0.0
            val spent = spentGroup[category]?.sumOf { it.amount } ?: 0.0

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = getCategoryIcon(category),
                            contentDescription = category,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = category,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Set Limit triggers Configuration Panel
                        Button(
                            onClick = {
                                selectedCategoryForLimit = category
                                inputLimitAmount = if (limit > 0) limit.toInt().toString() else ""
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (limit > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                                contentColor = if (limit > 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(if (limit > 0) "Update Limit" else "Set Limit", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Spent ₹${spent.toInt()} of ${if (limit > 0) "₹${limit.toInt()}" else "No limit"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        
                        if (limit > 0) {
                            val ratio = spent / limit
                            val percentage = (ratio * 100).toInt()
                            Text(
                                text = "$percentage%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (ratio > 1.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (limit > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val progressFraction = (spent / limit).coerceIn(0.0, 1.0).toFloat()
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            color = if (spent > limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceColorAtElevation(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }

    // Modal dialog for entering limits
    if (selectedCategoryForLimit.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { selectedCategoryForLimit = "" },
            title = { Text("Budget Limit for $selectedCategoryForLimit") },
            text = {
                Column {
                    Text(
                        "Set maximum monthly expenditure. Enter 0 to remove budget constraints.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = inputLimitAmount,
                        onValueChange = { inputLimitAmount = it },
                        label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = inputLimitAmount.toDoubleOrNull() ?: 0.0
                        onSaveLimit(selectedCategoryForLimit, amount)
                        selectedCategoryForLimit = ""
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedCategoryForLimit = "" }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LedgerScreen(
    transactions: List<Transaction>,
    selectedMonth: Int,
    selectedYear: Int,
    onMonthSelected: (Int) -> Unit,
    onYearSelected: (Int) -> Unit,
    onEditTransaction: (Transaction) -> Unit,
    onDeleteTransaction: (Int) -> Unit
) {
    val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val years = (2020..2030).toList()

    var showMonthMenu by remember { mutableStateOf(false) }
    var showYearMenu by remember { mutableStateOf(false) }

    // Filtered transaction dataset
    val filtered = transactions.filter {
        val tCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
        tCal.get(Calendar.MONTH) == selectedMonth && tCal.get(Calendar.YEAR) == selectedYear
    }

    val totalIncome = filtered.filter { !it.isExpense }.sumOf { it.amount }
    val totalExpense = filtered.filter { it.isExpense }.sumOf { it.amount }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Year & Month Selection controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Button(
                    onClick = { showMonthMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(months[selectedMonth])
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "dropdown")
                }
                DropdownMenu(
                    expanded = showMonthMenu,
                    onDismissRequest = { showMonthMenu = false }
                ) {
                    months.forEachIndexed { idx, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                onMonthSelected(idx)
                                showMonthMenu = false
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                Button(
                    onClick = { showYearMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(selectedYear.toString())
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "dropdown")
                }
                DropdownMenu(
                    expanded = showYearMenu,
                    onDismissRequest = { showYearMenu = false }
                ) {
                    years.forEach { yearVal ->
                        DropdownMenuItem(
                            text = { Text(yearVal.toString()) },
                            onClick = {
                                onYearSelected(yearVal)
                                showYearMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Balance Ribbon Area
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Month Spends", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text(
                        "₹${String.format(Locale.getDefault(), "%,.1f", totalExpense)}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 15.sp
                    )
                }
                Divider(
                    modifier = Modifier
                        .height(30.dp)
                        .width(1.dp)
                        .align(Alignment.CenterVertically)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Month Income", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    Text(
                        "₹${String.format(Locale.getDefault(), "%,.1f", totalIncome)}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676),
                        fontSize = 15.sp
                    )
                }
                Divider(
                    modifier = Modifier
                        .height(30.dp)
                        .width(1.dp)
                        .align(Alignment.CenterVertically)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Net Saving", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    val net = totalIncome - totalExpense
                    Text(
                        "₹${String.format(Locale.getDefault(), "%,.1f", net)}",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (net >= 0) Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Ledger scroller grouped by dates
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.EventNote,
                        contentDescription = "No ledger entries in current selection",
                        modifier = Modifier.size(54.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No transactions logged in current selection.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            // Group transactions by Date
            val grouped = filtered.groupBy {
                val calTemp = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                val format = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.getDefault())
                format.format(calTemp.time)
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                grouped.forEach { (dateStr, itemsList) ->
                    item {
                        Text(
                            text = dateStr,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(itemsList) { item ->
                        TransactionListItem(
                            transaction = item,
                            onEdit = { onEditTransaction(item) },
                            onDelete = { onDeleteTransaction(item.id) }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun BackupReportsScreen(
    viewModel: ExpenseViewModel,
    transactions: List<Transaction>,
    budgetLimits: List<BudgetLimit>,
    onCreateBackupFile: () -> Unit,
    onOpenBackupFile: () -> Unit
) {
    val context = LocalContext.current

    val cal = Calendar.getInstance()
    var selectedReportRange by remember { mutableStateOf("month") } // "month" or "year" or "all"

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Visual Charting Canvas Segment
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Expense Category Breakdown",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    val currentMonthTxs = transactions.filter {
                        val tCal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
                        it.isExpense && tCal.get(Calendar.MONTH) == cal.get(Calendar.MONTH) && tCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
                    }

                    if (currentMonthTxs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No active expenses this month to graph.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        val grouping = currentMonthTxs.groupBy { it.category }
                        val totals = grouping.mapValues { it.value.sumOf { tx -> tx.amount } }
                        val overTotal = totals.values.sum()

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Donut chart drawing
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(110.dp)
                            ) {
                                Canvas(modifier = Modifier.size(100.dp)) {
                                    var currentAngle = 0f
                                    totals.entries.forEachIndexed { index, entry ->
                                        val factionDegrees = (entry.value / overTotal).toFloat() * 360f
                                        val sliceColor = getChartColorByIndex(index)
                                        drawArc(
                                            color = sliceColor,
                                            startAngle = currentAngle,
                                            sweepAngle = factionDegrees,
                                            useCenter = false,
                                            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                                        )
                                        currentAngle += factionDegrees
                                    }
                                }
                                Text(
                                    text = "Monthly",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            // Chart Legend
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                totals.entries.take(5).forEachIndexed { idx, entry ->
                                    val pct = (entry.value / overTotal * 100).toInt()
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(getChartColorByIndex(idx), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${entry.key} ($pct%)",
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // PDF Generation and Sharing Section
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Export Detailed Budget Analysis PDF",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Generates a polished financial ledger formatted with budget evaluations, totals, and analysis tables. Easily shared via Email or active social integrations.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    // Triple range target segment selectors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("month" to "Selected Month", "year" to "Selected Year", "all" to "All-time").forEach { (v, l) ->
                            val s = selectedReportRange == v
                            FilterChip(
                                selected = s,
                                onClick = { selectedReportRange = v },
                                label = { Text(l, fontSize = 11.sp) },
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { viewModel.generatePdfReport(context, selectedReportRange) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_pdf_button")
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = "PDF icon")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Statement / Send PDF Ledger")
                    }
                }
            }
        }

        // Automatic/Manual Backups and Cloud synchronization
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Backup & Recovery Configurations",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        "Synchronize data with target files stored on your Google Drive, Dropbox or local directory. Safe local state migration is fully integrated.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onCreateBackupFile,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.CloudDownload, contentDescription = "Sync Cloud")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Backup", fontSize = 11.sp)
                        }

                        Button(
                            onClick = onOpenBackupFile,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = "Restore Cloud")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import Backup", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun AddEditTransactionDialog(
    transaction: Transaction?,
    onDismiss: () -> Unit,
    onSave: (Double, String, String, Boolean, Long, String) -> Unit
) {
    var amount by remember { mutableStateOf(transaction?.amount?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var title by remember { mutableStateOf(transaction?.title ?: "") }
    var isExpense by remember { mutableStateOf(transaction?.isExpense ?: true) }
    var notes by remember { mutableStateOf(transaction?.notes ?: "") }
    var selectedCategory by remember { mutableStateOf(transaction?.category ?: "Food & Dining") }

    val categories = listOf("Food & Dining", "Shopping", "Transportation", "Entertainment", "Bills & Utilities", "Housing & Rent", "Salary & Profits", "Others")
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (transaction != null) Icons.Default.Edit else Icons.Default.AddCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (transaction != null) "Edit Transaction" else "Add Transaction",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Expense / Income toggle switch
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilterChip(
                            selected = isExpense,
                            onClick = { isExpense = true },
                            label = { Text("Expense", fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        FilterChip(
                            selected = !isExpense,
                            onClick = { isExpense = false },
                            label = { Text("Income", fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE8F5E9),
                                selectedLabelColor = Color(0xFF2E7D32)
                            )
                        )
                    }
                }

                // Amount Text Field
                item {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Transaction Value") },
                        placeholder = { Text("0.00") },
                        leadingIcon = {
                            Text(
                                text = "₹",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Description Title Text Field
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Description") },
                        placeholder = { Text("e.g. Groceries, salary, cafe") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Category selector drop-down box
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Spending Category") },
                            leadingIcon = {
                                Icon(
                                    imageVector = getCategoryIcon(selectedCategory),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { categoryExpanded = true }) {
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "expanded menu")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { categoryExpanded = true }
                        )
                        DropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    leadingIcon = { Icon(getCategoryIcon(cat), contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    text = { Text(cat) },
                                    onClick = {
                                        selectedCategory = cat
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Notes Field
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Additional Notes (Optional)") },
                        placeholder = { Text("e.g., split payment info, memo") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val value = amount.toDoubleOrNull() ?: 0.0
                    onSave(
                        value,
                        title,
                        selectedCategory,
                        isExpense,
                        transaction?.timestamp ?: System.currentTimeMillis(),
                        notes
                    )
                },
                enabled = amount.isNotBlank() && amount.toDoubleOrNull() != null
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun QuickActionNotificationDialog(
    data: QuickLogData,
    onDismiss: () -> Unit,
    onApprove: (String, String) -> Unit
) {
    var notes by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food & Dining") }

    val categories = listOf("Food & Dining", "Shopping", "Transportation", "Entertainment", "Bills & Utilities", "Housing & Rent", "Salary & Profits", "Others")
    var categoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New UPI Log Detected!") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Detected ₹${data.amount} payment",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = data.merchant,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                Text("Pick a category to save this transaction:", fontSize = 12.sp)

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = {
                            IconButton(onClick = { categoryExpanded = true }) {
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "dropdown menu")
                                }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { categoryExpanded = true }
                    )
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Quick Memo / Notes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApprove(selectedCategory, notes) }
            ) {
                Text("Quick Log")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

fun getCategoryIcon(category: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (category) {
        "Food & Dining" -> Icons.Outlined.Restaurant
        "Shopping" -> Icons.Outlined.ShoppingBag
        "Transportation" -> Icons.Outlined.DirectionsCar
        "Entertainment" -> Icons.Outlined.Movie
        "Bills & Utilities" -> Icons.Outlined.ReceiptLong
        "Housing & Rent" -> Icons.Outlined.Home
        "Salary & Profits" -> Icons.Outlined.MonetizationOn
        else -> Icons.Outlined.Category
    }
}

fun getChartColorByIndex(index: Int): Color {
    val colors = listOf(
        Color(0xFF1E88E5), // Blue
        Color(0xFFFFB300), // Gold
        Color(0xFFE53935), // Red
        Color(0xFF43A047), // Green
        Color(0xFF8E24AA), // Purple
        Color(0xFF00ACC1), // Cyan
        Color(0xFFD81B60), // Pink
        Color(0xFF5D4037)  // Brown
    )
    return colors[index % colors.size]
}
