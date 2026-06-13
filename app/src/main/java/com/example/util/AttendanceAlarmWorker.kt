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
            com.example.data.repository.FirebaseSyncManager.initialize(context)
            val db = FirebaseFirestore.getInstance()
            val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

            // 9:00 AM Daily leaves and weekoffs posting
            val cal = Calendar.getInstance()
            val hr = cal.get(Calendar.HOUR_OF_DAY)
            if (hr >= 9) {
                val sdfYmd = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val todayYmd = sdfYmd.format(java.util.Date())
                val lastLeavesPostDate = sharedPrefs.getString("last_leaves_webhook_posted_date", "")
                if (lastLeavesPostDate != todayYmd) {
                    try {
                        val galleryPrefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
                        val urlStr = galleryPrefs.getString("whatsapp_webhook_url", "") ?: ""
                        val enabled = galleryPrefs.getBoolean("whatsapp_enable", false)
                        val sparePhoneEnabled = galleryPrefs.getBoolean("whatsapp_spare_phone_enable", false)
                        
                        if ((enabled && urlStr.isNotBlank()) || sparePhoneEnabled) {
                            val leavesCol = db.collection("leave_applications")
                                .get()
                                .await()
                            
                            val approvedToday = mutableListOf<String>()
                            for (doc in leavesCol.documents) {
                                val sDate = doc.getString("startDateString") ?: doc.getString("startDate") ?: ""
                                val eDate = doc.getString("endDateString") ?: doc.getString("endDate") ?: ""
                                val empName = doc.getString("userName") ?: doc.getString("employeeName") ?: ""
                                val type = doc.getString("leaveType") ?: "Leave"
                                val statusVal = doc.getString("status") ?: ""
                                
                                if (statusVal.equals("Approved", ignoreCase = true) && 
                                    todayYmd >= sDate && todayYmd <= eDate && empName.isNotBlank()
                                ) {
                                    approvedToday.add("• $empName ($type)")
                                }
                            }
                            
                            val isSunday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
                            val isSaturday = cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                            
                            val weekoffs = mutableListOf<String>()
                            if (isSunday) {
                                weekoffs.add("• Sunday Weekly Off (All staff)")
                            } else if (isSaturday) {
                                weekoffs.add("• Saturday (Half day/Off for some groups)")
                            } else {
                                weekoffs.add("• Regular Working Day")
                            }
                            
                            val leavesText = if (approvedToday.isNotEmpty()) {
                                approvedToday.joinToString("\n")
                            } else {
                                "• No approved leaves today"
                            }
                            
                            val weekoffsText = weekoffs.joinToString("\n")
                            
                            val message = """
                                *Daily Leaves & Weekoffs*
                                📅 *Date:* $todayYmd
                                
                                🌴 *Approved Leaves:*
                                $leavesText
                                
                                ℹ *Weekoffs:*
                                $weekoffsText
                            """.trimIndent()
                            
                            if (sparePhoneEnabled) {
                                Log.d("AttendanceWorker", "Spare phone automation active: posting daily summary notification")
                                com.example.util.AppUtils.postWhatsAppAutomationNotification(
                                    context,
                                    "[WhatsApp Automation] Daily Summary",
                                    message
                                )
                            }
                            
                            if (enabled && urlStr.isNotBlank()) {
                                val url = java.net.URL(urlStr)
                                val conn = url.openConnection() as java.net.HttpURLConnection
                                conn.requestMethod = "POST"
                                conn.connectTimeout = 5000
                                conn.readTimeout = 5000
                                conn.doOutput = true
                                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                                
                                val escapedMessage = message.replace("\n", "\\n").replace("\"", "\\\"")
                                val json = """
                                    {
                                        "text": "$escapedMessage",
                                        "message": "$escapedMessage",
                                        "date": "$todayYmd",
                                        "type": "daily_summary"
                                    }
                                """.trimIndent()
                                
                                conn.outputStream.use { os ->
                                    val input = json.toByteArray(Charsets.UTF_8)
                                    os.write(input, 0, input.size)
                                }
                                val code = conn.responseCode
                                Log.d("AttendanceWorker", "Daily 9:00am leaves webhook push completed with code: $code")
                                conn.disconnect()
                            }
                        }
                        
                        sharedPrefs.edit().putString("last_leaves_webhook_posted_date", todayYmd).apply()
                    } catch (e: Exception) {
                        Log.e("AttendanceWorker", "Failed daily leaves webhook dispatch", e)
                    }
                }
            }

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
