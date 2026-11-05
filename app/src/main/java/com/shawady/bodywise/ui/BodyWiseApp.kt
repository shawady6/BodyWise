package com.shawady.bodywise.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.shawady.bodywise.ui.progress.ProgressScreen
import com.shawady.bodywise.ui.settings.SettingsScreen
import com.shawady.bodywise.ui.theme.BodyWiseTheme
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
            if (vm.settings.appLock && !unlocked) {
                LockScreen(lockMessage) { appLock.authenticate({ unlocked = true }, { lockMessage = it }) }
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
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.toast) {
        vm.toast?.let {
            snackbar.showSnackbar(it)
            vm.toast = null
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, icon = { Icon(Icons.Default.Restaurant, null) }, label = { Text("الأكل والشرب") })
                NavigationBarItem(tab == 1, { tab = 1 }, icon = { Icon(Icons.AutoMirrored.Filled.ShowChart, null) }, label = { Text("الوزن والمقاسات") })
                NavigationBarItem(tab == 2, { tab = 2 }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("الإعدادات") })
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BODYWISE", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("خطوة صغيرة كل يوم 💚", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), style = MaterialTheme.typography.labelMedium)
            }
            when (tab) {
                0 -> FoodScreen(vm)
                1 -> ProgressScreen(vm)
                else -> SettingsScreen(vm, updateVm, appLock.isAvailable())
            }
        }
    }
}
