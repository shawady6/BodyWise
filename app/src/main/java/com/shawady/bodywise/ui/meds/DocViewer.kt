package com.shawady.bodywise.ui.meds

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.shawady.bodywise.data.MedicalDoc
import com.shawady.bodywise.data.isPdfFile
import com.shawady.bodywise.data.pdfDisplayName
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.util.decodeThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Opens a stored PDF in whatever PDF app the phone has. */
fun openPdf(context: Context, vm: BodyWiseViewModel, name: String) {
    val file = vm.medPhotoFile(name)
    if (!file.isFile) { vm.toast = "الملف مش موجود"; return }
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { vm.toast = "مفيش تطبيق على الموبايل بيفتح ملفات PDF" }
}

/** Small preview tiles for a document's files; images open the viewer, PDFs open the PDF app. */
@Composable
fun DocFilesStrip(vm: BodyWiseViewModel, files: List<String>, onOpenImage: (Int) -> Unit) {
    val context = LocalContext.current
    val images = files.filter { !isPdfFile(it) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (images.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                images.take(4).forEachIndexed { i, name ->
                    Box(Modifier.size(74.dp).clip(MaterialTheme.shapes.medium).clickable { onOpenImage(i) }) {
                        Thumb(vm.medPhotoFile(name))
                        if (i == 3 && images.size > 4) {
                            Surface(color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f), modifier = Modifier.fillMaxSize()) {
                                Box(contentAlignment = Alignment.Center) { Text("+${images.size - 3}", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                            }
                        }
                    }
                }
            }
        }
        files.filter { isPdfFile(it) }.forEach { name ->
            Surface(
                shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable { openPdf(context, vm, name) }
            ) { Text("📄 ${pdfDisplayName(name)}", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 14.sp, maxLines = 1) }
        }
    }
}

@Composable
private fun Thumb(file: File) {
    val bitmap by produceState<ImageBitmap?>(null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(file.absolutePath, 300)?.asImageBitmap() }
    }
    val image = bitmap
    if (image != null) Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
}

/** Full-screen viewer: swipe between a document's photos, pinch or double-tap to zoom. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocViewer(vm: BodyWiseViewModel, doc: MedicalDoc, startIndex: Int, onDismiss: () -> Unit) {
    val images = doc.files.filter { !isPdfFile(it) }
    if (images.isEmpty()) { onDismiss(); return }
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, images.lastIndex), pageCount = { images.size })
    var zoomed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(state = pager, userScrollEnabled = !zoomed, modifier = Modifier.fillMaxSize()) { page ->
                    ZoomableImage(vm.medPhotoFile(images[page]), onZoomChange = { zoomed = it })
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp).align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "إغلاق", tint = androidx.compose.ui.graphics.Color.White) }
                    Text(
                        doc.title.ifBlank { doc.kind } + if (images.size > 1) "  (${pager.currentPage + 1}/${images.size})" else "",
                        color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(file: File, onZoomChange: (Boolean) -> Unit) {
    val bitmap by produceState<ImageBitmap?>(null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(file.absolutePath, 2048)?.asImageBitmap() }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val image = bitmap
    if (image == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Image(
        image, null,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    onZoomChange(scale > 1f)
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                    onZoomChange(scale > 1f)
                }
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        contentScale = ContentScale.Fit
    )
}
