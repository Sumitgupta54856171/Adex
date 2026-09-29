package com.example.a_dex

import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

class PresentationLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

data class DesktopAppItem(val name: String, val packageName: String, val componentName: String?)

class DesktopPresentation(
    outerContext: Context,
    display: Display,
    private val displayId: Int
) : Presentation(outerContext, display) {

    private val lifecycleOwner = PresentationLifecycleOwner()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)

        lifecycleOwner.onCreate()

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                LiquidGlassDesktopUI(
                    displayId = displayId,
                    packageManager = context.packageManager
                )
            }
        }

        setContentView(composeView)
    }

    override fun onStop() {
        super.onStop()
        lifecycleOwner.onDestroy()
    }
}

@Composable
fun LiquidGlassDesktopUI(displayId: Int, packageManager: PackageManager) {
    var isAppDrawerOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<DesktopAppItem>>(emptyList()) }

    // Update Time & Date
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now)
            delay(1000L)
        }
    }

    // Load installed apps
    LaunchedEffect(Unit) {
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val apps = packageManager.queryIntentActivities(intent, 0)
        installedApps = apps.map {
            DesktopAppItem(
                name = it.loadLabel(packageManager).toString(),
                packageName = it.activityInfo.packageName,
                componentName = it.activityInfo.packageName + "/" + it.activityInfo.name
            )
        }.sortedBy { it.name }
    }

    // Futuristic Liquid Glass Wallpaper Gradient
    val glassWallpaper = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A), // Deep Slate
            Color(0xFF1E1B4B), // Liquid Purple
            Color(0xFF31103F), // Dark Violet
            Color(0xFF020617)  // Obsidian Black
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(glassWallpaper)
    ) {
        // TOP STATUS BAR (Apple Liquid Glass Style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = 20.dp, end = 20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x22FFFFFF))
                .border(1.dp, Color(0x35FFFFFF), RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left Status
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = " A-DeX OS",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x3310B981))
                        .border(1.dp, Color(0xFF10B981), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Desktop Mode • Display $displayId",
                        color = Color(0xFF34D399),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Right Status Info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "📶 Wi-Fi  |  ⚡️ 98%  |  ", color = Color.LightGray, fontSize = 12.sp)
                Text(text = "$currentDate  •  ", color = Color.LightGray, fontSize = 12.sp)
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // MAIN DESKTOP SHORTCUTS GRID
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 70.dp, bottom = 100.dp, start = 40.dp, end = 40.dp)
        ) {
            val primaryShortcuts = listOf(
                DesktopAppItem("Chrome", "com.android.chrome", "com.android.chrome/com.google.android.apps.chrome.Main"),
                DesktopAppItem("ChatGPT", "com.openai.chatgpt", "com.openai.chatgpt/.MainActivity"),
                DesktopAppItem("Claude", "com.anthropic.claude", "com.anthropic.claude/.mainactivity.MainActivity"),
                DesktopAppItem("Calendar", "com.google.android.calendar", "com.google.android.calendar/com.android.calendar.AllInOneActivity"),
                DesktopAppItem("YouTube", "com.google.android.youtube", "com.google.android.youtube/com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity")
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(primaryShortcuts) { app ->
                    GlassDesktopIcon(app = app, displayId = displayId)
                }
            }
        }

        // APP DRAWER / START MENU OVERLAY
        AnimatedVisibility(
            visible = isAppDrawerOpen,
            enter = fadeIn(tween(200)) + slideInVertically(tween(250)) { it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically(tween(200)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 90.dp, start = 30.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(420.dp)
                    .height(500.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xE60F172A))
                    .border(1.5.dp, Color(0x44FFFFFF), RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "App Launcher",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))

                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search installed apps...", color = Color.Gray, fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0x33FFFFFF),
                            unfocusedContainerColor = Color(0x22FFFFFF),
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    val filteredApps = installedApps.filter {
                        it.name.contains(searchQuery, ignoreCase = true)
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredApps) { app ->
                            GlassAppDrawerIcon(app = app, displayId = displayId) {
                                isAppDrawerOpen = false
                            }
                        }
                    }
                }
            }
        }

        // FLOATING APPLE LIQUID GLASS TASKBAR DOCK (Bottom)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0x450F172A))
                .border(1.2.dp, Color(0x40FFFFFF), RoundedCornerShape(28.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Start / App Drawer Toggle Button
            IconButton(
                onClick = { isAppDrawerOpen = !isAppDrawerOpen },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isAppDrawerOpen) Color(0xFF6366F1) else Color(0x33FFFFFF)
                    )
                    .border(1.dp, Color(0x55FFFFFF), CircleShape)
            ) {
                Text(text = "❖", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(Color(0x33FFFFFF))
            )

            // Quick Dock Shortcuts
            val dockApps = listOf(
                DesktopAppItem("Chrome", "com.android.chrome", "com.android.chrome/com.google.android.apps.chrome.Main"),
                DesktopAppItem("Calendar", "com.google.android.calendar", "com.google.android.calendar/com.android.calendar.AllInOneActivity"),
                DesktopAppItem("YouTube", "com.google.android.youtube", "com.google.android.youtube/com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity")
            )

            dockApps.forEach { app ->
                GlassDockIcon(app = app, displayId = displayId)
            }
        }
    }
}

@Composable
fun GlassDesktopIcon(app: DesktopAppItem, displayId: Int) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isHovered) 1.08f else 1.0f, label = "scale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .clickable { launchAppInFreeform(app.packageName, app.componentName, displayId) }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0x44FFFFFF), Color(0x11FFFFFF))
                    )
                )
                .border(1.2.dp, Color(0x44FFFFFF), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = app.name.take(1).uppercase(),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = app.name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GlassAppDrawerIcon(app: DesktopAppItem, displayId: Int, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                onClick()
                launchAppInFreeform(app.packageName, app.componentName, displayId)
            }
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x22FFFFFF))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = app.name.take(1).uppercase(),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.name,
            color = Color.White,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GlassDockIcon(app: DesktopAppItem, displayId: Int) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x33FFFFFF))
            .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp))
            .clickable { launchAppInFreeform(app.packageName, app.componentName, displayId) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = app.name.take(1).uppercase(),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun launchAppInFreeform(packageName: String, componentName: String?, displayId: Int) {
    val cmd = if (componentName != null) {
        arrayOf("am", "start", "--display", "$displayId", "--windowingMode", "5", "-f", "0x18000000", "-n", componentName)
    } else {
        arrayOf("monkey", "--display", "$displayId", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1")
    }

    try {
        if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, cmd, null, null) as? Process
            process?.waitFor()
            Timber.d("Launched $packageName on VirtualDisplay $displayId in freeform mode")
        }
    } catch (e: Exception) {
        Timber.e(e, "Error launching $packageName in freeform mode")
    }
}
