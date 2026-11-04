package com.shawady.bodywise

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BodyWiseApp() }
    }
}

private data class Food(val name: String, val amount: String)
private data class Measure(val date: String, val weight: String, val belly: String, val waist: String, val chest: String, val arm: String, val thigh: String)
private data class ProgressPhoto(val date: String, val file: String)

private val green = androidx.compose.ui.graphics.Color(0xFF347B52)
private val pale = androidx.compose.ui.graphics.Color(0xFFE7F1E8)

@Composable
private fun BodyWiseApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bodywise_data", Context.MODE_PRIVATE) }
    var page by remember { mutableIntStateOf(0) }
    var lockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock", false)) }
    var isUnlocked by remember { mutableStateOf(!prefs.getBoolean("app_lock", false)) }
    var lockMessage by remember { mutableStateOf("استخدم بصمة الإصبع أو قفل الشاشة لفتح BodyWise") }
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as FragmentActivity
    val allowedAuthenticators = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
    }
    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("فتح BodyWise")
            .setSubtitle("أكد هويتك بالبصمة")
            .setAllowedAuthenticators(allowedAuthenticators)
            .apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) setNegativeButtonText("إلغاء")
            }
            .build()
    }
    val biometricPrompt = remember {
        BiometricPrompt(activity, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                isUnlocked = true
                lockMessage = "تم فتح التطبيق"
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                lockMessage = errString.toString()
            }
            override fun onAuthenticationFailed() {
                lockMessage = "لم يتم التعرف على البصمة. حاول مرة أخرى."
            }
        })
    }
    fun authenticate() { biometricPrompt.authenticate(promptInfo) }
    DisposableEffect(lifecycleOwner, lockEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            if (lockEnabled && event == Lifecycle.Event.ON_STOP) isUnlocked = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lockEnabled, isUnlocked) {
        if (lockEnabled && !isUnlocked) {
            kotlinx.coroutines.delay(300)
            authenticate()
        }
    }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    val meals = remember { mutableStateMapOf<String, MutableList<Food>>() }
    val water = remember { mutableStateMapOf<String, Int>() }
    val measurements = remember { mutableStateListOf<Measure>() }
    val photos = remember { mutableStateListOf<ProgressPhoto>() }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var dialogMeal by remember { mutableStateOf<String?>(null) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var updateAvailable by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("لم يتم التحقق بعد") }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var updateAssetUrl by remember { mutableStateOf<String?>(null) }
    var updateDigest by remember { mutableStateOf<String?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var backupMessage by remember { mutableStateOf("لا توجد نسخة احتياطية حديثة") }
    val scope = rememberCoroutineScope()

    fun saveAll() {
        val mealJson = JSONObject()
        meals.forEach { (day, list) ->
            val a = JSONArray()
            list.forEach { f -> a.put(JSONObject().put("name", f.name).put("amount", f.amount)) }
            mealJson.put(day, a)
        }
        val waterJson = JSONObject()
        water.forEach { (day, cups) -> waterJson.put(day, cups) }
        val measureJson = JSONArray()
        measurements.forEach { m -> measureJson.put(JSONObject().put("date",m.date).put("weight",m.weight).put("belly",m.belly).put("waist",m.waist).put("chest",m.chest).put("arm",m.arm).put("thigh",m.thigh)) }
        val photoJson = JSONArray()
        photos.forEach { p -> photoJson.put(JSONObject().put("date",p.date).put("file",p.file)) }
        prefs.edit().putString("meals",mealJson.toString()).putString("water",waterJson.toString())
            .putString("measurements",measureJson.toString()).putString("photos",photoJson.toString()).apply()
    }

    LaunchedEffect(Unit) {
        runCatching {
            val j = JSONObject(prefs.getString("meals","{}") ?: "{}")
            j.keys().forEach { key -> val a=j.getJSONArray(key); meals[key]=MutableList(a.length()){i->val x=a.getJSONObject(i);Food(x.getString("name"),x.optString("amount"))} }
            val w=JSONObject(prefs.getString("water","{}") ?: "{}")
            w.keys().forEach { water[it]=w.getInt(it) }
            val ms=JSONArray(prefs.getString("measurements","[]") ?: "[]")
            for(i in 0 until ms.length()){val x=ms.getJSONObject(i);measurements.add(Measure(x.getString("date"),x.optString("weight"),x.optString("belly"),x.optString("waist"),x.optString("chest"),x.optString("arm"),x.optString("thigh")))}
            val ps=JSONArray(prefs.getString("photos","[]") ?: "[]")
            for(i in 0 until ps.length()){val x=ps.getJSONObject(i);photos.add(ProgressPhoto(x.getString("date"),x.getString("file")))}
        }
    }

    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val captured = pendingCameraFile
        if (success && captured != null && captured.exists()) {
            runCatching {
                val saved = File(context.filesDir, "progress_${System.currentTimeMillis()}.jpg")
                captured.copyTo(saved, overwrite = true)
                photos.add(0, ProgressPhoto(LocalDate.now().toString(), saved.name))
                saveAll()
            }.onFailure { updateMessage = "تعذر حفظ الصورة الملتقطة" }
        }
        captured?.delete()
        pendingCameraFile = null
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val file = File(context.filesDir, "progress_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                photos.add(0, ProgressPhoto(LocalDate.now().toString(), file.name))
                saveAll()
            }.onFailure { updateMessage = "تعذر حفظ الصورة" }
        }
    }


    val exportBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val backupJson = JSONObject()
                .put("format", "bodywise-backup")
                .put("version", 1)
                .put("exportedAt", LocalDate.now().toString())
                .put("meals", JSONObject(prefs.getString("meals", "{}") ?: "{}"))
                .put("water", JSONObject(prefs.getString("water", "{}") ?: "{}"))
                .put("measurements", JSONArray(prefs.getString("measurements", "[]") ?: "[]"))
                .put("photos", JSONArray(prefs.getString("photos", "[]") ?: "[]"))
                .toString(2)
            val photoFiles = photos.map { it.file }
            scope.launch {
                backupMessage = "جاري إنشاء النسخة الاحتياطية..."
                val result = withContext(Dispatchers.IO) {
                    runCatching { writeBackupZip(context, uri, backupJson, photoFiles) }
                }
                backupMessage = if (result.isSuccess) "تم حفظ النسخة الاحتياطية بنجاح، بالصور والبيانات." else "فشل إنشاء النسخة الاحتياطية: ${result.exceptionOrNull()?.message ?: "خطأ غير معروف"}"
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                backupMessage = "جاري فحص النسخة الاحتياطية..."
                val result = withContext(Dispatchers.IO) { runCatching { readBackupZip(context, uri) } }
                result.onSuccess { (backup, tempDir) ->
                    try {
                        val mealsJson = backup.getJSONObject("meals")
                        val waterJson = backup.getJSONObject("water")
                        val measurementsJson = backup.getJSONArray("measurements")
                        val sourcePhotos = backup.optJSONArray("photos") ?: JSONArray()
                        val restoredPhotoJson = JSONArray()
                        val restoredPhotoItems = mutableListOf<ProgressPhoto>()
                        for (i in 0 until sourcePhotos.length()) {
                            val item = sourcePhotos.getJSONObject(i)
                            val oldName = File(item.getString("file")).name
                            val source = File(File(tempDir, "photos"), oldName)
                            if (source.isFile) {
                                val restoredName = "progress_restored_${System.currentTimeMillis()}_$i.jpg"
                                source.copyTo(File(context.filesDir, restoredName), overwrite = true)
                                val date = item.optString("date", LocalDate.now().toString())
                                restoredPhotoJson.put(JSONObject().put("date", date).put("file", restoredName))
                                restoredPhotoItems.add(ProgressPhoto(date, restoredName))
                            }
                        }

                        prefs.edit()
                            .putString("meals", mealsJson.toString())
                            .putString("water", waterJson.toString())
                            .putString("measurements", measurementsJson.toString())
                            .putString("photos", restoredPhotoJson.toString())
                            .apply()

                        meals.clear()
                        mealsJson.keys().forEach { key ->
                            val array = mealsJson.getJSONArray(key)
                            meals[key] = MutableList(array.length()) { index ->
                                val item = array.getJSONObject(index)
                                Food(item.getString("name"), item.optString("amount"))
                            }
                        }
                        water.clear()
                        waterJson.keys().forEach { key -> water[key] = waterJson.getInt(key) }
                        measurements.clear()
                        for (i in 0 until measurementsJson.length()) {
                            val item = measurementsJson.getJSONObject(i)
                            measurements.add(Measure(item.getString("date"), item.optString("weight"), item.optString("belly"), item.optString("waist"), item.optString("chest"), item.optString("arm"), item.optString("thigh")))
                        }
                        photos.clear()
                        photos.addAll(restoredPhotoItems)
                        backupMessage = "تمت استعادة البيانات و${restoredPhotoItems.size} صورة بنجاح."
                    } catch (error: Exception) {
                        backupMessage = "تعذرت استعادة النسخة: ${error.message ?: "ملف غير صالح"}"
                    } finally {
                        tempDir.deleteRecursively()
                    }
                }.onFailure { backupMessage = "الملف غير صالح أو تعذر قراءته: ${it.message ?: "خطأ غير معروف"}" }
            }
        }
    }


    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary=androidx.compose.ui.graphics.Color(0xFF8BC49B)) else lightColorScheme(primary=green, secondary=green, background=androidx.compose.ui.graphics.Color(0xFFF6F8F5), surface=androidx.compose.ui.graphics.Color.White)) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(page==0,{page=0},icon={Icon(Icons.Default.Restaurant,null)},label={Text("الأكل والشرب")})
                    NavigationBarItem(page==1,{page=1},icon={Icon(Icons.Default.ShowChart,null)},label={Text("الوزن والمقاسات")})
                    NavigationBarItem(page==2,{page=2},icon={Icon(Icons.Default.Settings,null)},label={Text("الإعدادات")})
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background).padding(horizontal=16.dp)) {
                Row(Modifier.fillMaxWidth().padding(top=16.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("BODYWISE",color=MaterialTheme.colorScheme.primary,fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
                        Text("خطوة صغيرة كل يوم 💚",fontSize=20.sp,fontWeight=FontWeight.Bold)
                    }
                    Text(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),style=MaterialTheme.typography.labelMedium)
                }
                when(page) {
                    0 -> {
                        val weekStart = LocalDate.now().with(java.time.DayOfWeek.SATURDAY)
                        val week = (0..6).map { weekStart.plusDays(it.toLong()) }
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.fillMaxWidth()) {
                            week.forEach { day ->
                                val active=day==selectedDate
                                Surface(onClick={selectedDate=day},shape=RoundedCornerShape(12.dp),color=if(active) green else MaterialTheme.colorScheme.surface,modifier=Modifier.weight(1f)) {
                                    Column(Modifier.padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                                        Text(day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT,Locale("ar")),fontSize=10.sp,color=if(active) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface)
                                        Text(day.dayOfMonth.toString(),fontWeight=FontWeight.Bold,color=if(active) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                        val key=selectedDate.toString()
                        val todayKey=LocalDate.now().toString()
                        val todayCount=meals.filterKeys { it.startsWith("$todayKey|") }.values.sumOf { it.size }
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.padding(vertical=14.dp)) {
                            SmallStat("وجبات النهارده","$todayCount / 3",Modifier.weight(1f))
                            SmallStat("المياه"," ${water[todayKey] ?: 0} كوب",Modifier.weight(1f))
                        }
                        Text("وجبات ${selectedDate.format(DateTimeFormatter.ofPattern("EEEE",Locale("ar")))}",fontSize=19.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(bottom=8.dp))
                        LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=12.dp)) {
                            items(listOf("الفطار","الغداء","العشاء")) { meal ->
                                val list=meals["$key|$meal"] ?: emptyList()
                                Card(shape=RoundedCornerShape(18.dp)) {
                                    Column(Modifier.fillMaxWidth().padding(15.dp)) {
                                        Row(verticalAlignment=Alignment.CenterVertically) {
                                            Text(when(meal){"الفطار"->"🍳";"الغداء"->"🍲";else->"🥗"},fontSize=22.sp)
                                            Text(meal,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f).padding(horizontal=10.dp))
                                            TextButton(onClick={dialogMeal=meal}) { Icon(Icons.Default.Add, null); Text("إضافة") }
                                        }
                                        if(list.isEmpty()) Text("لسه مفيش وجبات مضافة",color=MaterialTheme.colorScheme.onSurfaceVariant,fontSize=13.sp)
                                        list.forEachIndexed { index, food ->
                                            Row(verticalAlignment=Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f).padding(vertical=5.dp)) {
                                                    Text(food.name,fontWeight=FontWeight.Medium)
                                                    if(food.amount.isNotBlank()) Text(food.amount,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                IconButton(onClick={meals["$key|$meal"]?.removeAt(index);saveAll()}) { Icon(Icons.Default.DeleteOutline,null) }
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                Card(shape=RoundedCornerShape(18.dp)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("شرب المياه 💧",fontWeight=FontWeight.Bold)
                                        Row(verticalAlignment=Alignment.CenterVertically) {
                                            Text("${water[todayKey] ?: 0} كوب",fontSize=24.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary,modifier=Modifier.weight(1f))
                                            OutlinedButton(onClick={water[todayKey]=((water[todayKey]?:0)+1);saveAll()}) { Text("+ كوب") }
                                            IconButton(onClick={water[todayKey]=((water[todayKey]?:0)-1).coerceAtLeast(0);saveAll()}) { Icon(Icons.Default.Remove,null) }
                                        }
                                        Text("هدف مبدئي: 8 أكواب — تقدر تتابع الكمية فقط",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        var weight by remember { mutableStateOf("") }
                        var belly by remember { mutableStateOf("") }
                        var waist by remember { mutableStateOf("") }
                        var chest by remember { mutableStateOf("") }
                        var arm by remember { mutableStateOf("") }
                        var thigh by remember { mutableStateOf("") }
                        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=16.dp)) {
                            item {
                                Card(shape=RoundedCornerShape(18.dp)) {
                                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                                        Text("تسجيل قياسات الأسبوع",fontWeight=FontWeight.Bold,fontSize=18.sp)
                                        NumberField("الوزن (كجم)",weight,{weight=it})
                                        NumberField("محيط البطن (سم)",belly,{belly=it})
                                        NumberField("محيط الخصر (سم)",waist,{waist=it})
                                        NumberField("محيط الصدر (سم)",chest,{chest=it})
                                        NumberField("محيط الذراع (سم)",arm,{arm=it})
                                        NumberField("محيط الفخذ (سم)",thigh,{thigh=it})
                                        Button(onClick={
                                            if(listOf(weight,belly,waist,chest,arm,thigh).any{it.isNotBlank()}) {
                                                measurements.add(0,Measure(LocalDate.now().toString(),weight,belly,waist,chest,arm,thigh));saveAll()
                                                weight="";belly="";waist="";chest="";arm="";thigh=""
                                            }
                                        },modifier=Modifier.fillMaxWidth()) { Text("حفظ قياسات الأسبوع") }
                                    }
                                }
                            }
                            item {
                                Card(shape=RoundedCornerShape(18.dp)) {
                                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                        Text("صور التقدم الأسبوعية 📸",fontSize=18.sp,fontWeight=FontWeight.Bold)
                                        Text("أضف صورة كل أسبوع علشان تلاحظ التغيير مع الوقت.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp), modifier=Modifier.fillMaxWidth()) {
                                            Button(onClick={
                                                runCatching {
                                                    val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                                                    pendingCameraFile = file
                                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                    cameraLauncher.launch(uri)
                                                }.onFailure { updateMessage = "تعذر فتح الكاميرا" }
                                            }, modifier=Modifier.weight(1f)) {
                                                Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("الكاميرا")
                                            }
                                            OutlinedButton(onClick={picker.launch("image/*")}, modifier=Modifier.weight(1f)) {
                                                Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("المعرض")
                                            }
                                        }
                                        photos.forEach { photo ->
                                            val bitmap=remember(photo.file) { BitmapFactory.decodeFile(File(context.filesDir,photo.file).absolutePath)?.asImageBitmap() }
                                            if(bitmap!=null) {
                                                Image(bitmap,null,modifier=Modifier.fillMaxWidth().height(190.dp))
                                                Text(photo.date,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Text("الصور محفوظة داخل التطبيق على جهازك فقط.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            item {
                                Text("سجل القياسات",fontSize=18.sp,fontWeight=FontWeight.Bold)
                                if(measurements.isEmpty()) Text("لسه مفيش قياسات مسجلة.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            items(measurements) { m ->
                                Card { Column(Modifier.padding(14.dp)) {
                                    Text(m.date,fontWeight=FontWeight.Bold)
                                    Text(listOfNotNull(m.weight.takeIf{it.isNotBlank()}?.let{"الوزن: $it كجم"},m.belly.takeIf{it.isNotBlank()}?.let{"البطن: $it سم"},m.waist.takeIf{it.isNotBlank()}?.let{"الخصر: $it سم"},m.chest.takeIf{it.isNotBlank()}?.let{"الصدر: $it سم"},m.arm.takeIf{it.isNotBlank()}?.let{"الذراع: $it سم"},m.thigh.takeIf{it.isNotBlank()}?.let{"الفخذ: $it سم"}).joinToString(" • "),fontSize=12.sp)
                                } }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=16.dp)) {
                            item {
                                Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                                    Text("الإعدادات",fontSize=20.sp,fontWeight=FontWeight.Bold)
                                    Row(verticalAlignment=Alignment.CenterVertically) { Text("الوضع الليلي",Modifier.weight(1f)); Switch(dark,{dark=it;prefs.edit().putBoolean("dark",it).apply()}) }
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("قفل التطبيق بالبصمة",fontWeight=FontWeight.Medium)
                                            Text("يتطلب التحقق عند فتح التطبيق من جديد",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked=lockEnabled,onCheckedChange={ enabled ->
                                            if (enabled) {
                                                val status = BiometricManager.from(context).canAuthenticate(allowedAuthenticators)
                                                if (status == BiometricManager.BIOMETRIC_SUCCESS) {
                                                    lockEnabled = true
                                                    isUnlocked = false
                                                    prefs.edit().putBoolean("app_lock", true).apply()
                                                } else {
                                                    lockMessage = "فعّل بصمة أو قفل شاشة آمن من إعدادات الهاتف أولًا."
                                                }
                                            } else {
                                                lockEnabled = false
                                                isUnlocked = true
                                                prefs.edit().putBoolean("app_lock", false).apply()
                                            }
                                        })
                                    }
                                    Text("كل بياناتك محفوظة محليًا على الموبايل.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                } }
                            }
                            item {
                                Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                    Text("تحديث التطبيق",fontSize=18.sp,fontWeight=FontWeight.Bold)
                                    Text("الإصدار المثبت: ${BuildConfig.VERSION_NAME}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(updateMessage,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(enabled=!checking && !downloading,onClick={
                                        checking=true;updateMessage="جاري التحقق من آخر إصدار..."
                                        scope.launch {
                                            val result=withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val connection=URL("https://api.github.com/repos/shawady6/BodyWise/releases/latest").openConnection() as HttpURLConnection
                                                    connection.connectTimeout=12000;connection.readTimeout=12000
                                                    connection.setRequestProperty("Accept","application/vnd.github+json")
                                                    val body=connection.inputStream.bufferedReader().use{it.readText()}
                                                    connection.disconnect()
                                                    val release=JSONObject(body)
                                                    val assets=release.optJSONArray("assets") ?: JSONArray()
                                                    var apk:JSONObject?=null
                                                    for(index in 0 until assets.length()) {
                                                        val asset=assets.getJSONObject(index)
                                                        if(asset.optString("name").endsWith(".apk")) { apk=asset; break }
                                                    }
                                                    val selected=apk ?: error("ملف APK غير موجود في أحدث إصدار")
                                                    Triple(release.optString("tag_name"),selected.optString("browser_download_url"),selected.optString("digest"))
                                                }
                                            }
                                            checking=false
                                            result.onSuccess { info ->
                                                latestVersion=info.first
                                                updateAssetUrl=info.second
                                                updateDigest=info.third
                                                updateAvailable=versionIsNewer(info.first,BuildConfig.VERSION_NAME)
                                                downloadedApk=null
                                                updateMessage=if(updateAvailable) "متاح تحديث ${info.first}. نزّله وثبّته من هنا." else "أنت تستخدم أحدث إصدار مثبت: ${BuildConfig.VERSION_NAME}"
                                            }.onFailure { updateMessage="تعذر التحقق: ${it.message ?: "راجع اتصال الإنترنت"}" }
                                        }
                                    },modifier=Modifier.fillMaxWidth()) { Text(if(checking)"جاري التحقق..." else "التحقق من التحديثات") }
                                    if(updateAvailable && updateAssetUrl!=null) {
                                        if(downloading) {
                                            LinearProgressIndicator(progress={downloadProgress},modifier=Modifier.fillMaxWidth())
                                            Text("جاري تنزيل التحديث: ${(downloadProgress*100).toInt()}%",fontSize=12.sp)
                                        }
                                        Button(enabled=!downloading,onClick={
                                            val ready=downloadedApk
                                            if(ready!=null && ready.isFile) {
                                                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                                                    updateMessage="اسمح لـ BodyWise بتثبيت التطبيقات من هذا المصدر، ثم ارجع واضغط تثبيت التحديث."
                                                    runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}"))) }
                                                } else if(!hasSameSigningCertificate(context,ready)) {
                                                    updateMessage="التحديث تم تنزيله وفحصه، لكن توقيعه مختلف عن التطبيق المثبت. أوقفنا التثبيت لحماية بياناتك. يلزم إعداد توقيع ثابت متوافق أولًا."
                                                } else {
                                                    runCatching {
                                                        val apkUri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",ready)
                                                        val install=Intent(Intent.ACTION_VIEW).apply {
                                                            setDataAndType(apkUri,"application/vnd.android.package-archive")
                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(install)
                                                        updateMessage="تم فتح مثبت Android. وافق على التحديث لإكمال التثبيت."
                                                    }.onFailure { updateMessage="تعذر فتح مثبت Android: ${it.message}" }
                                                }
                                            } else {
                                                val url=updateAssetUrl ?: error("رابط التحديث غير متاح")
                                                val expected=updateDigest
                                                downloading=true;downloadProgress=0f;updateMessage="جاري تنزيل ملف التحديث..."
                                                scope.launch {
                                                    val result=withContext(Dispatchers.IO) {
                                                        runCatching {
                                                            val connection=URL(url).openConnection() as HttpURLConnection
                                                            connection.connectTimeout=15000;connection.readTimeout=30000
                                                            connection.instanceFollowRedirects=true
                                                            val total=connection.contentLengthLong
                                                            val target=File(context.cacheDir,"bodywise-update.apk")
                                                            connection.inputStream.use { input ->
                                                                target.outputStream().use { output ->
                                                                    val buffer=ByteArray(64*1024);var count:Long=0
                                                                    while(true) {
                                                                        val read=input.read(buffer);if(read<0) break
                                                                        output.write(buffer,0,read);count+=read
                                                                        if(total>0) withContext(Dispatchers.Main) { downloadProgress=(count.toFloat()/total).coerceIn(0f,1f) }
                                                                    }
                                                                }
                                                            }
                                                            connection.disconnect()
                                                            check(target.length()>1_000_000L) { "حجم ملف APK غير متوقع" }
                                                            val actual=MessageDigest.getInstance("SHA-256").digest(target.readBytes()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
                                                            val expectedHex=expected?.removePrefix("sha256:")?.lowercase()
                                                            check(!expectedHex.isNullOrBlank()) { "لا تتوفر بصمة SHA-256 لهذا الإصدار، أوقفنا التثبيت للأمان" }
                                                            check(actual==expectedHex) { "فشل التحقق من سلامة الملف (SHA-256)" }
                                                            target
                                                        }
                                                    }
                                                    downloading=false
                                                    result.onSuccess { file ->
                                                        downloadedApk=file
                                                        downloadProgress=1f
                                                        updateMessage="اكتمل التنزيل وتم التحقق من سلامة الملف. اضغط تثبيت التحديث."
                                                    }.onFailure { updateMessage="فشل تنزيل التحديث: ${it.message ?: "خطأ غير معروف"}" }
                                                }
                                            }
                                        },modifier=Modifier.fillMaxWidth()) {
                                            Text(if(downloadedApk!=null)"تثبيت التحديث" else "تنزيل التحديث")
                                        }
                                    }
                                    Text("يتم فحص SHA-256 قبل فتح مثبت Android. التثبيت يحتاج موافقتك، وبياناتك لا تُحذف بالتحديث المتوافق.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                } }
                            }
                            item {
                                Card {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                        Text("النسخ الاحتياطي والاستعادة", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text("احفظ الوجبات والمياه والقياسات وصور التقدم في ملف واحد، واستعدها عند تغيير الهاتف.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Button(onClick = {
                                            exportBackupLauncher.launch("BodyWise-Backup-${LocalDate.now()}.zip")
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Default.Backup, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("إنشاء نسخة احتياطية كاملة")
                                        }
                                        OutlinedButton(onClick = {
                                            importBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Default.Restore, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("استعادة نسخة احتياطية")
                                        }
                                        Text(backupMessage, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("الاستعادة تستبدل البيانات الحالية بالبيانات الموجودة في النسخة المختارة.", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (lockEnabled && !isUnlocked) {
            Dialog(onDismissRequest = { }) {
                Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                        Text("BodyWise مقفول", fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        Text(lockMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { authenticate() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.LockOpen, null); Spacer(Modifier.width(8.dp)); Text("فتح بالبصمة")
                        }
                    }
                }
            }
        }
    }

    if(dialogMeal!=null) {
        var name by remember(dialogMeal) { mutableStateOf("") }
        var amount by remember(dialogMeal) { mutableStateOf("") }
        AlertDialog(onDismissRequest={dialogMeal=null},title={Text("إضافة ${dialogMeal}")},text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name,{name=it},label={Text("اسم الأكل")},singleLine=true)
                OutlinedTextField(amount,{amount=it},label={Text("الكمية أو ملاحظات")},singleLine=true)
            }
        },confirmButton={TextButton(onClick={
            if(name.isNotBlank()){val k="${selectedDate}|$dialogMeal";val list=meals[k]?.toMutableList()?: mutableListOf();list.add(Food(name.trim(),amount.trim()));meals[k]=list;saveAll();dialogMeal=null}
        }){Text("حفظ")}},dismissButton={TextButton(onClick={dialogMeal=null}){Text("إلغاء")}})
    }
}


private fun hasSameSigningCertificate(context: Context, apkFile: File): Boolean {
    return try {
        val pm=context.packageManager
        val installed=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName,PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners?.map { it.toByteArray() }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName,PackageManager.GET_SIGNATURES).signatures?.map { it.toByteArray() }
        } ?: return false
        val archive=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.P) {
            pm.getPackageArchiveInfo(apkFile.absolutePath,PackageManager.GET_SIGNING_CERTIFICATES)?.signingInfo?.apkContentsSigners?.map { it.toByteArray() }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(apkFile.absolutePath,PackageManager.GET_SIGNATURES)?.signatures?.map { it.toByteArray() }
        } ?: return false
        installed.map { it.toList() }.toSet() == archive.map { it.toList() }.toSet()
    } catch (_: Exception) { false }
}

private fun writeBackupZip(context: Context, uri: Uri, backupJson: String, photoFiles: List<String>) {
    val output = context.contentResolver.openOutputStream(uri) ?: error("تعذر فتح ملف الحفظ")
    ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry("backup.json"))
        zip.write(backupJson.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        photoFiles.forEach { originalName ->
            val safeName = File(originalName).name
            val photo = File(context.filesDir, safeName)
            if (photo.isFile) {
                zip.putNextEntry(ZipEntry("photos/$safeName"))
                photo.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }
}

private fun readBackupZip(context: Context, uri: Uri): Pair<JSONObject, File> {
    val tempDir = File(context.cacheDir, "bodywise_restore_${System.currentTimeMillis()}").apply { mkdirs() }
    try {
        val input = context.contentResolver.openInputStream(uri) ?: error("تعذر فتح ملف النسخة")
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (name == "backup.json") {
                    File(tempDir, "backup.json").outputStream().use { zip.copyTo(it) }
                } else if (!entry.isDirectory && name.startsWith("photos/")) {
                    val fileName = name.removePrefix("photos/")
                    if (fileName.isNotBlank() && File(fileName).name == fileName && !fileName.contains("..")) {
                        val photoDir = File(tempDir, "photos").apply { mkdirs() }
                        File(photoDir, fileName).outputStream().use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
            }
        }
        val manifest = File(tempDir, "backup.json")
        check(manifest.isFile) { "ملف النسخة لا يحتوي على بيانات BodyWise" }
        val backup = JSONObject(manifest.readText(Charsets.UTF_8))
        check(backup.optString("format") == "bodywise-backup" && backup.optInt("version") == 1) { "صيغة النسخة غير مدعومة" }
        backup.getJSONObject("meals")
        backup.getJSONObject("water")
        backup.getJSONArray("measurements")
        return backup to tempDir
    } catch (error: Exception) {
        tempDir.deleteRecursively()
        throw error
    }
}

@Composable
private fun SmallStat(title:String,value:String,modifier:Modifier=Modifier){
    Card(modifier) { Column(Modifier.padding(13.dp)) { Text(title,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,fontWeight=FontWeight.Bold,fontSize=19.sp) } }
}

@Composable
private fun NumberField(label:String,value:String,onChange:(String)->Unit){
    OutlinedTextField(value,onChange,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
}

private fun versionIsNewer(latest: String, current: String): Boolean {
    fun parts(value: String) = value.removePrefix("v").removePrefix("V").split(".").map { it.toIntOrNull() ?: 0 }
    val a = parts(latest)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
