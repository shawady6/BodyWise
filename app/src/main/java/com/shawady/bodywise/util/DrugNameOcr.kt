package com.shawady.bodywise.util

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private val noiseWords = setOf(
    "tablet", "tablets", "capsule", "capsules", "film", "coated", "each", "contains", "contain", "store", "keep",
    "below", "above", "children", "reach", "of", "the", "and", "for", "use", "dosage", "batch", "exp", "mfg", "price",
    "egypt", "pharma", "pharmaceuticals", "pharmaceutical", "manufactured", "made", "in", "by", "oral", "solution",
    "syrup", "injection", "cream", "ointment", "gel", "drops", "suspension", "strip", "box", "pack", "see", "leaflet",
    "inside", "mg", "ml", "gm", "mcg", "iu", "rx", "only", "prescription", "ltd", "co", "inc", "company", "www", "com"
)

/** Picks the lines most likely to be the brand name: big, mostly letters, not boilerplate. [lines] = text to box height. */
fun rankNameCandidates(lines: List<Pair<String, Int>>, limit: Int = 4): List<String> =
    lines.asSequence()
        .map { (text, height) -> text.trim().replace(Regex("\\s+"), " ") to height }
        .filter { (text, _) ->
            val letters = text.count { it.isLetter() && it.code < 128 }
            val visible = text.count { !it.isWhitespace() }
            text.length in 3..28 && visible > 0 && letters.toDouble() / visible >= 0.7
        }
        .filter { (text, _) -> text.lowercase().trim('.', ',', ':', '®', '™') !in noiseWords }
        .sortedByDescending { it.second }
        .map { (text, _) -> prettify(text) }
        .distinct()
        .take(limit)
        .toList()

private fun prettify(text: String): String {
    val clean = text.trim('.', ',', ':', '®', '™', ' ')
    return if (clean.any { it.isLowerCase() }) clean
    else clean.lowercase().replaceFirstChar { it.titlecase() }
}

/** On-device text recognition (no network). Returns suggested names, best first; empty if nothing useful is found. */
suspend fun recognizeDrugNames(path: String): List<String> = withContext(Dispatchers.Default) {
    val bitmap = decodeThumbnail(path, 1600) ?: return@withContext emptyList()
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    try {
        val text = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text?> { cont ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        } ?: return@withContext emptyList()
        val lines = text.textBlocks.flatMap { it.lines }.map { it.text to (it.boundingBox?.height() ?: 0) }
        rankNameCandidates(lines)
    } catch (_: Exception) {
        emptyList()
    } finally {
        recognizer.close()
    }
}
