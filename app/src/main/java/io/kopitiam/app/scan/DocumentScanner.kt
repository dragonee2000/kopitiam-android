package io.kopitiam.app.scan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Thin wrapper around Google's ML Kit Document Scanner — the Android mirror of
 * iOS ScannerView.swift (VisionKit). The scanner runs in the Google Play
 * services UI, returning captured page images. The EMULATOR (and any device with
 * no camera / no up-to-date Play services) cannot start it; the caller shows a
 * themed unsupported state instead and never crashes.
 */
object DocumentScanner {

    /** Build scanner options: full-mode filters, unlimited pages, JPEG results. */
    private fun options(pageLimit: Int?): GmsDocumentScannerOptions {
        val b = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        if (pageLimit != null) b.setPageLimit(pageLimit)
        return b.build()
    }

    /**
     * Request the scanner IntentSender. Delivered to [onReady] for launch via an
     * ActivityResult launcher; [onError] fires if Play services cannot provide it
     * (the common emulator path).
     */
    fun requestIntent(
        context: Context,
        activity: Activity,
        pageLimit: Int?,
        onReady: (IntentSender) -> Unit,
        onError: (String) -> Unit,
    ) {
        val scanner = GmsDocumentScanning.getClient(options(pageLimit))
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { onReady(it) }
            .addOnFailureListener {
                onError(it.localizedMessage ?: "Scanning is unavailable on this device.")
            }
    }

    /** Decode the captured pages from a scanner result into Bitmaps, in order. */
    fun pagesFrom(context: Context, data: Intent?): List<Bitmap> {
        val result = GmsDocumentScanningResult.fromActivityResultIntent(data) ?: return emptyList()
        val bmps = ArrayList<Bitmap>()
        for (page in result.pages.orEmpty()) {
            val uri: Uri = page.imageUri
            val bmp = decode(context, uri)
            if (bmp != null) bmps.add(bmp)
        }
        return bmps
    }

    private fun decode(context: Context, uri: Uri): Bitmap? = try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { d, _, _ ->
            d.isMutableRequired = true
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (_: Exception) { null }
}
