package com.shawady.bodywise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BodyWiseApp() }
    }
}

private enum class Destination(val label: String) {
    HOME("الرئيسية"), FOOD("الأكل"), PROGRESS("التقدم"), PROFILE("حسابي")
}

@Composable
private fun BodyWiseApp() {
    var selected by remember { mutableStateOf(Destination.HOME) }
    var darkMode by remember { mutableStateOf(false) }
    val colorScheme = if (darkMode) darkColorScheme() else lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF287A68),
        secondary = androidx.compose.ui.graphics.Color(0xFFB6D9C9),
        background = androidx.compose.ui.graphics.Color(0xFFF6F8F6),
        surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    )
    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val icon = when (destination) {
                        Destination.HOME -> Icons.Default.Home
                        Destination.FOOD -> Icons.Default.Restaurant
                        Destination.PROGRESS -> Icons.Default.TrendingUp
                        Destination.PROFILE -> Icons.Default.Person
                    }
                    NavigationBarItem(selected == destination, { selected = destination },
                        icon = { Icon(icon, contentDescription = destination.label) },
                        label = { Text(destination.label) })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("BODYWISE", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("أهلاً بيك 👋", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("كل خطوة صغيرة بتفرق", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.AutoAwesome, contentDescription = "BodyWise AI", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                }
                when (selected) {
                    Destination.HOME -> HomeScreen()
                    Destination.FOOD -> FoodScreen()
                    Destination.PROGRESS -> ProgressScreen()
                    Destination.PROFILE -> ProfileScreen(darkMode) { darkMode = it }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ملخص يومك", fontWeight = FontWeight.SemiBold)
            Text("0", fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Text("سعرة مسجلة حتى الآن")
            LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth())
            Text("ابدأ بتسجيل أول وجبة ليظهر تقدمك هنا")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MetricCard("الوزن الحالي", "--", "kg", Modifier.weight(1f))
        MetricCard("الهدف اليومي", "--", "kcal", Modifier.weight(1f))
    }
    Text("العناصر اليومية", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MetricCard("بروتين", "--", "g", Modifier.weight(1f))
        MetricCard("مياه", "--", "ml", Modifier.weight(1f))
        MetricCard("خطوات", "--", "", Modifier.weight(1f))
    }
    Card {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, tint = MaterialTheme.colorScheme.primary, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("BodyWise Coach", fontWeight = FontWeight.Bold)
                Text("مساعدك الذكي هيظهر هنا بعد تفعيل ميزة AI.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Text(unit, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun FoodScreen() {
    Text("مذكرات الطعام", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Text("سجّل وجباتك وتابع السعرات والماكروز.")
    listOf("الإفطار", "سناك", "الغداء", "سناك", "العشاء").forEach { meal ->
        Card {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(meal, fontWeight = FontWeight.Bold)
                    Text("لم تُضف أطعمة بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.AddCircleOutline, contentDescription = "إضافة")
            }
        }
    }
    Text("قاعدة الطعام المصرية والعالمية قادمة ضمن المرحلة التالية.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ProgressScreen() {
    Text("تقدمك", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    MetricCard("آخر وزن مسجل", "--", "kg")
    MetricCard("محيط الوسط", "--", "cm")
    Card {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("سجل القياسات", fontWeight = FontWeight.Bold)
            Text("ابدأ بإضافة وزنك وقياسات جسمك لمتابعة التغيير بمرور الوقت.")
        }
    }
}

@Composable
private fun ProfileScreen(darkMode: Boolean, onDarkModeChange: (Boolean) -> Unit) {
    Text("حسابي والإعدادات", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("ملفك الشخصي", fontWeight = FontWeight.Bold)
            Text("بياناتك وأهدافك ستُحفظ محليًا على جهازك.")
        }
    }
    Card {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("الوضع الداكن", Modifier.weight(1f))
            Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
        }
    }
    Text("BodyWise AI • إصدار تأسيسي 0.1.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
}
