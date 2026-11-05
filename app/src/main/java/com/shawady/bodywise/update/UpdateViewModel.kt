package com.shawady.bodywise.update

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shawady.bodywise.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class UpdateViewModel(app: Application) : AndroidViewModel(app) {
    var message by mutableStateOf("لم يتم التحقق بعد")
        private set
    var checking by mutableStateOf(false)
        private set
    var downloading by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var release by mutableStateOf<ReleaseInfo?>(null)
        private set
    var available by mutableStateOf(false)
        private set
    var readyFile by mutableStateOf<File?>(null)
        private set

    init {
        viewModelScope.launch {
            InstallEvents.message.collect { text ->
                if (text != null) {
                    message = text
                    InstallEvents.message.value = null
                }
            }
        }
    }

    fun check() {
        if (checking || downloading) return
        checking = true
        message = "جاري التحقق من آخر إصدار..."
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { UpdateManager.fetchLatest() }
                .onSuccess { info ->
                    release = info
                    readyFile = null
                    available = versionIsNewer(info.tag, BuildConfig.VERSION_NAME)
                    message = if (available) "متاح تحديث ${info.tag}. نزّله وثبّته من هنا."
                    else "أنت تستخدم أحدث إصدار: ${BuildConfig.VERSION_NAME}"
                }
                .onFailure { message = "تعذر التحقق: ${it.message ?: "راجع اتصال الإنترنت"}" }
            checking = false
        }
    }

    fun download() {
        val info = release ?: return
        if (downloading) return
        downloading = true
        progress = 0f
        message = "جاري تنزيل ملف التحديث..."
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { UpdateManager.download(getApplication(), info) { progress = it } }
                .onSuccess { file ->
                    readyFile = file
                    progress = 1f
                    message = "اكتمل التنزيل وتم التحقق من سلامة الملف. اضغط تثبيت التحديث."
                }
                .onFailure { message = "فشل تنزيل التحديث: ${it.message ?: "خطأ غير معروف"}" }
            downloading = false
        }
    }

    fun install() {
        val file = readyFile ?: return
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            message = "اسمح لـ BodyWise بتثبيت التطبيقات من هذا المصدر، ثم ارجع واضغط تثبيت التحديث."
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            return
        }
        if (UpdateManager.signaturesMatch(context, file) == false) {
            message = "توقيع التحديث مختلف عن التطبيق المثبت (غالبًا النسخة المثبتة من بناء قديم). خد نسخة احتياطية، امسح BodyWise مرة واحدة، ثم ثبّت النسخة الجديدة."
            return
        }
        message = "جاري تجهيز التثبيت..."
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { installApk(context, file) }
                .onSuccess { message = "تم فتح مثبت Android. وافق على التحديث لإكمال التثبيت." }
                .onFailure { message = "تعذر بدء التثبيت: ${it.message ?: it.javaClass.simpleName}" }
        }
    }
}
