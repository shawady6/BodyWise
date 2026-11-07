package com.shawady.bodywise.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawady.bodywise.util.decodeThumbnail
import com.shawady.bodywise.util.formatNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Card heading: emoji badge + title. */
@Composable
fun SectionTitle(emoji: String, title: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(34.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 17.sp) }
        Text(title, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun SmallStat(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(13.dp)) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 19.sp)
        }
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun PhotoThumb(file: File, modifier: Modifier = Modifier, height: Int = 190) {
    val bitmap by produceState<ImageBitmap?>(null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(file.absolutePath)?.asImageBitmap() }
    }
    val image = bitmap
    if (image != null) {
        Image(image, null, modifier = modifier.fillMaxWidth().height(height.dp), contentScale = ContentScale.Fit)
    } else {
        Box(modifier.fillMaxWidth().height(height.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    }
}

/** Simple line chart; time runs left to right regardless of the app's RTL layout. */
@Composable
fun LineChart(points: List<Pair<LocalDate, Double>>, unit: String, modifier: Modifier = Modifier, target: Double? = null,
    secondary: List<Pair<LocalDate, Double>>? = null) {
    if (points.size < 2) {
        Hint("سجّل قياسين على الأقل لعرض الرسم البياني.", modifier)
        return
    }
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val targetColor = Color(0xFFE0A030)
    val values = points.map { it.second } + (secondary?.map { it.second } ?: emptyList()) + listOfNotNull(target)
    val minValue = values.min()
    val maxValue = values.max()
    val pad = (if (maxValue - minValue > 0.0) maxValue - minValue else 1.0) * 0.15
    val low = minValue - pad
    val high = maxValue + pad
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Hint("${formatNumber(minValue)} $unit")
                Hint("${formatNumber(maxValue)} $unit")
            }
            Canvas(Modifier.fillMaxWidth().height(160.dp).padding(vertical = 8.dp)) {
                val w = size.width
                val h = size.height
                for (i in 0..2) {
                    val y = h * i / 2f
                    drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
                }
                val firstDay = points.first().first.toEpochDay()
                val span = (points.last().first.toEpochDay() - firstDay).coerceAtLeast(1L).toFloat()
                fun px(date: LocalDate): Float = w * (date.toEpochDay() - firstDay) / span
                fun py(value: Double): Float = h - (h * ((value - low) / (high - low))).toFloat()
                if (target != null) {
                    drawLine(
                        targetColor, Offset(0f, py(target)), Offset(w, py(target)),
                        strokeWidth = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    )
                }
                val path = Path()
                points.forEachIndexed { index, (date, value) ->
                    if (index == 0) path.moveTo(px(date), py(value)) else path.lineTo(px(date), py(value))
                }
                drawPath(path, primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                if (secondary != null && secondary.size >= 2) {
                    val path2 = Path()
                    secondary.forEachIndexed { index, (date, value) ->
                        if (index == 0) path2.moveTo(px(date), py(value)) else path2.lineTo(px(date), py(value))
                    }
                    drawPath(path2, targetColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                points.forEach { (date, value) -> drawCircle(primary, radius = 4.dp.toPx(), center = Offset(px(date), py(value))) }
            }
            if (target != null) Hint("الخط المتقطع: الهدف ${formatNumber(target)} $unit")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Hint(points.first().first.toString())
                Hint(points.last().first.toString())
            }
        }
    }
}


/** Heading for a person's group of medications / appointments. */
@Composable
fun PersonHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        "👤 $text", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth().padding(top = 4.dp)
    )
}

/** "الدواء لـ / الموعد لـ" chooser: أنا first, then the people. */
@Composable
fun PersonPicker(label: String, people: List<com.shawady.bodywise.data.Person>, selectedId: String?, onSelect: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(selected = selectedId == null, onClick = { onSelect(null) }, label = { Text(com.shawady.bodywise.data.ME_LABEL) })
            people.forEach { p ->
                androidx.compose.material3.FilterChip(selected = selectedId == p.id, onClick = { onSelect(p.id) }, label = { Text(p.name) })
            }
        }
    }
}
