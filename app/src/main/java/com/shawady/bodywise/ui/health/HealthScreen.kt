package com.shawady.bodywise.ui.health

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shawady.bodywise.data.VitalType
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.progress.ProgressScreen
import com.shawady.bodywise.ui.vitals.VitalsScreen

/** تبويب "صحتي": الوزن (ومعاه القياسات والصور) أولًا، بعده الضغط والسكر والنبض. */
@Composable
fun HealthScreen(vm: BodyWiseViewModel, page: Int, onPage: (Int) -> Unit) {
    val vitals = VitalType.entries
    val current = page.coerceIn(0, vitals.size)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = current == 0, onClick = { onPage(0) }, label = { Text("⚖️ الوزن والصور") })
            vitals.forEachIndexed { i, t ->
                FilterChip(selected = current == i + 1, onClick = { onPage(i + 1) }, label = { Text("${t.emoji} ${t.label}") })
            }
        }
        Box(Modifier.weight(1f)) {
            if (current == 0) ProgressScreen(vm) else VitalsScreen(vm, vitals[current - 1])
        }
    }
}
