package com.shawady.bodywise.notify

import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shawady.bodywise.data.DoseStatus
import com.shawady.bodywise.data.MedStore
import com.shawady.bodywise.data.SNOOZE_MINUTES
import com.shawady.bodywise.ui.theme.BodyWiseTheme
import com.shawady.bodywise.util.decodeThumbnail
import com.shawady.bodywise.util.format12
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Full-screen medication alarm: shows over the lock screen and turns the screen on. */
class DoseAlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val medId = intent.getStringExtra(MedAlarms.EXTRA_MED)
        val date = runCatching { LocalDate.parse(intent.getStringExtra(MedAlarms.EXTRA_DATE)) }.getOrNull()
        val time = runCatching { LocalTime.parse(intent.getStringExtra(MedAlarms.EXTRA_TIME)) }.getOrNull()
        val store = MedStore(this)
        val med = store.loadMeds().firstOrNull { it.id == medId }
        if (med == null || date == null || time == null) { finish(); return }
        val notificationId = ("false|${med.id}|$time").hashCode()

        fun close() {
            getSystemService(NotificationManager::class.java).cancel(notificationId)
            finish()
        }

        setContent {
            BodyWiseTheme(dark = true) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("💊", fontSize = 64.sp)
                            val forPerson = com.shawady.bodywise.data.personName(med.personId, com.shawady.bodywise.data.HealthStore(this@DoseAlarmActivity).loadPeople())
                            if (forPerson != null) Text("الدواء لـ $forPerson", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            Text("وقت الدواء", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(time.format12(), fontSize = 44.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Text(med.name, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            val details = listOf(
                                listOf(med.dose, med.unit).filter { it.isNotBlank() }.joinToString(" "),
                                med.instruction.takeIf { it.isNotBlank() && it != "لا يهم" }.orEmpty(),
                                med.notes
                            ).filter { it.isNotBlank() }
                            details.forEach { Text(it, fontSize = 18.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            med.photo?.let { name ->
                                val bitmap = produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, name) {
                                    value = withContext(Dispatchers.IO) { decodeThumbnail(File(filesDir, name).absolutePath, 600)?.asImageBitmap() }
                                }.value
                                if (bitmap != null) {
                                    Spacer(Modifier.height(12.dp))
                                    Image(bitmap, null, Modifier.fillMaxWidth().height(160.dp), contentScale = ContentScale.Fit)
                                }
                            }
                            Spacer(Modifier.height(28.dp))
                            Button(
                                onClick = { store.mark(med, date, time, DoseStatus.TAKEN, LocalDateTime.now()); close() },
                                modifier = Modifier.fillMaxWidth().height(64.dp)
                            ) { Text("أخدتها ✓", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    store.mark(med, date, time, DoseStatus.SNOOZED, LocalDateTime.now())
                                    MedAlarms.scheduleSnooze(applicationContext, med, date, time)
                                    close()
                                },
                                modifier = Modifier.fillMaxWidth().height(56.dp)
                            ) { Text("أجّل $SNOOZE_MINUTES دقايق", fontSize = 18.sp) }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { close() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors()) { Text("إغلاق") }
                        }
                    }
                }
            }
        }
    }
}
