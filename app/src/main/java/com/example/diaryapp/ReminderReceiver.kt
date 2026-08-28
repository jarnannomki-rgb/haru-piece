package com.example.diaryapp

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Calendar

private const val REMINDER_CHANNEL_ID = "haru_piece_reminders_high"
private const val REMINDER_REQUEST_BASE = 41000
private const val MAX_REMINDER_COUNT = 64

data class ReminderSpec(
    val raw: String,
    val daysLabel: String,
    val hour: Int,
    val minute: Int
)

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            loadProfile(context)?.let { scheduleDiaryReminders(context, it.notifyTimes) }
            return
        }

        showDiaryReminder(context)
        val firedReminder = intent.getStringExtra("reminder")?.let(::normalizeReminderText)
        val profile = loadProfile(context) ?: return
        if (firedReminder != null && firedReminder.startsWith("안 함 ")) {
            val updatedProfile = profile.copy(
                notifyTimes = profile.notifyTimes.map(::normalizeReminderText).filter { it != firedReminder }
            )
            saveProfile(context, updatedProfile)
            scheduleDiaryReminders(context, updatedProfile.notifyTimes)
        } else {
            scheduleDiaryReminders(context, profile.notifyTimes)
        }
    }
}

fun scheduleDiaryReminders(context: Context, reminders: List<String>) {
    ensureReminderChannel(context)
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    cancelDiaryReminders(context, alarmManager)

    reminders.mapNotNull(::parseReminderSpec).take(MAX_REMINDER_COUNT).forEachIndexed { index, spec ->
        val triggerAt = nextReminderMillis(spec)
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("reminder", spec.raw)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_BASE + index,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }
}

private fun cancelDiaryReminders(context: Context, alarmManager: AlarmManager) {
    repeat(MAX_REMINDER_COUNT) { index ->
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REMINDER_REQUEST_BASE + index,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) alarmManager.cancel(pendingIntent)
    }
}

fun parseReminderSpec(raw: String): ReminderSpec? {
    val normalized = normalizeReminderText(raw)
    val timeText = normalized.substringAfterLast(' ', "")
    val daysText = normalized.substringBeforeLast(' ', "매일")
    val parts = timeText.split(':')
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull()?.coerceIn(0, 23) ?: return null
    val minute = parts[1].toIntOrNull()?.coerceIn(0, 59) ?: return null
    return ReminderSpec(normalized, daysText, hour, minute)
}

fun nextReminderMillis(spec: ReminderSpec, now: Calendar = Calendar.getInstance()): Long {
    val days = calendarDays(spec.daysLabel)
    repeat(8) { offset ->
        val candidate = now.clone() as Calendar
        candidate.add(Calendar.DAY_OF_YEAR, offset)
        candidate.set(Calendar.HOUR_OF_DAY, spec.hour)
        candidate.set(Calendar.MINUTE, spec.minute)
        candidate.set(Calendar.SECOND, 0)
        candidate.set(Calendar.MILLISECOND, 0)
        if (candidate.get(Calendar.DAY_OF_WEEK) in days && candidate.after(now)) {
            return candidate.timeInMillis
        }
    }
    val fallback = now.clone() as Calendar
    fallback.add(Calendar.DAY_OF_YEAR, 1)
    return fallback.timeInMillis
}

private fun calendarDays(label: String): Set<Int> {
    val all = setOf(Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY)
    if (label.contains("안 함")) return all
    if (label.contains("매일")) return all
    if (label.contains("평일")) return setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
    if (label.contains("주말")) return setOf(Calendar.SATURDAY, Calendar.SUNDAY)
    val result = mutableSetOf<Int>()
    if (label.contains("월")) result.add(Calendar.MONDAY)
    if (label.contains("화")) result.add(Calendar.TUESDAY)
    if (label.contains("수")) result.add(Calendar.WEDNESDAY)
    if (label.contains("목")) result.add(Calendar.THURSDAY)
    if (label.contains("금")) result.add(Calendar.FRIDAY)
    if (label.contains("토")) result.add(Calendar.SATURDAY)
    if (label.contains("일")) result.add(Calendar.SUNDAY)
    return result.ifEmpty { all }
}

private fun ensureReminderChannel(context: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channel = NotificationChannel(
        REMINDER_CHANNEL_ID,
        "하루조각 알림",
        NotificationManager.IMPORTANCE_HIGH
    )
    channel.enableVibration(true)
    notificationManager.createNotificationChannel(channel)
}
fun postDiaryReminderNow(context: Context) = showDiaryReminder(context)

private fun showDiaryReminder(context: Context) {
    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        return
    }

    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    ensureReminderChannel(context)

    val openIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("하루조각")
        .setContentText("오늘도 기억을 남겨볼까요?")
        .setContentIntent(openIntent)
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()

    notificationManager.notify(REMINDER_REQUEST_BASE, notification)
}
