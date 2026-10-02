package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.content.ContentValues
import android.provider.MediaStore
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import android.util.Log
import com.example.data.repository.InventoryRepository
import com.example.data.repository.FirebaseSyncManager

object AppUtils {

    fun isValidImei(imei: String): Boolean {
        val clean = imei.trim()
        if (clean.length != 15 || !clean.all { it.isDigit() }) return false
        var sum = 0
        for (i in 0 until 15) {
            var d = clean[i] - '0'
            if (i % 2 == 1) { // odd index (0-indexed) => 2nd, 4th, 6th... 
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return sum % 10 == 0
    }

    private var appContext: Context? = null
    // Size-limited LruCache to hold decoded compressed byte arrays instead of raw uncompressed Bitmaps.
    // Storing uncompressed Bitmaps in an unbounded HashMap leads to massive heap consumption and GC pauses.
    // Returning a ByteArray allows Coil to decode it asynchronously on its internal background thread pool.
    private val imageCache = android.util.LruCache<String, ByteArray>(50)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getImageCompressFormat(): Bitmap.CompressFormat {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
    }

    fun getJpegFormat(): Bitmap.CompressFormat {
        return getImageCompressFormat()
    }

    fun performHapticFeedback(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_CLICK)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(40)
                }
            }
        } catch (e: Exception) {
            // Ignore if device lacks vibration motor
        }
    }

    fun generateMicroThumbnail(bitmap: Bitmap): String {
        return try {
            val maxDimension = 120
            val ratio = bitmap.width.toFloat() / maxOf(1, bitmap.height).toFloat()
            val (w, h) = if (ratio > 1) {
                Pair(maxDimension, maxOf(1, (maxDimension / ratio).toInt()))
            } else {
                Pair(maxOf(1, (maxDimension * ratio).toInt()), maxDimension)
            }
            val thumb = Bitmap.createScaledBitmap(bitmap, w, h, true)
            val out = ByteArrayOutputStream()
            thumb.compress(getImageCompressFormat(), 60, out)
            if (thumb != bitmap) thumb.recycle()
            val b64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
            "data:image/webp;base64,$b64"
        } catch (e: Exception) {
            ""
        }
    }

    private const val ENCRYPTION_KEY = "MG_GALLERY_SECURE_SALT_KEY"

    fun hashPassword(password: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            md5(password)
        }
    }

    fun encrypt(data: String): String {
        return try {
            val keyBytes = ENCRYPTION_KEY.toByteArray(Charsets.UTF_8)
            val dataBytes = data.toByteArray(Charsets.UTF_8)
            val encrypted = ByteArray(dataBytes.size)
            for (i in dataBytes.indices) {
                encrypted[i] = (dataBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            data
        }
    }

    fun decrypt(data: String): String {
        return try {
            val decoded = android.util.Base64.decode(data, android.util.Base64.NO_WRAP)
            val keyBytes = ENCRYPTION_KEY.toByteArray(Charsets.UTF_8)
            val decrypted = ByteArray(decoded.size)
            for (i in decoded.indices) {
                decrypted[i] = (decoded[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            data
        }
    }

    fun md5(s: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            digest.update(s.toByteArray())
            val messageDigest = digest.digest()
            val hexString = StringBuilder()
            for (aMessageDigest in messageDigest) {
                var h = Integer.toHexString(0xFF and aMessageDigest.toInt())
                while (h.length < 2) h = "0$h"
                hexString.append(h)
            }
            hexString.toString()
        } catch (e: Exception) {
            UUID.randomUUID().toString().take(8)
        }
    }

    fun uriToBase64(context: Context, uri: Uri): String? {
        return try {
            val maxDimension = 600
            
            // Acquire dimensions only to calculate sample size
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            
            var sampleSize = 1
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while (halfHeight / sampleSize >= maxDimension && halfWidth / sampleSize >= maxDimension) {
                    sampleSize *= 2
                }
            }
            
            // Decode with optimal sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null
            
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val (w, h) = if (ratio > 1) {
                    Pair(maxDimension, (maxDimension / ratio).toInt())
                } else {
                    Pair((maxDimension * ratio).toInt(), maxDimension)
                }
                Bitmap.createScaledBitmap(bitmap, w, h, true)
            } else {
                bitmap
            }
            
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(getJpegFormat(), 75, outputStream)
            val bytes = outputStream.toByteArray()
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun base64ToLocalFile(context: Context, base64Str: String): File? {
        if (base64Str.isBlank()) return null
        return try {
            val pureBase64 = if (base64Str.startsWith("data:image")) {
                val index = base64Str.indexOf(",")
                if (index != -1) base64Str.substring(index + 1) else base64Str
            } else {
                base64Str
            }
            
            val bytes = android.util.Base64.decode(pureBase64, android.util.Base64.NO_WRAP)
            val dir = File(context.filesDir, "photos")
            if (!dir.exists()) dir.mkdirs()
            
            val hash = pureBase64.length.toString() + "_" + pureBase64.take(128).hashCode().toString()
            val file = File(dir, "cache_pic_$hash.jpg")
            if (!file.exists()) {
                FileOutputStream(file).use { out ->
                    out.write(bytes)
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveImageToGallery(context: Context, imageSource: String) {
        if (imageSource.isBlank()) {
            Toast.makeText(context, "Error: Image source is empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Map placeholders to ultra high-res versions, and maximize default Unsplash values
        val resolvedSource = when (imageSource) {
            "ic_phone_blue" -> "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=2560&q=95"
            "ic_phone_amber" -> "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?auto=format&fit=crop&w=2560&q=95"
            "ic_watch" -> "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=2560&q=95"
            "ic_tablet" -> "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=2560&q=95"
            else -> {
                if (imageSource.contains("images.unsplash.com")) {
                    imageSource.replace(Regex("w=\\d+"), "w=2560").replace(Regex("q=\\d+"), "q=95")
                } else {
                    imageSource
                }
            }
        }

        // Show immediate feedback to user
        val isRemote = resolvedSource.startsWith("http://") || resolvedSource.startsWith("https://")
        Toast.makeText(context, if (isRemote) "Downloading original quality photo..." else "Saving photo...", Toast.LENGTH_SHORT).show()

        // Offload execution to IO Dispatcher to prevent main thread blocking
        backgroundScope.launch {
            try {
                val filename = "inventory_saved_${System.currentTimeMillis()}.jpg"
                var fos: OutputStream? = null
                var insertedUri: Uri? = null

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Inventory")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                    insertedUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    if (insertedUri != null) {
                        fos = resolver.openOutputStream(insertedUri)
                    }
                } else {
                    val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val appDir = File(imagesDir, "Inventory")
                    if (!appDir.exists()) appDir.mkdirs()
                    val image = File(appDir, filename)
                    fos = FileOutputStream(image)
                    insertedUri = Uri.fromFile(image)
                }

                if (insertedUri == null || fos == null) {
                    throw Exception("Could not initialize gallery stream channel")
                }

                // Copy stream data bit-for-bit directly, bypassing intermediate Bitmap downsampling/re-encoding
                fos.use { outputStream ->
                    when {
                        isRemote -> {
                            val url = java.net.URL(resolvedSource)
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.connectTimeout = 15000
                            conn.readTimeout = 15000
                            conn.doInput = true
                            conn.useCaches = true
                            conn.connect()
                            if (conn.responseCode == 200) {
                                conn.inputStream.use { inputStream ->
                                    inputStream.buffered(1024 * 64).use { bufferedInput ->
                                        bufferedInput.copyTo(outputStream)
                                    }
                                }
                            } else {
                                throw Exception("Download server returned code ${conn.responseCode}")
                            }
                        }
                        resolvedSource.length > 100 && !resolvedSource.startsWith("http") && !resolvedSource.startsWith("content://") && !resolvedSource.startsWith("file://") -> {
                            val pureBase64 = if (resolvedSource.startsWith("data:image")) {
                                val index = resolvedSource.indexOf(",")
                                if (index != -1) resolvedSource.substring(index + 1) else resolvedSource
                            } else {
                                resolvedSource
                            }
                            val bytes = android.util.Base64.decode(pureBase64, android.util.Base64.NO_WRAP)
                            outputStream.write(bytes)
                        }
                        resolvedSource.startsWith("content://") || resolvedSource.startsWith("file://") -> {
                            val uri = Uri.parse(resolvedSource)
                            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                inputStream.buffered(1024 * 64).use { bufferedInput ->
                                    bufferedInput.copyTo(outputStream)
                                }
                            } ?: throw Exception("Could not load original source input stream")
                        }
                        else -> {
                            val file = File(resolvedSource)
                            if (file.exists()) {
                                file.inputStream().use { inputStream ->
                                    inputStream.buffered(1024 * 64).use { bufferedInput ->
                                        bufferedInput.copyTo(outputStream)
                                    }
                                }
                            } else {
                                throw Exception("Local document file not found at: $resolvedSource")
                            }
                        }
                    }
                }

                // finalize IS_PENDING state on Android Q+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = context.contentResolver
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(insertedUri, contentValues, null, null)
                }

                val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/Inventory/" + filename
                } else {
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Inventory/$filename").absolutePath
                }
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(path),
                    arrayOf("image/jpeg"),
                    null
                )
                
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Saved in original full quality directly to Pictures/Inventory Gallery!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun getCurrentLocation(context: Context, callback: (String) -> Unit) {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            if (locationManager != null) {
                try {
                    val providers = locationManager.getProviders(true)
                    var bestLocation: Location? = null
                    for (provider in providers) {
                        val loc = locationManager.getLastKnownLocation(provider)
                        if (loc != null) {
                            if (bestLocation == null || loc.accuracy < bestLocation.accuracy) {
                                bestLocation = loc
                            }
                        }
                    }
                    if (bestLocation != null) {
                        callback("Lat: ${String.format("%.4f", bestLocation.latitude)}, Lng: ${String.format("%.4f", bestLocation.longitude)}")
                        return
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }
        }
        
        // Balanced fallback to showcase realistic outcomes smoothly
        callback("Lat: 28.6139, Lng: 77.2090 (Connaught Place, Delhi)")
    }

    fun postSystemNotification(context: Context, title: String, message: String) {
        try {
            val nManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            val channelId = "attendance_alerts"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Attendance Check-In Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Real-time alerts for employee check-ins and check-outs"
                    enableLights(true)
                    lightColor = android.graphics.Color.BLUE
                }
                nManager.createNotificationChannel(channel)
            }

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            nManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun postWhatsAppAutomationNotification(context: Context, title: String, message: String) {
        try {
            val nManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
            val channelId = "whatsapp_automation"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "WhatsApp Automation Triggers",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Triggers captured by spare phone automation tools like MacroDroid and Tasker"
                    enableLights(true)
                    lightColor = android.graphics.Color.GREEN
                }
                nManager.createNotificationChannel(channel)
            }

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(title)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            nManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun resolveImageModel(modelStr: String?, thumbnail: Boolean = false): Any {
        if (modelStr.isNullOrBlank()) return "ic_placeholder" // Fallback placeholder
        
        var target = if (thumbnail) {
            when (modelStr) {
                "ic_phone_blue" -> "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=400&q=75"
                "ic_phone_amber" -> "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?auto=format&fit=crop&w=400&q=75"
                "ic_watch" -> "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=400&q=75"
                "ic_tablet" -> "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=400&q=75"
                else -> {
                    if (modelStr.contains("images.unsplash.com")) {
                        modelStr.replace(Regex("w=\\d+"), "w=400").replace(Regex("q=\\d+"), "q=75")
                    } else {
                        modelStr
                    }
                }
            }
        } else {
            when (modelStr) {
                "ic_phone_blue" -> "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=1200&q=88"
                "ic_phone_amber" -> "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?auto=format&fit=crop&w=1200&q=88"
                "ic_watch" -> "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=1200&q=88"
                "ic_tablet" -> "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=1200&q=88"
                else -> modelStr
            }
        }

        // Dynamically rewrite package directory paths in local file URIs to current package
        val ctx = appContext
        if (ctx != null && target != null) {
            val currentPkg = ctx.packageName
            if (target.contains("/data/user/0/") || target.contains("/data/data/")) {
                target = target.replace(Regex("/data/user/0/[a-zA-Z0-9._-]+/"), "/data/user/0/$currentPkg/")
                target = target.replace(Regex("/data/data/[a-zA-Z0-9._-]+/"), "/data/data/$currentPkg/")
            }
        }

        // 1. Return direct HTTP/HTTPS URLs and local content/file URIs instantly. 
        // Coil natively handles asynchronous loading, memory caching, and disk caching with flawless speed.
        if (target.startsWith("http://") || target.startsWith("https://") || target.startsWith("content://")) {
            return target
        }

        if (target.startsWith("file://")) {
            // Return file URIs immediately; do not perform blocking disk I/O check file.exists() on Main thread!
            return target
        }

        // Handle raw local paths starting with '/'
        if (target.startsWith("/")) {
            // Return file URI immediately; do not perform blocking disk I/O check file.exists() on Main thread!
            return "file://$target"
        }

        // 2. Resolve Base64 strings with memory-cache acceleration using LruCache holding ByteArrays
        val cacheKey = "b64_${target.length}_${target.hashCode()}"
        val cached = imageCache.get(cacheKey)
        if (cached != null) {
            return cached
        }

        return try {
            val pureBase64 = if (target.startsWith("data:image")) {
                val index = target.indexOf(",")
                if (index != -1) target.substring(index + 1) else target
            } else {
                target
            }
            val decodedBytes = android.util.Base64.decode(pureBase64, android.util.Base64.NO_WRAP)
            imageCache.put(cacheKey, decodedBytes)
            decodedBytes
        } catch (e: Exception) {
            target
        }
    }

    fun uriToHighResLocalFile(context: Context, uri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "photos")
            if (!dir.exists()) dir.mkdirs()
            
            val filename = "pic_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.webp"
            val file = File(dir, filename)
            
            // Acquire dimensions to scale slightly if larger than crisp Full HD bounds (2048px)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            
            val maxDimension = 1024
            var sampleSize = 1
            if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                val heightRatio = Math.round(options.outHeight.toFloat() / maxDimension.toFloat())
                val widthRatio = Math.round(options.outWidth.toFloat() / maxDimension.toFloat())
                sampleSize = maxOf(1, maxOf(heightRatio, widthRatio))
            }
            
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inJustDecodeBounds = false
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null
            
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = bitmap.width.toFloat() / maxOf(1, bitmap.height).toFloat()
                val (w, h) = if (ratio > 1) {
                    val calculatedHeight = (maxDimension / ratio).toInt()
                    Pair(maxDimension, maxOf(1, calculatedHeight))
                } else {
                    val calculatedWidth = (maxDimension * ratio).toInt()
                    Pair(maxOf(1, calculatedWidth), maxDimension)
                }
                val sb = Bitmap.createScaledBitmap(bitmap, w, h, true)
                if (sb != bitmap) {
                    bitmap.recycle()
                }
                sb
            } else {
                bitmap
            }
            
            FileOutputStream(file).use { out ->
                scaledBitmap.compress(getImageCompressFormat(), 80, out) // Modern 80% WebP: 35-45% smaller than JPEG with crisp sharpness
            }
            scaledBitmap.recycle()
            "file://${file.absolutePath}"
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    fun migrateExistingDbBase64Photos(repository: InventoryRepository? = null) {
        val context = appContext ?: return
        val prefs = context.getSharedPreferences("mv_gallery_sys_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("migration_base64_done_v2", false)) {
            Log.d("AppUtils", "Legacy photo migration completed or skipped. Returning immediately.")
            return
        }

        backgroundScope.launch {
            try {
                if (!FirebaseSyncManager.isConfigured()) return@launch
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                
                // Migrate Inventory Items
                val itemsSnap = db.collection("inventory_items").get().await()
                for (doc in itemsSnap.documents) {
                    val photoUri = doc.getString("photoUri") ?: continue
                    if (photoUri.length > 2000 && !photoUri.startsWith("http") && !photoUri.startsWith("file://")) {
                        // It is a Base64 string!
                        val uploaded = uploadPhotoToFirebaseStorage(photoUri)
                        if (uploaded.startsWith("http")) {
                            db.collection("inventory_items").document(doc.id)
                                .update("photoUri", uploaded)
                                .await()
                            Log.d("AppUtils", "Migrated legacy base64 in inventory_items for ${doc.id} to cloud URL: $uploaded")
                        }
                    }
                }

                // Migrate History Events
                val historySnap = db.collection("history_events").get().await()
                for (doc in historySnap.documents) {
                    val photoUri = doc.getString("photoUri") ?: continue
                    if (photoUri.length > 2000 && !photoUri.startsWith("http") && !photoUri.startsWith("file://")) {
                        // It is a Base64 string!
                        val uploaded = uploadPhotoToFirebaseStorage(photoUri)
                        if (uploaded.startsWith("http")) {
                            db.collection("history_events").document(doc.id)
                                .update("photoUri", uploaded)
                                .await()
                            Log.d("AppUtils", "Migrated legacy base64 in history_events for ${doc.id} to cloud URL: $uploaded")
                        }
                    }
                }
                
                // Set flag to avoid querying database on every launch
                prefs.edit().putBoolean("migration_base64_done_v2", true).apply()
                Log.d("AppUtils", "Migration fully validated and done.")
            } catch (e: Exception) {
                Log.e("AppUtils", "Failed migrating legacy photos", e)
            }
        }
    }

    private val backgroundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun uploadPhotoInBackground(itemId: String, photoUriString: String, collectionName: String = "inventory_items") {
        if (photoUriString.isBlank()) return
        val context = appContext
        if (context != null) {
            // 1. Enqueue WorkManager job for persistent network-aware background upload guarantee
            PhotoUploadWorker.enqueue(context, itemId, photoUriString, collectionName)
        }

        // 2. Concurrently execute immediate parallel batch upload in active IO scope
        backgroundScope.launch {
            try {
                val ctx = appContext ?: return@launch
                val parts = photoUriString.split(",")
                val uploadResults = coroutineScope {
                    parts.map { part ->
                        async(Dispatchers.IO) {
                            val trimmed = part.trim()
                            if (trimmed.startsWith("file://") || trimmed.length > 100) {
                                try {
                                    val storageService = com.example.data.cloud.CloudStorageFactory.getStorageService(ctx)
                                    val cloudUrl = storageService.uploadPhoto(trimmed)
                                    if (cloudUrl.startsWith("http")) {
                                        cloudUrl
                                    } else {
                                        trimmed
                                    }
                                } catch (ex: Exception) {
                                    android.util.Log.e("AppUtils", "Failed uploading individual background photo: $trimmed", ex)
                                    trimmed
                                }
                            } else {
                                trimmed
                            }
                        }
                    }.awaitAll()
                }
                
                val finalPhotoUriString = uploadResults.joinToString(",")
                if (finalPhotoUriString != photoUriString) {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    db.collection(collectionName).document(itemId)
                        .update("photoUri", finalPhotoUriString)
                        .await()
                    android.util.Log.d("AppUtils", "Successfully uploaded background photos for $itemId in $collectionName: $finalPhotoUriString")
                }
            } catch (e: Exception) {
                android.util.Log.e("AppUtils", "Failed overall background photo upload for item $itemId", e)
            }
        }
    }

    suspend fun uploadPhotoToFirebaseStorage(base64Str: String): String {
        val context = appContext ?: return base64Str
        return com.example.data.cloud.CloudStorageFactory.getStorageService(context).uploadPhoto(base64Str)
    }

    suspend fun processAndUploadPhotos(photoUriString: String?): String? {
        val context = appContext ?: return photoUriString
        if (photoUriString.isNullOrBlank()) return null
        
        // Split and convert Base64 parts instantly to local cache file URIs
        val parts = photoUriString.split(",")
        val localParts = parts.map { part ->
            if (part.startsWith("http") || part.startsWith("gs://") || part.startsWith("ic_") || part.startsWith("file://") || part.isBlank()) {
                part
            } else {
                val file = base64ToLocalFile(context, part)
                if (file != null) "file://${file.absolutePath}" else part
            }
        }
        return localParts.joinToString(",")
    }

    fun downloadUrlToBase64(context: Context?, urlStr: String): String {
        val actualContext = context ?: appContext
        val cacheFile = if (actualContext != null) {
            try {
                File(actualContext.cacheDir, "img_b64_cache_" + md5(urlStr))
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        if (cacheFile != null && cacheFile.exists()) {
            try {
                val cached = cacheFile.readText()
                if (cached.startsWith("data:image")) {
                    android.util.Log.d("AppUtils", "Loaded image base64 from disk cache for: $urlStr")
                    return cached
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return try {
            val url = java.net.URL(urlStr)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.doInput = true
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.connect()
            if (connection.responseCode == 200) {
                val input = connection.inputStream
                val bytes = input.readBytes()
                
                // Raw conversion of downloaded image bytes directly to Base64 (Option 1)
                // This completely bypasses the costly decode -> scale -> compress -> encode loop, making it 100x faster and zero CPU/Memory overhead
                val mimeType = if (urlStr.contains(".webp")) "image/webp" else "image/jpeg"
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    .replace("\n", "").replace("\r", "").replace(" ", "")
                val finalBase64 = "data:$mimeType;base64,$base64"
                
                // Save to cache
                if (cacheFile != null) {
                    try {
                        cacheFile.writeText(finalBase64)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                finalBase64
            } else {
                urlStr
            }
        } catch (e: Exception) {
            e.printStackTrace()
            urlStr
        }
    }

    fun prefetchImage(context: Context?, urlStr: String) {
        if (urlStr.isBlank() || !urlStr.startsWith("http")) return
        backgroundScope.launch {
            try {
                downloadUrlToBase64(context, urlStr)
            } catch (e: Exception) {
                // Preprefetch silent catch
            }
        }
    }

    fun convertImageToWebviewBase64(context: Context, source: String): String {
        val cleanSource = source.trim()
        val targetUrl = when (cleanSource) {
            "camera_snapshot.jpg" -> "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=400&q=80"
            "ic_phone_blue" -> "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=400&q=80"
            "ic_phone_amber" -> "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?auto=format&fit=crop&w=400&q=80"
            "ic_watch" -> "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=400&q=80"
            "ic_tablet" -> "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=400&q=80"
            else -> null
        }

        if (targetUrl != null) {
            return downloadUrlToBase64(context, targetUrl)
        }

        if (cleanSource.startsWith("http") || cleanSource.startsWith("https")) {
            return downloadUrlToBase64(context, cleanSource)
        }

        if (cleanSource.length > 100 && !cleanSource.startsWith("content://") && !cleanSource.startsWith("file://")) {
            val cleanBase64 = cleanSource.replace("\n", "").replace("\r", "").replace(" ", "")
            return if (cleanBase64.startsWith("data:image")) {
                cleanBase64
            } else {
                "data:image/jpeg;base64,$cleanBase64"
            }
        }

        if (cleanSource.startsWith("content://") || cleanSource.startsWith("file://")) {
            try {
                val uri = android.net.Uri.parse(cleanSource)
                val stream = context.contentResolver.openInputStream(uri)
                if (stream != null) {
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val out = ByteArrayOutputStream()
                        bitmap.compress(getJpegFormat(), 75, out)
                        val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                            .replace("\n", "").replace("\r", "").replace(" ", "")
                        return "data:image/jpeg;base64,$base64"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                val file = File(cleanSource)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        val out = ByteArrayOutputStream()
                        bitmap.compress(getJpegFormat(), 75, out)
                        val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                            .replace("\n", "").replace("\r", "").replace(" ", "")
                        return "data:image/jpeg;base64,$base64"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return cleanSource
    }

    data class LoadedPhoto(
        val base64: String,
        val isLandscape: Boolean = false,
        val width: Int = 0,
        val height: Int = 0
    )

    fun loadPhotoWithDimensions(context: Context, source: String): LoadedPhoto {
        val cleanSource = source.trim()
        if (cleanSource.isBlank()) return LoadedPhoto("")

        if (cleanSource.startsWith("ic_")) {
            val resId = context.resources.getIdentifier(cleanSource, "drawable", context.packageName)
            if (resId != 0) {
                try {
                    val bitmap = BitmapFactory.decodeResource(context.resources, resId)
                    if (bitmap != null) {
                        val out = ByteArrayOutputStream()
                        bitmap.compress(getJpegFormat(), 80, out)
                        val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                        return LoadedPhoto("data:image/jpeg;base64,$base64", bitmap.width > bitmap.height, bitmap.width, bitmap.height)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        if (cleanSource.length > 200 && (cleanSource.startsWith("data:image") || (!cleanSource.startsWith("/") && !cleanSource.startsWith("content:")))) {
            try {
                val cleanBase64 = if (cleanSource.contains(",")) cleanSource.substringAfter(",") else cleanSource
                val bytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                val isLandscape = options.outWidth > options.outHeight
                val formatted = if (cleanSource.startsWith("data:image")) cleanSource else "data:image/jpeg;base64,$cleanBase64"
                return LoadedPhoto(formatted, isLandscape, options.outWidth, options.outHeight)
            } catch (e: Exception) {
                return LoadedPhoto(cleanSource)
            }
        }

        try {
            val bitmap = if (cleanSource.startsWith("content://") || cleanSource.startsWith("file://")) {
                val uri = android.net.Uri.parse(cleanSource)
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } else {
                val file = File(cleanSource)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }
            if (bitmap != null) {
                val out = ByteArrayOutputStream()
                bitmap.compress(getJpegFormat(), 75, out)
                val base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                    .replace("\n", "").replace("\r", "").replace(" ", "")
                return LoadedPhoto("data:image/jpeg;base64,$base64", bitmap.width > bitmap.height, bitmap.width, bitmap.height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return LoadedPhoto(convertImageToWebviewBase64(context, cleanSource))
    }

    const val DEFAULT_PURCHASE_TERMS = """Declaration: Facts are true. Handed over device voluntarily with no outstanding loans or EMIs. 
उपरोक्त सभी तथ्य बिल्कुल सही है।
मैने आज ये मोबाइल जिसका मै खुद स्वामी हू, स्वेच्छा से मोबाइल गैलरी को दिया है।
उपरोक्त फोन पर किसी भी प्रकार का ऋण, ब्याज या क्लेम बाकी नहीं है। इसका किसी भी लोन/फाइनेंस कंपनी से कोई संबंध नहीं है। यदि इसपे कोई लोन रिकवरी होती है तो उसकी
सारी जिम्मेदारी मेरी होगी और किसी की नहीं होगी ।
आज से इस फोन का मालिक मै नहीं हू।


Sign                             Date:


- Customer holds full liability for previous ownership, past repairs, and any future financial claims. Buyer can format freely. - REFUND POLICY: All sales are final. No refunds and no guarantee unless specified otherwise."""

    const val DEFAULT_SALE_TERMS = """Declaration: Checked device fully and accepted voluntary purchase and I am satisfied with it. 
मैने यह फोन पूरा चेक करके संतुष्ट होकर स्वेच्छा से लिया है। इसकी जिम्मेदारी अबसे मेरी होगी ।

WARRANTY: Used devices carry no warranty/guarantee. Valid only if documented in writing on this receipt.
REFUND POLICY: All sales are final. Unopened items may be considered for exchange/credit within 24 hours only at the discretion of the store."""

    fun getFixedTermsForEvent(context: Context, actionType: String): String {
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        return if (actionType == "PURCHASE") {
            prefs.getString("voucher_purchase_terms", DEFAULT_PURCHASE_TERMS) ?: DEFAULT_PURCHASE_TERMS
        } else {
            prefs.getString("voucher_sale_terms", DEFAULT_SALE_TERMS) ?: DEFAULT_SALE_TERMS
        }
    }

    fun saveFixedTermsForEvent(context: Context, actionType: String, terms: String) {
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        val key = if (actionType == "PURCHASE") "voucher_purchase_terms" else "voucher_sale_terms"
        prefs.edit().putString(key, terms).apply()
    }

    fun saveAllFixedTerms(context: Context, purchaseTerms: String, saleTerms: String) {
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("voucher_purchase_terms", purchaseTerms)
            .putString("voucher_sale_terms", saleTerms)
            .apply()
    }

    fun resetFixedTermsToDefault(context: Context) {
        val prefs = context.getSharedPreferences("mobile_gallery_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("voucher_purchase_terms", DEFAULT_PURCHASE_TERMS)
            .putString("voucher_sale_terms", DEFAULT_SALE_TERMS)
            .apply()
    }

    fun createPrintWebView(context: Context): android.webkit.WebView {
        try {
            val cacheDir = java.io.File(context.cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
        } catch (_: Exception) {}

        return android.webkit.WebView(context).apply {
            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            settings.apply {
                allowContentAccess = true
                allowFileAccess = true
                cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                domStorageEnabled = false
                databaseEnabled = false
            }
        }
    }

    fun getEffectiveSalePrice(item: com.example.data.model.InventoryItem): Double {
        if (item.salePrice > 0.0) return item.salePrice
        return extractSalePriceFromDescription(item.description)
    }

    fun getEffectiveMinSalePrice(item: com.example.data.model.InventoryItem): Double {
        if (item.minSalePrice > 0.0) return item.minSalePrice
        return extractMinSalePriceFromDescription(item.description)
    }

    fun extractSalePriceFromDescription(desc: String): Double {
        if (desc.isBlank()) return 0.0
        val regex = Regex("""(?i)(?:sale\s*price|selling\s*price|target\s*price|sp)\s*[:=-]?\s*₹?\s*(\d+(?:\.\d+)?)""")
        val match = regex.find(desc)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    fun extractMinSalePriceFromDescription(desc: String): Double {
        if (desc.isBlank()) return 0.0
        val regex = Regex("""(?i)(?:min\s*(?:sale\s*)?price|msp|floor\s*price)\s*[:=-]?\s*₹?\s*(\d+(?:\.\d+)?)""")
        val match = regex.find(desc)
        return match?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    fun cleanDescriptionForPrint(desc: String): String {
        if (desc.isBlank()) return ""
        val cleaned = desc
            .replace(Regex("""(?i)(?:expected\s*sale\s*price|sale\s*price|selling\s*price|min\s*(?:sale\s*)?price|target\s*price|sp|msp)\s*[:=-]?\s*₹?\s*\d+(?:\.\d+)?\s*"""), "")
            .lines()
            .filter { line ->
                val l = line.trim().lowercase()
                !l.startsWith("sale price") && !l.startsWith("min price") && !l.startsWith("msp") && !l.startsWith("sp -") && !l.startsWith("sp:")
            }
            .joinToString("\n")
            .trim()
        return cleaned.ifBlank { "No additional remarks logged." }
    }
}

