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

@Composable
fun DashboardScreen() {
    val context = LocalContext.current

    // Load history once per composition; refreshed on recomposition from outside.
    val repository = remember { UsageHistoryRepository(context) }
    val dailyRows: List<DailyUsageEntry> = remember { repository.getHistory(30) }
    val summaryRows: List<DailyUsageEntry> = remember {
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
        Spacer(modifier = Modifier.height(32.dp))

        Spacer(modifier = Modifier.height(24.dp))

        UsageHistoryTable(
            dailyRows = dailyRows,
            summaryRows = summaryRows
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}


