package com.shawady.bodywise.backup

import com.shawady.bodywise.data.ProgressPhoto
import com.shawady.bodywise.data.Snapshot
import com.shawady.bodywise.data.SnapshotJson
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {
    private const val MANIFEST = "backup.json"
    private const val MAX_JSON = 20L * 1024 * 1024
    private const val MAX_PHOTO = 60L * 1024 * 1024
    private const val MAX_TOTAL = 1500L * 1024 * 1024

    fun writeZip(output: OutputStream, snapshot: Snapshot, photosDir: File) {
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(SnapshotJson.toBackup(snapshot, LocalDate.now()).toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            snapshot.photos.forEach { photo ->
                val name = File(photo.file).name
                val file = File(photosDir, name)
                if (file.isFile) {
                    zip.putNextEntry(ZipEntry("photos/$name"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            snapshot.medications.forEach { med ->
                val name = med.photo?.let { File(it).name } ?: return@forEach
                val file = File(photosDir, name)
                if (file.isFile) {
                    zip.putNextEntry(ZipEntry("medphotos/$name"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * Reads and validates the whole backup before the app changes anything. Photos are copied into
     * [photosDir] under new names; the returned snapshot points at those files.
     */
    fun restore(input: InputStream, photosDir: File, cacheDir: File): Snapshot {
        val temp = File(cacheDir, "bodywise_restore_${System.currentTimeMillis()}").apply { mkdirs() }
        val copied = mutableListOf<File>()
        try {
            var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (!entry.isDirectory) {
                        if (name == MANIFEST) {
                            total += copyLimited(zip, File(temp, MANIFEST), MAX_JSON)
                        } else if (name.startsWith("photos/") || name.startsWith("medphotos/")) {
                            val folder = name.substringBefore("/")
                            val fileName = name.removePrefix("$folder/")
                            if (fileName.isNotBlank() && File(fileName).name == fileName && !fileName.contains("..")) {
                                val dir = File(temp, folder).apply { mkdirs() }
                                total += copyLimited(zip, File(dir, fileName), MAX_PHOTO)
                            }
                        }
                        check(total <= MAX_TOTAL) { "حجم النسخة أكبر من المعتاد" }
                    }
                    zip.closeEntry()
                }
            }
            val manifest = File(temp, MANIFEST)
            check(manifest.isFile) { "ملف النسخة لا يحتوي على بيانات BodyWise" }
            val parsed = SnapshotJson.fromBackup(JSONObject(manifest.readText(Charsets.UTF_8)))
            val photos = mutableListOf<ProgressPhoto>()
            parsed.photos.forEachIndexed { index, photo ->
                val source = File(File(temp, "photos"), File(photo.file).name)
                if (source.isFile) {
                    val newName = "progress_restored_${System.currentTimeMillis()}_$index.jpg"
                    val target = File(photosDir, newName)
                    source.copyTo(target, overwrite = true)
                    copied.add(target)
                    photos.add(ProgressPhoto(photo.date, newName))
                }
            }
            val meds = parsed.medications.mapIndexed { index, med ->
                val source = med.photo?.let { File(File(temp, "medphotos"), File(it).name) }
                if (source != null && source.isFile) {
                    val newName = "med_restored_${System.currentTimeMillis()}_$index.jpg"
                    val target = File(photosDir, newName)
                    source.copyTo(target, overwrite = true)
                    copied.add(target)
                    med.copy(photo = newName)
                } else med.copy(photo = null)
            }
            return parsed.copy(photos = photos, medications = meds)
        } catch (error: Exception) {
            copied.forEach { it.delete() }
            throw error
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun copyLimited(input: InputStream, target: File, limit: Long): Long {
        var count = 0L
        target.outputStream().use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                count += read
                check(count <= limit) { "ملف داخل النسخة أكبر من المسموح" }
                out.write(buffer, 0, read)
            }
        }
        return count
    }
}
