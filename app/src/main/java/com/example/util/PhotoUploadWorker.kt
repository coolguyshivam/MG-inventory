package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.data.cloud.CloudStorageFactory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

class PhotoUploadWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val itemId = inputData.getString(KEY_ITEM_ID) ?: return Result.failure()
        val photoUriString = inputData.getString(KEY_PHOTO_URI) ?: return Result.success()
        val collectionName = inputData.getString(KEY_COLLECTION_NAME) ?: "inventory_items"

        if (photoUriString.isBlank()) return Result.success()

        return try {
            com.example.data.repository.FirebaseSyncManager.initialize(context)
            val parts = photoUriString.split(",")

            val uploadResults = coroutineScope {
                parts.map { part ->
                    async(Dispatchers.IO) {
                        val trimmed = part.trim()
                        if (trimmed.startsWith("file://") || trimmed.length > 100) {
                            try {
                                val storageService = CloudStorageFactory.getStorageService(context)
                                val cloudUrl = storageService.uploadPhoto(trimmed)
                                if (cloudUrl.startsWith("http")) cloudUrl else trimmed
                            } catch (e: Exception) {
                                Log.w(TAG, "PhotoUploadWorker: single part upload retryable error", e)
                                trimmed
                            }
                        } else {
                            trimmed
                        }
                    }
                }.awaitAll()
            }

            val finalPhotoUri = uploadResults.joinToString(",")
            if (finalPhotoUri != photoUriString) {
                val db = FirebaseFirestore.getInstance()
                db.collection(collectionName).document(itemId)
                    .update("photoUri", finalPhotoUri)
                    .await()
                Log.d(TAG, "PhotoUploadWorker: updated $itemId in $collectionName with $finalPhotoUri")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "PhotoUploadWorker failed for item $itemId, requesting retry", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val TAG = "PhotoUploadWorker"
        const val KEY_ITEM_ID = "item_id"
        const val KEY_PHOTO_URI = "photo_uri"
        const val KEY_COLLECTION_NAME = "collection_name"

        fun enqueue(context: Context, itemId: String, photoUriString: String, collectionName: String = "inventory_items") {
            try {
                val data = workDataOf(
                    KEY_ITEM_ID to itemId,
                    KEY_PHOTO_URI to photoUriString,
                    KEY_COLLECTION_NAME to collectionName
                )
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<PhotoUploadWorker>()
                    .setInputData(data)
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    "upload_photo_${itemId}_${collectionName}",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enqueue PhotoUploadWorker", e)
            }
        }
    }
}
