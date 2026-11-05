package com.shawady.bodywise.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ReleaseInfo(val tag: String, val apkUrl: String, val sha256: String?, val size: Long)

object UpdateManager {
    private const val LATEST = "https://api.github.com/repos/shawady6/BodyWise/releases/latest"

    private fun open(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        return connection
    }

    private fun readText(url: String): String {
        val connection = open(url)
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Blocking; call from a background dispatcher. */
    fun fetchLatest(): ReleaseInfo {
        val release = JSONObject(readText(LATEST))
        val assets = release.optJSONArray("assets") ?: JSONArray()
        var apk: JSONObject? = null
        var shaAsset: JSONObject? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            if (name.endsWith(".apk") && apk == null) apk = asset
            if (name.endsWith(".apk.sha256") && shaAsset == null) shaAsset = asset
        }
        val selected = apk ?: error("ملف APK غير موجود في أحدث إصدار")
        var sha = selected.optString("digest").removePrefix("sha256:").lowercase().takeIf { it.length == 64 }
        if (sha == null && shaAsset != null) {
            sha = runCatching {
                readText(shaAsset.getString("browser_download_url")).trim().substringBefore(' ').lowercase()
            }.getOrNull()?.takeIf { it.length == 64 }
        }
        return ReleaseInfo(release.optString("tag_name"), selected.getString("browser_download_url"), sha, selected.optLong("size"))
    }

    /** Blocking. Downloads, then verifies size and SHA-256; deletes the file when verification fails. */
    fun download(context: Context, info: ReleaseInfo, onProgress: (Float) -> Unit): File {
        val expected = info.sha256 ?: error("لا تتوفر بصمة SHA-256 لهذا الإصدار، أوقفنا التثبيت للأمان")
        val target = File(context.cacheDir, "bodywise-update.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = open(info.apkUrl)
        try {
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: info.size
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var count = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        count += read
                        if (total > 0) onProgress((count.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (target.length() < 1_000_000L) { target.delete(); error("حجم ملف APK غير متوقع") }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (actual != expected) { target.delete(); error("فشل التحقق من سلامة الملف (SHA-256)") }
        return target
    }

    /** true/false when both certificates are readable; null when Android cannot read them (then Android itself decides). */
    fun signaturesMatch(context: Context, apk: File): Boolean? = try {
        val pm = context.packageManager
        val installed: List<ByteArray>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.map { it.toByteArray() }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures?.map { it.toByteArray() }
        }
        val archive: List<ByteArray>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
                ?.signingInfo?.apkContentsSigners?.map { it.toByteArray() }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNATURES)?.signatures?.map { it.toByteArray() }
        }
        if (installed.isNullOrEmpty() || archive.isNullOrEmpty()) null
        else installed.size == archive.size && installed.all { a -> archive.any { b -> a.contentEquals(b) } }
    } catch (_: Exception) {
        null
    }
}
