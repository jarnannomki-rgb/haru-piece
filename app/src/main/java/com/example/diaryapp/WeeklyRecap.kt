package com.example.diaryapp

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal fun startOfWeek(date: LocalDate): LocalDate =
    date.minusDays((date.dayOfWeek.value - 1).toLong())

@Composable
fun WeeklyRecapScreen(
    entries: List<DiaryEntry>,
    onClose: () -> Unit,
    anchorDate: LocalDate = LocalDate.now()
) {
    val monday = remember(anchorDate) { startOfWeek(anchorDate) }
    val days = remember(monday) { (0L..6L).map(monday::plusDays) }
    val entriesByDate = remember(entries) { entries.groupBy { it.date } }
    val initialDate = remember(entries, days) {
        days.lastOrNull { entriesByDate[it.format(DateFormatter)].orEmpty().isNotEmpty() } ?: anchorDate
    }
    var selectedDate by remember { mutableStateOf(initialDate) }
    val assembleProgress = remember { Animatable(0f) }
    val shortDate = remember { DateTimeFormatter.ofPattern("M.d") }
    val dayNames = listOf("월", "화", "수", "목", "금", "토", "일")

    LaunchedEffect(monday) {
        assembleProgress.snapTo(0f)
        assembleProgress.animateTo(7f, animationSpec = tween(durationMillis = 2_300))
    }
    BackHandler(onBack = onClose)

    AppScreen(
        title = "이번 주의 조각",
        subtitle = "${monday.format(shortDate)} - ${monday.plusDays(6).format(shortDate)}"
    ) {
        WhitePanel {
            Text(
                "하루씩 모인 조각이에요",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "날짜를 누르면 그날 남긴 기록과 사진을 볼 수 있어요.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 21.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                days.forEachIndexed { index, day ->
                    val key = day.format(DateFormatter)
                    val hasEntry = entriesByDate[key].orEmpty().isNotEmpty()
                    val selected = day == selectedDate
                    val reveal = (assembleProgress.value - index).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            dayNames[index],
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .graphicsLayer {
                                    alpha = 0.25f + 0.75f * reveal
                                    scaleX = 0.78f + 0.22f * reveal
                                    scaleY = 0.78f + 0.22f * reveal
                                    translationY = (1f - reveal) * 18f
                                }
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        selected -> MaterialTheme.colorScheme.primary
                                        hasEntry -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.background
                                    }
                                )
                                .clickable { selectedDate = day },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    day.dayOfMonth.toString(),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    if (hasEntry) "조각" else "",
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        val selectedEntries = entriesByDate[selectedDate.format(DateFormatter)].orEmpty()
        WhitePanel {
            Text(
                "${selectedDate.format(DateFormatter)} 기록",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (selectedEntries.isEmpty()) {
                Text(
                    "이날은 남겨진 조각이 없어요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            } else {
                selectedEntries.forEach { entry ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (entry.kind == "rest") {
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.background
                                }
                            )
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            entry.time,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            entry.text,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 23.sp
                        )
                        entry.photoUri?.let { uri ->
                            PhotoPreview(uri, Modifier.fillMaxWidth().height(190.dp))
                        }
                    }
                }
            }
        }

        Text(
            "${days.count { entriesByDate[it.format(DateFormatter)].orEmpty().isNotEmpty() }}일의 조각이 모였어요.",
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        )
        PrimaryButton("달력으로", onClick = onClose)
    }
}
