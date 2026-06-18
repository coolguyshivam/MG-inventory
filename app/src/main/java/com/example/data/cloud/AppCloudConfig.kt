package com.example.data.cloud

import com.example.BuildConfig

/**
 * Global configurations for all Cloud Services (Storage & Syncing).
 * Decouples table/collection naming, sub-folders, and adapter toggles from
 * business logic, enabling easy environment swapping, path changes, or schema restructuring.
 */
object AppCloudConfig {

    // --- Active Database and Storage Service Provider Types ---
    // Change these values to swap to AWS S3, Supabase, local PHP APIs, or Custom REST Servers easily.
    const val PROVIDER_FIREBASE = "FIREBASE"
    const val PROVIDER_LOCAL_ONLY = "LOCAL_ONLY"
    
    // Choose active providers: either "FIREBASE" or "LOCAL_ONLY" (or extend with custom names)
    val CURRENT_DATABASE_PROVIDER: String = try {
        // Can be customized via a custom env field e.g. BuildConfig.DATABASE_PROVIDER
        PROVIDER_FIREBASE
    } catch (e: Exception) {
        PROVIDER_FIREBASE
    }

    val CURRENT_STORAGE_PROVIDER: String = try {
        // Can be customized via a custom env field e.g. BuildConfig.STORAGE_PROVIDER
        PROVIDER_FIREBASE
    } catch (e: Exception) {
        PROVIDER_FIREBASE
    }

    // --- Cloud Collection/Table Names (Firestore / Database Schema) ---
    // Amend these string values to change collection or tables mapping instantly.
    const val COLL_INVENTORY_ITEMS = "inventory_items"
    const val COLL_HISTORY_EVENTS = "history_events"
    const val COLL_USERS = "users"
    const val COLL_PARTIES = "parties"
    const val COLL_LEDGER_ENTRIES = "ledger_entries"
    const val COLL_ATTENDANCE_RECORDS = "attendance_records"
    const val COLL_LEAVE_APPLICATIONS = "leave_applications"
    const val COLL_ATTENDANCE_NOTIFICATIONS = "attendance_notifications"
    const val COLL_BRAND_STOCK_ITEMS = "brand_stock_items_v2"
    const val COLL_BRAND_STOCK_TRANSACTIONS = "brand_stock_transactions_v2"

    // --- Cloud Storage Folders & Subpaths ---
    // Modify the photo upload directory paths easily.
    const val STORAGE_FOLDER_PHOTOS = "photos" // Placed inside the bucket e.g., gs://project-id.appspot.com/photos/
}
