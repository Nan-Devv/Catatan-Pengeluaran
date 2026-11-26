package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalDailySpendColors
import com.example.ui.viewmodel.CategorySpendSlice
import com.example.ui.viewmodel.ChartBucket
import com.example.utils.CurrencyFormatter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
fun InteractiveIncomeExpenseBarChart(
    buckets: List<ChartBucket>,
    currencyCode: String,
    modifier: Modifier = Modifier,
    showIncome: Boolean = true,
    showExpense: Boolean = true
) {
    val semantic = LocalDailySpendColors.current
    var selectedIndex by remember(buckets) { mutableIntStateOf(-1) }
    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(550),
        label = "barChartAnim"
    )

    if (buckets.isEmpty() || buckets.all { it.income == 0L && it.expense == 0L }) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No transactions recorded in this period yet",
                style = MaterialTheme.typography.bodyMedium,
                color = semantic.secondaryText
            )
        }
        return
    }

    val maxVal = buckets.maxOf {
        maxOf(if (showIncome) it.income else 0L, if (showExpense) it.expense else 0L)
    }.coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(visible = selectedIndex in buckets.indices) {
            if (selectedIndex in buckets.indices) {
                val b = buckets[selectedIndex]
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = b.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (showIncome) {
                                Text(
                                    text = "+${CurrencyFormatter.formatCompact(b.income, currencyCode)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = semantic.income,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (showExpense) {
                                Text(
                                    text = "-${CurrencyFormatter.formatCompact(b.expense, currencyCode)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = semantic.expense,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        val highlightColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(buckets) {
                    detectTapGestures { offset ->
                        if (buckets.isNotEmpty() && size.width > 0) {
                            val slotWidth = size.width.toFloat() / buckets.size
                            val idx = (offset.x / slotWidth).toInt().coerceIn(0, buckets.lastIndex)
                            selectedIndex = if (selectedIndex == idx) -1 else idx
                        }
                    }
                }
        ) {
            val h = size.height
            val w = size.width
            val slotW = w / buckets.size.coerceAtLeast(1)

            // Draw 3 subtle horizontal gridlines
            listOf(0.25f, 0.55f, 0.85f).forEach { frac ->
                drawLine(
                    color = gridColor,
                    start = Offset(0f, h * frac),
                    end = Offset(w, h * frac),
                    strokeWidth = 1.dp.toPx()
                )
            }

            buckets.forEachIndexed { idx, bucket ->
                val slotStart = idx * slotW
                if (idx == selectedIndex) {
                    drawRoundRect(
                        color = highlightColor,
                        topLeft = Offset(slotStart + 2.dp.toPx(), 0f),
                        size = Size((slotW - 4.dp.toPx()).coerceAtLeast(4f), h),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                }

                val barCount = (if (showIncome) 1 else 0) + (if (showExpense) 1 else 0)
                val barWidth = (slotW * 0.28f).coerceIn(5.dp.toPx(), 16.dp.toPx())
                val gap = 3.dp.toPx()
                val totalBarsW = barCount * barWidth + (barCount - 1).coerceAtLeast(0) * gap
                var curX = slotStart + (slotW - totalBarsW) / 2f

                if (showIncome) {
                    val ratio = (bucket.income.toFloat() / maxVal.toFloat()) * animProgress
                    val barH = (h * 0.88f * ratio).coerceAtLeast(if (bucket.income > 0) 4.dp.toPx() else 0f)
                    if (barH > 0f) {
                        drawRoundRect(
                            color = semantic.income,
                            topLeft = Offset(curX, h - barH),
                            size = Size(barWidth, barH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                    curX += barWidth + gap
                }

                if (showExpense) {
                    val ratio = (bucket.expense.toFloat() / maxVal.toFloat()) * animProgress
                    val barH = (h * 0.88f * ratio).coerceAtLeast(if (bucket.expense > 0) 4.dp.toPx() else 0f)
                    if (barH > 0f) {
                        drawRoundRect(
                            color = semantic.expense,
                            topLeft = Offset(curX, h - barH),
                            size = Size(barWidth, barH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val step = if (buckets.size > 7) (buckets.size / 6).coerceAtLeast(1) else 1
            buckets.forEachIndexed { idx, bucket ->
                val show = idx % step == 0 || idx == buckets.lastIndex
                Text(
                    text = if (show) bucket.label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (idx == selectedIndex) MaterialTheme.colorScheme.primary else semantic.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
            }
        }
    }
}

@Composable
fun InteractiveDonutChart(
    slices: List<CategorySpendSlice>,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    val semantic = LocalDailySpendColors.current
    var selectedSliceIndex by remember(slices) { mutableIntStateOf(-1) }
    val animSweep by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(650),
        label = "donutAnim"
    )

    if (slices.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(190.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No expense categories to display",
                style = MaterialTheme.typography.bodyMedium,
                color = semantic.secondaryText
            )
        }
        return
    }

    val total = slices.sumOf { it.amount }.coerceAtLeast(1L)
    val selected = slices.getOrNull(selectedSliceIndex)

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(slices) {
                        detectTapGestures { offset ->
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = offset.x - cx
                            val dy = offset.y - cy
                            val dist = hypot(dx, dy)
                            val radius = minOf(cx, cy)
                            if (dist in (radius * 0.4f)..(radius * 1.05f)) {
                                var angle = (atan2(dy, dx) * 180.0 / PI).toFloat() + 90f
                                if (angle < 0f) angle += 360f
                                var acc = 0f
                                var found = -1
                                slices.forEachIndexed { idx, s ->
                                    val sweep = (s.amount.toFloat() / total.toFloat()) * 360f
                                    if (angle in acc..(acc + sweep)) {
                                        found = idx
                                    }
                                    acc += sweep
                                }
                                selectedSliceIndex = if (selectedSliceIndex == found) -1 else found
                            } else {
                                selectedSliceIndex = -1
                            }
                        }
                    }
            ) {
                val strokeW = 28.dp.toPx()
                val inset = strokeW / 2f + 4.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                var startAngle = -90f

                slices.forEachIndexed { idx, slice ->
                    val sweep = (slice.amount.toFloat() / total.toFloat()) * 360f * animSweep
                    val isSelected = idx == selectedSliceIndex
                    drawArc(
                        color = Color(slice.colorHex),
                        startAngle = startAngle,
                        sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(
                            width = if (isSelected) strokeW + 6.dp.toPx() else strokeW,
                            cap = StrokeCap.Butt
                        )
                    )
                    startAngle += sweep
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = selected?.categoryName ?: "Total Spend",
                    style = MaterialTheme.typography.labelMedium,
                    color = semantic.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = CurrencyFormatter.formatCompact(selected?.amount ?: total, currencyCode),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (selected != null) {
                    Text(
                        text = "${(selected.percentage * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(selected.colorHex),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        slices.take(6).forEachIndexed { idx, slice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (idx == selectedSliceIndex) MaterialTheme.colorScheme.surfaceVariant
                        else Color.Transparent
                    )
                    .pointerInput(idx) {
                        detectTapGestures {
                            selectedSliceIndex = if (selectedSliceIndex == idx) -1 else idx
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(slice.colorHex))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = slice.categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${(slice.percentage * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = semantic.secondaryText,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Text(
                    text = CurrencyFormatter.format(slice.amount, currencyCode),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun DailySpendingSparklineChart(
    buckets: List<ChartBucket>,
    currencyCode: String,
    modifier: Modifier = Modifier
) {
    val semantic = LocalDailySpendColors.current
    var selectedIdx by remember(buckets) { mutableIntStateOf(-1) }

    if (buckets.isEmpty() || buckets.all { it.expense == 0L }) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No daily expenses in selected range",
                style = MaterialTheme.typography.bodyMedium,
                color = semantic.secondaryText
            )
        }
        return
    }

    val maxExp = buckets.maxOf { it.expense }.coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth()) {
        if (selectedIdx in buckets.indices) {
            val b = buckets[selectedIdx]
            Text(
                text = "${b.label}: ${CurrencyFormatter.format(b.expense, currencyCode)}",
                style = MaterialTheme.typography.labelLarge,
                color = semantic.expense,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        val primaryColor = MaterialTheme.colorScheme.primary
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .pointerInput(buckets) {
                    detectTapGestures { offset ->
                        if (buckets.isNotEmpty() && size.width > 0) {
                            val stepW = size.width.toFloat() / buckets.size.coerceAtLeast(1)
                            val idx = (offset.x / stepW).toInt().coerceIn(0, buckets.lastIndex)
                            selectedIdx = if (selectedIdx == idx) -1 else idx
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val stepX = if (buckets.size > 1) w / (buckets.size - 1) else w / 2f

            val points = buckets.mapIndexed { idx, b ->
                val x = if (buckets.size > 1) idx * stepX else w / 2f
                val y = h - ((b.expense.toFloat() / maxExp.toFloat()) * (h * 0.82f)) - 10.dp.toPx()
                Offset(x, y)
            }

            if (points.size >= 2) {
                val fillPath = Path().apply {
                    moveTo(points.first().x, h)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(points.last().x, h)
                    close()
                }
                drawPath(
                    path = fillPath,
                    color = semantic.expense.copy(alpha = 0.14f)
                )

                val linePath = Path().apply {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
                drawPath(
                    path = linePath,
                    color = semantic.expense,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            points.forEachIndexed { idx, pt ->
                drawCircle(
                    color = if (idx == selectedIdx) primaryColor else semantic.expense,
                    radius = if (idx == selectedIdx) 6.dp.toPx() else 3.5.dp.toPx(),
                    center = pt
                )
            }
        }
    }
}
