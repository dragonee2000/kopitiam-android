package io.kopitiam.app.convert

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import io.kopitiam.app.ocr.OcrEngine
import io.kopitiam.app.pdf.PdfEngine
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Group B "Convert" operations — the Android mirror of iOS ConvertEngine.swift.
 * Fidelity is deliberately honest: image↔PDF and PDF→images are raster ops;
 * PDF→Word is a plain-text WordprocessingML .docx (layout NOT preserved);
 * PDF→Excel is offered only when tabular text is detected, otherwise the caller
 * marks it coming-soon. A .docx/.xlsx is a ZIP of XML parts — built here with
 * java.util.zip (no third-party dependency).
 */
object ConvertEngine {

    class ConvertException(message: String) : Exception(message)

    enum class RasterFormat(val label: String, val ext: String) {
        JPG("JPG", "jpg"), PNG("PNG", "png")
    }

    // ------------------------------------------------------------------
    // image → PDF
    // ------------------------------------------------------------------

    fun imagesToPdf(context: Context, images: List<Bitmap>, name: String = "Converted.pdf"): File =
        PdfEngine.pdfFromImages(context, images, name)

    // ------------------------------------------------------------------
    // PDF → images
    // ------------------------------------------------------------------

    fun pdfToImages(
        context: Context, uri: Uri, format: RasterFormat, quality: Int = 90, scale: Float = 2f,
    ): List<File> {
        val count = PdfEngine.pageCount(context, uri)
        val results = ArrayList<File>()
        for (i in 0 until count) {
            val bmp = PdfEngine.renderPage(context, uri, i, scale) ?: continue
            val dest = PdfEngine.outFile(context, "Page-${i + 1}.${format.ext}")
            FileOutputStream(dest).use {
                when (format) {
                    RasterFormat.JPG -> bmp.compress(Bitmap.CompressFormat.JPEG, quality, it)
                    RasterFormat.PNG -> bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            bmp.recycle()
            results.add(dest)
        }
        if (results.isEmpty()) throw ConvertException("The conversion produced nothing.")
        return results
    }

    // ------------------------------------------------------------------
    // Text extraction (OCR of rendered pages)
    // ------------------------------------------------------------------

    suspend fun extractText(context: Context, uri: Uri): String {
        // PdfRenderer has no text layer accessor, so extract via OCR (mirrors the
        // iOS OCR fallback path). Honest: works for scans and text pages alike.
        return OcrEngine.recognizeDocument(context, uri)
    }

    // ------------------------------------------------------------------
    // PDF → Word (.docx, plain text)
    // ------------------------------------------------------------------

    suspend fun pdfToWord(context: Context, uri: Uri, name: String = "Converted.docx"): File {
        val text = extractText(context, uri)
        val paragraphs = text.split("\n").joinToString("") { paragraphXml(it) }
        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
<w:body>$paragraphs<w:sectPr/></w:body></w:document>"""
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""
        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""
        val dest = PdfEngine.outFile(context, name)
        writeZip(dest, listOf(
            "[Content_Types].xml" to contentTypes.toByteArray(),
            "_rels/.rels" to rootRels.toByteArray(),
            "word/document.xml" to documentXml.toByteArray(),
        ))
        return dest
    }

    private fun paragraphXml(line: String): String =
        "<w:p><w:r><w:t xml:space=\"preserve\">${xmlEscape(line)}</w:t></w:r></w:p>"

    // ------------------------------------------------------------------
    // Tabular detection + PDF → Excel (.xlsx)
    // ------------------------------------------------------------------

    /** Heuristic table detector — mirrors iOS detectTable. */
    fun detectTable(text: String): List<List<String>>? {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return null
        fun cols(s: String): List<String> =
            (if (s.contains("\t")) s.split("\t") else s.split(Regex(" {2,}")))
                .map { it.trim() }.filter { it.isNotEmpty() }
        val rows = lines.map { cols(it) }
        val counts = rows.map { it.size }
        val modal = middleMode(counts) ?: return null
        if (modal < 2) return null
        val matching = counts.count { it == modal }
        if (matching < 2 || matching.toDouble() / counts.size < 0.6) return null
        return rows.map { row ->
            val r = row.toMutableList()
            while (r.size < modal) r.add("")
            if (r.size > modal) r.subList(modal, r.size).clear()
            r
        }
    }

    fun rowsToExcel(context: Context, rows: List<List<String>>, name: String = "Converted.xlsx"): File {
        if (rows.isEmpty()) throw ConvertException("The conversion produced nothing.")
        val sb = StringBuilder()
        for ((r, row) in rows.withIndex()) {
            sb.append("<row r=\"${r + 1}\">")
            for ((c, value) in row.withIndex()) {
                val ref = "${columnLetter(c)}${r + 1}"
                sb.append("<c r=\"$ref\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xmlEscape(value)}</t></is></c>")
            }
            sb.append("</row>")
        }
        val sheetXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<sheetData>$sb</sheetData></worksheet>"""
        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>"""
        val workbookRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
        val dest = PdfEngine.outFile(context, name)
        writeZip(dest, listOf(
            "[Content_Types].xml" to contentTypes.toByteArray(),
            "_rels/.rels" to rootRels.toByteArray(),
            "xl/workbook.xml" to workbookXml.toByteArray(),
            "xl/_rels/workbook.xml.rels" to workbookRels.toByteArray(),
            "xl/worksheets/sheet1.xml" to sheetXml.toByteArray(),
        ))
        return dest
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun writeZip(dest: File, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(FileOutputStream(dest)).use { zip ->
            for ((path, bytes) in entries) {
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private fun columnLetter(index: Int): String {
        var i = index
        var s = ""
        do {
            s = ('A' + (i % 26)) + s
            i = i / 26 - 1
        } while (i >= 0)
        return s
    }

    private fun middleMode(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val counts = values.groupingBy { it }.eachCount()
        val max = counts.values.max()
        val candidates = counts.filter { it.value == max }.keys.sorted()
        return if (candidates.isEmpty()) null else candidates[candidates.size / 2]
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}
