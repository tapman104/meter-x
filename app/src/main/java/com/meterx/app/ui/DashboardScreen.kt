package com.meterx.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meterx.core.network.DailyUsageEntry
import com.meterx.core.network.UsageHistoryRepository
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun DashboardScreen() {
    val context = LocalContext.current

    val repository = remember { UsageHistoryRepository(context) }
    var dayRolloverKey by remember { mutableIntStateOf(0) }

    // Refresh table data when the date rolls over while the screen stays open.
    LaunchedEffect(Unit) {
        while (true) {
            delay(millisUntilNextMidnight())
            dayRolloverKey++
        }
    }

    val dailyRows: List<DailyUsageEntry> = remember(dayRolloverKey) { repository.getHistory(30) }
    val summaryRows: List<DailyUsageEntry> = remember(dayRolloverKey) {
        listOf(
            repository.getAggregated(7).copy(date = "Last 7 days"),
            repository.getAggregated(30).copy(date = "Last 30 days"),
            repository.getThisMonth()
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Internet Meter",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))

        UsageHistoryTable(
            dailyRows = dailyRows,
            summaryRows = summaryRows
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun millisUntilNextMidnight(): Long {
    val now = Calendar.getInstance()
    val nextMidnight = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return (nextMidnight.timeInMillis - now.timeInMillis).coerceAtLeast(1L)
}

