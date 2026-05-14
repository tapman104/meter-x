package com.meterx.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meterx.core.network.DailyUsageEntry

/**
 * Backward-compatible `HorizontalDivider` shim.
 * This avoids the deprecated Material3 `Divider` API while staying compatible with the
 * Compose BOM version currently used by this project.
 */
@Composable
private fun HorizontalDividerCompat(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
    color: Color = DividerDefaults.color
) {
    Canvas(modifier.fillMaxWidth().height(thickness)) {
        val y = thickness.toPx() / 2f
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = thickness.toPx()
        )
    }
}

// ─── Formatting helper ────────────────────────────────────────────────────────

/** Converts bytes to a human-readable "x.xx MB" / "x.xx GB" string. */
internal fun Long.toReadableSize(): String {
    val mb = this / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        val gb = mb / 1024.0
        "%.2f GB".format(gb)
    } else {
        "%.0f MB".format(mb)
    }
}

// ─── Column header ────────────────────────────────────────────────────────────

@Composable
fun UsageHistoryHeader() {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(primary)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell(text = "Date", weight = 2f)
        HeaderCell(text = "Mobile", weight = 1.4f)
        HeaderCell(text = "WiFi", weight = 1.4f)
        HeaderCell(text = "Total", weight = 1.4f)
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center
    )
}

// ─── Regular data row ─────────────────────────────────────────────────────────

@Composable
fun UsageHistoryRow(entry: DailyUsageEntry, isEven: Boolean) {
    val rowBg = if (isEven)
        MaterialTheme.colorScheme.surfaceVariant
    else
        MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DataCell(text = entry.date, weight = 2f, bold = true)
        DataCell(text = entry.mobileBytes.toReadableSize(), weight = 1.4f)
        DataCell(text = entry.wifiBytes.toReadableSize(), weight = 1.4f)
        DataCell(text = entry.totalBytes.toReadableSize(), weight = 1.4f)
    }
}

// ─── Summary row (Last 7 days / This Month …) ────────────────────────────────

@Composable
fun UsageHistorySummaryRow(entry: DailyUsageEntry) {
    val bg = MaterialTheme.colorScheme.primaryContainer
    val fg = MaterialTheme.colorScheme.onPrimaryContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = entry.date,
            modifier = Modifier.weight(2f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            textAlign = TextAlign.Start
        )
        SummaryValueCell(text = entry.mobileBytes.toReadableSize(), weight = 1.4f, color = fg)
        SummaryValueCell(text = entry.wifiBytes.toReadableSize(), weight = 1.4f, color = fg)
        SummaryValueCell(text = entry.totalBytes.toReadableSize(), weight = 1.4f, color = fg)
    }
}

@Composable
private fun RowScope.SummaryValueCell(text: String, weight: Float, color: Color) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        textAlign = TextAlign.Center
    )
}

// ─── Shared cell helper ───────────────────────────────────────────────────────

@Composable
private fun RowScope.DataCell(text: String, weight: Float, bold: Boolean = false) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center
    )
}

// ─── Divider ──────────────────────────────────────────────────────────────────

@Composable
fun UsageHistoryDivider() {
    HorizontalDividerCompat(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp
    )
}

// ─── Top-level table ──────────────────────────────────────────────────────────

/**
 * Scrollable usage-history table.
 *
 * @param dailyRows      Per-day entries (most-recent first, up to 30 days).
 * @param summaryRows    Aggregated rows shown below the divider (e.g. last-7-days, this-month).
 * @param modifier       Optional outer modifier.
 */
@Composable
fun UsageHistoryTable(
    dailyRows: List<DailyUsageEntry>,
    summaryRows: List<DailyUsageEntry>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // Title
            Text(
                text = "Usage History",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            UsageHistoryHeader()

            // Daily rows — wrapped in a fixed-height lazy list so it doesn't
            // conflict with the outer scroll of DashboardScreen.
            val rowHeight = 36.dp
            val maxVisibleRows = 10
            val listHeight = rowHeight * minOf(dailyRows.size, maxVisibleRows)

            LazyColumn(modifier = Modifier.height(listHeight)) {
                itemsIndexed(dailyRows) { index, entry ->
                    UsageHistoryRow(entry = entry, isEven = index % 2 == 0)
                    if (index < dailyRows.lastIndex) UsageHistoryDivider()
                }
            }

            // Summary rows
            if (summaryRows.isNotEmpty()) {
                HorizontalDividerCompat(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
                summaryRows.forEach { entry ->
                    UsageHistorySummaryRow(entry = entry)
                    UsageHistoryDivider()
                }
            }
        }
    }
}
