package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.example.data.model.HistoryEvent
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.File
import java.util.UUID

object AppUtils {
    private const val TAG = "AppUtils"

    /**
     * Compresses the selected photo and uploads it to Firebase Storage.
     * Returns the remote HTTPS URL, or falls back to the local Uri string if failure occurs.
     */
    suspend fun compressAndUploadPhoto(context: Context, uriString: String): String {
        if (uriString.isBlank()) return ""
        if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
            return uriString // Already uploaded
        }

        val localUri = Uri.parse(uriString)
        try {
            // Read and compress
            val bitmap = getBitmapFromUri(context, localUri) ?: return uriString
            val rotatedBitmap = rotateImageIfRequired(context, bitmap, localUri)
            
            // Scaled version to avoid huge memory & payload
            val scaledBitmap = scaleBitmap(rotatedBitmap, 1200)
            
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
            val compressedBytes = outputStream.toByteArray()

            // Firebase Storage Upload
            try {
                // Pre-authenticate anonymously to satisfy Firebase Security Rules
                val auth = FirebaseAuth.getInstance()
                if (auth.currentUser == null) {
                    Log.d(TAG, "Signing in anonymously to satisfy Firebase Storage rules...")
                    auth.signInAnonymously().await()
                }

                val filepath = "photos/${UUID.randomUUID()}.jpg"
                val storageRef = FirebaseStorage.getInstance().reference.child(filepath)
                
                Log.d(TAG, "Uploading compressed image to Firebase Storage: $filepath")
                storageRef.putBytes(compressedBytes).await()
                
                val downloadUrl = storageRef.downloadUrl.await().toString()
                Log.d(TAG, "Upload success! URL: $downloadUrl")
                return downloadUrl
            } catch (firebaseEx: Throwable) {
                Log.e(TAG, "Firebase upload failed, falling back to local URI", firebaseEx)
                // If firebase fails (e.g. no internet or unconfigured), we return the local path
                return uriString
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to compress or read bitmap from $uriString", e)
            return uriString
        }
    }

    private fun getBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }

            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) return null

            // Target max dimension of 1200 px to preserve memory and network resources
            val maxDim = 1200
            var inSampleSize = 1
            if (width > maxDim || height > maxDim) {
                val halfWidth = width / 2
                val halfHeight = height / 2
                while ((halfWidth / inSampleSize) >= maxDim && (halfHeight / inSampleSize) >= maxDim) {
                    inSampleSize *= 2
                }
            }

            // Now decode with the calculated inSampleSize
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "getBitmapFromUri with downsampling failed", e)
            null
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val aspectRatio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / aspectRatio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * aspectRatio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun rotateImageIfRequired(context: Context, img: Bitmap, selectedImage: Uri): Bitmap {
        var input: InputStream? = null
        try {
            input = context.contentResolver.openInputStream(selectedImage)
            if (input == null) return img
            val ei = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                ExifInterface(input)
            } else {
                ExifInterface(selectedImage.path ?: "")
            }
            
            val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            return when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270)
                else -> img
            }
        } catch (e: Throwable) {
            Log.e(TAG, "rotateImageIfRequired error", e)
            return img
        } finally {
            try {
                input?.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun rotateImage(img: Bitmap, degree: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree.toFloat())
        val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        img.recycle()
        return rotatedImg
    }

    /**
     * Builds and opens standard PDF invoice print previews from the custom inputs.
     * Hindi text formats beautifully, details list matches, and photos load correctly.
     */
    fun printHistoryEventCustom(
        context: Context,
        event: HistoryEvent,
        termsText: String,
        photoList: List<String>
    ) {
        val mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(context)
        mainExecutor.execute {
            try {
                val webView = android.webkit.WebView(context)
                
                val htmlBuilder = StringBuilder()
            htmlBuilder.append("""
                <html>
                <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
                        color: #1a1a1a;
                        padding: 24px;
                        font-size: 14px;
                        line-height: 1.6;
                    }
                    .header {
                        text-align: center;
                        border-bottom: 2px dashed #475569;
                        padding-bottom: 16px;
                        margin-bottom: 24px;
                    }
                    .header h1 {
                        margin: 0;
                        font-size: 28px;
                        font-weight: 900;
                        color: #0f172a;
                        letter-spacing: 1.5px;
                    }
                    .header p {
                        margin: 4px 0 0 0;
                        font-size: 11px;
                        color: #64748b;
                        text-transform: uppercase;
                        font-weight: bold;
                    }
                    .details-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 24px;
                    }
                    .details-table td {
                        padding: 10px 14px;
                        border: 1px solid #cbd5e1;
                        font-size: 13px;
                    }
                    .details-table .label {
                        font-weight: bold;
                        background-color: #f8fafc;
                        width: 32%;
                        color: #334155;
                    }
                    .details-table .value {
                        width: 68%;
                        color: #0f172a;
                    }
                    .amount-row {
                        font-weight: bold;
                        font-size: 16px;
                        color: #0f766e;
                        background-color: #f0fdfa !important;
                    }
                    .terms-section {
                        margin-top: 24px;
                        padding: 16px;
                        background-color: #f8fafc;
                        border-left: 4px solid #475569;
                        font-size: 14px;
                        white-space: pre-line;
                        color: #1e293b;
                    }
                    .photo-container {
                        margin-top: 32px;
                        page-break-inside: avoid;
                        border-top: 1px dashed #94a3b8;
                        padding-top: 16px;
                    }
                    .photo-title {
                        font-size: 15px;
                        font-weight: bold;
                        color: #334155;
                        margin-bottom: 12px;
                    }
                    .photo-grid {
                        display: flex;
                        flex-direction: row;
                        flex-wrap: wrap;
                        gap: 16px;
                    }
                    .photo-item {
                        border: 1px solid #e2e8f0;
                        padding: 4px;
                        border-radius: 8px;
                        background: #ffffff;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.05);
                    }
                    .photo-img {
                        max-width: 260px;
                        max-height: 200px;
                        object-fit: contain;
                        display: block;
                        border-radius: 4px;
                    }
                    .footer {
                        margin-top: 48px;
                        text-align: center;
                        font-size: 11px;
                        color: #94a3b8;
                        letter-spacing: 0.5px;
                    }
                </style>
                </head>
                <body>
                    <div class="header">
                        <h1>MOBILE GALLERY</h1>
                        <p>Authorized Sales, Purchase & Ledger Slip</p>
                    </div>
                    
                    <table class="details-table">
                        <tr>
                            <td class="label">Transaction ID</td>
                            <td class="value">MG-TXN-${event.id}</td>
                        </tr>
                        <tr>
                            <td class="label">Timestamp</td>
                            <td class="value">${java.text.SimpleDateFormat("dd MMMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(event.timestamp))}</td>
                        </tr>
                        <tr>
                            <td class="label">Ledger Type</td>
                            <td class="value"><strong>${event.actionType}</strong></td>
                        </tr>
                        <tr>
                            <td class="label">Device Description</td>
                            <td class="value"><strong>${event.model}</strong></td>
                        </tr>
                        <tr>
                            <td class="label">IMEI / Serial Number</td>
                            <td class="value" style="font-family: monospace; font-size: 14px;">${event.serialNumber}</td>
                        </tr>
                        <tr>
                            <td class="label">Customer / Seller</td>
                            <td class="value">${event.name}</td>
                        </tr>
                        ${if (!event.phoneNumber.isNullOrBlank()) """<tr><td class="label">Contact Phone</td><td class="value">${event.phoneNumber}</td></tr>""" else ""}
                        ${if (!event.aadhaarNumber.isNullOrBlank()) """<tr><td class="label">Aadhaar Card Number</td><td class="value">${event.aadhaarNumber}</td></tr>""" else ""}
                        <tr>
                            <td class="label">Quantity</td>
                            <td class="value">${event.quantity} units</td>
                        </tr>
                        <tr class="amount-row">
                            <td class="label" style="color: #0f766e;">Total Transaction Cost</td>
                            <td class="value" style="color: #0f766e;">INR ${String.format(java.util.Locale.getDefault(), "%,.2f", event.amount)}</td>
                        </tr>
                    </table>

                    <div class="terms-section">${termsText}</div>
            """.trimIndent())

            if (photoList.isNotEmpty()) {
                htmlBuilder.append("""
                    <div class="photo-container">
                        <div class="photo-title">Attached Device Verification Snapshots</div>
                        <div class="photo-grid">
                """.trimIndent())
                photoList.forEach { p ->
                    if (p.isNotBlank()) {
                        htmlBuilder.append("""
                            <div class="photo-item">
                                <img class="photo-img" src="$p" alt="Snapshot"/>
                            </div>
                        """.trimIndent())
                    }
                }
                htmlBuilder.append("""
                        </div>
                    </div>
                """.trimIndent())
            }

            htmlBuilder.append("""
                    <div class="footer">
                        Authentic Slip Generated via Mobile Gallery ERP. All rights reserved.
                    </div>
                </body>
                </html>
            """.trimIndent())

                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        try {
                            val printManager = context.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
                            val jobName = "Mobile_Gallery_Document_${event.id}"
                            val printAdapter = webView.createPrintDocumentAdapter(jobName)
                            printManager.print(jobName, printAdapter, android.print.PrintAttributes.Builder().build())
                        } catch (pt: Throwable) {
                            Log.e(TAG, "Printing failed", pt)
                            android.widget.Toast.makeText(context, "Printing system not responding: ${pt.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                webView.loadDataWithBaseURL(null, htmlBuilder.toString(), "text/html", "UTF-8", null)
            } catch (t: Throwable) {
                Log.e(TAG, "WebView print failed", t)
                android.widget.Toast.makeText(context, "Failed to launch printing on this device: ${t.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
}
