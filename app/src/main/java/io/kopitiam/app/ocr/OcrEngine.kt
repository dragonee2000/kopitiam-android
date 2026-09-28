package io.kopitiam.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.kopitiam.app.pdf.PdfEngine
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit Text Recognition (on-device Latin script) — the Android mirror of iOS
 * OCREngine.swift (Apple Vision). Renders each PDF page to a bitmap and runs the
 * recognizer, joining page results with page-break headers.
 */
object OcrEngine {

    class OcrException(message: String) : Exception(message)

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Recognize text on a single bitmap. */
    suspend fun recognize(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { cont.resume(it.text) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    /** Recognize text across every page of a PDF Uri, concatenated with breaks. */
    suspend fun recognizeDocument(context: Context, uri: Uri): String {
        val pageCount = PdfEngine.pageCount(context, uri)
        val chunks = ArrayList<String>()
        for (i in 0 until pageCount) {
            val bmp = PdfEngine.renderPage(context, uri, i, scale = 2f) ?: continue
            val text = try { recognize(bmp) } catch (_: Exception) { "" }
            bmp.recycle()
            if (text.isNotBlank()) chunks.add("— Page ${i + 1} —\n$text")
        }
        val all = chunks.joinToString("\n\n")
        if (all.isBlank()) throw OcrException("No text was found on this document.")
        return all
    }

    /** Write recognized text to a shareable .txt file. */
    fun writeTextFile(context: Context, text: String, name: String = "Extracted-Text.txt"): File {
        val dest = PdfEngine.outFile(context, name)
        dest.writeText(text)
        return dest
    }
}
