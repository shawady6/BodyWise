package com.shawady.bodywise.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shawady.bodywise.security.AppLock
import com.shawady.bodywise.ui.components.Hint
import com.shawady.bodywise.ui.food.FoodScreen
import com.shawady.bodywise.ui.health.HealthScreen
import com.shawady.bodywise.ui.settings.OnboardingScreen
import com.shawady.bodywise.ui.settings.SettingsScreen
import com.shawady.bodywise.ui.theme.BodyWiseTheme
import com.shawady.bodywise.ui.home.HomeScreen
import com.shawady.bodywise.ui.meds.MedsScreen
import com.shawady.bodywise.update.UpdateViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun BodyWiseApp(activity: FragmentActivity) {
    val vm: BodyWiseViewModel = viewModel()
    val updateVm: UpdateViewModel = viewModel()
    val dark = when (vm.settings.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    SideEffect {
        val controller = WindowCompat.getInsetsController((view.context as Activity).window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }

    BodyWiseTheme(dark) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            val appLock = remember { AppLock(activity) }
            var unlocked by rememberSaveable { mutableStateOf(!vm.settings.appLock) }
            var lockMessage by remember { mutableStateOf("استخدم بصمة الإصبع أو قفل الشاشة لفتح BodyWise") }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, vm.settings.appLock) {
                val observer = LifecycleEventObserver { _, event ->
                    if (vm.settings.appLock && event == Lifecycle.Event.ON_STOP) unlocked = false
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            LaunchedEffect(vm.settings.appLock, unlocked) {
                if (vm.settings.appLock && !unlocked) {
                    delay(300)
                    appLock.authenticate({ unlocked = true }, { lockMessage = it })
                }
            }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) { vm.reloadWater(); vm.reloadMeds() }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            if (vm.settings.appLock && !unlocked) {
                LockScreen(lockMessage) { appLock.authenticate({ unlocked = true }, { lockMessage = it }) }
            } else if (!vm.settings.onboarded) {
                OnboardingScreen(vm)
            } else {
                MainScaffold(vm, updateVm, appLock)
            }
        }
    }
}

@Composable
private fun LockScreen(message: String, onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Fingerprint, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
            Spacer(Modifier.padding(8.dp))
            Text("BodyWise مقفول", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.padding(6.dp))
            Hint(message)
            Spacer(Modifier.padding(10.dp))
            Button(onClick = onUnlock, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.LockOpen, null)
                Spacer(Modifier.width(8.dp))
                Text("فتح بالبصمة")
            }
        }
    }
}

@Composable
private fun MainScaffold(vm: BodyWiseViewModel, updateVm: UpdateViewModel, appLock: AppLock) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var healthPage by rememberSaveable { mutableIntStateOf(0) }
    var settingsSection by rememberSaveable { mutableStateOf<String?>(null) }
    var lastMainTab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(tab) { if (tab != 4) lastMainTab = tab }
    var requests by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { updateVm.autoCheck() }
    if (updateVm.prompt) UpdatePrompt(updateVm)
    LaunchedEffect(vm.toast) {
        vm.toast?.let {
            snackbar.showSnackbar(it)
            vm.toast = null
        }
    }
    LaunchedEffect(vm.undoRequest) {
        val request = vm.undoRequest ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(request.message, actionLabel = "تراجع", duration = SnackbarDuration.Long)
        vm.resolveUndo(request, result == SnackbarResult.ActionPerformed)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tab != 4) NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("الرئيسية", fontSize = 11.sp, maxLines = 1) })
                NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.Default.Restaurant, null) }, label = { Text("غذائي", fontSize = 11.sp, maxLines = 1) })
                NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Default.MonitorHeart, null) }, label = { Text("صحتي", fontSize = 11.sp, maxLines = 1) })
                NavigationBarItem(tab == 3, { tab = 3 }, icon = { Icon(Icons.Default.Medication, null) }, label = { Text("روشتتي", fontSize = 11.sp, maxLines = 1) })
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    "BodyWise", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.Center)
                )
                if (tab == 4) {
                    IconButton(onClick = { tab = lastMainTab }, modifier = Modifier.align(Alignment.CenterStart).size(52.dp)) {
                        Icon(androidx.compose.material.icons.automirrored.filled.ArrowBack, "رجوع", modifier = Modifier.size(28.dp))
                    }
                }
                IconButton(onClick = { settingsSection = null; tab = if (tab == 4) lastMainTab else 4 }, modifier = Modifier.align(Alignment.CenterEnd).size(52.dp)) {
                    Icon(
                        Icons.Default.Settings, "الإعدادات", modifier = Modifier.size(30.dp),
                        tint = if (tab == 4) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when (tab) {
                // الرئيسية بتنادي بالأرقام القديمة: 2 = الوزن، 3 = الحيويات، 4 = روشتتي
                0 -> HomeScreen(vm, updateVm) { target ->
                    when (target) {
                        2 -> { healthPage = 0; tab = 2 }
                        3 -> { healthPage = 1; tab = 2 }
                        4 -> tab = 3
                        else -> tab = target
                    }
                }
                1 -> FoodScreen(vm)
                2 -> HealthScreen(vm, healthPage) { healthPage = it }
                3 -> MedsScreen(vm, onOpenSettings = { requests++; settingsSection = "alerts#$requests"; tab = 4 })
                else -> SettingsScreen(vm, updateVm, appLock.isAvailable(), settingsSection)
            }
        }
    }
}

@Composable
private fun UpdatePrompt(updateVm: UpdateViewModel) {
    val tag = updateVm.release?.tag.orEmpty()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { updateVm.dismissPrompt() },
        title = { Text("🎉 فيه تحديث جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الإصدار $tag متاح. الإصدار اللي معاك: ${com.shawady.bodywise.BuildConfig.VERSION_NAME}.")
                if (updateVm.downloading) {
                    androidx.compose.material3.LinearProgressIndicator(progress = { updateVm.progress }, modifier = Modifier.fillMaxWidth())
                    Hint("جاري التنزيل: ${(updateVm.progress * 100).toInt()}%")
                } else {
                    Hint(updateVm.message)
                    Hint("التحديث اختياري، وبياناتك مش بتتمسح. وتقدر تحدّث بعدين من الرئيسية أو الإعدادات.")
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { updateVm.updateNow() }, enabled = !updateVm.downloading) { Text("حدّث الآن") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { updateVm.dismissPrompt() }) { Text(if (updateVm.downloading) "إخفاء" else "لاحقًا") }
        }
    )
}
