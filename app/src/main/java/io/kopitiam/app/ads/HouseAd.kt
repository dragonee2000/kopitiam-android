package io.kopitiam.app.ads

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.kopitiam.app.ui.theme.LocalKopi
import io.kopitiam.app.ui.theme.Radius
import io.kopitiam.app.ui.theme.Space
import io.kopitiam.app.ui.theme.rememberReduceMotion
import kotlinx.coroutines.delay

/** Rotating house-ad directions — mirror iOS HouseKind. */
enum class HouseKind { STEAM, TOOLS, SWEEP }

/** Ad copy — exact strings from iOS AdCopy. */
object AdCopy {
    const val contactEmail = "hello@kopitiam.io"
    const val bookingUrl = "https://calendly.com/ethan-kopitiam/30min"
    const val enquirySubject = "Advertising enquiry — Kopitiam"
    val enquiryBody = listOf(
        "Hi Kopitiam team,",
        "",
        "We'd like to advertise on Kopitiam. A few details:",
        "",
        "• Company / product:",
        "• What you want to promote:",
        "• Target audience:",
        "• Monthly budget (rough is fine):",
        "• Preferred start date:",
        "",
        "Thanks!",
    ).joinToString("\n")

    fun openEmail(context: Context) {
        val uri = Uri.parse(
            "mailto:$contactEmail" +
                "?subject=${Uri.encode(enquirySubject)}" +
                "&body=${Uri.encode(enquiryBody)}"
        )
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openBooking(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(bookingUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

/** Cross-promo of Kopitiam's own tools — mirror iOS HouseAdData.toolPromos. */
data class ToolPromo(val head: String, val sub: String, val toolId: String, val soon: Boolean = false)

private val toolPromos = listOf(
    ToolPromo("Merge PDFs", "Combine files in seconds — free", "merge"),
    ToolPromo("Fill any form", "Auto-detected fields, no typing hunts", "edit"),
    ToolPromo("Split & extract", "Pull the pages you need", "split"),
    ToolPromo("No sign-up", "Open a tab, drop a file, done", "edit"),
    ToolPromo("Convert PDFs", "To Word, Excel & images — coming soon", "convert", soon = true),
    ToolPromo("Compress PDFs", "Shrink big files to share — coming soon", "compress", soon = true),
)

/**
 * The single native ad slot (mirror iOS AdSlotView). No ad-server integration —
 * it renders a HOUSE ad directly. The kind is chosen ONCE per slot lifetime so
 * it doesn't reroll on recomposition.
 */
@Composable
fun AdSlot(modifier: Modifier = Modifier, force: HouseKind? = null, onOpenTool: (String) -> Unit = {}) {
    val kopi = LocalKopi.current
    val kind = remember { force ?: HouseKind.entries.random() }
    Column(modifier) {
        Text(
            "ADVERTISE HERE",
            color = kopi.textSoft.copy(alpha = 0.8f),
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp,
            modifier = Modifier.padding(bottom = Space.xs),
        )
        when (kind) {
            HouseKind.STEAM -> HouseSteam()
            HouseKind.TOOLS -> HouseTools(onOpenTool)
            HouseKind.SWEEP -> HouseSweep()
        }
    }
}

@Composable
private fun AdCard(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val kopi = LocalKopi.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(Radius.lg))
            .background(kopi.surface)
            .border(1.dp, kopi.line, RoundedCornerShape(Radius.lg))
            .padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Pill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, ghost: Boolean, onClick: () -> Unit) {
    val kopi = LocalKopi.current
    Row(
        Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .then(if (ghost) Modifier.border(1.dp, kopi.line, RoundedCornerShape(Radius.pill)) else Modifier.background(kopi.brand))
            .clickable { onClick() }
            .padding(horizontal = Space.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (ghost) kopi.text else kopi.onBrand, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = if (ghost) kopi.text else kopi.onBrand, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun HouseSteam() {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    val reduce = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "steam")
    // Gentle brand-motif pulse on the cup; frozen under reduce-motion.
    val pulse by transition.animateFloat(
        initialValue = 0.55f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val cupAlpha = if (reduce) 1f else pulse
    AdCard {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.LocalCafe, null, tint = kopi.brand.copy(alpha = cupAlpha), modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text("Advertise on Kopitiam", color = kopi.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Reach people getting real work done.", color = kopi.textSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.sm))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill("Email us", Icons.Filled.Email, ghost = false) { AdCopy.openEmail(context) }
            Pill("Book a call", Icons.Filled.CalendarMonth, ghost = true) { AdCopy.openBooking(context) }
        }
    }
}

@Composable
private fun HouseTools(onOpenTool: (String) -> Unit) {
    val kopi = LocalKopi.current
    val reduce = rememberReduceMotion()
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(reduce) {
        if (reduce) return@LaunchedEffect
        while (true) { delay(2800); i = (i + 1) % toolPromos.size }
    }
    val p = toolPromos[i]
    Box(Modifier.clickable { onOpenTool(p.toolId) }) {
        AdCard {
            Column(Modifier.weight(1f)) {
                Text(
                    if (p.soon) "COMING SOON" else "FREE ON KOPITIAM",
                    color = kopi.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                )
                Text(p.head, color = kopi.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(p.sub, color = kopi.textSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Space.sm))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (p.soon) "Preview" else "Open", color = kopi.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = kopi.accent, modifier = Modifier.size(14.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    toolPromos.indices.forEach { n ->
                        Box(
                            Modifier.size(5.dp).clip(CircleShape)
                                .background(if (n == i) kopi.accent else kopi.line)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HouseSweep() {
    val kopi = LocalKopi.current
    val context = LocalContext.current
    Box(Modifier.clickable { AdCopy.openEmail(context) }) {
        AdCard {
            Box(Modifier.width(3.dp).height(40.dp).clip(RoundedCornerShape(Radius.pill)).background(kopi.accent))
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("Advertise here", color = kopi.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Reach people getting real work done.", color = kopi.textSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Email, null, tint = kopi.accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(AdCopy.contactEmail, color = kopi.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}
