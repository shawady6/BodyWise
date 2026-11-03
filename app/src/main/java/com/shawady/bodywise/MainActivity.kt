package com.shawady.bodywise

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
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

class MainActivity : ComponentActivity() {
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
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
    val meals = remember { mutableStateMapOf<String, MutableList<Food>>() }
    val water = remember { mutableStateMapOf<String, Int>() }
    val measurements = remember { mutableStateListOf<Measure>() }
    val photos = remember { mutableStateListOf<ProgressPhoto>() }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var dialogMeal by remember { mutableStateOf<String?>(null) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var updateMessage by remember { mutableStateOf("لم يتم التحقق بعد") }
    var checking by remember { mutableStateOf(false) }
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
                        val todayCount=meals[todayKey]?.size ?: 0
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
                                        Button(onClick={picker.launch("image/*")},modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.AddAPhoto,null); Spacer(Modifier.width(8.dp)); Text("إضافة صورة") }
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
                                    Text("كل بياناتك محفوظة محليًا على الموبايل.",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                } }
                            }
                            item {
                                Card { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                                    Text("تحديث التطبيق",fontSize=18.sp,fontWeight=FontWeight.Bold)
                                    Text(updateMessage,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    Button(enabled=!checking,onClick={
                                        checking=true;updateMessage="جاري التحقق..."
                                        scope.launch {
                                            val result=withContext(Dispatchers.IO) {
                                                runCatching {
                                                    val c=URL("https://api.github.com/repos/shawady6/BodyWise/releases/latest").openConnection() as HttpURLConnection
                                                    c.connectTimeout=8000;c.readTimeout=8000;c.setRequestProperty("Accept","application/vnd.github+json")
                                                    val body=c.inputStream.bufferedReader().use{it.readText()};c.disconnect()
                                                    val j=JSONObject(body);j.optString("tag_name") to j.optString("html_url")
                                                }.getOrNull()
                                            }
                                            checking=false
                                            if(result!=null){latestVersion=result.first;updateMessage="أحدث إصدار متاح: ${result.first}";if(result.first=="v0.1.0")updateMessage="أنت على الإصدار الأول: ${result.first}"}
                                            else updateMessage="تعذر التحقق. جرّب تاني لما الإنترنت يكون متاح."
                                        }
                                    },modifier=Modifier.fillMaxWidth()) { Text(if(checking)"جاري التحقق..." else "التحقق من التحديثات") }
                                    if(latestVersion!=null) OutlinedButton(onClick={
                                        val i=Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/shawady6/BodyWise/releases/latest"));context.startActivity(i)
                                    },modifier=Modifier.fillMaxWidth()) { Text("فتح صفحة تنزيل التحديث") }
                                    Text("التثبيت النهائي بيتم بموافقتك من Android حفاظًا على أمان الجهاز.",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                } }
                            }
                            item {
                                OutlinedButton(onClick={
                                    val backup=JSONObject().put("meals",JSONObject(prefs.getString("meals","{}")?:"{}")).put("water",JSONObject(prefs.getString("water","{}")?:"{}")).put("measurements",JSONArray(prefs.getString("measurements","[]")?:"[]")).toString(2)
                                    val send=Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT,backup)
                                    context.startActivity(Intent.createChooser(send,"تصدير نسخة البيانات"))
                                },modifier=Modifier.fillMaxWidth()) { Text("تصدير نسخة من البيانات") }
                            }
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

@Composable
private fun SmallStat(title:String,value:String,modifier:Modifier=Modifier){
    Card(modifier) { Column(Modifier.padding(13.dp)) { Text(title,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,fontWeight=FontWeight.Bold,fontSize=19.sp) } }
}

@Composable
private fun NumberField(label:String,value:String,onChange:(String)->Unit){
    OutlinedTextField(value,onChange,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),modifier=Modifier.fillMaxWidth())
}
