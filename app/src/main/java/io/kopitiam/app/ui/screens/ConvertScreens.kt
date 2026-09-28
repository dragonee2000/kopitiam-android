package io.kopitiam.app.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kopitiam.app.convert.ConvertEngine
import io.kopitiam.app.ocr.OcrEngine
import io.kopitiam.app.pdf.PdfEngine
import io.kopitiam.app.pdf.Share
import io.kopitiam.app.scan.ScanFilters
import io.kopitiam.app.ui.components.AccentButton
import io.kopitiam.app.ui.components.PrimaryButton
import io.kopitiam.app.ui.components.ToolBody
import io.kopitiam.app.ui.components.ToolScaffold
import io.kopitiam.app.ui.components.pressable
import io.kopitiam.app.ui.theme.LocalKopi
import io.kopitiam.app.ui.theme.Radius
import io.kopitiam.app.ui.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------------------------------------------------------------------
// Extract Text (OCR)
// ---------------------------------------------------------------------------

@Composable
fun OcrScreen(onBack: () -> Unit) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<Uri?>(null) }
    var running by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var textFile by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri = u; text = ""; textFile = null; error = null }
    }

    fun runOcr() {
        val u = uri ?: return
        running = true; error = null
        scope.launch {
            try {
                val recognized = withContext(Dispatchers.Default) { OcrEngine.recognizeDocument(context, u) }
                text = recognized
                textFile = OcrEngine.writeTextFile(context, recognized)
            } catch (e: Exception) { error = e.message }
            running = false
        }
    }

    ToolScaffold("Extract Text (OCR)", onBack) { padding ->
        ToolBody(padding) {
            if (uri != null) {
                Text("Reads the text off each page with ML Kit (on-device). Great for scans and photos of documents.", color = kopi.textSoft, fontSize = 15.sp)
                PrimaryButton(if (running) "Recognizing…" else "Extract text", icon = Icons.Filled.Description, loading = running, enabled = !running) { runOcr() }
                if (text.isNotEmpty()) {
                    Text("Recognized text", color = kopi.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text, color = kopi.text, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(kopi.surface).border(1.dp, kopi.line, RoundedCornerShape(Radius.md)).padding(Space.md),
                    )
                    textFile?.let { AccentButton("Save & Share .txt", icon = Icons.Filled.Description) { Share.share(context, listOf(it)) } }
                }
            } else {
                Text("Choose a PDF, or load the sample, to pull out its text.", color = kopi.textSoft, fontSize = 15.sp)
                PrimaryButton("Choose a PDF") { picker.launch(arrayOf("application/pdf")) }
                Text("Try the sample document", color = kopi.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.pressable { uri = Uri.fromFile(PdfEngine.makeSampleDocument(context)) })
            }
            error?.let { Text(it, color = Color(0xFFD1332B), fontSize = 13.sp) }
        }
    }
}

// ---------------------------------------------------------------------------
// Scan Filters
// ---------------------------------------------------------------------------

@Composable
fun ScanFilterScreen(onBack: () -> Unit) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    var base by remember { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableStateOf(ScanFilters.Mode.ORIGINAL) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var output by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) loadFilterBase(context, u)?.let { base = it; preview = it; mode = ScanFilters.Mode.ORIGINAL; output = null }
    }

    fun select(m: ScanFilters.Mode) {
        mode = m
        base?.let { preview = ScanFilters.apply(m, it) }
    }

    ToolScaffold("Scan Filters", onBack) { padding ->
        ToolBody(padding) {
            val b = base
            if (b != null) {
                Text("Clean up a scanned page: enhance, whiten the background, or go high-contrast black & white. Live preview below.", color = kopi.textSoft, fontSize = 15.sp)
                Box(Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(Radius.md)).background(Color.White).border(1.dp, kopi.line, RoundedCornerShape(Radius.md)), contentAlignment = Alignment.Center) {
                    (preview ?: b).let { Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    ScanFilters.Mode.entries.forEach { m ->
                        val on = mode == m
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pressable { select(m) }) {
                            Box(
                                Modifier.size(64.dp).clip(RoundedCornerShape(Radius.sm)).background(kopi.surface).border(if (on) 2.dp else 1.dp, if (on) kopi.accent else kopi.line, RoundedCornerShape(Radius.sm)),
                                contentAlignment = Alignment.Center,
                            ) {
                                val thumb = remember(m, b) { ScanFilters.apply(m, b) }
                                Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp).clip(RoundedCornerShape(Radius.sm)))
                            }
                            Text(m.label, color = if (on) kopi.accent else kopi.textSoft, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
                PrimaryButton("Apply & make PDF") {
                    try {
                        val filtered = ScanFilters.apply(mode, b)
                        output = PdfEngine.pdfFromImages(context, listOf(filtered), "Filtered-${mode.label}.pdf")
                        error = null
                    } catch (e: Exception) { error = e.message }
                }
                output?.let { AccentButton("Save & Share", icon = Icons.Filled.Description) { Share.share(context, listOf(it)) } }
            } else {
                Text("Choose a PDF (its first page becomes the preview) or load the sample.", color = kopi.textSoft, fontSize = 15.sp)
                PrimaryButton("Choose a PDF") { picker.launch(arrayOf("application/pdf")) }
                Text("Try the sample document", color = kopi.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.pressable {
                        val f = PdfEngine.makeSampleDocument(context)
                        loadFilterBase(context, Uri.fromFile(f))?.let { base = it; preview = it }
                    })
            }
            error?.let { Text(it, color = Color(0xFFD1332B), fontSize = 13.sp) }
        }
    }
}

private fun loadFilterBase(context: android.content.Context, uri: Uri): Bitmap? =
    PdfEngine.renderPage(context, uri, 0, scale = 2f)

// ---------------------------------------------------------------------------
// Convert
// ---------------------------------------------------------------------------

private enum class ConvertJob(val label: String) {
    IMAGES_TO_PDF("Images → PDF"),
    PDF_TO_IMAGES("PDF → Images"),
    PDF_TO_WORD("PDF → Word"),
    PDF_TO_EXCEL("PDF → Excel"),
}

@Composable
fun ConvertScreen(onBack: () -> Unit) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf(ConvertJob.IMAGES_TO_PDF) }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfPages by remember { mutableStateOf(0) }
    var images by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var format by remember { mutableStateOf(ConvertEngine.RasterFormat.JPG) }
    var quality by remember { mutableStateOf(0.9f) }
    var outputs by remember { mutableStateOf<List<File>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reset() { outputs = emptyList(); status = null; error = null }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { pdfUri = u; pdfPages = runCatching { PdfEngine.pageCount(context, u) }.getOrDefault(0); reset() }
    }
    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked ->
        images = picked.mapNotNull { decodeImage(context, it) }; reset()
    }

    ToolScaffold("Convert", onBack) { padding ->
        ToolBody(padding) {
            Text("Convert between PDF and images, and export text. Fidelity is shown honestly for each mode.", color = kopi.textSoft, fontSize = 15.sp)

            // Job picker
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                ConvertJob.entries.forEach { j ->
                    val on = job == j
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(if (on) kopi.brand else kopi.surface).border(1.dp, kopi.line, RoundedCornerShape(Radius.md)).pressable { job = j; reset() }.padding(Space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(j.label, color = if (on) kopi.onBrand else kopi.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Honest fidelity note
            val note = when (job) {
                ConvertJob.IMAGES_TO_PDF -> "Lossless — each image becomes a full page." to kopi.textSoft
                ConvertJob.PDF_TO_IMAGES -> "Each page is rendered to a crisp ${format.label}." to kopi.textSoft
                ConvertJob.PDF_TO_WORD -> "Text export — layout is NOT preserved. Opens in Word as plain paragraphs." to kopi.accent
                ConvertJob.PDF_TO_EXCEL -> "Only when the page has clearly tabular text; otherwise it stays coming-soon (we never ship a garbage spreadsheet)." to kopi.accent
            }
            Text(note.first, color = note.second, fontSize = 13.sp)

            when (job) {
                ConvertJob.IMAGES_TO_PDF -> {
                    if (images.isEmpty()) {
                        PrimaryButton("Choose images") { imgPicker.launch(arrayOf("image/*")) }
                    } else {
                        Text("${images.size} image${if (images.size == 1) "" else "s"} selected.", color = kopi.text, fontSize = 15.sp)
                        PrimaryButton("Make PDF") {
                            try { outputs = listOf(ConvertEngine.imagesToPdf(context, images)); status = "Created a ${images.size}-page PDF."; error = null }
                            catch (e: Exception) { error = e.message }
                        }
                        Text("Choose different images", color = kopi.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressable { imgPicker.launch(arrayOf("image/*")) })
                    }
                }
                else -> {
                    if (pdfUri == null) {
                        PrimaryButton("Choose a PDF") { pdfPicker.launch(arrayOf("application/pdf")) }
                        Text("Try the sample document", color = kopi.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.pressable { val f = PdfEngine.makeSampleDocument(context); pdfUri = Uri.fromFile(f); pdfPages = PdfEngine.pageCount(context, pdfUri!!); reset() })
                    } else {
                        Text("$pdfPages page${if (pdfPages == 1) "" else "s"} loaded.", color = kopi.text, fontSize = 15.sp)
                        when (job) {
                            ConvertJob.PDF_TO_IMAGES -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                    ConvertEngine.RasterFormat.entries.forEach { f ->
                                        val on = format == f
                                        Box(Modifier.clip(RoundedCornerShape(Radius.pill)).background(if (on) kopi.brand else kopi.surface).border(1.dp, kopi.line, RoundedCornerShape(Radius.pill)).pressable { format = f }.padding(horizontal = Space.md, vertical = 6.dp)) {
                                            Text(f.label, color = if (on) kopi.onBrand else kopi.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                if (format == ConvertEngine.RasterFormat.JPG) {
                                    Text("Quality: ${(quality * 100).toInt()}%", color = kopi.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Slider(value = quality, onValueChange = { quality = it }, valueRange = 0.3f..1f)
                                }
                                PrimaryButton("Export $pdfPages ${format.label}") {
                                    try { outputs = ConvertEngine.pdfToImages(context, pdfUri!!, format, (quality * 100).toInt()); status = "Exported ${outputs.size} ${format.label} file${if (outputs.size == 1) "" else "s"}."; error = null }
                                    catch (e: Exception) { error = e.message }
                                }
                            }
                            ConvertJob.PDF_TO_WORD -> PrimaryButton(if (running) "Working…" else "Export .docx (text)", icon = Icons.Filled.Description, loading = running, enabled = !running) {
                                running = true; error = null
                                scope.launch {
                                    try { val f = withContext(Dispatchers.Default) { ConvertEngine.pdfToWord(context, pdfUri!!) }; outputs = listOf(f); status = "Created a plain-text .docx (layout not preserved)." }
                                    catch (e: Exception) { error = e.message }
                                    running = false
                                }
                            }
                            ConvertJob.PDF_TO_EXCEL -> PrimaryButton(if (running) "Checking…" else "Check for a table & export", loading = running, enabled = !running) {
                                running = true; error = null; status = null
                                scope.launch {
                                    try {
                                        val text = withContext(Dispatchers.Default) { ConvertEngine.extractText(context, pdfUri!!) }
                                        val rows = ConvertEngine.detectTable(text)
                                        if (rows != null) { outputs = listOf(ConvertEngine.rowsToExcel(context, rows)); status = "Detected a ${rows.size}×${rows.firstOrNull()?.size ?: 0} table — exported .xlsx." }
                                        else { outputs = emptyList(); status = "No tabular text detected. Excel export stays coming-soon here — a plain .xlsx would be garbage. Try PDF → Word for the text." }
                                    } catch (e: Exception) { error = e.message }
                                    running = false
                                }
                            }
                            else -> {}
                        }
                        Text("Choose a different PDF", color = kopi.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.pressable { pdfPicker.launch(arrayOf("application/pdf")) })
                    }
                }
            }

            if (outputs.isNotEmpty()) AccentButton(if (outputs.size > 1) "Share ${outputs.size} files" else "Save & Share", icon = Icons.Filled.Description) { Share.share(context, outputs) }
            status?.let { Text(it, color = kopi.accent, fontSize = 14.sp) }
            error?.let { Text(it, color = Color(0xFFD1332B), fontSize = 13.sp) }
        }
    }
}

private fun decodeImage(context: android.content.Context, uri: Uri): Bitmap? = try {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { d, _, _ -> d.isMutableRequired = true; d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
} catch (_: Exception) { null }
