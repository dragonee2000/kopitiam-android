package io.kopitiam.app.ui.screens

import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kopitiam.app.Tool
import io.kopitiam.app.pdf.PdfEngine
import io.kopitiam.app.pdf.Share
import io.kopitiam.app.ui.components.AccentButton
import io.kopitiam.app.ui.components.PrimaryButton
import io.kopitiam.app.ui.components.ToolBody
import io.kopitiam.app.ui.components.ToolScaffold
import io.kopitiam.app.ui.components.pressable
import io.kopitiam.app.ui.theme.LocalKopi
import io.kopitiam.app.ui.theme.Radius
import io.kopitiam.app.ui.theme.Space
import java.io.File
import kotlin.math.abs
import kotlin.math.min

// ---------------------------------------------------------------------------
// Shared result bar
// ---------------------------------------------------------------------------

@Composable
private fun ResultBar(files: List<File>) {
    val context = LocalContext.current
    if (files.isEmpty()) return
    val label = if (files.size > 1) "Share ${files.size} files" else "Save & Share"
    AccentButton(label, icon = Icons.Filled.Description) { Share.share(context, files) }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = LocalKopi.current.textSoft, fontSize = 15.sp)
}

@Composable
private fun ErrorText(msg: String?) {
    if (msg != null) Text(msg, color = Color(0xFFD1332B), fontSize = 13.sp)
}

// ---------------------------------------------------------------------------
// Merge
// ---------------------------------------------------------------------------

@Composable
fun MergeScreen(onBack: () -> Unit) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var output by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { picked -> if (picked.isNotEmpty()) uris = uris + picked; output = null }

    ToolScaffold("Merge PDF", onBack) { padding ->
        ToolBody(padding) {
            Hint("Add two or more PDFs; they combine top to bottom in this order.")
            uris.forEachIndexed { idx, u ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(kopi.surface).padding(Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Description, null, tint = kopi.brand, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Space.sm))
                    Text(u.lastPathSegment ?: "PDF ${idx + 1}", color = kopi.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${idx + 1}", color = kopi.textSoft, fontSize = 12.sp)
                }
            }
            AccentButton("Add PDF") { picker.launch(arrayOf("application/pdf")) }
            if (uris.size >= 2) {
                PrimaryButton("Merge ${uris.size} PDFs") {
                    try { output = PdfEngine.merge(context, uris); error = null }
                    catch (e: Exception) { error = e.message }
                }
            }
            output?.let { ResultBar(listOf(it)) }
            ErrorText(error)
        }
    }
}

// ---------------------------------------------------------------------------
// Split (one file per page)
// ---------------------------------------------------------------------------

@Composable
fun SplitScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var count by remember { mutableStateOf(0) }
    var outputs by remember { mutableStateOf<List<File>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri = u; count = runCatching { PdfEngine.pageCount(context, u) }.getOrDefault(0); outputs = emptyList() }
    }

    ToolScaffold("Split PDF", onBack) { padding ->
        ToolBody(padding) {
            if (uri != null) {
                Hint("Splits into one file per page ($count files).")
                PagePreviewGrid(uri!!, count)
                PrimaryButton("Split into $count files") {
                    try { outputs = PdfEngine.splitPerPage(context, uri!!); error = null }
                    catch (e: Exception) { error = e.message }
                }
                if (outputs.isNotEmpty()) ResultBar(outputs)
            } else {
                Hint("Choose a PDF to cut into separate files.")
                PrimaryButton("Choose a PDF") { picker.launch(arrayOf("application/pdf")) }
                SampleButton { u -> uri = u; count = PdfEngine.pageCount(context, u) }
            }
            ErrorText(error)
        }
    }
}

// ---------------------------------------------------------------------------
// Extract (select pages)
// ---------------------------------------------------------------------------

@Composable
fun ExtractScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var count by remember { mutableStateOf(0) }
    var selection by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var output by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri = u; count = runCatching { PdfEngine.pageCount(context, u) }.getOrDefault(0); selection = emptySet() }
    }

    ToolScaffold("Extract Pages", onBack) { padding ->
        ToolBody(padding) {
            if (uri != null) {
                Hint("Select the pages to pull into a new PDF.")
                PageSelectGrid(uri!!, count, selection) { selection = it }
                if (selection.isNotEmpty()) {
                    PrimaryButton("Extract ${selection.size} page${if (selection.size == 1) "" else "s"}") {
                        try { output = PdfEngine.extract(context, uri!!, selection.sorted()); error = null }
                        catch (e: Exception) { error = e.message }
                    }
                }
                output?.let { ResultBar(listOf(it)) }
            } else {
                Hint("Choose a PDF, then pick the pages you need.")
                PrimaryButton("Choose a PDF") { picker.launch(arrayOf("application/pdf")) }
                SampleButton { u -> uri = u; count = PdfEngine.pageCount(context, u) }
            }
            ErrorText(error)
        }
    }
}

// ---------------------------------------------------------------------------
// Compress
// ---------------------------------------------------------------------------

@Composable
fun CompressScreen(onBack: () -> Unit) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var quality by remember { mutableStateOf(0.5f) }
    var output by remember { mutableStateOf<File?>(null) }
    var summary by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri = u; output = null; summary = null }
    }

    ToolScaffold("Compress PDF", onBack) { padding ->
        ToolBody(padding) {
            if (uri != null) {
                Hint("Re-renders pages at lower quality to shrink the file. Best for scans and image-heavy PDFs (text becomes non-selectable).")
                Text("Quality: ${(quality * 100).toInt()}%", color = kopi.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Slider(value = quality, onValueChange = { quality = it }, valueRange = 0.2f..0.9f)
                PrimaryButton("Compress") {
                    try {
                        val r = PdfEngine.compress(context, uri!!, jpegQuality = (quality * 100).toInt())
                        output = r.file
                        summary = "${bytes(r.original)} → ${bytes(r.compressed)} (${pct(r.original, r.compressed)})"
                        error = null
                    } catch (e: Exception) { error = e.message }
                }
                summary?.let { Text(it, color = kopi.accent, fontSize = 15.sp) }
                output?.let { ResultBar(listOf(it)) }
            } else {
                Hint("Choose a PDF to shrink for sharing or upload.")
                PrimaryButton("Choose a PDF") { picker.launch(arrayOf("application/pdf")) }
                SampleButton { u -> uri = u }
            }
            ErrorText(error)
        }
    }
}

// ---------------------------------------------------------------------------
// Redact (TRUE removal via rasterize+flatten)
// ---------------------------------------------------------------------------

@Composable
fun RedactScreen(onBack: () -> Unit) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var count by remember { mutableStateOf(0) }
    var pageIndex by remember { mutableStateOf(0) }
    var boxes by remember { mutableStateOf<Map<Int, List<RectF>>>(emptyMap()) }
    var output by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri = u; count = runCatching { PdfEngine.pageCount(context, u) }.getOrDefault(0); pageIndex = 0; boxes = emptyMap() }
    }

    ToolScaffold("Redact PDF", onBack) { padding ->
        ToolBody(padding) {
            if (uri != null) {
                Text(
                    "Drag to draw redaction boxes. On export, each page you marked is flattened to an image so the covered text is truly removed — not selectable underneath.",
                    color = kopi.textSoft, fontSize = 13.sp,
                )
                RedactCanvas(uri!!, pageIndex, boxes[pageIndex].orEmpty()) { r ->
                    boxes = boxes.toMutableMap().apply { put(pageIndex, (get(pageIndex).orEmpty()) + r) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Page ${pageIndex + 1} of $count", color = kopi.text, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronLeft, "Previous", tint = if (pageIndex > 0) kopi.text else kopi.line,
                        modifier = Modifier.size(32.dp).pressable { if (pageIndex > 0) pageIndex-- })
                    Icon(Icons.Filled.ChevronRight, "Next", tint = if (pageIndex < count - 1) kopi.text else kopi.line,
                        modifier = Modifier.size(32.dp).pressable { if (pageIndex < count - 1) pageIndex++ })
                }
                val total = boxes.values.sumOf { it.size }
                if (total > 0) {
                    PrimaryButton("Redact & flatten ($total box${if (total == 1) "" else "es"})") {
                        try { output = PdfEngine.redact(context, uri!!, boxes); error = null }
                        catch (e: Exception) { error = e.message }
                    }
                }
                output?.let { ResultBar(listOf(it)) }
            } else {
                Hint("Choose a PDF to black out sensitive content.")
                PrimaryButton("Choose a PDF") { picker.launch(arrayOf("application/pdf")) }
                SampleButton { u -> uri = u; count = PdfEngine.pageCount(context, u) }
            }
            ErrorText(error)
        }
    }
}

/**
 * Renders one page and captures drag-drawn boxes in PDF-point (top-left) space,
 * so PdfEngine.redact flattens them exactly. Mirrors iOS RedactCanvas.
 */
@Composable
private fun RedactCanvas(uri: Uri, pageIndex: Int, committed: List<RectF>, onBox: (RectF) -> Unit) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    val bmp = remember(uri, pageIndex) { PdfEngine.renderPage(context, uri, pageIndex, scale = 1.5f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var start by remember { mutableStateOf<Offset?>(null) }
    var current by remember { mutableStateOf<Offset?>(null) }

    // Point dimensions of this page (bitmap px / renderScale).
    val renderScale = 1.5f
    val pageWpt = (bmp?.width ?: 1) / renderScale
    val pageHpt = (bmp?.height ?: 1) / renderScale

    Box(
        Modifier
            .fillMaxWidth()
            .height(420.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(Color.White)
            .border(1.dp, kopi.line, RoundedCornerShape(Radius.md))
            .onSizeChanged { canvasSize = it }
            .pointerInput(uri, pageIndex, canvasSize) {
                detectDragGestures(
                    onDragStart = { start = it; current = it },
                    onDrag = { change, _ -> current = change.position },
                    onDragEnd = {
                        val s = start; val c = current
                        if (s != null && c != null && bmp != null && canvasSize.width > 0) {
                            val fit = fitRect(bmp.width.toFloat(), bmp.height.toFloat(), canvasSize.width.toFloat(), canvasSize.height.toFloat())
                            val offX = (canvasSize.width - fit.first) / 2f
                            val offY = (canvasSize.height - fit.second) / 2f
                            val vx0 = min(s.x, c.x); val vy0 = min(s.y, c.y)
                            val vw = abs(c.x - s.x); val vh = abs(c.y - s.y)
                            if (vw > 8 && vh > 8) {
                                // view px → page points
                                val sx = pageWpt / fit.first
                                val sy = pageHpt / fit.second
                                val left = ((vx0 - offX) * sx).coerceIn(0f, pageWpt)
                                val top = ((vy0 - offY) * sy).coerceIn(0f, pageHpt)
                                val right = ((vx0 + vw - offX) * sx).coerceIn(0f, pageWpt)
                                val bottom = ((vy0 + vh - offY) * sy).coerceIn(0f, pageHpt)
                                onBox(RectF(left, top, right, bottom))
                            }
                        }
                        start = null; current = null
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
        }
        // Overlay committed + live boxes in view space.
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(420.dp)) {
            if (bmp == null) return@Canvas
            val fit = fitRect(bmp.width.toFloat(), bmp.height.toFloat(), size.width, size.height)
            val offX = (size.width - fit.first) / 2f
            val offY = (size.height - fit.second) / 2f
            val sx = fit.first / pageWpt
            val sy = fit.second / pageHpt
            committed.forEach { r ->
                drawRect(
                    Color.Black,
                    topLeft = Offset(offX + r.left * sx, offY + r.top * sy),
                    size = androidx.compose.ui.geometry.Size((r.right - r.left) * sx, (r.bottom - r.top) * sy),
                )
            }
            val s = start; val c = current
            if (s != null && c != null) {
                drawRect(
                    Color.Black.copy(alpha = 0.55f),
                    topLeft = Offset(min(s.x, c.x), min(s.y, c.y)),
                    size = androidx.compose.ui.geometry.Size(abs(c.x - s.x), abs(c.y - s.y)),
                )
            }
        }
    }
}

private fun fitRect(cw: Float, ch: Float, bw: Float, bh: Float): Pair<Float, Float> {
    if (cw <= 0 || ch <= 0) return bw to bh
    val scale = min(bw / cw, bh / ch)
    return cw * scale to ch * scale
}

// ---------------------------------------------------------------------------
// Shared page grids + sample button
// ---------------------------------------------------------------------------

@Composable
private fun SampleButton(onLoad: (Uri) -> Unit) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    Text(
        "Try the sample document", color = kopi.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.pressable {
            val f = PdfEngine.makeSampleDocument(context)
            onLoad(Uri.fromFile(f))
        },
    )
}

@Composable
private fun PagePreviewGrid(uri: Uri, count: Int) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(90.dp),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        items((0 until count).toList()) { i ->
            val bmp = remember(uri, i) { PdfEngine.renderPage(context, uri, i, scale = 0.6f) }
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), null,
                    modifier = Modifier.aspectRatio(0.77f).clip(RoundedCornerShape(Radius.sm)).background(Color.White).border(1.dp, kopi.line, RoundedCornerShape(Radius.sm)),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun PageSelectGrid(uri: Uri, count: Int, selection: Set<Int>, onChange: (Set<Int>) -> Unit) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(92.dp),
        modifier = Modifier.fillMaxWidth().height(320.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        items((0 until count).toList()) { i ->
            val on = selection.contains(i)
            val bmp = remember(uri, i) { PdfEngine.renderPage(context, uri, i, scale = 0.6f) }
            Box(
                Modifier
                    .aspectRatio(0.77f)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(Color.White)
                    .border(if (on) 2.dp else 1.dp, if (on) kopi.accent else kopi.line, RoundedCornerShape(Radius.sm))
                    .pressable { onChange(if (on) selection - i else selection + i) },
            ) {
                if (bmp != null) Image(bmp.asImageBitmap(), null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                Icon(
                    if (on) Icons.Filled.CheckCircle else Icons.Filled.Circle, null,
                    tint = if (on) kopi.accent else kopi.textSoft,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Coming soon
// ---------------------------------------------------------------------------

@Composable
fun ComingSoonScreen(tool: Tool, onBack: () -> Unit) {
    val kopi = LocalKopi.current
    ToolScaffold(tool.title, onBack) { padding ->
        Column(
            Modifier.padding(padding).fillMaxWidth().padding(Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Icon(tool.icon, null, tint = kopi.brand, modifier = Modifier.size(52.dp))
            Text(tool.title, color = kopi.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Coming soon.", color = kopi.textSoft, fontSize = 18.sp)
            Text(tool.blurb, color = kopi.textSoft, fontSize = 15.sp)
        }
    }
}

private fun bytes(n: Long): String {
    if (n <= 0) return "—"
    val kb = n / 1024.0
    return if (kb < 1024) "%.0f KB".format(kb) else "%.1f MB".format(kb / 1024)
}

private fun pct(a: Long, b: Long): String {
    if (a <= 0) return "—"
    val saved = (a - b).toDouble() / a * 100
    return if (saved >= 0) "-${saved.toInt()}%" else "+${(-saved).toInt()}%"
}
