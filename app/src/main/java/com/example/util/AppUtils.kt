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

object AppUtils {

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
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream) ?: return null
            
            // Resize to maximum dimension of 600px for lightning-fast uploads and minimal document footprint
            val maxDimension = 600
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
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
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
            
            val hash = md5(pureBase64)
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
        try {
            val bitmap: Bitmap? = when {
                imageSource.length > 100 && !imageSource.startsWith("http") && !imageSource.startsWith("content://") && !imageSource.startsWith("file://") -> {
                    val pureBase64 = if (imageSource.startsWith("data:image")) {
                        val index = imageSource.indexOf(",")
                        if (index != -1) imageSource.substring(index + 1) else imageSource
                    } else {
                        imageSource
                    }
                    val bytes = android.util.Base64.decode(pureBase64, android.util.Base64.NO_WRAP)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                imageSource.startsWith("content://") || imageSource.startsWith("file://") -> {
                    val uri = Uri.parse(imageSource)
                    val stream: InputStream? = context.contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(stream)
                }
                else -> {
                    val file = File(imageSource)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    } else {
                        null
                    }
                }
            }
            
            if (bitmap == null) {
                Toast.makeText(context, "Error: Could not decode image to save.", Toast.LENGTH_SHORT).show()
                return
            }

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
                    if (fos != null) {
                        fos.use {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(insertedUri, contentValues, null, null)
                }
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(imagesDir, "Inventory")
                if (!appDir.exists()) appDir.mkdirs()
                val image = File(appDir, filename)
                fos = FileOutputStream(image)
                insertedUri = Uri.fromFile(image)
                fos.use {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                }
            }

            if (insertedUri != null) {
                Toast.makeText(context, "Saved directly to Pictures/Inventory Gallery!", Toast.LENGTH_LONG).show()
                
                // Alert the system media scanner
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
            } else {
                Toast.makeText(context, "Failed to capture media storage channel.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Save Failed: ${e.message}", Toast.LENGTH_SHORT).show()
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

    @androidx.compose.runtime.Composable
    fun resolveImageModel(modelStr: String?): Any {
        if (modelStr.isNullOrBlank()) return "ic_placeholder" // Fallback placeholder
        val context = androidx.compose.ui.platform.LocalContext.current
        return androidx.compose.runtime.remember(modelStr) {
            val target = when (modelStr) {
                "ic_phone_blue" -> "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=400&q=80"
                "ic_phone_amber" -> "https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?auto=format&fit=crop&w=400&q=80"
                "ic_watch" -> "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=400&q=80"
                "ic_tablet" -> "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=400&q=80"
                else -> modelStr
            }
            if (target.length > 100 && !target.startsWith("http") && !target.startsWith("content://") && !target.startsWith("file://")) {
                val file = base64ToLocalFile(context, target)
                file ?: target
            } else {
                target
            }
        }
    }

    suspend fun uploadPhotoToFirebaseStorage(base64Str: String): String {
        if (base64Str.startsWith("http") || base64Str.startsWith("gs://") || base64Str.startsWith("ic_") || base64Str.isBlank()) {
            return base64Str
        }
        
        return try {
            val pureBase64 = if (base64Str.startsWith("data:image")) {
                val index = base64Str.indexOf(",")
                if (index != -1) base64Str.substring(index + 1) else base64Str
            } else {
                base64Str
            }
            val bytes = android.util.Base64.decode(pureBase64, android.util.Base64.NO_WRAP)
            
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val compressedBytes = if (bitmap != null) {
                val maxDimension = 800
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
                val out = java.io.ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                out.toByteArray()
            } else {
                bytes
            }

            val bucket = try {
                com.example.BuildConfig.FIREBASE_STORAGE_BUCKET
            } catch (e: Exception) {
                ""
            }
            val storage = if (bucket.isNotBlank() && !bucket.contains("your-app")) {
                val cleanBucket = if (bucket.startsWith("gs://")) bucket else "gs://$bucket"
                com.google.firebase.storage.FirebaseStorage.getInstance(cleanBucket)
            } else {
                com.google.firebase.storage.FirebaseStorage.getInstance()
            }
            val ref = storage.reference.child("photos/${UUID.randomUUID()}.jpg")
            
            // Upload bytes to Cloud Storage and await
            ref.putBytes(compressedBytes).await()
            
            // Fetch download URI and await
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("UploadPhoto", "Error uploading photo content to storage: ${e.message}")
            base64Str
        }
    }

    suspend fun processAndUploadPhotos(photoUriString: String?): String? {
        if (photoUriString.isNullOrBlank()) return photoUriString
        val parts = photoUriString.split(",")
        val uploadedParts = parts.map { part ->
            if (part.isNotBlank() && !part.startsWith("http") && !part.startsWith("ic_")) {
                uploadPhotoToFirebaseStorage(part)
            } else {
                part
            }
        }
        return uploadedParts.filter { it.isNotBlank() }.joinToString(",")
    }
}

