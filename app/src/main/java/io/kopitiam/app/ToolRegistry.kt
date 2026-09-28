package io.kopitiam.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CropRotate
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Rectangle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.ui.graphics.vector.ImageVector

/** Tool categories — mirror iOS ToolCategory. */
enum class ToolCategory(val title: String) {
    PDF("PDF Tools"),
    SCAN("Scan"),
    CONVERT("Convert & Recognize"),
}

/** A single tool. Adding a tool is one entry in [ToolRegistry.all]. */
data class Tool(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val blurb: String,
    val category: ToolCategory,
    val comingSoon: Boolean = false,
)

/**
 * Native source of truth for every tool — the Android mirror of iOS
 * ToolRegistry.swift. Same ids/titles/blurbs/categories. `refine`
 * (Fix Perspective) is marked coming-soon: iOS uses Vision rectangle detection +
 * CIPerspectiveCorrection, which have no first-party Android equivalent without
 * bundling OpenCV; shipped honestly as coming-soon rather than a fake.
 */
object ToolRegistry {
    val all: List<Tool> = listOf(
        // PDF Tools
        Tool("edit", "Edit PDF", Icons.Filled.Edit,
            "Add text, draw, highlight, and sign.", ToolCategory.PDF),
        Tool("merge", "Merge PDF", Icons.Filled.LibraryBooks,
            "Combine several PDFs into one document.", ToolCategory.PDF),
        Tool("split", "Split PDF", Icons.Filled.ContentCut,
            "Cut a PDF into separate files.", ToolCategory.PDF),
        Tool("extract", "Extract Pages", Icons.Filled.Difference,
            "Pull out just the pages you need.", ToolCategory.PDF),
        Tool("compress", "Compress PDF", Icons.Filled.Compress,
            "Shrink a PDF to share or upload.", ToolCategory.PDF),
        Tool("redact", "Redact PDF", Icons.Filled.Rectangle,
            "Black out sensitive text so it is truly removed.", ToolCategory.PDF),
        // Scan
        Tool("document", "Scan Document", Icons.Filled.DocumentScanner,
            "Capture a page straight into a PDF.", ToolCategory.SCAN),
        Tool("id-card", "Scan ID Card", Icons.Filled.Badge,
            "Scan both sides of an ID onto one page.", ToolCategory.SCAN),
        Tool("batch", "Batch Scan", Icons.Filled.LibraryBooks,
            "Scan many pages in a single session.", ToolCategory.SCAN),
        Tool("filters", "Scan Filters", Icons.Filled.AutoAwesome,
            "Enhance, whiten, grayscale, or B&W a page.", ToolCategory.SCAN),
        Tool("refine", "Fix Perspective", Icons.Filled.CropRotate,
            "Drag the corners to straighten a photographed page.",
            ToolCategory.SCAN, comingSoon = true),
        // Convert & Recognize
        Tool("ocr", "Extract Text (OCR)", Icons.Filled.TextFields,
            "Read selectable text off scans with ML Kit.", ToolCategory.CONVERT),
        Tool("convert", "Convert", Icons.AutoMirrored.Filled.CompareArrows,
            "Image↔PDF, PDF→images, and honest text export.", ToolCategory.CONVERT),
    )

    fun tools(category: ToolCategory): List<Tool> = all.filter { it.category == category }

    fun tool(id: String): Tool? = all.firstOrNull { it.id == id }
}
