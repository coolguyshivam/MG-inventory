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
        try {
            val app = com.google.firebase.FirebaseApp.getInstance()
            val options = app.options
            val projectId = options.projectId
            if (projectId != null && projectId.isNotBlank() && !projectId.contains("dummy", ignoreCase = true)) {
                return true
            }
        } catch (e: Exception) {
            // Not initialized yet
        }

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
            com.example.util.AppUtils.init(context)
            if (isInitialized) return
            try {
                val existingApp = try {
                    FirebaseApp.getInstance()
                } catch (e: IllegalStateException) {
                    null
                }

                if (existingApp == null) {
                    try {
                        val options = if (isConfigured()) {
                            Log.d("FirebaseSyncManager", "Firebase configured. Initializing real sync mode!")
                            val builder = FirebaseOptions.Builder()
                                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                                .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                                .setDatabaseUrl(BuildConfig.FIREBASE_DATABASE_URL.takeIf { it.isNotBlank() })
                            
                            try {
                                val bucket = BuildConfig.FIREBASE_STORAGE_BUCKET
                                val rawBucket = if (bucket.isNotBlank() && !bucket.contains("your-app")) {
                                    val cleanBucket = if (bucket.startsWith("gs://")) bucket else "gs://$bucket"
                                    cleanBucket.removePrefix("gs://")
                                } else {
                                    "${BuildConfig.FIREBASE_PROJECT_ID}.firebasestorage.app"
                                }
                                builder.setStorageBucket(rawBucket)
                                Log.d("FirebaseSyncManager", "Firebase Storage Bucket configured: $rawBucket")
                            } catch (e: Exception) {
                                Log.w("FirebaseSyncManager", "Could not set custom storage bucket in FirebaseOptions: ${e.message}")
                            }
                            
                            builder.build()
                        } else {
                            Log.d("FirebaseSyncManager", "Firebase configurations missing/placeholder. Initializing crash-safe local offline-first mode.")
                            FirebaseOptions.Builder()
                                .setApiKey("AIzaSyDummyKeyForFirestoreOfflineWorking")
                                .setApplicationId("1:123456789012:android:0123456789abcdef012345")
                                .setProjectId("inventorymanagement-dummy")
                                .build()
                        }
                        FirebaseApp.initializeApp(context.applicationContext, options)
                    } catch (e: Exception) {
                        Log.e("FirebaseSyncManager", "Failed to initialize Firebase with main options, trying dummy fallback", e)
                        try {
                            val dummyOptions = FirebaseOptions.Builder()
                                .setApiKey("AIzaSyDummyKeyForFirestoreOfflineWorking")
                                .setApplicationId("1:123456789012:android:0123456789abcdef012345")
                                .setProjectId("inventorymanagement-dummy")
                                .build()
                            if (FirebaseApp.getApps(context.applicationContext).isEmpty()) {
                                FirebaseApp.initializeApp(context.applicationContext, dummyOptions)
                            }
                        } catch (ex: Exception) {
                            Log.e("FirebaseSyncManager", "Failed to initialize default dummy Firebase fallback", ex)
                        }
                    }
                }

                try {
                    firestoreInstance = FirebaseFirestore.getInstance()
                    isInitialized = true
                    Log.d("FirebaseSyncManager", "Firebase initialized successfully!")
                } catch (e: Exception) {
                    Log.e("FirebaseSyncManager", "Failed to get Firestore instance during initialization", e)
                }
                
                // Attempt Anonymous Sign-in for Firestore Auth rules if configured and initialized
                if (isConfigured() && isInitialized) {
                    try {
                        com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously()
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Log.d("FirebaseSyncManager", "signInAnonymously:success")
                                } else {
                                    Log.w("FirebaseSyncManager", "signInAnonymously:failure", task.exception)
                                }
                            }
                    } catch (e: Exception) {
                        Log.w("FirebaseSyncManager", "Auth sign in bypassed or failed under current context configuration", e)
                    }
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
                val docRef = db.collection(com.example.data.cloud.AppCloudConfig.COLL_INVENTORY_ITEMS).document(item.id.toString())
                var shouldOverwrite = true
                try {
                    val remoteDoc = docRef.get().awaitTask()
                    if (remoteDoc.exists()) {
                        val remoteLastUpdated = remoteDoc.getLong("lastUpdated") ?: 0L
                        val localLastUpdated = if (item.lastUpdated > 0) item.lastUpdated else System.currentTimeMillis()
                        if (remoteLastUpdated > localLastUpdated) {
                            Log.d("FirebaseSyncManager", "Conflict detected for item ${item.id}: Cloud has newer modifications. Keeping cloud version.")
                            shouldOverwrite = false
                        }
                    }
                } catch (e: Exception) {
                    Log.w("FirebaseSyncManager", "Could not check remote timestamp, proceeding with sync: ${e.message}")
                }

                if (shouldOverwrite) {
                    val finalTimestamp = if (item.lastUpdated > 0) item.lastUpdated else System.currentTimeMillis()
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
                        "underRepair" to item.underRepair,
                        "technicianName" to item.technicianName,
                        "repairReason" to item.repairReason,
                        "lastUpdated" to finalTimestamp
                    )
                    docRef.set(itemMap).awaitTask()
                }
            }
            Log.d("FirebaseSyncManager", "Synced items to cloud with conflict resolution logic.")
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
                db.collection(com.example.data.cloud.AppCloudConfig.COLL_HISTORY_EVENTS)
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
