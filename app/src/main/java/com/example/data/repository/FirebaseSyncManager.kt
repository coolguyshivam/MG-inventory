package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.model.HistoryEvent
import com.example.data.model.InventoryItem
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FirebaseSyncManager {
    private var isInitialized = false
    private var firestoreInstance: FirebaseFirestore? = null

    /**
     * Helper extension to await standard play-services Tasks.
     */
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    continuation.resume(task.result)
                } else {
                    continuation.resumeWithException(
                        task.exception ?: RuntimeException("Firebase transaction task failed")
                    )
                }
            }
        }

    fun isConfigured(): Boolean {
        return try {
            val apiKey = BuildConfig.FIREBASE_API_KEY
            val projectId = BuildConfig.FIREBASE_PROJECT_ID
            val appId = BuildConfig.FIREBASE_APPLICATION_ID
            apiKey.isNotBlank() && projectId.isNotBlank() && appId.isNotBlank() &&
                    !apiKey.contains("your_") && !apiKey.contains("FIREBASE_API_KEY")
        } catch (e: Exception) {
            false
        }
    }

    fun initialize(context: Context) {
        synchronized(this) {
            if (isInitialized) return
            try {
                if (isConfigured()) {
                    val options = FirebaseOptions.Builder()
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .setDatabaseUrl(BuildConfig.FIREBASE_DATABASE_URL.takeIf { it.isNotBlank() })
                        .build()

                    FirebaseApp.initializeApp(context.applicationContext, options)
                    firestoreInstance = FirebaseFirestore.getInstance()
                    isInitialized = true
                    Log.d("FirebaseSyncManager", "Firebase initialized successfully dynamic!")
                } else {
                    Log.d("FirebaseSyncManager", "Firebase configurations missing/placeholders. Local mode.")
                }
            } catch (e: Exception) {
                Log.e("FirebaseSyncManager", "Failed to initialize Firebase app dynamically", e)
            }
        }
    }

    /**
     * Upload / Synchronize a list of local Room database items to Firebase Firestore.
     */
    suspend fun syncLocalItemsToCloud(items: List<InventoryItem>): Boolean {
        if (!isInitialized) return false
        val db = firestoreInstance ?: return false

        try {
            for (item in items) {
                val itemMap = hashMapOf(
                    "id" to item.id,
                    "serialNumber" to item.serialNumber,
                    "model" to item.model,
                    "name" to item.name,
                    "phoneNumber" to item.phoneNumber,
                    "aadhaarNumber" to item.aadhaarNumber,
                    "amount" to item.amount,
                    "description" to item.description,
                    "dateInMillis" to item.dateInMillis,
                    "quantity" to item.quantity,
                    "photoUri" to item.photoUri,
                    "isUnderRepair" to item.isUnderRepair,
                    "technicianName" to item.technicianName,
                    "repairReason" to item.repairReason,
                    "lastUpdated" to System.currentTimeMillis()
                )
                db.collection("inventory_items")
                    .document(item.id.toString())
                    .set(itemMap)
                    .awaitTask()
            }
            Log.d("FirebaseSyncManager", "Synced ${items.size} inventory items to cloud.")
            return true
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "Error syncing items to Firebase", e)
            return false
        }
    }

    /**
     * Upload / Synchronize a list of local Room transactions / history logs to Firebase Firestore.
     */
    suspend fun syncLocalHistoryToCloud(events: List<HistoryEvent>): Boolean {
        if (!isInitialized) return false
        val db = firestoreInstance ?: return false

        try {
            for (event in events) {
                val eventMap = hashMapOf(
                    "id" to event.id,
                    "actionType" to event.actionType,
                    "serialNumber" to event.serialNumber,
                    "model" to event.model,
                    "name" to event.name,
                    "phoneNumber" to event.phoneNumber,
                    "aadhaarNumber" to event.aadhaarNumber,
                    "amount" to event.amount,
                    "description" to event.description,
                    "dateInMillis" to event.dateInMillis,
                    "quantity" to event.quantity,
                    "photoUri" to event.photoUri,
                    "userId" to event.userId,
                    "timestamp" to event.timestamp,
                    "extraDetails" to event.extraDetails
                )
                db.collection("history_events")
                    .document(event.id.toString())
                    .set(eventMap)
                    .awaitTask()
            }
            Log.d("FirebaseSyncManager", "Synced ${events.size} audit history transactions to cloud.")
            return true
        } catch (e: Exception) {
            Log.e("FirebaseSyncManager", "Error syncing history events to Firebase", e)
            return false
        }
    }
}
