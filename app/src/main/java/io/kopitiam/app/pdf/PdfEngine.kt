package io.kopitiam.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

/**
 * Real PDF operations backed by Android's own [PdfRenderer] (read) and
 * [PdfDocument] (write). No stubs — every method produces a real file. This is
 * the Android mirror of iOS PDFEngine.swift.
 *
 * Coordinate note: unlike PDFKit (bottom-left origin, points), Android's
 * PdfRenderer/PdfDocument use a top-left origin in points. Redaction rectangles
 * are therefore stored in this engine's own top-left PDF-point space; the
 * RedactCanvas maps view coordinates into it directly.
 */
object PdfEngine {

    class EngineException(message: String) : Exception(message)

    /** Directory FileProvider shares from: <cacheDir>/shared. */
    fun sharedDir(context: Context): File =
        File(context.cacheDir, "shared").apply { mkdirs() }

    fun outFile(context: Context, name: String): File = File(sharedDir(context), name)

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** Open a PdfRenderer over a content Uri (copies to a seekable temp file). */
    private fun openRenderer(context: Context, uri: Uri): Pair<PdfRenderer, File> {
        val tmp = File.createTempFile("src", ".pdf", context.cacheDir)
        context.contentResolver.openInputStream(uri)!!.use { input ->
            FileOutputStream(tmp).use { input.copyTo(it) }
        }
        val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(pfd) to tmp
    }

    private fun openRenderer(file: File): PdfRenderer {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(pfd)
    }

    fun pageCount(context: Context, uri: Uri): Int {
        val (renderer, tmp) = openRenderer(context, uri)
        val n = renderer.pageCount
        renderer.close(); tmp.delete()
        return n
    }

    /** Render one page of a source Uri to a Bitmap at [scale] (1.0 = 72dpi points). */
    fun renderPage(context: Context, uri: Uri, index: Int, scale: Float = 2f): Bitmap? {
        val (renderer, tmp) = openRenderer(context, uri)
        try {
            if (index < 0 || index >= renderer.pageCount) return null
            renderer.openPage(index).use { page ->
                val w = (page.width * scale).toInt().coerceAtLeast(1)
                val h = (page.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp
            }
        } finally {
            renderer.close(); tmp.delete()
        }
    }

    /** Render every page of a source Uri at [scale]. */
    private fun renderAll(context: Context, uri: Uri, scale: Float): List<Bitmap> {
        val (renderer, tmp) = openRenderer(context, uri)
        val out = ArrayList<Bitmap>()
        try {
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    out.add(bmp)
                }
            }
        } finally {
            renderer.close(); tmp.delete()
        }
        return out
    }

    /** Point size (width,height) of each page, for building matching output pages. */
    private fun pageSizes(context: Context, uri: Uri): List<Pair<Int, Int>> {
        val (renderer, tmp) = openRenderer(context, uri)
        val sizes = ArrayList<Pair<Int, Int>>()
        try {
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { sizes.add(it.width to it.height) }
            }
        } finally {
            renderer.close(); tmp.delete()
        }
        return sizes
    }

    // ------------------------------------------------------------------
    // Merge
    // ------------------------------------------------------------------

    /** Combine several PDFs (in order) into one document, page-per-page rasterized. */
    fun merge(context: Context, uris: List<Uri>, outputName: String = "Merged.pdf"): File {
        val doc = PdfDocument()
        var pageNo = 0
        for (uri in uris) {
            val sizes = pageSizes(context, uri)
            val bmps = renderAll(context, uri, scale = 2f)
            for ((i, bmp) in bmps.withIndex()) {
                val (wPt, hPt) = sizes[i]
                writeBitmapPage(doc, bmp, wPt, hPt, pageNo++)
                bmp.recycle()
            }
        }
        if (pageNo == 0) throw EngineException("The operation produced no pages.")
        return finalize(context, doc, outputName)
    }

    // ------------------------------------------------------------------
    // Split — one file per page
    // ------------------------------------------------------------------

    fun splitPerPage(context: Context, uri: Uri): List<File> {
        val sizes = pageSizes(context, uri)
        val bmps = renderAll(context, uri, scale = 2f)
        val results = ArrayList<File>()
        for ((i, bmp) in bmps.withIndex()) {
            val doc = PdfDocument()
            val (wPt, hPt) = sizes[i]
            writeBitmapPage(doc, bmp, wPt, hPt, 0)
            results.add(finalize(context, doc, "Split-${i + 1}.pdf"))
            bmp.recycle()
        }
        if (results.isEmpty()) throw EngineException("The operation produced no pages.")
        return results
    }

    // ------------------------------------------------------------------
    // Extract — chosen pages into one PDF
    // ------------------------------------------------------------------

    fun extract(context: Context, uri: Uri, pages: List<Int>, outputName: String = "Extracted.pdf"): File {
        val sizes = pageSizes(context, uri)
        val bmps = renderAll(context, uri, scale = 2f)
        val doc = PdfDocument()
        var dst = 0
        for (p in pages) {
            if (p < 0 || p >= bmps.size) continue
            val (wPt, hPt) = sizes[p]
            writeBitmapPage(doc, bmps[p], wPt, hPt, dst++)
        }
        bmps.forEach { it.recycle() }
        if (dst == 0) throw EngineException("The operation produced no pages.")
        return finalize(context, doc, outputName)
    }

    // ------------------------------------------------------------------
    // Compress — rasterize + JPEG re-encode
    // ------------------------------------------------------------------

    data class CompressResult(val file: File, val original: Long, val compressed: Long)

    fun compress(
        context: Context, uri: Uri, jpegQuality: Int = 50, scale: Float = 1f,
        outputName: String = "Compressed.pdf",
    ): CompressResult {
        val original = sizeOf(context, uri)
        val sizes = pageSizes(context, uri)
        val bmps = renderAll(context, uri, scale = scale)
        val doc = PdfDocument()
        for ((i, bmp) in bmps.withIndex()) {
            // Round-trip through JPEG to genuinely drop bytes, then draw at page size.
            val jpeg = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, jpeg)
            val bytes = jpeg.toByteArray()
            val reBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: bmp
            val (wPt, hPt) = sizes[i]
            writeBitmapPage(doc, reBmp, wPt, hPt, i)
            if (reBmp !== bmp) reBmp.recycle()
            bmp.recycle()
        }
        if (bmps.isEmpty()) throw EngineException("The operation produced no pages.")
        val file = finalize(context, doc, outputName)
        return CompressResult(file, original, file.length())
    }

    // ------------------------------------------------------------------
    // Redact — TRUE removal by rasterize/flatten with opaque boxes
    // ------------------------------------------------------------------

    /**
     * Paint opaque boxes over regions, then flatten every page to an image so the
     * underlying text/vector content is physically gone (not selectable). [regions]
     * maps a 0-based page index to rectangles in top-left PDF-point space.
     */
    fun redact(
        context: Context, uri: Uri, regions: Map<Int, List<RectF>>,
        outputName: String = "Redacted.pdf",
    ): File {
        val sizes = pageSizes(context, uri)
        val doc = PdfDocument()
        // Render at 2x so the flattened output stays crisp.
        val scale = 2f
        val bmps = renderAll(context, uri, scale = scale)
        val boxPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        for ((i, bmp) in bmps.withIndex()) {
            val canvas = Canvas(bmp)
            for (r in regions[i].orEmpty()) {
                // Region is in PDF points (top-left origin) → bitmap px.
                canvas.drawRect(
                    RectF(r.left * scale, r.top * scale, r.right * scale, r.bottom * scale),
                    boxPaint,
                )
            }
            val (wPt, hPt) = sizes[i]
            writeBitmapPage(doc, bmp, wPt, hPt, i)
            bmp.recycle()
        }
        if (bmps.isEmpty()) throw EngineException("The operation produced no pages.")
        return finalize(context, doc, outputName)
    }

    // ------------------------------------------------------------------
    // Images → PDF (scanning / convert)
    // ------------------------------------------------------------------

    /** One PDF page per image, each page sized to the image's aspect at 72dpi. */
    fun pdfFromImages(context: Context, images: List<Bitmap>, outputName: String = "Scan.pdf"): File {
        if (images.isEmpty()) throw EngineException("The operation produced no pages.")
        val doc = PdfDocument()
        for ((i, bmp) in images.withIndex()) {
            // Size the page to the image (cap the long edge so pages stay reasonable).
            val maxEdge = 1400f
            val s = (maxEdge / maxOf(bmp.width, bmp.height)).coerceAtMost(1f)
            val wPt = (bmp.width * s).toInt().coerceAtLeast(1)
            val hPt = (bmp.height * s).toInt().coerceAtLeast(1)
            writeBitmapPage(doc, bmp, wPt, hPt, i)
        }
        return finalize(context, doc, outputName)
    }

    /** Stack several images vertically on ONE US-Letter page (front+back ID card). */
    fun stackedPdf(context: Context, images: List<Bitmap>, outputName: String = "ID-Card.pdf"): File {
        if (images.isEmpty()) throw EngineException("The operation produced no pages.")
        val pageW = 612; val pageH = 792
        val margin = 36f; val gap = 24f
        val contentW = pageW - margin * 2
        val drawn = images.map { if (it.width > 0) contentW * (it.height.toFloat() / it.width) else 0f }
        val totalGap = gap * (images.size - 1).coerceAtLeast(0)
        val available = pageH - margin * 2 - totalGap
        val natural = drawn.sum()
        val fit = if (natural > available && natural > 0) available / natural else 1f

        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 0).create())
        page.canvas.drawColor(Color.WHITE)
        var y = margin
        for ((i, bmp) in images.withIndex()) {
            val h = drawn[i] * fit
            page.canvas.drawBitmap(bmp, null, RectF(margin, y, margin + contentW, y + h), null)
            y += h + gap
        }
        doc.finishPage(page)
        return finalize(context, doc, outputName)
    }

    // ------------------------------------------------------------------
    // Sample document
    // ------------------------------------------------------------------

    /** Build a small 3-page sample PDF with real text (demo + verification). */
    fun makeSampleDocument(context: Context, outputName: String = "Kopitiam-Sample.pdf"): File {
        val pageW = 612; val pageH = 792
        val doc = PdfDocument()
        val titlePaint = Paint().apply {
            color = Color.rgb(111, 78, 55); textSize = 28f; isFakeBoldText = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(26, 19, 16); textSize = 15f; isAntiAlias = true
        }
        val body = listOf(
            "This is a real, selectable PDF page generated on-device so you can",
            "try the editor immediately. Tap \"Text\" then tap the page to place",
            "editable text. Tap \"Draw\" to ink with your finger. Tap \"Highlight\"",
            "to mark a region. Then Save & Share the result.",
            "",
            "Sensitive line to try Redact on: Account 4111-1111-1111-1111.",
        )
        for (pageNo in 1..3) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo - 1).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            c.drawText("Kopitiam Sample — Page $pageNo", 48f, 80f, titlePaint)
            var ty = 130f
            for (line in body) { c.drawText(line, 48f, ty, bodyPaint); ty += 24f }
            doc.finishPage(page)
        }
        return finalize(context, doc, outputName)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Add one page sized [wPt]x[hPt] points, drawing [bmp] to fill it. */
    private fun writeBitmapPage(doc: PdfDocument, bmp: Bitmap, wPt: Int, hPt: Int, pageIndex: Int) {
        val w = wPt.coerceAtLeast(1); val h = hPt.coerceAtLeast(1)
        val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageIndex).create())
        page.canvas.drawColor(Color.WHITE)
        page.canvas.drawBitmap(bmp, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
        doc.finishPage(page)
    }

    private fun finalize(context: Context, doc: PdfDocument, name: String): File {
        val dest = outFile(context, name)
        FileOutputStream(dest).use { doc.writeTo(it) }
        doc.close()
        return dest
    }

    private fun sizeOf(context: Context, uri: Uri): Long {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
        } catch (_: Exception) { 0L }
    }
}
