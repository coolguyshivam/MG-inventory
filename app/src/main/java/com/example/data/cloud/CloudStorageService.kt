package com.example.data.cloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.repository.FirebaseSyncManager
import com.example.util.AppUtils
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * Clean abstraction for Cloud Media/Photo Storage.
 * Implementations of this interface handle how photos are optimized, compressed, 
 * and uploaded to a target storage solution (e.g. Firebase, AWS S3, local dev API).
 */
interface CloudStorageService {
    suspend fun uploadPhoto(base64Str: String): String
    suspend fun processAndUploadPhotos(photoUriString: String?): String?
}

/**
 * Base implementation handling image resizing and compression locally
 * before delegating the final byte upload to the target provider.
 */
abstract class BaseCloudStorageService : CloudStorageService {
    protected fun getJpegFormat(): Bitmap.CompressFormat {
        return Bitmap.CompressFormat.JPEG
    }

    override suspend fun processAndUploadPhotos(photoUriString: String?): String? {
        if (photoUriString.isNullOrBlank()) return null
        val parts = photoUriString.split(",")
        val uploadedParts = parts.map { part ->
            uploadPhoto(part)
        }
        return uploadedParts.joinToString(",")
    }

    protected fun compressImage(base64Str: String, maxDimension: Int = 1024): ByteArray {
        val pureBase64 = if (base64Str.startsWith("data:image")) {
            val index = base64Str.indexOf(",")
            if (index != -1) base64Str.substring(index + 1) else base64Str
        } else {
            base64Str
        }
        val bytes = Base64.decode(pureBase64, Base64.NO_WRAP)
        
        // 1. Decode bounds to compute sample size
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        
        var sampleSize = 1
        if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while (halfHeight / sampleSize >= maxDimension && halfWidth / sampleSize >= maxDimension) {
                sampleSize *= 2
            }
        }
        
        // 2. Decode with sample size to save massive memory and time
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inJustDecodeBounds = false
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return bytes
        
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
        
        val out = ByteArrayOutputStream()
        scaledBitmap.compress(getJpegFormat(), 75, out) // 75% JPEG preserves high visual quality while optimized for fast cloud uploads
        return out.toByteArray()
    }
}

/**
 * Standard Firebase Cloud Storage implementation.
 */
class FirebaseStorageService(private val context: Context) : BaseCloudStorageService() {
    override suspend fun uploadPhoto(base64Str: String): String {
        if (base64Str.startsWith("http") || base64Str.startsWith("gs://") || base64Str.startsWith("ic_") || base64Str.isBlank()) {
            return base64Str
        }

        if (!FirebaseSyncManager.isConfigured()) {
            return LocalStorageService(context).uploadPhoto(base64Str)
        }

        return try {
            val compressedBytes = if (base64Str.startsWith("file://")) {
                val cleanPath = base64Str.removePrefix("file://")
                val file = File(cleanPath)
                if (file.exists()) {
                    try {
                        val bytes = file.readBytes()
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        
                        val maxDimension = 1024
                        var sampleSize = 1
                        if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                            val halfHeight = options.outHeight / 2
                            val halfWidth = options.outWidth / 2
                            while (halfHeight / sampleSize >= maxDimension && halfWidth / sampleSize >= maxDimension) {
                                sampleSize *= 2
                            }
                        }
                        
                        val decodeOptions = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inJustDecodeBounds = false
                        }
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                        if (bitmap != null) {
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
                            val out = ByteArrayOutputStream()
                            scaledBitmap.compress(getJpegFormat(), 75, out)
                            out.toByteArray()
                        } else {
                            bytes
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        file.readBytes()
                    }
                } else {
                    return base64Str
                }
            } else {
                compressImage(base64Str)
            }
            
            // Get configurable Storage Bucket configuration
            val bucket = try { BuildConfig.FIREBASE_STORAGE_BUCKET } catch (e: Exception) { "" }
            val storage = if (bucket.isNotBlank() && !bucket.contains("your-app")) {
                val cleanBucket = if (bucket.startsWith("gs://")) bucket else "gs://$bucket"
                com.google.firebase.storage.FirebaseStorage.getInstance(cleanBucket)
            } else {
                val projId = try { BuildConfig.FIREBASE_PROJECT_ID } catch (e: Exception) { "" }
                if (projId.isNotBlank() && !projId.contains("dummy")) {
                    try {
                        com.google.firebase.storage.FirebaseStorage.getInstance()
                    } catch (e: Exception) {
                        try {
                            com.google.firebase.storage.FirebaseStorage.getInstance("gs://$projId.firebasestorage.app")
                        } catch (e2: Exception) {
                            com.google.firebase.storage.FirebaseStorage.getInstance("gs://$projId.appspot.com")
                        }
                    }
                } else {
                    com.google.firebase.storage.FirebaseStorage.getInstance()
                }
            }

            val filename = "${AppCloudConfig.STORAGE_FOLDER_PHOTOS}/${UUID.randomUUID()}.jpg"
            val ref = storage.reference.child(filename)
            
            // Build standard image/jpeg content-type metadata so CDN can stream/cache properly
            val metadata = com.google.firebase.storage.StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build()
            
            // Execute cloud upload with timeout and retrieve download URL
            kotlinx.coroutines.withTimeout(30000) {
                ref.putBytes(compressedBytes, metadata).await()
                ref.downloadUrl.await().toString()
            }
        } catch (e: Exception) {
            Log.e("FirebaseStorageService", "Failed or timed out uploading to Firebase Storage. Generating compact base64 fallback. Error: ${e.message}")
            try {
                val bytes = if (base64Str.startsWith("file://")) {
                    val cleanPath = base64Str.removePrefix("file://")
                    val file = File(cleanPath)
                    if (file.exists()) {
                        file.readBytes()
                    } else {
                        null
                    }
                } else {
                    val pureBase64 = if (base64Str.startsWith("data:image")) {
                        val index = base64Str.indexOf(",")
                        if (index != -1) base64Str.substring(index + 1) else base64Str
                    } else {
                        base64Str
                    }
                    Base64.decode(pureBase64, Base64.NO_WRAP)
                }

                if (bytes != null) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    val maxDimension = 320
                    var sampleSize = 1
                    if (options.outHeight > maxDimension || options.outWidth > maxDimension) {
                        val halfHeight = options.outHeight / 2
                        val halfWidth = options.outWidth / 2
                        while (halfHeight / sampleSize >= maxDimension && halfWidth / sampleSize >= maxDimension) {
                            sampleSize *= 2
                        }
                    }
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inJustDecodeBounds = false
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
                    if (bitmap != null) {
                        val scaled = Bitmap.createScaledBitmap(bitmap, 180, (180f * bitmap.height / bitmap.width).toInt(), true)
                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 60, out)
                        val fallbackBytes = out.toByteArray()
                        "data:image/jpeg;base64," + Base64.encodeToString(fallbackBytes, Base64.NO_WRAP)
                    } else {
                        LocalStorageService(context).uploadPhoto(base64Str)
                    }
                } else {
                    LocalStorageService(context).uploadPhoto(base64Str)
                }
            } catch (ex: Exception) {
                LocalStorageService(context).uploadPhoto(base64Str)
            }
        }
    }

    // Helper extension to handle storage Task await
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    continuation.resume(task.result, null)
                } else {
                    continuation.resumeWith(Result.failure(task.exception ?: RuntimeException("Task failed")))
                }
            }
        }
}

/**
 * Local File Cache storage implementation. 
 * Perfect for offline deployments or simple SQLite-only environments.
 * Saves base64 strings into local jpg files on disk to prevent heap issues.
 */
class LocalStorageService(private val context: Context) : BaseCloudStorageService() {
    override suspend fun uploadPhoto(base64Str: String): String {
        if (base64Str.startsWith("http") || base64Str.startsWith("gs://") || base64Str.startsWith("ic_") || base64Str.startsWith("file://") || base64Str.isBlank()) {
            return base64Str
        }
        val file = AppUtils.base64ToLocalFile(context, base64Str)
        return if (file != null) "file://${file.absolutePath}" else base64Str
    }
}

/**
 * Central Cloud Provider Factory.
 * Determines storage destination based on configurations.
 * To change the storage solution completely in the future, developers only need
 * to add their provider here (e.g. AWS S3, Cloudinary) and update the return statement.
 */
object CloudStorageFactory {
    private var instance: CloudStorageService? = null

    fun getStorageService(context: Context): CloudStorageService {
        if (instance == null) {
            synchronized(this) {
                if (instance == null) {
                    val providerType = AppCloudConfig.CURRENT_STORAGE_PROVIDER
                    
                    instance = when (providerType) {
                        AppCloudConfig.PROVIDER_LOCAL_ONLY -> LocalStorageService(context.applicationContext)
                        AppCloudConfig.PROVIDER_FIREBASE -> FirebaseStorageService(context.applicationContext)
                        else -> FirebaseStorageService(context.applicationContext)
                    }
                    Log.d("CloudStorageFactory", "Configured storage adapter: ${instance?.javaClass?.simpleName}")
                }
            }
        }
        return instance!!
    }
}
