package io.kopitiam.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Path as APath
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as CSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kopitiam.app.pdf.PdfEngine
import io.kopitiam.app.pdf.Share
import io.kopitiam.app.ui.components.PrimaryButton
import io.kopitiam.app.ui.components.ToolScaffold
import io.kopitiam.app.ui.components.pressable
import io.kopitiam.app.ui.theme.LocalKopi
import io.kopitiam.app.ui.theme.Radius
import io.kopitiam.app.ui.theme.Space
import kotlin.math.abs
import kotlin.math.min

private enum class EditMode(val label: String, val icon: ImageVector) {
    VIEW("View", Icons.Filled.PanTool),
    TEXT("Text", Icons.Filled.TextFields),
    DRAW("Draw", Icons.Filled.Draw),
    HIGHLIGHT("Highlight", Icons.Filled.Brush),
}

private data class InkStroke(val points: MutableList<Offset> = mutableListOf())
private data class TextMark(val pos: Offset, val text: String)
private data class HighlightMark(val topLeft: Offset, val size: CSize)

/**
 * Real editor: renders the current page, overlays text / finger-ink / highlight
 * annotations captured in view space, and flattens them onto the page bitmap on
 * export (Android has no PDFKit annotation layer, so the honest equivalent is a
 * flattened render). Mirrors iOS EditorView behavior + Sharesheet.
 */
@Composable
fun EditorScreen(onBack: () -> Unit, autoloadSample: Boolean = false) {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var mode by remember { mutableStateOf(EditMode.VIEW) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var error by remember { mutableStateOf<String?>(null) }

    val strokes = remember { mutableStateListOf<InkStroke>() }
    val texts = remember { mutableStateListOf<TextMark>() }
    val highlights = remember { mutableStateListOf<HighlightMark>() }
    var liveStroke by remember { mutableStateOf<InkStroke?>(null) }
    var hlStart by remember { mutableStateOf<Offset?>(null) }
    var hlCurrent by remember { mutableStateOf<Offset?>(null) }

    val bmp = remember(uri) { uri?.let { PdfEngine.renderPage(context, it, 0, scale = 2f) } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { uri = u; strokes.clear(); texts.clear(); highlights.clear() }
    }

    LaunchedEffect(autoloadSample) {
        if (autoloadSample && uri == null) uri = Uri.fromFile(PdfEngine.makeSampleDocument(context))
    }

    ToolScaffold("Edit PDF", onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (bmp == null) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(Space.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Space.md, Alignment.CenterVertically),
                ) {
                    Icon(Icons.Filled.Description, null, tint = kopi.brand, modifier = Modifier.size(56.dp))
                    Text("Open a PDF to edit", color = kopi.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Add text, draw, and highlight on real pages.", color = kopi.textSoft, fontSize = 15.sp)
                    PrimaryButton("Choose a PDF") { picker.launch(arrayOf("application/pdf")) }
                    Text("Try a sample document", color = kopi.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.pressable { uri = Uri.fromFile(PdfEngine.makeSampleDocument(context)) })
                    error?.let { Text(it, color = Color(0xFFD1332B), fontSize = 13.sp) }
                }
            } else {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(kopi.bg)
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(mode, uri) {
                            when (mode) {
                                EditMode.TEXT -> detectTapGestures(onTap = { texts.add(TextMark(it, "Text")) })
                                EditMode.DRAW -> detectDragGestures(
                                    onDragStart = { liveStroke = InkStroke(mutableListOf(it)) },
                                    onDrag = { ch, _ -> liveStroke?.points?.add(ch.position) },
                                    onDragEnd = { liveStroke?.let { strokes.add(it) }; liveStroke = null },
                                )
                                EditMode.HIGHLIGHT -> detectDragGestures(
                                    onDragStart = { hlStart = it; hlCurrent = it },
                                    onDrag = { ch, _ -> hlCurrent = ch.position },
                                    onDragEnd = {
                                        val s = hlStart; val c = hlCurrent
                                        if (s != null && c != null && abs(c.x - s.x) > 6 && abs(c.y - s.y) > 6) {
                                            highlights.add(HighlightMark(Offset(min(s.x, c.x), min(s.y, c.y)), CSize(abs(c.x - s.x), abs(c.y - s.y))))
                                        }
                                        hlStart = null; hlCurrent = null
                                    },
                                )
                                EditMode.VIEW -> {}
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(bmp.asImageBitmap(), null, modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        highlights.forEach {
                            drawRect(Color(0x73FFEB3B), topLeft = it.topLeft, size = it.size)
                        }
                        (strokes + listOfNotNull(liveStroke)).forEach { st ->
                            if (st.points.size > 1) {
                                val p = Path().apply {
                                    moveTo(st.points[0].x, st.points[0].y)
                                    st.points.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                drawPath(p, kopi.accent, style = Stroke(width = 5f, cap = StrokeCap.Round))
                            }
                        }
                        val s = hlStart; val c = hlCurrent
                        if (s != null && c != null) {
                            drawRect(Color(0x40FFEB3B), topLeft = Offset(min(s.x, c.x), min(s.y, c.y)), size = CSize(abs(c.x - s.x), abs(c.y - s.y)))
                        }
                    }
                    // Text marks rendered as overlaid labels.
                    texts.forEach { t ->
                        Text(
                            t.text, color = kopi.accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.absoluteOffset(t.pos),
                        )
                    }
                }

                // Export bar + toolbar
                error?.let { Text(it, color = Color(0xFFD1332B), fontSize = 13.sp, modifier = Modifier.padding(horizontal = Space.md)) }
                Row(Modifier.fillMaxWidth().background(kopi.surface).padding(Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    EditMode.entries.forEach { m ->
                        val on = mode == m
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(Radius.md)).background(if (on) kopi.brand else kopi.surface).pressable { mode = m }.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(m.icon, null, tint = if (on) kopi.onBrand else kopi.text, modifier = Modifier.size(18.dp))
                            Text(m.label, color = if (on) kopi.onBrand else kopi.text, fontSize = 11.sp)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().background(kopi.surface).padding(horizontal = Space.md, vertical = Space.sm)) {
                    PrimaryButton("Save & Share", icon = Icons.Filled.Description) {
                        try {
                            val out = flattenAndExport(context, bmp, canvasSize, strokes, texts, highlights)
                            Share.share(context, listOf(out))
                            error = null
                        } catch (e: Exception) { error = e.message }
                    }
                }
            }
        }
    }
}

private fun Modifier.absoluteOffset(o: Offset): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(o.x.toInt(), o.y.toInt()) }
    }

/** Flatten the annotations (in view space) onto a copy of the page bitmap, then wrap as a 1-page PDF. */
private fun flattenAndExport(
    context: android.content.Context, page: Bitmap, canvas: IntSize,
    strokes: List<InkStroke>, texts: List<TextMark>, highlights: List<HighlightMark>,
): java.io.File {
    // Compute the fitted image rect inside the canvas (ContentScale.Fit, fillMaxWidth).
    val cw = canvas.width.toFloat().coerceAtLeast(1f)
    val fitScale = cw / page.width
    val drawnW = page.width * fitScale
    val drawnH = page.height * fitScale
    val offX = (cw - drawnW) / 2f
    val offY = 0f // fillMaxWidth aligns to top within the Box's centered content; image starts at top of drawn area
    // Map view px → bitmap px.
    val vx = { x: Float -> ((x - offX) / fitScale) }
    val vy = { y: Float -> ((y - offY) / fitScale) }

    val out = page.copy(Bitmap.Config.ARGB_8888, true)
    val c = Canvas(out)
    // Highlights
    val hlPaint = Paint().apply { color = AColor.argb(115, 255, 235, 59) }
    highlights.forEach {
        c.drawRect(vx(it.topLeft.x), vy(it.topLeft.y), vx(it.topLeft.x + it.size.width), vy(it.topLeft.y + it.size.height), hlPaint)
    }
    // Ink
    val inkPaint = Paint().apply {
        color = AColor.rgb(192, 86, 33); style = Paint.Style.STROKE
        strokeWidth = 5f / fitScale; strokeCap = Paint.Cap.ROUND; isAntiAlias = true
    }
    strokes.forEach { st ->
        if (st.points.size > 1) {
            val path = APath()
            path.moveTo(vx(st.points[0].x), vy(st.points[0].y))
            st.points.drop(1).forEach { path.lineTo(vx(it.x), vy(it.y)) }
            c.drawPath(path, inkPaint)
        }
    }
    // Text
    val textPaint = Paint().apply {
        color = AColor.rgb(192, 86, 33); textSize = 16f / fitScale
        isFakeBoldText = true; isAntiAlias = true
    }
    texts.forEach { c.drawText(it.text, vx(it.pos.x), vy(it.pos.y) + textPaint.textSize, textPaint) }

    return PdfEngine.pdfFromImages(context, listOf(out), "Kopitiam-Edited.pdf")
}
