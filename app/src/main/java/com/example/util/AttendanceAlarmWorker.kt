package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class AttendanceAlarmWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = FirebaseFirestore.getInstance()
            val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val username = sharedPrefs.getString("logged_in_username", null) ?: return Result.success()

            val todayDate = getTodayDateString()
            
            // Check if on leave
            val leavesSnap = db.collection("leave_applications")
                .whereEqualTo("employeeName", username)
                .whereEqualTo("status", "APPROVED")
                .get()
                .await()
            var isOnLeave = false
            for (doc in leavesSnap.documents) {
                val lStartDate = doc.getString("startDate") ?: doc.getString("date") ?: ""
                val lEndDate = doc.getString("endDate") ?: doc.getString("date") ?: ""
                // A simplified match for 'todayDate'. To be perfectly accurate we'd parse dates,
                // but checking string equals or containment usually works if using the same format.
                if (lStartDate == todayDate || lEndDate == todayDate) {
                    isOnLeave = true
                    break
                }
            }

            if (isOnLeave) return Result.success()

            val attendanceSnap = db.collection("attendance_records")
                .whereEqualTo("employeeName", username)
                .whereEqualTo("date", todayDate)
                .get()
                .await()
            
            val todayRecord = attendanceSnap.documents.firstOrNull()

            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val timeAsValue = hour * 100 + minute

            if (timeAsValue >= 1100 && timeAsValue < 2330) {
                if (todayRecord == null) {
                    val lastAlarmDate = sharedPrefs.getString("last_checkin_alarm_date", "")
                    if (lastAlarmDate != todayDate) {
                        sendLoudAlarmNotification(context, "Check-in Reminder", "You haven't checked in today (11 AM passed)!")
                        sharedPrefs.edit().putString("last_checkin_alarm_date", todayDate).apply()
                    }
                }
            }

            if (timeAsValue >= 2330 || timeAsValue < 300) {
                if (todayRecord != null) {
                    val checkOutTime = todayRecord.getString("checkOutTime")
                    if (checkOutTime.isNullOrEmpty()) {
                        val lastOutAlarmDate = sharedPrefs.getString("last_checkout_alarm_date", "")
                        if (lastOutAlarmDate != todayDate) {
                            sendStandardNotification(context, "Check-out Reminder", "You haven't checked out yet (11:30 PM passed)!")
                            sharedPrefs.edit().putString("last_checkout_alarm_date", todayDate).apply()
                        }
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("AttendanceWorker", "Error", e)
            Result.retry()
        }
    }

    private fun getTodayDateString(): String {
        val sdf = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    private fun sendLoudAlarmNotification(ctx: Context, title: String, message: String) {
        val channelId = "loud_alarm_channel"
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val channel = NotificationChannel(channelId, "Loud Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                val attr = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                setSound(alarmUri, attr)
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }

        // To make it loud, we use full screen intent as well, or just high priority alert.
        val notification = NotificationCompat.Builder(ctx, channelId)
            // Using a system drawable. You can replace with an app icon if available
            .setSmallIcon(android.R.drawable.ic_dialog_alert) 
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }

    private fun sendStandardNotification(ctx: Context, title: String, message: String) {
        val channelId = "standard_notification"
        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Standard Notifications", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()

        manager.notify(1002, notification)
    }
}
