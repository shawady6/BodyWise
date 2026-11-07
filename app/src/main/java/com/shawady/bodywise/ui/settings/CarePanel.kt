package com.shawady.bodywise.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shawady.bodywise.data.MAX_PEOPLE
import com.shawady.bodywise.data.Person
import com.shawady.bodywise.ui.BodyWiseViewModel
import com.shawady.bodywise.ui.components.Hint

/** "I look after someone": up to three people, name only. */
@Composable
fun CarePanel(vm: BodyWiseViewModel) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Person?>(null) }
    val enabled = vm.settings.careEnabled

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("أنا برعى حد", fontWeight = FontWeight.SemiBold)
                Hint("تقدر تسجّل أدوية ومواعيد دكتور باسم ابنك أو والدك، والتنبيه يقول اسمه.")
            }
            Switch(checked = enabled, onCheckedChange = { vm.setCareEnabled(it) })
        }
        if (enabled) {
            vm.people.forEach { p ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("👤 ${p.name}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { deleting = p }) { Icon(Icons.Default.DeleteOutline, "حذف") }
                }
            }
            if (vm.people.size < MAX_PEOPLE) {
                OutlinedTextField(
                    name, { name = it; error = null }, label = { Text("اسم الشخص") }, singleLine = true,
                    isError = error != null, supportingText = error?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    val problem = vm.addPerson(name)
                    if (problem == null) name = "" else error = problem
                }, modifier = Modifier.fillMaxWidth()) { Text("إضافة شخص") }
            } else {
                Hint("وصلت للحد الأقصى ($MAX_PEOPLE أشخاص).")
            }
        }
    }

    deleting?.let { person ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف ${person.name}") },
            text = { Text("أدوية ومواعيد ${person.name} هتتعمل فيها إيه؟") },
            confirmButton = {
                TextButton(onClick = { vm.deletePerson(person, deleteData = true); deleting = null }) { Text("احذفها معاه") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.deletePerson(person, deleteData = false); deleting = null }) { Text("انقلها ليّ") }
                    TextButton(onClick = { deleting = null }) { Text("إلغاء") }
                }
            }
        )
    }
}
